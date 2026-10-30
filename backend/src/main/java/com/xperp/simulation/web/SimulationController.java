package com.xperp.simulation.web;

import com.xperp.ai.api.AiFacade;
import com.xperp.shared.contract.api.SimulationApi;
import com.xperp.shared.contract.model.Scenario;
import com.xperp.shared.contract.model.SimulationClock;
import com.xperp.shared.contract.model.SimulationSpeedRequest;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.tenancy.TenantContext;
import com.xperp.shared.time.SimClock;
import com.xperp.shared.time.SimTime;
import com.xperp.simulation.clock.SimulationEngine;
import com.xperp.simulation.scenarios.ScenarioRunner;
import com.xperp.simulation.seed.SimulationBootstrap;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SimulationController implements SimulationApi {

  private final SimulationEngine engine;
  private final SimulationBootstrap bootstrap;
  private final ScenarioRunner scenarios;
  private final AiFacade ai;
  private final SimClock clock;

  SimulationController(
      SimulationEngine engine,
      SimulationBootstrap bootstrap,
      ScenarioRunner scenarios,
      AiFacade ai,
      SimClock clock) {
    this.engine = engine;
    this.bootstrap = bootstrap;
    this.scenarios = scenarios;
    this.ai = ai;
    this.clock = clock;
  }

  @Override
  public ResponseEntity<SimulationClock> getSimulationClock() {
    return ResponseEntity.ok(clockModel());
  }

  @Override
  @PreAuthorize("hasRole('admin')")
  public ResponseEntity<SimulationClock> startSimulation() {
    engine.start();
    return ResponseEntity.ok(clockModel());
  }

  @Override
  @PreAuthorize("hasRole('admin')")
  public ResponseEntity<SimulationClock> pauseSimulation() {
    engine.pause();
    return ResponseEntity.ok(clockModel());
  }

  @Override
  @PreAuthorize("hasRole('admin')")
  public ResponseEntity<SimulationClock> setSimulationSpeed(SimulationSpeedRequest request) {
    engine.setSpeed(request.getSecondsPerDay());
    return ResponseEntity.ok(clockModel());
  }

  @Override
  @PreAuthorize("hasRole('admin')")
  public ResponseEntity<SimulationClock> resetSimulation() {
    bootstrap.reset();
    return ResponseEntity.ok(clockModel());
  }

  @Override
  @PreAuthorize("hasRole('admin')")
  public ResponseEntity<SimulationClock> advanceSimulation(Integer hours) {
    if (SimulationEngine.RUNNING.equals(engine.status())) {
      throw new BusinessException(
          "simulation_running",
          "Pausa la simulación antes de avanzar el reloj",
          HttpStatus.CONFLICT);
    }
    engine.advance(hours);
    return ResponseEntity.ok(clockModel());
  }

  @Override
  public ResponseEntity<List<Scenario>> listScenarios() {
    int cursor = engine.scenarioCursor();
    List<ScenarioRunner.Scenario> list = scenarios.scenarios();
    return ResponseEntity.ok(
        java.util.stream.IntStream.range(0, list.size())
            .mapToObj(
                i -> {
                  ScenarioRunner.Scenario s = list.get(i);
                  return new Scenario(
                      i, s.day(), s.time(), s.type(), s.title(), s.expected(), i < cursor);
                })
            .toList());
  }

  private SimulationClock clockModel() {
    String mode =
        engine.companyId() == null ? "active" : TenantContext.call(engine.companyId(), ai::aiMode);
    return new SimulationClock(
        SimTime.toOffset(clock.instant()),
        engine.status(),
        engine.secondsPerDay(),
        engine.dayNumber(),
        engine.scenarioCursor(),
        scenarios.scenarios().size(),
        mode);
  }
}
