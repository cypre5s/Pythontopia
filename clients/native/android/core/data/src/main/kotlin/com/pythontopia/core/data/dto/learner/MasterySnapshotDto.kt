package com.pythontopia.core.data.dto.learner

import kotlinx.serialization.Serializable

/** KC 掌握度快照，对应后端 `MasterySnapshotDto`（BigDecimal 以 JSON number 出参，建模为 Double）。 */
@Serializable
data class MasterySnapshotDto(
    val kcCode: String,
    val masteryScore: Double? = null,
    val confidence: Double? = null,
    val lastEventAt: String? = null,
    val latestSource: String? = null,
)
