package com.xperp.shared.security;

import com.xperp.shared.contract.api.AuthApi;
import com.xperp.shared.contract.model.Company;
import com.xperp.shared.contract.model.CurrentUser;
import com.xperp.shared.contract.model.LoginRequest;
import com.xperp.shared.contract.model.LoginResponse;
import com.xperp.shared.contract.model.Role;
import com.xperp.shared.contract.model.UserSummary;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.tenancy.TenantContext;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;

/** Login, sesión, usuarios y empresa. */
@RestController
class AuthController implements AuthApi {

  private final UserDirectory users;
  private final JwtService jwt;
  private final JdbcTemplate jdbc;

  AuthController(UserDirectory users, JwtService jwt, JdbcTemplate jdbc) {
    this.users = users;
    this.jwt = jwt;
    this.jdbc = jdbc;
  }

  @Override
  public ResponseEntity<LoginResponse> login(LoginRequest request) {
    UserDirectory.UserRecord user =
        users
            .byEmail(request.getEmail())
            .filter(UserDirectory.UserRecord::active)
            .filter(u -> users.passwordMatches(u, request.getPassword()))
            .orElseThrow(
                () ->
                    new BusinessException(
                        "invalid_credentials",
                        "Correo o contraseña incorrectos",
                        HttpStatus.UNAUTHORIZED));
    users.touchLogin(user.id());
    String token = jwt.issue(user.toAuthenticated());
    return ResponseEntity.ok(
        new LoginResponse(
            token,
            LoginResponse.TokenTypeEnum.BEARER,
            (int) jwt.expiresInSeconds(),
            toCurrentUser(user)));
  }

  @Override
  public ResponseEntity<CurrentUser> getCurrentUser() {
    AuthenticatedUser session = com.xperp.shared.security.CurrentUser.require();
    UserDirectory.UserRecord user =
        users
            .byId(session.id())
            .orElseThrow(() -> BusinessException.notFound("Usuario", session.id()));
    return ResponseEntity.ok(toCurrentUser(user));
  }

  @Override
  public ResponseEntity<List<UserSummary>> listUsers() {
    return ResponseEntity.ok(
        users.companyUsers().stream()
            .map(
                u ->
                    new UserSummary(
                            u.id(), u.email(), u.fullName(), Role.fromValue(u.role()), u.active())
                        .warehouseId(u.warehouseId())
                        .warehouseCode(u.warehouseCode()))
            .toList());
  }

  @Override
  public ResponseEntity<Company> getCompany() {
    long companyId = TenantContext.require();
    Company company =
        jdbc.queryForObject(
            "select id, name, nit, city, timezone, currency from companies where id = ?",
            (rs, i) ->
                new Company(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("nit"),
                    rs.getString("city"),
                    rs.getString("timezone"),
                    rs.getString("currency").trim()),
            companyId);
    return ResponseEntity.ok(company);
  }

  private static CurrentUser toCurrentUser(UserDirectory.UserRecord u) {
    return new CurrentUser(u.id(), u.email(), u.fullName(), Role.fromValue(u.role()), u.companyId())
        .warehouseId(u.warehouseId());
  }
}
