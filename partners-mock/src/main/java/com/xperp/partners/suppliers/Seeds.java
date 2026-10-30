package com.xperp.partners.suppliers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Semillas reproducibles e independientes por orden. {@code new Random(a + b)} con semillas
 * parecidas da primeros valores correlacionados (y la misma decisión para OC consecutivas); el hash
 * SHA-256 de la llave las separa.
 */
public final class Seeds {

  private Seeds() {}

  public static Random random(long seed, String key) {
    try {
      byte[] h =
          MessageDigest.getInstance("SHA-256")
              .digest((seed + "|" + key).getBytes(StandardCharsets.UTF_8));
      long mixed = 0;
      for (int i = 0; i < 8; i++) {
        mixed = (mixed << 8) | (h[i] & 0xff);
      }
      return new Random(mixed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
