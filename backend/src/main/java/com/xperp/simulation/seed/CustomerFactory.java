package com.xperp.simulation.seed;

import com.xperp.sales.api.CustomerCommand;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.datafaker.Faker;

/**
 * Genera los 120 clientes demo con semilla fija (AGENTS.md, 2.8). Los nombres siguen los patrones
 * "Tienda Doña &lt;nombre&gt;", "Minimercado &lt;apellido&gt;", etc. Son ficticios.
 */
final class CustomerFactory {

  record Spec(String segment, double share) {}

  private CustomerFactory() {}

  static List<CustomerCommand> build(
      int total,
      List<Spec> segments,
      Map<String, Long> warehouseIds,
      Map<String, Map<String, Double>> citiesByWarehouse,
      long seed) {
    Faker faker = new Faker(Locale.forLanguageTag("es"), new Random(seed));
    Random rng = new Random(seed + 17);
    List<CustomerCommand> result = new ArrayList<>();
    Set<String> names = new HashSet<>();
    int n = 1;
    for (Spec s : segments) {
      int count = (int) Math.round(total * s.share());
      for (int i = 0; i < count; i++) {
        String warehouse = i % 7 < 5 ? "CAL-01" : "BOG-01";
        String city = pick(citiesByWarehouse.get(warehouse), rng);
        String name;
        int guard = 0;
        do {
          name = name(s.segment(), faker);
        } while (!names.add(name) && guard++ < 20);
        String document =
            "%d%07d-%d".formatted(9, 1_000_000 + rng.nextInt(8_999_999), rng.nextInt(10));
        result.add(
            new CustomerCommand(
                "CLI-%04d".formatted(n++),
                name,
                document,
                s.segment(),
                city,
                faker.address().streetAddress(),
                warehouseIds.get(warehouse)));
      }
    }
    return result;
  }

  static String name(String segment, Faker faker) {
    return switch (segment) {
      case "tienda" -> "Tienda Doña " + faker.name().femaleFirstName();
      case "minimercado" -> "Minimercado " + faker.name().lastName();
      case "supermercado" -> "Supermercados " + faker.name().lastName() + " S.A.S.";
      case "mayorista" -> "Comercializadora " + faker.name().lastName();
      default -> faker.name().firstName() + " en línea";
    };
  }

  static String pick(Map<String, Double> weights, Random rng) {
    double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
    double r = rng.nextDouble() * total;
    for (Map.Entry<String, Double> e : weights.entrySet()) {
      r -= e.getValue();
      if (r <= 0) {
        return e.getKey();
      }
    }
    return new LinkedHashMap<>(weights).keySet().iterator().next();
  }
}
