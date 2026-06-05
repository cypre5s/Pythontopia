package com.pythontopia.world.npc.dto;

/**
 * talk 接口（{@code POST /api/world/npcs/{npcKey}/talk}）应用的 effect 载荷。
 *
 * <p>支持的 {@code type}：
 *
 * <ul>
 *   <li>{@code FRIENDSHIP_DELTA} — 按 {@code delta} 调整好感度（钳制到 0..100）。
 *   <li>{@code COMPLETE_NODE} — 将 {@code key}（节点 id）追加到 {@code completed_node_ids}。
 *   <li>{@code SET_FLAG} — 将 {@code value}（原始字符串）写入 {@code flags[key]}。
 * </ul>
 *
 * <p>未知 {@code type} 静默忽略以保持前向兼容。
 */
public record NpcDialogEffectDto(String type, Integer delta, String key, String value) {}
