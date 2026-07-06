package com.pythontopia.learner.memory.provider;

/**
 * {@link MemoryProvider#remember} 的返回：provider 侧记忆映射，由 {@link MemoryProviderRouter} 落
 * {@code agent_memory_ref(provider, external_id, kind)}。
 *
 * @param provider 写入 provider（local / mem0 / zep）
 * @param externalId provider 侧记忆主键
 * @param kind 记忆类别
 */
public record MemoryRef(String provider, String externalId, String kind) {}
