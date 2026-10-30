package com.xperp.partners.api;

/** partners-mock no respondió o respondió con error. */
public class PartnerUnavailableException extends RuntimeException {

  public PartnerUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
