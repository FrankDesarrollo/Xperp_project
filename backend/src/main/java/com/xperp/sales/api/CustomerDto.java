package com.xperp.sales.api;

public record CustomerDto(
    long id,
    String code,
    String name,
    String document,
    String segmentCode,
    String segmentName,
    String city,
    String address,
    long warehouseId,
    boolean active) {}
