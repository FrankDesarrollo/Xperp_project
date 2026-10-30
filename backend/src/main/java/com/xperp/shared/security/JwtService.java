package com.xperp.shared.security;

import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Emite JWT firmados con HS256. La expiración usa la hora real (no la simulada): la sesión dura lo
 * que diga JWT_EXPIRES_MINUTES aunque el reloj de la demo vaya rápido.
 */
@Component
class JwtService {

  private final JwtEncoder encoder;
  private final SecurityProperties properties;

  JwtService(JwtEncoder encoder, SecurityProperties properties) {
    this.encoder = encoder;
    this.properties = properties;
  }

  String issue(AuthenticatedUser user) {
    Instant now = Instant.now(java.time.Clock.systemUTC());
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer("xperp")
            .subject(Long.toString(user.id()))
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofMinutes(properties.jwtExpiresMinutes())))
            .claim("company_id", user.companyId())
            .claim("email", user.email())
            .claim("name", user.fullName())
            .claim("role", user.role());
    if (user.warehouseId() != null) {
      claims.claim("warehouse_id", user.warehouseId());
    }
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }

  long expiresInSeconds() {
    return properties.jwtExpiresMinutes() * 60L;
  }
}
