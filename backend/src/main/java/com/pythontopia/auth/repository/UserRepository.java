package com.pythontopia.auth.repository;

import com.pythontopia.auth.domain.UserAccount;
import com.pythontopia.auth.security.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

  private final JdbcTemplate jdbcTemplate;

  public UserRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UserAccount save(
      String username, String displayName, String passwordHash, String email, Role role) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  """
                  INSERT INTO users (username, display_name, password_hash, email, role)
                  VALUES (?, ?, ?, ?, ?)
                  """,
                  new String[] {"id"});
          statement.setString(1, username);
          statement.setString(2, displayName);
          statement.setString(3, passwordHash);
          statement.setString(4, email);
          statement.setString(5, role.name());
          return statement;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    return findById(key.longValue()).orElseThrow();
  }

  public Optional<UserAccount> findById(Long id) {
    return jdbcTemplate
        .query(
            """
            SELECT id, username, display_name, password_hash, email, role, status, token_version
            FROM users
            WHERE id = ?
            """,
            this::map,
            id)
        .stream()
        .findFirst();
  }

  public Optional<UserAccount> findByUsername(String username) {
    return jdbcTemplate
        .query(
            """
            SELECT id, username, display_name, password_hash, email, role, status, token_version
            FROM users
            WHERE username = ?
            """,
            this::map,
            username)
        .stream()
        .findFirst();
  }

  public boolean isLoginThrottled(Long userId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT login_blocked_until > now() FROM users WHERE id = ?", Boolean.class, userId));
  }

  public int recordFailedLogin(Long userId) {
    // Keep account denial brief while making repeated failures increasingly expensive.
    return jdbcTemplate.update(
        """
        UPDATE users
        SET login_failed_count = CASE
              WHEN login_failure_window_started_at IS NULL
                OR login_failure_window_started_at < now() - interval '15 minutes' THEN 1
              ELSE LEAST(login_failed_count + 1, 7)
            END,
            login_failure_window_started_at = CASE
              WHEN login_failure_window_started_at IS NULL
                OR login_failure_window_started_at < now() - interval '15 minutes' THEN now()
              ELSE login_failure_window_started_at
            END,
            login_blocked_until = CASE
              WHEN login_failure_window_started_at IS NULL
                OR login_failure_window_started_at < now() - interval '15 minutes' THEN NULL
              WHEN login_failed_count >= 4
                THEN now() + make_interval(secs => POWER(2.0, LEAST(login_failed_count - 4, 3))::integer)
              ELSE NULL
            END,
            updated_at = now()
        WHERE id = ?
        """,
        userId);
  }

  public int clearLoginFailures(Long userId) {
    return jdbcTemplate.update(
        """
        UPDATE users
        SET login_failed_count = 0,
            login_failure_window_started_at = NULL,
            login_blocked_until = NULL,
            updated_at = now()
        WHERE id = ?
        """,
        userId);
  }

  /**
   * 自增会话代次：该用户全部已签发 JWT（携带旧 {@code tv}）随即失效。停用账号、重置密码或怀疑
   * token 泄露时调用；等价的运维手段是 {@code UPDATE users SET token_version = token_version + 1}。
   *
   * @return 受影响行数，0 表示用户不存在
   */
  public int bumpTokenVersion(Long userId) {
    return jdbcTemplate.update(
        """
        UPDATE users
        SET token_version = token_version + 1, updated_at = now()
        WHERE id = ?
        """,
        userId);
  }

  private UserAccount map(ResultSet rs, int rowNum) throws SQLException {
    return new UserAccount(
        rs.getLong("id"),
        rs.getString("username"),
        rs.getString("display_name"),
        rs.getString("password_hash"),
        rs.getString("email"),
        Role.valueOf(rs.getString("role")),
        rs.getString("status"),
        rs.getInt("token_version"));
  }
}
