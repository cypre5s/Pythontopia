package com.pythontopia.feature.workshop

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

/**
 * MockWebServer 集成测试：直接对 [WorkshopRepository] + [WorkshopApi] 做 HTTP wire 级验证。
 *
 * 覆盖：
 *  - `GET /api/course/problems?kcCode=` 列表反序列化（包括嵌套 [ProblemSampleDto]）；
 *  - 列表请求异常时仓库静默返回空表（仓库容错合同）；
 *  - `POST /api/submissions` 把 [SubmissionCreateRequest] 序列化为正确 JSON；
 *  - `pollStatus` 在 MockWebServer 上多次入队 PENDING → RUNNING → PASSED，
 *    验证轮询正好发起 3 次请求并在终态停止；
 *  - `history` 反序列化。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: WorkshopRepository
    private lateinit var api: WorkshopApi

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(WorkshopApi::class.java)
        repository = WorkshopRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `problems deserializes list and forwards kcCode as query param`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {
                        "problemId": 476,
                        "kcCode": "PY43-KC-016",
                        "title": "圆面积计算",
                        "statementMarkdown": "## 题目",
                        "inputDescription": "radius",
                        "outputDescription": "area",
                        "difficulty": "Low",
                        "samples": [
                          {"input": "25", "output": "1963.4375"},
                          {"input": "10", "output": "314.1500"}
                        ]
                      }
                    ]
                    """.trimIndent(),
                ),
        )

        val result = repository.problems("PY43-KC-016")

        assertEquals(1, result.size)
        val first = result.first()
        assertEquals(476L, first.id)
        assertEquals("圆面积计算", first.title)
        assertEquals(2, first.samples.size)
        assertEquals("1963.4375", first.samples[0].output)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.startsWith("/api/course/problems?kcCode=PY43-KC-016"))
    }

    @Test
    fun `problems returns empty list when server returns 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository.problems("PY43-KC-016")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `submit posts kotlin-serialized body with TRAINING source default`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"id":901,"problemId":476,"code":"print(1)","status":"PENDING"}
                    """.trimIndent(),
                ),
        )

        val dto = repository.submit(problemId = 476L, code = "print(1)")
        assertEquals(901L, dto.id)
        assertEquals(SubmissionStatus.PENDING, dto.status)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/submissions", recorded.path)
        val bodyJson = recorded.body.readUtf8()
        assertTrue(bodyJson.contains("\"problemId\":476"))
        assertTrue(bodyJson.contains("\"code\":\"print(1)\""))
        assertTrue(bodyJson.contains("\"source\":\"TRAINING\""))
    }

    @Test
    fun `pollStatus emits each response and stops at terminal status`() = runTest {
        server.enqueue(jsonOk("""{"submission":{"id":7,"problemId":476,"code":"x","status":"PENDING"},"judgeResult":null}"""))
        server.enqueue(jsonOk("""{"submission":{"id":7,"problemId":476,"code":"x","status":"RUNNING"},"judgeResult":null}"""))
        server.enqueue(
            jsonOk(
                """
                {
                  "submission":{
                    "id":7,
                    "problemId":476,
                    "code":"x",
                    "status":"PASSED",
                    "runtimeMs":12
                  },
                  "judgeResult":{
                    "verdict":"PASSED",
                    "passedCases":3,
                    "totalCases":3
                  }
                }
                """.trimIndent(),
            ),
        )

        val emissions = repository
            .pollStatus(submissionId = 7L, intervalMs = 1L, maxAttempts = 5)
            .toList()

        assertEquals(3, emissions.size)
        assertEquals(SubmissionStatus.PENDING, emissions[0].status)
        assertEquals(SubmissionStatus.RUNNING, emissions[1].status)
        assertEquals(SubmissionStatus.PASSED, emissions[2].status)
        assertEquals(12, emissions[2].runtimeMs)

        assertEquals(3, server.requestCount)
        repeat(3) {
            val req = server.takeRequest()
            assertEquals("GET", req.method)
            assertEquals("/api/submissions/7", req.path)
        }
    }

    @Test
    fun `pollStatus tolerates a single transient error then resumes`() = runTest {
        server.enqueue(jsonOk("""{"submission":{"id":9,"problemId":476,"code":"x","status":"PENDING"},"judgeResult":null}"""))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(
            jsonOk(
                """
                {
                  "submission":{"id":9,"problemId":476,"code":"x","status":"FAILED"},
                  "judgeResult":{"verdict":"WRONG_ANSWER","passedCases":1,"totalCases":3}
                }
                """.trimIndent(),
            ),
        )

        val emissions = repository
            .pollStatus(submissionId = 9L, intervalMs = 1L, maxAttempts = 5)
            .toList()

        assertEquals(2, emissions.size)
        assertEquals(SubmissionStatus.PENDING, emissions[0].status)
        assertEquals(SubmissionStatus.FAILED, emissions[1].status)
        assertEquals(1, emissions[1].passedCases)
    }

    @Test
    fun `pollStatus stops after maxAttempts when terminal never arrives`() = runTest {
        repeat(3) {
            server.enqueue(jsonOk("""{"submission":{"id":11,"problemId":476,"code":"x","status":"RUNNING"},"judgeResult":null}"""))
        }

        val emissions = repository
            .pollStatus(submissionId = 11L, intervalMs = 1L, maxAttempts = 3)
            .toList()

        assertEquals(3, emissions.size)
        assertTrue(emissions.all { it.status == SubmissionStatus.RUNNING })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `history deserializes list for current authenticated user`() = runTest {
        server.enqueue(
            jsonOk(
                """
                [
                  {"id":1,"problemId":476,"code":"a","status":"PASSED","passedCases":3,"totalCases":3},
                  {"id":2,"problemId":476,"code":"b","status":"FAILED","passedCases":1,"totalCases":3}
                ]
                """.trimIndent(),
            ),
        )

        val history = repository.history()

        assertEquals(2, history.size)
        assertEquals(SubmissionStatus.PASSED, history[0].status)
        assertEquals(SubmissionStatus.FAILED, history[1].status)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertNotNull(recorded.path)
        assertEquals("/api/submissions", recorded.path)
    }

    private fun jsonOk(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
}
