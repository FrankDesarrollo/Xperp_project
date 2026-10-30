package com.xperp.simulation.demand;

import static org.assertj.core.api.Assertions.assertThat;

import com.xperp.sales.api.SalesLine;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderPlannerTest {

  private static DemandProfile profile() {
    Map<DayOfWeek, Double> weekday = new EnumMap<>(DayOfWeek.class);
    for (DayOfWeek d : DayOfWeek.values()) {
      weekday.put(d, 1.0);
    }
    Map<Integer, Double> hours = new LinkedHashMap<>();
    for (int h = 7; h < 19; h++) {
      hours.put(h, 1.0);
    }
    return new DemandProfile(
        weekday,
        1.25,
        0.4,
        1.15,
        12,
        Map.of("BEB", 1.5),
        1.2,
        0.15,
        Map.of("CAL-01", 30),
        hours,
        Map.of(),
        10);
  }

  private static final List<OrderPlanner.ProductSpec> PRODUCTS =
      List.of(
          new OrderPlanner.ProductSpec(1, "ABA-001", "ABA", 25, 180),
          new OrderPlanner.ProductSpec(2, "BEB-001", "BEB", 12, 200),
          new OrderPlanner.ProductSpec(3, "SNK-006", "SNK", 12, 10));

  private static final List<OrderPlanner.CustomerSpec> CUSTOMERS =
      List.of(
          new OrderPlanner.CustomerSpec(10, "tienda"),
          new OrderPlanner.CustomerSpec(11, "tienda"),
          new OrderPlanner.CustomerSpec(12, "minimercado"),
          new OrderPlanner.CustomerSpec(13, "mayorista"));

  private static final Map<String, OrderPlanner.SegmentSpec> SEGMENTS =
      Map.of(
          "tienda", new OrderPlanner.SegmentSpec("tienda", 1, 3, 2, 12, false),
          "minimercado", new OrderPlanner.SegmentSpec("minimercado", 2, 3, 6, 36, false),
          "mayorista", new OrderPlanner.SegmentSpec("mayorista", 2, 3, 1, 5, true));

  private static List<OrderPlanner.PlannedOrder> plan(LocalDate day, long seed) {
    return OrderPlanner.plan(
        day, "CAL-01", 1.0, PRODUCTS, CUSTOMERS, SEGMENTS, profile(), Set.of(), seed);
  }

  @Test
  void sameSeedGivesSameOrders() {
    LocalDate day = LocalDate.of(2026, 11, 3);
    assertThat(plan(day, 42)).isEqualTo(plan(day, 42));
    assertThat(plan(day, 42)).isNotEqualTo(plan(day, 7));
  }

  @Test
  void ordersAreSortedAndWithinBusinessHours() {
    List<OrderPlanner.PlannedOrder> orders = plan(LocalDate.of(2026, 11, 3), 42);
    assertThat(orders).isNotEmpty();
    for (int i = 1; i < orders.size(); i++) {
      assertThat(orders.get(i).at()).isAfterOrEqualTo(orders.get(i - 1).at());
    }
    orders.forEach(
        o -> {
          int hour = o.at().atZone(java.time.ZoneId.of("America/Bogota")).getHour();
          assertThat(hour).isBetween(7, 18);
          assertThat(o.lines()).allMatch(l -> l.quantity() > 0);
        });
  }

  @Test
  void totalUnitsTrackTheDailyDemand() {
    int units =
        plan(LocalDate.of(2026, 11, 3), 42).stream()
            .flatMap(o -> o.lines().stream())
            .mapToInt(SalesLine::quantity)
            .sum();
    // 180 + 200 + 10 = 390 por día, con ruido lognormal
    assertThat(units).isBetween(250, 550);
  }

  @Test
  void paydayAndHolidayFactors() {
    DemandProfile p = profile();
    assertThat(DemandModel.dayFactor(LocalDate.of(2026, 11, 15), p, Set.of())).isEqualTo(1.25);
    LocalDate holiday = LocalDate.of(2026, 11, 2);
    assertThat(DemandModel.dayFactor(holiday, p, Set.of(holiday))).isEqualTo(0.4);
    assertThat(DemandModel.dayFactor(holiday.minusDays(1), p, Set.of(holiday)))
        .isEqualTo(1.25 * 1.15);
    assertThat(DemandModel.season(LocalDate.of(2026, 12, 5), "BEB", p)).isEqualTo(1.5);
    assertThat(DemandModel.season(LocalDate.of(2026, 12, 5), "ABA", p)).isEqualTo(1.2);
  }
}
