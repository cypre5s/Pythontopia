package com.pythontopia.learner.memory.provider;

import java.util.List;

/**
 * Agentic Loop 记忆层 provider 抽象：把「召回（recall）」与「记忆（remember）」从具体后端（本地
 * {@code memory_embedding} / Mem0 / Zep）解耦，由 {@link MemoryProviderRouter} 按 {@code MEMORY_PROVIDER}
 * env 路由，外部实现失败时 fail-soft 回退 {@link LocalMemoryProvider}。
 *
 * <p>所有实现必须满足：
 *
 * <ul>
 *   <li>{@link #recall} 只读、按 {@code userId} 隔离，不得跨用户泄露；无命中返回空列表（不抛）。
 *   <li>{@link #remember} 写一条记忆并返回外部映射 {@link MemoryRef}（由 router 落 {@code agent_memory_ref}）。
 *   <li>外部不可达 / 5xx / 配置缺失等故障：<strong>抛 {@link MemoryProviderException}</strong>，由 router 捕获回退 local；
 *       绝不静默返回脏数据。
 * </ul>
 */
public interface MemoryProvider {

  /** provider 标识，与 {@code MEMORY_PROVIDER} / {@code agent_memory_ref.provider} 取值一致。 */
  String name();

  /**
   * 召回该学生与 {@code query} 最相关的 topK 记忆（{@code query} 为空时退化为最近记忆）。
   *
   * @param userId 当前学生（鉴权后注入，杜绝越权）
   * @param query 语义查询；可空 / 空白
   * @param limit 期望条数；实现自行 clamp 到安全区间
   * @return 召回结果（可空安全，绝不返回 {@code null}）
   * @throws MemoryProviderException 外部 provider 故障时抛出，触发 router fail-soft 回退 local
   */
  List<RecalledMemory> recall(long userId, String query, int limit);

  /**
   * 为该学生写入一条记忆。
   *
   * @return provider 侧映射（provider/externalId/kind）；router 据此落 {@code agent_memory_ref}
   * @throws MemoryProviderException 外部 provider 故障时抛出
   */
  MemoryRef remember(long userId, MemoryEvent event);
}
