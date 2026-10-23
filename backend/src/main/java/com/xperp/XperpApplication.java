package com.xperp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@ConfigurationPropertiesScan
@Modulithic(systemName = "Xperp", sharedModules = "shared")
public class XperpApplication {

  public static void main(String[] args) {
    SpringApplication.run(XperpApplication.class, args);
  }
}
