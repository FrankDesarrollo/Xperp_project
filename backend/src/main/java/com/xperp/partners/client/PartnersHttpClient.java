package com.xperp.partners.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xperp.partners.api.CarrierGateway;
import com.xperp.partners.api.PartnerUnavailableException;
import com.xperp.partners.api.PartnersAdminGateway;
import com.xperp.partners.api.PartnersProperties;
import com.xperp.partners.api.SupplierGateway;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP de partners-mock (contracts/partners.yaml). Se autentica con {@code Authorization:
 * Bearer <PARTNERS_API_TOKEN>}.
 */
@Component
class PartnersHttpClient implements SupplierGateway, CarrierGateway, PartnersAdminGateway {

  private static final Logger log = LoggerFactory.getLogger(PartnersHttpClient.class);

  record OrderRequest(
      @JsonProperty("po_number") String poNumber,
      @JsonProperty("warehouse_code") String warehouseCode,
      List<OrderItem> items) {}

  record OrderItem(String sku, int quantity, @JsonProperty("unit_cost") String unitCost) {}

  record OrderReceipt(@JsonProperty("external_id") String externalId, String status) {}

  record CatalogItem(
      String sku,
      @JsonProperty("unit_cost") String unitCost,
      @JsonProperty("valid_from") OffsetDateTime validFrom) {}

  record QuoteRequest(
      @JsonProperty("origin_city") String originCity,
      @JsonProperty("destination_city") String destinationCity,
      @JsonProperty("weight_kg") double weightKg) {}

  record QuoteResponse(String service, String cost, @JsonProperty("eta_days") int etaDays) {}

  record ShipmentRequest(
      @JsonProperty("sales_order_number") String salesOrderNumber,
      @JsonProperty("origin_city") String originCity,
      @JsonProperty("destination_city") String destinationCity,
      @JsonProperty("weight_kg") double weightKg,
      String service) {}

  record ShipmentReceipt(@JsonProperty("tracking_number") String trackingNumber) {}

  private final RestClient http;

  PartnersHttpClient(PartnersProperties properties, RestClient.Builder builder) {
    HttpClient jdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
    factory.setReadTimeout(Duration.ofSeconds(5));
    this.http =
        builder
            .baseUrl(properties.apiUrl())
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
            .build();
  }

  @Override
  public String sendOrder(
      String supplierCode, String poNumber, String warehouseCode, List<OrderLine> lines) {
    List<OrderItem> items =
        lines.stream()
            .map(l -> new OrderItem(l.sku(), l.quantity(), l.unitCost().toPlainString()))
            .toList();
    try {
      OrderReceipt receipt =
          http.post()
              .uri("/suppliers/{code}/orders", supplierCode)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new OrderRequest(poNumber, warehouseCode, items))
              .retrieve()
              .body(OrderReceipt.class);
      return receipt == null ? null : receipt.externalId();
    } catch (RestClientException e) {
      throw new PartnerUnavailableException("No se pudo enviar la OC a " + supplierCode, e);
    }
  }

  @Override
  public List<CatalogPrice> catalog(String supplierCode) {
    try {
      List<CatalogItem> items =
          http.get()
              .uri("/suppliers/{code}/catalog", supplierCode)
              .retrieve()
              .body(new ParameterizedTypeReference<List<CatalogItem>>() {});
      return items == null
          ? List.of()
          : items.stream()
              .map(
                  i ->
                      new CatalogPrice(
                          i.sku(), new BigDecimal(i.unitCost()), i.validFrom().toInstant()))
              .toList();
    } catch (RestClientException e) {
      throw new PartnerUnavailableException("No se pudo leer el catálogo de " + supplierCode, e);
    }
  }

  @Override
  public Optional<Quote> quote(
      String carrierCode, String originCity, String destinationCity, double weightKg) {
    try {
      QuoteResponse q =
          http.post()
              .uri("/carriers/{code}/quotes", carrierCode)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new QuoteRequest(originCity, destinationCity, weightKg))
              .retrieve()
              .body(QuoteResponse.class);
      return q == null
          ? Optional.empty()
          : Optional.of(new Quote(carrierCode, q.service(), new BigDecimal(q.cost()), q.etaDays()));
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_ENTITY)) {
        return Optional.empty();
      }
      throw new PartnerUnavailableException("No se pudo cotizar con " + carrierCode, e);
    } catch (RestClientException e) {
      throw new PartnerUnavailableException("No se pudo cotizar con " + carrierCode, e);
    }
  }

  @Override
  public String createShipment(
      String carrierCode,
      String salesOrderNumber,
      String originCity,
      String destinationCity,
      double weightKg,
      String service) {
    try {
      ShipmentReceipt receipt =
          http.post()
              .uri("/carriers/{code}/shipments", carrierCode)
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  new ShipmentRequest(
                      salesOrderNumber, originCity, destinationCity, weightKg, service))
              .retrieve()
              .body(ShipmentReceipt.class);
      return receipt == null ? null : receipt.trackingNumber();
    } catch (RestClientException e) {
      throw new PartnerUnavailableException("No se pudo crear el envío con " + carrierCode, e);
    }
  }

  @Override
  public void reset() {
    try {
      http.post().uri("/admin/reset").retrieve().toBodilessEntity();
    } catch (RestClientException e) {
      log.warn("partners-mock no respondió al reinicio: {}", e.getMessage());
    }
  }

  @Override
  public void configureScenarios(List<Map<String, Object>> scenarios) {
    try {
      http.post()
          .uri("/admin/scenarios")
          .contentType(MediaType.APPLICATION_JSON)
          .body(scenarios)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      log.warn("No se pudieron enviar los escenarios a partners-mock: {}", e.getMessage());
    }
  }
}
