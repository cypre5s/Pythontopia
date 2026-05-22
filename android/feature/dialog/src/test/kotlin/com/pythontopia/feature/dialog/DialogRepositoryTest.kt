package com.pythontopia.feature.dialog

import com.pythontopia.core.data.dto.world.NpcDialogEffectDto
import com.pythontopia.core.data.dto.world.NpcDialogStateDto
import com.pythontopia.core.data.dto.world.NpcTalkRequest
import com.pythontopia.core.network.api.NpcDialogApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DialogRepositoryTest {

    private val api: NpcDialogApi = mockk()
    private val repository = DialogRepository(api)

    @Test
    fun `loadState returns remote state when api succeeds`() = runTest {
        coEvery { api.state("mayor_py") } returns NpcDialogStateDto(
            npcKey = "mayor_py",
            currentNodeId = "where_to_start",
            friendship = 7,
        )
        val result = repository.loadState("mayor_py")
        assertEquals("where_to_start", result.currentNodeId)
        assertEquals(7, result.friendship)
    }

    @Test
    fun `loadState falls back to empty state when api throws`() = runTest {
        coEvery { api.state("mayor_py") } throws RuntimeException("offline")
        val result = repository.loadState("mayor_py")
        assertEquals(DemoDialogTree.ROOT_NODE_ID, result.currentNodeId)
        assertEquals(0, result.friendship)
    }

    @Test
    fun `advance encodes friendship + complete_node + set_flag into effects`() = runTest {
        val request = slot<NpcTalkRequest>()
        coEvery { api.talk(any(), capture(request)) } returns NpcDialogStateDto(
            npcKey = "mayor_py",
            currentNodeId = null,
            friendship = 4,
        )
        val choice = DialogChoice(
            label = "记一下",
            nextNodeId = null,
            friendshipDelta = 2,
            setFlagKey = "android_r12_demo_played",
            setFlagValue = "true",
        )
        repository.advance("mayor_py", "chat_progress", choice)

        coVerify { api.talk("mayor_py", any()) }
        val effects = request.captured.effects.orEmpty()
        val friendship = effects.first { it.type == "FRIENDSHIP_DELTA" }
        val complete = effects.first { it.type == "COMPLETE_NODE" }
        val flag = effects.first { it.type == "SET_FLAG" }
        assertEquals(2, friendship.delta)
        assertEquals("chat_progress", complete.key)
        assertEquals("android_r12_demo_played", flag.key)
        assertEquals("true", flag.value)
        // 验证 nextNodeId 透传：终结分支为 null
        assertTrue(request.captured.nextNodeId == null)
    }

    @Test
    fun `advance falls back to local state when api throws`() = runTest {
        coEvery { api.talk(any(), any()) } throws RuntimeException("offline")
        val choice = DialogChoice("好的", nextNodeId = "where_to_start", friendshipDelta = 0)
        val result = repository.advance("mayor_py", "greet", choice)
        assertNotNull(result)
        assertEquals("mayor_py", result.npcKey)
        assertEquals("where_to_start", result.currentNodeId)
        assertEquals(listOf("greet"), result.completedNodeIds)
        // 没有友善度增量时 effects 不应包含 FRIENDSHIP_DELTA，但本地 fallback 不发请求
        // 这里仅验证 fallback 路径成功 + 字段映射正确
    }

    @Test
    fun `advance sends only COMPLETE_NODE when no friendship and no flag`() = runTest {
        val request = slot<NpcTalkRequest>()
        coEvery { api.talk(any(), capture(request)) } returns NpcDialogStateDto(
            npcKey = "mayor_py",
            currentNodeId = "introduce_town",
            friendship = 0,
        )
        val choice = DialogChoice("请告诉我这里有什么？", nextNodeId = "introduce_town")
        repository.advance("mayor_py", "greet", choice)

        val effects: List<NpcDialogEffectDto> = request.captured.effects.orEmpty()
        // 至少包含 COMPLETE_NODE 一条；不含 FRIENDSHIP_DELTA / SET_FLAG
        assertTrue(effects.any { it.type == "COMPLETE_NODE" && it.key == "greet" })
        assertTrue(effects.none { it.type == "FRIENDSHIP_DELTA" })
        assertTrue(effects.none { it.type == "SET_FLAG" })
    }
}
