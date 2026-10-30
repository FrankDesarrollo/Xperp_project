package com.xperp.shared.error;

import com.xperp.shared.contract.model.Error;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Traduce las excepciones a {@code { code, message, details }} (AGENTS.md, sección 7.1). */
@RestControllerAdvice
class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<Error> business(BusinessException ex) {
    return body(ex.status(), ex.code(), ex.getMessage(), ex.details());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Error> invalid(MethodArgumentNotValidException ex) {
    Map<String, Object> fields = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
    return body(HttpStatus.BAD_REQUEST, "validation_error", "Datos inválidos", fields);
  }

  @ExceptionHandler({
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<Error> badRequest(Exception ex) {
    return body(HttpStatus.BAD_REQUEST, "validation_error", "Solicitud inválida", Map.of());
  }

  @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
  ResponseEntity<Error> forbidden(Exception ex) {
    return body(
        HttpStatus.FORBIDDEN, "forbidden", "Tu rol no tiene permiso para esta acción", Map.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Error> conflict(DataIntegrityViolationException ex) {
    log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
    return body(
        HttpStatus.CONFLICT,
        "conflict",
        "La operación choca con datos existentes",
        Map.of("cause", ex.getMostSpecificCause().getMessage()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<Error> notFound(NoResourceFoundException ex) {
    return body(HttpStatus.NOT_FOUND, "not_found", "Recurso no encontrado", Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Error> unexpected(Exception ex) {
    log.error("Error no controlado", ex);
    return body(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal_error",
        "Ocurrió un error inesperado",
        Map.of());
  }

  private static ResponseEntity<Error> body(
      HttpStatus status, String code, String message, Map<String, Object> details) {
    Error error = new Error(code, message);
    if (details != null && !details.isEmpty()) {
      error.setDetails(new LinkedHashMap<>(details));
    }
    return ResponseEntity.status(status).body(error);
  }
}
