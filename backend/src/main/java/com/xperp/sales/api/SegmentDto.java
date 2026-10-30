package com.xperp.sales.api;

import java.math.BigDecimal;

public record SegmentDto(String code, String name, BigDecimal discountPct) {}
