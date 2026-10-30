package com.xperp.logistics.api;

import java.util.Map;

/** Resultado de una tarea: conteo físico o cantidades recibidas por producto. */
public record TaskCompletion(Integer countedQty, Map<Long, Integer> quantities) {

  public static final TaskCompletion EMPTY = new TaskCompletion(null, null);
}
