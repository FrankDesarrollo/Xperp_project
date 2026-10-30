package com.xperp.shared.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/** Toma la empresa del claim {@code company_id} del JWT y la deja en {@link TenantContext}. */
public class TenantFilter extends OncePerRequestFilter {

  private final TenantDirectory directory;

  public TenantFilter(TenantDirectory directory) {
    this.directory = directory;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Long companyId = null;
    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
      Object claim = jwt.getClaim("company_id");
      if (claim instanceof Number n) {
        companyId = n.longValue();
      }
    }
    if (companyId == null && isPartnerCall(request)) {
      // Webhooks y reloj: los socios simulados atienden a la empresa demo (ver ADR 0003).
      companyId = directory.defaultCompanyId().orElse(null);
    }
    TenantContext.set(companyId);
    try {
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private static boolean isPartnerCall(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri.contains("/webhooks/") || uri.endsWith("/simulation/clock");
  }
}
