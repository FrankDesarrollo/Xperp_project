package com.xperp.shared.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Conversión a y desde JSON para columnas jsonb y payloads de la IA. */
@Component
public class JsonSupport {

  private final ObjectMapper mapper;

  JsonSupport(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public ObjectMapper mapper() {
    return mapper;
  }

  public String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("No se pudo serializar a JSON", e);
    }
  }

  public <T> T read(String json, Class<T> type) {
    try {
      return mapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("JSON inválido", e);
    }
  }

  public <T> T read(String json, TypeReference<T> type) {
    try {
      return mapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("JSON inválido", e);
    }
  }

  /** Convierte un objeto (record, mapa) en un mapa, respetando el snake_case de la API. */
  public Map<String, Object> toMap(Object value) {
    if (value == null) {
      return new LinkedHashMap<>();
    }
    return mapper.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  public List<Map<String, Object>> toList(Object value) {
    if (value == null) {
      return List.of();
    }
    return mapper.convertValue(value, new TypeReference<List<Map<String, Object>>>() {});
  }

  public <T> T convert(Object value, Class<T> type) {
    return mapper.convertValue(value, type);
  }
}
