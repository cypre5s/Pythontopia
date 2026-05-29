package com.pythontopia.tutor;

import java.time.Instant;
import java.util.List;

public record TutorSession(
    Long id,
    Long userId,
    String question,
    String answerMd,
    List<Integer> evidencePageIds,
    String groundingStatus,
    Instant createdAt) {}
