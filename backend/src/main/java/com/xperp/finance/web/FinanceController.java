package com.xperp.finance.web;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.CategoryDto;
import com.xperp.finance.api.BudgetDto;
import com.xperp.finance.api.FinanceService;
import com.xperp.finance.api.PayableDto;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.shared.contract.api.FinanceApi;
import com.xperp.shared.contract.model.AccountPayable;
import com.xperp.shared.contract.model.AccountPayablePage;
import com.xperp.shared.contract.model.Budget;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.time.SimTime;
import java.time.Clock;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FinanceController implements FinanceApi {

  private final FinanceService finance;
  private final CatalogService catalog;
  private final PurchasingService purchasing;
  private final Clock clock;

  FinanceController(
      FinanceService finance, CatalogService catalog, PurchasingService purchasing, Clock clock) {
    this.finance = finance;
    this.catalog = catalog;
    this.purchasing = purchasing;
    this.clock = clock;
  }

  @Override
  public ResponseEntity<List<Budget>> listBudgets(String period) {
    String p = period == null || period.isBlank() ? SimTime.period(clock.instant()) : period;
    return ResponseEntity.ok(finance.budgets(p).stream().map(this::toModel).toList());
  }

  @Override
  public ResponseEntity<AccountPayablePage> listAccountsPayable(
      String status, Integer page, Integer size) {
    PageResult<AccountPayable> result = finance.payables(status, page, size).map(this::toModel);
    return ResponseEntity.ok(
        new AccountPayablePage(result.items(), result.total(), result.page(), result.size()));
  }

  private Budget toModel(BudgetDto b) {
    CategoryDto c = catalog.category(b.categoryId());
    return new Budget(
        b.id(),
        c.id(),
        c.code(),
        c.name(),
        b.period(),
        Money.format(b.amount()),
        Money.format(b.committed()),
        Money.format(b.spent()),
        Money.format(b.available()),
        b.usagePct());
  }

  private AccountPayable toModel(PayableDto p) {
    var po = purchasing.order(p.purchaseOrderId());
    return new AccountPayable(
        p.id(),
        p.purchaseOrderId(),
        po.number(),
        p.supplierId(),
        po.supplierName(),
        Money.format(p.amount()),
        p.dueDate(),
        p.status());
  }
}
