package com.xperp.finance.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record BudgetDto(
    long id,
    long categoryId,
    String period,
    BigDecimal amount,
    BigDecimal committed,
    BigDecimal spent) {

  public BigDecimal used() {
    return committed.add(spent);
  }

  public BigDecimal available() {
    return amount.subtract(used());
  }

  /** (committed + spent) / amount × 100. */
  public double usagePct() {
    if (amount.signum() == 0) {
      return 0;
    }
    return used()
        .multiply(BigDecimal.valueOf(100))
        .divide(amount, 2, RoundingMode.HALF_UP)
        .doubleValue();
  }
}
