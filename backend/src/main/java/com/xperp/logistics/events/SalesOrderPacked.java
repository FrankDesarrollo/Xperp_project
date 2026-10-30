package com.xperp.logistics.events;

/** El pedido quedó empacado: la IA elige transportadora. */
public record SalesOrderPacked(long companyId, long salesOrderId, long warehouseId) {}
