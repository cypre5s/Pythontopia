package com.pythontopia.feature.dialog

import com.pythontopia.core.data.dto.world.NpcDialogEffectDto
import com.pythontopia.core.data.dto.world.NpcDialogStateDto
import com.pythontopia.core.data.dto.world.NpcTalkRequest
import com.pythontopia.core.network.api.NpcDialogApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NPC 对话仓库；[loadState] 拉取会话状态，[advance] 将 [DialogChoice] 译为 effects 并推进节点。
 * 网络异常时 fallback 为本地 [NpcDialogStateDto]，保证离线可跑通。
 */
@Singleton
class DialogRepository @Inject constructor(
    private val api: NpcDialogApi,
) {

    suspend fun loadState(npcKey: String): NpcDialogStateDto =
        runCatching { api.state(npcKey) }
            .getOrElse { emptyState(npcKey, DemoDialogTree.ROOT_NODE_ID) }

    suspend fun advance(
        npcKey: String,
        fromNodeId: String,
        choice: DialogChoice,
    ): NpcDialogStateDto {
        val effects = buildList {
            if (choice.friendshipDelta != 0) {
                add(
                    NpcDialogEffectDto(
                        type = EFFECT_FRIENDSHIP,
                        delta = choice.friendshipDelta,
                    ),
                )
            }
            if (choice.setFlagKey != null) {
                add(
                    NpcDialogEffectDto(
                        type = EFFECT_SET_FLAG,
                        key = choice.setFlagKey,
                        value = choice.setFlagValue,
                    ),
                )
            }
            add(
                NpcDialogEffectDto(
                    type = EFFECT_COMPLETE_NODE,
                    key = fromNodeId,
                ),
            )
        }
        val request = NpcTalkRequest(
            nodeId = fromNodeId,
            nextNodeId = choice.nextNodeId,
            effects = effects,
        )
        return runCatching { api.talk(npcKey, request) }
            .getOrElse { localAdvance(npcKey, fromNodeId, choice) }
    }

    private fun localAdvance(
        npcKey: String,
        fromNodeId: String,
        choice: DialogChoice,
    ): NpcDialogStateDto = NpcDialogStateDto(
        npcKey = npcKey,
        currentNodeId = choice.nextNodeId,
        completedNodeIds = listOf(fromNodeId),
        friendship = choice.friendshipDelta.coerceAtLeast(0),
        lastTalkedAt = null,
        flags = null,
    )

    private fun emptyState(npcKey: String, rootNodeId: String): NpcDialogStateDto =
        NpcDialogStateDto(
            npcKey = npcKey,
            currentNodeId = rootNodeId,
            completedNodeIds = emptyList(),
            friendship = 0,
            lastTalkedAt = null,
            flags = null,
        )

    private companion object {
        const val EFFECT_FRIENDSHIP = "FRIENDSHIP_DELTA"
        const val EFFECT_COMPLETE_NODE = "COMPLETE_NODE"
        const val EFFECT_SET_FLAG = "SET_FLAG"
    }
}
