package com.pythontopia.tutor.dto;

import java.time.Instant;
import java.util.List;

public record TutorSessionDto(
    Long id,
    String question,
    String answerMd,
    List<Integer> evidencePageIds,
    String groundingStatus,
    Instant createdAt) {}
