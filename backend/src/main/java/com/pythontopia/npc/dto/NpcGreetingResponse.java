package com.pythontopia.npc.dto;

import java.util.List;

/**
 * NPC 首句寒暄响应（fail-fast，不伪造内容）：
 * available=true 产出寒暄；available=false&degraded=true 记忆/agent 不可用（前端示「记忆服务暂不可用」）；
 * 均 false 则无可引用记忆（新玩家，直接走脚本）。
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
