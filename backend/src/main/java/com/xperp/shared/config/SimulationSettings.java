package com.xperp.shared.config;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros de la simulación (variables SIM_* de .env.example). Viven en shared porque los usan el
 * reloj, la carga de datos demo y la simulación.
 */
@ConfigurationProperties("xperp.simulation")
public record SimulationSettings(
    @DefaultValue("2026-11-01") LocalDate startDate,
    @DefaultValue("20") int secondsPerDay,
    @DefaultValue("90") int historyDays,
    @DefaultValue("42") long randomSeed,
    @DefaultValue("false") boolean autostart,
    @DefaultValue("false") boolean autoApprove) {}
