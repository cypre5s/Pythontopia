package com.pythontopia.dialog;

import java.util.Map;

/**
 * NPC 对话模板真实数据上下文。
 *
 * <p>NPC 对话 JSON 中存在四类占位符：
 *
 * <ul>
 *   <li>{@code {{playerName}}} — 玩家显示名；
 *   <li>{@code {{stats.xxx}}} — 各种统计字段（本周练习数、最难 KC、班级数据……），由
 *       {@link DialogContextService} 从 mastery / submission / classroom / world 等模块实时聚合；
 *   <li>{@code {{profileAdaptive:A | B}}} — 二分支模板，由 {@link #profileType} 决策走哪个文案；
 *   <li>{@code {{styleAdaptive:视觉|逻辑|试错}}} — compatibility slot selected from an explicit media preference.
 * </ul>
 *
 * <p>{@link #stats} 用 {@link Map} 而非具名 record 字段：占位符可能在多个 NPC 中按需扩展，
 * key→string 平铺让后端新增字段不必改 DTO；且所有数值已 stringify，模板渲染器拿到即用。
 */
public record DialogContextDto(
    String playerName,
    String profileType,
    String cognitiveStyle,
    Map<String, String> stats) {

  /** Profile 类型常量：{@code practical} 走实践型分支，{@code cautious} 走谨慎型分支。 */
  public static final String PROFILE_PRACTICAL = "practical";

  public static final String PROFILE_CAUTIOUS = "cautious";

  /**
   * Legacy template values mapped from explicit media preferences; unknown preferences use the visual fallback.
   */
  public static final String STYLE_VISUAL = "visual";

  public static final String STYLE_LOGICAL = "logical";

  public static final String STYLE_TRIAL = "trial";

  public static final String STYLE_UNKNOWN = "unknown";
}
