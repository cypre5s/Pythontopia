package com.pythontopia.feature.mine

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

internal interface MineApi {

    @POST("api/bug-mine/sessions")
    suspend fun startSession(@Body request: MineStartRequest): BugMineSessionWireDto

    @GET("api/bug-mine/sessions/{id}")
    suspend fun session(@Path("id") sessionId: Long): BugMineSessionWireDto

    @POST("api/bug-mine/sessions/{id}/cards/{cardId}/reveal")
    suspend fun reveal(
        @Path("id") sessionId: Long,
        @Path("cardId") cardId: Long,
    ): BugMineCardWireDto

    @POST("api/bug-mine/sessions/{id}/retry-submit")
    suspend fun retrySubmit(
        @Path("id") sessionId: Long,
        @Body request: MineAttemptRequest,
    ): MineSubmissionWireDto

    @POST("api/bug-mine/sessions/{id}/close")
    suspend fun close(
        @Path("id") sessionId: Long,
        @Query("status") status: String,
    ): BugMineSessionWireDto
}

@Module
@InstallIn(SingletonComponent::class)
internal object MineApiModule {

    @Provides
    @Singleton
    fun provideMineApi(retrofit: Retrofit): MineApi = retrofit.create(MineApi::class.java)
}

/** Bug 矿洞仓库：须带失败 `submissionId` 开副本；判题走 `/retry-submit`，通过后由后端写入 DebugResolved。 */
@Singleton
class MineRepository @Inject internal constructor(
    private val api: MineApi,
) {

    suspend fun startSession(srcSubmissionId: Long?): MineSession {
        val submissionId = requireNotNull(srcSubmissionId) {
            "Bug 矿洞需要从一次失败提交进入；请先在代码工坊或训练场提交未通过代码"
        }
        return api.startSession(MineStartRequest(submissionId = submissionId)).toMineSession()
    }

    suspend fun cards(sessionId: Long): List<MineCard> =
        api.session(sessionId).cards.mapIndexed { index, card -> card.toMineCard(index) }

    suspend fun attempt(sessionId: Long, code: String): MineAttempt =
        api.retrySubmit(sessionId, MineAttemptRequest(code = code)).toMineAttempt()

    suspend fun next(sessionId: Long): MineCard? {
        val session = api.session(sessionId)
        val next = session.cards.firstOrNull { !it.revealed } ?: return null
        return api.reveal(sessionId, next.id).toMineCard(session.cards.indexOf(next))
    }
}

@Serializable
internal data class BugMineSessionWireDto(
    val id: Long,
    val submissionId: Long? = null,
    val problemId: Long? = null,
    val status: String = "OPEN",
    val retryCount: Int = 0,
    val cards: List<BugMineCardWireDto> = emptyList(),
) {
    fun toMineSession(): MineSession =
        MineSession(
            sessionId = id,
            entry = MineSession.ENTRY_FAILED_SUBMISSION,
            sourceSubmissionId = submissionId,
            currentLayer = (cards.count { it.revealed } + 1).coerceIn(1, 5),
            totalCards = cards.size.coerceAtLeast(1),
            status = status,
        )
}

@Serializable
internal data class BugMineCardWireDto(
    val id: Long,
    val sessionId: Long,
    val cardType: String,
    val payload: JsonObject = JsonObject(emptyMap()),
    val revealed: Boolean = false,
) {
    fun toMineCard(index: Int): MineCard {
        val expected = payload.string("expected_stdout").orEmpty()
        return MineCard(
            id = id,
            deckCardId = cardType,
            layer = (index + 1).coerceIn(1, 5),
            title = titleFor(cardType),
            snippet = "阅读提示后，在这里粘贴你的修复代码。\n\n# ${bodyFrom(payload)}\n",
            expectedOutput = expected,
            kcCode = kcFrom(payload),
            pythocoReward = if (revealed) 5 else 0,
            revealed = revealed,
        )
    }

    private fun titleFor(type: String): String = when (type) {
        "COUNTER_EXAMPLE" -> "反例矿脉"
        "VARIABLE_TRACE" -> "变量轨迹"
        "WRONG_ASSUMPTION" -> "错误假设"
        "HINT_LAYER_1" -> "一层提示"
        "HINT_LAYER_2" -> "二层提示"
        "HINT_LAYER_3" -> "三层提示"
        else -> type
    }

    private fun bodyFrom(payload: JsonObject): String =
        payload.string("body")
            ?: payload.string("diff_summary")
            ?: payload.string("actually")
            ?: payload.string("summary")
            ?: payload.toString()

    private fun kcFrom(payload: JsonObject): String =
        payload.arrayStrings("evidence_kc_codes").firstOrNull()
            ?: payload.arrayStrings("expected_kc_codes").firstOrNull()
            ?: "PY43-KC-016"
}

@Serializable
internal data class MineSubmissionWireDto(
    val id: Long,
    val problemId: Long,
    val code: String,
    val status: String,
    val runtimeMs: Int? = null,
) {
    fun toMineAttempt(): MineAttempt =
        MineAttempt(
            cardId = id,
            code = code,
            status = when (status) {
                "PASSED" -> MineAttempt.STATUS_PASS
                "ERROR" -> MineAttempt.STATUS_RUNTIME_ERROR
                else -> MineAttempt.STATUS_FAIL
            },
            actualOutput = null,
            message = when (status) {
                "PASSED" -> runtimeMs?.let { "修复提交通过，耗时 ${it}ms" } ?: "修复提交通过"
                "ERROR" -> "服务端运行错误，请检查异常信息和输入处理"
                else -> "服务端判题未通过，请继续利用矿洞提示修复"
            },
        )
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.arrayStrings(key: String): List<String> =
    (this[key] as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .orEmpty()
