package com.xperp.shared.time;

import com.xperp.shared.config.SimulationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClockConfig {

  /** Arranca en SIM_START_DATE 00:00; el módulo de simulación lo corrige con el estado guardado. */
  @Bean
  SimClock simClock(SimulationSettings settings) {
    SimClock clock = new SimClock(SimTime.ZONE, SimTime.startOfDay(settings.startDate()));
    ClockHolder.set(clock);
    return clock;
  }
}
