package com.xperp.simulation.events;

import java.time.LocalDate;

/** Empezó un día simulado. */
public record SimulationDayStarted(long companyId, LocalDate day, int dayNumber) {}
