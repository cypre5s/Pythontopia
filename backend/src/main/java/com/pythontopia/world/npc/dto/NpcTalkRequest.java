package com.pythontopia.world.npc.dto;

import java.util.List;

/**
 * talk 接口的请求载荷，描述一次对话状态机迁移。
 *
 * <p>{@code nodeId} 为本次完成/触发的节点（并入 completed_node_ids）；{@code nextNodeId} 为推进到的下一节点
 * （留空则停留在当前节点）；{@code effects} 为本次对话产生的副作用列表。
 */
public record NpcTalkRequest(
    String nodeId, String nextNodeId, List<NpcDialogEffectDto> effects) {}
