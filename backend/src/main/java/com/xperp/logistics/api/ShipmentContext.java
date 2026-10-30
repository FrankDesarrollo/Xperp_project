package com.xperp.logistics.api;

/** Datos de un pedido empacado que la IA usa para elegir transportadora. */
public record ShipmentContext(
    long salesOrderId,
    String salesOrderNumber,
    String originCity,
    String destinationCity,
    double weightKg) {}
