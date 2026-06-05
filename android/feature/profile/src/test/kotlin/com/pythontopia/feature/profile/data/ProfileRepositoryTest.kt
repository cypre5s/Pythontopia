package com.pythontopia.feature.profile.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pythontopia.core.common.ApiError
import com.pythontopia.core.common.NetworkError
import com.pythontopia.core.common.Result
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

/**
 * 用 [MockWebServer] 跑真的 OkHttp + Retrofit + kotlinx-serialization-converter，
 * 端到端验证 [ProfileApi] 的 GET / PUT 与 [ProfileRepository] 的错误归一。
 *
 * 不用 mockk 是因为 JSON 解析层是 Repository 失败的高频源头（snake/camel 不匹配、
 * `explicitNulls=false`、204 → null 等），用真实 Retrofit 才能锁住。
 */
class ProfileRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ProfileRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val api = retrofit.create(ProfileApi::class.java)
        repository = ProfileRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchProfile parses full 8-field payload`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "profileId": 1, "userId": 2, "packId": 43, "major": "CS",
                  "baseLevel": {"programmingYears": 1, "languagesKnown": ["C"], "pythonSelfRatedLevel": 2},
                  "learningGoal": {"kind": "EXAM", "deadlineIso": null, "targetKcCodes": ["PY43-KC-001"]},
                  "cognitiveStyle": {"fieldDependent": 0.4, "sequentialVsHolistic": 0.5},
                  "learningPreference": {"mediaPriority": ["code", "text"], "sessionMinutesPerDay": 45},
                  "errorPatterns": {"frequentKcCodes": ["PY43-KC-001"], "commonCategories": ["syntax"]},
                  "practiceCapability": {"avgAttemptsPerProblem": 2.0, "successRate": 0.65},
                  "emotionPersistence": {"resilience": 0.7, "recentMood": "neutral"},
                  "narrativeSummary": "学生具备基础编程经验。"
                }
                """.trimIndent(),
            ),
        )

        val result = repository.fetchProfile()

        check(result is Result.Success)
        val dto = result.data
        assertNotNull(dto)
        assertEquals("CS", dto?.major)
        assertEquals(1, dto?.baseLevel?.programmingYears)
        assertEquals(listOf("C"), dto?.baseLevel?.languagesKnown)
        assertEquals("EXAM", dto?.learningGoal?.kind)
        assertEquals(listOf("PY43-KC-001"), dto?.learningGoal?.targetKcCodes)
        assertEquals(0.65, dto?.practiceCapability?.successRate)
        assertEquals("学生具备基础编程经验。", dto?.narrativeSummary)

        val req = server.takeRequest()
        assertEquals("/api/learner/profile", req.path)
        assertEquals("GET", req.method)
    }

    @Test
    fun `fetchDimensions parses the 8-dimension snapshot array`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {"key":"professional_background","label":"专业背景","score":0.5,"confidence":0.0,"evidenceCount":0,"selfEditable":true,"tags":[]},
                  {"key":"learning_goal","label":"学习目标","score":0.72,"confidence":0.5,"evidenceCount":4,"selfEditable":true,"tags":["exam"]},
                  {"key":"practical_ability","label":"实践能力","score":0.4,"confidence":0.25,"evidenceCount":2,"selfEditable":false,"tags":[]}
                ]
                """.trimIndent(),
            ),
        )

        val result = repository.fetchDimensions()

        check(result is Result.Success)
        val dims = result.data
        assertEquals(3, dims.size)
        assertEquals("professional_background", dims[0].key)
        assertTrue(dims[0].selfEditable)
        assertEquals(0.72, dims[1].score)
        assertEquals(0.5, dims[1].confidence)
        assertEquals(4, dims[1].evidenceCount)
        assertEquals(listOf("exam"), dims[1].tags)
        assertFalse(dims[2].selfEditable)

        val req = server.takeRequest()
        assertEquals("/api/learner/profile/dimensions", req.path)
        assertEquals("GET", req.method)
    }

    @Test
    fun `fetchDimensions maps 5xx to Failure with ApiError carrying httpStatus`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(500).setBody("""{"code":"E","message":"boom"}"""),
        )

        val result = repository.fetchDimensions()

        check(result is Result.Failure)
        val error = result.error
        check(error is ApiError)
        assertEquals(500, error.httpStatus)
    }

    @Test
    fun `fetchProfile maps 204 No Content to Success with null payload`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.fetchProfile()

        check(result is Result.Success)
        assertNull(result.data)
    }

    @Test
    fun `fetchProfile maps 5xx to Failure with ApiError carrying httpStatus`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(500).setBody("""{"code":"E","message":"boom"}"""),
        )

        val result = repository.fetchProfile()

        check(result is Result.Failure)
        val error = result.error
        check(error is ApiError)
        assertEquals(500, error.httpStatus)
        assertEquals("HTTP_500", error.code)
    }

    @Test
    fun `fetchProfile maps connection failure to NetworkError`() = runTest {
        server.shutdown()
        val result = repository.fetchProfile()
        check(result is Result.Failure)
        assertTrue(result.error is NetworkError, "expected NetworkError, got ${result.error}")
    }

    @Test
    fun `updateDimension PUTs body to namespaced path and parses response`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"userId":1,"dimensionKey":"learning_goal","finalDimension":{"score":0.6,"evidence_count":3}}""",
            ),
        )

        val result = repository.updateDimension(
            dimensionKey = "learning_goal",
            request = DimensionUpdateRequest(scoreDelta = 0.2, evidenceIncrement = 1),
        )

        check(result is Result.Success)
        assertEquals(1L, result.data.userId)
        assertEquals("learning_goal", result.data.dimensionKey)

        val req = server.takeRequest()
        assertEquals("/api/learner/profile/dimensions/learning_goal", req.path)
        assertEquals("PUT", req.method)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"scoreDelta\":0.2"), "body should encode scoreDelta, got $body")
        assertTrue(body.contains("\"evidenceIncrement\":1"), "body should encode evidenceIncrement, got $body")
    }

    @Test
    fun `updateDimension maps 403 to Failure with ApiError`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """{"code":"PROFILE_DIMENSION_NOT_SELF_EDITABLE","message":"x"}""",
            ),
        )

        val result = repository.updateDimension(
            dimensionKey = "knowledge_base",
            request = DimensionUpdateRequest(scoreDelta = 0.1),
        )

        check(result is Result.Failure)
        check(result.error is ApiError)
        assertEquals(403, (result.error as ApiError).httpStatus)
    }
}
