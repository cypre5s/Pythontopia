package com.pythontopia.learner.profile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.agent.run.ResourceRunRecorder;
import com.pythontopia.agent.run.RunStatus;
import com.pythontopia.common.config.GraphHttpClientProperties;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.sse.GraphStreamTransport;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.profile.dto.*;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ProfileService {

  private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
  private static final int STREAM_READ_TIMEOUT_MS = 120_000;

  private final ProfileRepository profileRepository;
  private final CoursePackRepository coursePackRepository;
  private final ResourceRunRecorder resourceRunRecorder;
  private final RestTemplate graphRestTemplate;
  private final GraphServiceProperties graphProperties;
  private final ObjectMapper objectMapper;
  private final SseRelayExecutor sseRelayExecutor;
  private final InterviewDimensionMirror interviewDimensionMirror;
  private final GraphStreamTransport streamTransport;

  @Autowired
  public ProfileService(
      ProfileRepository profileRepository,
      CoursePackRepository coursePackRepository,
      ResourceRunRecorder resourceRunRecorder,
      RestTemplate graphRestTemplate,
      GraphServiceProperties graphProperties,
      GraphHttpClientProperties graphHttpProperties,
      ObjectMapper objectMapper,
      SseRelayExecutor sseRelayExecutor,
      InterviewDimensionMirror interviewDimensionMirror) {
    this(
        profileRepository,
        coursePackRepository,
        resourceRunRecorder,
        graphRestTemplate,
        graphProperties,
        objectMapper,
        sseRelayExecutor,
        interviewDimensionMirror,
        graphHttpProperties.connectTimeoutMs(),
        STREAM_READ_TIMEOUT_MS);
  }

  ProfileService(
      ProfileRepository profileRepository,
      CoursePackRepository coursePackRepository,
      ResourceRunRecorder resourceRunRecorder,
      RestTemplate graphRestTemplate,
      GraphServiceProperties graphProperties,
      ObjectMapper objectMapper,
      SseRelayExecutor sseRelayExecutor,
      InterviewDimensionMirror interviewDimensionMirror,
      int connectTimeoutMs,
      int readTimeoutMs) {
    this.profileRepository = profileRepository;
    this.coursePackRepository = coursePackRepository;
    this.resourceRunRecorder = resourceRunRecorder;
    this.graphRestTemplate = graphRestTemplate;
    this.graphProperties = graphProperties;
    this.objectMapper = objectMapper;
    this.sseRelayExecutor = sseRelayExecutor;
    this.interviewDimensionMirror = interviewDimensionMirror;
    this.streamTransport =
        new GraphStreamTransport(objectMapper, connectTimeoutMs, readTimeoutMs);
  }

  public ProfileInterviewStartResponse openOrResumeInterview(Long userId) {
    return profileRepository.findOpenInterview(userId).orElseGet(() -> profileRepository.createInterview(userId));
  }

  /** 拼接「本轮之前的多轮历史 + 当前用户消息」，让 ProfileAgent 具备多轮记忆。 */
  private List<ProfileMessageDto> buildHistory(ProfileTurnRequest request) {
    List<ProfileMessageDto> history = new java.util.ArrayList<>(request.resolvedHistory());
    history.add(new ProfileMessageDto("user", request.userMessage()));
    return history;
  }

  public ProfileTurnResponse sendTurn(Long userId, String sessionId, ProfileTurnRequest request) {
    if (!profileRepository.ownsInterview(userId, sessionId)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "PROFILE_INTERVIEW_NOT_FOUND", "Interview not found");
    }
    ProfileAgentTurnRequest agentRequest =
        new ProfileAgentTurnRequest(
            userId,
            sessionId,
            buildHistory(request),
            request.userMessage(),
            coursePackRepository.findKcs(43L, null).stream()
                .map(kc -> new KcIndexItemDto(kc.kcCode(), kc.kcName()))
                .toList());
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    GraphInternalTokenSupport.apply(headers, graphProperties);
    ProfileTurnResponse response;
    try {
      response =
          graphRestTemplate.postForObject(
              graphProperties.graphUrl() + "/agents/profile/turn",
              new HttpEntity<>(agentRequest, headers),
              ProfileTurnResponse.class);
    } catch (RestClientException exception) {
      log.warn("ProfileAgent request failed: {}", exception.getMessage());
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "PROFILE_AGENT_UNAVAILABLE",
          "ProfileAgent is temporarily unavailable");
    }
    if (response == null) {
      throw new BusinessException(HttpStatus.BAD_GATEWAY, "PROFILE_AGENT_EMPTY_RESPONSE", "ProfileAgent returned empty response");
    }
    RunStatus status = statusOf(response);
    if (status == RunStatus.SUCCESS && response.profileDraft() != null) {
      applyDraft(userId, sessionId, response.profileDraft());
    } else if (status == RunStatus.FAILED) {
      profileRepository.abortInterview(userId, sessionId);
    }
    resourceRunRecorder.record(
        userId,
        "ProfileAgent",
        "PROFILE_INTERVIEW",
        Map.of("sessionId", sessionId, "message", request.userMessage()),
        response,
        List.of(),
        response.safetyResult(),
        response.groundingResult(),
        status);
    return response;
  }

  public SseEmitter sendTurnStream(Long userId, String sessionId, ProfileTurnRequest request) {
    SseEmitter emitter = new SseEmitter((long) STREAM_READ_TIMEOUT_MS);
    startTurnStream(userId, sessionId, request, emitter);
    return emitter;
  }

  void startTurnStream(
      Long userId, String sessionId, ProfileTurnRequest request, SseEmitter emitter) {
    if (!profileRepository.ownsInterview(userId, sessionId)) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "PROFILE_INTERVIEW_NOT_FOUND", "Interview not found");
    }
    ProfileAgentTurnRequest agentRequest =
        new ProfileAgentTurnRequest(
            userId,
            sessionId,
            buildHistory(request),
            request.userMessage(),
            coursePackRepository.findKcs(43L, null).stream()
                .map(kc -> new KcIndexItemDto(kc.kcCode(), kc.kcName()))
                .toList());

    String url = graphProperties.graphUrl() + "/agents/profile/turn/stream";

    sseRelayExecutor.submit(
        () ->
            streamTransport.relay(
                url,
                () -> agentRequest,
                GraphInternalTokenSupport.tokenOrNull(graphProperties),
                emitter,
                (event, data) -> {
                  if ("result".equals(event)) {
                    handleTurnResult(userId, sessionId, request, data);
                  } else if ("RUN_FINISHED".equals(event)) {
                    handleTurnResult(userId, sessionId, request, unwrapAguiResult(data));
                  }
                },
                failure -> recoverProfileTurn(userId, sessionId, request, failure),
                null));
  }

  private GraphStreamTransport.TerminalEvent recoverProfileTurn(
      Long userId,
      String sessionId,
      ProfileTurnRequest request,
      GraphStreamTransport.FailureKind failure) {
    log.warn("Profile turn SSE failed as {}; trying synchronous ProfileAgent recovery", failure);
    try {
      ProfileTurnResponse fallback = sendTurn(userId, sessionId, request);
      return new GraphStreamTransport.TerminalEvent(
          "result", objectMapper.writeValueAsString(fallback));
    } catch (Exception fallbackFailure) {
      log.error("Profile turn synchronous recovery failed after SSE failure={}", failure, fallbackFailure);
      return null;
    }
  }

  private String unwrapAguiResult(String data) {
    try {
      JsonNode result = objectMapper.readTree(data).get("result");
      if (result != null && result.isObject()) {
        return objectMapper.writeValueAsString(result);
      }
    } catch (Exception e) {
      log.warn("Failed to unwrap ProfileAgent RUN_FINISHED payload", e);
    }
    return data;
  }

  private void handleTurnResult(
      Long userId, String sessionId, ProfileTurnRequest request, String resultJson) {
    try {
      ProfileTurnResponse response = objectMapper.readValue(resultJson, ProfileTurnResponse.class);
      RunStatus status = statusOf(response);
      if (status == RunStatus.SUCCESS && response.profileDraft() != null) {
        applyDraft(userId, sessionId, response.profileDraft());
      } else if (status == RunStatus.FAILED) {
        profileRepository.abortInterview(userId, sessionId);
      }
      resourceRunRecorder.record(
          userId,
          "ProfileAgent",
          "PROFILE_INTERVIEW",
          Map.of("sessionId", sessionId, "message", request.userMessage()),
          response,
          List.of(),
          response.safetyResult(),
          response.groundingResult(),
          status);
    } catch (Exception e) {
      log.warn("Failed to handle profile turn SSE result", e);
    }
  }

  /**
   * 落库访谈结果：写旧列 → 镜像进 8 维 dimensions → 记 revision → 关闭访谈。
   *
   * <p>镜像这一步不可省：8 维消费方（答疑 / NPC / 工坊 / 记忆简报 / 推荐）只读 dimensions，
   * 只写旧列等于访谈信号对下游完全不可见。详见 {@link InterviewDimensionMirror}。
   */
  public void applyDraft(Long userId, String sessionId, LearnerProfileDto draft) {
    profileRepository.upsertProfile(userId, draft);
    interviewDimensionMirror.mirror(userId, draft);
    profileRepository.addRevision(userId, "INTERVIEW", draft);
    profileRepository.completeInterview(userId, sessionId);
  }

  public void finishInterview(Long userId, String sessionId) {
    profileRepository.completeInterview(userId, sessionId);
  }

  public void applyPassiveRevision(Long userId, String source, Object diff) {
    applyPassiveRevision(userId, source, diff, null);
  }

  public void applyPassiveRevision(Long userId, String source, Object diff, Long evaluationId) {
    profileRepository.addRevision(userId, source, diff, evaluationId);
  }

  public java.util.Optional<LearnerProfileDto> findProfile(Long userId) {
    return profileRepository.findProfile(userId);
  }

  public List<ProfileRevisionDto> findRevisions(Long userId, int limit) {
    return profileRepository.findRevisions(userId, Math.max(1, Math.min(limit, 50)));
  }

  private RunStatus statusOf(ProfileTurnResponse response) {
    if (response.safetyResult() != null && Boolean.FALSE.equals(response.safetyResult().passed())) {
      return RunStatus.FAILED;
    }
    if (response.groundingResult() != null && Boolean.FALSE.equals(response.groundingResult().passed())) {
      return RunStatus.PARTIAL;
    }
    if (Boolean.TRUE.equals(response.done()) && response.profileDraft() != null) {
      return RunStatus.SUCCESS;
    }
    return RunStatus.PARTIAL;
  }
}
