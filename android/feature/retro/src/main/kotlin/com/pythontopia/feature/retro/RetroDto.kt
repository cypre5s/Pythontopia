package com.pythontopia.feature.retro

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 周复盘报告契约（前端→后端，`GET /api/learner/retro/weekly`）。
 *
 * 设计原则：
 *  - 字段对齐 `learner_evaluation` + `pyt_resource_run` + `kc_mastery_delta` 的物化视图，
 *    后端按 `userId` + `weekIndex` 聚合一次返回，避免 Android 端二次拼装；
 *  - `weekIndex` 用 ISO 周编号的 epochDay/7（Long），客户端可在不依赖 datetime 库的情况下
 *    与服务端使用同一刻度切换上一周/本周；
 *  - 所有字段默认值兜底，保持 demo 阶段后端字段缺失时 UI 仍可渲染空状态。
 *
 * 与 AGENTS.md `## 防幻觉与安全` 一致：`generatedResources` 仅来源于服务端 RAG 已校验产物，
 * Android 不在本地拼接资源；点击跳转走外部浏览器，不在 retro 内做内联展示。
 */
@Serializable
data class RetroReport(
    val reportId: String,
    val userId: String,
    val weekIndex: Long,
    val periodStart: String,
    val periodEnd: String,
    val kcGains: List<KcGain> = emptyList(),
    val attempts: Int = 0,
    val bugMineCount: Int = 0,
    val generatedResources: List<ResourceDto> = emptyList(),
    @SerialName("read") val isRead: Boolean = false,
)

/**
 * 单个 KC 在本周窗口内的 mastery 增量。
 *
 *  - `masteryDelta` 可正可负；负值代表退化（遗忘曲线生效），由 `KcGainBarChart` 区分配色；
 *  - `masteryAfter` 为本周末快照，落在 [0.0, 1.0]，用于显示当前掌握度文案；
 *  - 取前 10 项展示（按 masteryDelta 降序）的逻辑放在 UI 层而非 DTO 层。
 */
@Serializable
data class KcGain(
    val kcId: String,
    val kcName: String,
    val masteryDelta: Double,
    val masteryAfter: Double = 0.0,
)

/**
 * 复盘期间多智能体生成的学习资源摘要（mindmap / slides / notes / quiz_set 等）。
 *
 *  - `resourceType` 对齐后端 `pyt_resource.resource_type` 枚举字符串；
 *  - `url` 缺省时退化为占位（UI 显示「资源未渲染」），可避免点击空跳；
 *  - `createdAt` 是 ISO-8601 字符串，UI 直接显示，不在客户端解析时区。
 */
@Serializable
data class ResourceDto(
    val resourceId: String,
    val title: String,
    val resourceType: String,
    val url: String? = null,
    val createdAt: String? = null,
)
