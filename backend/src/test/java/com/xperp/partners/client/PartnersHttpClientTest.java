package com.xperp.partners.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.xperp.partners.api.CarrierGateway.Quote;
import com.xperp.partners.api.PartnerUnavailableException;
import com.xperp.partners.api.PartnersProperties;
import com.xperp.partners.api.SupplierGateway.CatalogPrice;
import com.xperp.partners.api.SupplierGateway.OrderLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * El cliente de partners-mock contra un servidor WireMock: lo que envía, lo que lee y cómo falla.
 */
class PartnersHttpClientTest {

  private static final String TOKEN = "demo-partners-token";

  // partners-mock (Spring Boot sin server.http2) solo habla HTTP/1.1. WireMock acepta HTTP/2 sin
  // cifrar, el cliente HTTP de Java intenta pasarse a él y los POST con cuerpo terminan en
  // RST_STREAM: se apaga para que el servidor de prueba se comporte como el real.
  @RegisterExtension
  static WireMockExtension partners =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  private PartnersHttpClient client;

  @BeforeEach
  void setUp() {
    client =
        new PartnersHttpClient(
            new PartnersProperties(partners.baseUrl(), TOKEN, "secreto"), RestClient.builder());
  }

  @Test
  void enviaLaOcConElTokenYElCuerpoDelContratoYDevuelveElIdDelProveedor() {
    partners.stubFor(
        post("/suppliers/GDV/orders")
            .withHeader("Authorization", equalTo("Bearer " + TOKEN))
            .withRequestBody(
                equalToJson(
                    """
                    {"po_number": "OC-2026-000123", "warehouse_code": "CAL-01",
                     "items": [{"sku": "ABA-007", "quantity": 1284, "unit_cost": "7800.00"}]}
                    """))
            .willReturn(
                okJson("{\"external_id\": \"GDV-PO-0042\", \"status\": \"received\"}")
                    .withStatus(201)));

    String externalId =
        client.sendOrder(
            "GDV",
            "OC-2026-000123",
            "CAL-01",
            List.of(new OrderLine("ABA-007", 1284, new BigDecimal("7800.00"))));

    assertThat(externalId).isEqualTo("GDV-PO-0042");
  }

  @Test
  void siPartnersRechazaElTokenLaOcFallaComoSocioNoDisponible() {
    partners.stubFor(
        post("/suppliers/GDV/orders")
            .willReturn(
                status(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"code\":\"unauthorized\",\"message\":\"Token inválido\"}")));

    assertThatThrownBy(() -> client.sendOrder("GDV", "OC-1", "CAL-01", List.of()))
        .isInstanceOf(PartnerUnavailableException.class)
        .hasMessageContaining("GDV")
        .hasCauseInstanceOf(HttpClientErrorException.Unauthorized.class);
  }

  @Test
  void siPartnersSeCaeAMitadDeLaRespuestaLaOcFallaComoSocioNoDisponible() {
    partners.stubFor(
        post("/suppliers/GDV/orders")
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThatThrownBy(() -> client.sendOrder("GDV", "OC-1", "CAL-01", List.of()))
        .isInstanceOf(PartnerUnavailableException.class)
        .hasMessageContaining("GDV");
  }

  @Test
  void leeElCatalogoConPreciosExactosYFechaDeVigencia() {
    partners.stubFor(
        get("/suppliers/GDV/catalog")
            .willReturn(
                okJson(
                    """
                    [{"sku": "ABA-007", "unit_cost": "7950.50",
                      "valid_from": "2026-10-10T08:00:00-05:00"}]
                    """)));

    List<CatalogPrice> prices = client.catalog("GDV");

    assertThat(prices)
        .containsExactly(
            new CatalogPrice(
                "ABA-007", new BigDecimal("7950.50"), Instant.parse("2026-10-10T13:00:00Z")));
  }

  @Test
  void cotizaYDevuelveElCostoExacto() {
    partners.stubFor(
        post("/carriers/RVL/quotes")
            .withRequestBody(
                equalToJson(
                    """
                    {"origin_city": "Cali", "destination_city": "Bogotá", "weight_kg": 12.5}
                    """))
            .willReturn(
                okJson("{\"service\": \"express\", \"cost\": \"38500\", \"eta_days\": 2}")));

    Optional<Quote> quote = client.quote("RVL", "Cali", "Bogotá", 12.5);

    assertThat(quote).contains(new Quote("RVL", "express", new BigDecimal("38500"), 2));
  }

  @Test
  void siLaTransportadoraNoCubreLaCiudadNoHayCotizacionYNoEsUnError() {
    partners.stubFor(post("/carriers/RVL/quotes").willReturn(status(422)));

    assertThat(client.quote("RVL", "Cali", "Leticia", 3)).isEmpty();
  }

  @Test
  void siLaTransportadoraFallaAlCotizarEsSocioNoDisponibleNoCiudadSinCobertura() {
    partners.stubFor(post("/carriers/RVL/quotes").willReturn(serverError()));

    assertThatThrownBy(() -> client.quote("RVL", "Cali", "Bogotá", 3))
        .isInstanceOf(PartnerUnavailableException.class)
        .hasCauseInstanceOf(HttpServerErrorException.class);
  }

  @Test
  void creaElEnvioYDevuelveLaGuia() {
    partners.stubFor(
        post("/carriers/RVL/shipments")
            .withRequestBody(
                equalToJson(
                    """
                    {"sales_order_number": "PV-2026-011330", "origin_city": "Cali",
                     "destination_city": "Bogotá", "weight_kg": 12.5, "service": "express"}
                    """))
            .willReturn(okJson("{\"tracking_number\": \"RVL-0000059\"}").withStatus(201)));

    String tracking =
        client.createShipment("RVL", "PV-2026-011330", "Cali", "Bogotá", 12.5, "express");

    assertThat(tracking).isEqualTo("RVL-0000059");
  }

  @Test
  void elReinicioYLosEscenariosNoTumbanLaSimulacionSiPartnersFalla() {
    partners.stubFor(post("/admin/reset").willReturn(serverError()));
    partners.stubFor(post("/admin/scenarios").willReturn(serverError()));

    assertThatCode(
            () -> {
              client.reset();
              client.configureScenarios(List.of(Map.of("day", 5, "type", "delay")));
            })
        .doesNotThrowAnyException();
    partners.verify(
        postRequestedFor(urlEqualTo("/admin/scenarios"))
            .withRequestBody(equalToJson("[{\"day\": 5, \"type\": \"delay\"}]")));
  }
}
