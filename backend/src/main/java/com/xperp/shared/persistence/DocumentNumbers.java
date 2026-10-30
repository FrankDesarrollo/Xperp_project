package com.xperp.shared.persistence;

import com.xperp.shared.tenancy.TenantContext;
import com.xperp.shared.time.SimTime;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Consecutivos de documentos por empresa y año: {@code PV-2026-000001}, {@code OC-2026-000001},
 * {@code TR-2026-000001} (AGENTS.md, sección 6). El incremento es atómico en PostgreSQL.
 */
@Component
public class DocumentNumbers {

  public static final String SALES_ORDER = "PV";
  public static final String PURCHASE_ORDER = "OC";
  public static final String STOCK_TRANSFER = "TR";

  private final JdbcTemplate jdbc;
  private final Clock clock;

  DocumentNumbers(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public String next(String prefix) {
    long companyId = TenantContext.require();
    int year = SimTime.today(clock).getYear();
    Long value =
        jdbc.queryForObject(
            """
            insert into document_sequences (company_id, prefix, year, last_value)
            values (?, ?, ?, 1)
            on conflict (company_id, prefix, year)
            do update set last_value = document_sequences.last_value + 1, updated_at = now()
            returning last_value
            """,
            Long.class,
            companyId,
            prefix,
            year);
    return "%s-%d-%06d".formatted(prefix, year, value);
  }
}
