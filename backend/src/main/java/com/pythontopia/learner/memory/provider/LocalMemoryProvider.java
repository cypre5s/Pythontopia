package com.pythontopia.learner.memory.provider;

import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 默认（{@code MEMORY_PROVIDER=local}）记忆 provider：直接复用既有 {@link MemoryRetrievalService}
 * （recency×importance×relevance + {@code memory_embedding} 语义召回），<strong>不</strong>引入任何外部依赖，
 * 因此也是所有外部 provider 故障时的 fail-soft 回退目标。
 *
 * <p>{@link #remember}：本地记忆为事件溯源（{@code learning_event_log} 由既有学习事件管道写入，本类不触碰），
 * 故仅返回一个本地 {@link MemoryRef}（UUID）供 router 落 {@code agent_memory_ref} 审计，不旁路写入事件表。
 */
@Component
public class LocalMemoryProvider implements MemoryProvider {

  private final MemoryRetrievalService memoryRetrievalService;

  public LocalMemoryProvider(MemoryRetrievalService memoryRetrievalService) {
    this.memoryRetrievalService = memoryRetrievalService;
  }

  @Override
  public String name() {
    return MemoryProviderProperties.LOCAL;
  }

  @Override
  public List<RecalledMemory> recall(long userId, String query, int limit) {
    List<MemoryItemDto> items = memoryRetrievalService.retrieve(userId, null, blankToNull(query), limit);
    return items.stream().map(LocalMemoryProvider::toRecalled).toList();
  }

  @Override
  public MemoryRef remember(long userId, MemoryEvent event) {
    if (event == null || event.isBlank()) {
      throw new MemoryProviderException("local remember requires non-blank text");
    }
    return new MemoryRef(name(), UUID.randomUUID().toString(), event.kind());
  }

  private static RecalledMemory toRecalled(MemoryItemDto item) {
    String kc = item.kcCodes() == null || item.kcCodes().isEmpty() ? null : item.kcCodes().get(0);
    return new RecalledMemory(
        item.summaryZh(),
        kc,
        item.eventType(),
        parseInstant(item.createdAt()),
        item.totalScore(),
        MemoryProviderProperties.LOCAL);
  }

  private static Instant parseInstant(String iso) {
    if (iso == null || iso.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(iso);
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
