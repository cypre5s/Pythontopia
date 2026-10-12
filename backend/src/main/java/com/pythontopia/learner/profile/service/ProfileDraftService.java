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

  /** Claims the open interview and persists all profile projections in one transaction. */
  @Transactional
  public void applyDraft(Long userId, String sessionId, LearnerProfileDto draft) {
    if (!profileRepository.completeInterview(userId, sessionId)) {
      return;
    }
    profileRepository.upsertProfile(userId, draft);
    interviewDimensionMirror.mirror(userId, draft);
    profileRepository.addRevision(userId, "INTERVIEW", draft);
  }
}
