package com.xperp.ai.forecasting;

import com.xperp.shared.time.HolidayCalendar;
import java.time.LocalDate;
import java.time.Month;
import org.springframework.stereotype.Component;

/**
 * Factores de calendario conocidos que se aplican sobre el pronóstico base (AGENTS.md, 8.2):
 * quincena, festivos (y su víspera) y temporada de diciembre. El día de la semana lo aprende
 * Holt-Winters con su estacionalidad de 7 días.
 */
@Component
public class CalendarFactors {

  static final double PAYDAY = 1.25;
  static final double HOLIDAY = 0.40;
  static final double HOLIDAY_EVE = 1.15;

  private final HolidayCalendar holidays;

  CalendarFactors(HolidayCalendar holidays) {
    this.holidays = holidays;
  }

  public double factor(LocalDate date, String categoryCode) {
    return calendar(date) * season(date, categoryCode);
  }

  double calendar(LocalDate date) {
    double f = 1.0;
    if (isPayday(date)) {
      f *= PAYDAY;
    }
    if (holidays.isHoliday(date)) {
      f *= HOLIDAY;
    } else if (holidays.isHoliday(date.plusDays(1))) {
      f *= HOLIDAY_EVE;
    }
    return f;
  }

  static boolean isPayday(LocalDate date) {
    int day = date.getDayOfMonth();
    return day == 15 || day == 16 || day == 1 || day == date.lengthOfMonth();
  }

  static double season(LocalDate date, String categoryCode) {
    if (date.getMonth() != Month.DECEMBER) {
      return 1.0;
    }
    return "BEB".equals(categoryCode) || "SNK".equals(categoryCode) ? 1.50 : 1.20;
  }
}
