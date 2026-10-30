package com.xperp.simulation.events;

import java.time.LocalDate;

/** Terminó un día simulado. */
public record SimulationDayEnded(long companyId, LocalDate day, int dayNumber) {}
