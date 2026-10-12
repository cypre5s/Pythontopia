package com.pythontopia.world.boss;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class EndlessPregenServiceTest {

  @Mock BossGraphClient graphClient;
  @Mock JdbcTemplate jdbcTemplate;
  private EndlessPregenService service;

  @BeforeEach
  void setUp() {
    service = new EndlessPregenService(graphClient, jdbcTemplate, new ObjectMapper());
  }

  @Test
  void reviewedAiProblemMovesPendingJobToReadyAtomically() {
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(2),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenReturn(reviewedProblem());
    when(jdbcTemplate.update(contains("status = 'READY'"), anyString(), eq("ai-1"), eq(19L), eq(1)))
        .thenReturn(1);

    service.generatePrefetch(
        19L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null, 1);

    verify(jdbcTemplate)
        .update(contains("status = 'READY'"), anyString(), eq("ai-1"), eq(19L), eq(1));
  }

  @Test
  void generationFailureIsPersistedAsFailedForRetry() {
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(4),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenThrow(new RestClientException("graph timeout"));

    service.generatePrefetch(
        23L, 7L, 1, 2, "PY43-KC-001", 4, "PY43-KC-001", "dual_track", null, null, 1);

    verify(jdbcTemplate)
        .update(contains("status = 'FAILED'"), eq("graph timeout"), eq(23L), eq(1));
  }

  @Test
  void unreviewedGoldenFallbackNeverBecomesReady() {
    BossInternalProblem golden =
        new BossInternalProblem(
            "golden-1", 1, 2, 2, "PY43-KC-001", "fallback", List.of(), "", "", 1,
            false, false, List.of(new BossTestCasePayload("", "", false)), "print(0)");
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(2),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenReturn(golden);

    service.generatePrefetch(
        29L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null, 1);

    verify(jdbcTemplate)
        .update(
            contains("status = 'FAILED'"),
            eq("generated problem did not pass the reviewed AI contract"),
            eq(29L), eq(1));
  }

  @Test
  void claimingReadyProblemPersistsFreshProblemIdWithPayload() throws Exception {
    BossInternalProblem original = reviewedProblem();
    String payload = new ObjectMapper().writeValueAsString(original);
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
        .thenReturn(List.of(Map.of("id", 19L, "payload", payload)));
    when(jdbcTemplate.update(
            contains("status = 'CONSUMED'"), anyString(), anyString(), org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq(19L)))
        .thenReturn(1);

    var claimed = service.claimReady(7L, 1, "PY43-KC-001", 2, "dual_track", null, null);

    assertThat(claimed).isPresent();
    assertThat(claimed.orElseThrow().problemId()).startsWith("issue-").isNotEqualTo("ai-1");
    org.mockito.ArgumentCaptor<String> savedPayload = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate)
        .update(
            contains("status = 'CONSUMED'"),
            org.mockito.ArgumentMatchers.eq(claimed.orElseThrow().problemId()),
            savedPayload.capture(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq(19L));
    assertThat(new ObjectMapper().readTree(savedPayload.getValue()).path("problemId").asText())
        .isEqualTo(claimed.orElseThrow().problemId());
  }

  @Test
  void rejectsNewPrefetchWhenUserAlreadyHasFourOutstandingProblems() {
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());
    when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Integer.class), eq(7L)))
        .thenReturn(4);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.beginPrefetch(7L, 1, "PY43-KC-001", 2, "dual_track", null))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("队列已满");

    verify(jdbcTemplate).update(contains("status = 'FAILED'"), eq(7L), eq(10));
  }

  @Test
  void staleGenerationCannotOverwriteRetryAfterItsAttemptWasSuperseded() {
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(2),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenReturn(reviewedProblem());
    when(jdbcTemplate.update(contains("status = 'READY'"), anyString(), eq("ai-1"), eq(19L), eq(1)))
        .thenReturn(0);

    service.generatePrefetch(
        19L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null, 1);

    verify(jdbcTemplate).update(contains("attempt_count = ?"), anyString(), eq("ai-1"), eq(19L), eq(1));
  }

  private BossInternalProblem reviewedProblem() {
    return new BossInternalProblem(
        "ai-1", 1, 2, 2, "PY43-KC-001", "statement", List.of(), "2 3", "5", 1,
        true, true, List.of(new BossTestCasePayload("2 3", "5", false)), "print(5)");
  }
}
