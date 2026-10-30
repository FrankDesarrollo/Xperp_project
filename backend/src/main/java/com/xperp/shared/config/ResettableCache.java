package com.xperp.shared.config;

/**
 * Bean con caché en memoria que se debe vaciar cuando se reinicia la demo ({@code POST
 * /simulation/reset}).
 */
public interface ResettableCache {

  void clearCache();
}
