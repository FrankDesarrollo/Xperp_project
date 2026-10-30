package com.xperp.partners.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xperp.partners.PartnersProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Envía webhooks al backend con {@code X-Xperp-Signature: sha256=<hmac del cuerpo con
 * WEBHOOK_SECRET>} (AGENTS.md, 7.3).
 */
@Component
public class WebhookClient {

  private static final Logger log = LoggerFactory.getLogger(WebhookClient.class);

  private final RestClient http;
  private final ObjectMapper mapper;
  private final byte[] secret;

  WebhookClient(PartnersProperties properties, RestClient.Builder builder, ObjectMapper mapper) {
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
    factory.setReadTimeout(Duration.ofSeconds(5));
    this.http = builder.baseUrl(properties.backendUrl()).requestFactory(factory).build();
    this.mapper = mapper;
    this.secret = properties.webhookSecret().getBytes(StandardCharsets.UTF_8);
  }

  /** Envía el webhook; devuelve false si el backend no lo recibió (se reintenta luego). */
  public boolean send(String path, Object body) {
    try {
      byte[] json = mapper.writeValueAsBytes(body);
      http.post()
          .uri("/api/v1/webhooks/" + path)
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Xperp-Signature", "sha256=" + hmac(secret, json))
          .body(json)
          .retrieve()
          .toBodilessEntity();
      return true;
    } catch (RestClientException | JsonProcessingException e) {
      log.warn("No se pudo entregar el webhook {}: {}", path, e.getMessage());
      return false;
    }
  }

  public static String hmac(byte[] key, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      StringBuilder sb = new StringBuilder();
      for (byte b : mac.doFinal(body)) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
