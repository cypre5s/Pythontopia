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
  /** Twin 深链的 KC；匹配后优先标出其课程配置所属小节。 */
  recommendedKcCode?: string | null;
  /** 错题复盘补星申领进行中：禁用按钮，避免连点连弹「补星 +1 ★」与「尚未满足补星条件」两条互斥提示。 */
  claimingErrorbook?: boolean;
}>();

const emit = defineEmits<{
  (e: 'enter-stage', stageId: number): void;
  (e: 'open-test', chapter: number): void;
  (e: 'open-endless', chapter: number): void;
  (e: 'claim-errorbook', chapter: number): void;
  (e: 'open-boss', chapter: number): void;
}>();

const nodeState = computed(() => chapterNodeState(props.chapter));
const unlocked = computed<boolean>(() => props.chapter.unlocked);
const progress = computed(() => chapterStageProgress(props.chapter));
const allStagesComplete = computed<boolean>(
  () => progress.value.total > 0 && progress.value.done === progress.value.total,
);
const isFullStar = computed<boolean>(() => props.chapter.stars >= 3);
// 章末 BOSS 入口：本章已解锁且已通过章末大测试（≥1★）。与 router 的 /boss/:chapter 守卫同口径。
const bossUnlocked = computed<boolean>(() => unlocked.value && props.chapter.stars >= 1);
// 断点续学焦点：第一个未完成小节。它的入口升格为「继续学习」主 CTA 并高亮，
// 学生打开抽屉即知从哪继续，不用逐行扫进度条。
const resumeStageId = computed<number | null>(() =>
  unlocked.value
    ? (props.chapter.stages.find((s) => !isStageComplete(s))?.id ?? null)
    : null,
);
const recommendedStageId = computed<number | null>(() => {
  const kcCode = props.recommendedKcCode;
  if (!unlocked.value || !kcCode) return null;
  return props.chapter.stages.find((stage) => stage.kcCodes?.includes(kcCode))?.id ?? null;
});

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

/**
 * 单件进度比例 0..1。
 * required ≤ 0 = 本节该维度无要求：**不画满格**。旧写法 return 1 会把「0/0」渲染成
 * 100% 满绿条（R-14 起 requiredExamples 常为 0），学生看到三条全绿却拿不到 ✓。
 */
function ratio(value: number, required: number): number {
  if (required <= 0) return 0;
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
  /** 本维度无要求（required ≤ 0）：文本显示「—」，进度条走虚线空态而非满格。 */
  na: boolean;
}

function piece(
  key: StagePiece['key'],
  label: string,
  value: number,
  required: number,
  text: string,
): StagePiece {
  const na = required <= 0;
  return { key, label, text: na ? '—' : text, pct: percent(value, required), na };
}

function pieces(stage: MainlineStageDto): StagePiece[] {
  return [
    piece('video', '看', stage.videoWatchedSec, stage.requiredVideoSec, `${stage.videoWatchedSec}/${stage.requiredVideoSec}s`),
    piece('pages', '学', stage.pagesRead, stage.requiredPages, `${stage.pagesRead}/${stage.requiredPages}`),
    piece('examples', '练', stage.examplesDone, stage.requiredExamples, `${stage.examplesDone}/${stage.requiredExamples}`),
  ];
}

/**
 * overview DTO 里的三件套计数是否都已达标。
 * 服务端的 stage.completed 还包含 overview **没有**的「本节客观题全对」维度，
 * 所以「三条全满 + 无 ✓」是正常的——此时给出显式线索，别让学生对着满格进度条猜。
 */
function metersFull(stage: MainlineStageDto): boolean {
  return (
    stage.videoWatchedSec >= stage.requiredVideoSec
    && stage.pagesRead >= stage.requiredPages
    && stage.examplesDone >= stage.requiredExamples
  );
}
function needsQuestionHint(stage: MainlineStageDto): boolean {
  return !isStageComplete(stage) && metersFull(stage);
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
        :class="{
          'stage--done': isStageComplete(stage),
          'stage--resume': stage.id === resumeStageId,
          'stage--recommended': stage.id === recommendedStageId,
        }"
        :data-testid="`mainline-stage-${stage.id}`"
        :data-done="isStageComplete(stage) ? 'true' : 'false'"
        :data-resume="stage.id === resumeStageId ? 'true' : 'false'"
        :data-recommended="stage.id === recommendedStageId ? 'true' : 'false'"
      >
        <div class="stage__head">
          <span class="stage__index">{{ stage.index }}</span>
          <span class="stage__title">{{ stage.title }}</span>
          <span
            v-if="stage.id === recommendedStageId"
            class="stage__recommended"
          >学习分身推荐</span>
          <span
            v-if="stage.id === resumeStageId"
            class="stage__now"
            aria-label="当前进度"
          >当前</span>
          <span
            v-if="isStageComplete(stage)"
            class="stage__check"
            aria-label="已完成"
          >✓</span>
        </div>
        <div class="stage__pieces">
          <div
            v-for="p in pieces(stage)"
            :key="p.key"
            class="piece"
            :class="{ 'piece--na': p.na }"
            :data-testid="`mainline-stage-${stage.id}-${p.key}`"
            :data-na="p.na ? 'true' : 'false'"
          >
            <span class="piece__label">{{ p.label }}</span>
            <span class="piece__bar">
              <span
                class="piece__fill"
                :style="{ width: p.pct }"
              />
            </span>
            <span class="piece__text">{{ p.na ? '本节无要求' : p.text }}</span>
          </div>
        </div>
        <p
          v-if="needsQuestionHint(stage)"
          class="stage__gap"
          :data-testid="`mainline-stage-${stage.id}-question-gap`"
        >
          看 / 学 / 练 已达标，还差本节客观题全部答对——进入学习页的「练」里把选择 / 填空 / 判断做对即可。
        </p>
        <button
          type="button"
          class="stage__enter"
          :class="{
            'stage__enter--resume': stage.id === resumeStageId,
            'stage__enter--recommended': stage.id === recommendedStageId,
          }"
          :data-testid="`mainline-enter-stage-${stage.id}`"
          :disabled="!unlocked"
          @click="emit('enter-stage', stage.id)"
        >
          {{ stage.id === recommendedStageId ? '▶ 开始推荐任务' : stage.id === resumeStageId ? '▶ 继续学习' : '进入学习' }}
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
        :disabled="!allStagesComplete"
        :title="allStagesComplete
          ? '本章全部小节已完成，可参加章末大测试'
          : '每个小节需：看/学/练三件套达标 + 本节客观题全部答对'"
        @click="allStagesComplete && emit('open-test', chapter.chapter)"
      >
        {{ allStagesComplete ? '参加大测试' : '完成全部小节后参加大测试' }}
        <span
          v-if="!allStagesComplete"
          class="action__hint"
          data-testid="mainline-open-test-hint"
        >（每节需看、学、练达标 + 本节客观题全对）</span>
      </button>

      <!-- 章末 BOSS：通过本章大测试（≥1★）后开放的可选挑战；未达标时不渲染入口，
           与 /boss/:chapter 的路由守卫同口径，杜绝「手输 URL 直接开打」。 -->
      <button
        v-if="bossUnlocked"
        type="button"
        class="action action--boss"
        data-testid="mainline-open-boss"
        @click="emit('open-boss', chapter.chapter)"
      >
        ⚔ 挑战第 {{ chapter.chapter }} 章 BOSS
        <span class="action__hint">（4 阶段限时作答，可选挑战）</span>
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
          :disabled="claimingErrorbook"
          @click="emit('claim-errorbook', chapter.chapter)"
        >
          {{ claimingErrorbook ? '申领中…' : '错题复盘补星' }}
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
.stage--resume {
  border-color: var(--pyt-color-accent, #e8a33d);
  box-shadow: 0 0 0 1px var(--pyt-color-accent, #e8a33d);
}
.stage--recommended {
  border-color: var(--pyt-color-gold);
  box-shadow: 0 0 0 1px var(--pyt-color-gold);
}
.stage__now {
  padding: 0 6px;
  background: var(--pyt-color-accent, #e8a33d);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-bg-primary, #241a10);
  font-size: 11px;
  line-height: 18px;
}
.stage__recommended {
  padding: 0 6px;
  background: var(--pyt-color-gold);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  font-size: 11px;
  line-height: 18px;
}
.stage__enter--resume {
  border-color: var(--pyt-color-accent, #e8a33d);
  font-weight: 700;
}
.stage__enter--recommended {
  border-color: var(--pyt-color-gold);
  background: var(--pyt-color-gold);
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
/* 本节该维度无要求：空槽 + 虚线边，一眼区别于「满格已完成」。 */
.piece--na .piece__bar {
  border-style: dashed;
  background: transparent;
}
.piece--na .piece__text {
  color: var(--pyt-color-text-hint);
}
.stage__gap {
  margin: 0 0 var(--pyt-space-2);
  padding: var(--pyt-space-1) var(--pyt-space-2);
  font-size: var(--pyt-font-size-xs);
  line-height: 1.5;
  color: var(--pyt-color-text-muted);
  background: var(--pyt-color-warning-bg);
  border-left: 3px solid var(--pyt-color-gold);
  border-radius: var(--pyt-radius-sm);
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
.action--boss {
  background: var(--pyt-color-danger-bg, var(--pyt-color-warning-bg));
  border-color: var(--pyt-color-danger);
  color: var(--pyt-color-danger);
}
.action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
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
