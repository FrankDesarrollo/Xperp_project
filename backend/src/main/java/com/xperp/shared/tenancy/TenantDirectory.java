package com.xperp.shared.tenancy;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Consulta las empresas registradas (tabla {@code companies}, sin filtro de tenant). */
@Component
public class TenantDirectory {

  private final JdbcTemplate jdbc;

  TenantDirectory(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Long> activeCompanyIds() {
    return jdbc.queryForList("select id from companies where is_active order by id", Long.class);
  }

  /** Registra una empresa nueva y devuelve su id. */
  public long createCompany(
      String name, String nit, String city, String timezone, String currency) {
    return jdbc.queryForObject(
        "insert into companies (name, nit, city, timezone, currency) values (?, ?, ?, ?, ?) returning id",
        Long.class,
        name,
        nit,
        city,
        timezone,
        currency);
  }

  public boolean empty() {
    Integer n = jdbc.queryForObject("select count(*) from companies", Integer.class);
    return n == null || n == 0;
  }

  /** La empresa demo: la primera activa. La usan los webhooks de los socios simulados. */
  public Optional<Long> defaultCompanyId() {
    return activeCompanyIds().stream().findFirst();
  }
}
