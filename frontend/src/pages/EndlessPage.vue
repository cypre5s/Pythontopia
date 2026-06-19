<script setup lang="ts">
/**
 * 「无尽模式」页面：连续出题、自适应难度，玩家追求 streak / 总分
 * （区别于 /boss/:chapter 的 4 阶段章节 boss 主线）。
 *
 * 数据：graph /agents/boss/problem（5 重审查 + golden 兜底）+ /agents/boss/evaluate
 *       + 前端 Pyodide 二次校验；题目章节按 focusChapter 锁定，未锁定时随机。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import { kcDisplayName } from '@/data/kcNames';
import BossSprite from '@/components/boss/BossSprite.vue';
import { KindRouter } from '@/components/arena';
import PyButton from '@/components/ui/PyButton.vue';
import PythonCodeEditor from '@/components/training/PythonCodeEditor.vue';
import JudgeResultPanel from '@/components/challenge/JudgeResultPanel.vue';
import { useEndlessRun } from '@/composables/useEndlessRun';
import { MAIN_QUEST_BOSSES } from '@/data/bossCatalog';
import { useArenaStore } from '@stores/arena';
import { useMainlineStore } from '@stores/mainline';
import { useToastStore } from '@stores/toast';
import type { ArenaLevelKind } from '@/types/arena';
import type { ChallengeResult } from '@/api/dto/CodeChallenge';

const router = useRouter();
const route = useRoute();
const arena = useArenaStore();
const mainline = useMainlineStore();
const toast = useToastStore();

// 从 query 读 ?chapter & ?arenaLevel：EndlessPage 同时承担「随机刷题」与
// 「ArenaPage 闯关塔单关入口」两个角色。arenaLevel 形如 "1-3"，通关后回 ArenaPage markCleared。
const queryChapter = computed<number | null>(() => {
  const raw = Number(route.query.chapter);
  return Number.isInteger(raw) && raw >= 1 && raw <= 7 ? raw : null;
});
const queryKc = computed<string | null>(() =>
  typeof route.query.kc === 'string' && route.query.kc.length > 0 ? route.query.kc : null,
);
const queryKind = computed<string | null>(() =>
  typeof route.query.kind === 'string' && route.query.kind.length > 0 ? route.query.kind : null,
);
const arenaLevelId = computed<string | null>(() =>
  typeof route.query.arenaLevel === 'string' ? route.query.arenaLevel : null,
);
const focusChapter = ref<number | null>(queryChapter.value);
const focusKc = ref<string | null>(queryKc.value);
const preferredKind = ref<string | null>(queryKind.value);
const arenaLevel = ref<string | null>(arenaLevelId.value);
const run = useEndlessRun({ focusChapter, focusKc, preferredKind, arenaLevel });

watch(queryChapter, (next) => {
  focusChapter.value = next;
});
watch(queryKc, (next) => {
  focusKc.value = next;
});
watch(queryKind, (next) => {
  preferredKind.value = next;
});
watch(arenaLevelId, (next) => {
  arenaLevel.value = next;
});

// 作为闯关塔单关入口时，通过 1 题即记 markCleared 并自动回 ArenaPage。
watch(
  () => run.phase.value,
  (phase) => {
    if (phase === 'passed' && arenaLevelId.value) {
      arena.markCleared(arenaLevelId.value, run.elapsedSec.value);
      // 留 1.5s 让玩家看到「通过」庆祝再回闯关塔
      if (typeof window !== 'undefined' && queryChapter.value !== null) {
        const ch = queryChapter.value;
        window.setTimeout(() => {
          void router.push(`/arena/${ch}`);
        }, 1500);
      }
    }
  },
);

// 无尽补星小结上报（r15 §7：锁定本章 + 单轮答对 ≥8 且正确率 ≥80%）。
// 达标即上报一次（每轮至多一次，失败可在下次达标变化时重试）；门槛与
// 后端 MainlineRecoveryService 常量一致，服务端会重新校验 + 每日每章防刷。
const ENDLESS_STAR_MIN_CORRECT = 8;
const ENDLESS_STAR_MIN_ACCURACY = 0.8;
const summaryReported = ref<boolean>(false);

watch(
  () => [run.passedCount.value, run.attempts.value] as const,
  async ([correct, total]) => {
    const chapter = focusChapter.value;
    if (summaryReported.value || chapter === null || total <= 0) return;
    if (correct < ENDLESS_STAR_MIN_CORRECT || correct / total < ENDLESS_STAR_MIN_ACCURACY) return;
    summaryReported.value = true;
    try {
      const awarded = await mainline.submitEndlessSummary({
        chapter,
        correct,
        total,
        streak: run.streak.value,
      });
      if (awarded) {
        toast.show(`第 ${chapter} 章无尽挑战补星 +1 ★`, 'success');
      }
    } catch {
      summaryReported.value = false; // 上报失败：允许继续答题后重试。
    }
  },
);

let secondTimer: number | null = null;

onMounted(() => {
  if (typeof window !== 'undefined') {
    secondTimer = window.setInterval(() => run.tickSecond(), 1000);
  }
});
onBeforeUnmount(() => {
  if (secondTimer !== null) {
    window.clearInterval(secondTimer);
    secondTimer = null;
  }
});

async function onStartRun(): Promise<void> {
  summaryReported.value = false; // 新一轮重新累计补星小结
  await run.startRun();
}
async function onSubmit(): Promise<void> {
  await run.submit();
}
async function onNext(): Promise<void> {
  await run.nextProblem();
}
function onExit(): void {
  run.abort();
  void router.push('/world');
}

const currentChapterName = computed<string>(() => {
  const p = run.problem.value;
  if (!p) return '—';
  return MAIN_QUEST_BOSSES.find((b) => b.chapter === p.chapter)?.name ?? `第 ${p.chapter} 章`;
});

const submitDisabled = computed<boolean>(() => {
  const ph = run.phase.value;
  return ph === 'evaluating' || ph === 'loading' || ph === 'error';
});

const showNextButton = computed<boolean>(() => {
  const ph = run.phase.value;
  return ph === 'passed' || ph === 'failed';
});

// 闯关塔单关入口显示题型中文名，区分 9 种关卡题型。
const KIND_LABELS: Record<string, string> = {
  pdf_question: 'Rd 课件问答',
  fill_blank:   '▭ 代码填空',
  debug:        '⚠ 改错题',
  short_answer: '✎ 简答 (grounding 评分)',
  closed_loop:  '⟲ 调试闭环',
  dual_track:   '⇆ 双轨判题',
  multi_round:  '◉◉◉ 3 题组合',
  teach_back:   'Tb 教学反转',
  bug_mine:     'Bg Bug 矿洞 (30s 限时)',
};
const kindLabel = computed<string>(() => {
  const kind = preferredKind.value;
  if (!kind) return '随机刷题';
  return KIND_LABELS[kind] ?? kind;
});

// 把 query 里的任意 kind 字符串收窄成合法 ArenaLevelKind；非法 / 缺省 → null（回退到通用代码编辑器路径）。
const ARENA_KINDS = Object.keys(KIND_LABELS) as ArenaLevelKind[];
const preferredKindTyped = computed<ArenaLevelKind | null>(() => {
  const k = preferredKind.value;
  return k && (ARENA_KINDS as string[]).includes(k) ? (k as ArenaLevelKind) : null;
});

// 后端 BossAttemptResultDto → 共享 ChallengeResult，喂统一的 JudgeResultPanel（带 aria-live 自动播报，NFR-USE）。
const challengeResult = computed<ChallengeResult | null>(() => {
  const r = run.lastResult.value;
  if (!r) return null;
  return {
    passed: r.passed,
    passedCases: r.passedTestCases,
    totalCases: r.totalTestCases,
    verdict: r.passed ? 'AC' : 'WA',
    feedback: r.feedback,
    ...(r.failureFirstHint ? { firstHint: r.failureFirstHint } : {}),
  };
});

// 本题加分（展示用）：取最近一条历史记录的得分。
const lastScore = computed<number>(() => run.history.value[0]?.score ?? 0);

// 提交期出错（判题服务不可用）时，若题目仍在则允许「重试提交」，否则「重新开始」。
async function onRetry(): Promise<void> {
  if (run.problem.value) {
    await onSubmit();
  } else {
    await onStartRun();
  }
}

// 对标做题页（/training?problemId=476）的 OJ 排布：
// - 通用刷题（无 kind）走「题面 | 作答」两栏；
// - 关卡题型（pdf_question / fill_blank / debug…）的卡片自带题面+作答，整张铺在作答区（单栏）。
const isTwoPane = computed<boolean>(() => preferredKindTyped.value === null);

// 「最近题」改为可收缩隐藏的侧栏收纳，默认收起，作答区更宽敞。
const recentOpen = ref<boolean>(false);
function toggleRecent(): void {
  recentOpen.value = !recentOpen.value;
}

/** 输入输出样例「一键复制」（与做题页一致）。 */
async function copySample(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text ?? '');
    toast.show('已复制到剪贴板', 'success');
  } catch {
    toast.show('复制失败，请手动选择复制', 'error');
  }
}
</script>

<template>
  <AppLayout>
    <section
      class="endless-page"
      data-testid="endless-page"
    >
      <header class="endless-head">
        <PyButton
          variant="secondary"
          size="sm"
          test-id="endless-exit"
          @click="onExit"
        >
          ← 返回小镇
        </PyButton>
        <h1 class="endless-title">
          {{ arenaLevelId ? `闯关塔 ${arenaLevelId} · ${kindLabel}` : '无尽闯关 · 连续出题' }}
        </h1>
        <div class="endless-stats">
          <span
            class="stat"
            data-testid="endless-streak"
          >
            <span class="stat-label">连胜</span>
            <span class="stat-value">{{ run.streak.value }}</span>
          </span>
          <span class="stat">
            <span class="stat-label">最佳</span>
            <span class="stat-value">{{ run.bestStreak.value }}</span>
          </span>
          <span
            class="stat"
            data-testid="endless-total-score"
          >
            <span class="stat-label">总分</span>
            <span class="stat-value">{{ run.totalScore.value }}</span>
          </span>
          <span class="stat">
            <span class="stat-label">难度</span>
            <span class="stat-value">{{ run.currentDifficulty.value }} / 5</span>
          </span>
        </div>
      </header>

      <div class="endless-toolbar">
        <label class="toolbar-field">
          <span>章节聚焦</span>
          <select
            v-model.number="focusChapter"
            data-testid="endless-focus-chapter"
          >
            <option :value="null">
              随机 7 章
            </option>
            <option
              v-for="b in MAIN_QUEST_BOSSES"
              :key="b.chapter"
              :value="b.chapter"
            >
              第 {{ b.chapter }} 章 · {{ b.name }}
            </option>
          </select>
        </label>
        <PyButton
          v-if="run.phase.value === 'idle'"
          variant="primary"
          size="sm"
          test-id="endless-start"
          @click="onStartRun"
        >
          开始无尽
        </PyButton>
        <span
          v-else
          class="endless-rate"
        >
          近 10 题失败率 {{ Math.round(run.failureRate.value * 100) }}%
        </span>
      </div>

      <div
        v-if="run.phase.value === 'idle'"
        class="endless-empty"
        data-testid="endless-empty"
      >
        <BossSprite
          :chapter="focusChapter ?? 1"
          phase="idle"
          :size="160"
        />
        <p>
          点击「开始无尽」抽一道 AI 出题（5 重审查 + golden 兜底）；
          连胜越多难度越高，失败一次难度自适应回调。
        </p>
        <p class="endless-hint">
          建议：选择最薄弱章节锁定练习；解放双手时切换"随机 7 章"。
        </p>
      </div>

      <div
        v-else-if="run.phase.value === 'loading'"
        class="endless-loading"
        role="status"
        aria-live="polite"
      >
        正在抽题中…
      </div>

      <div
        v-else-if="run.phase.value === 'error'"
        class="endless-error"
        role="alert"
        aria-live="assertive"
      >
        <p>{{ run.errorMessage.value }}</p>
        <PyButton
          variant="primary"
          size="sm"
          test-id="endless-retry"
          @click="onRetry"
        >
          {{ run.problem.value ? '重试提交' : '重新开始' }}
        </PyButton>
      </div>

      <div
        v-else
        class="endless-stage"
        data-testid="endless-body"
      >
        <div
          class="endless-oj"
          :class="{
            'endless-oj--two': isTwoPane,
            'endless-oj--single': !isTwoPane,
            'endless-oj--recent': recentOpen,
          }"
        >
          <!-- 题面（通用刷题：左列） -->
          <aside
            v-if="isTwoPane"
            class="oj-pane oj-problem"
          >
            <div class="oj-card oj-card--problem">
              <header class="oj-card__head">
                <div class="oj-title-row">
                  <h2>{{ currentChapterName }}</h2>
                  <span class="oj-tag">{{ kindLabel }}</span>
                </div>
                <div class="oj-meta">
                  <span>{{ kcDisplayName(run.problem.value?.kcCode) }}</span>
                  <span>难度 {{ run.problem.value?.difficulty }} / 5</span>
                  <span
                    class="oj-meta__badge"
                    :class="run.problem.value?.aiGenerated ? 'is-ai' : 'is-golden'"
                  >{{ run.problem.value?.aiGenerated ? 'AI · 5 重审查' : 'Golden 兜底' }}</span>
                  <span class="oj-meta__timer">⏱ {{ run.elapsedSec.value }}s</span>
                </div>
              </header>
              <div class="oj-card__body">
                <pre class="oj-statement">{{ run.problem.value?.statement }}</pre>
                <section
                  v-if="run.problem.value?.sampleInput || run.problem.value?.sampleOutput"
                  class="oj-samples"
                >
                  <h3 class="oj-section-title">
                    输入输出样例
                  </h3>
                  <div class="oj-sample-grid">
                    <div class="oj-sample">
                      <span class="oj-sample__label">输入</span>
                      <div class="oj-iobox">
                        <button
                          type="button"
                          class="oj-copy"
                          title="复制输入"
                          @click="copySample(run.problem.value?.sampleInput ?? '')"
                        >
                          ⧉
                        </button>
                        <pre>{{ run.problem.value?.sampleInput || '(空)' }}</pre>
                      </div>
                    </div>
                    <div class="oj-sample">
                      <span class="oj-sample__label">输出</span>
                      <div class="oj-iobox">
                        <button
                          type="button"
                          class="oj-copy"
                          title="复制输出"
                          @click="copySample(run.problem.value?.sampleOutput ?? '')"
                        >
                          ⧉
                        </button>
                        <pre>{{ run.problem.value?.sampleOutput || '(空)' }}</pre>
                      </div>
                    </div>
                  </div>
                </section>
              </div>
            </div>
          </aside>

          <!-- 作答 -->
          <section class="oj-pane oj-solution">
            <article class="oj-card oj-card--work">
              <header class="oj-card__head oj-card__head--row">
                <div class="oj-work-title">
                  <h3>{{ isTwoPane ? '作答' : kindLabel }}</h3>
                  <span
                    v-if="!isTwoPane"
                    class="oj-sub"
                  >{{ currentChapterName }} · {{ kcDisplayName(run.problem.value?.kcCode) }} · 难度 {{ run.problem.value?.difficulty }} / 5 · ⏱ {{ run.elapsedSec.value }}s</span>
                </div>
                <button
                  type="button"
                  class="oj-recent-toggle"
                  data-testid="endless-recent-toggle"
                  @click="toggleRecent"
                >
                  {{ recentOpen ? '收起最近 ›' : `‹ 最近 ${run.history.value.length} 题` }}
                </button>
              </header>
              <div class="oj-card__body oj-card__body--work">
                <KindRouter
                  v-if="preferredKindTyped && run.problem.value"
                  v-model:code="run.code.value"
                  :kind="preferredKindTyped"
                  :problem="run.problem.value"
                  @timeout="onSubmit"
                >
                  <pre class="oj-statement">{{ run.problem.value?.statement }}</pre>
                </KindRouter>
                <div
                  v-else
                  class="oj-editor-host"
                  data-testid="endless-code"
                >
                  <PythonCodeEditor v-model="run.code.value" />
                </div>
              </div>
              <footer class="oj-card__foot">
                <div class="oj-actions">
                  <PyButton
                    variant="primary"
                    test-id="endless-submit"
                    :disabled="submitDisabled"
                    @click="onSubmit"
                  >
                    {{ run.phase.value === 'evaluating' ? '评测中…' : '提交答案' }}
                  </PyButton>
                  <PyButton
                    v-if="showNextButton"
                    variant="secondary"
                    test-id="endless-next"
                    @click="onNext"
                  >
                    {{ run.phase.value === 'passed' ? '下一题（难度提升）' : '继续挑战（难度下调）' }}
                  </PyButton>
                </div>
                <div
                  v-if="challengeResult"
                  class="oj-result"
                  data-testid="endless-result"
                >
                  <JudgeResultPanel
                    :result="challengeResult"
                    mode="judge"
                  />
                  <p
                    v-if="challengeResult.passed"
                    class="oj-result__score"
                  >
                    本题加分 +{{ lastScore }}
                  </p>
                </div>
              </footer>
            </article>
          </section>

          <!-- 最近题：可收缩隐藏的侧栏收纳 -->
          <aside
            v-if="recentOpen"
            class="oj-pane oj-recent"
          >
            <header class="oj-recent__head">
              <h3>最近 8 题</h3>
              <button
                type="button"
                class="oj-recent__close"
                aria-label="收起最近题"
                @click="toggleRecent"
              >
                ×
              </button>
            </header>
            <ul
              class="oj-history"
              data-testid="endless-history"
            >
              <li
                v-for="(h, idx) in run.history.value.slice(0, 8)"
                :key="`${h.problemId}-${idx}`"
                :class="{
                  'oj-history__item': true,
                  'is-passed': h.passed,
                  'is-failed': !h.passed,
                }"
              >
                <header>
                  <strong>第 {{ h.chapter }} 章 · 难 {{ h.difficulty }}</strong>
                  <span class="oj-history__kc">{{ kcDisplayName(h.kcCode) }}</span>
                </header>
                <small>
                  {{ h.passed ? '通过' : '失败' }} · +{{ h.score }} 分 · {{ h.elapsedSec }}s
                </small>
              </li>
              <li
                v-if="run.history.value.length === 0"
                class="oj-history__empty"
              >
                还没有记录，先答一题吧。
              </li>
            </ul>
          </aside>
        </div>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
/* 白色/羊皮纸亮色主题：与练习场（TrainingPanel / tokens.css）同语言；
   仅题面/样例/代码编辑区保留终端深色例外（与训练场 stdout 块一致）。 */
.endless-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  min-height: calc(100dvh - 56px);
  background:
    linear-gradient(180deg, rgba(243, 220, 157, 0.26), rgba(122, 93, 48, 0.1)),
    var(--pyt-color-bg-primary);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-pixel);
}
.endless-head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  background: rgba(255, 249, 234, 0.94);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  box-shadow: 3px 3px 0 rgba(74, 54, 29, 0.35);
}
.endless-title {
  margin: 0;
  font-size: 18px;
  color: var(--pyt-color-text-primary);
}
.endless-stats {
  display: flex;
  gap: 10px;
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.stat-label {
  font-size: 10px;
  color: var(--pyt-color-text-muted);
}
.stat-value {
  font-size: 16px;
  color: var(--pyt-color-warning);
  font-weight: 700;
}
.endless-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}
.toolbar-field {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.toolbar-field select {
  font-family: inherit;
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-primary);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  padding: 2px 6px;
}
.endless-rate {
  color: var(--pyt-color-warning);
  font-size: 12px;
}
.endless-empty,
.endless-loading,
.endless-error {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  text-align: center;
  padding: 24px;
  color: var(--pyt-color-text-hint);
}
.endless-error {
  color: var(--pyt-color-danger);
}
.endless-hint {
  color: var(--pyt-color-text-muted);
  font-size: 12px;
}
/* ── OJ 排布（对标 /training?problemId=476）：题面 | 作答 + 可收纳「最近题」侧栏 ── */
.endless-stage {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
}
.endless-oj {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  column-gap: 12px;
  row-gap: 12px;
}
/* 通用刷题：题面 | 作答（与做题页一致的两栏） */
.endless-oj--two {
  grid-template-columns: minmax(320px, 0.92fr) minmax(440px, 1.08fr);
}
.endless-oj--two.endless-oj--recent {
  grid-template-columns: minmax(280px, 0.8fr) minmax(380px, 1fr) minmax(240px, 0.5fr);
}
/* 关卡题型：卡片自带题面+作答，单栏铺满（+ 可选最近侧栏） */
.endless-oj--single {
  grid-template-columns: minmax(0, 1fr);
}
.endless-oj--single.endless-oj--recent {
  grid-template-columns: minmax(0, 1fr) minmax(240px, 0.42fr);
}

.oj-pane {
  min-width: 0;
  min-height: 0;
}
.oj-card {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: rgba(255, 249, 234, 0.94);
  border: 2px solid var(--pyt-color-border);
  border-radius: 8px;
  box-shadow: 0 4px 0 rgba(74, 54, 29, 0.32), 0 10px 28px rgba(60, 41, 18, 0.14);
}
.oj-card__head {
  flex: 0 0 auto;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(74, 54, 29, 0.22);
  background: rgba(255, 252, 242, 0.82);
}
.oj-card__head--row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.oj-title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.oj-title-row h2 {
  margin: 0;
  font-family: var(--pyt-font-read);
  font-size: 19px;
  line-height: 1.3;
}
.oj-tag {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
  padding: 3px 8px;
}
.oj-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}
.oj-meta > span {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
  background: rgba(241, 216, 150, 0.42);
  border: 1px solid rgba(74, 54, 29, 0.22);
  border-radius: 4px;
  padding: 4px 8px;
}
.oj-meta__badge.is-ai {
  color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
  border-color: var(--pyt-color-success);
}
.oj-meta__badge.is-golden {
  color: var(--pyt-color-warning);
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-warning);
}
.oj-meta__timer {
  margin-left: auto;
}
.oj-work-title h3 {
  margin: 0;
  font-family: var(--pyt-font-read);
  font-size: 16px;
}
.oj-sub {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--pyt-color-text-hint);
}
.oj-card__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  scrollbar-width: thin;
}
.oj-card__body--work {
  gap: 12px;
}
.oj-section-title {
  margin: 0 0 8px;
  font-family: var(--pyt-font-read);
  font-size: 15px;
}
/* 题面/样例：保留终端深色（与做题页 stdout 同语言） */
.oj-statement {
  margin: 0;
  padding: 12px 14px;
  background: var(--pyt-color-terminal-bg);
  border: 1px solid var(--pyt-color-border-strong);
  border-radius: 6px;
  white-space: pre-wrap;
  font-family: 'Fira Code', 'JetBrains Mono', 'Source Code Pro', monospace;
  font-size: 13px;
  line-height: 1.7;
  color: var(--pyt-color-terminal-text);
}
.oj-sample-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.oj-sample {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.oj-sample__label {
  font-family: var(--pyt-font-read);
  font-size: 13px;
  font-weight: 700;
}
/* LeetCode 式：复制按钮嵌在数据黑框内部右上角，悬停显形 */
.oj-iobox {
  position: relative;
}
.oj-iobox pre {
  margin: 0;
  padding: 10px 12px;
  min-height: 46px;
  overflow: auto;
  white-space: pre-wrap;
  border-radius: 6px;
  font-family: 'Fira Code', 'JetBrains Mono', 'Source Code Pro', monospace;
  font-size: 13px;
  line-height: 1.55;
  color: var(--pyt-color-terminal-text);
  background: var(--pyt-color-terminal-bg);
  border: 1px solid var(--pyt-color-border-strong);
}
.oj-copy {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 1;
  padding: 2px 7px;
  font-size: 12px;
  line-height: 1.2;
  cursor: pointer;
  color: #cfe4f5;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 4px;
  opacity: 0;
  transition: opacity 120ms ease, background 120ms ease;
}
.oj-iobox:hover .oj-copy,
.oj-copy:focus-visible {
  opacity: 1;
}
.oj-copy:hover {
  background: rgba(255, 255, 255, 0.2);
}
.oj-editor-host {
  flex: 1 1 auto;
  min-height: 240px;
  border-radius: 6px;
  overflow: hidden;
}
.oj-card__foot {
  flex: 0 0 auto;
  display: grid;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid rgba(74, 54, 29, 0.22);
  background: rgba(255, 252, 242, 0.7);
}
.oj-actions {
  display: flex;
  gap: 8px;
}
.oj-result {
  display: grid;
  gap: 6px;
}
.oj-result__score {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-success);
}
.oj-recent-toggle {
  flex: 0 0 auto;
  font-family: var(--pyt-font-pixel);
  font-size: 11px;
  padding: 5px 10px;
  cursor: pointer;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
}
.oj-recent-toggle:hover {
  background: var(--pyt-color-warning-bg);
}

/* 「最近题」可收纳侧栏 */
.oj-recent {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: rgba(255, 249, 234, 0.96);
  border: 2px solid var(--pyt-color-border);
  border-radius: 8px;
  box-shadow: 0 4px 0 rgba(74, 54, 29, 0.32), 0 10px 28px rgba(60, 41, 18, 0.14);
}
.oj-recent__head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(74, 54, 29, 0.22);
  background: rgba(255, 252, 242, 0.82);
}
.oj-recent__head h3 {
  margin: 0;
  font-family: var(--pyt-font-read);
  font-size: 14px;
}
.oj-recent__close {
  width: 26px;
  height: 26px;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
}
.oj-history {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  list-style: none;
  padding: 10px;
  margin: 0;
  display: grid;
  gap: 6px;
  align-content: start;
}
.oj-history__item {
  padding: 7px 9px;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
}
.oj-history__item.is-passed {
  border-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
}
.oj-history__item.is-failed {
  border-color: var(--pyt-color-danger);
  background: var(--pyt-color-danger-bg);
}
.oj-history__item header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}
.oj-history__kc {
  margin-left: auto;
  font-size: 10px;
  color: var(--pyt-color-info);
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-info);
  border-radius: var(--pyt-radius-sm);
  padding: 1px 6px;
}
.oj-history__item small {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  color: var(--pyt-color-text-muted);
}
.oj-history__empty {
  font-size: 12px;
  color: var(--pyt-color-text-muted);
  text-align: center;
  padding: 18px 8px;
}

@media (max-width: 1024px) {
  .endless-oj,
  .endless-oj--two,
  .endless-oj--two.endless-oj--recent,
  .endless-oj--single,
  .endless-oj--single.endless-oj--recent {
    grid-template-columns: 1fr;
    grid-auto-rows: minmax(220px, auto);
    overflow-y: auto;
  }
  .oj-card {
    height: auto;
    min-height: 280px;
  }
}
@media (max-width: 900px) {
  .endless-head {
    grid-template-columns: auto 1fr;
  }
  .endless-stats {
    grid-column: 1 / span 2;
    justify-content: space-around;
    width: 100%;
  }
}
</style>
