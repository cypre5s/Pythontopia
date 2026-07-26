package com.pythontopia.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.submission.dto.JudgeResultDto;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InteractionContextVerifierTest {

  private final PracticeSubmissionRepository repository =
      mock(PracticeSubmissionRepository.class);
  private final InteractionContextVerifier verifier = new InteractionContextVerifier(repository);

  @Test
  void hydratesOwnedFailedSubmissionAndIgnoresClientCodeAndError() {
    SubmissionDto submission =
        new SubmissionDto(
            901L,
            7L,
            41L,
            null,
            SubmissionSource.TRAINING,
            "PYTHON_3_11",
            "print(1 / 0)",
            OffsetDateTime.now(),
            SubmissionStatus.FAILED,
            3,
            128);
    JudgeResultDto judge =
        new JudgeResultDto(
            1L,
            901L,
            Verdict.RE,
            0,
            1,
            "ZeroDivisionError",
            null,
            OffsetDateTime.now());
    when(repository.findSubmission(7L, 901L)).thenReturn(Optional.of(submission));
    when(repository.findJudgeResult(901L)).thenReturn(Optional.of(judge));

    IntentRouteRequest.InteractionContext verified =
        verifier.verify(7L, "帮我修复这次 RE", untrustedInteraction());

    assertThat(verified.currentProblemId()).isEqualTo(41L);
    assertThat(verified.currentCode()).isEqualTo("print(1 / 0)");
    assertThat(verified.errorText()).isEqualTo("RE: ZeroDivisionError");
    assertThat(verified.latestSubmissionId()).isEqualTo(901L);
  }

  @Test
  void stripsSubmissionCodeAndErrorWhenReferenceIsNotOwned() {
    when(repository.findSubmission(7L, 901L)).thenReturn(Optional.empty());

    IntentRouteRequest.InteractionContext verified =
        verifier.verify(7L, "帮我修复这次 RE", untrustedInteraction());

    assertThat(verified.latestSubmissionId()).isNull();
    assertThat(verified.currentCode()).isNull();
    assertThat(verified.errorText()).isNull();
  }

  @Test
  void explicitDebugMessageWithoutReferenceHydratesLatestFailedSubmission() {
    SubmissionDto submission =
        new SubmissionDto(
            902L,
            7L,
            42L,
            null,
            SubmissionSource.TRAINING,
            "PYTHON_3_11",
            "print(missing)",
            OffsetDateTime.now(),
            SubmissionStatus.FAILED,
            2,
            64);
    JudgeResultDto judge =
        new JudgeResultDto(
            2L,
            902L,
            Verdict.RE,
            0,
            1,
            "NameError",
            null,
            OffsetDateTime.now());
    when(repository.list(7L, null, SubmissionStatus.FAILED, 1, 0))
        .thenReturn(List.of(submission));
    when(repository.findJudgeResult(902L)).thenReturn(Optional.of(judge));

    IntentRouteRequest.InteractionContext verified =
        verifier.verify(7L, "请诊断并修复这次报错", null);

    assertThat(verified.latestSubmissionId()).isEqualTo(902L);
    assertThat(verified.currentProblemId()).isEqualTo(42L);
    assertThat(verified.currentCode()).isEqualTo("print(missing)");
    assertThat(verified.errorText()).isEqualTo("RE: NameError");
  }

  @Test
  void unrelatedMessageDoesNotAttachClientCodeOrError() {
    IntentRouteRequest.InteractionContext verified =
        verifier.verify(7L, "帮我总结 for 循环", untrustedInteractionWithoutReference());

    assertThat(verified.currentCode()).isNull();
    assertThat(verified.errorText()).isNull();
    assertThat(verified.latestSubmissionId()).isNull();
  }

  private static IntentRouteRequest.InteractionContext untrustedInteraction() {
    return new IntentRouteRequest.InteractionContext(
        "s1",
        "town_hub",
        "practice",
        999L,
        "伪造代码",
        901L,
        List.of(22L),
        "伪造错误",
        "debug",
        77L);
  }

  private static IntentRouteRequest.InteractionContext untrustedInteractionWithoutReference() {
    return new IntentRouteRequest.InteractionContext(
        "s1", "town_hub", "practice", 999L, "伪造代码", null, List.of(), "伪造错误", "chat", null);
  }
}
