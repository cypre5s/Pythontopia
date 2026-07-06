package com.pythontopia.core.data.dto.mainline

import kotlinx.serialization.Serializable

/**
 * 章测提交：`answers` 为 qid → 学生作答（CHOICE 选项标识 / JUDGE 真假 / FILL 文本 / CODING 源码）；
 * `integrity` 为前端尽力而为的防作弊计数（仅供教师参考，不自动判零）。
 */
@Serializable
data class ChapterTestSubmitRequest(
    val answers: Map<String, String>,
    val integrity: ChapterTestIntegrityReportDto? = null,
)

@Serializable
data class ChapterTestIntegrityReportDto(
    val pasteBlocked: Int? = null,
    val blurEvents: Int? = null,
    val overtime: Boolean? = null,
)
