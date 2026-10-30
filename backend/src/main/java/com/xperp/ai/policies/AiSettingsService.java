package com.xperp.ai.policies;

import com.fasterxml.jackson.databind.JsonNode;
import com.xperp.ai.api.AiProperties;
import com.xperp.ai.scoring.SupplierScorer;
import com.xperp.shared.config.JsonSupport;
import com.xperp.shared.config.ResettableCache;
import com.xperp.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Lee y guarda los ajustes de la IA en system_settings, con caché por empresa. */
@Service
public class AiSettingsService implements ResettableCache {

  static final String KILL_SWITCH = "kill_switch";
  static final String SHADOW_MODE = "shadow_mode";
  static final String SPEND_CAP = "ai_daily_spend_cap";
  static final String LLM_CALLS = "llm_max_calls_per_day";
  static final String WEIGHTS = "scoring_weights";

  private final JdbcTemplate jdbc;
  private final JsonSupport json;
  private final AiProperties properties;
  private final Map<Long, AiSettings> cache = new ConcurrentHashMap<>();

  AiSettingsService(JdbcTemplate jdbc, JsonSupport json, AiProperties properties) {
    this.jdbc = jdbc;
    this.json = json;
    this.properties = properties;
  }

  public AiSettings current() {
    return cache.computeIfAbsent(TenantContext.require(), this::load);
  }

  private AiSettings load(long companyId) {
    Map<String, JsonNode> values = new HashMap<>();
    jdbc.query(
        "select key, value::text as value from system_settings where company_id = ?",
        rs -> {
          values.put(rs.getString("key"), json.read(rs.getString("value"), JsonNode.class));
        },
        companyId);
    boolean kill = values.containsKey(KILL_SWITCH) && values.get(KILL_SWITCH).asBoolean();
    boolean shadow =
        values.containsKey(SHADOW_MODE)
            ? values.get(SHADOW_MODE).asBoolean()
            : properties.shadowMode();
    BigDecimal cap =
        values.containsKey(SPEND_CAP)
            ? new BigDecimal(values.get(SPEND_CAP).asText())
            : properties.dailySpendCap();
    int calls =
        values.containsKey(LLM_CALLS)
            ? values.get(LLM_CALLS).asInt()
            : properties.maxLlmCallsPerSimDay();
    SupplierScorer.Weights weights = SupplierScorer.Weights.DEFAULT;
    if (values.containsKey(WEIGHTS)) {
      JsonNode w = values.get(WEIGHTS);
      weights =
          new SupplierScorer.Weights(
              w.path("price").asDouble(0.40),
              w.path("lead_time").asDouble(0.25),
              w.path("fulfillment").asDouble(0.25),
              w.path("preferred").asDouble(0.10));
    }
    return new AiSettings(kill, shadow, cap, calls, weights);
  }

  /** Guarda los ajustes y devuelve los vigentes. */
  public AiSettings save(AiSettings s) {
    long companyId = TenantContext.require();
    put(companyId, KILL_SWITCH, s.killSwitch());
    put(companyId, SHADOW_MODE, s.shadowMode());
    put(companyId, SPEND_CAP, s.dailySpendCap().setScale(2, RoundingMode.HALF_UP).toPlainString());
    put(companyId, LLM_CALLS, s.llmMaxCallsPerDay());
    Map<String, Object> w = new LinkedHashMap<>();
    w.put("price", s.weights().price());
    w.put("lead_time", s.weights().leadTime());
    w.put("fulfillment", s.weights().fulfillment());
    w.put("preferred", s.weights().preferred());
    put(companyId, WEIGHTS, w);
    cache.remove(companyId);
    return current();
  }

  /** Valores por defecto al crear la empresa demo. */
  public void initialize() {
    save(
        new AiSettings(
            false,
            properties.shadowMode(),
            properties.dailySpendCap(),
            properties.maxLlmCallsPerSimDay(),
            SupplierScorer.Weights.DEFAULT));
  }

  private void put(long companyId, String key, Object value) {
    jdbc.update(
        """
        insert into system_settings (company_id, key, value) values (?, ?, ?::jsonb)
        on conflict (company_id, key) do update set value = excluded.value, updated_at = now()
        """,
        companyId,
        key,
        json.write(value));
  }

  @Override
  public void clearCache() {
    cache.clear();
  }
}
