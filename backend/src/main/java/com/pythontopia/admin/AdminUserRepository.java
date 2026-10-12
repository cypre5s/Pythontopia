package com.pythontopia.admin;

import com.pythontopia.auth.security.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUserRepository {

  private static final String FILTERS =
      " WHERE (CAST(? AS text) IS NULL OR position(lower(CAST(? AS text)) in"
          + " lower(concat_ws(' ', id::text, username, display_name, role))) > 0)"
          + " AND (CAST(? AS text) IS NULL OR status = CAST(? AS text))";

  private final JdbcTemplate jdbcTemplate;

  public AdminUserRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public AdminUserPageDto page(
      int page, int size, String query, AdminUserStatus status) {
    String statusValue = status == null ? null : status.name();
    Long total =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM users" + FILTERS,
            Long.class,
            query,
            query,
            statusValue,
            statusValue);
    List<AdminUserDto> content =
        jdbcTemplate.query(
            "SELECT id, username, display_name, role, status FROM users"
                + FILTERS
                + " ORDER BY id DESC LIMIT ? OFFSET ?",
            this::map,
            query,
            query,
            statusValue,
            statusValue,
            size,
            (long) page * size);
    long count = total == null ? 0 : total;
    int totalPages = count == 0 ? 0 : (int) ((count + size - 1) / size);
    return new AdminUserPageDto(content, page, size, count, totalPages);
  }

  public Optional<AdminUserDto> setStatusAndRevokeSessions(
      long userId, AdminUserStatus status) {
    return jdbcTemplate
        .query(
            "UPDATE users SET status = ?,"
                + " token_version = token_version + CASE WHEN status <> ? THEN 1 ELSE 0 END,"
                + " updated_at = now() WHERE id = ?"
                + " RETURNING id, username, display_name, role, status",
            this::map,
            status.name(),
            status.name(),
            userId)
        .stream()
        .findFirst();
  }

  private AdminUserDto map(ResultSet rs, int rowNum) throws SQLException {
    return new AdminUserDto(
        rs.getLong("id"),
        rs.getString("username"),
        rs.getString("display_name"),
        Role.valueOf(rs.getString("role")),
        rs.getString("status"));
  }
}
