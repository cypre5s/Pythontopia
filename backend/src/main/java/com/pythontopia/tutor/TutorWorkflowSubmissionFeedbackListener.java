package com.pythontopia.tutor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.tutor.dto.TutorWorkflowAgentStateDto;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 将后续正式提交作为环境反馈写回导学 agent state。
 *
 * <p>这里不重新调 graph，也不主动生成新卡；只是让下一次学生点击导学动作时，manager agent 能读到 上一次提示之后的真实 AC/WA 结果。
 */
@Component
public class TutorWorkflowSubmissionFeedbackListener {

  private static final Logger log =
      LoggerFactory.getLogger(TutorWorkflowSubmissionFeedbackListener.class);
  private static final int MAX_CHAT_HISTORY_TURNS = 20;
  private static final int MAX_CHAT_MESSAGE_LENGTH = 2000;
  private static final Pattern IMAGE_DATA_URI =
      Pattern.compile("(?i)data:image/(?:png|jpeg|gif|webp);base64,");

  private final TutorWorkflowRepository repository;
  private final ObjectMapper objectMapper;

  public TutorWorkflowSubmissionFeedbackListener(
      TutorWorkflowRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @EventListener
  public void onPracticeSucceeded(LearningEvent.PracticeSucceeded event) {
    observeSafely(event.userId(), event.problemId(), "AC", true, event.tutorWorkflowSessionKey());
  }

  @EventListener
  public void onPracticeFailed(LearningEvent.PracticeFailed event) {
    String verdict =
        event.failureKind() == null || event.failureKind().isBlank()
            ? "FAILED"
            : event.failureKind();
    observeSafely(
        event.userId(), event.problemId(), verdict, false, event.tutorWorkflowSessionKey());
  }

  private void observeSafely(
      Long userId,
      Long problemId,
      String verdict,
      boolean accepted,
      String tutorWorkflowSessionKey) {
    try {
      if (tutorWorkflowSessionKey != null && !tutorWorkflowSessionKey.isBlank()) {
        repository
            .findActiveTransferSession(userId, tutorWorkflowSessionKey, problemId)
            .ifPresent(
                session -> observeSafely(session, userId, problemId, verdict, accepted, true));
      } else {
        repository
            .findActive(userId, problemId)
            .ifPresent(
                session -> observeSafely(session, userId, problemId, verdict, accepted, false));
      }
    } catch (Exception e) {
      log.warn(
          "Failed to update tutor workflow coach state from submission errorType={}",
          e.getClass().getSimpleName());
    }
  }

  private void observeSafely(
      TutorWorkflowSessionDto session,
      Long userId,
      Long problemId,
      String verdict,
      boolean accepted,
      boolean transferSubmission) {
    try {
      observeSubmission(session, userId, problemId, verdict, accepted, transferSubmission);
    } catch (Exception e) {
      log.warn(
          "Failed to update tutor workflow coach state from submission errorType={}",
          e.getClass().getSimpleName());
    }
  }

  private void observeSubmission(
      TutorWorkflowSessionDto session,
      Long userId,
      Long problemId,
      String verdict,
      boolean accepted,
      boolean transferSubmission)
      throws JsonProcessingException {
    TutorWorkflowAgentStateDto agentState =
        repository
            .findAgentState(session.id())
            .orElse(new TutorWorkflowAgentStateDto(session.id(), "{}", "{}"));
    ObjectNode stored = readState(agentState.stateJson());
    boolean wrapped =
        stored.has("student_state") || stored.has("episode_plan") || stored.has("chat_history");
    ObjectNode state = objectOrEmpty(wrapped ? stored.path("student_state") : stored);
    int previousAttempts = state.path("attempt_count").asInt(0);
    int previousErrors = state.path("consecutive_errors").asInt(0);
    int consecutiveErrors = accepted ? 0 : previousErrors + 1;

    state.put("session_id", session.sessionKey());
    state.put("user_id", userId);
    state.put("problem_id", session.problemId());
    state.put("latest_submission_problem_id", problemId);
    if (!state.hasNonNull("phase")) {
      state.put("phase", session.phase());
    }
    state.put("attempt_count", previousAttempts + 1);
    state.put("latest_verdict", verdict);
    state.put("consecutive_errors", consecutiveErrors);
    state.put("frustration_level", "low");
    if (state.hasNonNull("last_action") && !state.path("last_action").asText().isBlank()) {
      state.put("last_action_effective", accepted);
    }
    ObjectNode checkpoint = objectMapper.createObjectNode();
    checkpoint.set("student_state", state);
    JsonNode episodePlan = stored.path("episode_plan");
    if (episodePlan.isObject()) {
      checkpoint.set(
          "episode_plan",
          advancePlanForSubmission(episodePlan, session, userId, accepted, transferSubmission));
    } else {
      checkpoint.putNull("episode_plan");
    }
    checkpoint.set("chat_history", safeChatHistory(stored.path("chat_history")));
    repository.persistSubmissionFeedback(
        session.id(),
        new TutorWorkflowRepository.SubmissionFeedbackProjection(
            session.seq(),
            verdict,
            objectMapper.writeValueAsString(checkpoint),
            safeDecision(agentState),
            objectMapper.writeValueAsString(submissionPayload(verdict, accepted))));
  }

  private ObjectNode readState(String stateJson) {
    try {
      JsonNode node =
          objectMapper.readTree(stateJson == null || stateJson.isBlank() ? "{}" : stateJson);
      if (node != null && node.isObject()) {
        return (ObjectNode) node;
      }
    } catch (Exception e) {
      log.warn(
          "Invalid tutor workflow agent state JSON; rebuilding minimal state errorType={}",
          e.getClass().getSimpleName());
    }
    return objectMapper.createObjectNode();
  }

  private ObjectNode objectOrEmpty(JsonNode node) {
    return node != null && node.isObject()
        ? (ObjectNode) node.deepCopy()
        : objectMapper.createObjectNode();
  }

  private ObjectNode advancePlanForSubmission(
      JsonNode planNode,
      TutorWorkflowSessionDto session,
      Long userId,
      boolean accepted,
      boolean transferSubmission) {
    ObjectNode plan = objectOrEmpty(planNode);
    if (!"ACTIVE".equals(plan.path("status").asText())
        || !session.sessionKey().equals(plan.path("session_id").asText())
        || !userId.equals(plan.path("user_id").longValue())
        || !session.problemId().equals(plan.path("problem_id").longValue())) {
      return plan;
    }

    JsonNode stepsNode = plan.path("steps");
    if (!stepsNode.isArray()) {
      return plan;
    }
    ArrayNode steps = (ArrayNode) stepsNode;
    String currentStepId = plan.path("current_step_id").asText();
    ObjectNode activeStep = null;
    int activeIndex = -1;
    for (int index = 0; index < steps.size(); index++) {
      JsonNode candidate = steps.get(index);
      if (candidate.isObject()
          && currentStepId.equals(candidate.path("step_id").asText())
          && "ACTIVE".equals(candidate.path("status").asText())) {
        activeStep = (ObjectNode) candidate;
        activeIndex = index;
        break;
      }
    }
    if (activeStep == null || !submissionMatches(activeStep, accepted, transferSubmission)) {
      return plan;
    }

    activeStep.put("status", "DONE");
    int nextIndex = nextPendingStep(steps, activeIndex);
    plan.put("revision", plan.path("revision").asInt() + 1);
    if (nextIndex >= 0) {
      ObjectNode nextStep = (ObjectNode) steps.get(nextIndex);
      nextStep.put("status", "ACTIVE");
      plan.put("current_step_id", nextStep.path("step_id").asText());
    } else {
      plan.put("status", "COMPLETED");
    }
    return plan;
  }

  private boolean submissionMatches(
      ObjectNode activeStep, boolean accepted, boolean transferSubmission) {
    return switch (activeStep.path("expected_evidence").asText()) {
      case "SUBMISSION_OBSERVED" -> true;
      case "AC_SUBMISSION" -> accepted;
      case "TRANSFER_ATTEMPT" -> transferSubmission;
      default -> false;
    };
  }

  private int nextPendingStep(ArrayNode steps, int afterIndex) {
    for (int index = afterIndex + 1; index < steps.size(); index++) {
      JsonNode candidate = steps.get(index);
      if (candidate.isObject() && "PENDING".equals(candidate.path("status").asText())) {
        return dependenciesSatisfied(candidate.path("depends_on"), steps) ? index : -1;
      }
    }
    return -1;
  }

  private boolean dependenciesSatisfied(JsonNode dependencies, ArrayNode steps) {
    if (!dependencies.isArray()) {
      return true;
    }
    for (JsonNode dependency : dependencies) {
      boolean done = false;
      for (JsonNode step : steps) {
        if (dependency.asText().equals(step.path("step_id").asText())
            && "DONE".equals(step.path("status").asText())) {
          done = true;
          break;
        }
      }
      if (!done) {
        return false;
      }
    }
    return true;
  }

  private ArrayNode safeChatHistory(JsonNode history) {
    ArrayNode safe = objectMapper.createArrayNode();
    if (!history.isArray()) {
      return safe;
    }
    List<JsonNode> turns = new ArrayList<>();
    history.forEach(turns::add);
    int start = Math.max(0, turns.size() - MAX_CHAT_HISTORY_TURNS);
    for (JsonNode turn : turns.subList(start, turns.size())) {
      String role = turn.path("role").asText("");
      String content = turn.path("content").asText("");
      if (!("user".equals(role) || "assistant".equals(role))
          || content.isBlank()
          || IMAGE_DATA_URI.matcher(content).find()) {
        continue;
      }
      ObjectNode safeTurn = objectMapper.createObjectNode();
      safeTurn.put("role", role);
      safeTurn.put(
          "content", content.substring(0, Math.min(MAX_CHAT_MESSAGE_LENGTH, content.length())));
      safe.add(safeTurn);
    }
    return safe;
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
}
