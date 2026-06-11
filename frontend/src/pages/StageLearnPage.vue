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
/** 左侧大区当前展示的资源类型（看/学/练）。 */
type StageTab = 'video' | 'courseware' | 'practice';

const status = ref<LoadStatus>('loading');
const stage = ref<StageDetailDto | null>(null);
const chapterNo = ref<number | null>(null);
const chapterTitle = ref<string>('');

/** 当前左侧展示的资源页签（默认「看」）。 */
const activeTab = ref<StageTab>('video');
/** 右侧目录在窄屏下可收纳；宽屏常驻。 */
const tocOpen = ref<boolean>(true);

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
      <!-- 极简顶栏：仅一个返回按钮（去掉章节/标题/KC/三件套大表头）。 -->
      <div class="stage-learn__topbar">
        <PyButton
          test-id="stage-learn-back"
          variant="secondary"
          size="sm"
          @click="backToMap"
        >
          ← 返回冒险地图
        </PyButton>
        <button
          v-if="status === 'ready' && stage"
          type="button"
          class="stage-learn__toc-toggle"
          :aria-expanded="tocOpen"
          data-testid="stage-learn-toc-toggle"
          @click="toggleToc"
        >
          {{ tocOpen ? '隐藏目录 ▸' : '◂ 目录' }}
        </button>
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
              @progress="onProgress"
            />
          </article>
          <article
            v-show="activeTab === 'practice'"
            class="stage-learn__panel"
          >
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

        <!-- 右侧可收纳目录：看/学/练三组，进度 + ✓，点击切换左侧 -->
        <aside
          v-show="tocOpen"
          class="stage-learn__toc"
          data-testid="stage-learn-toc"
          aria-label="本节目录"
        >
          <header class="stage-learn__toc-head">
            <span class="stage-learn__toc-chapter">第 {{ chapterNo }} 章</span>
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
  min-height: calc(100vh - 56px);
  background:
    linear-gradient(180deg, var(--pyt-color-bg-primary) 0%, var(--pyt-color-bg-sunken) 100%),
    repeating-linear-gradient(45deg, rgba(110, 79, 29, 0.04) 0 4px, transparent 4px 12px);
}

/* ── 极简顶栏 ── */
.stage-learn__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}
.stage-learn__toc-toggle {
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: var(--pyt-font-size-xs);
  padding: 4px 12px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
}
.stage-learn__toc-toggle:hover {
  background: var(--pyt-color-warning-bg);
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

/* ── 学习通式左右布局 ── */
.stage-learn__body {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--pyt-space-4);
  align-items: start;
  min-height: 0;
}
.stage-learn__body--toc-collapsed {
  grid-template-columns: minmax(0, 1fr);
}
.stage-learn__main {
  min-width: 0;
}
.stage-learn__panel {
  padding: var(--pyt-space-4);
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border-strong);
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong);
}

/* ── 右侧目录 ── */
.stage-learn__toc {
  position: sticky;
  top: var(--pyt-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-3);
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border-strong);
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong);
}
.stage-learn__toc-head {
  padding-bottom: var(--pyt-space-2);
  border-bottom: 2px dashed var(--pyt-color-border);
}
.stage-learn__toc-chapter {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
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

/* ── 窄屏：目录移到上方，单列 ── */
@media (max-width: 900px) {
  .stage-learn__body {
    grid-template-columns: minmax(0, 1fr);
  }
  .stage-learn__toc {
    position: static;
    order: -1;
  }
}
</style>
