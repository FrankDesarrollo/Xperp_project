package com.xperp.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** JWT_SECRET y JWT_EXPIRES_MINUTES. */
@ConfigurationProperties("xperp.security")
public record SecurityProperties(String jwtSecret, @DefaultValue("120") int jwtExpiresMinutes) {}
