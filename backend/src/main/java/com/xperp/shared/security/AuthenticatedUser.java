package com.xperp.shared.security;

/** Usuario de la sesión, tal como viene en el JWT. */
public record AuthenticatedUser(
    long id, long companyId, String email, String fullName, String role, Long warehouseId) {

  public boolean hasRole(String... roles) {
    for (String r : roles) {
      if (r.equals(role)) {
        return true;
      }
    }
    return false;
  }

  public boolean isAdmin() {
    return "admin".equals(role);
  }
}
