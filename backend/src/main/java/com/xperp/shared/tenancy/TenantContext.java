package com.xperp.shared.tenancy;

import java.util.function.Supplier;

/**
 * Empresa activa en el hilo actual. En peticiones HTTP la fija {@link TenantFilter} desde el JWT;
 * en tareas programadas la fija quien las lanza; en los listeners asíncronos se copia del hilo que
 * publicó el evento ({@code TenantTaskDecorator}).
 *
 * <p>Hibernate la lee al abrir la sesión, así que debe fijarse antes de iniciar la transacción.
 */
public final class TenantContext {

  /** Sin empresa: Hibernate no filtra por {@code company_id}. Solo para código de sistema. */
  public static final Long ROOT = 0L;

  private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  public static Long current() {
    return CURRENT.get();
  }

  /** Empresa actual; falla si no hay una fijada. */
  public static long require() {
    Long id = CURRENT.get();
    if (id == null || ROOT.equals(id)) {
      throw new IllegalStateException("No hay empresa en el contexto actual");
    }
    return id;
  }

  public static void set(Long companyId) {
    if (companyId == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(companyId);
    }
  }

  public static void clear() {
    CURRENT.remove();
  }

  /** Ejecuta {@code action} con la empresa indicada y restaura la anterior al terminar. */
  public static void run(long companyId, Runnable action) {
    call(
        companyId,
        () -> {
          action.run();
          return null;
        });
  }

  public static <T> T call(long companyId, Supplier<T> action) {
    Long previous = CURRENT.get();
    CURRENT.set(companyId);
    try {
      return action.get();
    } finally {
      set(previous);
    }
  }
}
