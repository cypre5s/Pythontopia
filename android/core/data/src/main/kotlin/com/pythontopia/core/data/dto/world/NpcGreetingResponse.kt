package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

/**
 * NPC 首句个性化寒暄（`GET /api/npc/{npcKey}/greeting`，R-15 §8），对应后端 `NpcGreetingResponse`。
 * fail-fast：[available]=true 才有寒暄；[degraded]=true 表记忆/agent 暂不可用；均 false=无可引用记忆走脚本。
 */
@Serializable
data class NpcGreetingResponse(
    val greeting: String = "",
    val available: Boolean = false,
    val grounded: Boolean = false,
    val citedEventIds: List<Long> = emptyList(),
    val degraded: Boolean = false,
    val cached: Boolean = false,
)
