package com.pythontopia.learner.profile.service;

import com.pythontopia.agent.run.ResourceRunRecorder;
import com.pythontopia.agent.run.RunStatus;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.profile.dto.*;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ProfileService {

  private final ProfileRepository profileRepository;
  private final CoursePackRepository coursePackRepository;
  private final ResourceRunRecorder resourceRunRecorder;
  private final RestTemplate graphRestTemplate;
  private final GraphServiceProperties graphProperties;

  public ProfileService(
      ProfileRepository profileRepository,
      CoursePackRepository coursePackRepository,
      ResourceRunRecorder resourceRunRecorder,
      RestTemplate graphRestTemplate,
      GraphServiceProperties graphProperties) {
    this.profileRepository = profileRepository;
    this.coursePackRepository = coursePackRepository;
    this.resourceRunRecorder = resourceRunRecorder;
    this.graphRestTemplate = graphRestTemplate;
    this.graphProperties = graphProperties;
  }

  public ProfileInterviewStartResponse openOrResumeInterview(Long userId) {
    return profileRepository.findOpenInterview(userId).orElseGet(() -> profileRepository.createInterview(userId));
  }

  public ProfileTurnResponse sendTurn(Long userId, String sessionId, ProfileTurnRequest request) {
    if (!profileRepository.ownsInterview(userId, sessionId)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "PROFILE_INTERVIEW_NOT_FOUND", "Interview not found");
    }
    ProfileAgentTurnRequest agentRequest =
        new ProfileAgentTurnRequest(
            userId,
            sessionId,
            List.of(new ProfileMessageDto("user", request.userMessage())),
            request.userMessage(),
            coursePackRepository.findKcs(43L, null).stream()
                .map(kc -> new KcIndexItemDto(kc.kcCode(), kc.kcName()))
                .toList());
    ProfileTurnResponse response =
        graphRestTemplate.postForObject(
            graphProperties.graphUrl() + "/agents/profile/turn",
            agentRequest,
            ProfileTurnResponse.class);
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

  public void applyDraft(Long userId, String sessionId, LearnerProfileDto draft) {
    profileRepository.upsertProfile(userId, draft);
    profileRepository.addRevision(userId, "INTERVIEW", draft);
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
