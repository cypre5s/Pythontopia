package com.pythontopia.feature.retro

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import retrofit2.HttpException
import retrofit2.Retrofit

/**
 * 用 [MockWebServer] 验证 [RetroRepository] 通过 Retrofit 真正发出的请求路径 / 查询串 /
 * 反序列化结果，不 mock interface，把契约风险（端点拼错、Query 参数漏掉）也覆盖。
 */
class RetroRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private lateinit var server: MockWebServer
    private lateinit var api: RetroApi
    private lateinit var repository: RetroRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(RetroApi::class.java)
        repository = RetroRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `thisWeek hits weekly endpoint with userId and decodes report`() = runTest {
        server.enqueue(jsonResponse(SAMPLE_REPORT_JSON))

        val report = repository.thisWeek(userId = "alice", weekIndex = 2812L)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/learner/retro/weekly", recorded.requestUrl?.encodedPath)
        assertEquals("2812", recorded.requestUrl?.queryParameter("weekIndex"))
        assertEquals("alice", recorded.requestUrl?.queryParameter("userId"))

        assertEquals("rep-1", report.reportId)
        assertEquals(2812L, report.weekIndex)
        assertEquals("2026-05-18", report.periodStart)
        assertEquals(2, report.kcGains.size)
        assertEquals("变量与赋值", report.kcGains.first().kcName)
        assertEquals(0.12, report.kcGains.first().masteryDelta)
        assertEquals(9, report.attempts)
        assertEquals(2, report.bugMineCount)
        assertEquals(1, report.generatedResources.size)
        assertEquals("mindmap", report.generatedResources.first().resourceType)
    }

    @Test
    fun `pastWeeks issues count sequential requests with descending weekIndex`() = runTest {
        repeat(3) { idx ->
            server.enqueue(jsonResponse(reportJson(weekIndex = 2812L - (idx + 1))))
        }

        val past = repository.pastWeeks(userId = "alice", count = 3, baseWeekIndex = 2812L)

        assertEquals(listOf(2811L, 2810L, 2809L), past.map { it.weekIndex })
        val orderedWeekParams = (1..3).map {
            server.takeRequest().requestUrl?.queryParameter("weekIndex")
        }
        assertEquals(listOf("2811", "2810", "2809"), orderedWeekParams)
    }

    @Test
    fun `pastWeeks rejects non-positive count`() = runTest {
        assertThrows<IllegalArgumentException> {
            repository.pastWeeks(userId = "alice", count = 0, baseWeekIndex = 100L)
        }
    }

    @Test
    fun `markRead posts to read endpoint with reportId path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        repository.markRead("report-77")

        val recorded: RecordedRequest = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/learner/retro/report-77/read", recorded.requestUrl?.encodedPath)
        assertEquals(0L, recorded.bodySize)
    }

    @Test
    fun `markRead rejects blank reportId before hitting network`() = runTest {
        assertThrows<IllegalArgumentException> {
            repository.markRead("   ")
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `thisWeek propagates 5xx via HttpException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"boom"}"""),
        )

        val ex = assertThrows<HttpException> {
            repository.thisWeek(userId = "alice", weekIndex = 1L)
        }
        assertEquals(500, ex.code())
    }

    @Test
    fun `weekly report tolerates missing optional fields`() = runTest {
        // 后端在 demo 阶段可能漏发 generatedResources / bugMineCount，
        // 校验 DTO 默认值兜底（也间接验证 explicitNulls=false 的配置）。
        server.enqueue(
            jsonResponse(
                """
                {
                  "reportId":"rep-min",
                  "userId":"alice",
                  "weekIndex":2812,
                  "periodStart":"2026-05-18",
                  "periodEnd":"2026-05-24"
                }
                """.trimIndent(),
            ),
        )

        val report = repository.thisWeek(userId = "alice", weekIndex = 2812L)

        assertEquals("rep-min", report.reportId)
        assertTrue(report.kcGains.isEmpty())
        assertTrue(report.generatedResources.isEmpty())
        assertEquals(0, report.attempts)
        assertEquals(0, report.bugMineCount)
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(body)

    private fun reportJson(weekIndex: Long): String = """
        {
          "reportId":"rep-$weekIndex",
          "userId":"alice",
          "weekIndex":$weekIndex,
          "periodStart":"2026-05-${10 + weekIndex % 10}",
          "periodEnd":"2026-05-${16 + weekIndex % 10}",
          "kcGains":[],
          "attempts":0,
          "bugMineCount":0,
          "generatedResources":[]
        }
    """.trimIndent()

    private companion object {
        const val SAMPLE_REPORT_JSON: String = """
        {
          "reportId":"rep-1",
          "userId":"alice",
          "weekIndex":2812,
          "periodStart":"2026-05-18",
          "periodEnd":"2026-05-24",
          "kcGains":[
            {"kcId":"kc-1","kcName":"变量与赋值","masteryDelta":0.12,"masteryAfter":0.62},
            {"kcId":"kc-2","kcName":"if 条件","masteryDelta":0.07,"masteryAfter":0.55}
          ],
          "attempts":9,
          "bugMineCount":2,
          "generatedResources":[
            {"resourceId":"res-1","title":"本周脑图","resourceType":"mindmap","url":"https://example.org/m.png","createdAt":"2026-05-24T02:00:00Z"}
          ],
          "read":false
        }
        """
    }
}
