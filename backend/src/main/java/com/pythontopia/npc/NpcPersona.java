package com.pythontopia.npc;

/** NPC 稳定人格核心块，用于后续对话 agent prompt 注入。 */
public record NpcPersona(String npcKey, String displayName, String coreMd) {}
