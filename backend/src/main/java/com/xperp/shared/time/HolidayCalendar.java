package com.xperp.shared.time;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Festivos (tabla {@code holidays}, compartida entre empresas). */
@Component
public class HolidayCalendar {

  private final JdbcTemplate jdbc;
  private volatile Map<LocalDate, String> cache;

  HolidayCalendar(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean isHoliday(LocalDate date) {
    return holidays().containsKey(date);
  }

  public Optional<String> name(LocalDate date) {
    return Optional.ofNullable(holidays().get(date));
  }

  public Map<LocalDate, String> holidays() {
    Map<LocalDate, String> local = cache;
    if (local == null) {
      Map<LocalDate, String> loaded = new ConcurrentHashMap<>();
      jdbc.query(
          "select date, name from holidays",
          rs -> {
            loaded.put(rs.getDate("date").toLocalDate(), rs.getString("name"));
          });
      cache = loaded;
      local = loaded;
    }
    return local;
  }

  public void put(LocalDate date, String name) {
    jdbc.update(
        "insert into holidays (date, name) values (?, ?) on conflict (date) do update set name ="
            + " excluded.name",
        date,
        name);
    cache = null;
  }

  public void evict() {
    cache = null;
  }
}
