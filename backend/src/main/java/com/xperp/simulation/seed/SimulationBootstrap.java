package com.xperp.simulation.seed;

import com.xperp.partners.api.PartnersAdminGateway;
import com.xperp.shared.config.ResettableCache;
import com.xperp.shared.security.UserDirectory;
import com.xperp.shared.tenancy.TenantDirectory;
import com.xperp.shared.time.HolidayCalendar;
import com.xperp.simulation.clock.SimulationEngine;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Al arrancar con el perfil {@code demo} y la base vacía, carga la empresa demo. Además, hace el
 * reinicio completo de {@code POST /simulation/reset}: borra todo y vuelve a cargar.
 */
@Component
public class SimulationBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SimulationBootstrap.class);

  @ConfigurationProperties("xperp.demo")
  public record DemoProperties(@DefaultValue("false") boolean enabled) {}

  private final DemoProperties demo;
  private final TenantDirectory tenants;
  private final DemoDataLoader loader;
  private final SimulationEngine engine;
  private final JdbcTemplate jdbc;
  private final List<ResettableCache> caches;
  private final HolidayCalendar holidays;
  private final UserDirectory users;
  private final PartnersAdminGateway partners;

  SimulationBootstrap(
      DemoProperties demo,
      TenantDirectory tenants,
      DemoDataLoader loader,
      SimulationEngine engine,
      JdbcTemplate jdbc,
      List<ResettableCache> caches,
      HolidayCalendar holidays,
      UserDirectory users,
      PartnersAdminGateway partners) {
    this.demo = demo;
    this.tenants = tenants;
    this.loader = loader;
    this.engine = engine;
    this.jdbc = jdbc;
    this.caches = caches;
    this.holidays = holidays;
    this.users = users;
    this.partners = partners;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (demo.enabled() && tenants.empty()) {
      log.info("Base vacía: cargando la empresa demo");
      partners.reset();
      loader.load();
    }
    engine.reload();
  }

  /** Borra todos los datos y vuelve a cargar la empresa demo. */
  public synchronized void reset() {
    engine.suspend(true);
    try {
      engine.pause();
      engine.drain();
      sleepQuietly(1500);
      List<String> tables =
          jdbc.queryForList(
              "select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'",
              String.class);
      jdbc.execute("truncate table " + String.join(", ", tables) + " restart identity cascade");
      caches.forEach(ResettableCache::clearCache);
      holidays.evict();
      users.evict();
      partners.reset();
      loader.load();
      engine.reload();
      log.info("Demo reiniciada");
    } finally {
      engine.suspend(false);
    }
  }

  private static void sleepQuietly(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
