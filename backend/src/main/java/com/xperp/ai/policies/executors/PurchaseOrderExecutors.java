package com.xperp.ai.policies.executors;

import com.xperp.ai.policies.ActionExecutor;
import com.xperp.ai.policies.ActionProposal;
import com.xperp.ai.policies.Payloads;
import com.xperp.purchasing.api.CreatePurchaseOrder;
import com.xperp.purchasing.api.PurchaseLine;
import com.xperp.purchasing.api.PurchaseOrderDto;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.purchasing.api.SupplierDto;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.persistence.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Ejecutores de las acciones sobre órdenes de compra. */
@Configuration
class PurchaseOrderExecutors {

  static final Set<String> NOT_CONFIRMED = Set.of("draft", "pending_approval", "sent");

  @Bean
  ActionExecutor createPurchaseOrderExecutor(PurchasingService purchasing) {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "create_purchase_order";
      }

      @Override
      public void validate(Map<String, Object> payload) {
        long supplierId = Payloads.getLong(payload, "supplier_id");
        SupplierDto supplier = purchasing.supplier(supplierId);
        if (!supplier.active()) {
          throw BusinessException.conflict("inactive_supplier", supplier.name() + " está inactivo");
        }
        if (Payloads.getList(payload, "lines").isEmpty()) {
          throw BusinessException.invalid("empty_order", "La OC no tiene líneas");
        }
        String key = Payloads.getString(payload, "idempotency_key");
        if (key != null && purchasing.byIdempotencyKey(key).isPresent()) {
          throw BusinessException.conflict(
              "duplicate_purchase_order", "Ya existe una OC para este disparador");
        }
        long warehouseId = Payloads.getLong(payload, "warehouse_id");
        for (Map<String, Object> line : Payloads.getList(payload, "lines")) {
          long productId = Payloads.getLong(line, "product_id");
          if (purchasing.hasOpenOrder(productId, warehouseId)) {
            throw BusinessException.conflict(
                "open_purchase_order", "Ya hay una OC abierta para ese SKU y bodega");
          }
        }
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        PurchaseOrderDto po = purchasing.create(command(payload, decisionId, false));
        po = purchasing.send(po.id());
        return result("Enviada " + po.number() + " a " + po.supplierName(), po);
      }

      @Override
      public Result prepare(long decisionId, Map<String, Object> payload) {
        PurchaseOrderDto po = purchasing.create(command(payload, decisionId, true));
        return result(po.number() + " pendiente de aprobación", po);
      }

      @Override
      public Result executeApproved(
          long decisionId, Map<String, Object> payload, Map<String, Object> resultRef) {
        Long poId = Payloads.getLongOrNull(resultRef, "purchase_order_id");
        if (poId == null) {
          return execute(decisionId, payload);
        }
        PurchaseOrderDto po = purchasing.send(poId);
        return result("Aprobada y enviada " + po.number() + " a " + po.supplierName(), po);
      }

      @Override
      public void discard(
          long decisionId, Map<String, Object> payload, Map<String, Object> resultRef) {
        Long poId = Payloads.getLongOrNull(resultRef, "purchase_order_id");
        if (poId != null) {
          purchasing.cancel(poId, "Aprobación rechazada o vencida");
        }
      }

      @Override
      public boolean reversible(Map<String, Object> payload, Map<String, Object> resultRef) {
        Long poId = Payloads.getLongOrNull(resultRef, "purchase_order_id");
        return poId != null && NOT_CONFIRMED.contains(purchasing.order(poId).status());
      }

      @Override
      public String revert(
          long decisionId, Map<String, Object> payload, Map<String, Object> resultRef) {
        PurchaseOrderDto po =
            purchasing.cancel(
                Payloads.getLong(resultRef, "purchase_order_id"), "Decisión de la IA revertida");
        return "Cancelada " + po.number();
      }
    };
  }

  static CreatePurchaseOrder command(
      Map<String, Object> payload, long decisionId, boolean pending) {
    List<PurchaseLine> lines = new ArrayList<>();
    for (Map<String, Object> l : Payloads.getList(payload, "lines")) {
      BigDecimal cost = Payloads.getMoney(l, "unit_cost");
      lines.add(
          new PurchaseLine(
              Payloads.getLong(l, "product_id"), Payloads.getInt(l, "quantity"), cost));
    }
    return new CreatePurchaseOrder(
        Payloads.getLong(payload, "supplier_id"),
        Payloads.getLong(payload, "warehouse_id"),
        lines,
        null,
        decisionId,
        null,
        Payloads.getString(payload, "idempotency_key"),
        pending,
        "Generada por Xperp IA (decisión #" + decisionId + ")");
  }

  static ActionExecutor.Result result(String message, PurchaseOrderDto po) {
    return new ActionExecutor.Result(
        message,
        List.of(new ActionProposal.Ref("purchase_order", po.id(), po.number())),
        Map.of(
            "purchase_order_id",
            po.id(),
            "purchase_order_number",
            po.number(),
            "total",
            Money.format(po.total())));
  }

  @Bean
  ActionExecutor reassignPurchaseOrderExecutor(PurchasingService purchasing) {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "reassign_purchase_order";
      }

      @Override
      public void validate(Map<String, Object> payload) {
        PurchaseOrderDto po = purchasing.order(Payloads.getLong(payload, "purchase_order_id"));
        if (!Set.of("sent", "confirmed", "delayed", "rejected").contains(po.status())) {
          throw BusinessException.conflict(
              "invalid_status", po.number() + " ya no se puede reasignar");
        }
        purchasing.supplier(Payloads.getLong(payload, "new_supplier_id"));
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        PurchaseOrderDto old = purchasing.order(Payloads.getLong(payload, "purchase_order_id"));
        PurchaseOrderDto po =
            purchasing.reassign(
                old.id(),
                Payloads.getLong(payload, "new_supplier_id"),
                Payloads.getString(payload, "reason"),
                decisionId);
        return new Result(
            "Reasignada " + old.number() + " → " + po.number() + " con " + po.supplierName(),
            List.of(
                new ActionProposal.Ref("purchase_order", old.id(), old.number()),
                new ActionProposal.Ref("purchase_order", po.id(), po.number())),
            Map.of("purchase_order_id", po.id(), "replaced_purchase_order_id", old.id()));
      }
    };
  }

  @Bean
  ActionExecutor cancelPurchaseOrderExecutor(PurchasingService purchasing) {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "cancel_purchase_order";
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        PurchaseOrderDto po =
            purchasing.cancel(
                Payloads.getLong(payload, "purchase_order_id"),
                Payloads.getString(payload, "reason"));
        return new Result(
            "Cancelada " + po.number(),
            List.of(new ActionProposal.Ref("purchase_order", po.id(), po.number())),
            Map.of("purchase_order_id", po.id()));
      }
    };
  }

  @Bean
  ActionExecutor waitPurchaseOrderExecutor(PurchasingService purchasing) {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "wait_purchase_order";
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        PurchaseOrderDto po = purchasing.order(Payloads.getLong(payload, "purchase_order_id"));
        return new Result(
            "Se espera la nueva fecha de " + po.number(),
            List.of(new ActionProposal.Ref("purchase_order", po.id(), po.number())),
            Map.of("purchase_order_id", po.id()));
      }
    };
  }
}
