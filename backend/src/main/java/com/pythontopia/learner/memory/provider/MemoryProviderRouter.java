package com.pythontopia.learner.memory.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * 记忆 provider 路由固定使用 {@link LocalMemoryProvider}，不向 Mem0/Zep 等外部服务发送学习记录。
 *
 * <p>{@link #remember} 成功后把外部映射落 {@code agent_memory_ref}（best-effort，DB 故障仅告警不阻断）。
 */
@Component
public class MemoryProviderRouter {

  private static final Logger log = LoggerFactory.getLogger(MemoryProviderRouter.class);

  private final LocalMemoryProvider local;
  private final Map<String, MemoryProvider> byName;
  private final MemoryProviderProperties properties;
  private final AgentMemoryRefRepository refRepository;

  public MemoryProviderRouter(
      LocalMemoryProvider local,
      Mem0MemoryProvider mem0,
      ZepMemoryProvider zep,
      MemoryProviderProperties properties,
      AgentMemoryRefRepository refRepository) {
    this.local = local;
    this.properties = properties;
    this.refRepository = refRepository;
    Map<String, MemoryProvider> map = new LinkedHashMap<>();
    map.put(local.name(), local);
    map.put(mem0.name(), mem0);
    map.put(zep.name(), zep);
    this.byName = Map.copyOf(map);
  }

  /** 当前生效 provider 名（诊断 / 审计）。 */
  public String activeProvider() {
    return active().name();
  }

  private MemoryProvider active() {
    return byName.getOrDefault(properties.provider(), local);
  }

  /**
   * 召回：active provider 优先；外部 provider 故障时 fail-soft 回退 local。local 自身故障不吞（属本地基础设施问题）。
   */
  public List<RecalledMemory> recall(long userId, String query, int limit) {
    MemoryProvider provider = active();
    if (provider == local) {
      return local.recall(userId, query, limit);
    }
    try {
      return provider.recall(userId, query, limit);
    } catch (MemoryProviderException ex) {
      log.warn(
          "memory provider '{}' recall failed, fail-soft 回退 local: {}",
          provider.name(),
          ex.getMessage());
      return local.recall(userId, query, limit);
    }
  }

  /**
   * 写入：active provider 优先；外部故障回退 local。成功后 best-effort 落 {@code agent_memory_ref}。
   */
  public MemoryRef remember(long userId, MemoryEvent event) {
    MemoryProvider provider = active();
    MemoryRef ref;
    if (provider == local) {
      ref = local.remember(userId, event);
    } else {
      try {
        ref = provider.remember(userId, event);
      } catch (MemoryProviderException ex) {
        log.warn(
            "memory provider '{}' remember failed, fail-soft 回退 local: {}",
            provider.name(),
            ex.getMessage());
        ref = local.remember(userId, event);
      }
    }
    persistRef(userId, ref);
    return ref;
  }

  /** 落库审计；DB 故障（如 V169 未迁移）仅告警，不阻断 remember 主流程。 */
  private void persistRef(long userId, MemoryRef ref) {
    if (ref == null || ref.externalId() == null || ref.externalId().isBlank()) {
      return;
    }
    try {
      refRepository.record(userId, ref.provider(), ref.externalId(), ref.kind());
    } catch (DataAccessException ex) {
      log.warn(
          "agent_memory_ref 落库降级 userId={} provider={}: {}",
          userId,
          ref.provider(),
          ex.getMostSpecificCause() == null
              ? ex.getMessage()
              : ex.getMostSpecificCause().getMessage());
    }
  }
}
