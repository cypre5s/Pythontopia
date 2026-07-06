package com.pythontopia.feature.teacherreview

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
class TeacherReviewRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: TeacherReviewRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TeacherReviewApi::class.java)
        repository = TeacherReviewRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchProblem 解析待审题与审查状态`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"problemId":"boss-3-1","chapter":3,"stage":1,"difficulty":2,"kcCode":"PY43-KC-020","statement":"实现循环求和","hints":["用 for"],"sampleInput":"5","sampleOutput":"15","totalTestCases":6,"reviewed":true,"aiGenerated":true}""",
            ),
        )
        val result = repository.fetchProblem(3, 1)
        assertTrue(result.isSuccess)
        val p = result.getOrThrow()
        assertEquals("boss-3-1", p.problemId)
        assertTrue(p.reviewed)
        assertTrue(p.aiGenerated)
        assertEquals(6, p.totalTestCases)

        val recorded = server.takeRequest()
        assertEquals("/api/world/boss/3/problem", recorded.path)
    }

    @Test
    fun `出题失败上抛 Result_failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val result = repository.fetchProblem(1, 1)
        assertFalse(result.isSuccess)
    }
}
