package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ProfileAgentTurnRequest(
    @JsonProperty("user_id")
    Long userId,
    @JsonProperty("interview_session_id")
    String interviewSessionId,
    @JsonProperty("last_turn_history")
    List<ProfileMessageDto> lastTurnHistory,
    @JsonProperty("last_user_message")
    String lastUserMessage,
    @JsonProperty("course_kc_index")
    List<KcIndexItemDto> courseKcIndex) {}
