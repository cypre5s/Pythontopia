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
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * 导师问答 Retrofit 端点。
 *
 * `POST /api/tutor/ask` 是 `text/event-stream`：用 [Streaming] 拿原始 [ResponseBody]，
 * 由 [TutorRepository.askStream] 手动按 SSE 协议逐行解析（避免引入 okhttp-sse 依赖，
 * 与后端 GraphSseConsumer 的 event/data 帧格式对齐）。
 */
interface TutorApi {

    @Streaming
    @Headers("Accept: text/event-stream")
    @POST("api/tutor/ask")
    suspend fun ask(@Body body: TutorAskRequestBody): ResponseBody

    @GET("api/tutor/sessions")
    suspend fun sessions(@Query("limit") limit: Int = 20): List<TutorSessionDto>
}

/**
 * 导师问答仓库：把 SSE 流解析为 [TutorStreamEvent] 序列。
 *
 * 事件帧（后端原样转发 Python graph）：
 *  - `event: token` / `data: {"delta": "..."}`  → [TutorStreamEvent.Token]
 *  - `event: result` / `data: {"answer_md","evidence_page_ids","grounding_status"}` → [TutorStreamEvent.Result]
 *  - `event: error` / `data: {"code","message"}` → [TutorStreamEvent.Failed]
 *  - `event: trace` 等 → 忽略（仅进度，无 UI 文本）。
 *
 * 网络/解析异常向上游抛出，由 ViewModel 兜底为错误态（不静默吞，便于学生看到失败）。
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

    private fun parseEvent(event: String, data: String): TutorStreamEvent? = runCatching {
        when (event) {
            "token" -> TutorStreamEvent.Token(json.decodeFromString<TutorTokenData>(data).delta)
            "result" -> json.decodeFromString<TutorResultData>(data).let {
                TutorStreamEvent.Result(
                    answerMd = it.answerMd.orEmpty(),
                    evidencePageIds = it.evidencePageIds,
                    groundingStatus = it.groundingStatus,
                )
            }
            "error" -> TutorStreamEvent.Failed(
                json.decodeFromString<TutorErrorData>(data).message ?: "导师服务出错",
            )
            else -> null
        }
    }.getOrNull()

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
