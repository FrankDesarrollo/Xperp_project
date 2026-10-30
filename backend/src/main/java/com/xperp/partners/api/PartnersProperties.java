package com.xperp.partners.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** PARTNERS_API_URL, PARTNERS_API_TOKEN y WEBHOOK_SECRET. */
@ConfigurationProperties("xperp.partners")
public record PartnersProperties(String apiUrl, String apiToken, String webhookSecret) {}
