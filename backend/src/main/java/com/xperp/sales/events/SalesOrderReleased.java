package com.xperp.sales.events;

public record SalesOrderReleased(long companyId, long salesOrderId, String number) {}
