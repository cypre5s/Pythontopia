<script setup lang="ts">
import { computed } from 'vue';
import { useReducedMotion } from '@/composables/useReducedMotion';
import type { MemoryItemDto } from '@/api/npc';

const props = defineProps<{
  npcName: string;
  memories: MemoryItemDto[];
  citedEventIds?: number[];
}>();

const prefersReducedMotion = useReducedMotion();

const citedIds = computed(() => new Set(props.citedEventIds ?? []));

function percent(value: number): string {
  return `${Math.round(Math.max(0, Math.min(1, value)) * 100)}%`;
}

function eventLabel(memory: MemoryItemDto): string {
  return `event #${memory.eventId}`;
}

function formatDate(createdAt: string | null): string {
  if (!createdAt) {
    return '时间未知';
  }
  return createdAt.slice(0, 10);
}
</script>

<template>
  <section
    class="npc-memory-badge"
    :class="{ 'npc-memory-badge--reduced-motion': prefersReducedMotion }"
    :data-motion="prefersReducedMotion ? 'reduced' : 'default'"
    data-testid="npc-memory-badge"
    aria-label="NPC 记忆玻璃盒"
  >
    <details
      class="npc-memory-badge__details"
      :open="memories.length > 0"
    >
      <summary class="npc-memory-badge__summary">
        <span class="npc-memory-badge__title">{{ npcName }} 记得你</span>
        <span
          class="npc-memory-badge__count"
          data-testid="npc-memory-count"
        >
          {{ memories.length }} 条
        </span>
      </summary>

      <p
        v-if="memories.length === 0"
        class="npc-memory-badge__empty"
        role="status"
        data-testid="npc-memory-empty"
      >
        还没有可引用的真实记忆。继续看、学、练、测后，NPC 会把相关事件写进这里。
      </p>

      <ol
        v-else
        class="npc-memory-badge__list"
      >
        <li
          v-for="memory in memories"
          :key="memory.eventId"
          class="npc-memory-badge__item"
          :class="{ 'npc-memory-badge__item--cited': citedIds.has(memory.eventId) }"
          :data-testid="`npc-memory-item-${memory.eventId}`"
        >
          <div class="npc-memory-badge__item-head">
            <strong>{{ memory.summaryZh }}</strong>
            <span class="npc-memory-badge__event">{{ eventLabel(memory) }}</span>
          </div>

          <div
            class="npc-memory-badge__meta"
            aria-label="事件来源"
          >
            <span>{{ memory.eventType }}</span>
            <span>{{ formatDate(memory.createdAt) }}</span>
            <span>重要度 {{ memory.importance }}/5</span>
          </div>

          <div
            class="npc-memory-badge__kc-list"
            aria-label="关联知识点"
          >
            <span
              v-for="kcCode in memory.kcCodes"
              :key="kcCode"
              class="npc-memory-badge__kc"
            >
              {{ kcCode }}
            </span>
          </div>

          <dl
            class="npc-memory-badge__scores"
            aria-label="记忆检索打分分解"
          >
            <div>
              <dt>近期</dt>
              <dd>{{ ` ${percent(memory.recencyScore)}` }}</dd>
            </div>
            <div>
              <dt>重要</dt>
              <dd>{{ ` ${percent(memory.importanceScore)}` }}</dd>
            </div>
            <div>
              <dt>相关</dt>
              <dd>{{ ` ${percent(memory.relevanceScore)}` }}</dd>
            </div>
            <div>
              <dt>综合</dt>
              <dd>{{ ` ${percent(memory.totalScore)}` }}</dd>
            </div>
          </dl>
        </li>
      </ol>
    </details>
  </section>
</template>

<style scoped>
.npc-memory-badge {
  width: 100%;
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-read);
}

.npc-memory-badge__details {
  border: 2px solid var(--pyt-color-border);
  background: rgba(255, 247, 230, 0.86);
  box-shadow: var(--pyt-shadow-card);
  border-radius: var(--pyt-radius-md);
  overflow: hidden;
}

.npc-memory-badge__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-3);
  cursor: pointer;
  font-family: var(--pyt-font-pixel);
  background: var(--pyt-color-bg-soft);
}

.npc-memory-badge__title {
  font-size: var(--pyt-font-size-base);
}

.npc-memory-badge__count {
  padding: 2px 6px;
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-info-bg);
  color: var(--pyt-color-info);
  font-size: var(--pyt-font-size-xs);
}

.npc-memory-badge__empty {
  margin: 0;
  padding: var(--pyt-space-4);
  color: var(--pyt-color-text-muted);
  line-height: 1.7;
}

.npc-memory-badge__list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--pyt-space-3);
  margin: 0;
  padding: var(--pyt-space-3);
  list-style: none;
}

.npc-memory-badge__item {
  display: grid;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-3);
  border: 1px solid var(--pyt-color-border-soft);
  background: rgba(253, 243, 208, 0.68);
  border-radius: var(--pyt-radius-sm);
  transition: transform var(--pyt-motion-base) ease, border-color var(--pyt-motion-base) ease;
}

.npc-memory-badge__item--cited {
  border-color: var(--pyt-color-gold);
  background: rgba(255, 209, 102, 0.18);
}

.npc-memory-badge__item-head {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-1);
}

.npc-memory-badge__event {
  width: fit-content;
  padding: 1px 5px;
  border: 1px solid var(--pyt-color-border-soft);
  color: var(--pyt-color-text-muted);
  font-family: var(--pyt-font-pixel-mono);
  font-size: var(--pyt-font-size-xs);
}

.npc-memory-badge__meta,
.npc-memory-badge__kc-list,
.npc-memory-badge__scores {
  display: flex;
  flex-wrap: wrap;
  gap: var(--pyt-space-2);
}

.npc-memory-badge__meta {
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-sm);
}

.npc-memory-badge__kc {
  padding: 2px 5px;
  background: var(--pyt-color-accent-bg);
  color: var(--pyt-color-accent);
  font-family: var(--pyt-font-pixel-mono);
  font-size: var(--pyt-font-size-xs);
}

.npc-memory-badge__scores {
  margin: 0;
}

.npc-memory-badge__scores > div {
  display: grid;
  gap: 2px;
  min-width: 58px;
  padding: var(--pyt-space-2);
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border-soft);
}

.npc-memory-badge__scores dt {
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-xs);
}

.npc-memory-badge__scores dd {
  margin: 0;
  font-family: var(--pyt-font-pixel-mono);
  color: var(--pyt-color-text-primary);
}

@media (max-width: 640px) {
  .npc-memory-badge__summary,
  .npc-memory-badge__list,
  .npc-memory-badge__meta,
  .npc-memory-badge__scores {
    grid-template-columns: 1fr;
  }

  .npc-memory-badge__summary,
  .npc-memory-badge__meta,
  .npc-memory-badge__scores {
    align-items: stretch;
    flex-direction: column;
  }

  .npc-memory-badge__list {
    display: grid;
  }
}

@media (prefers-reduced-motion: reduce) {
  .npc-memory-badge__item {
    transition: none;
  }
}

.npc-memory-badge--reduced-motion .npc-memory-badge__item {
  transition: none;
}
</style>
