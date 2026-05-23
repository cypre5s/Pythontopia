package com.pythontopia.feature.path

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

/**
 * 真实 Retrofit + MockWebServer 集成测试。
 *
 * 覆盖：
 *  1. fetchPath 路径 + query 参数正确，JSON 反序列化通过
 *  2. fetchPath 在 5xx 时 fallback 到 DEMO_FALLBACK（保证 demo 不空屏）
 *  3. markNodeStarted 拼对了 POST 路径 + 返回最新快照
 *  4. markNodeDone 拼对了 POST 路径 + 返回最新快照
 *  5. markNodeStarted / markNodeDone 在异常时 fallback 到本地推进，UI 仍能继续
 */
class PathRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: PathRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val api = retrofit.create(LearningPathApi::class.java)
        repository = PathRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchPath hits GET api learner path with userId and packId query`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "pathId":"p-77",
                      "userId":42,
                      "packId":43,
                      "currentNodeId":"n-002",
                      "nodes":[
                        {"id":"n-001","type":"READ","title":"入门","kcCode":"KC-1","status":"DONE","nextIds":["n-002"]},
                        {"id":"n-002","type":"PRACTICE","title":"练手","kcCode":"KC-2","status":"IN_PROGRESS","nextIds":[]}
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val path = repository.fetchPath(userId = 42L)

        assertEquals("p-77", path.pathId)
        assertEquals(42L, path.userId)
        assertEquals(43L, path.packId)
        assertEquals(2, path.nodes.size)
        assertEquals(PathNodeStatus.DONE, path.nodes[0].status)
        assertEquals(PathNodeType.PRACTICE, path.nodes[1].type)

        val recorded: RecordedRequest = server.takeRequest()
        assertEquals("GET", recorded.method)
        val recordedPath = recorded.path ?: ""
        assertTrue(recordedPath.startsWith("/api/learner/path"), "actual=$recordedPath")
        assertTrue(recordedPath.contains("userId=42"))
        assertTrue(recordedPath.contains("packId=43"))
    }

    @Test
    fun `fetchPath returns DEMO_FALLBACK when server errors`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val path = repository.fetchPath(userId = 9L, packId = 43L)

        assertEquals(PathRepository.DEMO_FALLBACK.pathId, path.pathId)
        assertEquals(9L, path.userId)
        assertEquals(43L, path.packId)
        assertEquals(PathRepository.DEMO_FALLBACK.nodes.size, path.nodes.size)
    }

    @Test
    fun `fetchPath fallback when server is shut down`() = runTest {
        server.shutdown()

        val path = repository.fetchPath(userId = 9L)
        assertNotNull(path)
        assertEquals(PathRepository.DEMO_FALLBACK.pathId, path.pathId)
    }

    @Test
    fun `markNodeStarted posts to correct path and returns updated snapshot`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "pathId":"p-77",
                      "userId":42,
                      "packId":43,
                      "currentNodeId":"n-003",
                      "nodes":[
                        {"id":"n-003","type":"QUEST","title":"任务","status":"IN_PROGRESS","nextIds":[]}
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val updated = repository.markNodeStarted("p-77", "n-003")

        assertEquals("n-003", updated.currentNodeId)
        assertEquals(PathNodeStatus.IN_PROGRESS, updated.nodes.first().status)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/learner/path/p-77/nodes/n-003/start", recorded.path)
    }

    @Test
    fun `markNodeDone posts to correct path and returns updated snapshot`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "pathId":"p-77",
                      "userId":42,
                      "packId":43,
                      "currentNodeId":"n-004",
                      "nodes":[
                        {"id":"n-003","type":"QUEST","title":"任务","status":"DONE","nextIds":["n-004"]},
                        {"id":"n-004","type":"DEBUG","title":"矿洞","status":"UNLOCKED","nextIds":[]}
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val updated = repository.markNodeDone("p-77", "n-003")

        assertEquals("n-004", updated.currentNodeId)
        assertEquals(PathNodeStatus.DONE, updated.nodes.first { it.id == "n-003" }.status)
        assertEquals(PathNodeStatus.UNLOCKED, updated.nodes.first { it.id == "n-004" }.status)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/learner/path/p-77/nodes/n-003/done", recorded.path)
    }

    @Test
    fun `markNodeStarted fallback locally promotes node to IN_PROGRESS when server errors`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502))

        // DEMO_FALLBACK includes n-003 in UNLOCKED; calling start on it should locally flip to IN_PROGRESS.
        val updated = repository.markNodeStarted(PathRepository.DEMO_FALLBACK.pathId, "n-003")

        assertEquals(PathRepository.DEMO_FALLBACK.pathId, updated.pathId)
        assertEquals("n-003", updated.currentNodeId)
        assertEquals(PathNodeStatus.IN_PROGRESS, updated.nodes.first { it.id == "n-003" }.status)
    }

    @Test
    fun `markNodeDone fallback locally promotes node + unlocks next when server errors`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        // DEMO_FALLBACK n-002 is IN_PROGRESS with nextIds=[n-003]; done should DONE n-002 + UNLOCK keeps n-003 UNLOCKED.
        val updated = repository.markNodeDone(PathRepository.DEMO_FALLBACK.pathId, "n-002")

        val n2 = updated.nodes.first { it.id == "n-002" }
        val n3 = updated.nodes.first { it.id == "n-003" }
        assertEquals(PathNodeStatus.DONE, n2.status)
        // n-003 already UNLOCKED in fallback, remains UNLOCKED
        assertEquals(PathNodeStatus.UNLOCKED, n3.status)
        assertEquals("n-003", updated.currentNodeId)
    }
}
