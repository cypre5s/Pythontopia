package com.pythontopia.feature.tutor

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

/** `POST /api/tutor/ask` 返回 `text/event-stream`；[askStream] 手动解析 SSE event/data 帧，与 GraphSseConsumer 对齐。 */
interface TutorApi {

    @Streaming
    @Headers("Accept: text/event-stream")
    @POST("api/tutor/ask")
    suspend fun ask(@Body body: TutorAskRequestBody): ResponseBody

    @GET("api/tutor/sessions")
    suspend fun sessions(@Query("limit") limit: Int = 20): List<TutorSessionDto>
}

/**
 * SSE 事件映射：token→[TutorStreamEvent.Token]、result→Result（含 evidence/grounding）、error→Failed；trace 等进度帧忽略。
 * 网络/解析异常向上游抛出，不静默吞。
 */
@Singleton
class TutorRepository @Inject constructor(
    private val api: TutorApi,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun askStream(
        question: String,
        kcCodes: List<String>? = null,
        context: String? = null,
    ): Flow<TutorStreamEvent> = flow {
        val body = api.ask(
            TutorAskRequestBody(
                question = question,
                kcCodes = kcCodes?.takeIf { it.isNotEmpty() },
                context = context?.takeIf { it.isNotBlank() },
            ),
        )
        body.use { rb ->
            val reader = rb.charStream().buffered()
            var event = "message"
            val data = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                val l = line
                when {
                    l.startsWith("event:") -> event = l.substring("event:".length).trim()
                    l.startsWith("data:") -> data.append(l.substring("data:".length).trim())
                    l.isEmpty() && data.isNotEmpty() -> {
                        parseEvent(event, data.toString())?.let { emit(it) }
                        event = "message"
                        data.setLength(0)
                    }
                }
                line = reader.readLine()
            }
            if (data.isNotEmpty()) {
                parseEvent(event, data.toString())?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * AG-UI 双语解析：同时接受项目历史事件名（token/result/error）与 AG-UI 官方名
     * （TEXT_MESSAGE_CONTENT/RUN_FINISHED/RUN_ERROR），与前端 `agui.ts#toAguiEvent` 对齐，
     * 使后端灰度迁移到 AG-UI 命名后 Android 流不回归。RUN_FINISHED 的结果在 `data.result` 内需拆包。
     */
    private fun parseEvent(event: String, data: String): TutorStreamEvent? = runCatching {
        when (event) {
            "token", "TEXT_MESSAGE_CONTENT" ->
                TutorStreamEvent.Token(json.decodeFromString<TutorTokenData>(data).delta)
            "result" -> toResult(data)
            "RUN_FINISHED" -> toResult(unwrap(data, "result"))
            "error", "RUN_ERROR" ->
                TutorStreamEvent.Failed(json.decodeFromString<TutorErrorData>(data).message ?: "导师服务出错")
            else -> null
        }
    }.getOrNull()

    private fun toResult(data: String): TutorStreamEvent =
        json.decodeFromString<TutorResultData>(data).let {
            TutorStreamEvent.Result(
                answerMd = it.answerMd.orEmpty(),
                evidencePageIds = it.evidencePageIds,
                groundingStatus = it.groundingStatus,
            )
        }

    /** 拆出 AG-UI 包装事件的内层对象（如 RUN_FINISHED 的 data.result）；缺失则回退原文。 */
    private fun unwrap(data: String, key: String): String =
        json.parseToJsonElement(data).jsonObject[key]?.toString() ?: data

    suspend fun sessions(limit: Int = 20): List<TutorSessionDto> =
        runCatching { api.sessions(limit) }.getOrElse { emptyList() }
}

@Module
@InstallIn(SingletonComponent::class)
object TutorNetworkModule {

    @Provides
    @Singleton
    fun provideTutorApi(retrofit: Retrofit): TutorApi =
        retrofit.create(TutorApi::class.java)
}
