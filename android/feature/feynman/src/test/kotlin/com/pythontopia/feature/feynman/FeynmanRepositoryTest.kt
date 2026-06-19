package com.pythontopia.feature.feynman

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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

@Suppress("MaxLineLength") // MockWebServer JSON 夹具单行更易读，豁免行宽
class FeynmanRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: FeynmanRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FeynmanApi::class.java)
        repository = FeynmanRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `startSession 解析会话与误解`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":7,"userId":3,"kcCode":"PY43-KC-016","status":"OPEN","tuteeState":{"misconceptions":[{"tag":"off-by-one","evidenceEventIds":[11,12],"resolved":false}]}}""",
            ),
        )
        val result = repository.startSession("PY43-KC-016")
        assertTrue(result.isSuccess)
        val s = result.getOrThrow()
        assertEquals(7L, s.id)
        assertEquals("PY43-KC-016", s.kcCode)
        assertEquals(1, s.tuteeState.misconceptions.size)
        assertEquals(listOf(11L, 12L), s.tuteeState.misconceptions.first().evidenceEventIds)
    }

    @Test
    fun `teach 解析学弟轮次_taughtFix 承载产出代码`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":21,"role":"TUTEE","content":"我懂了：先说原因再改代码","taughtFix":"print('ok')"}""",
            ),
        )
        val result = repository.teach(7L, "因为索引从 0 开始，range(n) 不含 n")
        assertTrue(result.isSuccess)
        val turn = result.getOrThrow()
        assertEquals("TUTEE", turn.role)
        assertEquals("print('ok')", turn.taughtFix)
    }

    @Test
    fun `judge 解析裁定`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"sessionId":7,"passed":true,"message":"学弟通过真判题"}"""),
        )
        val result = repository.judge(7L)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().passed)
        assertEquals("学弟通过真判题", result.getOrThrow().message)
    }

    @Test
    fun `服务不可用上抛 Result_failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val result = repository.startSession("PY43-KC-016")
        assertFalse(result.isSuccess)
    }
}
