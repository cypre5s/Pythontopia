package com.pythontopia.feature.retro

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 周复盘报告；由 LearnerEvaluation 映射，`weekIndex` 为 epochDay/7，`generatedResources` 仅来自服务端。 */
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

/** 单 KC 周窗口掌握度增量；`masteryDelta` 可正可负，`masteryAfter` 为周末快照 [0.0, 1.0]。 */
@Serializable
data class KcGain(
    val kcId: String,
    val kcName: String,
    val masteryDelta: Double,
    val masteryAfter: Double = 0.0,
)

/** 复盘生成的学习资源摘要；`resourceType` 对齐 `pyt_resource.resource_type`，`url` 缺省时不响应点击。 */
@Serializable
data class ResourceDto(
    val resourceId: String,
    val title: String,
    val resourceType: String,
    val url: String? = null,
    val createdAt: String? = null,
)
