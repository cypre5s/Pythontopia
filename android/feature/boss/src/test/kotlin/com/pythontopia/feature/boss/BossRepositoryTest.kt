package com.pythontopia.feature.boss

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
class BossRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: BossRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BossApi::class.java)
        repository = BossRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchProblem 成功解析题目`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"problemId":"boss-3-1","chapter":3,"stage":1,"difficulty":2,"kcCode":"PY43-KC-020","statement":"实现循环求和","hints":["用 for"],"sampleInput":"5","sampleOutput":"15","totalTestCases":6,"reviewed":true,"aiGenerated":true}""",
            ),
        )
        val result = repository.fetchProblem(3, 1)
        assertTrue(result.isSuccess)
        val p = result.getOrThrow()
        assertEquals("boss-3-1", p.problemId)
        assertEquals(1, p.hints.size)
        assertEquals(6, p.totalTestCases)
    }

    @Test
    fun `submitAttempt 解析判题结果`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"problemId":"boss-3-1","passed":true,"passedTestCases":6,"totalTestCases":6,"score":120,"feedback":"漂亮"}""",
            ),
        )
        val result = repository.submitAttempt(3, 1, "boss-3-1", "print(15)", 42)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().passed)
        assertEquals(120, result.getOrThrow().score)
    }

    @Test
    fun `出题失败上抛 Result_failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val result = repository.fetchProblem(3, 1)
        assertFalse(result.isSuccess)
    }
}
