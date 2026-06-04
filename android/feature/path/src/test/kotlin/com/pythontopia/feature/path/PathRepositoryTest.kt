package com.pythontopia.feature.path

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

class PathRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: PathRepository

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
        repository = PathRepository(retrofit.create(LearningPathApi::class.java))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchPath reads active path for current authenticated user`() = runTest {
        server.enqueue(jsonOk(ACTIVE_PATH_JSON))

        val path = repository.fetchPath(userId = -1L)

        assertEquals("77", path.pathId)
        assertEquals(42L, path.userId)
        assertEquals(43L, path.packId)
        assertEquals("101", path.currentNodeId)
        assertEquals(3, path.nodes.size)
        assertEquals(PathNodeType.READ, path.nodes[0].type)
        assertEquals(PathNodeStatus.IN_PROGRESS, path.nodes[0].status)
        assertEquals(listOf("102"), path.nodes[0].nextIds)
        assertEquals(PathNodeType.PRACTICE, path.nodes[1].type)
        assertEquals(PathNodeType.DEBUG, path.nodes[2].type)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/learner/path/active", recorded.path)
        assertTrue(recorded.requestUrl?.queryParameterNames?.isEmpty() == true)
    }

    @Test
    fun `fetchPath plans real path when active path is missing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(jsonOk("""{"path":$ACTIVE_PATH_JSON}"""))

        val path = repository.fetchPath(userId = -1L)

        assertEquals("77", path.pathId)
        assertEquals(2, server.requestCount)
        assertEquals("/api/learner/path/active", server.takeRequest().path)
        val planRequest = server.takeRequest()
        assertEquals("POST", planRequest.method)
        assertEquals("/api/learner/path/plan", planRequest.path)
    }

    @Test
    fun `markNodeStarted rejects local progress mutation`() {
        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { repository.markNodeStarted("77", "101") }
        }
    }

    @Test
    fun `markNodeDone rejects local progress mutation`() {
        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { repository.markNodeDone("77", "101") }
        }
    }

    private fun jsonOk(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private companion object {
        const val ACTIVE_PATH_JSON = """
        {
          "id":77,
          "userId":42,
          "packId":43,
          "nodes":[
            {
              "id":101,
              "dayIndex":1,
              "orderInDay":1,
              "kcCode":"PY43-KC-016",
              "nodeType":"READ_RESOURCE",
              "reasonSummary":"阅读 input 函数"
            },
            {
              "id":102,
              "dayIndex":1,
              "orderInDay":2,
              "kcCode":"PY43-KC-016",
              "nodeType":"SOLVE_PROBLEM",
              "suggestedProblemId":476
            },
            {
              "id":103,
              "dayIndex":2,
              "orderInDay":1,
              "kcCode":"PY43-KC-022",
              "nodeType":"FIX_BUG",
              "evidenceSummary":"复习失败提交"
            }
          ]
        }
        """
    }
}
