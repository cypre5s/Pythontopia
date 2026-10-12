package com.pythontopia.world.boss.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 无尽远征一局 run 的可持久化快照 DTO（前后端同构；前端 stores/rogueRun PersistShape + api/expedition.ts
 * ExpeditionRunSnapshot 的对端）。
 *
 * <p>服务端签发的 runId、章节绑定和地图拓扑/题目配置不可由快照改写。PUT 请求只用于兼容性校验，服务端不从快照写入节点进度、HP、遗物或历史。
 * verifiedDepth 与 score 只由服务端判题账本派生；请求中的值不会被用于正式成绩、学习奖励或已通关认定。
 *
 * <p>nodes 用 {@code List<Map<String,Object>>} 透传前端 RogueNode[]，后端比对其固定拓扑与题目字段；history 整体存取，
 * 不用于成绩、奖励或通关事实来源。
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
    @NotBlank @Pattern(regexp = "idle|map|event|challenge|reward|cleared|over") String phase,
    @Min(1) @Max(7) Integer focusChapter,
    @Size(max = 34) List<Map<String, Object>> nodes,
    @Min(0) @Max(10) int layers,
    @Size(max = 32) String currentNodeId,
    @Min(0) @Max(6) int hp,
    @Min(0) @Max(6) int maxHp,
    @Min(0) @Max(12) int momentum,
    @Min(0) @Max(12) int bestMomentum,
    @Min(0) @Max(100_000) int score,
    @Size(max = 7) List<String> relics,
    boolean isDaily,
    @Size(max = 50) List<Map<String, Object>> history,
    @Pattern(
            regexp =
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String runId,
    @Min(0) @Max(12) int verifiedDepth) {

  /** Compatibility constructor for clients/tests that predate the server-derived depth field. */
  public ExpeditionRunDto(
      String phase,
      Integer focusChapter,
      List<Map<String, Object>> nodes,
      int layers,
      String currentNodeId,
      int hp,
      int maxHp,
      int momentum,
      int bestMomentum,
      int score,
      List<String> relics,
      boolean isDaily,
      List<Map<String, Object>> history,
      String runId) {
    this(
        phase,
        focusChapter,
        nodes,
        layers,
        currentNodeId,
        hp,
        maxHp,
        momentum,
        bestMomentum,
        score,
        relics,
        isDaily,
        history,
        runId,
        0);
  }

  public ExpeditionRunDto {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
    relics = relics == null ? List.of() : List.copyOf(relics);
    history = history == null ? List.of() : List.copyOf(history);
  }
}
