<script setup lang="ts">
/**
 * StageCoursewareReader —— 主线小章节「学」：阅读 stage 的 courseware_page_ids。
 *
 * 复用图书馆阅读器：每页提供「阅读」外链（新标签打开 `/library?pageId=...`，由图书馆
 * 自行把 page_id 解析成 docId+pageNo），打开即触发 `postPageRead(stageId, pageId)` 打点；
 * 另给「标记已读」按钮兜底（在别处读完后回来补打点）。读满 required_pages（0=全部）即「学」过线。
 */
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { postPageRead } from '@/api/mainline';
import type { StageTaskProgress } from './stageTypes';

const props = defineProps<{
  stageId: number;
  pageIds: number[];
  requiredPages: number;
  readPageIds: number[];
}>();

const emit = defineEmits<{ (e: 'progress', p: StageTaskProgress): void }>();

const router = useRouter();
const read = ref<Set<number>>(new Set(props.readPageIds));
const pending = ref<Set<number>>(new Set());

const requiredCount = computed<number>(() =>
  props.requiredPages > 0 ? props.requiredPages : props.pageIds.length,
);
const readCount = computed<number>(() => props.pageIds.filter((id) => read.value.has(id)).length);
const done = computed<boolean>(() => requiredCount.value > 0 && readCount.value >= requiredCount.value);

function libraryHref(pageId: number): string {
  return router.resolve({ path: '/library', query: { pageId: String(pageId) } }).href;
}

function isRead(pageId: number): boolean {
  return read.value.has(pageId);
}

async function markRead(pageId: number): Promise<void> {
  if (read.value.has(pageId) || pending.value.has(pageId)) return;
  pending.value = new Set(pending.value).add(pageId);
  try {
    // 只 await 成功；用已知 pageId 乐观更新（不读返回体，规避 DTO 形状差异）。
    await postPageRead(props.stageId, pageId);
    read.value = new Set(read.value).add(pageId);
    emit('progress', { stageId: props.stageId, readPageIds: [...read.value] });
  } catch {
    // 打点失败静默：用户可再次「标记已读」重试（服务端幂等）。
  } finally {
    const next = new Set(pending.value);
    next.delete(pageId);
    pending.value = next;
  }
}

/** 打开图书馆阅读器（新标签）并记一次阅读打点。 */
function openAndMark(pageId: number): void {
  void markRead(pageId);
}
</script>

<template>
  <section
    class="stage-courseware"
    data-testid="stage-courseware"
  >
    <header class="stage-courseware__head">
      <h3 class="stage-courseware__title">
        <span class="stage-courseware__icon" aria-hidden="true">📖</span> 学 · 课件精读
      </h3>
      <span
        v-if="done"
        class="stage-courseware__passed"
        data-testid="stage-courseware-passed"
      >已过线 ✓</span>
      <span
        v-else
        class="stage-courseware__count"
        data-testid="stage-courseware-count"
      >{{ readCount }} / {{ requiredCount }} 页</span>
    </header>

    <p class="stage-courseware__hint">
      点开课件页在图书馆阅读器中精读（新标签打开），打开即记一次阅读；读满
      {{ requiredCount }} 页过线。
    </p>

    <ul
      v-if="pageIds.length > 0"
      class="stage-courseware__list"
    >
      <li
        v-for="(pageId, idx) in pageIds"
        :key="pageId"
        class="stage-courseware__item"
        :class="{ 'stage-courseware__item--read': isRead(pageId) }"
      >
        <span class="stage-courseware__page">
          <span class="stage-courseware__check" aria-hidden="true">{{ isRead(pageId) ? '✓' : '○' }}</span>
          课件页 {{ idx + 1 }}（#{{ pageId }}）
        </span>
        <span class="stage-courseware__actions">
          <a
            class="stage-courseware__open"
            :data-testid="`cw-open-${pageId}`"
            :href="libraryHref(pageId)"
            target="_blank"
            rel="noopener noreferrer"
            @click="openAndMark(pageId)"
          >阅读 →</a>
          <button
            type="button"
            class="stage-courseware__mark"
            :data-testid="`cw-mark-${pageId}`"
            :disabled="isRead(pageId)"
            @click="markRead(pageId)"
          >标记已读</button>
        </span>
      </li>
    </ul>
    <p
      v-else
      class="stage-courseware__empty"
    >
      本小节暂未关联课件页。
    </p>
  </section>
</template>

<style scoped>
.stage-courseware {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.stage-courseware__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}
.stage-courseware__title {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: var(--pyt-font-size-lg);
  color: var(--pyt-color-text-primary);
}
.stage-courseware__icon {
  color: var(--pyt-color-info);
}
.stage-courseware__passed {
  font-size: var(--pyt-font-size-xs);
  padding: 2px 8px;
  background: var(--pyt-color-success-bg);
  color: var(--pyt-color-success);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  white-space: nowrap;
}
.stage-courseware__count {
  font-size: var(--pyt-font-size-xs);
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-text-muted);
}
.stage-courseware__hint {
  margin: 0;
  font-size: var(--pyt-font-size-xs);
  line-height: 1.5;
  color: var(--pyt-color-text-muted);
}
.stage-courseware__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--pyt-space-1);
}
.stage-courseware__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-2);
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
}
.stage-courseware__item--read {
  background: var(--pyt-color-success-bg);
  border-color: var(--pyt-color-success);
}
.stage-courseware__page {
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-space-2);
  min-width: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-primary);
}
.stage-courseware__check {
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-success);
}
.stage-courseware__actions {
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-space-2);
  flex: 0 0 auto;
}
.stage-courseware__open {
  font-size: var(--pyt-font-size-xs);
  text-decoration: underline;
  color: var(--pyt-color-info);
}
.stage-courseware__mark {
  font-family: var(--pyt-font-pixel), monospace;
  font-size: var(--pyt-font-size-xs);
  padding: 2px 10px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
  box-shadow: 0 2px 0 var(--pyt-color-border);
}
.stage-courseware__mark:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}
.stage-courseware__mark:not(:disabled):active {
  transform: translateY(2px);
  box-shadow: none;
}
.stage-courseware__empty {
  margin: 0;
  padding: var(--pyt-space-3);
  text-align: center;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
  border: 1px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}
</style>
