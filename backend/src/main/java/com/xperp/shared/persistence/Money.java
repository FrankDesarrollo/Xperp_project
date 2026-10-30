package com.xperp.shared.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Montos en COP: {@link BigDecimal} en Java y string decimal con 2 decimales en JSON. */
public final class Money {

  public static final BigDecimal ZERO = new BigDecimal("0.00");

  private Money() {}

  public static BigDecimal of(String value) {
    return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal of(long value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal round(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
  }

  /** Redondea a la decena (regla de precios de proveedores, AGENTS.md 2.6). */
  public static BigDecimal roundToTens(BigDecimal value) {
    return value
        .divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP)
        .multiply(BigDecimal.TEN)
        .setScale(2, RoundingMode.HALF_UP);
  }

  public static String format(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  /** Formato legible para razonamientos: $ 1.250.000. */
  public static String display(BigDecimal value) {
    if (value == null) {
      return "—";
    }
    BigDecimal rounded = value.setScale(0, RoundingMode.HALF_UP);
    String digits = rounded.abs().toPlainString();
    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (int i = digits.length() - 1; i >= 0; i--) {
      sb.append(digits.charAt(i));
      if (++count % 3 == 0 && i > 0) {
        sb.append('.');
      }
    }
    return (rounded.signum() < 0 ? "-$ " : "$ ") + sb.reverse();
  }
}
