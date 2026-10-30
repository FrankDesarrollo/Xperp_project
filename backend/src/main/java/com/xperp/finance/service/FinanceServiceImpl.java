package com.xperp.finance.service;

import com.xperp.finance.api.BudgetDto;
import com.xperp.finance.api.FinanceService;
import com.xperp.finance.api.PayableDto;
import com.xperp.finance.domain.AccountPayable;
import com.xperp.finance.domain.Budget;
import com.xperp.finance.domain.BudgetCommitment;
import com.xperp.finance.repository.BudgetRepository;
import com.xperp.finance.repository.CommitmentRepository;
import com.xperp.finance.repository.PayableRepository;
import com.xperp.purchasing.events.GoodsReceived;
import com.xperp.purchasing.events.PurchaseOrderSent;
import com.xperp.purchasing.events.PurchaseOrderStatusChanged;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.time.SimTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Presupuesto de compras (AGENTS.md, 6.7): {@code committed} sube cuando se envía la OC y pasa a
 * {@code spent} cuando se recibe. Si la OC se cancela o la rechazan, se libera.
 */
@Service
@Transactional
class FinanceServiceImpl implements FinanceService {

  private final BudgetRepository budgets;
  private final CommitmentRepository commitments;
  private final PayableRepository payables;
  private final Clock clock;

  FinanceServiceImpl(
      BudgetRepository budgets,
      CommitmentRepository commitments,
      PayableRepository payables,
      Clock clock) {
    this.budgets = budgets;
    this.commitments = commitments;
    this.payables = payables;
    this.clock = clock;
  }

  @Override
  public void createBudget(long categoryId, String period, BigDecimal amount) {
    Budget budget = budgets.findByCategoryIdAndPeriod(categoryId, period).orElseGet(Budget::new);
    budget.setCategoryId(categoryId);
    budget.setPeriod(period);
    budget.setAmount(Money.round(amount));
    budgets.save(budget);
  }

  @Override
  @Transactional(readOnly = true)
  public List<BudgetDto> budgets(String period) {
    return budgets.findByPeriodOrderByCategoryIdAsc(period).stream()
        .map(FinanceServiceImpl::toDto)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public BudgetDto budget(long categoryId, String period) {
    return budgets
        .findByCategoryIdAndPeriod(categoryId, period)
        .map(FinanceServiceImpl::toDto)
        .orElse(new BudgetDto(0, categoryId, period, Money.ZERO, Money.ZERO, Money.ZERO));
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<PayableDto> payables(String status, Integer page, Integer size) {
    var pageable = PageResult.pageable(page, size, Sort.by(Sort.Direction.DESC, "id"));
    var result =
        status == null || status.isBlank()
            ? payables.findAll(pageable)
            : payables.findByStatus(status, pageable);
    return PageResult.of(
        result,
        p ->
            new PayableDto(
                p.getId(),
                p.getPurchaseOrderId(),
                p.getSupplierId(),
                p.getAmount(),
                p.getDueDate(),
                p.getStatus()));
  }

  // ---------------------------------------------------------------- eventos de compras

  @ApplicationModuleListener
  void on(PurchaseOrderSent event) {
    for (Map.Entry<Long, BigDecimal> e : event.amountsByCategory().entrySet()) {
      Budget budget =
          budgets
              .findByCategoryIdAndPeriod(e.getKey(), event.period())
              .orElseGet(
                  () -> {
                    Budget b = new Budget();
                    b.setCategoryId(e.getKey());
                    b.setPeriod(event.period());
                    b.setAmount(Money.ZERO);
                    return budgets.save(b);
                  });
      budget = budgets.findForUpdate(budget.getId()).orElseThrow();
      BigDecimal amount = Money.round(e.getValue());
      budget.setCommitted(budget.getCommitted().add(amount));
      BudgetCommitment c = new BudgetCommitment();
      c.setBudgetId(budget.getId());
      c.setPurchaseOrderId(event.purchaseOrderId());
      c.setAmount(amount);
      c.setStatus("committed");
      commitments.save(c);
    }
  }

  @ApplicationModuleListener
  void on(PurchaseOrderStatusChanged event) {
    if (!"cancelled".equals(event.status()) && !"rejected".equals(event.status())) {
      return;
    }
    for (BudgetCommitment c :
        commitments.findByPurchaseOrderIdAndStatus(event.purchaseOrderId(), "committed")) {
      Budget budget = budgets.findForUpdate(c.getBudgetId()).orElseThrow();
      budget.setCommitted(budget.getCommitted().subtract(c.getAmount()).max(BigDecimal.ZERO));
      c.setStatus("released");
    }
  }

  @ApplicationModuleListener
  void on(GoodsReceived event) {
    List<BudgetCommitment> open =
        commitments.findByPurchaseOrderIdAndStatus(event.purchaseOrderId(), "committed");
    for (BudgetCommitment c : open) {
      Budget budget = budgets.findForUpdate(c.getBudgetId()).orElseThrow();
      budget.setCommitted(budget.getCommitted().subtract(c.getAmount()).max(BigDecimal.ZERO));
      BigDecimal received =
          event.amountsByCategory().getOrDefault(budget.getCategoryId(), BigDecimal.ZERO);
      budget.setSpent(budget.getSpent().add(Money.round(received)));
      c.setStatus("spent");
    }
    if (open.isEmpty()) {
      // OC sin compromiso previo: se registra el gasto en el mes de la recepción.
      String period = SimTime.period(clock.instant());
      event
          .amountsByCategory()
          .forEach(
              (categoryId, amount) ->
                  budgets
                      .findByCategoryIdAndPeriod(categoryId, period)
                      .ifPresent(b -> b.setSpent(b.getSpent().add(Money.round(amount)))));
    }
    AccountPayable payable = new AccountPayable();
    payable.setPurchaseOrderId(event.purchaseOrderId());
    payable.setSupplierId(event.supplierId());
    payable.setAmount(event.total());
    payable.setDueDate(SimTime.today(clock).plusDays(event.paymentTermsDays()));
    payable.setStatus(event.paymentTermsDays() == 0 ? "paid" : "open");
    payables.save(payable);
  }

  private static BudgetDto toDto(Budget b) {
    return new BudgetDto(
        b.getId(), b.getCategoryId(), b.getPeriod(), b.getAmount(), b.getCommitted(), b.getSpent());
  }
}
