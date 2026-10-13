package com.pythontopia.learner.profile.service;

import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileDraftService {

  private final ProfileRepository profileRepository;
  private final InterviewDimensionMirror interviewDimensionMirror;

  public ProfileDraftService(
      ProfileRepository profileRepository, InterviewDimensionMirror interviewDimensionMirror) {
    this.profileRepository = profileRepository;
    this.interviewDimensionMirror = interviewDimensionMirror;
  }

  /** Claims the open interview and persists only self-reported goals, experience and preferences. */
  @Transactional
  public void applyDraft(Long userId, String sessionId, LearnerProfileDto draft) {
    if (!profileRepository.completeInterview(userId, sessionId)) {
      return;
    }
    LearnerProfileDto selfReport =
        new LearnerProfileDto(
            draft.profileId(),
            draft.userId(),
            draft.packId(),
            draft.major(),
            draft.baseLevel(),
            draft.learningGoal(),
            null,
            draft.learningPreference(),
            null,
            null,
            null,
            null);
    profileRepository.upsertProfile(userId, selfReport);
    interviewDimensionMirror.mirror(userId, selfReport);
    profileRepository.addRevision(userId, "INTERVIEW", selfReport);
  }
}
