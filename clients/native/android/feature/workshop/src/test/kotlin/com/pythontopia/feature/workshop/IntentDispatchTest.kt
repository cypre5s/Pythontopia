package com.pythontopia.feature.workshop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/** 纯 JVM：意图字符串 → 种类 → 分派映射，重点保证 REVIEW 不被当作资源生成误路由。 */
class IntentDispatchTest {

    @Test
    fun `review routes to mine echoes not resource generation`() {
        val dispatch = response("REVIEW", kcCodes = listOf("PY43-KC-001")).toDispatch()

        val review = assertInstanceOf(IntentDispatch.OpenReview::class.java, dispatch)
        assertEquals(listOf("PY43-KC-001"), review.kcCodes)
    }

    @Test
    fun `ask routes to tutor`() {
        assertInstanceOf(IntentDispatch.OpenTutor::class.java, response("ASK").toDispatch())
    }

    @Test
    fun `unknown intent falls back to tutor rather than generating a resource`() {
        assertInstanceOf(IntentDispatch.OpenTutor::class.java, response("definitely-not-an-intent").toDispatch())
    }

    @Test
    fun `resource intents route to workshop generation carrying their kind`() {
        val resourceIntents = listOf(
            "HANDOUT" to IntentKind.HANDOUT,
            "MINDMAP" to IntentKind.MINDMAP,
            "PRACTICE" to IntentKind.PRACTICE,
            "CODE_LAB" to IntentKind.CODE_LAB,
            "READING" to IntentKind.READING,
            "STORYBOARD" to IntentKind.STORYBOARD,
            "VIDEO" to IntentKind.VIDEO,
        )
        resourceIntents.forEach { (raw, expectedKind) ->
            val dispatch = response(raw).toDispatch()
            val generate = assertInstanceOf(IntentDispatch.Generate::class.java, dispatch)
            assertEquals(expectedKind, generate.kind)
        }
    }

    @Test
    fun `kind parsing is case insensitive and trims whitespace`() {
        assertEquals(IntentKind.REVIEW, IntentKind.from("  review "))
        assertEquals(IntentKind.CODE_LAB, IntentKind.from("code_lab"))
        assertEquals(IntentKind.UNKNOWN, IntentKind.from(""))
    }

    private fun response(intent: String, kcCodes: List<String> = emptyList()): IntentRouteResponse =
        IntentRouteResponse(
            intent = intent,
            kcCodes = kcCodes,
            focusHint = "复盘 for 循环",
            confidence = 0.7,
            reason = "测试",
        )
}
