package com.xperp.partners.web;

import com.xperp.partners.api.PartnersProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Verifica {@code X-Xperp-Signature: sha256=<hmac>} de los webhooks (AGENTS.md, sección 7.3). Si la
 * firma no coincide, responde 401 sin llegar al controller.
 */
@Component
class WebhookSignatureFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Xperp-Signature";

  private final byte[] secret;

  WebhookSignatureFilter(PartnersProperties properties) {
    this.secret = properties.webhookSecret().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/webhooks/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    byte[] body = request.getInputStream().readAllBytes();
    String header = request.getHeader(HEADER);
    if (header == null || !verify(body, header)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response
          .getWriter()
          .write("{\"code\":\"invalid_signature\",\"message\":\"Firma del webhook inválida\"}");
      return;
    }
    chain.doFilter(new CachedBodyRequest(request, body), response);
  }

  boolean verify(byte[] body, String header) {
    String expected = "sha256=" + hmacHex(secret, body);
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), header.trim().getBytes(StandardCharsets.UTF_8));
  }

  static String hmacHex(byte[] key, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      byte[] digest = mac.doFinal(body);
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo calcular el HMAC", e);
    }
  }

  /** Petición con el cuerpo ya leído, para que Spring lo pueda leer otra vez. */
  private static final class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream in = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public boolean isFinished() {
          return in.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException();
        }

        @Override
        public int read() {
          return in.read();
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }
}
