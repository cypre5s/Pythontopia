package com.pythontopia.mainline.practice.dto;

import java.util.List;

/**
 * 客观题判分结果（作答后下发）。
 *
 * <p>{@code correct} 为本次是否答对；{@code explanation} 作答后即回传，供学生自检与订正（不提前泄题）。
 * {@code correctAnswers} <b>仅在答对时</b>回传（答错为空列表）——否则一次试错就能拿到可直接复制回填的
 * 完整答案，「练-客观题」这道门形同虚设。多次作答幂等：答对后该题计入小节「练」进度（服务端去重）。
 */
public record PracticeAnswerResultDto(
    long questionId,
    boolean correct,
    List<String> correctAnswers,
    String explanation) {}
