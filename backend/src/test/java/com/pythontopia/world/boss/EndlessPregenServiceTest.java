package com.pythontopia.world.boss;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import java.util.List;
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
    when(jdbcTemplate.update(contains("status = 'READY'"), anyString(), eq("ai-1"), eq(19L)))
        .thenReturn(1);

    service.generatePrefetch(
        19L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null);

    verify(jdbcTemplate)
        .update(contains("status = 'READY'"), anyString(), eq("ai-1"), eq(19L));
  }

  @Test
  void generationFailureIsPersistedAsFailedForRetry() {
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(4),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenThrow(new RestClientException("graph timeout"));

    service.generatePrefetch(
        23L, 7L, 1, 2, "PY43-KC-001", 4, "PY43-KC-001", "dual_track", null, null);

    verify(jdbcTemplate)
        .update(contains("status = 'FAILED'"), eq("graph timeout"), eq(23L));
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
        29L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null);

    verify(jdbcTemplate)
        .update(
            contains("status = 'FAILED'"),
            eq("generated problem did not pass the reviewed AI contract"),
            eq(29L));
  }

  private BossInternalProblem reviewedProblem() {
    return new BossInternalProblem(
        "ai-1", 1, 2, 2, "PY43-KC-001", "statement", List.of(), "2 3", "5", 1,
        true, true, List.of(new BossTestCasePayload("2 3", "5", false)), "print(5)");
  }
}
