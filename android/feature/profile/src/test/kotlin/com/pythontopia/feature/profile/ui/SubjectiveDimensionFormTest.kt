package com.pythontopia.feature.profile.ui

import com.pythontopia.feature.profile.data.ProfileDimensions
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 通过 [DimensionFormState] 单测覆盖自评表单交互路径，无需 Compose UI 测试框架。 */
class SubjectiveDimensionFormTest {

    @Test
    fun `subjective dimension catalog covers all 4 self-editable keys`() {
        val keys = SUBJECTIVE_DIMENSIONS.map { it.key }.toSet()
        assertEquals(ProfileDimensions.SELF_EDITABLE, keys)
        SUBJECTIVE_DIMENSIONS.forEach { spec ->
            assertTrue(spec.items.size in 3..5, "dimension ${spec.key} has ${spec.items.size} items, expected 3..5")
            assertTrue(spec.items.map { it.key }.toSet().size == spec.items.size, "item keys must be unique within ${spec.key}")
        }
    }

    @Test
    fun `setScore returns new state and overrides previous selection`() {
        val state = DimensionFormState()
            .setScore("goal_clarity", 4)
            .setScore("goal_clarity", 5)
        assertEquals(5, state.scores["goal_clarity"])
        assertEquals(1, state.scores.size)
    }

    @Test
    fun `setScore rejects values outside Likert 1-5 range`() {
        assertThrows(IllegalArgumentException::class.java) {
            DimensionFormState().setScore("goal_clarity", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DimensionFormState().setScore("goal_clarity", 6)
        }
    }

    @Test
    fun `isFilled is false until every item in spec has a score`() {
        val spec = SUBJECTIVE_DIMENSIONS.first { it.key == ProfileDimensions.LEARNING_GOAL }
        var state = DimensionFormState()
        spec.items.dropLast(1).forEach { item ->
            state = state.setScore(item.key, 3)
        }
        assertFalse(state.isFilled(spec))
        state = state.setScore(spec.items.last().key, 3)
        assertTrue(state.isFilled(spec))
    }

    @Test
    fun `buildRequestFor returns null when not all items filled`() {
        val spec = SUBJECTIVE_DIMENSIONS.first { it.key == ProfileDimensions.LEARNING_PREFERENCE }
        val partial = DimensionFormState().setScore(spec.items.first().key, 5)
        assertNull(partial.buildRequestFor(spec))
    }

    @Test
    fun `buildRequestFor maps all-3 Likert to zero scoreDelta and writes metadata`() {
        val spec = SUBJECTIVE_DIMENSIONS.first { it.key == ProfileDimensions.PROFESSIONAL_BACKGROUND }
        var state = DimensionFormState()
        spec.items.forEach { state = state.setScore(it.key, 3) }
        val request = state.buildRequestFor(spec)
        assertNotNull(request)
        assertEquals(0.0, request!!.scoreDelta)
        assertEquals(1, request.evidenceIncrement)
        val items = request.metadata!!["items"]!!.jsonObject
        spec.items.forEach { item ->
            assertEquals(3, items[item.key]!!.jsonPrimitive.intOrNull)
        }
    }

    @Test
    fun `buildRequestFor maps all-5 Likert to positive scoreDelta within range`() {
        val spec = SUBJECTIVE_DIMENSIONS.first { it.key == ProfileDimensions.EMOTION_PERSISTENCE }
        var state = DimensionFormState()
        spec.items.forEach { state = state.setScore(it.key, 5) }
        val request = state.buildRequestFor(spec)
        assertNotNull(request)
        assertEquals(0.5, request!!.scoreDelta!!, 1e-9)
        assertTrue(request.scoreDelta in -1.0..1.0)
    }

    @Test
    fun `buildRequestFor maps all-1 Likert to negative scoreDelta within range`() {
        val spec = SUBJECTIVE_DIMENSIONS.first { it.key == ProfileDimensions.LEARNING_GOAL }
        var state = DimensionFormState()
        spec.items.forEach { state = state.setScore(it.key, 1) }
        val request = state.buildRequestFor(spec)
        assertNotNull(request)
        assertEquals(-0.5, request!!.scoreDelta!!, 1e-9)
    }

    @Test
    fun `interaction trace - user picks mixed scores then submits`() {
        // 模拟一次完整交互：用户在 LEARNING_PREFERENCE 上选完 5 项再提交。
        val spec = SUBJECTIVE_DIMENSIONS.first { it.key == ProfileDimensions.LEARNING_PREFERENCE }
        val picks = listOf(4, 5, 3, 4, 2) // 不同打分，平均 = 3.6
        require(picks.size == spec.items.size)

        var state = DimensionFormState()
        spec.items.zip(picks).forEach { (item, score) ->
            // 用户分两步：先误点 1，再改成正确的分数，state holder 必须始终保留最新选择
            state = state.setScore(item.key, 1)
            assertFalse(state.isFilled(spec) && spec.items.indexOf(item) < spec.items.lastIndex)
            state = state.setScore(item.key, score)
        }
        assertTrue(state.isFilled(spec))
        val request = state.buildRequestFor(spec)!!
        // (3.6 - 3) / 4 = 0.15
        assertEquals(0.15, request.scoreDelta!!, 1e-9)
        val items: JsonObject = request.metadata!!["items"]!!.jsonObject
        assertEquals(JsonPrimitive(4), items["pref_video"])
        assertEquals(JsonPrimitive(2), items["pref_session"])
    }
}
