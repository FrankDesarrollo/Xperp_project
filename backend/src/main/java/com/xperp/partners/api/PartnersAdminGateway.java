package com.xperp.partners.api;

import java.util.List;
import java.util.Map;

/** Control de partners-mock desde la simulación: reinicio y eventos forzados del guion. */
public interface PartnersAdminGateway {

  void reset();

  void configureScenarios(List<Map<String, Object>> scenarios);
}
