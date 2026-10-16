package com.pythontopia.feynman.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class FeynmanDtos {

  private FeynmanDtos() {}

  public record StartRequest(@NotBlank @Size(max = 64) String kcCode) {}

  public record TeachRequest(@NotBlank @Size(max = 2000) String explanation) {}

  public record SessionResponse(long id, long userId, String kcCode, String status, JsonNode tuteeState) {}

  public record TurnResponse(
      long id, String role, String content, String taughtFix, String noticeMd) {}

  public record Verdict(long sessionId, boolean passed, String message, String status) {

    public Verdict(long sessionId, JudgeStatus status, String message) {
      this(sessionId, status == JudgeStatus.PASSED, message, status.name());
    }
  }

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
