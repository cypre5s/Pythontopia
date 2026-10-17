package com.pythontopia.learner.profile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.agent.run.ResourceRunRecorder;
import com.pythontopia.agent.run.RunStatus;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.sse.GraphSseConsumer;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.profile.dto.*;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ProfileService {

  private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

  private final ProfileRepository profileRepository;
  private final CoursePackRepository coursePackRepository;
  private final ResourceRunRecorder resourceRunRecorder;
  private final RestTemplate graphRestTemplate;
  private final GraphServiceProperties graphProperties;
  private final ObjectMapper objectMapper;
  private final SseRelayExecutor sseRelayExecutor;
  private final ProfileDraftService profileDraftService;
  private final StudentWriteThrottle studentWriteThrottle;

  public ProfileService(
      ProfileRepository profileRepository,
      CoursePackRepository coursePackRepository,
      ResourceRunRecorder resourceRunRecorder,
      RestTemplate graphRestTemplate,
      GraphServiceProperties graphProperties,
      ObjectMapper objectMapper,
      SseRelayExecutor sseRelayExecutor,
      ProfileDraftService profileDraftService,
      StudentWriteThrottle studentWriteThrottle) {
    this.profileRepository = profileRepository;
    this.coursePackRepository = coursePackRepository;
    this.resourceRunRecorder = resourceRunRecorder;
    this.graphRestTemplate = graphRestTemplate;
    this.graphProperties = graphProperties;
    this.objectMapper = objectMapper;
    this.sseRelayExecutor = sseRelayExecutor;
    this.profileDraftService = profileDraftService;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  public ProfileInterviewStartResponse openOrResumeInterview(Long userId) {
    return profileRepository
        .findOpenInterview(userId)
        .orElseGet(
            () -> {
              if (!studentWriteThrottle.tryAcquireProfileInterview(userId)) {
                throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "STUDENT_WRITE_RATE_LIMITED",
                    "每个账号每小时最多新建 3 次画像访谈");
              }
              return profileRepository.createInterview(userId);
            });
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
      log.warn(
          "ProfileAgent request failed errorType={}", exception.getClass().getSimpleName());
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
      profileDraftService.applyDraft(userId, sessionId, response.profileDraft());
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

    SseEmitter emitter = new SseEmitter(120_000L);
    String url = graphProperties.graphUrl() + "/agents/profile/turn/stream";

    sseRelayExecutor.submit(() -> {
      try {
        GraphSseConsumer.consume(
            url,
            agentRequest,
            objectMapper,
            GraphInternalTokenSupport.tokenOrNull(graphProperties),
            (event, data) -> {
          try {
            emitter.send(SseEmitter.event().name(event).data(data));
            if ("result".equals(event)) {
              handleTurnResult(userId, sessionId, request, data);
            }
          } catch (IOException e) {
            log.debug(
                "Profile SSE emitter write failed errorType={}",
                e.getClass().getSimpleName());
          }
        });
        emitter.complete();
      } catch (IOException e) {
        log.warn(
            "Profile turn SSE stream failed, falling back to non-streaming errorType={}",
            e.getClass().getSimpleName());
        try {
          ProfileTurnResponse fallback = sendTurn(userId, sessionId, request);
          String json = objectMapper.writeValueAsString(fallback);
          emitter.send(SseEmitter.event().name("result").data(json));
          emitter.complete();
        } catch (Exception fallbackEx) {
          log.error(
              "Profile turn SSE fallback failed errorType={}",
              fallbackEx.getClass().getSimpleName());
          emitter.completeWithError(fallbackEx);
        }
      }
    });
    return emitter;
  }

  private void handleTurnResult(
      Long userId, String sessionId, ProfileTurnRequest request, String resultJson) {
    try {
      ProfileTurnResponse response = objectMapper.readValue(resultJson, ProfileTurnResponse.class);
      RunStatus status = statusOf(response);
      if (status == RunStatus.SUCCESS && response.profileDraft() != null) {
        profileDraftService.applyDraft(userId, sessionId, response.profileDraft());
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
      log.warn(
          "Failed to handle profile turn SSE result errorType={}",
          e.getClass().getSimpleName());
    }
  }

  public void finishInterview(Long userId, String sessionId) {
    if (!profileRepository.completeInterview(userId, sessionId)
        && !profileRepository.isInterviewCompleted(userId, sessionId)) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND,
          "PROFILE_INTERVIEW_NOT_FOUND",
          "访谈不存在、未打开或不属于当前学生");
    }
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
