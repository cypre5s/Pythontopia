package com.pythontopia.mainline.practice.dto;

import com.pythontopia.mainline.practice.PracticeQuestionType;
import java.util.List;

/**
 * 小节「练」客观题题面（下发学生端）。
 *
 * <p><b>防泄题约定</b>（AGENTS.md「不允许直接给可复制提交的完整答案」）：本 DTO <b>不含正确答案</b>，
 * 判分在服务端（{@code POST /api/mainline/stages/{stageId}/practice/{questionId}/answer}），作答后由
 * {@link PracticeAnswerResultDto} 回传正确答案与讲解供自检。
 *
 * <ul>
 *   <li>{@code CHOICE}：{@code options} 为选项文本（单选）；{@code blankCount}=0。
 *   <li>{@code JUDGE}：{@code options} 为空（前端用「正确 / 错误」按钮）；{@code blankCount}=0。
 *   <li>{@code FILL}：{@code options} 为空；{@code blankCount} 为填空数量。
 * </ul>
 */
public record StagePracticeQuestionDto(
    long id,
    String kcCode,
    PracticeQuestionType type,
    int ordinal,
    String stem,
    List<String> options,
    int blankCount) {}
