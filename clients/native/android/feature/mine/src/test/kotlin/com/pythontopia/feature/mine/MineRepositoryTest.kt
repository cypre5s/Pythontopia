package com.pythontopia.feature.mine

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
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
import retrofit2.Retrofit

class MineRepositoryTest {

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
    fun `startSession posts failed submission id to bug mine endpoint`() = runTest {
        server.enqueue(jsonOk(SESSION_JSON))

        val session = repository.startSession(srcSubmissionId = 9981L)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/bug-mine/sessions", request.path)
        assertTrue(request.body.readUtf8().contains("\"submissionId\":9981"))
        assertEquals(12345L, session.sessionId)
        assertEquals(9981L, session.sourceSubmissionId)
        assertEquals("OPEN", session.status)
    }

    @Test
    fun `startSession rejects missing source submission`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.startSession(srcSubmissionId = null) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cards maps session payload cards to mine cards`() = runTest {
        server.enqueue(jsonOk(SESSION_JSON))

        val cards = repository.cards(sessionId = 12345L)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/bug-mine/sessions/12345", request.path)
        assertEquals(2, cards.size)
        assertEquals("COUNTER_EXAMPLE", cards[0].deckCardId)
        assertEquals("PY43-KC-016", cards[0].kcCode)
        assertTrue(cards[0].snippet.contains("样例 1 输出不一致"))
    }

    @Test
    fun `attempt uses retry submit endpoint and maps passed status`() = runTest {
        server.enqueue(
            jsonOk("""{"id":777,"problemId":476,"code":"print(1)","status":"PASSED","runtimeMs":12}"""),
        )

        val attempt = repository.attempt(sessionId = 12345L, code = "print(1)")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/bug-mine/sessions/12345/retry-submit", request.path)
        assertTrue(request.body.readUtf8().contains("\"code\":\"print(1)\""))
        assertEquals(MineAttempt.STATUS_PASS, attempt.status)
        assertEquals(777L, attempt.cardId)
    }

    @Test
    fun `next reveals first hidden card`() = runTest {
        server.enqueue(jsonOk(SESSION_JSON_WITH_HIDDEN))
        server.enqueue(jsonOk(HIDDEN_CARD_JSON.replace("\"revealed\":false", "\"revealed\":true")))

        val card = repository.next(sessionId = 12345L)

        assertEquals("/api/bug-mine/sessions/12345", server.takeRequest().path)
        val reveal = server.takeRequest()
        assertEquals("POST", reveal.method)
        assertEquals("/api/bug-mine/sessions/12345/cards/302/reveal", reveal.path)
        assertEquals(302L, card?.id)
        assertTrue(card?.revealed == true)
    }

    private fun jsonOk(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private companion object {
        const val CARD_JSON = """
        {
          "id":301,
          "sessionId":12345,
          "cardType":"COUNTER_EXAMPLE",
          "revealed":true,
          "payload":{
            "expected_stdout":"42\n",
            "diff_summary":"样例 1 输出不一致",
            "evidence_kc_codes":["PY43-KC-016"]
          }
        }
        """
        const val HIDDEN_CARD_JSON = """
        {
          "id":302,
          "sessionId":12345,
          "cardType":"HINT_LAYER_1",
          "revealed":false,
          "payload":{"body":"先检查 input 转 int"}
        }
        """
        const val SESSION_JSON = """
        {
          "id":12345,
          "userId":42,
          "submissionId":9981,
          "problemId":476,
          "status":"OPEN",
          "retryCount":0,
          "cards":[$CARD_JSON,$HIDDEN_CARD_JSON]
        }
        """
        const val SESSION_JSON_WITH_HIDDEN = SESSION_JSON
    }
}
