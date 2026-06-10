<script setup lang="ts">
// 关卡地图 7 章节点列表（Phaser 羊皮卷地图之外的可读 / 可达 UI 镜像）。
// 纯展示组件：章节数据与选中态由父级（MainlineMapPage）传入，点击只 emit select，
// 不在此做解锁 / 星级判定（后端权威）。节点三态颜色取 tokens.css 令牌。
import { chapterNodeState, chapterStageProgress } from '@stores/mainline';
import type { MainlineChapterDto } from '@api/mainline';
import StarBadge from './StarBadge.vue';

defineProps<{
  chapters: MainlineChapterDto[];
  selected: number | null;
}>();

const emit = defineEmits<{
  (e: 'select', chapter: number): void;
}>();

const STATE_LABEL: Record<ReturnType<typeof chapterNodeState>, string> = {
  locked: '未解锁',
  'in-progress': '进行中',
  cleared: '已通关',
};

function progressText(ch: MainlineChapterDto): string {
  const { done, total } = chapterStageProgress(ch);
  return `${done}/${total}`;
}
</script>

<template>
  <ul
    class="node-list"
    data-testid="mainline-node-list"
  >
    <li
      v-for="ch in chapters"
      :key="ch.chapter"
      class="node"
      :class="[
        `node--${chapterNodeState(ch)}`,
        { 'node--selected': ch.chapter === selected },
      ]"
      :data-testid="`mainline-node-${ch.chapter}`"
      :data-state="chapterNodeState(ch)"
      :aria-current="ch.chapter === selected ? 'true' : undefined"
    >
      <button
        type="button"
        class="node__btn"
        :data-testid="`mainline-node-btn-${ch.chapter}`"
        @click="emit('select', ch.chapter)"
      >
        <span class="node__index">第 {{ ch.chapter }} 章</span>
        <span class="node__title">{{ ch.title }}</span>
        <span class="node__meta">
          <StarBadge :stars="ch.stars" />
          <span class="node__state">
            <template v-if="chapterNodeState(ch) === 'locked'">🔒 </template>
            {{ STATE_LABEL[chapterNodeState(ch)] }}
          </span>
          <span class="node__stages">小节 {{ progressText(ch) }}</span>
        </span>
      </button>
    </li>
  </ul>
</template>

<style scoped>
.node-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.node__btn {
  width: 100%;
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-areas:
    'index title'
    'meta meta';
  gap: var(--pyt-space-1) var(--pyt-space-2);
  align-items: center;
  text-align: left;
  cursor: pointer;
  padding: var(--pyt-space-2) var(--pyt-space-3);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  box-shadow: var(--pyt-shadow-card);
  transition: transform var(--pyt-motion-fast), box-shadow var(--pyt-motion-fast);
}
.node__btn:hover {
  transform: translate(1px, 1px);
}
.node__index {
  grid-area: index;
  font-weight: 700;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
}
.node__title {
  grid-area: title;
  font-size: var(--pyt-font-size-base);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node__meta {
  grid-area: meta;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--pyt-space-2);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
/* 三态视觉：锁定灰 / 进行中金边 / 已通关绿边 */
.node--locked .node__btn {
  opacity: 0.6;
  background: var(--pyt-color-bg-sunken);
  border-color: var(--pyt-color-border-soft);
}
.node--in-progress .node__btn {
  border-color: var(--pyt-color-gold);
}
.node--cleared .node__btn {
  border-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
}
.node--selected .node__btn {
  border-color: var(--pyt-color-border-strong);
  box-shadow: var(--pyt-shadow-modal);
}
.node__state {
  font-weight: 700;
}
</style>
