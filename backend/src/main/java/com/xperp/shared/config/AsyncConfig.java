package com.xperp.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Listeners asíncronos de Spring Modulith ({@code @ApplicationModuleListener}) y tareas programadas
 * atadas al reloj simulado.
 */
@Configuration
@EnableAsync
@EnableScheduling
class AsyncConfig {}
