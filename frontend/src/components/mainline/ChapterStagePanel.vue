<script setup lang="ts">
// 选中章节的详情面板：小节（stage）三件套进度 + 「进入学习 / 参加大测试 / 去补星」入口。
// 纯展示 + emit：所有动作只向上 emit，由 MainlineMapPage 决定路由跳转 / 调 store 补星。
// 解锁 / 星级 / 补星资格全部后端权威，本组件仅按 overview 字段渲染。
import { computed } from 'vue';
import {
  chapterNodeState,
  chapterStageProgress,
  isStageComplete,
} from '@stores/mainline';
import type { ChapterTestStatus, MainlineChapterDto, MainlineStageDto } from '@api/mainline';
import StarBadge from './StarBadge.vue';

const props = defineProps<{
  chapter: MainlineChapterDto;
}>();

const emit = defineEmits<{
  (e: 'enter-stage', stageId: number): void;
  (e: 'open-test', chapter: number): void;
  (e: 'open-endless', chapter: number): void;
  (e: 'claim-errorbook', chapter: number): void;
}>();

const nodeState = computed(() => chapterNodeState(props.chapter));
const unlocked = computed<boolean>(() => props.chapter.unlocked);
const progress = computed(() => chapterStageProgress(props.chapter));
const allStagesComplete = computed<boolean>(
  () => progress.value.total > 0 && progress.value.done === progress.value.total,
);
const isFullStar = computed<boolean>(() => props.chapter.stars >= 3);

const TEST_STATUS_LABEL: Record<ChapterTestStatus, string> = {
  NONE: '未开始',
  PENDING: '排队预生成',
  GENERATING: '试卷生成中',
  READY: '可应考',
  IN_PROGRESS: '进行中',
  SUBMITTED: '已提交',
  EXPIRED: '已过期',
  FAILED: '生成失败',
};

/** 单件进度比例 0..1：required ≤ 0 视为已满（无要求）。 */
function ratio(value: number, required: number): number {
  if (required <= 0) return 1;
  return Math.max(0, Math.min(1, value / required));
}
function percent(value: number, required: number): string {
  return `${Math.round(ratio(value, required) * 100)}%`;
}

interface StagePiece {
  key: 'video' | 'pages' | 'examples';
  label: string;
  text: string;
  pct: string;
}

function pieces(stage: MainlineStageDto): StagePiece[] {
  return [
    {
      key: 'video',
      label: '看',
      text: `${stage.videoWatchedSec}/${stage.requiredVideoSec}s`,
      pct: percent(stage.videoWatchedSec, stage.requiredVideoSec),
    },
    {
      key: 'pages',
      label: '学',
      text: `${stage.pagesRead}/${stage.requiredPages}`,
      pct: percent(stage.pagesRead, stage.requiredPages),
    },
    {
      key: 'examples',
      label: '练',
      text: `${stage.examplesDone}/${stage.requiredExamples}`,
      pct: percent(stage.examplesDone, stage.requiredExamples),
    },
  ];
}
</script>

<template>
  <section
    class="stage-panel"
    data-testid="mainline-stage-panel"
    :data-state="nodeState"
  >
    <header class="stage-panel__head">
      <h2
        class="stage-panel__title"
        data-testid="mainline-chapter-title"
      >
        第 {{ chapter.chapter }} 章 · {{ chapter.title }}
      </h2>
      <StarBadge :stars="chapter.stars" />
    </header>

    <p class="stage-panel__meta">
      <span data-testid="mainline-chapter-stage-progress">小节进度 {{ progress.done }}/{{ progress.total }}</span>
      <span
        class="stage-panel__test-status"
        data-testid="mainline-test-status"
      >大测试：{{ TEST_STATUS_LABEL[chapter.testStatus] }}</span>
      <span
        v-if="chapter.bestTestScore !== null"
        data-testid="mainline-best-score"
      >最高分 {{ chapter.bestTestScore }}</span>
    </p>

    <p
      v-if="!unlocked"
      class="stage-panel__locked"
      data-testid="mainline-locked-notice"
    >
      🔒 本章尚未解锁——先完成前一章的章末大测试（≥60 分）即可解锁。
    </p>

    <ol class="stage-list">
      <li
        v-for="stage in chapter.stages"
        :key="stage.id"
        class="stage"
        :class="{ 'stage--done': isStageComplete(stage) }"
        :data-testid="`mainline-stage-${stage.id}`"
        :data-done="isStageComplete(stage) ? 'true' : 'false'"
      >
        <div class="stage__head">
          <span class="stage__index">{{ stage.index }}</span>
          <span class="stage__title">{{ stage.title }}</span>
          <span
            v-if="isStageComplete(stage)"
            class="stage__check"
            aria-label="已完成"
          >✓</span>
        </div>
        <div class="stage__pieces">
          <div
            v-for="piece in pieces(stage)"
            :key="piece.key"
            class="piece"
            :data-testid="`mainline-stage-${stage.id}-${piece.key}`"
          >
            <span class="piece__label">{{ piece.label }}</span>
            <span class="piece__bar">
              <span
                class="piece__fill"
                :style="{ width: piece.pct }"
              />
            </span>
            <span class="piece__text">{{ piece.text }}</span>
          </div>
        </div>
        <button
          type="button"
          class="stage__enter"
          :data-testid="`mainline-enter-stage-${stage.id}`"
          :disabled="!unlocked"
          @click="emit('enter-stage', stage.id)"
        >
          进入学习
        </button>
      </li>
    </ol>

    <footer
      v-if="unlocked"
      class="stage-panel__actions"
    >
      <button
        type="button"
        class="action action--test"
        data-testid="mainline-open-test"
        @click="emit('open-test', chapter.chapter)"
      >
        参加大测试
        <span
          v-if="!allStagesComplete"
          class="action__hint"
        >（完成全部小节解锁 2★+）</span>
      </button>

      <div
        v-if="!isFullStar"
        class="recovery"
        data-testid="mainline-recovery"
      >
        <span class="recovery__label">去补星：</span>
        <button
          v-if="!chapter.recovery.errorbookDone"
          type="button"
          class="action action--recovery"
          data-testid="mainline-claim-errorbook"
          @click="emit('claim-errorbook', chapter.chapter)"
        >
          错题复盘补星
        </button>
        <span
          v-else
          class="recovery__done"
          data-testid="mainline-errorbook-done"
        >错题复盘 ✓已补星</span>

        <button
          v-if="!chapter.recovery.endlessDone"
          type="button"
          class="action action--recovery"
          data-testid="mainline-open-endless"
          @click="emit('open-endless', chapter.chapter)"
        >
          无尽挑战补星
        </button>
        <span
          v-else
          class="recovery__done"
          data-testid="mainline-endless-done"
        >无尽挑战 ✓已补星</span>
      </div>
      <p
        v-else
        class="recovery__full"
        data-testid="mainline-full-star"
      >
        ★★★ 本章已满星
      </p>
    </footer>
  </section>
</template>

<style scoped>
.stage-panel {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-4);
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
  box-shadow: var(--pyt-shadow-card);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}
.stage-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-3);
}
.stage-panel__title {
  margin: 0;
  font-size: var(--pyt-font-size-lg);
}
.stage-panel__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--pyt-space-3);
  margin: 0;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.stage-panel__test-status {
  color: var(--pyt-color-info);
}
.stage-panel__locked {
  margin: 0;
  padding: var(--pyt-space-2) var(--pyt-space-3);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-sunken);
  border: 2px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}
.stage-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.stage {
  padding: var(--pyt-space-2) var(--pyt-space-3);
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}
.stage--done {
  border-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
}
.stage__head {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  margin-bottom: var(--pyt-space-2);
}
.stage__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
  background: var(--pyt-color-gold-bright);
  color: var(--pyt-color-text-primary);
  border-radius: var(--pyt-radius-full);
}
.stage__title {
  flex: 1;
  font-size: var(--pyt-font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage__check {
  color: var(--pyt-color-success);
  font-weight: 700;
}
.stage__pieces {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-1);
  margin-bottom: var(--pyt-space-2);
}
.piece {
  display: grid;
  grid-template-columns: 24px 1fr auto;
  align-items: center;
  gap: var(--pyt-space-2);
  font-size: var(--pyt-font-size-xs);
}
.piece__label {
  color: var(--pyt-color-text-hint);
  text-align: center;
}
.piece__bar {
  position: relative;
  height: 8px;
  background: var(--pyt-color-bg-sunken);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  overflow: hidden;
}
.piece__fill {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--pyt-color-success);
}
.piece__text {
  color: var(--pyt-color-text-muted);
  font-variant-numeric: tabular-nums;
}
.stage__enter,
.action {
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: var(--pyt-radius-md);
  box-shadow: var(--pyt-shadow-card);
  padding: var(--pyt-space-1) var(--pyt-space-3);
  cursor: pointer;
}
.stage__enter:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}
.stage__enter:not(:disabled):active,
.action:active {
  transform: translate(1px, 1px);
  box-shadow: none;
}
.stage-panel__actions {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.action--test {
  background: var(--pyt-color-gold);
}
.action__hint {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-hint);
}
.recovery {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--pyt-space-2);
  font-size: var(--pyt-font-size-sm);
}
.recovery__label {
  color: var(--pyt-color-text-hint);
}
.action--recovery {
  background: var(--pyt-color-accent-bg);
  border-color: var(--pyt-color-accent);
  color: var(--pyt-color-accent);
}
.recovery__done {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-success);
}
.recovery__full {
  margin: 0;
  color: var(--pyt-color-gold-bright);
  text-shadow: 1px 1px 0 var(--pyt-color-shadow);
}
</style>
