package com.pythontopia.intent;

import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.submission.dto.JudgeResultDto;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 把客户端提交引用收敛成当前学生的权威失败提交上下文。 */
@Component
public class InteractionContextVerifier {

  private static final Logger log = LoggerFactory.getLogger(InteractionContextVerifier.class);
  private static final Pattern DEBUG_SIGNAL =
      Pattern.compile("(?:报错|诊断|失败提交|这次\\s*(?:WA|RE)|\\b(?:WA|RE)\\b)", Pattern.CASE_INSENSITIVE);

  private final PracticeSubmissionRepository repository;

  public InteractionContextVerifier(PracticeSubmissionRepository repository) {
    this.repository = repository;
  }

  public IntentRouteRequest.InteractionContext verify(
      Long userId, String message, IntentRouteRequest.InteractionContext interaction) {
    Long requestedSubmissionId = interaction == null ? null : interaction.latestSubmissionId();
    Optional<SubmissionDto> submission = Optional.empty();
    if (userId != null && requestedSubmissionId != null) {
      submission =
          repository
              .findSubmission(userId, requestedSubmissionId)
              .filter(candidate -> candidate.status() == SubmissionStatus.FAILED);
    } else if (userId != null && isExplicitDebugRequest(message)) {
      List<SubmissionDto> recent = repository.list(userId, null, SubmissionStatus.FAILED, 1, 0);
      if (!recent.isEmpty()) {
        submission = Optional.of(recent.get(0));
      }
    }
    if (submission.isPresent()) {
      return hydrated(interaction, submission.orElseThrow());
    }
    if (requestedSubmissionId != null) {
      log.warn(
          "interaction submission reference rejected for user {} submission {}",
          userId,
          requestedSubmissionId);
    }
    return sanitized(interaction);
  }

  private IntentRouteRequest.InteractionContext hydrated(
      IntentRouteRequest.InteractionContext source, SubmissionDto submission) {
    String failure =
        repository
            .findJudgeResult(submission.id())
            .map(InteractionContextVerifier::judgeFailureText)
            .orElse("FAILED");
    return new IntentRouteRequest.InteractionContext(
        source == null ? null : source.sessionId(),
        source == null ? null : source.currentScene(),
        source == null ? null : source.currentStage(),
        submission.problemId(),
        submission.code(),
        submission.id(),
        source == null ? List.of() : source.selectedPageIds(),
        failure,
        source == null ? null : source.frontendAction(),
        source == null ? null : source.currentResourceId());
  }

  private static IntentRouteRequest.InteractionContext sanitized(
      IntentRouteRequest.InteractionContext source) {
    if (source == null) {
      return null;
    }
    return new IntentRouteRequest.InteractionContext(
        source.sessionId(),
        source.currentScene(),
        source.currentStage(),
        source.currentProblemId(),
        null,
        null,
        source.selectedPageIds(),
        null,
        source.frontendAction(),
        source.currentResourceId());
  }

  private static boolean isExplicitDebugRequest(String message) {
    return message != null && DEBUG_SIGNAL.matcher(message).find();
  }

  private static String judgeFailureText(JudgeResultDto judge) {
    String reason = judge.failureReason();
    return reason == null || reason.isBlank()
        ? judge.verdict().name()
        : judge.verdict().name() + ": " + reason;
  }
}
