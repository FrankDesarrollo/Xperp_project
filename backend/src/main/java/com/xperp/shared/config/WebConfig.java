package com.xperp.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Todas las rutas de los controllers de Xperp cuelgan de {@code /api/v1}. Las interfaces generadas
 * desde el contrato declaran las rutas sin ese prefijo, igual que el contrato (que lo pone en
 * {@code servers}).
 */
@Configuration
class WebConfig implements WebMvcConfigurer {

  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix("/api/v1", HandlerTypePredicate.forBasePackage("com.xperp"));
  }

  @Override
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    // Las conexiones SSE duran lo que quiera el cliente
    configurer.setDefaultTimeout(-1);
  }
}
