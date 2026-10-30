package com.xperp.partners.webhook;

import com.xperp.partners.PartnersProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Exige {@code Authorization: Bearer <PARTNERS_API_TOKEN>} (AGENTS.md, 7.4). */
@Component
class TokenFilter extends OncePerRequestFilter {

  private final byte[] expected;

  TokenFilter(PartnersProperties properties) {
    this.expected = ("Bearer " + properties.apiToken()).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator") || "/".equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null
        || !MessageDigest.isEqual(expected, header.getBytes(StandardCharsets.UTF_8))) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json");
      response.getWriter().write("{\"code\":\"unauthorized\",\"message\":\"Token inválido\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
