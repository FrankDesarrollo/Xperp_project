package com.xperp.shared.time;

import java.time.Clock;
import java.time.Instant;

/**
 * Acceso estático al reloj para los callbacks de JPA ({@code @PrePersist}), que no admiten
 * inyección. El resto del código inyecta el bean {@link Clock}.
 */
public final class ClockHolder {

  private static volatile Clock clock = Clock.systemUTC();

  private ClockHolder() {}

  static void set(Clock value) {
    clock = value;
  }

  public static Instant now() {
    return clock.instant();
  }
}
