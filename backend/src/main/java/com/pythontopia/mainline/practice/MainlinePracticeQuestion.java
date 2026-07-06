package com.pythontopia.mainline.practice;

import com.pythontopia.mainline.practice.dto.StagePracticeQuestionDto;
import java.util.List;

/**
 * 小节「练」客观题的<b>带答案内部模型</b>（仅服务端可见，绝不直接下发）。
 *
 * <p>{@code answers} 是 {@code answer_json} 解析结果：{@code CHOICE}=[选项文本]；{@code JUDGE}=["正确"|"错误"]；
 * {@code FILL}=[每空答案...]（顺序即空位顺序）。下发学生端时调用 {@link #toDto()} 剥离答案 / 讲解
 * （AGENTS.md「不允许直接给可复制提交的完整答案」）。
 */
public record MainlinePracticeQuestion(
    long id,
    int chapter,
    int stageIndex,
    int ordinal,
    String kcCode,
    PracticeQuestionType type,
    String stem,
    List<String> options,
    List<String> answers,
    int blankCount,
    String explanation,
    String sourceLabel) {

  /** 去答案题面（下发学生端）：丢弃 {@code answers} / {@code explanation} / {@code sourceLabel}。 */
  public StagePracticeQuestionDto toDto() {
    return new StagePracticeQuestionDto(id, kcCode, type, ordinal, stem, options, blankCount);
  }
}
