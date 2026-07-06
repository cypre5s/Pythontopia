package com.pythontopia.feature.library

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

class LibraryRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: LibraryRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LibraryApi::class.java)
        repository = LibraryRepository(api)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `chapters 反序列化`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":179,"orderIndex":1,"title":"计算思维","summaryMarkdown":"导论"}]""",
            ),
        )
        val chapters = repository.chapters()
        assertEquals(1, chapters.size)
        assertEquals("计算思维", chapters[0].title)
        assertEquals(1, chapters[0].orderIndex)
    }

    @Test
    fun `pages 反序列化 contentMarkdown`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":10707,"pageNo":26,"title":"我国计算机的发展","contentMarkdown":"华罗庚..."}]""",
            ),
        )
        val pages = repository.pages("PY43-KC-001")
        assertEquals(1, pages.size)
        assertEquals(26, pages[0].pageNo)
        assertEquals("华罗庚...", pages[0].contentMarkdown)
    }

    @Test
    fun `网络失败兜底空列表`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(emptyList<LibraryChapterDto>(), repository.chapters())
    }
}
