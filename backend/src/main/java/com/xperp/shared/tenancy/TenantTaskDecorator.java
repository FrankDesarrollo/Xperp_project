package com.xperp.shared.tenancy;

import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * Copia la empresa del hilo que publica un evento al hilo del listener asíncrono. Así la sesión de
 * Hibernate del listener ya abre con la empresa correcta.
 */
@Component
class TenantTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    Long companyId = TenantContext.current();
    return () -> {
      Long previous = TenantContext.current();
      TenantContext.set(companyId);
      try {
        runnable.run();
      } finally {
        TenantContext.set(previous);
      }
    };
  }
}
