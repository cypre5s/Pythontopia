package com.pythontopia.feature.expedition

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * 无尽远征网络 DTO。对齐后端：
 * - `POST /api/world/boss/select-frontier`（知识前沿选择器）
 * - `GET/PUT/DELETE /api/world/expedition/run`（run 跨会话持久化，ExpeditionRunDto 同构）
 * - `POST /api/world/boss/{chapter}/problem` · `/attempt`（复用 boss 出题/判题闭环）
 */

@Serializable
data class FrontierPick(
    val kcCode: String,
    val chapter: Int = 0,
    val difficulty: Int = 1,
    val reason: String = "",
)

@Serializable
data class FrontierSelectResultDto(
    val picks: List<FrontierPick> = emptyList(),
)

@Serializable
data class FrontierSelectRequest(
    val focusChapter: Int? = null,
    val recentKcs: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
    val count: Int,
)

/**
 * 一局远征 run 的可持久化快照，与后端 ExpeditionRunDto / Web ExpeditionRunSnapshot 同构。
 *
 * 标量字段用 @EncodeDefault(ALWAYS) 强制序列化：共享 Retrofit Json 的 encodeDefaults=false 下，
 * 满血(hp==5==默认)会被省略 → 后端 primitive int 落 0 → 续局秒死，故必须始终编码。
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class ExpeditionRunSnapshot(
    val phase: RogueRunPhase,
    val focusChapter: Int? = null,
    val nodes: List<RogueNode> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val layers: Int = 0,
    val currentNodeId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val hp: Int = RogueConfig.EXPEDITION_MAX_HP,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val maxHp: Int = RogueConfig.EXPEDITION_MAX_HP,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val momentum: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val bestMomentum: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val score: Int = 0,
    val relics: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val isDaily: Boolean = false,
    val history: List<RogueHistoryEntry> = emptyList(),
)

@Serializable
data class ExpeditionProblemRequest(
    val stage: Int,
    val preferredKc: String? = null,
    val preferredKind: String? = null,
    val difficulty: Int? = null,
)

@Serializable
data class ExpeditionProblemDto(
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
data class ExpeditionAttemptRequest(
    val problemId: String,
    val chapter: Int,
    val stage: Int,
    val code: String,
    val elapsedSec: Int,
)

@Serializable
data class ExpeditionAttemptResultDto(
    val problemId: String = "",
    val passed: Boolean = false,
    val passedTestCases: Int = 0,
    val totalTestCases: Int = 0,
    val failureFirstHint: String? = null,
    val score: Int = 0,
    val feedback: String = "",
)
