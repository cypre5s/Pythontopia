package com.pythontopia.feature.mine

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Retrofit

/**
 * 用 MockWebServer 端到端验证 [MineRepository] 与 [MineApi] 之间的 HTTP 契约。
 *
 * 之所以不走 mockk 单元测试：mock 掉 [MineApi] 接口会绕过 Retrofit 注解，
 * 拼错 `@POST` 路径 / 漏掉 `@Path` / JSON 字段命名失配都不会被发现。MockWebServer
 * 让我们能：
 *  1. 校验客户端发出的请求 method + path + body 是否符合后端契约；
 *  2. 校验 `MineApi` 接口注解（`@POST`/`@GET`/`@Path`/`@Body`）正确反射到 Retrofit；
 *  3. 校验 [MineDto] 的 [kotlinx.serialization] 注解能正确双向反序列化后端 JSON；
 *  4. 校验 [MineRepository.next] 对 404 的语义降级（→ null）而非抛异常。
 */
class MineRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: MineRepository
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val api = retrofit.create(MineApi::class.java)
        repository = MineRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `startSession posts to api mine sessions with sourceSubmissionId body`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_OK)
                .setHeader("Content-Type", JSON_CT)
                .setBody(
                    """
                    {
                      "sessionId": 12345,
                      "entry": "FAILED_SUBMISSION",
                      "sourceSubmissionId": 9981,
                      "currentLayer": 1,
                      "totalCards": 5
                    }
                    """.trimIndent(),
                ),
        )

        val session = repository.startSession(srcSubmissionId = 9981L)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/mine/sessions", request.path)
        assertTrue(
            request.body.readUtf8().contains("\"sourceSubmissionId\":9981"),
            "request body should serialize sourceSubmissionId",
        )
        assertEquals(12345L, session.sessionId)
        assertEquals(MineSession.ENTRY_FAILED_SUBMISSION, session.entry)
        assertEquals(9981L, session.sourceSubmissionId)
    }

    @Test
    fun `startSession omits sourceSubmissionId from body when null thanks to explicitNulls false`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_OK)
                .setHeader("Content-Type", JSON_CT)
                .setBody("""{"sessionId":1,"entry":"IDLE_RECOMMEND","currentLayer":1,"totalCards":5}"""),
        )

        repository.startSession(srcSubmissionId = null)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(
            !body.contains("sourceSubmissionId"),
            "explicitNulls=false should drop null sourceSubmissionId, got: $body",
        )
    }

    @Test
    fun `cards GET returns deserialized list ordered by layer`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_OK)
                .setHeader("Content-Type", JSON_CT)
                .setBody(
                    """
                    [
                      {
                        "id": 101,
                        "deckCardId": "B1-01",
                        "layer": 1,
                        "title": "无名魔兽",
                        "snippet": "print(magick)",
                        "expectedOutput": "spell\n",
                        "kcCode": "PY43-KC-010",
                        "pythocoReward": 5
                      },
                      {
                        "id": 102,
                        "deckCardId": "B2-01",
                        "layer": 2,
                        "title": "站门口的 if",
                        "snippet": "x = 7\nif x > 0:\nprint(\"positive\")",
                        "expectedOutput": "positive\n",
                        "kcCode": "PY43-KC-033",
                        "pythocoReward": 6
                      }
                    ]
                    """.trimIndent(),
                ),
        )

        val cards = repository.cards(sessionId = 12345L)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/mine/sessions/12345/cards", request.path)
        assertEquals(2, cards.size)
        assertEquals("B1-01", cards[0].deckCardId)
        assertEquals(1, cards[0].layer)
        assertEquals("PY43-KC-010", cards[0].kcCode)
        assertEquals(2, cards[1].layer)
    }

    @Test
    fun `attempt POST sends code in body and parses PASS verdict`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_OK)
                .setHeader("Content-Type", JSON_CT)
                .setBody(
                    """
                    {
                      "cardId": 101,
                      "code": "magick = 'spell'\nprint(magick)",
                      "status": "PASS",
                      "actualOutput": "spell\n",
                      "message": "通过"
                    }
                    """.trimIndent(),
                ),
        )

        val attempt = repository.attempt(cardId = 101L, code = "magick = 'spell'\nprint(magick)")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/mine/cards/101/attempt", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"code\""), "body should contain code field, got: $body")
        assertEquals(MineAttempt.STATUS_PASS, attempt.status)
        assertEquals(101L, attempt.cardId)
        assertEquals("spell\n", attempt.actualOutput)
    }

    @Test
    fun `attempt POST surfaces FAIL verdict with message and actualOutput`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_OK)
                .setHeader("Content-Type", JSON_CT)
                .setBody(
                    """
                    {
                      "cardId": 101,
                      "code": "print(magick)",
                      "status": "FAIL",
                      "actualOutput": "",
                      "message": "NameError: name 'magick' is not defined"
                    }
                    """.trimIndent(),
                ),
        )

        val attempt = repository.attempt(101L, "print(magick)")
        assertEquals(MineAttempt.STATUS_FAIL, attempt.status)
        assertTrue(attempt.message!!.contains("NameError"))
    }

    @Test
    fun `next POST returns parsed card when backend has one`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_OK)
                .setHeader("Content-Type", JSON_CT)
                .setBody(
                    """
                    {
                      "id": 201,
                      "deckCardId": "B3-04",
                      "layer": 3,
                      "title": "缺页的档案柜",
                      "snippet": "record = {\"silver\": 12}\nprint(record[\"gold\"])",
                      "expectedOutput": "0\n",
                      "kcCode": "PY43-KC-041",
                      "pythocoReward": 10
                    }
                    """.trimIndent(),
                ),
        )

        val card = repository.next(sessionId = 12345L)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/mine/sessions/12345/next", request.path)
        assertEquals(201L, card?.id)
        assertEquals("B3-04", card?.deckCardId)
        assertEquals(3, card?.layer)
    }

    @Test
    fun `next returns null on 404 to signal session exhausted`() = runTest {
        server.enqueue(MockResponse().setResponseCode(HTTP_NOT_FOUND))

        val card = repository.next(sessionId = 12345L)

        assertNull(card)
    }

    @Test
    fun `next returns null on 410 GONE for explicitly closed session`() = runTest {
        server.enqueue(MockResponse().setResponseCode(HTTP_GONE))

        val card = repository.next(sessionId = 12345L)

        assertNull(card)
    }

    @Test
    fun `next propagates 5xx as HttpException so ViewModel can surface error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(HTTP_INTERNAL_ERROR))

        try {
            repository.next(12345L)
            fail<Unit>("expected HttpException for 500 status")
        } catch (e: HttpException) {
            assertEquals(HTTP_INTERNAL_ERROR, e.code())
        }
    }

    private companion object {
        const val JSON_CT = "application/json"
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GONE = 410
        const val HTTP_INTERNAL_ERROR = 500
    }
}
