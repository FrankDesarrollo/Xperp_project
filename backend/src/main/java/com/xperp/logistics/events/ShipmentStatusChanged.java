package com.xperp.logistics.events;

public record ShipmentStatusChanged(
    long companyId, long shipmentId, long salesOrderId, String trackingNumber, String status) {}
