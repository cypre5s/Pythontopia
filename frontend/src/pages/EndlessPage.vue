<script setup lang="ts">
/**
 * R-13 §5 Phase 8 续：「无尽模式」页面。
 *
 * 设计目标（与闯关模式 BossPage 区分）：
 *   - 闯关模式 `/boss/:chapter`：4 阶段 + 章节 boss，主线推进；
 *   - 无尽模式 `/endless`：连续出题，自适应难度，玩家追求 streak / 总分。
 *
 * 数据：复用 graph /agents/boss/problem（含 5 重审查 + golden 兜底）+ /agents/boss/evaluate
 *       + 前端 Pyodide 二次校验。题目章节按 focusChapter 锁定，未锁定时随机。
 *
 * UI：单页布局：
 *   - 顶部 status bar：streak / total / 难度 / 当前章 / 失败率
 *   - 左：题面 + 提交 textarea
 *   - 右：最近 8 题历史 + 总览
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import BossSprite from '@/components/boss/BossSprite.vue';
import { KindRouter } from '@/components/arena';
import { useEndlessRun } from '@/composables/useEndlessRun';
import { MAIN_QUEST_BOSSES } from '@/data/mainQuests';
import { useArenaStore } from '@stores/arena';

const router = useRouter();
const route = useRoute();
const arena = useArenaStore();

// R-13 §-1 续：从 query 读 ?chapter & ?arenaLevel 让 EndlessPage 同时承担
// "随机刷题" 与 "ArenaPage 闯关塔单关入口" 两个角色。
// arenaLevel 形如 "1-3"，通关后回 ArenaPage 上方 markCleared。
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
const run = useEndlessRun({ focusChapter, focusKc, preferredKind });

watch(queryChapter, (next) => {
  focusChapter.value = next;
});
watch(queryKc, (next) => {
  focusKc.value = next;
});
watch(queryKind, (next) => {
  preferredKind.value = next;
});

// R-13 §-1 续：作为闯关塔单关入口时，通过 1 题即记 markCleared 并自动回 ArenaPage。
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

// R-13 §-1 续：让闯关塔单关入口显示题型中文名，让玩家直观感受到 9 种关卡的差异
// （而不是所有关卡走完全一样的 endless 随机抽题流程）。
const KIND_LABELS: Record<string, string> = {
  pdf_question: '📖 课件问答',
  fill_blank:   '▭ 代码填空',
  debug:        '⚠ 改错题',
  short_answer: '✎ 简答 (grounding 评分)',
  closed_loop:  '⟲ 调试闭环',
  dual_track:   '⇆ 双轨判题',
  multi_round:  '◉◉◉ 3 题组合',
  teach_back:   '🎙 教学反转',
  bug_mine:     '⛏ Bug 矿洞 (30s 限时)',
};
const kindLabel = computed<string>(() => {
  const kind = preferredKind.value;
  if (!kind) return '随机刷题';
  return KIND_LABELS[kind] ?? kind;
});

watch(
  () => focusChapter.value,
  (next) => {
    if (run.phase.value === 'idle' || run.phase.value === 'error') return;
    void next;
  },
);
</script>

<template>
  <AppLayout>
    <section
      class="endless-page"
      data-testid="endless-page"
    >
      <header class="endless-head">
        <button
          type="button"
          class="endless-btn endless-btn--exit"
          data-testid="endless-exit"
          @click="onExit"
        >
          ← 返回小镇
        </button>
        <h1 class="endless-title">
          {{ arenaLevelId ? `闯关塔 ${arenaLevelId} · ${kindLabel}` : '无尽闯关 · 连续出题' }}
        </h1>
        <div class="endless-stats">
          <span class="stat" data-testid="endless-streak">
            <span class="stat-label">连胜</span>
            <span class="stat-value">{{ run.streak.value }}</span>
          </span>
          <span class="stat">
            <span class="stat-label">最佳</span>
            <span class="stat-value">{{ run.bestStreak.value }}</span>
          </span>
          <span class="stat" data-testid="endless-total-score">
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
        <button
          v-if="run.phase.value === 'idle'"
          type="button"
          class="endless-btn endless-btn--primary"
          data-testid="endless-start"
          @click="onStartRun"
        >
          开始无尽
        </button>
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
      >
        正在抽题中…
      </div>

      <div
        v-else-if="run.phase.value === 'error'"
        class="endless-error"
      >
        出错：{{ run.errorMessage.value }}
        <button
          type="button"
          class="endless-btn"
          @click="onStartRun"
        >
          重新开始
        </button>
      </div>

      <div
        v-else
        class="endless-body"
        data-testid="endless-body"
      >
        <article class="endless-problem">
          <header class="endless-problem__head">
            <span class="chapter-pill">{{ currentChapterName }}</span>
            <span class="kc-pill">KC {{ run.problem.value?.kcCode }}</span>
            <span class="diff-pill">难度 {{ run.problem.value?.difficulty }} / 5</span>
            <span
              v-if="run.problem.value?.aiGenerated"
              class="badge badge--ai"
            >AI · 5 重审查</span>
            <span
              v-else
              class="badge badge--golden"
            >Golden 兜底</span>
            <span class="timer">⏱ {{ run.elapsedSec.value }}s</span>
          </header>

          <KindRouter
            v-if="preferredKind && run.problem.value"
            v-model:code="run.code.value"
            :kind="preferredKind"
            :problem="run.problem.value"
            @timeout="onSubmit"
          >
            <pre class="endless-problem__statement">{{ run.problem.value?.statement }}</pre>
          </KindRouter>

          <template v-else>
            <pre class="endless-problem__statement">{{ run.problem.value?.statement }}</pre>
            <details
              v-if="run.problem.value?.sampleInput || run.problem.value?.sampleOutput"
              class="endless-sample"
            >
              <summary>样例输入 / 输出</summary>
              <div class="endless-sample__pair">
                <pre>{{ run.problem.value?.sampleInput }}</pre>
                <pre>{{ run.problem.value?.sampleOutput }}</pre>
              </div>
            </details>

            <label class="endless-editor">
              <span>你的代码（Python 3）</span>
              <textarea
                v-model="run.code.value"
                data-testid="endless-code"
                rows="10"
                placeholder="# 在这里写 Python 代码，stdout 等于样例期望即可"
              />
            </label>
          </template>
          <div class="endless-actions">
            <button
              type="button"
              class="endless-btn endless-btn--primary"
              data-testid="endless-submit"
              :disabled="submitDisabled"
              @click="onSubmit"
            >
              {{ run.phase.value === 'evaluating' ? '评测中…' : '提交答案' }}
            </button>
            <button
              v-if="showNextButton"
              type="button"
              class="endless-btn endless-btn--next"
              data-testid="endless-next"
              @click="onNext"
            >
              {{ run.phase.value === 'passed' ? '下一题（难度提升）' : '继续挑战（难度下调）' }}
            </button>
          </div>
          <div
            v-if="run.lastResult.value"
            class="endless-result"
            :class="{
              'endless-result--passed': run.lastResult.value.passed,
              'endless-result--failed': !run.lastResult.value.passed,
            }"
            data-testid="endless-result"
          >
            <strong>{{ run.lastResult.value.passed ? '通过' : '未通过' }}</strong>
            · 通过用例 {{ run.lastResult.value.passedTestCases }} / {{ run.lastResult.value.totalTestCases }}
            · 本题加分 {{ run.lastResult.value.passed ? '+' + (run.history.value[0]?.score ?? 0) : '0' }}
            <p>{{ run.lastResult.value.feedback }}</p>
            <p
              v-if="run.lastResult.value.failureFirstHint"
              class="hint"
            >
              提示：{{ run.lastResult.value.failureFirstHint }}
            </p>
          </div>
        </article>

        <aside class="endless-side">
          <h3>最近 8 题</h3>
          <ul
            class="history-list"
            data-testid="endless-history"
          >
            <li
              v-for="(h, idx) in run.history.value.slice(0, 8)"
              :key="`${h.problemId}-${idx}`"
              :class="{
                'history-item': true,
                'history-item--passed': h.passed,
                'history-item--failed': !h.passed,
              }"
            >
              <header>
                <strong>第 {{ h.chapter }} 章 · 难 {{ h.difficulty }}</strong>
                <span class="badge">{{ h.kcCode }}</span>
              </header>
              <small>
                {{ h.passed ? '通过' : '失败' }} · +{{ h.score }} 分 · {{ h.elapsedSec }}s
              </small>
            </li>
          </ul>
        </aside>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.endless-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  min-height: calc(100dvh - 56px);
  background: radial-gradient(ellipse at top, #1a3a6e 0%, #0a0a1a 80%);
  color: #fff7e6;
  font-family: 'PythontopiaPixel', 'PingFang SC', monospace;
}
.endless-head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  background: rgba(15, 25, 45, 0.85);
  border: 3px solid #4a90e2;
  box-shadow: 0 0 12px rgba(74, 144, 226, 0.45);
}
.endless-title {
  margin: 0;
  font-size: 18px;
  color: #ffd166;
  text-shadow: 2px 2px 0 #1a0e04;
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
  color: #94a3b8;
}
.stat-value {
  font-size: 16px;
  color: #f1c453;
}
.endless-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  background: rgba(15, 25, 45, 0.6);
  border: 2px solid #4a90e2;
  border-radius: 4px;
}
.toolbar-field {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}
.toolbar-field select {
  font-family: inherit;
  background: #1a2a4a;
  color: #fff7e6;
  border: 2px solid #4a90e2;
  padding: 2px 6px;
}
.endless-btn {
  font-family: inherit;
  cursor: pointer;
  font-size: 13px;
  padding: 6px 14px;
  background: #1e3a8a;
  color: #fff7e6;
  border: 2px solid #4a90e2;
  border-radius: 4px;
  box-shadow: 0 2px 0 #1a2a6e;
}
.endless-btn--exit {
  background: #6e4f1d;
  border-color: #4d3613;
  box-shadow: 0 2px 0 #4d3613;
}
.endless-btn--primary {
  background: #f1c453;
  color: #2a1a08;
  border-color: #6e4f1d;
  box-shadow: 0 2px 0 #6e4f1d;
}
.endless-btn--next {
  background: #6bbf59;
  color: #1f3a14;
  border-color: #2d5b1b;
  box-shadow: 0 2px 0 #2d5b1b;
}
.endless-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}
.endless-btn:active:not(:disabled) {
  transform: translateY(1px);
  box-shadow: none;
}
.endless-rate {
  color: #f1c453;
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
}
.endless-error {
  color: #ff7466;
}
.endless-hint {
  color: rgba(255, 247, 230, 0.6);
  font-size: 12px;
}
.endless-body {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: 1.4fr 0.6fr;
  gap: 12px;
  min-height: 0;
}
.endless-problem,
.endless-side {
  background: rgba(15, 25, 45, 0.8);
  border: 2px solid #4a90e2;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: auto;
}
.endless-problem__head {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  font-size: 11px;
}
.chapter-pill,
.kc-pill,
.diff-pill,
.badge,
.timer {
  display: inline-block;
  padding: 2px 8px;
  background: rgba(74, 144, 226, 0.2);
  border: 1px solid #4a90e2;
  border-radius: 3px;
  font-size: 11px;
  color: #93c5fd;
}
.badge--ai {
  background: rgba(120, 220, 120, 0.25);
  border-color: #6bbf59;
  color: #b8d99a;
}
.badge--golden {
  background: rgba(241, 196, 83, 0.2);
  border-color: #f1c453;
  color: #f1c453;
}
.timer {
  margin-left: auto;
  background: rgba(241, 196, 83, 0.18);
  border-color: #f1c453;
  color: #f1c453;
}
.endless-problem__statement {
  margin: 0;
  padding: 10px;
  background: #0a1428;
  border: 1px solid #4a90e2;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.7;
  color: #e5e7eb;
}
.endless-sample {
  background: #0a1428;
  border: 1px solid #4a90e2;
  padding: 6px 10px;
}
.endless-sample summary {
  cursor: pointer;
  font-size: 12px;
  color: #93c5fd;
}
.endless-sample__pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 6px;
}
.endless-sample__pair pre {
  margin: 0;
  padding: 6px;
  background: #1a2a4a;
  border: 1px solid #4a90e2;
  font-size: 12px;
  color: #fff7e6;
}
.endless-editor {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}
.endless-editor textarea {
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  background: #0a1428;
  color: #f0f9ff;
  border: 2px solid #4a90e2;
  padding: 8px;
  resize: vertical;
  min-height: 160px;
}
.endless-actions {
  display: flex;
  gap: 8px;
}
.endless-result {
  padding: 8px 12px;
  border-radius: 3px;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid #4a90e2;
}
.endless-result--passed {
  background: rgba(60, 140, 50, 0.25);
  border-color: #6bbf59;
}
.endless-result--failed {
  background: rgba(140, 60, 30, 0.3);
  border-color: #b3402a;
}
.endless-result p {
  margin: 4px 0 0;
}
.hint {
  font-style: italic;
  color: #f1c453;
}
.endless-side h3 {
  margin: 0;
  color: #ffd166;
  font-size: 14px;
}
.history-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 6px;
}
.history-item {
  padding: 6px 8px;
  background: rgba(74, 144, 226, 0.1);
  border: 1px solid #4a90e2;
}
.history-item--passed {
  border-color: #6bbf59;
  background: rgba(107, 191, 89, 0.18);
}
.history-item--failed {
  border-color: #b3402a;
  background: rgba(179, 64, 42, 0.18);
}
.history-item header {
  display: flex;
  align-items: center;
  gap: 6px;
}
.history-item small {
  display: block;
  margin-top: 2px;
  color: #cbd5e1;
}

@media (max-width: 900px) {
  .endless-body {
    grid-template-columns: 1fr;
  }
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
