package com.xperp.sales.api;

public record CustomerCommand(
    String code,
    String name,
    String document,
    String segmentCode,
    String city,
    String address,
    long warehouseId) {}
