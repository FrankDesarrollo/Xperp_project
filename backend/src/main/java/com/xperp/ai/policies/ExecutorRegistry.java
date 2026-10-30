package com.xperp.ai.policies;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Ejecutores disponibles por tipo de acción. */
@Component
public class ExecutorRegistry {

  private final Map<String, ActionExecutor> executors = new HashMap<>();

  ExecutorRegistry(List<ActionExecutor> list) {
    for (ActionExecutor e : list) {
      executors.put(e.actionType(), e);
    }
  }

  public ActionExecutor get(String actionType) {
    ActionExecutor executor = executors.get(actionType);
    if (executor == null) {
      throw new IllegalArgumentException("No hay ejecutor para la acción " + actionType);
    }
    return executor;
  }
}
