package com.pythontopia.world.boss.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.boss.BossGraphClient;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BossProblemReviewServiceTest {

  @Mock BossProblemReviewRepository repository;
  @Mock BossGraphClient graphClient;
  private BossProblemReviewService service;

  private final CurrentUserPrincipal teacher =
      new CurrentUserPrincipal(10L, "teacher", Role.TEACHER);
  private final CurrentUserPrincipal admin = new CurrentUserPrincipal(11L, "admin", Role.ADMIN);
  private final CurrentUserPrincipal student =
      new CurrentUserPrincipal(12L, "student", Role.STUDENT);

  @BeforeEach
  void setUp() {
    service = new BossProblemReviewService(repository, graphClient);
  }

  @Test
  void studentCannotCreateListOrDecideAndNeverTouchesReviewData() {
    assertForbidden(
        () ->
            service.create(
                student,
                new BossProblemReviewCreateRequest(1, 1, "PY43-KC-001", 2, "debug")));
    assertForbidden(() -> service.list(student, 100));
    assertForbidden(
        () ->
            service.page(
                student,
                0,
                20,
                null,
                null,
                BossProblemReviewSortField.REQUESTED_AT,
                BossProblemReviewSortDirection.DESC));
    assertForbidden(
        () ->
            service.decide(
                student,
                1L,
                new BossProblemReviewDecisionRequest(
                    BossProblemReviewDecision.APPROVED, "ok", 1)));

    verifyNoInteractions(repository, graphClient);
  }

  @Test
  void reviewerCanSearchAndReadAStablePageOfReviewRecords() {
    BossProblemReviewRecord pending =
        record(41L, problem("source-loop", 2, true, true), BossProblemReviewDecision.PENDING, 1);
    when(repository.countMatching(BossProblemReviewDecision.PENDING, "loop")).thenReturn(1L);
    when(repository.listMatching(
            BossProblemReviewDecision.PENDING,
            "loop",
            20,
            20,
            BossProblemReviewSortField.STATEMENT,
            BossProblemReviewSortDirection.ASC))
        .thenReturn(List.of(pending));

    BossProblemReviewPageDto result =
        service.page(
            admin,
            1,
            20,
            BossProblemReviewDecision.PENDING,
            " loop ",
            BossProblemReviewSortField.STATEMENT,
            BossProblemReviewSortDirection.ASC);

    assertThat(result.content()).hasSize(1);
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.size()).isEqualTo(20);
    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.totalPages()).isEqualTo(1);
    verify(repository)
        .listMatching(
            BossProblemReviewDecision.PENDING,
            "loop",
            20,
            20,
            BossProblemReviewSortField.STATEMENT,
            BossProblemReviewSortDirection.ASC);
  }

  @Test
  void teacherGenerationIsPersistedOnlyWhenFullReviewedGraphContractPasses() {
    BossInternalProblem graphProblem = problem("source-1", 2, true, true);
    when(graphClient.requestProblem(
            eq(10L), eq(1), eq(1), eq("PY43-KC-001"), eq(2),
            eq("PY43-KC-001"), eq("debug"), eq(null)))
        .thenReturn(graphProblem);
    BossProblemReviewRecord saved = record(41L, graphProblem, BossProblemReviewDecision.PENDING, 1);
    when(repository.create(10L, "debug", graphProblem)).thenReturn(saved);

    BossProblemReviewDto dto =
        service.create(
            teacher,
            new BossProblemReviewCreateRequest(1, 1, "PY43-KC-001", 2, "debug"));

    assertThat(dto.id()).isEqualTo(41L);
    assertThat(dto.problem().problemId()).isEqualTo("source-1");
    assertThat(dto.decision()).isEqualTo(BossProblemReviewDecision.PENDING);
    verify(repository).create(10L, "debug", graphProblem);
  }

  @Test
  void generationRejectsUnreviewedOrHiddenCaseFreePayload() {
    BossInternalProblem unsafe = problem("unsafe", 2, false, true);
    when(graphClient.requestProblem(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any(), any()))
        .thenReturn(unsafe);

    assertThatThrownBy(
            () ->
                service.create(
                    teacher,
                    new BossProblemReviewCreateRequest(1, 1, null, null, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
              assertThat(ex.code()).isEqualTo("PROBLEM_REVIEW_GENERATION_INVALID");
            });
    verifyNoInteractions(repository);
  }

  @Test
  void generationRejectsKcFromAnotherChapterBeforeCallingGraph() {
    assertThatThrownBy(
            () ->
                service.create(
                    teacher,
                    new BossProblemReviewCreateRequest(1, 1, "PY43-KC-008", 2, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    verifyNoInteractions(repository, graphClient);
  }

  @Test
  void adminCanApproveWithReasonAndOptimisticVersion() {
    BossProblemReviewRecord approved =
        record(41L, problem("source-1", 2, true, true), BossProblemReviewDecision.APPROVED, 2);
    when(repository.decide(
            41L, 1, BossProblemReviewDecision.APPROVED, 11L, "题面与用例已复核"))
        .thenReturn(Optional.of(approved));

    BossProblemReviewDto dto =
        service.decide(
            admin,
            41L,
            new BossProblemReviewDecisionRequest(
                BossProblemReviewDecision.APPROVED, "题面与用例已复核", 1));

    assertThat(dto.decision()).isEqualTo(BossProblemReviewDecision.APPROVED);
    assertThat(dto.version()).isEqualTo(2);
  }

  @Test
  void staleOrAlreadyDecidedReviewReturnsConflict() {
    BossProblemReviewRecord existing =
        record(41L, problem("source-1", 2, true, true), BossProblemReviewDecision.APPROVED, 2);
    when(repository.decide(any(Long.class), any(Integer.class), any(), any(Long.class), any()))
        .thenReturn(Optional.empty());
    when(repository.findById(41L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                service.decide(
                    teacher,
                    41L,
                    new BossProblemReviewDecisionRequest(
                        BossProblemReviewDecision.REJECTED, "重复操作", 1)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT));
  }

  private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  private BossProblemReviewRecord record(
      long id,
      BossInternalProblem problem,
      BossProblemReviewDecision decision,
      int version) {
    boolean decided = decision != BossProblemReviewDecision.PENDING;
    return new BossProblemReviewRecord(
        id,
        43L,
        problem.problemId(),
        "debug",
        problem,
        decision,
        10L,
        "teacher",
        OffsetDateTime.parse("2026-08-02T08:00:00Z"),
        decided ? 11L : null,
        decided ? "admin" : null,
        decided ? OffsetDateTime.parse("2026-08-02T08:01:00Z") : null,
        decided ? "题面与用例已复核" : null,
        version);
  }

  private BossInternalProblem problem(
      String problemId, int difficulty, boolean reviewed, boolean aiGenerated) {
    return new BossInternalProblem(
        problemId,
        1,
        1,
        difficulty,
        "PY43-KC-001",
        "输入两个整数并输出和",
        List.of("读取输入"),
        "2 3",
        "5",
        2,
        reviewed,
        aiGenerated,
        List.of(
            new BossTestCasePayload("2 3", "5", true),
            new BossTestCasePayload("-1 8", "7", false)),
        "a, b = map(int, input().split())\nprint(a + b)");
  }
}
