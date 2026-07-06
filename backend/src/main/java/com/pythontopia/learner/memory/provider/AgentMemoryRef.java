package com.pythontopia.learner.memory.provider;

import java.time.Instant;

/**
 * {@code agent_memory_ref} 一行：外部记忆 id 映射 / remember 审计快照（V169）。
 *
 * @param id 主键
 * @param userId 归属学生
 * @param provider 写入 provider（local / mem0 / zep）
 * @param externalId provider 侧记忆主键
 * @param kind 记忆类别
 * @param createdAt 落库时间
 */
public record AgentMemoryRef(
    long id, long userId, String provider, String externalId, String kind, Instant createdAt) {}
