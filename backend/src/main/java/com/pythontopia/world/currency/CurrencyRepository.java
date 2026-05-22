package com.pythontopia.world.currency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Currency balance + transaction ledger writes. All mutations honour the V105 CHECK constraints
 * ({@code coins >= 0}, {@code gems >= 0}) and always append a {@code currency_transaction} row so
 * the review hall has a complete audit trail.
 */
@Repository
public class CurrencyRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public CurrencyRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Optional<PlayerCurrency> findBalance(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT user_id, coins, gems, updated_at
            FROM player_currency
            WHERE user_id = ?
            """,
            this::map,
            userId)
        .stream()
        .findFirst();
  }

  /** Lazily create a default balance row (50 coins, 0 gems) so downstream calls always have one. */
  public PlayerCurrency findOrInit(Long userId) {
    Optional<PlayerCurrency> existing = findBalance(userId);
    if (existing.isPresent()) {
      return existing.get();
    }
    jdbcTemplate.update(
        """
        INSERT INTO player_currency (user_id)
        VALUES (?)
        ON CONFLICT (user_id) DO NOTHING
        """,
        userId);
    return findBalance(userId).orElseThrow();
  }

  /**
   * Credit (delta &gt; 0) or debit (delta &lt; 0) the learner's coin balance, recording the change
   * in {@code currency_transaction}. Throws {@link BusinessException} when the resulting balance
   * would be negative.
   */
  public PlayerCurrency addCoins(Long userId, int delta, String reason, Map<String, Object> metadata) {
    return adjust(userId, delta, 0, reason, metadata);
  }

  /** Convenience wrapper that throws when {@code amount} is not strictly positive. */
  public PlayerCurrency deductCoins(
      Long userId, int amount, String reason, Map<String, Object> metadata) {
    if (amount <= 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "CURRENCY_AMOUNT_INVALID", "Deduct amount must be positive");
    }
    return adjust(userId, -amount, 0, reason, metadata);
  }

  private PlayerCurrency adjust(
      Long userId, int deltaCoins, int deltaGems, String reason, Map<String, Object> metadata) {
    findOrInit(userId);
    int affected =
        jdbcTemplate.update(
            """
            UPDATE player_currency
            SET coins = coins + ?, gems = gems + ?, updated_at = now()
            WHERE user_id = ? AND coins + ? >= 0 AND gems + ? >= 0
            """,
            deltaCoins,
            deltaGems,
            userId,
            deltaCoins,
            deltaGems);
    if (affected == 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "CURRENCY_NOT_ENOUGH", "Insufficient coins or gems for " + reason);
    }
    jdbcTemplate.update(
        """
        INSERT INTO currency_transaction (user_id, delta_coins, delta_gems, reason, metadata)
        VALUES (?, ?, ?, ?, ?::jsonb)
        """,
        userId,
        deltaCoins,
        deltaGems,
        reason,
        writeJson(metadata));
    return findBalance(userId).orElseThrow();
  }

  private PlayerCurrency map(ResultSet rs, int rowNum) throws SQLException {
    return new PlayerCurrency(
        rs.getLong("user_id"),
        rs.getInt("coins"),
        rs.getInt("gems"),
        offset(rs, "updated_at"));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }

  private String writeJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialise currency_transaction metadata", exception);
    }
  }
}
