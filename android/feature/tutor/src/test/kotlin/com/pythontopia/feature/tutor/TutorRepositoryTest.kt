package com.pythontopia.feature.tutor

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit

class TutorRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: TutorRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TutorApi::class.java)
        repository = TutorRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `askStream 解析 token 增量与 result 终态`() = runTest {
        val sse = buildString {
            append("event: trace\ndata: {\"node\":\"TutorAgent\",\"status\":\"start\"}\n\n")
            append("event: token\ndata: {\"delta\":\"你好\"}\n\n")
            append("event: token\ndata: {\"delta\":\"，世界\"}\n\n")
            append(
                "event: result\ndata: {\"answer_md\":\"你好，世界\"," +
                    "\"evidence_page_ids\":[10707,10708],\"grounding_status\":\"PASSED\"}\n\n",
            )
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )

        val events = repository.askStream("for 和 while 区别？", listOf("PY43-KC-031")).toList()

        // trace 忽略，期望 token×2 + result×1
        assertEquals(3, events.size)
        assertTrue(events[0] is TutorStreamEvent.Token)
        assertEquals("你好", (events[0] as TutorStreamEvent.Token).delta)
        assertEquals("，世界", (events[1] as TutorStreamEvent.Token).delta)
        val result = events[2] as TutorStreamEvent.Result
        assertEquals("你好，世界", result.answerMd)
        assertEquals(listOf(10707, 10708), result.evidencePageIds)
        assertEquals("PASSED", result.groundingStatus)
    }

    @Test
    fun `askStream 解析 error 事件为 Failed`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: error\ndata: {\"code\":\"TUTOR_FAILED\",\"message\":\"上游超时\"}\n\n"),
        )

        val events = repository.askStream("x").toList()

        assertEquals(1, events.size)
        assertEquals("上游超时", (events[0] as TutorStreamEvent.Failed).message)
    }

    @Test
    fun `sessions 网络失败兜底空列表`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(emptyList<TutorSessionDto>(), repository.sessions())
    }
}
