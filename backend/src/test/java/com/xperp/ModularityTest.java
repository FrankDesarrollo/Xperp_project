package com.xperp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifica los límites entre módulos: otro módulo solo puede usar los paquetes {@code api} y {@code
 * events}. Si alguien usa clases internas de otro módulo, esta prueba falla.
 */
class ModularityTest {

  private final ApplicationModules modules = ApplicationModules.of(XperpApplication.class);

  @Test
  void verifiesModuleBoundaries() {
    modules.verify();
  }

  @Test
  void writesModuleDocumentation() {
    // Genera diagramas C4/PlantUML de los módulos en target/spring-modulith-docs
    new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
  }
}
