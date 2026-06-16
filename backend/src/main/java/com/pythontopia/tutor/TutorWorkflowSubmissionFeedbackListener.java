package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.tutor.dto.TutorWorkflowAgentStateDto;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 将后续正式提交作为环境反馈写回导学 agent state。
 *
 * <p>这里不重新调 graph，也不主动生成新卡；只是让下一次学生点击导学动作时，manager agent 能读到
 * 上一次提示之后的真实 AC/WA 结果。
 */
@Component
public class TutorWorkflowSubmissionFeedbackListener {

  private static final Logger log =
      LoggerFactory.getLogger(TutorWorkflowSubmissionFeedbackListener.class);

  private final TutorWorkflowRepository repository;
  private final ObjectMapper objectMapper;

  public TutorWorkflowSubmissionFeedbackListener(
      TutorWorkflowRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @EventListener
  public void onPracticeSucceeded(LearningEvent.PracticeSucceeded event) {
    observeSubmission(event.userId(), event.problemId(), "AC", true);
  }

  @EventListener
  public void onPracticeFailed(LearningEvent.PracticeFailed event) {
    String verdict = event.failureKind() == null || event.failureKind().isBlank()
        ? "FAILED"
        : event.failureKind();
    observeSubmission(event.userId(), event.problemId(), verdict, false);
  }

  private void observeSubmission(Long userId, Long problemId, String verdict, boolean accepted) {
    try {
      Optional<TutorWorkflowSessionDto> maybeSession = repository.findActive(userId, problemId);
      if (maybeSession.isEmpty()) {
        return;
      }
      TutorWorkflowSessionDto session = maybeSession.get();
      TutorWorkflowAgentStateDto agentState =
          repository
              .findAgentState(session.id())
              .orElse(new TutorWorkflowAgentStateDto(session.id(), "{}", "{}"));
      ObjectNode state = readState(agentState.stateJson());
      int previousAttempts = state.path("attempt_count").asInt(0);
      int previousErrors = state.path("consecutive_errors").asInt(0);
      int consecutiveErrors = accepted ? 0 : previousErrors + 1;

      state.put("session_id", session.sessionKey());
      state.put("user_id", userId);
      state.put("problem_id", problemId);
      if (!state.hasNonNull("phase")) {
        state.put("phase", session.phase());
      }
      state.put("attempt_count", previousAttempts + 1);
      state.put("latest_verdict", verdict);
      state.put("consecutive_errors", consecutiveErrors);
      state.put("frustration_level", frustrationLevel(consecutiveErrors));
      if (state.hasNonNull("last_action") && !state.path("last_action").asText().isBlank()) {
        state.put("last_action_effective", accepted);
      }
      repository.upsertAgentState(
          session.id(), objectMapper.writeValueAsString(state), safeDecision(agentState));
      repository.insertActionEvent(
          session.id(),
          session.seq(),
          "SUBMISSION_OBSERVED",
          verdict,
          objectMapper.writeValueAsString(submissionPayload(verdict, accepted)));
    } catch (Exception e) {
      log.warn("Failed to update tutor workflow coach state from submission", e);
    }
  }

  private ObjectNode readState(String stateJson) {
    try {
      JsonNode node = objectMapper.readTree(stateJson == null || stateJson.isBlank() ? "{}" : stateJson);
      if (node != null && node.isObject()) {
        return (ObjectNode) node;
      }
    } catch (Exception e) {
      log.warn("Invalid tutor workflow agent state JSON; rebuilding minimal state", e);
    }
    return objectMapper.createObjectNode();
  }

  private String safeDecision(TutorWorkflowAgentStateDto agentState) {
    String decisionJson = agentState.decisionJson();
    return decisionJson == null || decisionJson.isBlank() ? "{}" : decisionJson;
  }

  private ObjectNode submissionPayload(String verdict, boolean accepted) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("verdict", verdict);
    payload.put("accepted", accepted);
    return payload;
  }

  private String frustrationLevel(int consecutiveErrors) {
    if (consecutiveErrors >= 5) {
      return "severe";
    }
    if (consecutiveErrors >= 3) {
      return "moderate";
    }
    return "low";
  }
}
