package com.xperp.ai.policies;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Lectura tolerante de payloads JSON (los números pueden llegar como Integer, Long o String). */
public final class Payloads {

  private Payloads() {}

  public static long getLong(Map<String, Object> map, String key) {
    Object v = map.get(key);
    if (v instanceof Number n) {
      return n.longValue();
    }
    if (v instanceof String s) {
      return Long.parseLong(s);
    }
    throw new IllegalArgumentException("Falta " + key);
  }

  public static Long getLongOrNull(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    if (v == null) {
      return null;
    }
    return v instanceof Number n ? n.longValue() : Long.parseLong(v.toString());
  }

  public static int getInt(Map<String, Object> map, String key) {
    return (int) getLong(map, key);
  }

  public static Integer getIntOrNull(Map<String, Object> map, String key) {
    Long v = getLongOrNull(map, key);
    return v == null ? null : v.intValue();
  }

  public static double getDouble(Map<String, Object> map, String key) {
    Object v = map.get(key);
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    return Double.parseDouble(String.valueOf(v));
  }

  public static BigDecimal getMoney(Map<String, Object> map, String key) {
    Object v = map.get(key);
    return v == null ? null : new BigDecimal(v.toString());
  }

  public static String getString(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? null : v.toString();
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> getList(Map<String, Object> map, String key) {
    Object v = map.get(key);
    return v == null ? List.of() : (List<Map<String, Object>>) v;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> getMap(Map<String, Object> map, String key) {
    Object v = map.get(key);
    return v == null ? Map.of() : (Map<String, Object>) v;
  }
}
