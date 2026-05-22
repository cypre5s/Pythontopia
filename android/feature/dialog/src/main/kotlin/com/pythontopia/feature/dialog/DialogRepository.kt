package com.pythontopia.feature.dialog

import com.pythontopia.core.data.dto.world.NpcDialogEffectDto
import com.pythontopia.core.data.dto.world.NpcDialogStateDto
import com.pythontopia.core.data.dto.world.NpcTalkRequest
import com.pythontopia.core.network.api.NpcDialogApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 [NpcDialogApi] 包装成 demo 友好的接口：
 *  - [loadState] 拉取首次状态（404 即视为新会话，本地以 [DemoDialogTree.ROOT_NODE_ID] 启动）。
 *  - [advance] 推进对话节点，effects 由 [DialogChoice] 翻译为后端枚举 type。
 *
 * 离线/未登录场景下，本仓库捕获网络异常并返回纯本地 [NpcDialogStateDto]，
 * 让 demo 不依赖 backend 正常跑通；线上联调时如果接口 200 就走真实状态。
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
