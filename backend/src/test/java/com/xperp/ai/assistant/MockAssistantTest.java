package com.xperp.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xperp.ai.decisions.ApprovalRepository;
import com.xperp.ai.tools.ReadTools;
import com.xperp.catalog.api.CatalogService;
import com.xperp.purchasing.api.PurchasingService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MockAssistantTest {

  private ReadTools tools;
  private MockAssistant assistant;

  @BeforeEach
  void setUp() {
    tools = mock(ReadTools.class);
    assistant =
        new MockAssistant(
            tools,
            mock(CatalogService.class),
            mock(PurchasingService.class),
            mock(ApprovalRepository.class));
  }

  private static Map<String, Object> rec(String sku, String warehouse, String kind, String title) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("sku", sku);
    m.put("producto", "Producto " + sku);
    m.put("bodega", warehouse);
    m.put("accion", kind);
    m.put("titulo", title);
    m.put("razon", "Porque sí, con datos.");
    return m;
  }

  @Test
  void inventoryQuestionsAreNotAnsweredWithSales() {
    // «inventario» contiene «venta»: antes caía en el resumen de ventas.
    when(tools.listRecommendations(null, null))
        .thenReturn(
            List.of(
                rec("ABA-007", "CAL-01", "buy", "Comprar 240 und a Granos del Valle"),
                rec("SNK-002", "BOG-01", "excess", "No reponer: sobran 300 und")));

    String text = assistant.reply("¿Hay exceso de inventario?").text();

    verify(tools, never()).queryKpis(any(), any(), any());
    assertThat(text).contains("SNK-002").contains("No reponer: sobran 300 und");
    assertThat(text).doesNotContain("ABA-007");
  }

  @Test
  void salesQuestionsStillUseTheKpis() {
    when(tools.queryKpis(null, null, null)).thenReturn(Map.of("ventas_periodo", "$ 20.918.970"));

    assistant.reply("¿Cómo van las ventas del mes?");

    verify(tools).queryKpis(null, null, null);
  }

  @Test
  void askingForRecommendationsListsWhatToReplenishButNotTheExcess() {
    when(tools.listRecommendations(null, null))
        .thenReturn(
            List.of(
                rec("CPE-006", "BOG-01", "transfer", "Trasladar 120 und desde CAL-01"),
                rec("ABA-007", "CAL-01", "buy", "Comprar 240 und a Granos del Valle"),
                rec("SNK-002", "BOG-01", "excess", "No reponer: sobran 300 und")));

    String text = assistant.reply("¿Qué me recomiendas reponer?").text();

    assertThat(text)
        .contains("Trasladar 120 und desde CAL-01")
        .contains("Comprar 240 und a Granos del Valle")
        .doesNotContain("SNK-002");
  }

  @Test
  void askingAboutOneSkuInOneWarehouseFiltersAndExplains() {
    when(tools.listRecommendations("ABA-001", "CAL-01"))
        .thenReturn(List.of(rec("ABA-001", "CAL-01", "buy", "Comprar 96 und a Granos del Valle")));

    String text = assistant.reply("¿Qué recomiendas con ABA-001 en CAL-01?").text();

    verify(tools).listRecommendations("ABA-001", "CAL-01");
    assertThat(text).contains("Comprar 96 und").contains("Porque sí, con datos.");
  }

  @Test
  void aSkuWithNothingToDoSaysSo() {
    String text = assistant.reply("¿Qué recomiendas con ABA-002 en Bogotá?").text();

    verify(tools).listRecommendations("ABA-002", "BOG-01");
    assertThat(text).contains("no hay nada que hacer");
  }

  @Test
  void keywordsMatchAtTheStartOfAWordOnly() {
    String q = MockAssistant.normalize("¿Cómo está el inventario?");
    assertThat(MockAssistant.containsAny(q, "venta")).isFalse();
    assertThat(MockAssistant.containsAny(q, "inventario")).isTrue();
    assertThat(MockAssistant.containsAny(MockAssistant.normalize("¿Qué ventas hubo?"), "venta"))
        .isTrue();
  }
}
