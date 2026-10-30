package com.xperp.partners.events;

import java.time.Instant;

/** Una transportadora reportó un cambio de estado de un envío. */
public record CarrierUpdateReceived(
    long companyId, String trackingNumber, String status, Instant occurredAt) {}
