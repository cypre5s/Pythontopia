package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossEvaluateResultDto;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossProblemDto;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

/**
 * Unit-level tests for {@link BossService} with stubbed graph client.
 *
 * <p>R-13 §5 Phase 6 续：覆盖 problem cache + graph evaluate 真路径。
 */
@ExtendWith(MockitoExtension.class)
class BossServiceTest {

  @Mock BossGraphClient graphClient;

  @InjectMocks BossService service;

  private BossInternalProblem sampleInternal(String problemId, int chapter, int stage) {
    return new BossInternalProblem(
        problemId,
        chapter,
        stage,
        2,
        "PY43-KC-001",
        "stmt",
        List.of(),
        "in",
        "out",
        3,
        true,
        true,
        List.of(
            new BossTestCasePayload("1", "1", true),
            new BossTestCasePayload("2", "2", false),
            new BossTestCasePayload("3", "3", false)),
        "print(input())");
  }

  @Test
  void resolvesDefaultKcPerChapter() {
    assertThat(service.resolveKc(1, null)).isEqualTo("PY43-KC-001");
    assertThat(service.resolveKc(4, null)).isEqualTo("PY43-KC-026");
    assertThat(service.resolveKc(7, null)).isEqualTo("PY43-KC-056");
  }

  @Test
  void overrideKcWinsOverChapterDefault() {
    assertThat(service.resolveKc(1, "PY43-KC-099")).isEqualTo("PY43-KC-099");
  }

  @Test
  void returnsGraphProblemAndStripsHiddenFields() {
    BossInternalProblem fromGraph = sampleInternal("ai-1", 1, 1);
    when(graphClient.requestProblem(anyLong(), eq(1), eq(1), eq("PY43-KC-001"), anyInt(), any(), any(), any()))
        .thenReturn(fromGraph);

    BossProblemDto result =
        service.fetchProblem(1, new BossProblemRequest(1, null, 2, null, null, null), 42L);

    assertThat(result.problemId()).isEqualTo("ai-1");
    assertThat(result.aiGenerated()).isTrue();
    assertThat(result.kcCode()).isEqualTo("PY43-KC-001");
    // BossProblemDto 不带 testCases / referenceSolution，确保 hidden 字段不外泄
    assertThat(result.statement()).doesNotContain("print(input())");
  }

  @Test
  void fallsBackToGoldenWhenGraphReturnsNull() {
    when(graphClient.requestProblem(anyLong(), eq(2), eq(1), any(), anyInt(), any(), any(), any())).thenReturn(null);

    BossProblemDto result =
        service.fetchProblem(2, new BossProblemRequest(1, null, 1, null, null, null), 99L);

    assertThat(result.aiGenerated()).isFalse();
    assertThat(result.problemId()).contains("golden-c2-s1");
    assertThat(result.kcCode()).isEqualTo("PY43-KC-006");
  }

  @Test
  void fallsBackToGoldenWhenGraphThrows() {
    when(graphClient.requestProblem(anyLong(), anyInt(), anyInt(), any(), anyInt(), any(), any(), any()))
        .thenThrow(new RestClientException("graph down"));

    BossProblemDto result =
        service.fetchProblem(3, new BossProblemRequest(2, null, 3, null, null, null), 1L);

    assertThat(result.aiGenerated()).isFalse();
    assertThat(result.totalTestCases()).isEqualTo(3);
  }

  @Test
  void buildGoldenProblemPopulatesAllFields() {
    BossProblemDto golden = service.buildGoldenProblem(5, 3, "PY43-KC-036", 3);
    assertThat(golden.chapter()).isEqualTo(5);
    assertThat(golden.stage()).isEqualTo(3);
    assertThat(golden.kcCode()).isEqualTo("PY43-KC-036");
    assertThat(golden.totalTestCases()).isGreaterThan(0);
    assertThat(golden.sampleInput()).isNotBlank();
    assertThat(golden.sampleOutput()).isNotBlank();
    assertThat(golden.hints()).isNotEmpty();
  }

  @Test
  void difficultyDefaultsToMinOfStageAndFive() {
    when(graphClient.requestProblem(anyLong(), eq(4), eq(3), any(), eq(3), any(), any(), any()))
        .thenReturn(null);
    BossProblemDto result =
        service.fetchProblem(4, new BossProblemRequest(3, null, null, null, null, null), 1L);
    assertThat(result.difficulty()).isEqualTo(3);
  }

  // ──────────────────── evaluateAttempt: 真 graph 评测路径 ────────────────────

  @Test
  void evaluateAttemptReturnsPlaceholderWhenProblemNotCached() {
    BossAttemptRequest req =
        new BossAttemptRequest("unknown-id", 1, 1, "print('hello')", 12);
    var result = service.evaluateAttempt(req);
    assertThat(result.problemId()).isEqualTo("unknown-id");
    assertThat(result.passed()).isFalse();
    assertThat(result.feedback()).contains("Pyodide");
    verify(graphClient, never()).evaluateAttempt(any(), any(), any(), anyInt());
  }

  @Test
  void evaluateAttemptCachesProblemAfterFetchAndForwardsToGraph() {
    BossInternalProblem internal = sampleInternal("ai-cache-1", 1, 1);
    when(graphClient.requestProblem(anyLong(), eq(1), eq(1), any(), anyInt(), any(), any(), any()))
        .thenReturn(internal);
    service.fetchProblem(1, new BossProblemRequest(1, null, 2, null, null, null), 42L);

    when(graphClient.evaluateAttempt(
            eq("ai-cache-1"), eq("print(input())"), any(), anyInt()))
        .thenReturn(
            new BossEvaluateResultDto(
                "ai-cache-1", true, 3, 3, null, null, List.of()));

    var result =
        service.evaluateAttempt(new BossAttemptRequest("ai-cache-1", 1, 1, "print(input())", 9));

    assertThat(result.passed()).isTrue();
    assertThat(result.passedTestCases()).isEqualTo(3);
    assertThat(result.score()).isGreaterThanOrEqualTo(80);
    assertThat(result.feedback()).contains("通过");
    verify(graphClient, times(1)).evaluateAttempt(eq("ai-cache-1"), any(), any(), anyInt());
  }

  @Test
  void evaluateAttemptReportsFailureFirstHintFromGraph() {
    BossInternalProblem internal = sampleInternal("ai-cache-2", 2, 1);
    when(graphClient.requestProblem(anyLong(), eq(2), eq(1), any(), anyInt(), any(), any(), any()))
        .thenReturn(internal);
    service.fetchProblem(2, new BossProblemRequest(1, null, 2, null, null, null), 42L);

    when(graphClient.evaluateAttempt(eq("ai-cache-2"), any(), any(), anyInt()))
        .thenReturn(
            new BossEvaluateResultDto(
                "ai-cache-2",
                false,
                1,
                3,
                "用例 2 期望 '2' 实际 '20'",
                null,
                List.of(1, 2)));

    var result =
        service.evaluateAttempt(new BossAttemptRequest("ai-cache-2", 2, 1, "print(input()*10)", 12));

    assertThat(result.passed()).isFalse();
    assertThat(result.passedTestCases()).isEqualTo(1);
    assertThat(result.failureFirstHint()).contains("用例 2");
    assertThat(result.feedback()).contains("未通过");
    assertThat(result.score()).isLessThanOrEqualTo(60);
  }

  @Test
  void evaluateAttemptForwardsRuntimeErrorFromGraph() {
    BossInternalProblem internal = sampleInternal("ai-cache-3", 1, 1);
    when(graphClient.requestProblem(anyLong(), anyInt(), anyInt(), any(), anyInt(), any(), any(), any()))
        .thenReturn(internal);
    service.fetchProblem(1, new BossProblemRequest(1, null, 2, null, null, null), 42L);

    when(graphClient.evaluateAttempt(eq("ai-cache-3"), any(), any(), anyInt()))
        .thenReturn(
            new BossEvaluateResultDto(
                "ai-cache-3",
                false,
                0,
                3,
                "forbidden_imports:socket",
                "forbidden_imports:socket",
                List.of(0, 1, 2)));

    var result =
        service.evaluateAttempt(
            new BossAttemptRequest("ai-cache-3", 1, 1, "import socket\nprint(0)", 1));

    assertThat(result.passed()).isFalse();
    assertThat(result.feedback()).contains("运行错误");
    assertThat(result.feedback()).contains("socket");
  }

  @Test
  void evaluateAttemptFallsBackOnGraphRestClientException() {
    BossInternalProblem internal = sampleInternal("ai-cache-4", 1, 1);
    when(graphClient.requestProblem(anyLong(), anyInt(), anyInt(), any(), anyInt(), any(), any(), any()))
        .thenReturn(internal);
    service.fetchProblem(1, new BossProblemRequest(1, null, 2, null, null, null), 42L);

    when(graphClient.evaluateAttempt(eq("ai-cache-4"), any(), any(), anyInt()))
        .thenThrow(new RestClientException("graph down"));

    var result =
        service.evaluateAttempt(new BossAttemptRequest("ai-cache-4", 1, 1, "print(0)", 1));

    assertThat(result.passed()).isFalse();
    assertThat(result.feedback()).contains("Pyodide");
  }

  @Test
  void evaluateAttemptHandlesGraphReturningNull() {
    BossInternalProblem internal = sampleInternal("ai-cache-5", 1, 1);
    when(graphClient.requestProblem(anyLong(), anyInt(), anyInt(), any(), anyInt(), any(), any(), any()))
        .thenReturn(internal);
    service.fetchProblem(1, new BossProblemRequest(1, null, 2, null, null, null), 42L);

    when(graphClient.evaluateAttempt(eq("ai-cache-5"), any(), any(), anyInt())).thenReturn(null);

    var result =
        service.evaluateAttempt(new BossAttemptRequest("ai-cache-5", 1, 1, "print(0)", 1));

    assertThat(result.passed()).isFalse();
    assertThat(result.feedback()).contains("Pyodide");
  }
}
