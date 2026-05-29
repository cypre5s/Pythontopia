package com.pythontopia.dialog;

import java.util.Map;

/**
 * R-12 W2-NPC 对话模板真实数据上下文。
 *
 * <p>NPC 对话 JSON 中存在三类占位符：
 *
 * <ul>
 *   <li>{@code {{playerName}}} — 玩家显示名；
 *   <li>{@code {{stats.xxx}}} — 各种统计字段（本周练习数、最难 KC、班级数据……），由
 *       {@link DialogContextService} 从 mastery / submission / classroom / world 等模块实时聚合，
 *       不再依赖前端硬编码；
 *   <li>{@code {{profileAdaptive:A | B}}} 与 {@code {{giftFavorite:A | B}}} — 二分支模板，
 *       由 {@link #profileType} 与 {@link #giftJustGiven} 决策走哪个文案。
 * </ul>
 *
 * <p>{@link #stats} 用 {@link Map} 形态而非具名 record 字段：占位符可能在多个 NPC 中按需扩展，
 * 直接用 key→string 平铺让后端新增字段不需要前端跟着改 DTO；同时所有数值都已 stringify，
 * 模板渲染器拿到即用，不必再 toString。
 */
public record DialogContextDto(
    String playerName,
    String profileType,
    boolean giftJustGiven,
    Map<String, String> stats) {

  /** Profile 类型常量：{@code practical} 走实践型分支，{@code cautious} 走谨慎型分支。 */
  public static final String PROFILE_PRACTICAL = "practical";

  public static final String PROFILE_CAUTIOUS = "cautious";
}
