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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

/** `POST /api/workshop/resources/stream` 返回 text/event-stream（plan→trace*→result→error）；`GET .../{id}` 取生成物详情。 */
interface WorkshopGenApi {

    @Streaming
    @Headers("Accept: text/event-stream")
    @POST("api/workshop/resources/stream")
    suspend fun generateStream(@Body body: ResourceCreateRequestBody): ResponseBody

    @GET("api/workshop/resources/{id}")
    suspend fun resource(@Path("id") id: Long): WorkshopResourceDetailDto
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

    /**
     * AG-UI 双语解析：历史名（plan/trace/result/error）与 AG-UI 官方名
     * （CUSTOM(name=plan)/STEP_STARTED|FINISHED/RUN_FINISHED/RUN_ERROR/STATE_DELTA）均消费，
     * 与前端 `agui.ts#toAguiEvent` 对齐。RUN_FINISHED 结果在 `data.result` 内需拆包；
     * STATE_DELTA 取增量可读字段作为一条进度（HUD 实时同步的轻量映射）。
     */
    private fun parseEvent(event: String, data: String): WorkshopGenEvent? = runCatching {
        when (event) {
            "plan" -> toPlan(data)
            "CUSTOM" -> toCustom(data)
            "trace", "STEP_STARTED", "STEP_FINISHED" -> toTrace(data)
            "STATE_DELTA" -> toStateDelta(data)
            "result" -> toResult(data)
            "RUN_FINISHED" -> toResult(unwrap(data, "result"))
            "error", "RUN_ERROR" ->
                WorkshopGenEvent.Failed(json.decodeFromString<WorkshopGenErrorData>(data).message ?: "资源生成失败")
            else -> null
        }
    }.getOrNull()

    private fun toPlan(data: String): WorkshopGenEvent =
        json.decodeFromString<WorkshopGenPlanData>(data).let {
            WorkshopGenEvent.Plan(steps = it.steps, message = it.message)
        }

    private fun toCustom(data: String): WorkshopGenEvent? {
        val obj = json.parseToJsonElement(data).jsonObject
        val name = obj["name"]?.jsonPrimitive?.contentOrNull
        return if (name == "plan") toPlan(obj["value"]?.toString() ?: "{}") else null
    }

    private fun toTrace(data: String): WorkshopGenEvent =
        json.decodeFromString<WorkshopGenTraceData>(data).let {
            val label = it.label ?: it.summary ?: it.agent ?: it.node ?: "生成中…"
            WorkshopGenEvent.Trace(label = label, status = it.status)
        }

    private fun toStateDelta(data: String): WorkshopGenEvent? {
        val delta = json.parseToJsonElement(data).jsonObject["delta"] as? JsonObject ?: return null
        val label = STATE_DELTA_LABEL_KEYS
            .firstNotNullOfOrNull { key -> delta[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }
            ?: return null
        return WorkshopGenEvent.Trace(label = label, status = null)
    }

    private fun toResult(data: String): WorkshopGenEvent =
        json.decodeFromString<WorkshopGenResultData>(data).let {
            WorkshopGenEvent.Result(
                title = it.title.orEmpty().ifBlank { "已生成资源" },
                summary = it.summary.orEmpty(),
                resourceId = it.resourceId ?: it.id,
                groundingStatus = it.groundingStatus,
            )
        }

    /** 拆出 AG-UI 包装事件的内层对象（如 RUN_FINISHED 的 data.result）；缺失则回退原文。 */
    private fun unwrap(data: String, key: String): String =
        json.parseToJsonElement(data).jsonObject[key]?.toString() ?: data

    /** 取生成物详情；失败上抛 [Result]。 */
    suspend fun findResource(id: Long): Result<WorkshopResourceDetailDto> =
        runCatching { api.resource(id) }

    /** 按 type 宽松解码多态 content；结构不符/缺失回退 [WorkshopContentView.Unsupported]。 */
    fun parseContent(type: WorkshopResourceType, content: kotlinx.serialization.json.JsonElement?): WorkshopContentView {
        if (content == null) return WorkshopContentView.Unsupported
        return runCatching {
            when (type) {
                WorkshopResourceType.HANDOUT ->
                    WorkshopContentView.Handout(json.decodeFromJsonElement(WsHandoutContent.serializer(), content).sections)
                WorkshopResourceType.MINDMAP ->
                    json.decodeFromJsonElement(WsMindMapContent.serializer(), content)
                        .let { WorkshopContentView.MindMap(it.mermaid, it.kcMap) }
                WorkshopResourceType.PRACTICE ->
                    WorkshopContentView.Practice(json.decodeFromJsonElement(WsPracticeContent.serializer(), content).problems)
                WorkshopResourceType.CODE_LAB ->
                    WorkshopContentView.CodeLab(json.decodeFromJsonElement(WsCodeLabContent.serializer(), content).walkthroughs)
                WorkshopResourceType.READING ->
                    WorkshopContentView.Reading(json.decodeFromJsonElement(WsReadingContent.serializer(), content).items)
                WorkshopResourceType.STORYBOARD ->
                    WorkshopContentView.Storyboard(json.decodeFromJsonElement(WsStoryboardContent.serializer(), content).scenes)
            }
        }.getOrElse { WorkshopContentView.Unsupported }
    }

    private companion object {
        val STATE_DELTA_LABEL_KEYS = listOf("note", "message", "phase", "stage", "status")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object WorkshopGenNetworkModule {

    @Provides
    @Singleton
    fun provideWorkshopGenApi(retrofit: Retrofit): WorkshopGenApi =
        retrofit.create(WorkshopGenApi::class.java)
}
