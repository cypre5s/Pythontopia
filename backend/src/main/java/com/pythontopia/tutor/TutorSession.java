package com.pythontopia.tutor;

import java.time.Instant;
import java.util.List;

/**
 * 一次导师问答的会话记录；{@code evidencePageIds} 为答案引用的课件证据页，
 * {@code groundingStatus} 为证据接地校验结果。
 */
public record TutorSession(
    Long id,
    Long userId,
    String question,
    String answerMd,
    List<Integer> evidencePageIds,
    String groundingStatus,
    Instant createdAt) {}
