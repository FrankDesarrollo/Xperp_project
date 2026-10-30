package com.xperp.shared.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Acceso al usuario de la sesión desde cualquier módulo. Vacío en tareas del sistema o la IA. */
public final class CurrentUser {

  private CurrentUser() {}

  public static Optional<AuthenticatedUser> get() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
      return Optional.empty();
    }
    Number warehouse = jwt.getClaim("warehouse_id");
    return Optional.of(
        new AuthenticatedUser(
            Long.parseLong(jwt.getSubject()),
            ((Number) jwt.getClaim("company_id")).longValue(),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("name"),
            jwt.getClaimAsString("role"),
            warehouse == null ? null : warehouse.longValue()));
  }

  public static AuthenticatedUser require() {
    return get().orElseThrow(() -> new IllegalStateException("No hay usuario en la sesión"));
  }

  /** Id del usuario de la sesión, o null si la acción la hace el sistema o la IA. */
  public static Long idOrNull() {
    return get().map(AuthenticatedUser::id).orElse(null);
  }

  /** Nombre para registros (kardex, recepciones): el usuario o "Xperp IA". */
  public static String nameOr(String fallback) {
    return get().map(AuthenticatedUser::fullName).orElse(fallback);
  }
}
