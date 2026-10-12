package com.pythontopia.world.boss.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/**
 * 无尽远征一局 run 的可持久化快照 DTO（前后端同构；前端 stores/rogueRun PersistShape +
 * api/expedition.ts ExpeditionRunSnapshot 的对端）。
 *
 * <p>除服务端签发的 runId 和从判题记录计算的 score 外，其余过程字段仍由客户端提供，只用于续局展示/玩法；
 * 不得用于正式成绩、学习奖励或已通关认定。
 *
 * <p>nodes / history 用 {@code List<Map<String,Object>>} 透传前端 RogueNode[] / RogueHistoryEntry[]，
 * 后端不解释其内部结构（避免节点 schema 漂移时频繁改后端 DTO），仅整体存取于 {@code expedition_run}。
 *
 * @param phase 阶段（idle/map/event/challenge/cleared/over；服务端仅持久化，challenge/event 续局回退由前端处理）
 * @param focusChapter 聚焦章节 1..7（null=全局自适应）
 * @param nodes 分支地图节点（前端 RogueNode[]）
 * @param layers 总层数
 * @param currentNodeId 当前节点 id（可空）
 * @param hp 当前体力
 * @param maxHp 体力上限
 * @param momentum 当前气势
 * @param bestMomentum 本局最高气势
 * @param score 本局得分
 * @param relics 已获学习增益 id 列表
 * @param isDaily 是否每日挑战
 * @param history 答题历史（前端 RogueHistoryEntry[]）
 */
public record ExpeditionRunDto(
    @NotBlank @Size(max = 16) String phase,
    @Min(1) @Max(7) Integer focusChapter,
    List<Map<String, Object>> nodes,
    @Min(0) int layers,
    @Size(max = 32) String currentNodeId,
    @Min(0) int hp,
    @Min(0) int maxHp,
    @Min(0) int momentum,
    @Min(0) int bestMomentum,
    @Min(0) int score,
    List<String> relics,
    boolean isDaily,
    List<Map<String, Object>> history,
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String runId) {

  public ExpeditionRunDto {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
    relics = relics == null ? List.of() : List.copyOf(relics);
    history = history == null ? List.of() : List.copyOf(history);
  }
}
