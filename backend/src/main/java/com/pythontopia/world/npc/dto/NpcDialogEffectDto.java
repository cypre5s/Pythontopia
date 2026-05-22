package com.pythontopia.world.npc.dto;

/**
 * Effect payload applied during {@code POST /api/world/npcs/{npcKey}/talk}.
 *
 * <p>Recognised {@code type}s:
 *
 * <ul>
 *   <li>{@code FRIENDSHIP_DELTA} — adjust friendship by {@code delta} (clamped 0..100).
 *   <li>{@code COMPLETE_NODE} — append {@code key} (node id) to {@code completed_node_ids}.
 *   <li>{@code SET_FLAG} — write {@code value} (raw string) into {@code flags[key]}.
 * </ul>
 *
 * Unknown {@code type}s are silently ignored to keep narrative iteration cheap.
 */
public record NpcDialogEffectDto(String type, Integer delta, String key, String value) {}
