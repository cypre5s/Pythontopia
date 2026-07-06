package com.pythontopia.feature.board

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pythontopia.core.data.dto.learner.QuestStatus
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
class BoardRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: BoardRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BoardApi::class.java)
        repository = BoardRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `today 解析今日任务列表`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":1,"title":"读 input 章节","status":"OPEN","priority":2,"kcCode":"PY43-KC-016","source":"PATH_PLANNER"},{"id":2,"title":"完成班级练习","status":"IN_PROGRESS","priority":3,"requiredEvidenceType":"SUBMISSION","source":"CLASSROOM"}]""",
            ),
        )
        val result = repository.today()
        assertTrue(result.isSuccess)
        val quests = result.getOrThrow()
        assertEquals(2, quests.size)
        assertEquals(QuestStatus.OPEN, quests[0].status)
        assertTrue(quests[1].requiresEvidence())
    }

    @Test
    fun `complete 返回更新后的任务`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"id":1,"title":"读 input 章节","status":"COMPLETED","priority":2}"""),
        )
        val result = repository.complete(1L, note = "读完了")
        assertTrue(result.isSuccess)
        assertEquals(QuestStatus.COMPLETED, result.getOrThrow().status)

        val recorded = server.takeRequest()
        assertEquals("/api/quests/1/complete", recorded.path)
        assertEquals("POST", recorded.method)
    }

    @Test
    fun `skip 返回已跳过任务`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"id":3,"title":"可选任务","status":"SKIPPED","priority":0}"""),
        )
        val result = repository.skip(3L, reason = "今天没时间")
        assertTrue(result.isSuccess)
        assertEquals(QuestStatus.SKIPPED, result.getOrThrow().status)
    }

    @Test
    fun `today 服务不可用上抛 Result_failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val result = repository.today()
        assertFalse(result.isSuccess)
    }
}
