package com.xperp.ai.policies.executors;

import com.xperp.ai.decisions.Alert;
import com.xperp.ai.decisions.AlertService;
import com.xperp.ai.policies.ActionExecutor;
import com.xperp.ai.policies.ActionProposal;
import com.xperp.ai.policies.Payloads;
import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.MovementDto;
import com.xperp.inventory.api.StockParams;
import com.xperp.inventory.api.TransferDto;
import com.xperp.inventory.api.TransferLine;
import com.xperp.inventory.api.WarehouseDto;
import com.xperp.logistics.api.CreateTask;
import com.xperp.logistics.api.LogisticsService;
import com.xperp.logistics.api.ShipmentDto;
import com.xperp.logistics.api.TaskDto;
import com.xperp.sales.api.SalesOrderDto;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.error.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Ejecutores de inventario, bodega, despachos, ventas y alertas. */
@Configuration
class OperationsExecutors {

  @Bean
  ActionExecutor proposeStockTransferExecutor(
      InventoryService inventory, LogisticsService logistics, CatalogService catalog, Clock clock) {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "propose_stock_transfer";
      }

      @Override
      public void validate(Map<String, Object> payload) {
        long productId = Payloads.getLong(payload, "product_id");
        long from = Payloads.getLong(payload, "from_warehouse_id");
        int qty = Payloads.getInt(payload, "quantity");
        if (inventory.stock(productId, from).available() < qty) {
          throw BusinessException.conflict(
              "insufficient_stock", "La bodega de origen ya no tiene el disponible");
        }
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        long productId = Payloads.getLong(payload, "product_id");
        long from = Payloads.getLong(payload, "from_warehouse_id");
        long to = Payloads.getLong(payload, "to_warehouse_id");
        int qty = Payloads.getInt(payload, "quantity");
        TransferDto t =
            inventory.createTransfer(
                from, to, List.of(new TransferLine(productId, qty)), decisionId);
        ProductDto p = catalog.product(productId);
        WarehouseDto origin = inventory.warehouse(from);
        WarehouseDto dest = inventory.warehouse(to);
        TaskDto task =
            logistics.createTask(
                new CreateTask(
                    from,
                    "transfer_out",
                    1,
                    "Despachar traslado " + t.number(),
                    "%d und de %s hacia %s (TRP-02, 2 días)".formatted(qty, p.sku(), dest.code()),
                    null,
                    null,
                    t.id(),
                    null,
                    t.number(),
                    Instant.now(clock).plus(Duration.ofHours(3)),
                    decisionId));
        return new Result(
            "Traslado %s: %d und de %s de %s a %s"
                .formatted(t.number(), qty, p.sku(), origin.code(), dest.code()),
            List.of(
                new ActionProposal.Ref("stock_transfer", t.id(), t.number()),
                new ActionProposal.Ref("warehouse_task", task.id(), task.title())),
            Map.of("stock_transfer_id", t.id(), "task_id", task.id()));
      }

      @Override
      public boolean reversible(Map<String, Object> payload, Map<String, Object> resultRef) {
        Long id = Payloads.getLongOrNull(resultRef, "stock_transfer_id");
        return id != null && "requested".equals(inventory.transfer(id).status());
      }

      @Override
      public String revert(long decisionId, Map<String, Object> payload, Map<String, Object> ref) {
        TransferDto t = inventory.cancelTransfer(Payloads.getLong(ref, "stock_transfer_id"));
        Long taskId = Payloads.getLongOrNull(ref, "task_id");
        if (taskId != null && "pending".equals(logistics.task(taskId).status())) {
          logistics.cancelTask(taskId);
        }
        return "Cancelado " + t.number();
      }
    };
  }

  @Bean
  ActionExecutor createWarehouseTaskExecutor(
      LogisticsService logistics, InventoryService inventory, CatalogService catalog, Clock clock) {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "create_warehouse_task";
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        String type = Payloads.getString(payload, "type");
        if ("count".equals(type)) {
          long productId = Payloads.getLong(payload, "product_id");
          long warehouseId = Payloads.getLong(payload, "warehouse_id");
          ProductDto p = catalog.product(productId);
          TaskDto task =
              logistics.createTask(
                  new CreateTask(
                      warehouseId,
                      "count",
                      1,
                      "Reconteo de " + p.sku(),
                      "Contar de nuevo " + p.name() + " por diferencia en el conteo",
                      null,
                      null,
                      null,
                      productId,
                      "CNT-" + Payloads.getLong(payload, "count_id"),
                      Instant.now(clock).plus(Duration.ofHours(4)),
                      decisionId));
          return new Result(
              "Tarea de reconteo para " + p.sku(),
              List.of(new ActionProposal.Ref("warehouse_task", task.id(), task.title())),
              Map.of("task_ids", List.of(task.id())));
        }
        long salesOrderId = Payloads.getLong(payload, "sales_order_id");
        List<TaskDto> tasks = logistics.createFulfillmentTasks(salesOrderId, decisionId);
        List<ActionProposal.Ref> refs = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (TaskDto t : tasks) {
          refs.add(new ActionProposal.Ref("warehouse_task", t.id(), t.title()));
          ids.add(t.id());
        }
        return new Result(
            "Tareas de alistamiento y empaque creadas", refs, Map.of("task_ids", ids));
      }

      @Override
      @SuppressWarnings("unchecked")
      public boolean reversible(Map<String, Object> payload, Map<String, Object> resultRef) {
        Object ids = resultRef.get("task_ids");
        if (!(ids instanceof List<?> list) || list.isEmpty()) {
          return false;
        }
        return ((List<Object>) list)
            .stream()
                .allMatch(
                    id -> "pending".equals(logistics.task(((Number) id).longValue()).status()));
      }

      @Override
      @SuppressWarnings("unchecked")
      public String revert(long decisionId, Map<String, Object> payload, Map<String, Object> ref) {
        for (Object id : (List<Object>) ref.get("task_ids")) {
          logistics.cancelTask(((Number) id).longValue());
        }
        return "Tareas canceladas";
      }
    };
  }

  @Bean
  ActionExecutor createShipmentExecutor(LogisticsService logistics) {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "create_shipment";
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        ShipmentDto s =
            logistics.createShipment(
                Payloads.getLong(payload, "sales_order_id"),
                Payloads.getLong(payload, "carrier_id"),
                Payloads.getString(payload, "service"),
                Payloads.getMoney(payload, "cost"),
                Payloads.getInt(payload, "eta_days"),
                decisionId);
        return new Result(
            "Guía " + s.trackingNumber() + " generada",
            List.of(new ActionProposal.Ref("shipment", s.id(), s.trackingNumber())),
            Map.of("shipment_id", s.id(), "tracking_number", s.trackingNumber()));
      }
    };
  }

  @Bean
  ActionExecutor holdSalesOrderExecutor(SalesService sales, AlertService alerts) {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "hold_sales_order";
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        SalesOrderDto order = sales.order(Payloads.getLong(payload, "sales_order_id"));
        Alert alert =
            alerts.raise(
                "demand_anomaly",
                "warning",
                "Pedido %s retenido: %s".formatted(order.number(), order.holdReason()),
                "sales_order",
                order.id(),
                decisionId);
        return new Result(
            order.number() + " retenido para revisión",
            List.of(new ActionProposal.Ref("sales_order", order.id(), order.number())),
            Map.of("sales_order_id", order.id(), "alert_id", alert.getId()));
      }

      @Override
      public boolean reversible(Map<String, Object> payload, Map<String, Object> resultRef) {
        Long id = Payloads.getLongOrNull(resultRef, "sales_order_id");
        return id != null && "on_hold".equals(sales.order(id).status());
      }

      @Override
      public String revert(long decisionId, Map<String, Object> payload, Map<String, Object> ref) {
        SalesOrderDto order = sales.release(Payloads.getLong(ref, "sales_order_id"));
        return "Liberado " + order.number();
      }
    };
  }

  @Bean
  ActionExecutor raiseAlertExecutor(AlertService alerts) {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "raise_alert";
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        Alert alert =
            alerts.raise(
                Payloads.getString(payload, "type"),
                Payloads.getString(payload, "severity"),
                Payloads.getString(payload, "message"),
                Payloads.getString(payload, "entity_type"),
                Payloads.getLongOrNull(payload, "entity_id"),
                decisionId);
        return new Result("Alerta registrada", List.of(), Map.of("alert_id", alert.getId()));
      }
    };
  }

  @Bean
  ActionExecutor suggestPromotionExecutor(InventoryService inventory, CatalogService catalog) {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "suggest_promotion";
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        long productId = Payloads.getLong(payload, "product_id");
        long warehouseId = Payloads.getLong(payload, "warehouse_id");
        inventory.setFlags(productId, warehouseId, true, true);
        return Result.of("Promoción sugerida para " + catalog.product(productId).sku());
      }
    };
  }

  @Bean
  ActionExecutor adjustInventoryExecutor(InventoryService inventory, CatalogService catalog) {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "adjust_inventory";
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        long productId = Payloads.getLong(payload, "product_id");
        long warehouseId = Payloads.getLong(payload, "warehouse_id");
        int delta = Payloads.getInt(payload, "delta");
        Long countId = Payloads.getLongOrNull(payload, "count_id");
        MovementDto m =
            inventory.adjust(
                productId,
                warehouseId,
                delta,
                "Ajuste por conteo físico (decisión #" + decisionId + ")",
                "inventory_count",
                countId);
        if (countId != null) {
          inventory.markCountAdjusted(countId);
        }
        ProductDto p = catalog.product(productId);
        return new Result(
            "Ajuste de %+d und de %s; saldo %d".formatted(delta, p.sku(), m.balanceAfter()),
            List.of(new ActionProposal.Ref("inventory", productId, p.sku())),
            Map.of("movement_id", m.id()));
      }
    };
  }

  @Bean
  ActionExecutor updateReorderParamsExecutor(InventoryService inventory) {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "update_reorder_params";
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        List<Map<String, Object>> items = Payloads.getList(payload, "items");
        for (Map<String, Object> i : items) {
          inventory.updateParams(
              Payloads.getLong(i, "product_id"),
              Payloads.getLong(i, "warehouse_id"),
              new StockParams(
                  Payloads.getInt(i, "safety_stock"),
                  Payloads.getInt(i, "reorder_point"),
                  Payloads.getInt(i, "max_stock"),
                  Payloads.getInt(i, "cover_days"),
                  new BigDecimal(String.valueOf(i.get("avg_daily_demand")))));
        }
        return Result.of("Parámetros actualizados en " + items.size() + " existencias");
      }
    };
  }

  @Bean
  ActionExecutor recordOnlyExecutors() {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "rescore_supplier";
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        return Result.of("Calificación de proveedores actualizada");
      }
    };
  }

  @Bean
  ActionExecutor sendDailySummaryExecutor() {
    return new ActionExecutor() {
      @Override
      public String actionType() {
        return "send_daily_summary";
      }

      @Override
      public Result execute(long decisionId, Map<String, Object> payload) {
        return Result.of("Resumen enviado a gerencia y compras");
      }
    };
  }
}
