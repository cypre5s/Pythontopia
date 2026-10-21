package com.pythontopia.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAiConfigRepository {
  private final JdbcTemplate jdbc;

  public AdminAiConfigRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Optional<Stored> find() {
    return jdbc.query("SELECT model, encrypted_api_key, updated_by, updated_at FROM ai_provider_config WHERE singleton_id = 1", this::map)
        .stream().findFirst();
  }

  public Stored save(String model, String encryptedApiKey, long adminId) {
    return jdbc.queryForObject("""
        INSERT INTO ai_provider_config (singleton_id, model, encrypted_api_key, updated_by, updated_at)
        VALUES (1, ?, ?, ?, now())
        ON CONFLICT (singleton_id) DO UPDATE SET model = EXCLUDED.model,
          encrypted_api_key = EXCLUDED.encrypted_api_key, updated_by = EXCLUDED.updated_by,
          updated_at = now()
        RETURNING model, encrypted_api_key, updated_by, updated_at
        """, this::map, model, encryptedApiKey, adminId);
  }

  private Stored map(ResultSet rs, int row) throws SQLException {
    return new Stored(rs.getString("model"), rs.getString("encrypted_api_key"), rs.getLong("updated_by"), rs.getTimestamp("updated_at").toInstant());
  }

  public record Stored(String model, String encryptedApiKey, long updatedBy, Instant updatedAt) {}
}
