package com.xperp.simulation.seed;

import com.xperp.simulation.demand.DemandProfile;
import com.xperp.simulation.demand.OrderPlanner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/** Lectura de los archivos de {@code resources/demo/} (AGENTS.md, 2.14). */
@Component
public class DemoFiles {

  private final ResourceLoader resources;
  private volatile DemandProfile profile;

  DemoFiles(ResourceLoader resources) {
    this.resources = resources;
  }

  public List<Map<String, String>> csv(String name) {
    try (InputStream in = resource(name).getInputStream()) {
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      List<Map<String, String>> rows = new ArrayList<>();
      String[] lines = text.split("\\r?\\n");
      List<String> header = parseLine(lines[0]);
      for (int i = 1; i < lines.length; i++) {
        if (lines[i].isBlank()) {
          continue;
        }
        List<String> values = parseLine(lines[i]);
        Map<String, String> row = new LinkedHashMap<>();
        for (int c = 0; c < header.size(); c++) {
          row.put(header.get(c), c < values.size() ? values.get(c) : "");
        }
        rows.add(row);
      }
      return rows;
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo leer demo/" + name, e);
    }
  }

  /** Separa una línea CSV respetando comillas dobles. */
  static List<String> parseLine(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        quoted = !quoted;
      } else if (ch == ',' && !quoted) {
        out.add(sb.toString().trim());
        sb.setLength(0);
      } else {
        sb.append(ch);
      }
    }
    out.add(sb.toString().trim());
    return out;
  }

  public Map<String, Object> yaml(String name) {
    try (InputStream in = resource(name).getInputStream()) {
      return new Yaml().load(in);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo leer demo/" + name, e);
    }
  }

  public DemandProfile profile() {
    DemandProfile local = profile;
    if (local == null) {
      try {
        local = DemandProfile.load(resource("demand_profile.yml"));
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
      profile = local;
    }
    return local;
  }

  /** Segmentos de clientes con sus rangos de líneas y cantidades. */
  public Map<String, OrderPlanner.SegmentSpec> segments() {
    Map<String, OrderPlanner.SegmentSpec> map = new LinkedHashMap<>();
    for (Map<String, String> r : csv("customer_segments.csv")) {
      map.put(
          r.get("code"),
          new OrderPlanner.SegmentSpec(
              r.get("code"),
              Integer.parseInt(r.get("lines_min")),
              Integer.parseInt(r.get("lines_max")),
              Integer.parseInt(r.get("qty_min")),
              Integer.parseInt(r.get("qty_max")),
              "cases".equals(r.get("qty_unit"))));
    }
    return map;
  }

  /** Demanda base por SKU (columna base_demand de products.csv). */
  public Map<String, Double> baseDemand() {
    Map<String, Double> map = new LinkedHashMap<>();
    for (Map<String, String> r : csv("products.csv")) {
      map.put(r.get("sku"), Double.parseDouble(r.get("base_demand")));
    }
    return map;
  }

  /** Factor de demanda de cada bodega (company.yml). */
  @SuppressWarnings("unchecked")
  public Map<String, Double> warehouseFactors() {
    Map<String, Double> map = new LinkedHashMap<>();
    for (Map<String, Object> w :
        (List<Map<String, Object>>) yaml("company.yml").get("warehouses")) {
      map.put((String) w.get("code"), ((Number) w.get("demand_factor")).doubleValue());
    }
    return map;
  }

  private Resource resource(String name) {
    return resources.getResource("classpath:demo/" + name);
  }
}
