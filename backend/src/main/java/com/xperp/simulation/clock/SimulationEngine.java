package com.xperp.simulation.clock;

import com.xperp.ai.api.AiFacade;
import com.xperp.shared.config.SimulationSettings;
import com.xperp.shared.sse.SseHub;
import com.xperp.shared.tenancy.TenantContext;
import com.xperp.shared.tenancy.TenantDirectory;
import com.xperp.shared.time.SimClock;
import com.xperp.shared.time.SimTime;
import com.xperp.simulation.actors.VirtualWarehouseWorker;
import com.xperp.simulation.demand.LiveDemandGenerator;
import com.xperp.simulation.events.SimulationDayEnded;
import com.xperp.simulation.events.SimulationDayStarted;
import com.xperp.simulation.scenarios.ScenarioRunner;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reloj de la simulación (AGENTS.md, 10.1). Un ticker de {@code @Scheduled(fixedRate = 1000)}
 * avanza {@code 86400 / SIM_SECONDS_PER_DAY} segundos simulados por cada segundo real. En cada
 * paso: crea los pedidos del intervalo, corre el guion, el bodeguero virtual y los trabajos de la
 * IA a su hora (00:00 inicio del día, 05:00 pronóstico y precios, 06:00 reabastecimiento, 24:00
 * resumen).
 *
 * <p>Los trabajos de la IA corren en un hilo aparte, en orden, para no frenar el reloj.
 */
@Component
public class SimulationEngine {

  private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);

  public static final String RUNNING = "running";
  public static final String PAUSED = "paused";

  private final SimClock clock;
  private final SimulationStateStore store;
  private final SimulationSettings settings;
  private final TenantDirectory tenants;
  private final LiveDemandGenerator demand;
  private final VirtualWarehouseWorker worker;
  private final ScenarioRunner scenarios;
  private final AiFacade ai;
  private final SseHub sse;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate tx;

  private final ExecutorService jobs =
      Executors.newSingleThreadExecutor(Thread.ofPlatform().name("sim-jobs").daemon().factory());
  private final ExecutorService workerPool =
      Executors.newSingleThreadExecutor(Thread.ofPlatform().name("sim-worker").daemon().factory());
  private final AtomicBoolean workerBusy = new AtomicBoolean();
  private final AtomicBoolean stepping = new AtomicBoolean();

  private volatile Long companyId;
  private volatile String status = PAUSED;
  private volatile int secondsPerDay;
  private volatile int scenarioCursor;
  private volatile boolean partnersConfigured;
  private volatile boolean suspended;

  SimulationEngine(
      SimClock clock,
      SimulationStateStore store,
      SimulationSettings settings,
      TenantDirectory tenants,
      LiveDemandGenerator demand,
      VirtualWarehouseWorker worker,
      ScenarioRunner scenarios,
      AiFacade ai,
      SseHub sse,
      ApplicationEventPublisher events,
      PlatformTransactionManager txManager) {
    this.clock = clock;
    this.store = store;
    this.settings = settings;
    this.tenants = tenants;
    this.demand = demand;
    this.worker = worker;
    this.scenarios = scenarios;
    this.ai = ai;
    this.sse = sse;
    this.events = events;
    this.tx = new TransactionTemplate(txManager);
    this.secondsPerDay = settings.secondsPerDay();
  }

  /** Carga el estado guardado de la empresa demo y deja el reloj donde iba. */
  public void reload() {
    companyId = tenants.defaultCompanyId().orElse(null);
    if (companyId == null) {
      return;
    }
    store
        .load(companyId)
        .ifPresent(
            s -> {
              clock.set(s.simTime());
              status = s.status();
              secondsPerDay = s.secondsPerDay();
              scenarioCursor = s.scenarioCursor();
            });
    partnersConfigured = false;
    if (settings.autostart() && PAUSED.equals(status)) {
      start();
    } else if (RUNNING.equals(status)) {
      configurePartnersOnce();
    }
    log.info("Simulación lista en {} ({})", SimTime.display(clock.instant()), status);
  }

  public Long companyId() {
    return companyId;
  }

  public String status() {
    return status;
  }

  public int secondsPerDay() {
    return secondsPerDay;
  }

  public int scenarioCursor() {
    return scenarioCursor;
  }

  public int dayNumber() {
    return (int) ChronoUnit.DAYS.between(settings.startDate(), SimTime.today(clock)) + 1;
  }

  public synchronized void start() {
    if (companyId == null) {
      return;
    }
    status = RUNNING;
    persist();
    configurePartnersOnce();
    publishClock();
  }

  public synchronized void pause() {
    status = PAUSED;
    persist();
    publishClock();
  }

  public synchronized void setSpeed(int seconds) {
    secondsPerDay = Math.max(2, seconds);
    persist();
    publishClock();
  }

  /** Detiene el ticker mientras se reinicia la demo. */
  public void suspend(boolean value) {
    suspended = value;
  }

  private void configurePartnersOnce() {
    if (partnersConfigured || companyId == null) {
      return;
    }
    TenantContext.run(companyId, scenarios::configurePartners);
    partnersConfigured = true;
  }

  @Scheduled(fixedRate = 1000, initialDelay = 3000)
  void tick() {
    if (suspended || companyId == null || !RUNNING.equals(status)) {
      return;
    }
    long seconds = Math.max(1, 86_400L / secondsPerDay);
    step(Duration.ofSeconds(seconds), false);
  }

  /** Avanza {@code hours} horas simuladas de una vez, esperando los trabajos de cada hora. */
  public void advance(int hours) {
    if (companyId == null) {
      return;
    }
    configurePartnersOnce();
    for (int i = 0; i < hours; i++) {
      step(Duration.ofHours(1), true);
    }
  }

  private void step(Duration length, boolean waitJobs) {
    if (!stepping.compareAndSet(false, true)) {
      return;
    }
    try {
      long company = companyId;
      TenantContext.run(
          company,
          () -> {
            Instant from = clock.instant();
            Instant to = from.plus(length);
            demand.generate(from, to);
            clock.set(to);
            scheduleJobs(company, from, to);
            scenarioCursor = scenarios.runDue(scenarioCursor, to);
            store.saveCursor(company, scenarioCursor);
            runWorker(company, to, waitJobs);
            persist();
            publishClock();
          });
      if (waitJobs) {
        drain();
      }
    } catch (RuntimeException e) {
      log.error("Error en el paso de la simulación", e);
    } finally {
      stepping.set(false);
    }
  }

  /** Programa los trabajos cuyas horas cruzó el paso (de ({@code from}, {@code to}]). */
  private void scheduleJobs(long company, Instant from, Instant to) {
    LocalDate firstDay = SimTime.dateOf(from);
    if (from.equals(SimTime.startOfDay(firstDay))) {
      // Arranque justo a medianoche (por ejemplo, el día 1): el inicio del día no se ha corrido.
      job(company, "day_start", firstDay, () -> ai.onDayStarted(firstDay));
    }
    Instant hour = from.truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1));
    while (!hour.isAfter(to)) {
      LocalDate day = SimTime.dateOf(hour);
      int h = hour.atZone(SimTime.ZONE).getHour();
      if (h == 0) {
        LocalDate previous = day.minusDays(1);
        job(
            company,
            "day_end",
            previous,
            () -> {
              ai.onDayEnded(previous);
              publish(new SimulationDayEnded(company, previous, dayNumber() - 1));
            });
        job(
            company,
            "day_start",
            day,
            () -> {
              ai.onDayStarted(day);
              publish(new SimulationDayStarted(company, day, dayNumber()));
            });
      }
      if (h == 5) {
        job(company, "forecast", day, () -> ai.runForecast(day));
        job(company, "prices", day, ai::syncSupplierPrices);
      }
      if (h == 6) {
        job(company, "replenishment", day, () -> ai.runReplenishment(day));
      }
      submit(company, () -> ai.hourly(settings.autoApprove()));
      hour = hour.plus(Duration.ofHours(1));
    }
  }

  private void job(long company, String name, LocalDate day, Runnable action) {
    if (store.claimJob(company, name, day)) {
      submit(
          company,
          () -> {
            long started = System.currentTimeMillis();
            action.run();
            log.info("Trabajo {} del {} en {} ms", name, day, System.currentTimeMillis() - started);
          });
    }
  }

  private void submit(long company, Runnable action) {
    jobs.submit(
        () -> {
          try {
            TenantContext.run(company, action);
          } catch (RuntimeException e) {
            log.error("Falló un trabajo de la simulación", e);
          }
        });
  }

  private void runWorker(long company, Instant now, boolean wait) {
    if (wait) {
      worker.work(now);
      return;
    }
    if (workerBusy.compareAndSet(false, true)) {
      workerPool.submit(
          () -> {
            try {
              TenantContext.run(company, () -> worker.work(now));
            } catch (RuntimeException e) {
              log.warn("Bodeguero virtual: {}", e.getMessage());
            } finally {
              workerBusy.set(false);
            }
          });
    }
  }

  /** Espera a que terminen los trabajos en cola. */
  public void drain() {
    Future<?> marker = jobs.submit(() -> {});
    try {
      marker.get(10, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException | TimeoutException e) {
      log.warn("Los trabajos de la simulación no terminaron: {}", e.getMessage());
    }
  }

  private void publish(Object event) {
    tx.executeWithoutResult(s -> events.publishEvent(event));
  }

  private void persist() {
    if (companyId != null) {
      store.save(companyId, clock.instant(), status, secondsPerDay);
    }
  }

  private void publishClock() {
    if (companyId == null) {
      return;
    }
    TenantContext.run(
        companyId,
        () -> {
          Map<String, Object> data = new LinkedHashMap<>();
          data.put("sim_time", SimTime.toOffset(clock.instant()).toString());
          data.put("status", status);
          data.put("seconds_per_day", secondsPerDay);
          data.put("day_number", dayNumber());
          data.put("ai_mode", ai.aiMode());
          sse.publish("clock.tick", data, SseHub.Audience.ALL, "clock");
        });
  }
}
