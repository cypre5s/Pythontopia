<script setup lang="ts">
/**
 * /chapter-test/:chapter 章末大测试页（R-15 §4.2 / §4.3）。
 *
 * 流程：进入 → loadLatest + prepare（触发预生成）→「开始考试」start 下发去答案考卷 →
 *       限时作答（倒计时到点自动交卷）→ submit 上送 {answers, integrity} → 展示成绩 + 逐题解析。
 *
 * 防作弊（尽力而为）：
 *   - 编程题禁用粘贴并计数（CodingQuestion onpaste → recordPaste → integrity.paste_blocked）。
 *   - 考试期间页面失焦计数（visibilitychange→hidden → recordBlur → integrity.blur_events）。
 *   - 到点/截止后提交标记 integrity.overtime。
 *   - 路由守卫禁止考试中跳转 AI 页面（组件内 onBeforeRouteLeave + 全局守卫读 examActive 单例）。
 *
 * 绝不在提交前渲染任何答案/解析：考卷为去答案题面；解析仅来自提交后的 result。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import { useToastStore } from '@stores/toast';
import {
  ChapterTestQuestion,
  ExamCountdown,
  ResultPanel,
  setExamActive,
  useChapterTestExam,
  useExamRouteGuard,
} from '@/components/chaptertest';
import type { ChapterTestResultItemDto } from '@/api/chapterTest';

const props = defineProps<{ chapter: number }>();

const router = useRouter();
const exam = useChapterTestExam(props.chapter);
const exitConfirm = ref<boolean>(false);

const validChapter = computed<boolean>(
  () => Number.isInteger(props.chapter) && props.chapter >= 1 && props.chapter <= 7,
);

// 有考卷即进入「考试/结果」视图；否则停留在「开考前」闸门。
const showGate = computed<boolean>(() => validChapter.value && exam.paper.value === null);
const inProgress = computed<boolean>(() => exam.phase.value === 'in_progress');
const submitted = computed<boolean>(() => exam.phase.value === 'submitted');
const submitError = computed<boolean>(() => exam.phase.value === 'error' && exam.paper.value !== null);

const prepareHint = computed<string>(() => {
  switch (exam.prepareStatus.value) {
    case 'READY':
      return '题目已就绪，可以开始。';
    case 'GENERATING':
    case 'PENDING':
      return '题目正在按你的学情预生成…（开考时若仍未就绪将用兜底题组，全程不调用 AI）';
    case 'FAILED':
      return '预生成失败，开考将使用兜底题组。';
    default:
      return '本测试限时作答、禁止粘贴、失焦会被记录，请独立完成。';
  }
});

const reviewMap = computed<Record<string, ChapterTestResultItemDto>>(() => {
  const map: Record<string, ChapterTestResultItemDto> = {};
  for (const item of exam.result.value?.items ?? []) {
    map[item.qid] = item;
  }
  return map;
});

function reviewItemFor(qid: string): ChapterTestResultItemDto | null {
  return reviewMap.value[qid] ?? null;
}

function warn(message: string): void {
  try {
    useToastStore().show(message, 'error');
  } catch {
    // pinia 未就绪（极端时序）忽略，不影响考试主流程。
  }
}

async function onStart(): Promise<void> {
  await exam.start();
}

async function onSubmit(): Promise<void> {
  await exam.submitNow();
}

function onExpire(): void {
  if (exam.phase.value !== 'in_progress') return;
  exam.markOvertime();
  warn('时间到，已自动交卷。');
  void exam.submitNow();
}

function onPasteBlocked(): void {
  exam.recordPaste();
  warn('考试中编程题禁止粘贴。');
}

function onVisibility(): void {
  if (exam.isActive.value && typeof document !== 'undefined' && document.hidden) {
    exam.recordBlur();
  }
}

function onExit(): void {
  // 考试进行中离开需二次确认（离开不会自动交卷，截止时间仍在服务端计）。
  if (exam.isActive.value && !exitConfirm.value) {
    exitConfirm.value = true;
    window.setTimeout(() => {
      exitConfirm.value = false;
    }, 3000);
    return;
  }
  setExamActive(false);
  void router.push('/mainline-map');
}

// 组件内守卫：考试进行中拦截前往 AI 页面。
useExamRouteGuard({
  isActive: () => exam.isActive.value,
  onBlock: (path) => warn(`考试进行中，禁止前往 ${path}`),
});

// 同步全局 examActive 单例，供共享 router 守卫使用。
watch(
  () => exam.isActive.value,
  (active) => setExamActive(active),
  { immediate: true },
);

onMounted(() => {
  if (!validChapter.value) return;
  void exam.loadLatest();
  void exam.prepare();
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', onVisibility);
  }
});

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') {
    document.removeEventListener('visibilitychange', onVisibility);
  }
  setExamActive(false);
});
</script>

<template>
  <AppLayout>
    <section
      class="ct-page"
      data-testid="chapter-test-page"
    >
      <!-- 无效章节 -->
      <div
        v-if="!validChapter"
        class="ct-block ct-block--error"
        data-testid="ct-invalid"
      >
        <p>无效的章节编号：{{ chapter }}（应为 1–7）。</p>
        <button
          type="button"
          class="ct-btn"
          @click="router.push('/mainline-map')"
        >
          返回冒险地图
        </button>
      </div>

      <!-- 开考前闸门 -->
      <div
        v-else-if="showGate"
        class="ct-gate"
        data-testid="ct-gate"
      >
        <header class="ct-gate__head">
          <h1 class="ct-gate__title">
            第 {{ chapter }} 章 · 章末大测试
          </h1>
          <button
            type="button"
            class="ct-btn ct-btn--ghost"
            data-testid="ct-gate-exit"
            @click="onExit"
          >
            ← 返回地图
          </button>
        </header>

        <ul class="ct-gate__rules">
          <li>选择 / 判断 / 填空 / 编程四类题，满分 100，限时作答。</li>
          <li>考试期间禁止前往百晓工坊 / AI 导学 / Bug 矿洞。</li>
          <li>编程题禁止粘贴；切屏失焦会被记录供教师参考。</li>
          <li>到达截止时间将自动交卷。</li>
        </ul>

        <p
          class="ct-gate__hint"
          data-testid="ct-prepare-hint"
        >
          {{ prepareHint }}
        </p>

        <p
          v-if="exam.latestResult.value"
          class="ct-gate__latest"
          data-testid="ct-latest"
        >
          历史最佳：{{ exam.latestResult.value.score }} / {{ exam.latestResult.value.maxScore }}
          （第 {{ exam.latestResult.value.attemptNo }} 次）
        </p>

        <p
          v-if="exam.phase.value === 'error'"
          class="ct-gate__error"
          data-testid="ct-gate-error"
        >
          {{ exam.errorMessage.value }}
        </p>

        <button
          type="button"
          class="ct-btn ct-btn--primary"
          data-testid="ct-start"
          :disabled="exam.phase.value === 'starting'"
          @click="onStart"
        >
          {{ exam.phase.value === 'starting' ? '正在出卷…' : '开始考试' }}
        </button>
      </div>

      <!-- 考试 / 结果 -->
      <template v-else>
        <header class="ct-exam-head">
          <div class="ct-exam-head__left">
            <h1 class="ct-exam-head__title">
              第 {{ chapter }} 章 · 章末大测试
            </h1>
            <span
              class="ct-exam-head__progress"
              data-testid="ct-progress"
            >
              已答 {{ exam.answeredCount.value }} / {{ exam.totalCount.value }}
            </span>
          </div>
          <ExamCountdown
            v-if="exam.paper.value && (inProgress || exam.phase.value === 'submitting')"
            :deadline-at="exam.paper.value.deadlineAt"
            :active="inProgress"
            @expire="onExpire"
          />
          <div class="ct-exam-head__right">
            <button
              v-if="inProgress || submitError"
              type="button"
              class="ct-btn ct-btn--primary"
              data-testid="ct-submit"
              :disabled="exam.phase.value === 'submitting'"
              @click="onSubmit"
            >
              {{
                exam.phase.value === 'submitting'
                  ? '交卷中…'
                  : submitError
                    ? '重试交卷'
                    : '交卷'
              }}
            </button>
            <button
              type="button"
              class="ct-btn ct-btn--ghost"
              data-testid="ct-exit"
              @click="onExit"
            >
              {{ exitConfirm ? '再点确认离开' : '退出' }}
            </button>
          </div>
        </header>

        <p
          v-if="submitError"
          class="ct-exam-error"
          data-testid="ct-submit-error"
        >
          交卷失败：{{ exam.errorMessage.value }}
        </p>

        <ResultPanel
          v-if="submitted && exam.result.value"
          :result="exam.result.value"
        />

        <div class="ct-questions">
          <ChapterTestQuestion
            v-for="(item, i) in exam.paper.value?.items ?? []"
            :key="item.qid"
            :item="item"
            :index="i + 1"
            :answer="exam.answers[item.qid]"
            :review-item="reviewItemFor(item.qid)"
            @choice="exam.setChoice"
            @judge="exam.setJudge"
            @blank="exam.setBlank"
            @code="exam.setCode"
            @paste-blocked="onPasteBlocked"
          />
        </div>

        <footer class="ct-exam-foot">
          <button
            v-if="inProgress"
            type="button"
            class="ct-btn ct-btn--primary ct-btn--wide"
            data-testid="ct-submit-bottom"
            @click="onSubmit"
          >
            交卷
          </button>
          <button
            v-else-if="submitted"
            type="button"
            class="ct-btn ct-btn--primary ct-btn--wide"
            data-testid="ct-back-map"
            @click="onExit"
          >
            返回冒险地图
          </button>
        </footer>
      </template>
    </section>
  </AppLayout>
</template>

<style scoped>
.ct-page {
  min-height: 100%;
  padding: var(--pyt-space-5);
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-4);
  background:
    radial-gradient(ellipse at top, rgba(255, 247, 230, 0.85) 0%, rgba(241, 216, 150, 0.55) 60%, rgba(212, 175, 55, 0.18) 100%);
  font-family: var(--pyt-font-sans);
  color: var(--pyt-color-text-primary);
}
.ct-block {
  padding: var(--pyt-space-5);
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
  display: grid;
  gap: var(--pyt-space-3);
  justify-items: start;
}
.ct-block--error { border-color: var(--pyt-color-danger); }

.ct-gate {
  max-width: 720px;
  margin: 0 auto;
  width: 100%;
  display: grid;
  gap: var(--pyt-space-4);
  padding: var(--pyt-space-6);
  background: linear-gradient(180deg, var(--pyt-color-bg-elevated) 0%, var(--pyt-color-bg-sunken) 100%);
  border: 3px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
  box-shadow: var(--pyt-shadow-card);
}
.ct-gate__head { display: flex; align-items: center; justify-content: space-between; gap: var(--pyt-space-3); }
.ct-gate__title { margin: 0; font-size: var(--pyt-font-size-2xl); color: var(--pyt-color-text-primary); }
.ct-gate__rules {
  margin: 0;
  padding-left: var(--pyt-space-5);
  display: grid;
  gap: var(--pyt-space-1);
  color: var(--pyt-color-text-hint);
  font-size: var(--pyt-font-size-base);
  line-height: 1.6;
}
.ct-gate__hint { margin: 0; color: var(--pyt-color-text-muted); font-size: var(--pyt-font-size-sm); }
.ct-gate__latest { margin: 0; color: var(--pyt-color-text-primary); font-weight: 700; }
.ct-gate__error { margin: 0; color: var(--pyt-color-danger); }

.ct-exam-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-3);
  flex-wrap: wrap;
  padding: var(--pyt-space-3) var(--pyt-space-4);
  background: linear-gradient(180deg, var(--pyt-color-bg-elevated) 0%, var(--pyt-color-bg-sunken) 100%);
  border: 3px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
  box-shadow: var(--pyt-shadow-card);
  position: sticky;
  top: 0;
  z-index: var(--pyt-z-hud);
}
.ct-exam-head__left { display: flex; flex-direction: column; gap: var(--pyt-space-1); }
.ct-exam-head__title { margin: 0; font-size: var(--pyt-font-size-lg); }
.ct-exam-head__progress { font-size: var(--pyt-font-size-sm); color: var(--pyt-color-text-muted); }
.ct-exam-head__right { display: flex; align-items: center; gap: var(--pyt-space-2); }
.ct-exam-error {
  margin: 0;
  padding: var(--pyt-space-3);
  background: var(--pyt-color-danger-bg);
  border: 2px solid var(--pyt-color-danger);
  border-radius: var(--pyt-radius-md);
  color: var(--pyt-color-danger);
}
.ct-questions { display: grid; gap: var(--pyt-space-4); }
.ct-exam-foot { display: flex; justify-content: center; padding: var(--pyt-space-4) 0; }

.ct-btn {
  font-family: var(--pyt-font-sans);
  font-size: var(--pyt-font-size-sm);
  padding: var(--pyt-space-2) var(--pyt-space-4);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-primary);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  cursor: pointer;
  box-shadow: var(--pyt-shadow-card);
}
.ct-btn:hover:not(:disabled) { background: var(--pyt-color-bg-soft); }
.ct-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.ct-btn--primary {
  background: linear-gradient(180deg, var(--pyt-color-gold-bright) 0%, var(--pyt-color-gold) 100%);
  border-color: var(--pyt-color-primary-dark);
  color: var(--pyt-color-primary-dark);
  font-weight: 700;
}
.ct-btn--ghost { background: var(--pyt-color-bg-soft); }
.ct-btn--wide { min-width: 240px; padding: var(--pyt-space-3) var(--pyt-space-6); font-size: var(--pyt-font-size-base); }

@media (max-width: 720px) {
  .ct-exam-head { flex-direction: column; align-items: stretch; }
}
</style>
