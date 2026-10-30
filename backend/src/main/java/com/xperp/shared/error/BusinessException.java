package com.xperp.shared.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Excepción de negocio. Se traduce a {@code { code, message, details }} con el código HTTP indicado
 * (AGENTS.md, sección 13.1).
 */
public class BusinessException extends RuntimeException {

  private final String code;
  private final HttpStatus status;
  private final Map<String, Object> details;

  public BusinessException(String code, String message, HttpStatus status) {
    this(code, message, status, Map.of());
  }

  public BusinessException(
      String code, String message, HttpStatus status, Map<String, Object> details) {
    super(message);
    this.code = code;
    this.status = status;
    this.details = details;
  }

  public static BusinessException notFound(String what, Object id) {
    return new BusinessException(
        "not_found", "%s %s no existe".formatted(what, id), HttpStatus.NOT_FOUND);
  }

  public static BusinessException conflict(String code, String message) {
    return new BusinessException(code, message, HttpStatus.CONFLICT);
  }

  public static BusinessException invalid(String code, String message) {
    return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
  }

  public static BusinessException forbidden(String message) {
    return new BusinessException("forbidden", message, HttpStatus.FORBIDDEN);
  }

  public String code() {
    return code;
  }

  public HttpStatus status() {
    return status;
  }

  public Map<String, Object> details() {
    return details;
  }
}
