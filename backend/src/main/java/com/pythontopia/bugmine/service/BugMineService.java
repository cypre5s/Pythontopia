package com.pythontopia.bugmine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.bugmine.dto.BugMineCardDto;
import com.pythontopia.bugmine.dto.BugMineCardType;
import com.pythontopia.bugmine.dto.BugMineSessionDto;
import com.pythontopia.bugmine.dto.BugMineSessionStatus;
import com.pythontopia.bugmine.graph.DebugAgentClient;
import com.pythontopia.bugmine.graph.DebugAgentRequest;
import com.pythontopia.bugmine.graph.DebugAgentResponse;
import com.pythontopia.bugmine.judge.CounterExampleSelector;
import com.pythontopia.bugmine.judge.PythonTraceCollector;
import com.pythontopia.bugmine.repository.BugMineRepository;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.quest.dto.QuestStatus;
import com.pythontopia.submission.dto.SubmissionCreateRequest;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import com.pythontopia.submission.service.SubmissionService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BugMineService {

  private final BugMineRepository repository;
  private final PracticeSubmissionRepository submissionRepository;
  private final PublicProblemRepository problemRepository;
  private final PythonTraceCollector traceCollector;
  private final CounterExampleSelector counterExampleSelector;
  private final DebugAgentClient debugAgentClient;
  private final SubmissionService submissionService;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;
  private final int maxRetries;

  public BugMineService(
      BugMineRepository repository,
      PracticeSubmissionRepository submissionRepository,
      PublicProblemRepository problemRepository,
      PythonTraceCollector traceCollector,
      CounterExampleSelector counterExampleSelector,
      DebugAgentClient debugAgentClient,
      SubmissionService submissionService,
      ApplicationEventPublisher eventPublisher,
      ObjectMapper objectMapper,
      @Value("${pythontopia.bugMine.maxRetriesPerSession:3}") int maxRetries) {
    this.repository = repository;
    this.submissionRepository = submissionRepository;
    this.problemRepository = problemRepository;
    this.traceCollector = traceCollector;
    this.counterExampleSelector = counterExampleSelector;
    this.debugAgentClient = debugAgentClient;
    this.submissionService = submissionService;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
    this.maxRetries = maxRetries;
  }

  @Transactional
  public BugMineSessionDto open(Long userId, Long submissionId) {
    SubmissionDto submission =
        submissionRepository
            .findSubmission(userId, submissionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "SUBMISSION_NOT_FOUND",
                        "提交不存在或不属于当前用户"));
    if (submission.status() == SubmissionStatus.PASSED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "SUBMISSION_ALREADY_PASSED", "已通过的提交无需进入 Bug 矿洞");
    }
    Long sessionId =
        repository.insertSession(
            userId, submissionId, submission.problemId(), submission.questId());

    CounterExampleSelector.CounterExample counterExample =
        counterExampleSelector
            .findFirstFailing(submission.problemId(), submission.code())
            .orElseGet(
                () ->
                    new CounterExampleSelector.CounterExample(
                        "", "", "", false));

    PythonTraceCollector.TraceResult traceResult =
        traceCollector.collect(submission.code(), counterExample.stdin());

    Verdict verdict =
        submissionRepository
            .findJudgeResult(submissionId)
            .map(judge -> judge.verdict())
            .orElse(Verdict.WA);
    String kcCode = problemRepository.findKcCode(submission.problemId());
    DebugAgentRequest request =
        new DebugAgentRequest(
            userId,
            submissionId,
            submission.problemId(),
            kcCode == null ? List.of() : List.of(kcCode),
            verdict.name(),
            submission.code(),
            new DebugAgentRequest.CounterExamplePayload(
                counterExample.stdin(),
                counterExample.expected(),
                counterExample.actual(),
                buildDiffSummary(counterExample.expected(), counterExample.actual()),
                counterExample.redacted()),
            new DebugAgentRequest.VariableTracePayload(stepsFromTrace(traceResult), traceResult.truncated()),
            List.of());
    DebugAgentResponse response = debugAgentClient.analyse(request);
    if (response == null) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "DEBUG_GRAPH_EMPTY", "DebugAgent 返回空响应");
    }

    persistCards(sessionId, response, traceResult);
    return repository
        .findSession(userId, sessionId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BUG_MINE_PERSIST_FAILED",
                    "Bug 矿洞会话写入后未能读回"));
  }

  @Transactional(readOnly = true)
  public BugMineSessionDto find(Long userId, Long sessionId) {
    return repository
        .findSession(userId, sessionId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "BUG_MINE_NOT_FOUND", "Bug 矿洞会话不存在"));
  }

  @Transactional
  public BugMineCardDto reveal(Long userId, Long sessionId, Long cardId) {
    BugMineSessionDto session = find(userId, sessionId);
    repository
        .findCard(session.id(), cardId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "BUG_MINE_CARD_NOT_FOUND", "卡片不存在"));
    repository.revealCard(session.id(), cardId);
    return repository
        .findCard(session.id(), cardId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "BUG_MINE_REVEAL_FAILED", "卡片解锁失败"));
  }

  @Transactional
  public SubmissionDto retrySubmit(Long userId, Long sessionId, String code) {
    BugMineSessionDto session = find(userId, sessionId);
    if (session.status() != BugMineSessionStatus.OPEN) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "BUG_MINE_SESSION_CLOSED", "Bug 矿洞会话已关闭");
    }
    int currentRetry = repository.retryCount(session.id());
    if (currentRetry >= maxRetries) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "BUG_MINE_RETRY_EXCEEDED",
          "Bug 矿洞重试次数已达上限 " + maxRetries);
    }
    repository.incrementRetry(session.id());
    SubmissionDto submission =
        submissionService.submit(
            userId,
            new SubmissionCreateRequest(
                session.problemId(),
                code,
                session.questId(),
                SubmissionSource.BUG_MINE_RETRY));
    if (submission.status() == SubmissionStatus.PASSED) {
      repository.updateStatus(session.id(), BugMineSessionStatus.RESOLVED);
      String kcCode = problemRepository.findKcCode(session.problemId());
      if (kcCode != null && !kcCode.isBlank()) {
        eventPublisher.publishEvent(
            new LearningEvent.DebugResolved(userId, kcCode, session.problemId()));
      }
    }
    return submission;
  }

  @Transactional
  public BugMineSessionDto close(Long userId, Long sessionId, BugMineSessionStatus status) {
    BugMineSessionDto session = find(userId, sessionId);
    if (status == BugMineSessionStatus.OPEN) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "BUG_MINE_INVALID_CLOSE", "无法把会话状态置回 OPEN");
    }
    repository.updateStatus(session.id(), status);
    if (status == BugMineSessionStatus.ABANDONED) {
      // 情绪化小镇②：失败后放弃 → emotion_persistence 负向行为信号。
      String kcCode = problemRepository.findKcCode(session.problemId());
      eventPublisher.publishEvent(
          new LearningEvent.PracticeAbandoned(userId, session.problemId(), kcCode));
    }
    return repository
        .findSession(userId, sessionId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "BUG_MINE_CLOSE_FAILED", "关闭会话失败"));
  }

  private DebugAgentRequest.VariableTracePayload toRequestTrace(PythonTraceCollector.TraceResult trace) {
    return new DebugAgentRequest.VariableTracePayload(stepsFromTrace(trace), trace.truncated());
  }

  private List<DebugAgentRequest.TraceStep> stepsFromTrace(PythonTraceCollector.TraceResult trace) {
    JsonNode stepsNode = trace.summary().get("steps");
    List<DebugAgentRequest.TraceStep> out = new java.util.ArrayList<>();
    if (stepsNode != null && stepsNode.isArray()) {
      for (JsonNode entry : stepsNode) {
        int line = entry.path("line").asInt();
        java.util.Map<String, String> locals = new java.util.LinkedHashMap<>();
        JsonNode localsNode = entry.get("locals");
        if (localsNode != null) {
          localsNode
              .fields()
              .forEachRemaining(
                  field -> locals.put(field.getKey(), field.getValue().asText()));
        }
        out.add(new DebugAgentRequest.TraceStep(line, locals));
        if (out.size() >= 200) {
          break;
        }
      }
    }
    return out;
  }

  private void persistCards(
      Long sessionId, DebugAgentResponse response, PythonTraceCollector.TraceResult trace) {
    persistCard(sessionId, BugMineCardType.COUNTER_EXAMPLE, response.counterExample(), false);
    persistCard(sessionId, BugMineCardType.VARIABLE_TRACE, trace.summary(), false);
    persistCard(sessionId, BugMineCardType.WRONG_ASSUMPTION, response.wrongAssumption(), false);
    List<DebugAgentResponse.HintLayer> layers = response.hintLayers();
    for (DebugAgentResponse.HintLayer layer : layers) {
      BugMineCardType type =
          switch (layer.layer()) {
            case 1 -> BugMineCardType.HINT_LAYER_1;
            case 2 -> BugMineCardType.HINT_LAYER_2;
            case 3 -> BugMineCardType.HINT_LAYER_3;
            default -> throw new IllegalStateException("Unknown hint layer " + layer.layer());
          };
      persistCard(sessionId, type, layer, layer.layer() == 1);
    }
  }

  private void persistCard(Long sessionId, BugMineCardType type, Object payload, boolean revealed) {
    JsonNode node = objectMapper.valueToTree(payload);
    repository.insertCard(sessionId, type, node, revealed);
  }

  private String buildDiffSummary(String expected, String actual) {
    String[] e = expected == null ? new String[0] : expected.split("\\n", -1);
    String[] a = actual == null ? new String[0] : actual.split("\\n", -1);
    ArrayNode lines = objectMapper.createArrayNode();
    int max = Math.max(e.length, a.length);
    int reported = 0;
    StringBuilder diffSummary = new StringBuilder();
    for (int i = 0; i < max && reported < 3; i++) {
      String le = i < e.length ? e[i] : "";
      String la = i < a.length ? a[i] : "";
      if (!le.equals(la)) {
        if (diffSummary.length() > 0) diffSummary.append("; ");
        diffSummary
            .append("第 ")
            .append(i + 1)
            .append(" 行: 期望 '")
            .append(le)
            .append("' 实际 '")
            .append(la)
            .append("'");
        reported += 1;
      }
    }
    if (diffSummary.length() == 0) {
      return "尾部空白或空行差异";
    }
    return diffSummary.toString();
  }

  private ObjectNode emptyTraceSummary() {
    ObjectNode node = objectMapper.createObjectNode();
    node.set("steps", objectMapper.createArrayNode());
    node.put("truncated", false);
    return node;
  }
}
