package com.xperp.sales.events;

public record SalesOrderCancelled(long companyId, long salesOrderId, String number) {}
