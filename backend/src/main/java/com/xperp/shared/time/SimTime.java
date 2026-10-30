package com.xperp.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Utilidades de fecha en la zona de la empresa demo (America/Bogota). */
public final class SimTime {

  public static final ZoneId ZONE = ZoneId.of("America/Bogota");

  private static final DateTimeFormatter DISPLAY =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CO"))
          .withZone(ZONE);

  private static final DateTimeFormatter DATE_DISPLAY =
      DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-CO"));

  private SimTime() {}

  /** Convierte a la zona de Bogotá para la API (ISO 8601 con offset). Acepta nulos. */
  public static OffsetDateTime toOffset(Instant instant) {
    return instant == null ? null : instant.atZone(ZONE).toOffsetDateTime();
  }

  public static LocalDate today(Clock clock) {
    return LocalDate.ofInstant(clock.instant(), ZONE);
  }

  public static Instant startOfDay(LocalDate date) {
    return date.atStartOfDay(ZONE).toInstant();
  }

  public static LocalDate dateOf(Instant instant) {
    return LocalDate.ofInstant(instant, ZONE);
  }

  /** Formato de pantalla: dd/mm/aaaa hh:mm. */
  public static String display(Instant instant) {
    return instant == null ? "—" : DISPLAY.format(instant);
  }

  public static String display(LocalDate date) {
    return date == null ? "—" : DATE_DISPLAY.format(date);
  }

  /** Periodo de presupuesto (YYYY-MM) de un instante. */
  public static String period(Instant instant) {
    LocalDate date = dateOf(instant);
    return "%04d-%02d".formatted(date.getYear(), date.getMonthValue());
  }
}
