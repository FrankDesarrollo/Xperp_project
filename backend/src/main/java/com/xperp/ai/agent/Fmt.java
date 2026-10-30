package com.xperp.ai.agent;

import com.xperp.shared.persistence.Money;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formatos para los razonamientos en español (montos, unidades, porcentajes). */
final class Fmt {

  private static final DecimalFormatSymbols ES =
      DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-CO"));

  private Fmt() {}

  static String money(BigDecimal v) {
    return Money.display(v);
  }

  static String units(long v) {
    DecimalFormat f = new DecimalFormat("#,##0", ES);
    return f.format(v);
  }

  static String dec(double v) {
    DecimalFormat f = new DecimalFormat("#,##0.0", ES);
    return f.format(v);
  }

  static String score(double v) {
    DecimalFormat f = new DecimalFormat("0.00", ES);
    return f.format(v);
  }

  static String pct(double v) {
    DecimalFormat f = new DecimalFormat("#,##0.#", ES);
    return f.format(v) + " %";
  }

  static String days(double v) {
    if (Double.isInfinite(v)) {
      return "sin demanda";
    }
    return dec(v) + " días";
  }
}
