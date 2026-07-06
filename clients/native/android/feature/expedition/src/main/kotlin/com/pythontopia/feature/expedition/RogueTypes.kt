package com.pythontopia.feature.expedition

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 无尽远征（Roguelike）领域模型（移植 Web `frontend/src/types/rogue.ts`）。
 *
 * 一局 run = 一张分层 DAG（类杀戮尖塔）：玩家从底层沿出边选择下一节点向上闯，终点为 boss。
 * 序列化字段名与 Web `RogueNode` 完全一致，保证后端 `expedition_run`（透传 `List<Map>`）
 * 在 Web ↔ Android 之间跨端续局兼容。
 */

@Serializable
enum class RogueNodeKind {
    @SerialName("battle") BATTLE,
    @SerialName("elite") ELITE,
    @SerialName("event") EVENT,
    @SerialName("bug") BUG,
    @SerialName("chest") CHEST,
    @SerialName("rest") REST,
    @SerialName("boss") BOSS,
}

@Serializable
enum class RogueNodeState {
    @SerialName("locked") LOCKED,
    @SerialName("available") AVAILABLE,
    @SerialName("current") CURRENT,
    @SerialName("cleared") CLEARED,
    @SerialName("bypassed") BYPASSED,
}

@Serializable
enum class RogueRunPhase {
    @SerialName("idle") IDLE,
    @SerialName("map") MAP,
    @SerialName("event") EVENT,
    @SerialName("challenge") CHALLENGE,
    @SerialName("cleared") CLEARED,
    @SerialName("over") OVER,
}

/**
 * 分支地图节点。非空字段不设默认值 → 始终被序列化（即便等于零值），避免 `next`/`state` 在
 * `encodeDefaults=false` 的共享 Json 下被省略导致另一端续局崩溃；`kcCode`/`challengeKind`
 * 可空，`explicitNulls=false` 下为 null 时省略，两端解析均容忍。
 */
@Serializable
data class RogueNode(
    val id: String,
    val layer: Int,
    val col: Int,
    val kind: RogueNodeKind,
    val kcCode: String? = null,
    /** 题型（Web ArenaLevelKind，如 dual_track/fill_blank…）；chest/rest 为 null。 */
    val challengeKind: String? = null,
    val difficulty: Int,
    val next: List<String>,
    val state: RogueNodeState,
)

@Serializable
data class RogueHistoryEntry(
    val nodeId: String,
    val kcCode: String? = null,
    val kind: RogueNodeKind,
    val passed: Boolean,
    val score: Int,
    val at: String,
)

data class RogueMap(
    val nodes: List<RogueNode>,
    val layers: Int,
)
