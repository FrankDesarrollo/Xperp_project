package com.xperp.simulation.clock;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Estado de la simulación en {@code simulation_state} (AGENTS.md, 10.1): si se reinicia el backend,
 * la simulación sigue donde iba. Los trabajos diarios ya hechos quedan en {@code
 * simulation_job_runs} para no repetirlos.
 */
@Component
public class SimulationStateStore {

  public record State(
      long companyId, Instant simTime, String status, int secondsPerDay, int scenarioCursor) {}

  private final JdbcTemplate jdbc;

  SimulationStateStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void create(long companyId, Instant simTime, int secondsPerDay) {
    jdbc.update(
        """
        insert into simulation_state (company_id, sim_time, status, seconds_per_day, scenario_cursor)
        values (?, ?, 'paused', ?, 0)
        on conflict (company_id) do update set sim_time = excluded.sim_time, status = 'paused',
          seconds_per_day = excluded.seconds_per_day, scenario_cursor = 0, updated_at = now()
        """,
        companyId,
        Timestamp.from(simTime),
        secondsPerDay);
  }

  public Optional<State> load(long companyId) {
    return jdbc
        .query(
            "select company_id, sim_time, status, seconds_per_day, scenario_cursor from simulation_state where company_id = ?",
            (rs, i) ->
                new State(
                    rs.getLong("company_id"),
                    rs.getTimestamp("sim_time").toInstant(),
                    rs.getString("status"),
                    rs.getInt("seconds_per_day"),
                    rs.getInt("scenario_cursor")),
            companyId)
        .stream()
        .findFirst();
  }

  public void save(long companyId, Instant simTime, String status, int secondsPerDay) {
    jdbc.update(
        "update simulation_state set sim_time = ?, status = ?, seconds_per_day = ?, updated_at = now() where company_id = ?",
        Timestamp.from(simTime),
        status,
        secondsPerDay,
        companyId);
  }

  public void saveCursor(long companyId, int cursor) {
    jdbc.update(
        "update simulation_state set scenario_cursor = ?, updated_at = now() where company_id = ?",
        cursor,
        companyId);
  }

  /** Marca un trabajo diario; devuelve false si ya se había hecho. */
  public boolean claimJob(long companyId, String job, LocalDate day) {
    return jdbc.update(
            "insert into simulation_job_runs (company_id, job, sim_date) values (?, ?, ?) on conflict do nothing",
            companyId,
            job,
            Date.valueOf(day))
        == 1;
  }
}
