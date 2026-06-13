package com.pythontopia.feature.workshop

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
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming

/** `POST /api/workshop/resources/stream` 返回 text/event-stream（plan→trace*→result→error）。 */
interface WorkshopGenApi {

    @Streaming
    @Headers("Accept: text/event-stream")
    @POST("api/workshop/resources/stream")
    suspend fun generateStream(@Body body: ResourceCreateRequestBody): ResponseBody
}

/**
 * 资源生成仓库：流式消费工坊多智能体生成事件，手动解析 SSE event/data 帧（与 tutor 一致）。
 * plan→[WorkshopGenEvent.Plan]、trace→Trace、result→Result、error→Failed；网络/解析异常向上游抛出。
 */
@Singleton
class WorkshopGenRepository @Inject constructor(
    private val api: WorkshopGenApi,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun generateStream(
        type: WorkshopResourceType,
        kcCodes: List<String>,
        focusHint: String? = null,
    ): Flow<WorkshopGenEvent> = flow {
        val body = api.generateStream(
            ResourceCreateRequestBody(
                kcCodes = kcCodes,
                type = type,
                focusHint = focusHint?.takeIf { it.isNotBlank() },
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

    private fun parseEvent(event: String, data: String): WorkshopGenEvent? = runCatching {
        when (event) {
            "plan" -> json.decodeFromString<WorkshopGenPlanData>(data).let {
                WorkshopGenEvent.Plan(steps = it.steps, message = it.message)
            }
            "trace" -> json.decodeFromString<WorkshopGenTraceData>(data).let {
                val label = it.label ?: it.summary ?: it.agent ?: it.node ?: "生成中…"
                WorkshopGenEvent.Trace(label = label, status = it.status)
            }
            "result" -> json.decodeFromString<WorkshopGenResultData>(data).let {
                WorkshopGenEvent.Result(
                    title = it.title.orEmpty().ifBlank { "已生成资源" },
                    summary = it.summary.orEmpty(),
                    resourceId = it.resourceId ?: it.id,
                    groundingStatus = it.groundingStatus,
                )
            }
            "error" -> WorkshopGenEvent.Failed(
                json.decodeFromString<WorkshopGenErrorData>(data).message ?: "资源生成失败",
            )
            else -> null
        }
    }.getOrNull()
}

@Module
@InstallIn(SingletonComponent::class)
object WorkshopGenNetworkModule {

    @Provides
    @Singleton
    fun provideWorkshopGenApi(retrofit: Retrofit): WorkshopGenApi =
        retrofit.create(WorkshopGenApi::class.java)
}
