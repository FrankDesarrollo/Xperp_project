package com.xperp.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reloj simulado de la aplicación (AGENTS.md, sección 10.1). Es el único bean {@link Clock}: todo
 * el código pide la hora con {@code Instant.now(clock)} o {@code LocalDate.now(clock)}.
 *
 * <p>El módulo {@code simulation} lo avanza con su ticker y lo persiste en {@code
 * simulation_state}. Aquí solo se guarda el instante actual en memoria.
 */
public class SimClock extends Clock {

  private final ZoneId zone;
  private final AtomicReference<Instant> now;

  public SimClock(ZoneId zone, Instant initial) {
    this.zone = zone;
    this.now = new AtomicReference<>(initial);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return Clock.fixed(instant(), zone);
  }

  @Override
  public Instant instant() {
    return now.get();
  }

  /** Fija el instante simulado. Solo lo usa el módulo de simulación. */
  public void set(Instant instant) {
    now.set(instant);
  }
}
