package com.pythontopia.intent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 百晓工坊「意图路由」请求（前端 camelCase）。
 *
 * <p>前端把这一句话、已加载的课程 KC 目录（{@code courseKcs}，为 {@code @} 提及菜单本就全量加载）
 * 以及学生手动 {@code @} 指定的 KC（{@code manualKcCodes}，最高优先级）发来；Java 仅做透传代理，
 * 真正的意图分类与 KC 锚定在 Python graph 的 IntentRouterAgent 完成。除 {@code message} 外均可空。
 */
public record IntentRouteRequest(
    @NotBlank @Size(max = 4000) String message,
    List<String> manualKcCodes,
    @Valid List<CourseKc> courseKcs,
    Integer currentChapter,
    List<String> recentKcCodes) {

  /** 课程 KC 目录条目：code 用于锚定，name 用于让 LLM 把语句主题映射到正确知识点。 */
  public record CourseKc(@NotBlank @Size(max = 64) String code, @Size(max = 200) String name) {}
}
