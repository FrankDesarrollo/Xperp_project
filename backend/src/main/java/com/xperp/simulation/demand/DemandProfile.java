package com.xperp.simulation.demand;

import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;

/** Factores de la demanda simulada (demo/demand_profile.yml, AGENTS.md 2.10). */
public record DemandProfile(
    Map<DayOfWeek, Double> weekday,
    double payday,
    double holiday,
    double holidayEve,
    int seasonMonth,
    Map<String, Double> seasonByCategory,
    double seasonDefault,
    double noiseSigma,
    Map<String, Integer> ordersPerDay,
    Map<Integer, Double> hourlyWeights,
    Map<String, Map<String, Double>> cities,
    int customers) {

  @SuppressWarnings("unchecked")
  public static DemandProfile load(Resource resource) throws IOException {
    try (InputStream in = resource.getInputStream()) {
      Map<String, Object> y = new Yaml().load(in);
      Map<DayOfWeek, Double> weekday = new EnumMap<>(DayOfWeek.class);
      ((Map<String, Object>) y.get("weekday"))
          .forEach((k, v) -> weekday.put(DayOfWeek.valueOf(k), num(v)));
      Map<String, Object> season = (Map<String, Object>) y.get("season");
      Map<String, Double> seasonCats = new LinkedHashMap<>();
      ((Map<String, Object>) season.get("categories")).forEach((k, v) -> seasonCats.put(k, num(v)));
      Map<String, Integer> orders = new LinkedHashMap<>();
      ((Map<String, Object>) y.get("orders_per_day"))
          .forEach((k, v) -> orders.put(k, ((Number) v).intValue()));
      Map<Integer, Double> hours = new LinkedHashMap<>();
      ((Map<Object, Object>) y.get("hourly_weights"))
          .forEach((k, v) -> hours.put(Integer.parseInt(k.toString()), num(v)));
      Map<String, Map<String, Double>> cities = new LinkedHashMap<>();
      ((Map<String, Map<String, Object>>) y.get("cities"))
          .forEach(
              (wh, m) -> {
                Map<String, Double> c = new LinkedHashMap<>();
                m.forEach((city, share) -> c.put(city, num(share)));
                cities.put(wh, c);
              });
      return new DemandProfile(
          weekday,
          num(y.get("payday")),
          num(y.get("holiday")),
          num(y.get("holiday_eve")),
          ((Number) season.get("month")).intValue(),
          seasonCats,
          num(season.get("default")),
          num(y.get("noise_sigma")),
          orders,
          hours,
          cities,
          ((Number) y.get("customers")).intValue());
    }
  }

  private static double num(Object v) {
    return ((Number) v).doubleValue();
  }
}
