package com.xperp.ai.rules;

/**
 * Qué recomendar para una existencia. Reglas puras: deciden con los números que les pasan y no
 * consultan nada. Usan las mismas condiciones que el ciclo de reabastecimiento ({@link
 * ReorderRules#needsReorder} y el umbral de urgencia del traslado), para que lo que se recomienda
 * coincida con lo que la IA hace.
 */
public final class StockAdvice {

  /** Por debajo de estos días de cobertura, el agente prefiere trasladar antes que comprar. */
  public static final double URGENT_COVER_DAYS = 2;

  public enum Kind {
    /** Comprar al proveedor con mejor puntaje. */
    BUY,
    /** Trasladar desde la bodega que tiene de sobra. */
    TRANSFER,
    /** Ya hay una OC abierta: esperar a que llegue. */
    INCOMING,
    /** La IA ya lo propuso y espera aprobación. */
    PENDING_APPROVAL,
    /** Hay más de lo que cabe en el tope: no reponer. */
    EXCESS
  }

  public enum Priority {
    HIGH,
    MEDIUM,
    LOW
  }

  private StockAdvice() {}

  /** Quiebre o menos de 2 días de cobertura: la misma condición con la que el agente traslada. */
  public static boolean urgent(int available, double daysOfCover) {
    return available <= 0 || daysOfCover < URGENT_COVER_DAYS;
  }

  /**
   * Qué hacer con la existencia, o {@code null} si no necesita nada.
   *
   * @param canTransfer hay otra bodega que puede ceder y el traslado da una cantidad positiva
   */
  public static Kind kind(
      int available,
      int inTransit,
      int reorderPoint,
      int maxStock,
      double daysOfCover,
      boolean hasOpenPurchaseOrder,
      boolean hasPendingDecision,
      boolean canTransfer) {
    if (ReorderRules.needsReorder(available, inTransit, reorderPoint)) {
      if (hasPendingDecision) {
        return Kind.PENDING_APPROVAL;
      }
      if (hasOpenPurchaseOrder) {
        return Kind.INCOMING;
      }
      if (canTransfer && inTransit == 0 && urgent(available, daysOfCover)) {
        return Kind.TRANSFER;
      }
      return Kind.BUY;
    }
    if (maxStock > 0 && available > maxStock) {
      return Kind.EXCESS;
    }
    return null;
  }

  /**
   * Lo que está en quiebre o por agotarse va primero, aunque ya venga una OC; lo que hay que
   * reponer sin urgencia, después; lo que solo hay que esperar y el exceso, al final.
   */
  public static Priority priority(Kind kind, int available, double daysOfCover) {
    if (kind == Kind.EXCESS) {
      return Priority.LOW;
    }
    if (urgent(available, daysOfCover)) {
      return Priority.HIGH;
    }
    return kind == Kind.INCOMING ? Priority.LOW : Priority.MEDIUM;
  }
}
