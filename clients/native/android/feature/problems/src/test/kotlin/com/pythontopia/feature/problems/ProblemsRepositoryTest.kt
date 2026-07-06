package com.pythontopia.feature.problems

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
class ProblemsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ProblemsRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ProblemsApi::class.java)
        repository = ProblemsRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `load 合并题目_KC_提交进度`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"problemId":476,"kcCode":"PY43-KC-016","title":"读取输入","statementMarkdown":"读取一行并回显","inputDescription":"一行字符串","outputDescription":"原样回显","difficulty":"Low","sourceLabel":"教材 P10"}]""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """[{"kcId":16,"kcCode":"PY43-KC-016","kcName":"input 函数","chapterId":2,"chapterOrder":2,"difficulty":"Low","summaryMarkdown":""}]""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """[{"id":1,"problemId":476,"status":"FAILED"},{"id":2,"problemId":476,"status":"PASSED"}]""",
            ),
        )

        val result = repository.load()
        assertTrue(result.isSuccess)
        val rows = result.getOrThrow()
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(476L, row.problemId)
        assertEquals("input 函数", row.kcName)
        assertEquals(2, row.chapterOrder)
        assertEquals(ProblemDifficulty.LOW, row.difficulty)
        assertEquals(ProblemStatus.PASSED, row.status)
        assertEquals(2, row.attempts)
    }

    @Test
    fun `提交端点失败时回退空进度但仍列题`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"problemId":1,"kcCode":"PY43-KC-001","title":"变量","statementMarkdown":"x=1","inputDescription":"","outputDescription":"","difficulty":"Medium","sourceLabel":""}]""",
            ),
        )
        server.enqueue(MockResponse().setBody("""[]"""))
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository.load()
        assertTrue(result.isSuccess)
        val row = result.getOrThrow().first()
        assertEquals(ProblemStatus.NOT_TRIED, row.status)
        assertEquals(0, row.attempts)
        assertEquals("PY43-KC-001", row.kcName) // 无 KC 名时回退 kcCode
    }

    @Test
    fun `题目端点失败上抛 Result_failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val result = repository.load()
        assertFalse(result.isSuccess)
    }
}
