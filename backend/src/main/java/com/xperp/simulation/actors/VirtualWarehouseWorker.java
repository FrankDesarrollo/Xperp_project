package com.xperp.simulation.actors;

import com.xperp.logistics.api.LogisticsService;
import com.xperp.logistics.api.TaskCompletion;
import com.xperp.logistics.api.TaskDto;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bodeguero virtual (AGENTS.md, 10.2): toma las tareas pendientes y las completa entre 1 y 3 horas
 * simuladas después (la hora de vencimiento la fija logística al crearlas). Si un usuario real abre
 * una tarea, el bodeguero virtual la deja de lado.
 */
@Component
public class VirtualWarehouseWorker {

  private static final Logger log = LoggerFactory.getLogger(VirtualWarehouseWorker.class);

  private final LogisticsService logistics;

  VirtualWarehouseWorker(LogisticsService logistics) {
    this.logistics = logistics;
  }

  public int work(Instant now) {
    int done = 0;
    for (TaskDto task : logistics.dueForVirtualWorker(now, 300)) {
      try {
        logistics.complete(task.id(), TaskCompletion.EMPTY, false);
        done++;
      } catch (RuntimeException e) {
        log.debug("El bodeguero virtual no pudo cerrar la tarea {}: {}", task.id(), e.getMessage());
      }
    }
    return done;
  }
}
