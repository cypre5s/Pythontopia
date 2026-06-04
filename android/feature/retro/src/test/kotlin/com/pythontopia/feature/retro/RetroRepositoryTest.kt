package com.pythontopia.feature.retro

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Retrofit

class RetroRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: RetroRepository

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
        repository = RetroRepository(retrofit.create(RetroApi::class.java))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `thisWeek reads latest evaluation and maps it to report`() = runTest {
        server.enqueue(jsonOk("[$EVALUATION_JSON]"))

        val report = repository.thisWeek(userId = "current-user")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/learner/evaluations", request.requestUrl?.encodedPath)
        assertEquals("1", request.requestUrl?.queryParameter("limit"))
        assertEquals("501", report.reportId)
        assertEquals("42", report.userId)
        assertEquals("2026-05-18", report.periodStart)
        assertEquals("2026-05-24", report.periodEnd)
        assertEquals(2, report.attempts)
        assertEquals(1, report.bugMineCount)
        assertEquals(3, report.kcGains.size)
        assertEquals("练习提交数", report.kcGains.first().kcName)
    }

    @Test
    fun `thisWeek computes evaluation when list is empty`() = runTest {
        server.enqueue(jsonOk("[]"))
        server.enqueue(jsonOk(EVALUATION_JSON))

        val report = repository.thisWeek(userId = "current-user")

        assertEquals("501", report.reportId)
        assertEquals("/api/learner/evaluations?limit=1", server.takeRequest().path)
        val compute = server.takeRequest()
        assertEquals("POST", compute.method)
        assertEquals("/api/learner/evaluations", compute.path)
        assertTrue(compute.body.readUtf8().contains("\"periodDays\":7"))
    }

    @Test
    fun `pastWeeks reads recent evaluations after current one`() = runTest {
        server.enqueue(jsonOk("[$EVALUATION_JSON,${evaluationJson(id = 502L, end = "2026-05-17")}]"))

        val reports = repository.pastWeeks(userId = "current-user", count = 1)

        assertEquals(1, reports.size)
        assertEquals("502", reports.first().reportId)
        assertEquals("2", server.takeRequest().requestUrl?.queryParameter("limit"))
    }

    @Test
    fun `pastWeeks rejects non-positive count`() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.pastWeeks(userId = "current-user", count = 0) }
        }
    }

    @Test
    fun `markRead validates report id without hitting network`() = runTest {
        repository.markRead("501")
        assertEquals(0, server.requestCount)
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.markRead(" ") }
        }
    }

    @Test
    fun `thisWeek propagates server errors`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        val error = assertThrows(HttpException::class.java) {
            kotlinx.coroutines.runBlocking { repository.thisWeek(userId = "current-user") }
        }

        assertEquals(500, error.code())
    }

    private fun jsonOk(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun evaluationJson(id: Long, end: String): String = EVALUATION_JSON
        .replace("\"id\":501", "\"id\":$id")
        .replace("\"periodEnd\":\"2026-05-24\"", "\"periodEnd\":\"$end\"")

    private companion object {
        const val EVALUATION_JSON = """
        {
          "id":501,
          "userId":42,
          "packId":43,
          "periodStart":"2026-05-18T00:00:00Z",
          "periodEnd":"2026-05-24T00:00:00Z",
          "narrativeSummary":"本周练习稳定",
          "actedStatus":"PENDING",
          "metrics":[
            {"metricKey":"practice_attempts","value":2,"notes":"练习提交数"},
            {"metricKey":"bug_mine_count","value":1,"notes":"Bug 修复数"},
            {"metricKey":"mastery_delta","value":0.14,"denominator":0.62,"notes":"掌握度提升"}
          ]
        }
        """
    }
}
