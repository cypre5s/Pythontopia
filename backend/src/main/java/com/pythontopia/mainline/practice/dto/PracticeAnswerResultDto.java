package com.pythontopia.mainline.practice.dto;

import java.util.List;

/**
 * 客观题判分结果（作答后下发）。
 *
 * <p>{@code correct} 为本次是否答对；{@code correctAnswers} 与 {@code explanation} 仅在作答后回传，
 * 供学生自检与订正（不提前泄题）。多次作答幂等：答对后该题计入小节「练」进度（服务端去重）。
 */
public record PracticeAnswerResultDto(
    long questionId,
    boolean correct,
    List<String> correctAnswers,
    String explanation) {}
