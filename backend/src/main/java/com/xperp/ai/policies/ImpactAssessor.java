package com.xperp.ai.policies;

import com.xperp.ai.rules.Impact;
import com.xperp.ai.rules.ReorderRules;
import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.finance.api.FinanceService;
import com.xperp.inventory.api.InventoryRow;
import com.xperp.inventory.api.InventoryService;
import com.xperp.inventory.api.StockDto;
import com.xperp.purchasing.api.PurchaseOrderDto;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Calcula el impacto de cada propuesta con los datos del sistema (ADR 0004), para que quien aprueba
 * vea qué gasta, qué protege y qué arriesga. Lo hace el backend y no el LLM: así las cifras no
 * dependen de lo que el modelo diga. Si algo falla, la decisión sigue sin impacto en vez de caerse.
 */
@Component
public class ImpactAssessor {

  private static final Logger log = LoggerFactory.getLogger(ImpactAssessor.class);

  /** Días que tarda un traslado entre bodegas con TRP-02 (AGENTS.md, 2.7). */
  static final int TRANSFER_DAYS = 2;

  private final CatalogService catalog;
  private final InventoryService inventory;
  private final PurchasingService purchasing;
  private final FinanceService finance;
  private final SalesService sales;
  private final Clock clock;

  ImpactAssessor(
      CatalogService catalog,
      InventoryService inventory,
      PurchasingService purchasing,
      FinanceService finance,
      SalesService sales,
      Clock clock) {
    this.catalog = catalog;
    this.inventory = inventory;
    this.purchasing = purchasing;
    this.finance = finance;
    this.sales = sales;
    this.clock = clock;
  }

  public List<Map<String, Object>> assess(ActionProposal p) {
    try {
      Map<String, Object> payload = p.payload() == null ? Map.of() : p.payload();
      List<Impact.Item> items =
          switch (p.actionType()) {
            case "create_purchase_order" -> purchase(payload);
            case "reassign_purchase_order" -> reassign(p, payload);
            case "cancel_purchase_order" -> cancel(payload);
            case "propose_stock_transfer" -> transfer(payload);
            case "adjust_inventory" -> adjustment(payload);
            case "create_shipment" -> shipment(p, payload);
            case "apply_budget_plan" -> budgetPlan(payload);
            case "hold_sales_order" ->
                Impact.heldOrder(sales.order(Payloads.getLong(payload, "sales_order_id")).total());
            case "suggest_promotion" -> slowMover(payload);
            default -> List.of();
          };
      return items.stream().map(Impact.Item::toMap).toList();
    } catch (RuntimeException e) {
      log.debug("Sin impacto para {}: {}", p.actionType(), e.getMessage());
      return List.of();
    }
  }

  private List<Impact.Item> purchase(Map<String, Object> payload) {
    long warehouseId = Payloads.getLong(payload, "warehouse_id");
    Integer leadTime =
        purchasing.supplier(Payloads.getLong(payload, "supplier_id")).leadTimeTo(warehouseId);
    List<Impact.PurchaseLine> lines = new ArrayList<>();
    Map<Long, BigDecimal> byCategory = new LinkedHashMap<>();
    for (Map<String, Object> l : Payloads.getList(payload, "lines")) {
      ProductDto product = catalog.product(Payloads.getLong(l, "product_id"));
      InventoryRow row = inventory.row(product.id(), warehouseId);
      int qty = Payloads.getInt(l, "quantity");
      BigDecimal unitCost = Payloads.getMoney(l, "unit_cost");
      lines.add(
          new Impact.PurchaseLine(
              product.sku(),
              qty,
              unitCost,
              product.price(),
              demand(row.stock()),
              leadTime == null ? 0 : leadTime,
              row.stock().available(),
              row.inTransit()));
      byCategory.merge(
          product.categoryId(), unitCost.multiply(BigDecimal.valueOf(qty)), BigDecimal::add);
    }
    String period = SimTime.period(clock.instant());
    List<Impact.BudgetUse> budgets = new ArrayList<>();
    byCategory.forEach(
        (categoryId, charged) -> {
          var budget = finance.budget(categoryId, period);
          if (budget.amount().signum() > 0) {
            budgets.add(
                new Impact.BudgetUse(
                    catalog.category(categoryId).name(), charged, budget.available()));
          }
        });
    return Impact.purchase(lines, budgets);
  }

  private List<Impact.Item> reassign(ActionProposal p, Map<String, Object> payload) {
    if (p.amount() == null) {
      return List.of();
    }
    PurchaseOrderDto old = purchasing.order(Payloads.getLong(payload, "purchase_order_id"));
    Object pending = p.inputSnapshot() == null ? null : p.inputSnapshot().get("total_pendiente");
    BigDecimal pendingTotal = pending == null ? old.total() : new BigDecimal(pending.toString());
    Integer leadTime =
        purchasing
            .supplier(Payloads.getLong(payload, "new_supplier_id"))
            .leadTimeTo(old.warehouseId());
    return Impact.reassign(pendingTotal, p.amount(), leadTime);
  }

  private List<Impact.Item> cancel(Map<String, Object> payload) {
    PurchaseOrderDto po = purchasing.order(Payloads.getLong(payload, "purchase_order_id"));
    long pending =
        po.lines().stream().mapToLong(l -> Math.max(0, l.quantity() - l.quantityReceived())).sum();
    return Impact.cancel(po.total(), pending);
  }

  private List<Impact.Item> transfer(Map<String, Object> payload) {
    ProductDto product = catalog.product(Payloads.getLong(payload, "product_id"));
    int qty = Payloads.getInt(payload, "quantity");
    StockDto target = inventory.stock(product.id(), Payloads.getLong(payload, "to_warehouse_id"));
    StockDto donor = inventory.stock(product.id(), Payloads.getLong(payload, "from_warehouse_id"));
    double donorDemand = demand(donor);
    return Impact.transfer(
        qty,
        product.cost(),
        product.price(),
        demand(target),
        target.available(),
        TRANSFER_DAYS,
        donorDemand <= 0 ? 0 : ReorderRules.daysOfCover(donor.available() - qty, donorDemand));
  }

  private List<Impact.Item> adjustment(Map<String, Object> payload) {
    ProductDto product = catalog.product(Payloads.getLong(payload, "product_id"));
    return Impact.adjustment(Payloads.getInt(payload, "delta"), product.cost());
  }

  private List<Impact.Item> shipment(ActionProposal p, Map<String, Object> payload) {
    BigDecimal cost = Payloads.getMoney(payload, "cost");
    BigDecimal orderTotal = sales.order(Payloads.getLong(payload, "sales_order_id")).total();
    BigDecimal cheapest = null;
    String cheapestCarrier = null;
    for (Map<String, Object> alt :
        p.alternatives() == null ? List.<Map<String, Object>>of() : p.alternatives()) {
      Object c = alt.get("costo");
      if (c == null) {
        continue;
      }
      BigDecimal value = new BigDecimal(c.toString());
      if (cheapest == null || value.compareTo(cheapest) < 0) {
        cheapest = value;
        cheapestCarrier = String.valueOf(alt.get("transportadora"));
      }
    }
    return Impact.shipment(cost, orderTotal, cheapest, cheapestCarrier);
  }

  private static List<Impact.Item> budgetPlan(Map<String, Object> payload) {
    return Impact.budgetPlan(planTotal(payload, "buy"), planTotal(payload, "defer"));
  }

  private static BigDecimal planTotal(Map<String, Object> payload, String key) {
    BigDecimal total = BigDecimal.ZERO;
    for (Map<String, Object> plan : Payloads.getList(payload, key)) {
      total = total.add(Payloads.getMoney(Payloads.getMap(plan, "payload"), "total"));
    }
    return total;
  }

  private List<Impact.Item> slowMover(Map<String, Object> payload) {
    ProductDto product = catalog.product(Payloads.getLong(payload, "product_id"));
    StockDto s = inventory.stock(product.id(), Payloads.getLong(payload, "warehouse_id"));
    return Impact.slowMover(s.onHand(), product.cost(), s.daysOfCover());
  }

  private static double demand(StockDto s) {
    return s.avgDailyDemand() == null ? 0 : s.avgDailyDemand().doubleValue();
  }
}
