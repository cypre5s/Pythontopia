<script setup lang="ts">
/**
 * StageLearnPage —— 主线小章节「学习页」（R-15）。
 *
 * 路由 `/mainline/stage/:stageId` 进入；一次 `getMainlineOverview()` 拉全量主线，
 * 按 stageId 在各章 stages 里定位当前 stage，再渲染三件套（看/学/练）子组件。
 * 三个子组件各自打点（video-progress / page-read / example-done）并向上 emit 最新
 * StageProgressDto，本页据此合并刷新顶部三件套进度与「全部过线」状态。
 *
 * 依赖：值函数 `getMainlineOverview` 来自 `@/api/mainline`（A3 / fe-map 独占）；
 * 类型用本地 `stageTypes`（与 A3 对齐，详见交付报告）。本页**不**触碰 api/mainline.ts、
 * 路由表、导航等共享文件。
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import PyButton from '@/components/ui/PyButton.vue';
import StageVideoWatcher from '@/components/mainline-stage/StageVideoWatcher.vue';
import StageCoursewareReader from '@/components/mainline-stage/StageCoursewareReader.vue';
import StageExamplePractice from '@/components/mainline-stage/StageExamplePractice.vue';
import { ApiClientError } from '@/api/http';
import { getStageDetail } from '@/api/mainline';
import type { StageDetailDto } from '@/api/mainline';
import type { StageTaskProgress } from '@/components/mainline-stage/stageTypes';

const route = useRoute();
const router = useRouter();

type LoadStatus = 'loading' | 'ready' | 'not-found' | 'error';

const status = ref<LoadStatus>('loading');
const stage = ref<StageDetailDto | null>(null);
const chapterNo = ref<number | null>(null);
const chapterTitle = ref<string>('');

const stageId = computed<number>(() => {
  const raw = route.params.stageId;
  const value = Array.isArray(raw) ? raw[0] : raw;
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
});

const requiredPages = computed<number>(() => {
  const s = stage.value;
  if (!s) return 0;
  return s.requiredPages > 0 ? s.requiredPages : s.coursewarePageIds.length;
});
const readCount = computed<number>(() => {
  const s = stage.value;
  if (!s) return 0;
  return s.coursewarePageIds.filter((id) => s.pagesReadIds.includes(id)).length;
});
const requiredExamples = computed<number>(() => {
  const s = stage.value;
  if (!s) return 0;
  return s.requiredExamples > 0 ? s.requiredExamples : s.exampleProblemIds.length;
});
const doneCount = computed<number>(() => {
  const s = stage.value;
  if (!s) return 0;
  return s.exampleProblemIds.filter((id) => s.examplesDoneIds.includes(id)).length;
});

const videoPassed = computed<boolean>(() =>
  stage.value ? stage.value.videoWatchedSec >= stage.value.requiredVideoSec : false,
);
const coursewarePassed = computed<boolean>(
  () => requiredPages.value > 0 && readCount.value >= requiredPages.value,
);
const practicePassed = computed<boolean>(
  () => requiredExamples.value > 0 && doneCount.value >= requiredExamples.value,
);
// 「全部过线」客户端推导：三件套均过线即视为本小节完成（与服务端 stage.completed 取并集，
// 服务端权威值在下次 overview 拉取时回流）。
const allPassed = computed<boolean>(
  () =>
    (!!stage.value && stage.value.completed) ||
    (videoPassed.value && coursewarePassed.value && practicePassed.value),
);

function mmss(sec: number): string {
  const s = Math.max(0, Math.floor(sec));
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m}:${String(r).padStart(2, '0')}`;
}

async function load(): Promise<void> {
  status.value = 'loading';
  try {
    const detail = await getStageDetail(stageId.value);
    stage.value = detail;
    chapterNo.value = detail.chapter;
    chapterTitle.value = detail.chapterTitle;
    status.value = 'ready';
  } catch (e) {
    status.value = e instanceof ApiClientError && e.status === 404 ? 'not-found' : 'error';
  }
}

/** 子组件三件套打点后回流（本端内部事件）：合并该维度最新进度，刷新顶部总览。 */
function onProgress(p: StageTaskProgress): void {
  const current = stage.value;
  if (!current || p.stageId !== current.id) return;
  stage.value = {
    ...current,
    videoWatchedSec: p.videoWatchedSec ?? current.videoWatchedSec,
    pagesReadIds: p.readPageIds ?? current.pagesReadIds,
    examplesDoneIds: p.doneProblemIds ?? current.examplesDoneIds,
  };
}

function backToMap(): void {
  void router.push({ path: '/mainline-map' });
}

onMounted(load);
</script>

<template>
  <AppLayout>
    <section
      class="stage-learn-page"
      data-testid="stage-learn-page"
    >
      <header class="stage-learn__header">
        <div class="stage-learn__heading">
          <p
            v-if="chapterNo"
            class="stage-learn__chapter"
          >
            第 {{ chapterNo }} 章 · {{ chapterTitle }}
          </p>
          <h1 class="stage-learn__title">
            {{ stage ? stage.title : '小节学习' }}
          </h1>
          <p
            v-if="stage"
            class="stage-learn__kc"
          >
            知识点：{{ stage.kcCodes.join(' · ') || '—' }}
          </p>
        </div>
        <PyButton
          test-id="stage-learn-back"
          variant="secondary"
          size="sm"
          @click="backToMap"
        >
          ← 返回冒险地图
        </PyButton>
      </header>

      <p
        v-if="status === 'loading'"
        class="stage-learn__state"
        data-testid="stage-learn-loading"
      >
        正在加载小节…
      </p>
      <p
        v-else-if="status === 'not-found'"
        class="stage-learn__state"
        data-testid="stage-learn-missing"
      >
        没找到该小节（stageId={{ stageId }}）。它可能尚未解锁或不存在。
      </p>
      <p
        v-else-if="status === 'error'"
        class="stage-learn__state"
        data-testid="stage-learn-error"
      >
        主线进度加载失败，请稍后
        <button
          type="button"
          class="stage-learn__retry"
          data-testid="stage-learn-retry"
          @click="load"
        >
          重试
        </button>。
      </p>

      <template v-else-if="stage">
        <!-- 顶部三件套总览 -->
        <div
          class="stage-learn__summary"
          data-testid="stage-learn-summary"
        >
          <div
            class="stage-learn__chip"
            :class="{ 'stage-learn__chip--done': videoPassed }"
          >
            <span class="stage-learn__chip-label">看</span>
            <span
              class="stage-learn__chip-val"
              data-testid="summary-video-val"
            >{{ mmss(stage.videoWatchedSec) }} / {{ mmss(stage.requiredVideoSec) }}</span>
            <span
              v-if="videoPassed"
              class="stage-learn__chip-tick"
              aria-hidden="true"
            >✓</span>
          </div>
          <div
            class="stage-learn__chip"
            :class="{ 'stage-learn__chip--done': coursewarePassed }"
          >
            <span class="stage-learn__chip-label">学</span>
            <span
              class="stage-learn__chip-val"
              data-testid="summary-courseware-val"
            >{{ readCount }} / {{ requiredPages }} 页</span>
            <span
              v-if="coursewarePassed"
              class="stage-learn__chip-tick"
              aria-hidden="true"
            >✓</span>
          </div>
          <div
            class="stage-learn__chip"
            :class="{ 'stage-learn__chip--done': practicePassed }"
          >
            <span class="stage-learn__chip-label">练</span>
            <span
              class="stage-learn__chip-val"
              data-testid="summary-practice-val"
            >{{ doneCount }} / {{ requiredExamples }} 题</span>
            <span
              v-if="practicePassed"
              class="stage-learn__chip-tick"
              aria-hidden="true"
            >✓</span>
          </div>
          <div
            v-if="allPassed"
            class="stage-learn__complete"
            data-testid="stage-learn-complete"
          >
            本小节已全部过线 🎉
          </div>
        </div>

        <div class="stage-learn__cards">
          <article class="stage-learn__card">
            <StageVideoWatcher
              :stage-id="stage.id"
              :video-keyword="stage.videoKeyword"
              :required-video-sec="stage.requiredVideoSec"
              :initial-watched-sec="stage.videoWatchedSec"
              @progress="onProgress"
            />
          </article>
          <article class="stage-learn__card">
            <StageCoursewareReader
              :stage-id="stage.id"
              :page-ids="stage.coursewarePageIds"
              :required-pages="stage.requiredPages"
              :read-page-ids="stage.pagesReadIds"
              @progress="onProgress"
            />
          </article>
          <article class="stage-learn__card">
            <StageExamplePractice
              :stage-id="stage.id"
              :problem-ids="stage.exampleProblemIds"
              :required-examples="stage.requiredExamples"
              :done-problem-ids="stage.examplesDoneIds"
              @progress="onProgress"
            />
          </article>
        </div>
      </template>
    </section>
  </AppLayout>
</template>

<style scoped>
.stage-learn-page {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-4);
  padding: var(--pyt-space-4);
  min-height: calc(100vh - 56px);
  background:
    linear-gradient(180deg, var(--pyt-color-bg-primary) 0%, var(--pyt-color-bg-sunken) 100%),
    repeating-linear-gradient(45deg, rgba(110, 79, 29, 0.04) 0 4px, transparent 4px 12px);
}
.stage-learn__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-3) var(--pyt-space-4);
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border-strong);
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong);
}
.stage-learn__chapter {
  margin: 0 0 var(--pyt-space-1);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.stage-learn__title {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: var(--pyt-font-size-2xl);
  color: var(--pyt-color-text-primary);
}
.stage-learn__kc {
  margin: var(--pyt-space-1) 0 0;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-hint);
}
.stage-learn__state {
  margin: 0;
  padding: var(--pyt-space-6);
  text-align: center;
  font-size: var(--pyt-font-size-base);
  color: var(--pyt-color-text-muted);
  background: var(--pyt-color-bg-elevated);
  border: 2px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}
.stage-learn__retry {
  font-family: var(--pyt-font-pixel), monospace;
  font-size: var(--pyt-font-size-sm);
  padding: 2px 10px;
  background: var(--pyt-color-warning-bg);
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
}
.stage-learn__summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-3);
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border-strong);
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong);
}
.stage-learn__chip {
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-1) var(--pyt-space-3);
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-primary);
}
.stage-learn__chip--done {
  background: var(--pyt-color-success-bg);
  border-color: var(--pyt-color-success);
}
.stage-learn__chip-label {
  font-family: var(--pyt-font-pixel), sans-serif;
  font-weight: 700;
}
.stage-learn__chip-val {
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-text-muted);
}
.stage-learn__chip-tick {
  color: var(--pyt-color-success);
  font-weight: 700;
}
.stage-learn__complete {
  margin-left: auto;
  font-size: var(--pyt-font-size-sm);
  font-weight: 700;
  color: var(--pyt-color-success);
}
.stage-learn__cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: var(--pyt-space-4);
  align-items: start;
}
.stage-learn__card {
  padding: var(--pyt-space-4);
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border-strong);
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong);
}
@media (max-width: 720px) {
  .stage-learn__header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
