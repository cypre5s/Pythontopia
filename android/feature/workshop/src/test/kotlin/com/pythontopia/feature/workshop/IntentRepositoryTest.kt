package com.pythontopia.feature.workshop

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

/** MockWebServer 集成测试：意图路由请求序列化、REVIEW 响应反序列化、空集合参数剔除。 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntentRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: IntentRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                Json { ignoreUnknownKeys = true; explicitNulls = false }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
        repository = IntentRepository(retrofit.create(IntentApi::class.java))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `route posts message and parses review response`() = runTest {
        server.enqueue(
            jsonOk(
                """{"intent":"REVIEW","kcCodes":["PY43-KC-001"],"focusHint":"复盘 for 循环","confidence":0.7,"reason":"用户想复习巩固"}""",
            ),
        )

        val response = repository.route(message = "我想复习一下 for 循环")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/intent/route", request.path)
        assertTrue(request.body.readUtf8().contains("\"message\":\"我想复习一下 for 循环\""))
        assertEquals(IntentKind.REVIEW, response.kind)
        assertEquals(listOf("PY43-KC-001"), response.kcCodes)
        assertEquals("复盘 for 循环", response.focusHint)
    }

    @Test
    fun `route omits empty optional collections from the request body`() = runTest {
        server.enqueue(jsonOk("""{"intent":"ASK","kcCodes":[],"confidence":0.5}"""))

        repository.route(message = "什么是缩进", manualKcCodes = emptyList(), recentKcCodes = emptyList())

        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("manualKcCodes"))
        assertFalse(body.contains("recentKcCodes"))
        assertFalse(body.contains("courseKcs"))
    }

    @Test
    fun `route forwards manual kc codes when present`() = runTest {
        server.enqueue(jsonOk("""{"intent":"PRACTICE","kcCodes":["PY43-KC-016"],"confidence":0.8}"""))

        val response = repository.route(
            message = "给我出几道循环练习",
            manualKcCodes = listOf("PY43-KC-016"),
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"manualKcCodes\":[\"PY43-KC-016\"]"))
        assertEquals(IntentKind.PRACTICE, response.kind)
    }

    private fun jsonOk(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
}
