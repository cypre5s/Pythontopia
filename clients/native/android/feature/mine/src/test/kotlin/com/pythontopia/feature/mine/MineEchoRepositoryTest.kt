package com.pythontopia.feature.mine

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

/** MockWebServer 端到端验证遗忘回声/错题本/富会话/变式/解锁/关闭的路径、序列化与映射。 */
class MineEchoRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: MineRepository

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
        repository = MineRepository(retrofit.create(MineApi::class.java))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `echoes maps fsrs candidates from echoes endpoint`() = runTest {
        server.enqueue(jsonOk("[$ECHO_JSON]"))

        val echoes = repository.echoes()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/bug-mine/echoes", request.path)
        assertEquals(1, echoes.size)
        val echo = echoes.first()
        assertEquals("PY43-KC-016", echo.kcCode)
        assertEquals(476L, echo.problemId)
        assertEquals(71, echo.recommendationPercent)
        assertEquals(86, echo.decayPercent)
        assertTrue(echo.recommendationReason.contains("遗忘回声"))
    }

    @Test
    fun `errorBook maps open sessions from errorbook endpoint`() = runTest {
        server.enqueue(jsonOk("[$ERROR_BOOK_JSON]"))

        val items = repository.errorBook()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/bug-mine/errorbook", request.path)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals(555L, item.sessionId)
        assertEquals(2, item.depthLayer)
        assertEquals(4, item.profileDepthTarget)
        assertEquals(listOf("off_by_one"), item.mistakePatterns)
        assertEquals(71, item.recommendationPercent)
    }

    @Test
    fun `openEcho posts kcCode and maps rich session detail`() = runTest {
        server.enqueue(jsonOk(SESSION_DETAIL_JSON))

        val detail = repository.openEcho("PY43-KC-016")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/bug-mine/echoes", request.path)
        assertTrue(request.body.readUtf8().contains("\"kcCode\":\"PY43-KC-016\""))
        assertEquals(555L, detail.sessionId)
        assertEquals(2, detail.depthLayer)
        assertEquals(MineSessionDetail.STATUS_OPEN, detail.status)
        assertEquals(5, detail.depthSteps.size)
        assertEquals("圆面积", detail.problem?.title)
        assertEquals(1, detail.problem?.samples?.size)
        assertEquals("变式：椭圆面积", detail.variantProblem?.title)
    }

    @Test
    fun `sessionDetail reveal gating reflects card depth and revealed flag`() = runTest {
        server.enqueue(jsonOk(SESSION_DETAIL_JSON))

        val detail = repository.sessionDetail(555L)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/bug-mine/sessions/555", request.path)
        assertEquals(2, detail.cards.size)
        val counter = detail.cards[0]
        assertEquals("COUNTER_EXAMPLE", counter.cardType)
        assertEquals(2, counter.requiredDepth)
        assertTrue(counter.revealed)
        assertTrue(counter.body.contains("第1行不一致"))
        val trace = detail.cards[1]
        assertEquals(3, trace.requiredDepth)
        assertFalse(trace.revealed)
        assertEquals("", trace.body)
    }

    @Test
    fun `revealCard posts to reveal endpoint and summarizes trace body`() = runTest {
        server.enqueue(
            jsonOk(
                """{"id":302,"sessionId":555,"cardType":"VARIABLE_TRACE","revealed":true,
                   "payload":{"steps":[{"line":1},{"line":2}]}}""",
            ),
        )

        val card = repository.revealCard(sessionId = 555L, cardId = 302L)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/bug-mine/sessions/555/cards/302/reveal", request.path)
        assertTrue(card.revealed)
        assertEquals(3, card.requiredDepth)
        assertTrue(card.body.contains("2 步"))
    }

    @Test
    fun `variantAttempt posts code to variant-submit and maps passed`() = runTest {
        server.enqueue(
            jsonOk("""{"id":777,"problemId":600,"code":"print(1)","status":"PASSED","runtimeMs":5}"""),
        )

        val attempt = repository.variantAttempt(sessionId = 555L, code = "print(1)")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/bug-mine/sessions/555/variant-submit", request.path)
        assertTrue(request.body.readUtf8().contains("\"code\":\"print(1)\""))
        assertEquals(MineAttempt.STATUS_PASS, attempt.status)
    }

    @Test
    fun `close posts status query param and maps closed detail`() = runTest {
        server.enqueue(jsonOk(SESSION_DETAIL_JSON.replace("\"status\":\"OPEN\"", "\"status\":\"ABANDONED\"")))

        val detail = repository.close(sessionId = 555L, status = MineSessionDetail.STATUS_ABANDONED)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/bug-mine/sessions/555/close?status=ABANDONED", request.path)
        assertEquals(MineSessionDetail.STATUS_ABANDONED, detail.status)
        assertFalse(detail.isOpen)
        assertEquals(476L, detail.problem?.id)
    }

    private fun jsonOk(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private companion object {
        const val ECHO_JSON = """
        {
          "kcCode":"PY43-KC-016",
          "problemId":476,
          "problemTitle":"圆面积调试",
          "masteryScore":0.72,
          "confidence":0.25,
          "decayRisk":0.86,
          "minePriority":0.71,
          "recommendationReason":"遗忘回声 · 置信度低 · 建议入矿"
        }
        """
        const val ERROR_BOOK_JSON = """
        {
          "sessionId":555,
          "submissionId":9001,
          "problemId":476,
          "questId":null,
          "kcCode":"PY43-KC-016",
          "problemTitle":"圆面积调试",
          "status":"OPEN",
          "retryCount":1,
          "depthLayer":2,
          "profileDepthTarget":4,
          "profileGuidance":"建议下探到 L4",
          "masteryScore":0.40,
          "minePriority":0.71,
          "decayRisk":0.55,
          "recommendationReason":"掌握薄弱 · 建议目标 L4",
          "mistakePatterns":["off_by_one"],
          "patternMatched":true,
          "openedAt":"2026-06-05T13:39:00Z",
          "variantProblem":{"id":600,"title":"变式：椭圆面积"},
          "depthSteps":[]
        }
        """
        const val SESSION_DETAIL_JSON = """
        {
          "id":555,
          "userId":42,
          "submissionId":9001,
          "problemId":476,
          "questId":null,
          "status":"OPEN",
          "retryCount":0,
          "depthLayer":2,
          "profileDepthTarget":4,
          "masteryConfirmed":false,
          "variantSubmissionId":null,
          "variantProblem":{"id":600,"title":"变式：椭圆面积"},
          "profileGuidance":"建议下探到 L4",
          "depthSteps":[
            {"layer":1,"key":"bare_retry","title":"L1 裸重试","description":"先按题意重写一次","unlocked":true},
            {"layer":2,"key":"counter_example","title":"L2 反例","description":"看失败样例","unlocked":true},
            {"layer":3,"key":"variable_trace","title":"L3 变量轨迹","description":"看变量变化","unlocked":false},
            {"layer":4,"key":"layered_hints","title":"L4 分层提示","description":"只拿思路","unlocked":false},
            {"layer":5,"key":"same_kc_variant","title":"L5 同 KC 变式","description":"换题确认","unlocked":false}
          ],
          "cards":[
            {"id":301,"sessionId":555,"cardType":"COUNTER_EXAMPLE","revealed":true,
             "payload":{"expected_stdout":"3.14\n","diff_summary":"第1行不一致","evidence_kc_codes":["PY43-KC-016"]}},
            {"id":302,"sessionId":555,"cardType":"VARIABLE_TRACE","revealed":false,
             "payload":{"steps":[{"line":1}]}}
          ],
          "problem":{
            "id":476,"packId":43,"kcCode":"PY43-KC-016","title":"圆面积",
            "statementMarkdown":"## 圆面积","inputDescription":"r","outputDescription":"area",
            "difficulty":"Low","samples":[{"input":"1","output":"3.14"}]
          }
        }
        """
    }
}
