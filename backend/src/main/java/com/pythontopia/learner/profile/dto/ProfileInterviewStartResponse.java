package com.pythontopia.learner.profile.dto;

import java.util.List;

public record ProfileInterviewStartResponse(
    String interviewSessionId, String status, List<ProfileMessageDto> history, LearnerProfileDto draft) {}
