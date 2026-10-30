package com.xperp.sales.api;

import java.time.LocalDate;

public record DailyDemand(long productId, long warehouseId, LocalDate date, int quantity) {}
