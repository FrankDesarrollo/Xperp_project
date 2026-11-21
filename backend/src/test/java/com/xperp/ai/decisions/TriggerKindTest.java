package com.xperp.ai.decisions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Qué activó cada análisis (ADR 0004): evento, condición, solicitud o revisión programada. */
class TriggerKindTest {

  @Test
  void eachTriggerHasItsKind() {
    assertThat(TriggerKind.of("sales_order_created")).isEqualTo(TriggerKind.EVENT);
    assertThat(TriggerKind.of("purchase_order_rejected")).isEqualTo(TriggerKind.EVENT);
    assertThat(TriggerKind.of("stock_below_safety")).isEqualTo(TriggerKind.CONDITION);
    assertThat(TriggerKind.of("financial_condition")).isEqualTo(TriggerKind.CONDITION);
    assertThat(TriggerKind.of("daily_cycle")).isEqualTo(TriggerKind.SCHEDULE);
    assertThat(TriggerKind.of("financial_review_weekly")).isEqualTo(TriggerKind.SCHEDULE);
    assertThat(TriggerKind.of("user_request")).isEqualTo(TriggerKind.REQUEST);
  }

  /**
   * Busca en el código los disparadores escritos como texto ({@code .trigger("...")} y {@code
   * review("...")}) y exige que cada uno esté clasificado. Si alguien agrega uno nuevo y no lo
   * clasifica, el tablero lo contaría como evento sin que nadie lo haya decidido.
   */
  @Test
  void everyTriggerWrittenInTheCodeIsClassified() throws IOException {
    Pattern literal = Pattern.compile("(?:\\.trigger|review|checkConditions)\\(\\s*\"([a-z_]+)\"");
    Set<String> found = new TreeSet<>();
    try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/xperp/ai"))) {
      for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        Matcher m = literal.matcher(Files.readString(f));
        while (m.find()) {
          found.add(m.group(1));
        }
      }
    }
    assertThat(found).contains("daily_cycle", "sales_order_packed", "user_request", "month_close");
    assertThat(found).allSatisfy(t -> assertThat(TriggerKind.known(t)).as(t).isTrue());
  }
}
