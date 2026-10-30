package com.xperp.finance.api;

import com.xperp.shared.persistence.PageResult;
import java.math.BigDecimal;
import java.util.List;

/** API pública de finanzas: presupuesto mensual de compras y cuentas por pagar. */
public interface FinanceService {

  void createBudget(long categoryId, String period, BigDecimal amount);

  List<BudgetDto> budgets(String period);

  /** Presupuesto de una categoría en un periodo; si no existe, uno en cero. */
  BudgetDto budget(long categoryId, String period);

  PageResult<PayableDto> payables(String status, Integer page, Integer size);
}
