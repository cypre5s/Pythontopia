package com.pythontopia.tutor.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/** 导学工作流卡片时间线条目（payload 为整卡 JSON，含 card_id / ai_reply 的 user_message）。 */
public record TutorWorkflowCardDto(
    int seq, String event, String cardKey, JsonNode payload, OffsetDateTime createdAt) {}
