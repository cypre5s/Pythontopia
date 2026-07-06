package com.pythontopia.feature.classroom

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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

/** [ClassroomRepository] MockWebServer 契约与 fallback/fail-fast 路径单测。 */
class ClassroomRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ClassroomRepository
    private lateinit var api: ClassroomApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(ClassroomApi::class.java)
        repository = ClassroomRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `myClassrooms hits GET api classrooms mine and parses list`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {
                        "classroomId": 7,
                        "name": "Python 周一班",
                        "description": "示例",
                        "teacherId": 100,
                        "packId": 43,
                        "status": "ACTIVE",
                        "joinCode": "ABC123"
                      }
                    ]
                    """.trimIndent(),
                ),
        )

        val result = repository.myClassrooms()

        assertEquals(1, result.size)
        assertEquals(7L, result.first().classroomId)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/classrooms/mine", recorded.path)
    }

    @Test
    fun `myClassrooms returns empty list on 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val result = repository.myClassrooms()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `join posts JSON body with joinCode and returns Classroom`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "classroomId": 7,
                      "name": "Python 周一班",
                      "teacherId": 100,
                      "packId": 43,
                      "status": "ACTIVE",
                      "joinCode": "ABC123"
                    }
                    """.trimIndent(),
                ),
        )

        val joined = repository.join("ABC123")

        assertEquals("Python 周一班", joined.name)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/classrooms/join", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("ABC123"), "Request body should contain joinCode: $body")
    }

    @Test
    fun `join propagates 404 as exception`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        runCatching { repository.join("XXX999") }
            .onSuccess { error("join should have thrown on 404") }
            .onFailure { assertNotNull(it.message) }
    }

    @Test
    fun `quests as teacher hits teacher endpoint and bypasses local filter`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {
                        "id": 11,
                        "classroomId": 7,
                        "title": "完成 for 循环练习",
                        "kcCodes": ["KC_FOR_LOOP"],
                        "nodeType": "SOLVE_PROBLEM",
                        "dueDate": "2026-06-01",
                        "status": "PUBLISHED",
                        "requiredEvidenceType": "submission"
                      }
                    ]
                    """.trimIndent(),
                ),
        )

        val quests = repository.quests(classroomId = 7L, asTeacher = true)

        assertEquals(1, quests.size)
        assertEquals("submission", quests.first().requiredEvidenceType)
        val recorded = server.takeRequest()
        assertEquals("/api/teacher/classrooms/7/quests", recorded.path)
    }

    @Test
    fun `quests as student hits mine endpoint and filters by classroomId`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {
                        "id": 11,
                        "classroomId": 7,
                        "title": "属于 7 班",
                        "kcCodes": [],
                        "nodeType": "READ_RESOURCE",
                        "dueDate": "2026-06-01",
                        "status": "PUBLISHED"
                      },
                      {
                        "id": 12,
                        "classroomId": 9,
                        "title": "属于 9 班",
                        "kcCodes": [],
                        "nodeType": "READ_RESOURCE",
                        "dueDate": "2026-06-01",
                        "status": "PUBLISHED"
                      }
                    ]
                    """.trimIndent(),
                ),
        )

        val quests = repository.quests(classroomId = 7L, asTeacher = false)

        assertEquals(1, quests.size)
        assertEquals(7L, quests.first().classroomId)
        val recorded = server.takeRequest()
        assertEquals("/api/classroom/quests/mine", recorded.path)
    }

    @Test
    fun `submitEvidence posts evidence body to api quests id complete`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": 11,
                      "classroomId": 7,
                      "title": "完成 for 循环练习",
                      "kcCodes": [],
                      "nodeType": "SOLVE_PROBLEM",
                      "dueDate": "2026-06-01",
                      "status": "PUBLISHED",
                      "studentQuestId": 99,
                      "studentQuestStatus": "COMPLETED"
                    }
                    """.trimIndent(),
                ),
        )

        val updated = repository.submitEvidence(99L, QuestEvidence(submissionId = 5001L))

        assertEquals("COMPLETED", updated.studentQuestStatus)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/quests/99/complete", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("5001"), "Request body should serialize submissionId: $body")
    }

    @Test
    fun `submitEvidence throws on 422`() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody("evidence required"))
        runCatching { repository.submitEvidence(99L, QuestEvidence(submissionId = null)) }
            .onSuccess { error("submitEvidence should have thrown on 422") }
            .onFailure { assertNotNull(it.message) }
    }

    @Test
    fun `heatmap hits teacher dashboard endpoint and parses BigDecimal as String`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {
                        "kcCode": "KC_FOR_LOOP",
                        "kcName": "for 循环",
                        "chapterId": 4,
                        "chapterOrder": 4,
                        "avgMastery": "0.65",
                        "studentCount": 40,
                        "masteredCount": 12,
                        "learningCount": 22,
                        "strugglingCount": 6
                      }
                    ]
                    """.trimIndent(),
                ),
        )

        val heatmap = repository.heatmap(classroomId = 7L)

        assertEquals(1, heatmap.size)
        assertEquals("0.65", heatmap.first().avgMastery)
        val recorded = server.takeRequest()
        assertEquals("/api/teacher/classrooms/7/dashboard/kc-heatmap", recorded.path)
    }

    @Test
    fun `errorHotspots hits dashboard endpoint and parses nested categories`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {
                        "kcCode": "KC_FOR_LOOP",
                        "kcName": "for 循环",
                        "failedSubmissionCount": 38,
                        "affectedStudentCount": 15,
                        "categories": [
                          {"category": "INDEX_OUT_OF_RANGE", "count": 20},
                          {"category": "INFINITE_LOOP", "count": 18}
                        ]
                      }
                    ]
                    """.trimIndent(),
                ),
        )

        val hotspots = repository.errorHotspots(classroomId = 7L)

        assertEquals(1, hotspots.size)
        assertEquals(2, hotspots.first().categories.size)
        assertEquals("INDEX_OUT_OF_RANGE", hotspots.first().categories.first().category)
        val recorded = server.takeRequest()
        assertEquals("/api/teacher/classrooms/7/dashboard/error-hotspots", recorded.path)
    }
}
