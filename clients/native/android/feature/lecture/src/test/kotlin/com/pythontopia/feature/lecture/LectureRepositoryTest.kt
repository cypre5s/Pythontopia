package com.pythontopia.feature.lecture

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pythontopia.core.network.BaseUrlProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

@Suppress("MaxLineLength") // MockWebServer JSON 夹具单行更易读，豁免行宽
class LectureRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: LectureRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LectureApi::class.java)
        val baseUrlProvider = mockk<BaseUrlProvider>().apply {
            every { current() } returns server.url("/").toString()
        }
        repository = LectureRepository(api, baseUrlProvider)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `listHandouts 反序列化`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":42,"type":"HANDOUT","title":"input 函数讲义","summary":"读取输入","kcCodes":["PY43-KC-001"],"groundingStatus":"PASSED"}]""",
            ),
        )
        val items = repository.listHandouts()
        assertEquals(1, items.size)
        assertEquals("input 函数讲义", items[0].title)
        assertEquals("PASSED", items[0].groundingStatus)
    }

    @Test
    fun `findOne 解析后 parseHandout 还原结构化讲义`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":42,"type":"HANDOUT","title":"input","content":{"sections":[{"heading":"概念","paragraphs":["读取一行"],"code_snippets":["x = input()"],"page_refs":[12,13]}]},"evidencePageIds":[12,13],"groundingStatus":"PASSED"}""",
            ),
        )
        val resource = repository.findOne(42).getOrThrow()
        assertEquals(42L, resource.id)
        assertEquals(listOf(12L, 13L), resource.evidencePageIds)

        val handout = repository.parseHandout(resource.content)
        assertNotNull(handout)
        assertEquals(1, handout!!.sections.size)
        assertEquals("概念", handout.sections[0].heading)
        assertEquals(listOf("x = input()"), handout.sections[0].codeSnippets)
        assertEquals(listOf(12, 13), handout.sections[0].pageRefs)
    }

    @Test
    fun `非结构化 content 返回 null`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"id":7,"type":"MINDMAP","title":"思维导图","content":{"root":{"label":"x"}}}"""),
        )
        val resource = repository.findOne(7).getOrThrow()
        assertEquals(null, repository.parseHandout(resource.content))
    }
}
