package com.pythontopia.feature.endless

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pythontopia.core.network.api.MainlineApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

class EndlessRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: EndlessRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = EndlessRepository(
            retrofit.create(EndlessApi::class.java),
            retrofit.create(MainlineApi::class.java),
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `kcCodes 提取代码列表`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"kcId":1,"kcCode":"PY43-KC-001","kcName":"计算思维"},{"kcId":2,"kcCode":"PY43-KC-016","kcName":"分支"}]""",
            ),
        )
        assertEquals(listOf("PY43-KC-001", "PY43-KC-016"), repository.kcCodes())
    }

    @Test
    fun `submit 返回初始提交`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"id":77,"problemId":3,"status":"PENDING"}"""),
        )
        val dto = repository.submit(3, "print(1)")
        assertEquals(77L, dto.id)
        assertEquals(EndlessStatus.PENDING, dto.status)
    }

    @Test
    fun `pollStatus 轮询到通过终态`() = runTest {
        server.enqueue(MockResponse().setBody("""{"submission":{"id":7,"problemId":3,"status":"RUNNING"}}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"submission":{"id":7,"problemId":3,"status":"PASSED"},"judgeResult":{"passedCases":3,"totalCases":3}}""",
            ),
        )
        val emissions = repository.pollStatus(7L, intervalMs = 10L).toList()
        assertEquals(EndlessStatus.PASSED, emissions.last().status)
        assertTrue(emissions.last().isTerminal)
        assertEquals(3, emissions.last().totalCases)
    }
}
