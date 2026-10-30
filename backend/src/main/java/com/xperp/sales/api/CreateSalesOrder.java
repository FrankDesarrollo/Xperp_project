package com.xperp.sales.api;

import java.util.List;

public record CreateSalesOrder(long customerId, List<SalesLine> lines) {}
