package com.pythontopia.mainline.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 章测提交（{@code POST .../test/{testId}/submit}）。
 *
 * <p>{@code answers}: qid → 学生作答（CHOICE 选项标识 / JUDGE 真假 / FILL 文本 / CODING 源码）。
 * {@code integrity}: 前端尽力而为的防作弊计数（粘贴拦截 / 失焦次数），仅供教师参考，不自动判零（§4.3）。
 */
public record ChapterTestSubmitRequest(
    @NotNull Map<String, String> answers, IntegrityReport integrity) {

  public record IntegrityReport(Integer pasteBlocked, Integer blurEvents) {}
}
