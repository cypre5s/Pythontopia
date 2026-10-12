package com.pythontopia.world.boss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists one server-judged response per authenticated client submission ID. */
@Service
public class BossAttemptIdempotencyService {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  public BossAttemptIdempotencyService(
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public Claim claim(Long userId, UUID attemptId, BossAttemptRequest request) {
    String requestHash = requestHash(request);
    UUID token = UUID.randomUUID();
    int inserted =
        jdbcTemplate.update(
            "INSERT INTO boss_attempt_idempotency (user_id, attempt_id, request_hash, claim_token)"
                + " VALUES (?, ?, ?, ?) ON CONFLICT (user_id, attempt_id) DO NOTHING",
            userId,
            attemptId,
            requestHash,
            token);
    if (inserted == 1) {
      return new Claim(token, null, requestHash);
    }

    StoredAttempt stored =
        jdbcTemplate.queryForObject(
            "SELECT request_hash, response_payload::text AS response_payload"
                + " FROM boss_attempt_idempotency WHERE user_id = ? AND attempt_id = ? FOR UPDATE",
            (rs, rowNum) ->
                new StoredAttempt(rs.getString("request_hash"), rs.getString("response_payload")),
            userId,
            attemptId);
    if (!MessageDigest.isEqual(
        stored.requestHash().getBytes(StandardCharsets.US_ASCII),
        requestHash.getBytes(StandardCharsets.US_ASCII))) {
      throw conflict("BOSS_ATTEMPT_ID_REUSED", "同一判题请求 ID 不能用于不同提交内容");
    }
    if (stored.responsePayload() != null) {
      return new Claim(null, readResult(stored.responsePayload()), requestHash);
    }

    // The Graph HTTP read timeout is 180 seconds; leave a one-minute recovery margin.
    int reclaimed =
        jdbcTemplate.update(
            "UPDATE boss_attempt_idempotency SET claim_token = ?, updated_at = now()"
                + " WHERE user_id = ? AND attempt_id = ? AND response_payload IS NULL"
                + " AND updated_at < now() - interval '4 minutes'",
            token,
            userId,
            attemptId);
    if (reclaimed == 1) {
      return new Claim(token, null, requestHash);
    }
    throw conflict("BOSS_ATTEMPT_IN_PROGRESS", "判题请求仍在处理中，请稍后重试");
  }

  @Transactional
  public BossAttemptResultDto complete(
      Long userId,
      UUID attemptId,
      Claim claim,
      BossAttemptResultDto result,
      LearningEvent event) {
    if (claim.replay() != null) {
      return claim.replay();
    }
    String responsePayload = writeResult(result);
    int completed =
        jdbcTemplate.update(
            "UPDATE boss_attempt_idempotency SET response_payload = ?::jsonb, updated_at = now()"
                + " WHERE user_id = ? AND attempt_id = ? AND claim_token = ?"
                + " AND response_payload IS NULL",
            responsePayload,
            userId,
            attemptId,
            claim.token());
    if (completed == 1) {
      if (event != null) {
        eventPublisher.publishEvent(event);
      }
      return result;
    }

    return findCompleted(userId, attemptId, claim.requestHash())
        .orElseThrow(
            () -> conflict("BOSS_ATTEMPT_IN_PROGRESS", "判题请求所有权已变更，请稍后重试"));
  }

  @Transactional
  public void release(Long userId, UUID attemptId, Claim claim) {
    if (claim != null && claim.token() != null) {
      jdbcTemplate.update(
          "DELETE FROM boss_attempt_idempotency WHERE user_id = ? AND attempt_id = ?"
              + " AND claim_token = ? AND response_payload IS NULL",
          userId,
          attemptId,
          claim.token());
    }
  }

  private java.util.Optional<BossAttemptResultDto> findCompleted(
      Long userId, UUID attemptId, String requestHash) {
    return jdbcTemplate
        .query(
            "SELECT request_hash, response_payload::text AS response_payload"
                + " FROM boss_attempt_idempotency WHERE user_id = ? AND attempt_id = ?",
            (rs, rowNum) ->
                new StoredAttempt(rs.getString("request_hash"), rs.getString("response_payload")),
            userId,
            attemptId)
        .stream()
        .filter(stored -> stored.responsePayload() != null)
        .filter(
            stored ->
                MessageDigest.isEqual(
                    stored.requestHash().getBytes(StandardCharsets.US_ASCII),
                    requestHash.getBytes(StandardCharsets.US_ASCII)))
        .map(stored -> readResult(stored.responsePayload()))
        .findFirst();
  }

  private String requestHash(BossAttemptRequest request) {
    try {
      byte[] canonical =
          objectMapper.writeValueAsBytes(
              List.of(
                  request.problemId(),
                  request.chapter(),
                  request.stage(),
                  request.code(),
                  request.elapsedSec()));
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("无法计算判题请求摘要", exception);
    }
  }

  private String writeResult(BossAttemptResultDto result) {
    try {
      return objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("无法保存判题结果", exception);
    }
  }

  private BossAttemptResultDto readResult(String payload) {
    try {
      return objectMapper.readValue(payload, BossAttemptResultDto.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("无法读取已保存的判题结果", exception);
    }
  }

  private static BusinessException conflict(String code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }

  public record Claim(UUID token, BossAttemptResultDto replay, String requestHash) {}

  private record StoredAttempt(String requestHash, String responsePayload) {}
}
