package com.xperp.shared.security;

import com.xperp.shared.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Usuarios de la plataforma (tabla {@code users}, sección 6.1). La usan los módulos para resolver
 * nombres, a quién asignar una tarea o a quién notificar.
 */
@Component
public class UserDirectory {

  public record UserRecord(
      long id,
      long companyId,
      String email,
      String passwordHash,
      String fullName,
      String role,
      Long warehouseId,
      String warehouseCode,
      boolean active) {

    public AuthenticatedUser toAuthenticated() {
      return new AuthenticatedUser(id, companyId, email, fullName, role, warehouseId);
    }
  }

  private static final String SELECT =
      """
      select u.id, u.company_id, u.email, u.password_hash, u.full_name, u.role, u.warehouse_id,
             w.code as warehouse_code, u.is_active
      from users u left join warehouses w on w.id = u.warehouse_id
      """;

  private static final RowMapper<UserRecord> MAPPER =
      (rs, i) ->
          new UserRecord(
              rs.getLong("id"),
              rs.getLong("company_id"),
              rs.getString("email"),
              rs.getString("password_hash"),
              rs.getString("full_name"),
              rs.getString("role"),
              (Long) rs.getObject("warehouse_id", Long.class),
              rs.getString("warehouse_code"),
              rs.getBoolean("is_active"));

  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;
  private final Map<Long, String> names = new ConcurrentHashMap<>();

  UserDirectory(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
  }

  public Optional<UserRecord> byEmail(String email) {
    return jdbc.query(SELECT + " where lower(u.email) = lower(?)", MAPPER, email).stream()
        .findFirst();
  }

  public Optional<UserRecord> byId(long id) {
    return jdbc.query(SELECT + " where u.id = ?", MAPPER, id).stream().findFirst();
  }

  public List<UserRecord> companyUsers() {
    return jdbc.query(
        SELECT + " where u.company_id = ? order by u.id", MAPPER, TenantContext.require());
  }

  public List<UserRecord> byRole(String role) {
    return jdbc.query(
        SELECT + " where u.company_id = ? and u.role = ? and u.is_active order by u.id",
        MAPPER,
        TenantContext.require(),
        role);
  }

  /** Bodeguero asignado a una bodega (el primero activo). */
  public Optional<UserRecord> warehouseKeeper(long warehouseId) {
    return jdbc
        .query(
            SELECT
                + " where u.company_id = ? and u.role = 'warehouse' and u.warehouse_id = ? and"
                + " u.is_active order by u.id",
            MAPPER,
            TenantContext.require(),
            warehouseId)
        .stream()
        .findFirst();
  }

  /** Nombre completo para mostrar; cacheado porque se consulta mucho. */
  public String nameOf(Long id) {
    if (id == null) {
      return null;
    }
    return names.computeIfAbsent(id, k -> byId(k).map(UserRecord::fullName).orElse("—"));
  }

  public boolean passwordMatches(UserRecord user, String raw) {
    return passwordEncoder.matches(raw, user.passwordHash());
  }

  /** Crea un usuario. Lo usa la carga de datos demo. */
  public long create(
      long companyId, String email, String rawPassword, String fullName, String role, Long whId) {
    return jdbc.queryForObject(
        """
        insert into users (company_id, email, password_hash, full_name, role, warehouse_id)
        values (?, ?, ?, ?, ?, ?) returning id
        """,
        Long.class,
        companyId,
        email,
        passwordEncoder.encode(rawPassword),
        fullName,
        role,
        whId);
  }

  public void touchLogin(long id) {
    jdbc.update("update users set last_login_at = now() where id = ?", id);
  }

  public void evict() {
    names.clear();
  }
}
