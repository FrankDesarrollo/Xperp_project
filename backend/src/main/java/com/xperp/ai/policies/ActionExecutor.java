package com.xperp.ai.policies;

import java.util.List;
import java.util.Map;

/**
 * Ejecuta un tipo de acción llamando los servicios públicos de los módulos. Nunca toca repositorios
 * ni SQL de otros módulos (AGENTS.md, regla 5).
 */
public interface ActionExecutor {

  String actionType();

  /** Resultado de ejecutar o preparar una acción. */
  record Result(String message, List<ActionProposal.Ref> refs, Map<String, Object> resultRef) {

    public static Result of(String message) {
      return new Result(message, List.of(), Map.of());
    }
  }

  /** Validación de negocio previa: proveedor activo, pedido mínimo, duplicados... */
  default void validate(Map<String, Object> payload) {}

  /** Ejecuta la acción de inmediato (nivel 2 o 3). */
  Result execute(long decisionId, Map<String, Object> payload);

  /**
   * Prepara la acción mientras espera aprobación (nivel 1). Por ejemplo, la OC queda creada en
   * {@code pending_approval} para que se vea en Compras.
   */
  default Result prepare(long decisionId, Map<String, Object> payload) {
    return Result.of("Pendiente de aprobación");
  }

  /** Ejecuta lo que se aprobó. {@code resultRef} es lo que devolvió {@link #prepare}. */
  default Result executeApproved(
      long decisionId, Map<String, Object> payload, Map<String, Object> resultRef) {
    return execute(decisionId, payload);
  }

  /** Deshace lo preparado cuando la aprobación se rechaza o vence. */
  default void discard(
      long decisionId, Map<String, Object> payload, Map<String, Object> resultRef) {}

  /** ¿Se puede revertir todavía? */
  default boolean reversible(Map<String, Object> payload, Map<String, Object> resultRef) {
    return false;
  }

  default String revert(
      long decisionId, Map<String, Object> payload, Map<String, Object> resultRef) {
    throw new UnsupportedOperationException("La acción no es reversible");
  }
}
