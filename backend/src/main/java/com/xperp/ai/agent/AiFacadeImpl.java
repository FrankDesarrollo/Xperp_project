package com.xperp.ai.agent;

import com.xperp.ai.api.AiFacade;
import com.xperp.ai.decisions.Approval;
import com.xperp.ai.decisions.ApprovalService;
import com.xperp.ai.forecasting.ForecastService;
import com.xperp.ai.policies.AiSettingsService;
import com.xperp.ai.policies.PolicyDefaults;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.sales.api.SalesOrderDto;
import com.xperp.sales.api.SalesService;
import com.xperp.shared.persistence.PageResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class AiFacadeImpl implements AiFacade {

  private static final Logger log = LoggerFactory.getLogger(AiFacadeImpl.class);

  private final PolicyDefaults policyDefaults;
  private final AiSettingsService settings;
  private final ForecastService forecasts;
  private final ReplenishmentAgent replenishment;
  private final InventoryAgents inventoryAgents;
  private final SummaryAgent summary;
  private final FulfillmentAgents fulfillment;
  private final ApprovalService approvals;
  private final PurchasingService purchasing;
  private final SalesService sales;
  private final Clock clock;
  private final ReentrantLock replenishmentLock = new ReentrantLock();

  AiFacadeImpl(
      PolicyDefaults policyDefaults,
      AiSettingsService settings,
      ForecastService forecasts,
      ReplenishmentAgent replenishment,
      InventoryAgents inventoryAgents,
      SummaryAgent summary,
      FulfillmentAgents fulfillment,
      ApprovalService approvals,
      PurchasingService purchasing,
      SalesService sales,
      Clock clock) {
    this.policyDefaults = policyDefaults;
    this.settings = settings;
    this.forecasts = forecasts;
    this.replenishment = replenishment;
    this.inventoryAgents = inventoryAgents;
    this.summary = summary;
    this.fulfillment = fulfillment;
    this.approvals = approvals;
    this.purchasing = purchasing;
    this.sales = sales;
    this.clock = clock;
  }

  @Override
  public void initializeCompany() {
    policyDefaults.create();
    settings.initialize();
  }

  @Override
  public void initializeReorderParams(LocalDate today) {
    inventoryAgents.updateParams(today, false);
  }

  @Override
  public void onDayStarted(LocalDate day) {
    inventoryAgents.updateParams(day, true);
    inventoryAgents.slowMovers(day);
  }

  @Override
  public void runForecast(LocalDate day) {
    forecasts.generate(day);
  }

  @Override
  public void syncSupplierPrices() {
    int changes = purchasing.syncSupplierPrices();
    if (changes > 0) {
      log.info("{} cambios de precio de proveedores", changes);
    }
  }

  @Override
  public void runReplenishment(LocalDate day) {
    withLock(() -> replenishment.runDaily(day));
  }

  /** Revisión urgente de un SKU; comparte el candado con el ciclo diario para no duplicar OC. */
  void urgent(com.xperp.inventory.api.StockKey key) {
    withLock(() -> replenishment.runUrgent(key));
  }

  private void withLock(Runnable action) {
    replenishmentLock.lock();
    try {
      action.run();
    } finally {
      replenishmentLock.unlock();
    }
  }

  @Override
  public void onDayEnded(LocalDate day) {
    summary.summarize(day);
  }

  @Override
  public void hourly(boolean autoApprove) {
    approvals.expire();
    purchasing.retryUnsent();
    // Pedidos empacados que se quedaron sin guía (por ejemplo, si partners-mock no respondió)
    PageResult<SalesOrderDto> packed = sales.orders("packed", null, null, 1, 50);
    // Solo pedidos viejos: los recién empacados los atiende el listener de SalesOrderPacked.
    Instant cutoff = Instant.now(clock).minus(Duration.ofHours(12));
    for (SalesOrderDto o : packed.items()) {
      if (o.orderedAt().isBefore(cutoff)) {
        try {
          fulfillment.selectCarrier(o.id());
        } catch (RuntimeException e) {
          log.debug("Reintento de guía para {} falló: {}", o.number(), e.getMessage());
        }
      }
    }
    if (autoApprove) {
      Instant oldEnough = Instant.now(clock).minus(Duration.ofHours(1));
      for (Approval a : approvals.pending()) {
        if (a.getRequestedAt().isBefore(oldEnough)) {
          try {
            approvals.approve(a.getId(), null, "admin", "Aprobador virtual (SIM_AUTO_APPROVE)");
          } catch (RuntimeException e) {
            log.debug("Aprobación virtual de #{} falló: {}", a.getId(), e.getMessage());
          }
        }
      }
    }
  }

  @Override
  public String aiMode() {
    return settings.current().mode();
  }
}
