package com.pythontopia.feature.path

import kotlinx.serialization.Serializable

/**
 * 学习路径节点类型。
 *
 * 与 web `LearningPathNodeDto.nodeType` 的 5 元枚举（READ_RESOURCE/SOLVE_PROBLEM/FIX_BUG/PLANT_KC/PROFILE_UPDATE）
 * 收敛为 Android demo 的 4 大类活动：
 *  - [READ]    : 看课件 / 看资源（HANDOUT、MINDMAP、READING、STORYBOARD）
 *  - [PRACTICE]: 在训练场刷题
 *  - [QUEST]   : 在小镇里跟 NPC / 班级跑剧情任务
 *  - [DEBUG]   : 进入 Bug 矿洞调试
 *
 * 真实后端如果返回 5 元枚举，由 [PathRepository] 在 wire 层做收敛映射，UI 层只看 4 元。
 */
@Serializable
enum class PathNodeType {
    READ,
    PRACTICE,
    QUEST,
    DEBUG,
}

/**
 * 节点状态机：LOCKED -> UNLOCKED -> IN_PROGRESS -> DONE。
 *
 * UI 用 4 色高亮区分；点击合法路径只允许 UNLOCKED / IN_PROGRESS。
 * LOCKED 节点点击仅展示"前置节点未完成"提示，DONE 节点点击允许"复习"跳转。
 */
@Serializable
enum class PathNodeStatus {
    LOCKED,
    UNLOCKED,
    IN_PROGRESS,
    DONE,
}

/**
 * 单个路径节点。
 *
 * @param id        节点稳定 ID（后端生成；UI 不参与生成）。
 * @param type      4 元活动类型，决定点击跳转目标 feature。
 * @param title     节点显示名（人类可读，已 i18n）。
 * @param kcCode    关联 KC（Pythontopia packId=43 共 65 个），可为空（如 PROFILE_UPDATE 类节点）。
 * @param status    4 态状态机，由后端维护，UI 不本地推进，避免与 server-side mastery 不一致。
 * @param nextIds   后继节点 ID 列表；空表示路径终点，多于 1 表示分叉。
 */
@Serializable
data class PathNodeDto(
    val id: String,
    val type: PathNodeType,
    val title: String,
    val kcCode: String? = null,
    val status: PathNodeStatus,
    val nextIds: List<String> = emptyList(),
)

/**
 * 完整学习路径快照。
 *
 * @param pathId        路径 ID（同一用户同一 packId 任意时刻只有一个 ACTIVE 路径）。
 * @param userId        学习者 ID。
 * @param packId        课程包 ID；Pythontopia 默认 43（`Python语言基础`）。
 * @param currentNodeId 当前活跃节点（IN_PROGRESS 的优先，否则最近 UNLOCKED）；可为 null（空路径）。
 * @param nodes         所有节点；前端不假设排序，由 [PathRepository] 调用方按 `nextIds` 拓扑排序。
 */
@Serializable
data class PathDto(
    val pathId: String,
    val userId: Long,
    val packId: Long,
    val currentNodeId: String? = null,
    val nodes: List<PathNodeDto> = emptyList(),
)
