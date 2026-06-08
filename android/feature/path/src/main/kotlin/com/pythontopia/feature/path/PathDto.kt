package com.pythontopia.feature.path

import kotlinx.serialization.Serializable

/**
 * 学习路径节点类型；后端 5 元 `nodeType` 在 [PathRepository] wire 层收敛为 4 类：
 * READ（课件/资源）、PRACTICE（训练场）、QUEST（任务）、DEBUG（Bug 矿洞）。
 */
@Serializable
enum class PathNodeType {
    READ,
    PRACTICE,
    QUEST,
    DEBUG,
}

/** 节点状态机 LOCKED→UNLOCKED→IN_PROGRESS→DONE；仅 UNLOCKED/IN_PROGRESS 可推进。 */
@Serializable
enum class PathNodeStatus {
    LOCKED,
    UNLOCKED,
    IN_PROGRESS,
    DONE,
}

/** 单个路径节点；[status] 由后端维护，[type] 决定跳转 feature，[nextIds] 为空表示终点。 */
@Serializable
data class PathNodeDto(
    val id: String,
    val type: PathNodeType,
    val title: String,
    val kcCode: String? = null,
    val status: PathNodeStatus,
    val nextIds: List<String> = emptyList(),
)

/** 学习路径快照；同一用户同一 packId 仅一条 ACTIVE 路径，节点顺序由 [PathRepository] 按 dayIndex/orderInDay 排序。 */
@Serializable
data class PathDto(
    val pathId: String,
    val userId: Long,
    val packId: Long,
    val currentNodeId: String? = null,
    val nodes: List<PathNodeDto> = emptyList(),
)
