package com.pythontopia.feature.boss

import kotlinx.serialization.Serializable

/**
 * BOSS 战 DTO，对齐 `/api/world/boss/{chapter}/problem` 与 `/attempt`。
 * 请求体仅 stage 必填；其余 null 由 encodeDefaults=false 省略，后端按 stage 推断。
 */

@Serializable
data class BossProblemRequestBody(
    val stage: Int,
    val kcCode: String? = null,
    val difficulty: Int? = null,
    val preferredKc: String? = null,
    val preferredKind: String? = null,
)

@Serializable
data class BossProblemDto(
    val problemId: String,
    val chapter: Int = 0,
    val stage: Int = 0,
    val difficulty: Int = 0,
    val kcCode: String? = null,
    val statement: String = "",
    val hints: List<String> = emptyList(),
    val sampleInput: String? = null,
    val sampleOutput: String? = null,
    val totalTestCases: Int = 0,
    val reviewed: Boolean = false,
    val aiGenerated: Boolean = false,
)

@Serializable
data class BossAttemptRequestBody(
    val problemId: String,
    val chapter: Int,
    val stage: Int,
    val code: String,
    val elapsedSec: Int,
)

@Serializable
data class BossAttemptResultDto(
    val problemId: String = "",
    val passed: Boolean = false,
    val passedTestCases: Int = 0,
    val totalTestCases: Int = 0,
    val failureFirstHint: String? = null,
    val score: Int = 0,
    val feedback: String = "",
)
