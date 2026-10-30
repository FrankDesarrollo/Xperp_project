package com.xperp.ai.assistant;

import com.xperp.ai.decisions.AiDecision;
import com.xperp.ai.decisions.ApprovalRepository;
import com.xperp.ai.tools.ReadTools;
import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.ProductDto;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.purchasing.api.SupplierDto;
import com.xperp.shared.time.SimTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Asistente sin LLM (modo mock): reconoce la intención de la pregunta con palabras clave, consulta
 * las tools de lectura y redacta la respuesta con plantillas. Nunca genera SQL.
 */
@Component
public class MockAssistant {

  public record Reply(String text, List<Map<String, Object>> toolCalls) {}

  private static final Map<String, String> CATEGORIES =
      Map.of(
          "aseo", "ASE",
          "abarrote", "ABA",
          "grano", "ABA",
          "bebida", "BEB",
          "lacteo", "BEB",
          "cuidado", "CPE",
          "personal", "CPE",
          "snack", "SNK",
          "confiteria", "SNK");

  private final ReadTools tools;
  private final CatalogService catalog;
  private final PurchasingService purchasing;
  private final ApprovalRepository approvals;

  MockAssistant(
      ReadTools tools,
      CatalogService catalog,
      PurchasingService purchasing,
      ApprovalRepository approvals) {
    this.tools = tools;
    this.catalog = catalog;
    this.purchasing = purchasing;
    this.approvals = approvals;
  }

  public Reply reply(String question) {
    String q = normalize(question);
    String text;
    if (q.contains("por que") || q.contains("porque") || q.contains("razon")) {
      text = why(q);
    } else if (containsAny(
        q,
        "recomien",
        "recomend",
        "sugier",
        "sugerencia",
        "que compro",
        "que pido",
        "que hago",
        "que debo",
        "reponer")) {
      text = recommendations(q);
    } else if (containsAny(q, "exceso", "sobra", "sobrestock", "sobreinventario", "demasiado")) {
      text = excess(q);
    } else if (containsAny(q, "agota", "quiebre", "se acaba", "faltan", "escase", "agotar")) {
      text = lowStock();
    } else if (containsAny(q, "gastado", "gasto", "presupuesto", "llevamos")) {
      text = budget(q);
    } else if (containsAny(q, "aprobacion", "aprobar", "pendientes de aprob")) {
      text = pendingApprovals();
    } else if (containsAny(q, "orden de compra", "ordenes de compra", " oc", "compras abiertas")) {
      text = openOrders(q);
    } else if (containsAny(q, "pronostico", "proyeccion", "cuanto se va a vender")) {
      text = forecast(q);
    } else if (containsAny(q, "venta", "vendimos", "facturamos", "margen", "nivel de servicio")) {
      text = kpis();
    } else if (containsAny(
        q, "stock", "inventario", "existencia", "cuanto hay", "cuantas unidades")) {
      text = stock(q);
    } else {
      text =
          "Puedo responder preguntas de solo lectura sobre la operación. Por ejemplo: «¿qué se"
              + " agota esta semana?», «¿qué me recomiendas reponer?», «¿por qué se le compró a"
              + " Mayorista Central?», «¿cuánto"
              + " llevamos gastado en aseo?», «¿cuánto hay de ABA-001?» o «¿cómo van las ventas"
              + " del mes?».";
    }
    return new Reply(text, tools.drainCalls());
  }

  /** Lo que recomienda la IA: de un SKU si lo nombran, o la lista de lo que hay que reponer. */
  private String recommendations(String q) {
    String sku = extractSku(q).orElse(null);
    String warehouse = warehouseIn(q);
    List<Map<String, Object>> recs = tools.listRecommendations(sku, warehouse);
    String where = warehouse == null ? "" : " en " + warehouse;
    if (sku != null) {
      if (recs.isEmpty()) {
        return ("Con %s%s no hay nada que hacer por ahora: el disponible está entre el punto de"
                + " reorden y el tope.")
            .formatted(sku, where);
      }
      StringBuilder sb = new StringBuilder();
      for (Map<String, Object> r : recs) {
        sb.append(
            "• %s en %s: %s %s\n"
                .formatted(
                    r.get("sku"), r.get("bodega"), sentence(r.get("titulo")), r.get("razon")));
      }
      return sb.toString().trim();
    }
    List<Map<String, Object>> toAct =
        recs.stream().filter(r -> !"excess".equals(r.get("accion"))).toList();
    if (toAct.isEmpty()) {
      return "Por ahora no recomiendo reponer nada%s: ningún SKU está bajo su punto de reorden sin una compra en camino."
          .formatted(where);
    }
    StringBuilder sb =
        new StringBuilder(
            "Esto recomiendo hoy%s, de lo más urgente a lo menos:\n".formatted(where));
    for (Map<String, Object> r : toAct.stream().limit(6).toList()) {
      sb.append(
          "• %s %s en %s: %s\n"
              .formatted(
                  r.get("sku"), r.get("producto"), r.get("bodega"), sentence(r.get("titulo"))));
    }
    if (toAct.size() > 6) {
      sb.append("…y ").append(toAct.size() - 6).append(" más.\n");
    }
    sb.append("El porqué de cada una está en Inventario → Recomendaciones de la IA.");
    return sb.toString();
  }

  private String excess(String q) {
    String warehouse = warehouseIn(q);
    List<Map<String, Object>> over =
        tools.listRecommendations(null, warehouse).stream()
            .filter(r -> "excess".equals(r.get("accion")))
            .toList();
    if (over.isEmpty()) {
      return "No hay exceso: ningún SKU pasa su tope de 30 días de demanda.";
    }
    StringBuilder sb = new StringBuilder("Estos SKU tienen más de lo que cabe en su tope:\n");
    for (Map<String, Object> r : over.stream().limit(10).toList()) {
      sb.append(
          "• %s %s en %s: %s\n"
              .formatted(
                  r.get("sku"), r.get("producto"), r.get("bodega"), sentence(r.get("titulo"))));
    }
    return sb.append("La IA no los repondrá hasta que bajen del punto de reorden.").toString();
  }

  /** Cierra con punto, sin duplicarlo cuando el texto ya termina en uno («S.A.S.»). */
  private static String sentence(Object text) {
    String t = String.valueOf(text).strip();
    return t.endsWith(".") ? t : t + ".";
  }

  /** Bodega nombrada en la pregunta, o null si no nombra ninguna. */
  private static String warehouseIn(String q) {
    if (containsAny(q, "bog", "bogota")) {
      return "BOG-01";
    }
    if (containsAny(q, "cal-01", "cal 01", "cal01", "cali")) {
      return "CAL-01";
    }
    return null;
  }

  private String lowStock() {
    List<Map<String, Object>> rows = tools.listLowStock(7);
    if (rows.isEmpty()) {
      return "Ningún SKU baja de 7 días de cobertura en este momento.";
    }
    StringBuilder sb =
        new StringBuilder("Con la demanda actual, estos SKU no alcanzan para 7 días:\n");
    for (Map<String, Object> r : rows.stream().limit(10).toList()) {
      sb.append(
          "• %s %s en %s: %s und disponibles, %s días de cobertura%s\n"
              .formatted(
                  r.get("sku"),
                  r.get("producto"),
                  r.get("bodega"),
                  r.get("disponible"),
                  num(r.get("dias_cobertura")),
                  ((Integer) r.get("en_transito")) > 0
                      ? " (vienen " + r.get("en_transito") + " en camino)"
                      : ""));
    }
    if (rows.size() > 10) {
      sb.append("…y ").append(rows.size() - 10).append(" más.");
    }
    return sb.toString().trim();
  }

  private String why(String q) {
    Optional<SupplierDto> supplier =
        purchasing.suppliers().stream()
            .filter(
                s ->
                    q.contains(normalize(s.name()).replace(" s.a.s.", "").trim())
                        || q.contains(normalize(s.code()))
                        || q.contains(normalize(firstWords(s.name(), 2))))
            .findFirst();
    String search = supplier.map(SupplierDto::code).orElse(extractSku(q).orElse(""));
    if (search.isBlank()) {
      return "¿Sobre qué proveedor, SKU u OC quieres saber? Por ejemplo: «¿por qué se le compró a"
          + " Mayorista Central?».";
    }
    tools.findDecisions(search);
    List<AiDecision> found = tools.searchDecisions(search);
    if (found.isEmpty()) {
      return "No encontré decisiones recientes de la IA relacionadas con " + search + ".";
    }
    AiDecision d = found.get(0);
    StringBuilder sb = new StringBuilder();
    sb.append("La decisión más reciente es «")
        .append(d.getTitle())
        .append("» (")
        .append(SimTime.display(d.getSimTime()))
        .append("): ")
        .append(d.getReasoning());
    if (found.size() > 1) {
      sb.append(" Hay ")
          .append(found.size() - 1)
          .append(" decisiones más relacionadas en IA → Decisiones.");
    }
    return sb.toString();
  }

  private String budget(String q) {
    String category = null;
    for (Map.Entry<String, String> e : CATEGORIES.entrySet()) {
      if (q.contains(e.getKey())) {
        category = e.getValue();
      }
    }
    List<Map<String, Object>> rows = tools.getBudgetStatus(category);
    if (rows.isEmpty()) {
      return "No hay presupuesto cargado para este mes.";
    }
    StringBuilder sb = new StringBuilder();
    for (Map<String, Object> r : rows) {
      sb.append(
          "• %s (%s): gastado %s, comprometido %s, disponible %s de %s (%.1f %%)\n"
              .formatted(
                  r.get("category"),
                  r.get("period"),
                  r.get("spent"),
                  r.get("committed"),
                  r.get("available"),
                  r.get("budget"),
                  ((Number) r.get("usage_pct")).doubleValue()));
    }
    return sb.toString().trim();
  }

  private String pendingApprovals() {
    int count = approvals.findByStatus("pending").size();
    return count == 0
        ? "No hay aprobaciones pendientes."
        : "Hay %d aprobaciones pendientes. Revísalas en IA → Aprobaciones.".formatted(count);
  }

  private String openOrders(String q) {
    List<Map<String, Object>> rows = tools.getOpenPurchaseOrders(extractSku(q).orElse(null));
    if (rows.isEmpty()) {
      return "No hay órdenes de compra abiertas.";
    }
    StringBuilder sb = new StringBuilder("Órdenes de compra abiertas:\n");
    rows.stream()
        .limit(10)
        .forEach(
            r ->
                sb.append(
                    "• %s · %s · %s: %s und pendientes (%s, llega %s)\n"
                        .formatted(
                            r.get("po"),
                            r.get("supplier"),
                            r.get("sku"),
                            r.get("pending_units"),
                            r.get("status"),
                            r.get("expected_at"))));
    return sb.toString().trim();
  }

  private String kpis() {
    Map<String, Object> k = tools.queryKpis(null, null, null);
    return ("Del %s al %s: ventas por %s (hoy %s), margen bruto %s (%s %%), nivel de servicio %s %%,"
            + " %s unidades perdidas y %s SKU en quiebre. Compras del periodo: %s.")
        .formatted(
            k.get("desde"),
            k.get("hasta"),
            k.get("ventas_periodo"),
            k.get("ventas_hoy"),
            k.get("margen_bruto"),
            k.get("margen_pct"),
            k.get("nivel_servicio_pct"),
            k.get("unidades_perdidas"),
            k.get("sku_en_quiebre"),
            k.get("compras_periodo"));
  }

  private String stock(String q) {
    Optional<String> sku = extractSku(q);
    if (sku.isEmpty()) {
      return "¿De qué producto? Puedes darme el SKU (por ejemplo ABA-001) o el nombre.";
    }
    List<Map<String, Object>> rows = tools.getStock(sku.get(), null);
    StringBuilder sb = new StringBuilder();
    for (Map<String, Object> r : rows) {
      if (r.containsKey("error")) {
        return r.get("error").toString();
      }
      sb.append(
          "• %s %s en %s: %s disponibles, %s reservadas, %s en tránsito, %s días de cobertura (%s)\n"
              .formatted(
                  r.get("sku"),
                  r.get("producto"),
                  r.get("bodega"),
                  r.get("disponible"),
                  r.get("reservado"),
                  r.get("en_transito"),
                  num(r.get("dias_cobertura")),
                  r.get("estado")));
    }
    return sb.toString().trim();
  }

  private String forecast(String q) {
    Optional<String> sku = extractSku(q);
    if (sku.isEmpty()) {
      return "¿De qué SKU quieres el pronóstico?";
    }
    String warehouse = q.contains("bog") ? "BOG-01" : "CAL-01";
    Map<String, Object> f = tools.getForecast(sku.get(), warehouse, 7);
    return "Pronóstico de %s en %s para los próximos 7 días: %s unidades."
        .formatted(sku.get(), warehouse, f.get("forecast_units"));
  }

  /** SKU explícito (ABA-001) o el producto cuyo nombre aparece en la pregunta. */
  private Optional<String> extractSku(String q) {
    var m = java.util.regex.Pattern.compile("([a-z]{3})-?(\\d{3})").matcher(q);
    if (m.find()) {
      return Optional.of((m.group(1) + "-" + m.group(2)).toUpperCase(Locale.ROOT));
    }
    ProductDto best = null;
    for (ProductDto p : catalog.activeProducts()) {
      String name = normalize(firstWords(p.name(), 2));
      if (q.contains(name) && (best == null || p.name().length() < best.name().length())) {
        best = p;
      }
    }
    return Optional.ofNullable(best).map(ProductDto::sku);
  }

  private static String firstWords(String s, int n) {
    String[] parts = s.split("\\s+");
    List<String> out = new ArrayList<>();
    for (int i = 0; i < Math.min(n, parts.length); i++) {
      out.add(parts[i]);
    }
    return String.join(" ", out);
  }

  /** Número con coma decimal (es-CO) para las respuestas. */
  static String num(Object v) {
    if (v instanceof Number n) {
      return java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO")).format(n);
    }
    return String.valueOf(v);
  }

  static String normalize(String s) {
    String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD);
    return " " + n.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[¿?¡!]", "") + " ";
  }

  /**
   * ¿Alguna palabra aparece al comienzo de una palabra de la pregunta? Buscar en cualquier parte
   * confundía «inventario» con «venta» y respondía las ventas a toda pregunta de inventario.
   */
  static boolean containsAny(String q, String... words) {
    for (String w : words) {
      if (java.util.regex.Pattern.compile(
              "(?<![\\p{L}\\d])" + java.util.regex.Pattern.quote(w.strip()))
          .matcher(q)
          .find()) {
        return true;
      }
    }
    return false;
  }
}
