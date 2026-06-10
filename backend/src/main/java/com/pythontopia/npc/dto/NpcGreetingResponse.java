package com.pythontopia.npc.dto;

import java.util.List;

/**
 * NPC 首句个性化寒暄响应。
 *
 * <p>语义（遵守 AGENTS.md fail-fast / 防幻觉：不伪造个性化内容）：
 *
 * <ul>
 *   <li>{@code available=true}：graph {@code NpcDialogAgent} 基于真实记忆产出了寒暄，前端插入脚本首节点之前；
 *   <li>{@code available=false && degraded=true}：记忆 / agent 服务不可用 —— 前端显示「（记忆服务暂不可用）」并照常走脚本；
 *   <li>{@code available=false && degraded=false}：该生暂无可引用记忆（如新玩家）—— 不显示寒暄也不显示故障提示，直接走脚本。
 * </ul>
 *
 * @param greeting 个性化寒暄文本（{@code available=false} 时为空串）
 * @param available 是否产出了可展示的个性化寒暄
 * @param grounded 寒暄是否落在被引用的记忆证据上（防幻觉透明度）
 * @param citedEventIds 引用到的学习事件 id（来自记忆流，可空集）
 * @param degraded 记忆 / agent 下游是否降级（true 时前端展示「记忆服务暂不可用」）
 * @param cached 是否命中 30 分钟 npc×学生 缓存
 */
public record NpcGreetingResponse(
    String greeting,
    boolean available,
    boolean grounded,
    List<Long> citedEventIds,
    boolean degraded,
    boolean cached) {

  public NpcGreetingResponse {
    greeting = greeting == null ? "" : greeting;
    citedEventIds = citedEventIds == null ? List.of() : List.copyOf(citedEventIds);
  }

  /** 成功产出寒暄。 */
  public static NpcGreetingResponse personalized(
      String greeting, boolean grounded, List<Long> citedEventIds) {
    return new NpcGreetingResponse(greeting, true, grounded, citedEventIds, false, false);
  }

  /** 记忆 / agent 服务失败：结构化降级标志（不伪造内容）。 */
  public static NpcGreetingResponse serviceUnavailable() {
    return new NpcGreetingResponse("", false, false, List.of(), true, false);
  }

  /** 暂无可引用记忆（非故障）：既不寒暄也不报故障，直接走脚本。 */
  public static NpcGreetingResponse none() {
    return new NpcGreetingResponse("", false, false, List.of(), false, false);
  }

  /** 命中缓存时返回的副本（仅翻 {@code cached} 标志，便于前端/观测区分实时与复用）。 */
  public NpcGreetingResponse asCached() {
    return new NpcGreetingResponse(greeting, available, grounded, citedEventIds, degraded, true);
  }
}
