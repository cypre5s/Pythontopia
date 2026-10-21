package com.pythontopia.feynman.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class FeynmanDtos {

  private FeynmanDtos() {}

  public record StartRequest(@NotBlank String kcCode) {}

  public record TeachRequest(@NotBlank String explanation) {}

  public record SessionResponse(long id, long userId, String kcCode, String status, JsonNode tuteeState) {}

  /**
   * 一轮学弟回复。{@code noticeMd} 是系统侧的诚实旁白（非 LLM 生成），例如「该 KC 还没有真实
   * 错误记录，先去做题」，用于避免把「无误解可解除」误呈现为「已教会」。
   */
  public record TurnResponse(
      long id, String role, String content, String taughtFix, String noticeMd) {}

  /**
   * 真判题裁定。{@code status} 是唯一权威（{@link JudgeStatus}）：
   * {@code passed} 只是 {@code PASSED} 的布尔投影，判题系统故障 / 概念模式说明都不是「学生失败」。
   */
  public record Verdict(long sessionId, boolean passed, String message, String status) {

    public Verdict(long sessionId, JudgeStatus status, String message) {
      this(sessionId, status == JudgeStatus.PASSED, message, status.name());
    }
  }

  /**
   * 判题结论的四态，与前端 {@code FeynmanJudgeStatus} 一一对应。
   *
   * <ul>
   *   <li>{@code PASSED} / {@code FAILED}：学生教学的真实成败；
   *   <li>{@code ERROR}：判题系统故障（沙箱起不来、题目无用例），<strong>不</strong>归罪学生；
   *   <li>{@code NA}：本模式不做代表题真判题（概念模式），中性说明而非失败。
   * </ul>
   */
  public enum JudgeStatus {
    PASSED,
    FAILED,
    ERROR,
    NA
  }

  /**
   * 代码找茬：学弟写出的含 bug 代码 + 真实 bug 行号（供前端限时找茬判定命中）。
   *
   * <p>不下发 fixed_code（那是答案），仅给 {@code bugLineIndices} 用于判分。{@code source}：
   * {@code real_misconception}（复现真实误解）/ {@code generic_beginner_bug}（常见陷阱）/ {@code none}。
   */
  public record BugHuntResponse(
      String source,
      String misconceptionTag,
      String buggyCode,
      List<Integer> bugLineIndices,
      String hintMd) {}
}
