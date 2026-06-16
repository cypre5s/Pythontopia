<script setup lang="ts">
/**
 * StageLearnPage —— 主线小章节「学习页」（R-15，学习通式布局）。
 *
 * 路由 `/mainline/stage/:stageId` 进入；`getStageDetail(stageId)` 拉详情，渲染三件套
 * （看/学/练）。布局参考学习通：**左侧大区**展示当前选中的单一资源，**右侧可收纳目录**
 * 按看/学/练三组列出，组旁显示进度与 ✓ 过线标记，点击切换左侧展示。去掉冗余表头，
 * 仅保留一个「返回冒险地图」按钮。
 *
 * 三个子组件各自打点（video-progress / page-read / example-done）并向上 emit 最新进度，
 * 本页据此合并刷新目录里的进度与「全部过线」状态。
 *
 * 依赖：`getStageDetail` 来自 `@/api/mainline`；事件类型用本地 `stageTypes`。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import PyButton from '@/components/ui/PyButton.vue';
import StageVideoWatcher from '@/components/mainline-stage/StageVideoWatcher.vue';
import StageCoursewareReader from '@/components/mainline-stage/StageCoursewareReader.vue';
import StageExamplePractice from '@/components/mainline-stage/StageExamplePractice.vue';
import { ApiClientError } from '@/api/http';
import { prepareTest } from '@/api/chapterTest';
import { getStageDetail } from '@/api/mainline';
import type { StageDetailDto } from '@/api/mainline';
import type { StageTaskProgress } from '@/components/mainline-stage/stageTypes';

const route = useRoute();
const router = useRouter();

type LoadStatus = 'loading' | 'ready' | 'not-found' | 'error';
/** 左侧大区当前展示的资源类型（看/学/练）。 */
type StageTab = 'video' | 'courseware' | 'practice';

const status = ref<LoadStatus>('loading');
const stage = ref<StageDetailDto | null>(null);
const chapterNo = ref<number | null>(null);
const chapterTitle = ref<string>('');
const FINAL_STAGE_INDEX: Readonly<Record<number, number>> = {
  1: 3,
  2: 3,
  3: 4,
  4: 3,
  5: 3,
  6: 3,
  7: 5,
};

/** 当前左侧展示的资源页签（默认「看」）。 */
const activeTab = ref<StageTab>('video');
/** 右侧目录在窄屏下可收纳；宽屏常驻。 */
const tocOpen = ref<boolean>(true);
const finalTestPrepareRequested = ref<boolean>(false);

function selectTab(tab: StageTab): void {
  activeTab.value = tab;
}
function toggleToc(): void {
  tocOpen.value = !tocOpen.value;
}

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
// 练-OJ：R-14 起 requiredExamples = 0/1（无 OJ 则 0，有则需完成 1 道），不再回退到题数。
const requiredExamples = computed<number>(() => stage.value?.requiredExamples ?? 0);
const doneCount = computed<number>(() => {
  const s = stage.value;
  if (!s) return 0;
  return s.exampleProblemIds.filter((id) => s.examplesDoneIds.includes(id)).length;
});
// 练-客观题：需答对 requiredQuestions 道（mix4=4）。
const requiredQuestions = computed<number>(() => stage.value?.requiredQuestions ?? 0);
const correctCount = computed<number>(() => {
  const s = stage.value;
  if (!s) return 0;
  const correct = new Set(s.questionsCorrectIds ?? []);
  const qs = s.questions ?? [];
  return qs.length > 0 ? qs.filter((q) => correct.has(q.id)).length : correct.size;
});

const videoPassed = computed<boolean>(() =>
  stage.value ? stage.value.videoWatchedSec >= stage.value.requiredVideoSec : false,
);
const coursewarePassed = computed<boolean>(
  () => requiredPages.value > 0 && readCount.value >= requiredPages.value,
);
// 练过线 = 客观题全对（若有要求）且 OJ 完成（若有要求）；两者皆无要求时视为已过。
const questionsPassed = computed<boolean>(
  () => requiredQuestions.value === 0 || correctCount.value >= requiredQuestions.value,
);
const examplesPassed = computed<boolean>(
  () => requiredExamples.value === 0 || doneCount.value >= requiredExamples.value,
);
const practicePassed = computed<boolean>(() => questionsPassed.value && examplesPassed.value);
const isChapterFinalStage = computed<boolean>(() => {
  const s = stage.value;
  return !!s && FINAL_STAGE_INDEX[s.chapter] === s.index;
});
const quizUnlocked = computed<boolean>(
  () => isChapterFinalStage.value && videoPassed.value && coursewarePassed.value,
);
// 「全部过线」客户端推导：三件套均过线即视为本小节完成（与服务端 stage.completed 取并集，
// 服务端权威值在下次 overview 拉取时回流）。
const allPassed = computed<boolean>(
  () =>
    (!!stage.value && stage.value.completed) ||
    (videoPassed.value && coursewarePassed.value && practicePassed.value),
);

/** 练习进度文本（客观题 + OJ 合并展示）。 */
const practiceProgressText = computed<string>(() => {
  const parts: string[] = [];
  if (requiredQuestions.value > 0) parts.push(`${correctCount.value}/${requiredQuestions.value} 题`);
  if (requiredExamples.value > 0) parts.push(`${doneCount.value}/${requiredExamples.value} OJ`);
  return parts.length > 0 ? parts.join(' · ') : '无需作答';
});

interface TocEntry {
  tab: StageTab;
  icon: string;
  label: string;
  progress: string;
  passed: boolean;
}

/** 右侧目录三组（看/学/练）：图标 + 名称 + 进度 + 过线标记，驱动左侧切换。 */
const tocEntries = computed<TocEntry[]>(() => {
  if (!stage.value) return [];
  return [
    {
      tab: 'video',
      icon: '▶',
      label: '看 · 视频讲解',
      progress: `${mmss(stage.value.videoWatchedSec)} / ${mmss(stage.value.requiredVideoSec)}`,
      passed: videoPassed.value,
    },
    {
      tab: 'courseware',
      icon: '📖',
      label: '学 · 课件阅读',
      progress: `${readCount.value} / ${requiredPages.value} 页`,
      passed: coursewarePassed.value,
    },
    {
      tab: 'practice',
      icon: '✎',
      label: '练 · 题目练习',
      progress: practiceProgressText.value,
      passed: practicePassed.value,
    },
  ];
});

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
    questionsCorrectIds: p.correctQuestionIds ?? current.questionsCorrectIds,
  };
}

async function prepareChapterFinalTest(): Promise<void> {
  const chapter = stage.value?.chapter;
  if (!chapter || finalTestPrepareRequested.value) return;
  finalTestPrepareRequested.value = true;
  try {
    await prepareTest(chapter, 'test');
  } catch {
    // 后台预生成是提速手段，不打断学生继续看/学/练；真正开考时后端仍会返回权威状态。
  }
}

function backToMap(): void {
  void router.push({ path: '/mainline-map' });
}

function openChapterQuiz(): void {
  const chapter = stage.value?.chapter;
  if (!chapter || !quizUnlocked.value) return;
  void router.push({ path: `/chapter-quiz/${chapter}` });
}

watch(quizUnlocked, (ready) => {
  if (ready) {
    void prepareChapterFinalTest();
  }
});

onMounted(load);
</script>

<template>
  <AppLayout>
    <section
      class="stage-learn-page"
      data-testid="stage-learn-page"
    >
      <!-- 极简顶栏：返回按钮；目录收纳成图标栏时在此补充章节上下文。 -->
      <div class="stage-learn__topbar">
        <PyButton
          test-id="stage-learn-back"
          variant="secondary"
          size="sm"
          @click="backToMap"
        >
          ← 返回冒险地图
        </PyButton>
        <span
          v-if="status === 'ready' && stage && !tocOpen"
          class="stage-learn__topbar-title"
        >第 {{ chapterNo }} 章 · {{ stage.title }}</span>
      </div>

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

      <div
        v-else-if="stage"
        class="stage-learn__body"
        :class="{ 'stage-learn__body--toc-collapsed': !tocOpen }"
      >
        <!-- 左侧大区：当前选中的单一资源 -->
        <main class="stage-learn__main">
          <!-- 「看」始终挂载（保持播放计时连续），用 v-show 切显隐 -->
          <article
            v-show="activeTab === 'video'"
            class="stage-learn__panel"
          >
            <StageVideoWatcher
              :stage-id="stage.id"
              :videos="stage.videos ?? []"
              :required-video-sec="stage.requiredVideoSec"
              :initial-watched-sec="stage.videoWatchedSec"
              :kc-codes="stage.kcCodes"
              @progress="onProgress"
            />
          </article>
          <article
            v-show="activeTab === 'courseware'"
            class="stage-learn__panel"
          >
            <StageCoursewareReader
              :stage-id="stage.id"
              :chapter="stage.chapter"
              :doc-id="185 + stage.chapter"
              :page-ids="stage.coursewarePageIds"
              :required-pages="stage.requiredPages"
              :read-page-ids="stage.pagesReadIds"
              :kc-codes="stage.kcCodes"
              :active="activeTab === 'courseware'"
              @progress="onProgress"
            />
          </article>
          <article
            v-show="activeTab === 'practice'"
            class="stage-learn__panel stage-learn__panel--scroll"
          >
            <section
              v-if="isChapterFinalStage"
              class="stage-learn__quiz-card"
              data-testid="stage-chapter-quiz-card"
            >
              <div>
                <strong>本章视频与 PPT 学完后小测</strong>
                <p>10 道选择 + 10 道填空 + 10 道判断 + 3 道编程，整卷提交。</p>
              </div>
              <button
                type="button"
                class="stage-learn__quiz-button"
                data-testid="stage-chapter-quiz"
                :disabled="!quizUnlocked"
                @click="openChapterQuiz"
              >
                {{ quizUnlocked ? '进入章节小测' : '完成视频与 PPT 后解锁' }}
              </button>
            </section>
            <StageExamplePractice
              :stage-id="stage.id"
              :questions="stage.questions ?? []"
              :required-questions="stage.requiredQuestions ?? 0"
              :correct-question-ids="stage.questionsCorrectIds ?? []"
              :examples="stage.examples ?? []"
              :required-examples="stage.requiredExamples"
              :done-problem-ids="stage.examplesDoneIds"
              @progress="onProgress"
            />
          </article>
        </main>

        <!-- 右侧目录：展开 = 看/学/练 + 进度；收纳 = 窄图标栏（省出空间给主区/问答） -->
        <aside
          class="stage-learn__toc"
          :class="{ 'stage-learn__toc--rail': !tocOpen }"
          data-testid="stage-learn-toc"
          aria-label="本节目录"
        >
          <div
            v-show="tocOpen"
            class="stage-learn__toc-full"
          >
            <header class="stage-learn__toc-head">
              <div class="stage-learn__toc-meta">
                <span class="stage-learn__toc-chapter">第 {{ chapterNo }} 章</span>
                <button
                  type="button"
                  class="stage-learn__toc-toggle"
                  title="收起目录"
                  aria-label="收起目录"
                  :aria-expanded="tocOpen"
                  data-testid="stage-learn-toc-toggle"
                  @click="toggleToc"
                >
                  »
                </button>
              </div>
              <h2 class="stage-learn__toc-title">
                {{ stage.title }}
              </h2>
              <p class="stage-learn__toc-kc">
                {{ stage.kcCodes.join(' · ') || '—' }}
              </p>
            </header>
            <ul class="stage-learn__toc-list">
              <li
                v-for="entry in tocEntries"
                :key="entry.tab"
              >
                <button
                  type="button"
                  class="stage-learn__toc-item"
                  :class="{
                    'stage-learn__toc-item--active': activeTab === entry.tab,
                    'stage-learn__toc-item--done': entry.passed,
                  }"
                  :data-testid="`stage-toc-${entry.tab}`"
                  :aria-current="activeTab === entry.tab ? 'true' : undefined"
                  @click="selectTab(entry.tab)"
                >
                  <span
                    class="stage-learn__toc-icon"
                    aria-hidden="true"
                  >{{ entry.icon }}</span>
                  <span class="stage-learn__toc-body">
                    <span class="stage-learn__toc-label">{{ entry.label }}</span>
                    <span
                      class="stage-learn__toc-progress"
                      :data-testid="entry.tab === 'video'
                        ? 'summary-video-val'
                        : entry.tab === 'courseware'
                          ? 'summary-courseware-val'
                          : 'summary-practice-val'"
                    >{{ entry.progress }}</span>
                  </span>
                  <span
                    v-if="entry.passed"
                    class="stage-learn__toc-tick"
                    aria-hidden="true"
                  >✓</span>
                </button>
              </li>
            </ul>
            <div
              v-if="allPassed"
              class="stage-learn__complete"
              data-testid="stage-learn-complete"
            >
              本小节已全部过线 🎉
            </div>
          </div>

          <!-- 收纳态：窄图标栏，仍可切换看/学/练并显示 ✓ -->
          <div
            v-if="!tocOpen"
            class="stage-learn__toc-rail"
          >
            <button
              type="button"
              class="stage-learn__rail-btn stage-learn__rail-btn--expand"
              title="展开目录"
              aria-label="展开目录"
              :aria-expanded="tocOpen"
              data-testid="stage-learn-toc-expand"
              @click="toggleToc"
            >
              «
            </button>
            <button
              v-for="entry in tocEntries"
              :key="entry.tab"
              type="button"
              class="stage-learn__rail-btn"
              :class="{
                'stage-learn__rail-btn--active': activeTab === entry.tab,
                'stage-learn__rail-btn--done': entry.passed,
              }"
              :title="`${entry.label}（${entry.progress}）`"
              :aria-current="activeTab === entry.tab ? 'true' : undefined"
              :data-testid="`stage-rail-${entry.tab}`"
              @click="selectTab(entry.tab)"
            >
              <span aria-hidden="true">{{ entry.icon }}</span>
              <span
                v-if="entry.passed"
                class="stage-learn__rail-tick"
                aria-hidden="true"
              >✓</span>
            </button>
          </div>
        </aside>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.stage-learn-page {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-3) var(--pyt-space-4);
  height: calc(100vh - 56px);
  overflow: hidden;
  background:
    linear-gradient(180deg, var(--pyt-color-bg-primary) 0%, var(--pyt-color-bg-sunken) 100%),
    repeating-linear-gradient(45deg, rgba(110, 79, 29, 0.04) 0 4px, transparent 4px 12px);
}

/* ── 极简顶栏 ── */
.stage-learn__topbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: var(--pyt-space-3);
}
.stage-learn__topbar-title {
  min-width: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── 加载 / 错误态 ── */
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

/* ── 学习通式左右布局（视口高度内布局，主区不撑破屏幕） ── */
.stage-learn__body {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: var(--pyt-space-4);
  align-items: stretch;
  min-height: 0;
}
.stage-learn__body--toc-collapsed {
  grid-template-columns: minmax(0, 1fr) 52px;
}
.stage-learn__main {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.stage-learn__panel {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: var(--pyt-space-3) var(--pyt-space-4);
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border-strong);
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong);
  overflow: hidden;
}
.stage-learn__panel > * {
  flex: 1 1 auto;
  min-height: 0;
}
/* 「练」是长列表：面板内滚动（学习通作业页同款），不影响整页单屏 */
.stage-learn__panel--scroll {
  overflow-y: auto;
}
.stage-learn__panel--scroll > * {
  flex: 0 0 auto;
}
.stage-learn__quiz-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-3);
  margin-bottom: var(--pyt-space-3);
  padding: var(--pyt-space-3);
  background: var(--pyt-color-warning-bg);
  border: 2px solid var(--pyt-color-gold);
  border-radius: var(--pyt-radius-md);
}
.stage-learn__quiz-card p {
  margin: var(--pyt-space-1) 0 0;
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-sm);
}
.stage-learn__quiz-button {
  flex: 0 0 auto;
  padding: 8px 14px;
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-gold);
  color: var(--pyt-color-text-primary);
  font-weight: 700;
  cursor: pointer;
}
.stage-learn__quiz-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

/* ── 右侧目录（展开态） ── */
.stage-learn__toc {
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border-strong);
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong);
}
.stage-learn__toc-full {
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-3);
}
.stage-learn__toc-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}
.stage-learn__toc-head {
  padding-bottom: var(--pyt-space-2);
  border-bottom: 2px dashed var(--pyt-color-border);
}
.stage-learn__toc-chapter {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.stage-learn__toc-toggle {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  min-height: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--pyt-font-size-sm);
  line-height: 1;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  box-shadow: none;
  color: var(--pyt-color-text-muted);
  cursor: pointer;
}
.stage-learn__toc-toggle:active {
  transform: none;
  box-shadow: none;
}
.stage-learn__toc-toggle:hover {
  background: var(--pyt-color-warning-bg);
  color: var(--pyt-color-text-primary);
}
.stage-learn__toc-title {
  margin: var(--pyt-space-1) 0 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: var(--pyt-font-size-lg);
  color: var(--pyt-color-text-primary);
}
.stage-learn__toc-kc {
  margin: var(--pyt-space-1) 0 0;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-hint);
  word-break: break-word;
}
.stage-learn__toc-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.stage-learn__toc-item {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  width: 100%;
  padding: var(--pyt-space-2) var(--pyt-space-3);
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  cursor: pointer;
  text-align: left;
  transition: border-color var(--pyt-motion-base), background var(--pyt-motion-base);
}
.stage-learn__toc-item:hover {
  border-color: var(--pyt-color-gold);
}
.stage-learn__toc-item--active {
  border-color: var(--pyt-color-gold);
  background: var(--pyt-color-warning-bg);
  box-shadow: 2px 2px 0 var(--pyt-color-border-strong);
}
.stage-learn__toc-item--done {
  background: var(--pyt-color-success-bg);
  border-color: var(--pyt-color-success);
}
.stage-learn__toc-icon {
  flex: 0 0 auto;
  font-size: var(--pyt-font-size-lg);
  color: var(--pyt-color-text-hint);
}
.stage-learn__toc-body {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stage-learn__toc-label {
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: var(--pyt-font-size-sm);
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.stage-learn__toc-progress {
  font-family: var(--pyt-font-pixel-mono), monospace;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.stage-learn__toc-tick {
  flex: 0 0 auto;
  color: var(--pyt-color-success);
  font-weight: 700;
}
.stage-learn__complete {
  text-align: center;
  font-size: var(--pyt-font-size-sm);
  font-weight: 700;
  color: var(--pyt-color-success);
  padding-top: var(--pyt-space-2);
  border-top: 2px dashed var(--pyt-color-border);
}

/* ── 右侧目录（收纳态：窄图标栏） ── */
.stage-learn__toc--rail {
  background: var(--pyt-color-bg-elevated);
}
.stage-learn__toc-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-2) var(--pyt-space-1);
}
.stage-learn__rail-btn {
  position: relative;
  width: 36px;
  height: 36px;
  min-height: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--pyt-font-size-base);
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  box-shadow: 2px 2px 0 var(--pyt-color-border-soft);
  color: var(--pyt-color-text-hint);
  cursor: pointer;
  transition: border-color var(--pyt-motion-base), background var(--pyt-motion-base);
}
.stage-learn__rail-btn:hover {
  border-color: var(--pyt-color-gold);
}
.stage-learn__rail-btn--expand {
  font-weight: 700;
  color: var(--pyt-color-text-muted);
}
.stage-learn__rail-btn--active {
  border-color: var(--pyt-color-gold);
  background: var(--pyt-color-warning-bg);
}
.stage-learn__rail-btn--done {
  border-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
}
.stage-learn__rail-tick {
  position: absolute;
  right: -4px;
  top: -6px;
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
  color: var(--pyt-color-success);
}

/* ── 窄屏：恢复纵向文档流，目录移到上方 ── */
@media (max-width: 900px) {
  .stage-learn-page {
    height: auto;
    overflow: visible;
  }
  .stage-learn__body {
    grid-template-columns: minmax(0, 1fr);
  }
  .stage-learn__main {
    min-height: 60vh;
  }
  .stage-learn__toc {
    order: -1;
  }
  .stage-learn__toc-rail {
    flex-direction: row;
    justify-content: flex-start;
  }
}
</style>
