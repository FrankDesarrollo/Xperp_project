package com.xperp.ai.tools;

import com.xperp.ai.policies.ActionGateway;
import com.xperp.ai.policies.ActionProposal;
import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.purchasing.api.PurchaseOrderDto;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.purchasing.api.SupplierDto;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tools de escritura (AGENTS.md, 8.6). No ejecutan nada por su cuenta: arman la propuesta, la
 * mandan a {@link ActionGateway#submit} y devuelven el resultado de la política ("ejecutada
 * OC-2026-000031", "pendiente de aprobación #12", "bloqueada: kill switch").
 *
 * <p>El orquestador las usa para ejecutar la opción que eligió el LLM; antes fija un {@link
 * Context} con los datos de entrada, alternativas y traza, para que la bitácora quede completa.
 */
@Component
public class WriteTools {

  /** Contexto de la decisión que se registra junto con la acción. */
  public record Context(
      String decisionType,
      String triggerEvent,
      String triggerRef,
      Map<String, Object> input,
      List<Map<String, Object>> alternatives,
      String reasoning,
      double confidence,
      ActionProposal.LlmTrace llm,
      Map<String, Object> attributes,
      BigDecimal amount,
      List<ActionProposal.Ref> refs) {}

  private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

  private final ActionGateway gateway;
  private final PurchasingService purchasing;
  private final InventoryService inventory;
  private final CatalogService catalog;

  WriteTools(
      ActionGateway gateway,
      PurchasingService purchasing,
      InventoryService inventory,
      CatalogService catalog) {
    this.gateway = gateway;
    this.purchasing = purchasing;
    this.inventory = inventory;
    this.catalog = catalog;
  }

  /** Ejecuta {@code action} con un contexto de decisión. */
  public <T> T withContext(Context context, java.util.function.Supplier<T> action) {
    CONTEXT.set(context);
    try {
      return action.get();
    } finally {
      CONTEXT.remove();
    }
  }

  @Tool(
      name = "reassign_purchase_order",
      description = "Reasigna una OC rechazada o retrasada a otro proveedor.")
  public String reassignPurchaseOrder(
      @ToolParam(description = "Número de la OC, por ejemplo OC-2026-000012") String poNumber,
      @ToolParam(description = "Código del proveedor nuevo, por ejemplo PRV-09") String newSupplier,
      @ToolParam(description = "Motivo, en una frase") String reason) {
    Optional<PurchaseOrderDto> po = purchasing.byNumber(poNumber);
    Optional<SupplierDto> supplier = purchasing.supplierByCode(newSupplier);
    if (po.isEmpty() || supplier.isEmpty()) {
      return "bloqueada: OC o proveedor no encontrados";
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("purchase_order_id", po.get().id());
    payload.put("purchase_order_number", po.get().number());
    payload.put("new_supplier_id", supplier.get().id());
    payload.put("new_supplier_code", supplier.get().code());
    payload.put("reason", reason);
    return submit(
        "po_exception",
        "reassign_purchase_order",
        "Reasignar %s a %s".formatted(po.get().number(), supplier.get().code()),
        payload,
        reason,
        new ActionProposal.Ref("purchase_order", po.get().id(), po.get().number()));
  }

  @Tool(
      name = "cancel_purchase_order",
      description = "Cancela una OC que todavía no se ha despachado.")
  public String cancelPurchaseOrder(
      @ToolParam(description = "Número de la OC") String poNumber,
      @ToolParam(description = "Motivo") String reason) {
    Optional<PurchaseOrderDto> po = purchasing.byNumber(poNumber);
    if (po.isEmpty()) {
      return "bloqueada: OC no encontrada";
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("purchase_order_id", po.get().id());
    payload.put("purchase_order_number", po.get().number());
    payload.put("reason", reason);
    return submit(
        "po_exception",
        "cancel_purchase_order",
        "Cancelar " + po.get().number(),
        payload,
        reason,
        new ActionProposal.Ref("purchase_order", po.get().id(), po.get().number()));
  }

  @Tool(
      name = "wait_purchase_order",
      description = "Decide esperar la nueva fecha de una OC retrasada.")
  public String waitPurchaseOrder(
      @ToolParam(description = "Número de la OC") String poNumber,
      @ToolParam(description = "Motivo") String reason) {
    Optional<PurchaseOrderDto> po = purchasing.byNumber(poNumber);
    if (po.isEmpty()) {
      return "bloqueada: OC no encontrada";
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("purchase_order_id", po.get().id());
    payload.put("purchase_order_number", po.get().number());
    payload.put("reason", reason);
    return submit(
        "po_exception",
        "wait_purchase_order",
        "Esperar " + po.get().number(),
        payload,
        reason,
        new ActionProposal.Ref("purchase_order", po.get().id(), po.get().number()));
  }

  @Tool(
      name = "propose_stock_transfer",
      description = "Propone un traslado de un SKU entre bodegas.")
  public String proposeStockTransfer(
      @ToolParam(description = "SKU") String sku,
      @ToolParam(description = "Bodega de origen") String from,
      @ToolParam(description = "Bodega de destino") String to,
      @ToolParam(description = "Unidades") Integer quantity,
      @ToolParam(description = "Motivo") String reason) {
    Optional<ProductDto> p = catalog.productBySku(sku);
    Optional<WarehouseDto> origin = inventory.warehouseByCode(from);
    Optional<WarehouseDto> dest = inventory.warehouseByCode(to);
    if (p.isEmpty() || origin.isEmpty() || dest.isEmpty() || quantity == null) {
      return "bloqueada: datos incompletos";
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("product_id", p.get().id());
    payload.put("sku", sku);
    payload.put("from_warehouse_id", origin.get().id());
    payload.put("to_warehouse_id", dest.get().id());
    payload.put("quantity", quantity);
    return submit(
        "stock_transfer",
        "propose_stock_transfer",
        "Trasladar %s de %s a %s".formatted(sku, from, to),
        payload,
        reason,
        new ActionProposal.Ref("product", p.get().id(), sku));
  }

  @Tool(name = "raise_alert", description = "Registra una alerta para las personas.")
  public String raiseAlert(
      @ToolParam(description = "Tipo de alerta") String type,
      @ToolParam(description = "info, warning o critical") String severity,
      @ToolParam(description = "Mensaje") String message) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", type);
    payload.put("severity", severity);
    payload.put("message", message);
    return submit("alert", "raise_alert", message, payload, message, null);
  }

  private String submit(
      String defaultType,
      String actionType,
      String title,
      Map<String, Object> payload,
      String reason,
      ActionProposal.Ref ref) {
    Context ctx = CONTEXT.get();
    ActionProposal.Builder b =
        ActionProposal.builder(ctx == null ? defaultType : ctx.decisionType(), actionType)
            .title(title)
            .payload(payload)
            .reasoning(ctx == null || ctx.reasoning() == null ? reason : ctx.reasoning());
    if (ctx != null) {
      b.trigger(ctx.triggerEvent(), ctx.triggerRef())
          .input(ctx.input())
          .alternatives(ctx.alternatives())
          .confidence(ctx.confidence())
          .llm(ctx.llm())
          .amount(ctx.amount());
      ctx.attributes().forEach(b::attribute);
      if (ctx.refs() != null) {
        ctx.refs().forEach(r -> b.ref(r.type(), r.id(), r.label()));
      }
    } else {
      b.trigger("llm_tool", null);
    }
    if (ref != null
        && (ctx == null
            || ctx.refs() == null
            || ctx.refs().stream()
                .noneMatch(r -> r.id() == ref.id() && r.type().equals(ref.type())))) {
      b.ref(ref.type(), ref.id(), ref.label());
    }
    return gateway.submit(b.build()).summary();
  }
}
