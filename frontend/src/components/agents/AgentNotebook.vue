<script setup lang="ts">
/**
 * AgentNotebook：「Agent 关于你的笔记本」——记忆流可视（生成式教学智能体 Task 3.1）。
 *
 * 把后端 `GET /api/learner/memory` 的 topK 记忆渲染成一条「关于你的观察」时间线：
 *   - 每条 = 一个真实学习事件（中文摘要 + 重要度星级 + 相对时间），可溯源、防幻觉；
 *   - 点开单条看 recency / importance / relevance 三项检索打分分解（进度条 + 百分比）；
 *   - 空态 / 加载失败均降级为友好文案，不抛错（演示链路不断）。
 *
 * 设计令牌：颜色/间距/圆角一律走 `--pyt-*`；星级用 gold、三项分数用 info/warning/accent 语义色。
 * 可访问性：星级与分数条对屏读给文字替代；reduced-motion 下去掉进度条动画；窄屏自适应换行。
 */
import { onMounted, ref } from 'vue';
import { getMemory } from '@/api/memory';
import type { MemoryItemDto } from '@/api/dto/Memory';

const props = defineProps<{
  /** 可选聚焦知识点；传入则后端按相关性加权排序。 */
  focusKc?: string;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const loading = ref(true);
const failed = ref(false);
const items = ref<MemoryItemDto[]>([]);
const expandedIds = ref<number[]>([]);

function isExpanded(eventId: number): boolean {
  return expandedIds.value.includes(eventId);
}

function toggle(eventId: number): void {
  expandedIds.value = isExpanded(eventId)
    ? expandedIds.value.filter((id) => id !== eventId)
    : [...expandedIds.value, eventId];
}

function clamp01(value: number): number {
  if (Number.isNaN(value) || value < 0) {
    return 0;
  }
  return value > 1 ? 1 : value;
}

function pct(value: number): string {
  return `${Math.round(clamp01(value) * 100)}%`;
}

function stars(importance: number): string {
  const filled = Math.max(0, Math.min(5, Math.round(importance)));
  return '★'.repeat(filled) + '☆'.repeat(5 - filled);
}

function relativeTime(iso: string | null): string {
  if (!iso) {
    return '时间未知';
  }
  const at = Date.parse(iso);
  if (Number.isNaN(at)) {
    return '时间未知';
  }
  const seconds = Math.floor((Date.now() - at) / 1000);
  if (seconds < 60) {
    return '刚刚';
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} 分钟前`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours} 小时前`;
  }
  const days = Math.floor(hours / 24);
  if (days < 30) {
    return `${days} 天前`;
  }
  const months = Math.floor(days / 30);
  if (months < 12) {
    return `${months} 个月前`;
  }
  return `${Math.floor(days / 365)} 年前`;
}

interface ScoreBar {
  key: 'recency' | 'importance' | 'relevance';
  label: string;
  value: number;
}

function scoreBars(item: MemoryItemDto): ScoreBar[] {
  return [
    { key: 'recency', label: '近因', value: item.recencyScore },
    { key: 'importance', label: '重要度', value: item.importanceScore },
    { key: 'relevance', label: '相关性', value: item.relevanceScore },
  ];
}

onMounted(async () => {
  try {
    items.value = await getMemory(props.focusKc);
  } catch {
    // 记忆是只读陪伴信息，拉取失败时降级为空态，绝不打断学习主链路。
    failed.value = true;
    items.value = [];
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section
    class="agent-notebook"
    data-testid="agent-notebook"
    aria-label="Agent 的笔记本"
  >
    <header class="agent-notebook__head">
      <h2 class="agent-notebook__title">
        📓 关于你的观察
      </h2>
      <button
        type="button"
        class="agent-notebook__close"
        data-testid="notebook-close"
        aria-label="关闭笔记本"
        @click="emit('close')"
      >
        ✕
      </button>
    </header>

    <p
      v-if="loading"
      class="agent-notebook__hint"
      data-testid="notebook-loading"
    >
      正在翻看笔记…
    </p>

    <ul
      v-else-if="items.length > 0"
      class="agent-notebook__list"
    >
      <li
        v-for="item in items"
        :key="item.eventId"
        class="agent-notebook__item"
        data-testid="notebook-item"
      >
        <div class="agent-notebook__row">
          <span
            class="agent-notebook__stars"
            data-testid="notebook-stars"
            :data-importance="item.importance"
            :aria-label="`重要度 ${item.importance} / 5`"
          >
            <span aria-hidden="true">{{ stars(item.importance) }}</span>
          </span>
          <p class="agent-notebook__summary">
            {{ item.summaryZh }}
          </p>
          <span class="agent-notebook__time">{{ relativeTime(item.createdAt) }}</span>
        </div>

        <button
          type="button"
          class="agent-notebook__toggle"
          data-testid="notebook-scores-toggle"
          :aria-expanded="isExpanded(item.eventId)"
          @click="toggle(item.eventId)"
        >
          {{ isExpanded(item.eventId) ? '收起打分' : '为什么记下它？' }}
        </button>

        <div
          v-if="isExpanded(item.eventId)"
          class="agent-notebook__scores"
          data-testid="notebook-scores"
        >
          <div
            v-for="bar in scoreBars(item)"
            :key="bar.key"
            class="agent-notebook__score"
            :class="`agent-notebook__score--${bar.key}`"
            :data-testid="`notebook-score-${bar.key}`"
          >
            <span class="agent-notebook__score-label">{{ bar.label }}</span>
            <span
              class="agent-notebook__score-track"
              aria-hidden="true"
            >
              <span
                class="agent-notebook__score-fill"
                :style="{ width: pct(bar.value) }"
              />
            </span>
            <span class="agent-notebook__score-value">{{ pct(bar.value) }}</span>
          </div>
        </div>
      </li>
    </ul>

    <div
      v-else
      class="agent-notebook__empty"
      data-testid="notebook-empty"
      role="status"
    >
      <span
        class="agent-notebook__empty-glyph"
        aria-hidden="true"
      >
        📖
      </span>
      <p class="agent-notebook__empty-title">
        {{ failed ? '笔记暂时翻不开' : '还没有关于你的学习观察' }}
      </p>
      <p class="agent-notebook__empty-hint">
        {{ failed ? '稍后再来看看，先去练几道题吧' : '去练几道题、看几页讲义，这里就会记下你的成长啦' }}
      </p>
    </div>
  </section>
</template>

<style scoped>
.agent-notebook {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  width: 100%;
  padding: var(--pyt-space-4);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  background: var(--pyt-color-bg-elevated);
  box-shadow: var(--pyt-shadow-card);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-read);
}

.agent-notebook__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}

.agent-notebook__title {
  margin: 0;
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-lg);
  color: var(--pyt-color-text-primary);
}

.agent-notebook__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-base);
  cursor: pointer;
}

.agent-notebook__close:hover {
  background: var(--pyt-color-bg-sunken);
  color: var(--pyt-color-text-primary);
}

.agent-notebook__hint {
  margin: 0;
  padding: var(--pyt-space-3) 0;
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-sm);
  text-align: center;
}

.agent-notebook__list {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.agent-notebook__item {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-3);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-soft);
}

.agent-notebook__row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--pyt-space-2);
}

.agent-notebook__stars {
  flex: 0 0 auto;
  color: var(--pyt-color-gold);
  font-size: var(--pyt-font-size-sm);
  letter-spacing: 1px;
  white-space: nowrap;
}

.agent-notebook__summary {
  flex: 1 1 200px;
  min-width: 0;
  margin: 0;
  font-size: var(--pyt-font-size-base);
  line-height: 1.5;
  color: var(--pyt-color-text-primary);
}

.agent-notebook__time {
  flex: 0 0 auto;
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-xs);
  white-space: nowrap;
}

.agent-notebook__toggle {
  align-self: flex-start;
  padding: var(--pyt-space-1) var(--pyt-space-2);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-full);
  background: transparent;
  color: var(--pyt-color-text-hint);
  font-size: var(--pyt-font-size-xs);
  cursor: pointer;
}

.agent-notebook__toggle:hover {
  border-color: var(--pyt-color-border);
  color: var(--pyt-color-text-primary);
}

.agent-notebook__scores {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  padding-top: var(--pyt-space-1);
}

.agent-notebook__score {
  display: grid;
  grid-template-columns: 3.5em 1fr 3em;
  align-items: center;
  gap: var(--pyt-space-2);
  font-size: var(--pyt-font-size-xs);
}

.agent-notebook__score-label {
  color: var(--pyt-color-text-muted);
}

.agent-notebook__score-track {
  height: 8px;
  border-radius: var(--pyt-radius-full);
  background: var(--pyt-color-bg-sunken);
  overflow: hidden;
}

.agent-notebook__score-fill {
  display: block;
  height: 100%;
  border-radius: var(--pyt-radius-full);
  background: var(--pyt-color-info);
  transition: width var(--pyt-motion-slow) ease;
}

.agent-notebook__score--importance .agent-notebook__score-fill {
  background: var(--pyt-color-warning);
}

.agent-notebook__score--relevance .agent-notebook__score-fill {
  background: var(--pyt-color-accent);
}

.agent-notebook__score-value {
  color: var(--pyt-color-text-primary);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.agent-notebook__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--pyt-space-1);
  padding: var(--pyt-space-5) var(--pyt-space-4);
  border: 1px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-primary);
  text-align: center;
}

.agent-notebook__empty-glyph {
  font-size: var(--pyt-font-size-2xl);
  line-height: 1;
}

.agent-notebook__empty-title {
  margin: 0;
  font-size: var(--pyt-font-size-base);
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}

.agent-notebook__empty-hint {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
}

@media (prefers-reduced-motion: reduce) {
  .agent-notebook__score-fill {
    transition: none;
  }
}
</style>
