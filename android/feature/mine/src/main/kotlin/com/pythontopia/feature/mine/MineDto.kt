package com.pythontopia.feature.mine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bug 矿洞副本 DTO 集合。
 *
 * UI 层仍保留"矿洞卡牌"模型；Retrofit wire DTO 在 [MineRepository] 内部适配
 * 后端 `/api/bug-mine/sessions` 的真实会话、卡片 payload 和 retry-submit 协议。
 *
 * 端点 → DTO 映射：
 * - `POST /api/bug-mine/sessions`              → [MineStartRequest] → [MineSession]
 * - `GET  /api/bug-mine/sessions/{id}`         → session.cards → `List<`[MineCard]`>`
 * - `POST /api/bug-mine/sessions/{id}/retry-submit` → [MineAttemptRequest] → [MineAttempt]
 *
 * 所有类型都带 `@Serializable`：DTO 与 ViewState 共用同一份 model 可减少层间转换
 * 噪音；Beta+1 接 OpenAPI Generator 时这层可被生成版本替换，业务代码不动。
 */

/**
 * 一次副本会话快照。
 *
 * `entry` 取值与后端 `BugMineCardSelectorService` 三条入口路径一致：
 * - `FAILED_SUBMISSION`：玩家最近 24h 内有失败提交
 * - `CLASSROOM_QUEST`：教师在班级公告板下发 `BUG_MINE_PACK` 任务
 * - `IDLE_RECOMMEND`：无失败 / 无班级任务，按 mastery 矩阵推荐补强 KC
 *
 * @property sessionId 后端自增 ID；后续 attempt/next 都以该 ID 关联。
 * @property entry 进入副本的来源，决定本次 5 卡套餐的选卡策略。
 * @property sourceSubmissionId 当 `entry == FAILED_SUBMISSION` 时为触发的提交 ID，否则 null。
 * @property currentLayer 当前所在层（1..5），对应 B1..B5；新会话默认 1。
 * @property totalCards 本次副本共发的卡数；默认 5 张（1 主卡 + 2 同主题 + 2 候补）。
 */
@Serializable
data class MineSession(
    val sessionId: Long,
    val entry: String = ENTRY_IDLE_RECOMMEND,
    @SerialName("sourceSubmissionId") val sourceSubmissionId: Long? = null,
    val currentLayer: Int = 1,
    val totalCards: Int = 5,
    val status: String = "OPEN",
) {
    companion object {
        const val ENTRY_FAILED_SUBMISSION = "FAILED_SUBMISSION"
        const val ENTRY_CLASSROOM_QUEST = "CLASSROOM_QUEST"
        const val ENTRY_IDLE_RECOMMEND = "IDLE_RECOMMEND"
    }
}

/**
 * 单张谜题卡。
 *
 * 字段命名与 `04-bugmine-puzzle-deck.md` 一一对应；`snippet` 是带错误的 Python
 * 代码片段，玩家直接在 [MineUiState.userCode] 编辑器里改写；`expectedOutput` 是
 * 服务端判题对 `stdout` 的目标字符串（含末尾换行）。
 *
 * @property id 后端 BugMineCard 行主键（与 `deckCardId` 共存：deckCardId 是业务唯一编号）。
 * @property deckCardId 卡牌业务编号，形如 `B1-01`～`B5-10`，与卡牌库附录 A 完全一致。
 * @property layer 难度层 1..5；UI 用来决定 [MineCardView] 的色带与 [MineLayerProgress] 高亮位置。
 * @property title 卡牌标题，如「无名魔兽」。
 * @property snippet 带错误的 Python 代码，玩家在此基础上修复。
 * @property expectedOutput 期望 stdout；包含末尾 `\n`。
 * @property kcCode 关联知识点编号，如 `PY43-KC-010`，对齐 `course_kc.json`。
 * @property pythocoReward 通关本卡奖励的 Pythoco；boss 卡可能有区间，前端只展示基础值。
 */
@Serializable
data class MineCard(
    val id: Long,
    val deckCardId: String,
    val layer: Int,
    val title: String,
    val snippet: String,
    val expectedOutput: String,
    val kcCode: String,
    val pythocoReward: Int = 0,
    val revealed: Boolean = true,
)

/**
 * 一次提交后的判定结果。
 *
 * 与 `:feature:arena` 的 `Submission` 精神一致，只暴露终态 + 玩家可读反馈。
 * `actualOutput` 在 FAIL 时让玩家看见自己的输出 vs `expectedOutput` 的差异，
 * 配合「输出对比放大镜」工具卡使用。
 *
 * @property cardId 来源卡 ID，与 [MineCard.id] 对齐。
 * @property code 玩家提交的代码原文（后端会回显，方便排错）。
 * @property status 终态：`PASS` / `FAIL` / `RUNTIME_ERROR` / `TIMEOUT`，与判题服务对齐。
 * @property actualOutput 服务端运行得到的实际 stdout；FAIL 时非空，PASS 时与 expected 一致。
 * @property message 给玩家的人类可读提示，例如 "样例 1 输出不一致"。
 */
@Serializable
data class MineAttempt(
    val cardId: Long,
    val code: String,
    val status: String,
    val actualOutput: String? = null,
    val message: String? = null,
) {
    companion object {
        const val STATUS_PASS = "PASS"
        const val STATUS_FAIL = "FAIL"
        const val STATUS_RUNTIME_ERROR = "RUNTIME_ERROR"
        const val STATUS_TIMEOUT = "TIMEOUT"
    }
}

/** [MineRepository.startSession] 的请求体。后端要求来自一个失败提交。 */
@Serializable
data class MineStartRequest(
    @SerialName("submissionId") val submissionId: Long,
)

/** [MineRepository.attempt] 的请求体；只携带玩家代码，cardId 走 URL path。 */
@Serializable
data class MineAttemptRequest(
    val code: String,
)
