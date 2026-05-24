<script setup lang="ts">
/**
 * R-13 §5 Phase 4：章末 BOSS 副本页。
 *
 * 布局：
 *   - 顶部 stage 进度（1..4）+ 计时器 + 章节 boss 名 + 退出按钮；
 *   - 左侧：题目 + Pyodide 代码编辑器（textarea 就够）+ 提示展开；
 *   - 右侧：Boss 像素动画卡片（CSS draw + 摇晃）+ 上次结果摘要 + 操作按钮。
 *
 * 与 useBossSession 对接：
 *   - mounted 时调 startStage（stage 1 自动开始）；
 *   - 编辑器 v-model code；点击 "提交答案" 触发 submit；
 *   - 通过后 stage++ 自动进入下一阶段；4 全胜 → victory；
 *   - 失败时显示「再试一次」+ 提示（按 difficulty 给）。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import BossSprite from '@/components/boss/BossSprite.vue';
import { useBossSession } from '@/composables/useBossSession';
import { findBossByChapter } from '@/data/mainQuests';

const props = defineProps<{ chapter: number }>();

const router = useRouter();
const session = useBossSession({ chapter: props.chapter });
const boss = findBossByChapter(props.chapter);

let secondTimer: number | null = null;
const exitConfirm = ref<boolean>(false);

onMounted(async () => {
  if (!boss) {
    session.errorMessage.value = `未找到第 ${props.chapter} 章 boss 配置`;
    session.phase.value = 'error';
    return;
  }
  await session.startStage();
  if (typeof window !== 'undefined') {
    secondTimer = window.setInterval(() => session.tickSecond(), 1000);
  }
});

onBeforeUnmount(() => {
  if (secondTimer !== null) {
    window.clearInterval(secondTimer);
    secondTimer = null;
  }
});

function isSubmitDisabled(): boolean {
  const p = session.phase.value;
  return p === 'evaluating' || p === 'loading' || p === 'victory' || p === 'error';
}

async function onSubmit(): Promise<void> {
  const current = session.phase.value;
  if (current !== 'playing' && current !== 'failed') return;
  if (current === 'failed') {
    session.phase.value = 'playing';
  }
  await session.submit();
}

async function onAdvance(): Promise<void> {
  await session.advanceStage();
}

async function onRetry(): Promise<void> {
  session.phase.value = 'playing';
  session.lastResult.value = null;
}

function onExit(): void {
  if (exitConfirm.value) {
    void router.push('/world');
    return;
  }
  exitConfirm.value = true;
  window.setTimeout(() => {
    exitConfirm.value = false;
  }, 3000);
}

function pickHint(): string {
  const p = session.problem.value;
  if (!p) return '';
  if (p.hints.length === 0) {
    return '题目自带提示已用完，去工坊为该 KC 生成 HANDOUT。';
  }
  const idx = Math.min(p.hints.length - 1, session.clearedStages.value);
  return p.hints[idx] ?? '';
}

watch(
  () => session.phase.value,
  (next) => {
    if (next === 'victory' && typeof window !== 'undefined') {
      window.setTimeout(() => {
        void router.push('/world');
      }, 4000);
    }
  },
);

const bossSpritePhase = computed<'idle' | 'hurt' | 'angry' | 'victory'>(() => {
  const phase = session.phase.value;
  if (phase === 'passed' || phase === 'victory') return 'hurt';
  if (phase === 'failed' || phase === 'evaluating') return 'angry';
  return 'idle';
});
</script>

<template>
  <AppLayout>
    <section
      class="boss-page"
      data-testid="boss-page"
    >
      <header class="boss-head">
        <button
          type="button"
          class="boss-exit"
          data-testid="boss-exit"
          @click="onExit"
        >
          {{ exitConfirm ? '再点确认退出' : '退出副本' }}
        </button>
        <div class="boss-title">
          <span class="boss-chapter">第 {{ chapter }} 章 BOSS</span>
          <span class="boss-name">{{ boss?.name ?? '???' }}</span>
        </div>
        <div
          class="boss-stage-bar"
          data-testid="boss-stage-bar"
        >
          <span
            v-for="s in 4"
            :key="s"
            class="boss-stage-step"
            :class="{
              'boss-stage-step--current': s === session.stage.value,
              'boss-stage-step--cleared': s <= session.clearedStages.value,
            }"
            :data-testid="`boss-stage-${s}`"
          >Stage {{ s }}</span>
        </div>
        <div
          class="boss-timer"
          data-testid="boss-timer"
        >
          ⏱ {{ session.elapsedSec.value }}s
        </div>
      </header>

      <div
        v-if="session.phase.value === 'loading'"
        class="boss-state"
        data-testid="boss-loading"
      >
        正在生成第 {{ session.stage.value }} 阶段题目…
      </div>
      <div
        v-else-if="session.phase.value === 'error'"
        class="boss-state boss-state--error"
        data-testid="boss-error"
      >
        副本出错：{{ session.errorMessage.value }}
        <button
          type="button"
          class="boss-btn"
          @click="session.startStage"
        >
          重试
        </button>
      </div>
      <div
        v-else-if="session.phase.value === 'victory'"
        class="boss-state boss-state--victory"
        data-testid="boss-victory"
      >
        <h2>★ 全 4 阶段通关！</h2>
        <p>
          解锁奖励：
          <span
            v-for="r in boss?.rewards ?? []"
            :key="`${r.kind}-${r.ref ?? r.amount}`"
            class="boss-reward"
          >
            {{ r.kind }} × {{ r.amount }}{{ r.ref ? ` (${r.ref})` : '' }}
          </span>
        </p>
        <p class="boss-storyboard">
          {{ boss?.storyboardCue }}
        </p>
        <p class="boss-tip">
          4 秒后自动返回小镇…
        </p>
      </div>
      <div
        v-else-if="session.problem.value"
        class="boss-body"
      >
        <article
          class="boss-problem"
          data-testid="boss-problem"
        >
          <h2 class="boss-problem__title">
            难度 {{ session.problem.value.difficulty }} · KC {{ session.problem.value.kcCode }}
            <span
              v-if="session.problem.value.aiGenerated"
              class="badge badge--ai"
            >AI · 五重审查</span>
            <span
              v-else
              class="badge badge--golden"
            >Golden 兜底</span>
          </h2>
          <pre class="boss-problem__statement">{{ session.problem.value.statement }}</pre>
          <details class="boss-sample">
            <summary>样例输入 / 输出</summary>
            <div class="boss-sample__pair">
              <pre>{{ session.problem.value.sampleInput }}</pre>
              <pre>{{ session.problem.value.sampleOutput }}</pre>
            </div>
          </details>
          <details
            v-if="pickHint()"
            class="boss-sample"
          >
            <summary>提示</summary>
            <p>{{ pickHint() }}</p>
          </details>
        </article>
        <article class="boss-editor">
          <label class="boss-editor__label">
            <span>你的代码（Python 3）</span>
            <textarea
              v-model="session.code.value"
              data-testid="boss-code-input"
              placeholder="# 在这里写 Python 代码，stdout 与样例期望一致即可通过本阶段"
              rows="14"
            />
          </label>
          <div class="boss-editor__bar">
            <button
              type="button"
              class="boss-btn boss-btn--primary"
              data-testid="boss-submit"
              :disabled="isSubmitDisabled()"
              @click="onSubmit"
            >
              {{ session.phase.value === 'evaluating' ? '评测中…' : '提交答案' }}
            </button>
            <button
              v-if="session.phase.value === 'passed'"
              type="button"
              class="boss-btn boss-btn--next"
              data-testid="boss-next"
              @click="onAdvance"
            >
              下一阶段
            </button>
            <button
              v-if="session.phase.value === 'failed'"
              type="button"
              class="boss-btn"
              data-testid="boss-retry"
              @click="onRetry"
            >
              再来一次
            </button>
          </div>
          <div
            v-if="session.lastResult.value"
            class="boss-result"
            :class="{
              'boss-result--passed': session.lastResult.value.passed,
              'boss-result--failed': !session.lastResult.value.passed,
            }"
            data-testid="boss-result"
          >
            <p>
              <strong>
                {{ session.lastResult.value.passed ? '通过' : '未通过' }}
              </strong>
              · 通过用例 {{ session.lastResult.value.passedTestCases }}/{{ session.lastResult.value.totalTestCases }}
              · 分数 {{ session.lastResult.value.score }}
            </p>
            <p>{{ session.lastResult.value.feedback }}</p>
            <p
              v-if="session.lastResult.value.failureFirstHint"
              class="boss-hint"
            >
              提示：{{ session.lastResult.value.failureFirstHint }}
            </p>
          </div>
        </article>
        <article class="boss-sprite">
          <BossSprite
            :chapter="chapter"
            :phase="bossSpritePhase"
            :size="128"
          />
          <p class="boss-sprite__stage">
            Stage {{ session.stage.value }} / 4
          </p>
          <p class="boss-sprite__hp">
            HP {{ Math.max(0, 4 - session.clearedStages.value) }}/4
          </p>
        </article>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.boss-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  min-height: calc(100dvh - 56px);
  background: radial-gradient(ellipse at center, #2c1408 0%, #0a0500 80%);
  color: #fff7e6;
  font-family: 'PythontopiaPixel', 'PingFang SC', monospace;
}
.boss-head {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  background: rgba(28, 14, 4, 0.75);
  border: 3px solid #b3402a;
  box-shadow: 0 0 12px rgba(255, 80, 40, 0.4);
}
.boss-exit {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 10px;
  background: #6e4f1d;
  color: #fff7e6;
  border: 2px solid #4d3613;
  cursor: pointer;
}
.boss-title {
  display: flex;
  flex-direction: column;
}
.boss-chapter {
  font-size: 11px;
  color: #f1c453;
}
.boss-name {
  font-size: 16px;
  font-weight: 700;
}
.boss-stage-bar {
  display: flex;
  gap: 4px;
}
.boss-stage-step {
  padding: 2px 8px;
  font-size: 11px;
  border: 2px solid #6e4f1d;
  color: #fff7e6;
  background: rgba(110, 79, 29, 0.4);
  border-radius: 4px;
}
.boss-stage-step--current {
  border-color: #f1c453;
  background: rgba(241, 196, 83, 0.35);
}
.boss-stage-step--cleared {
  background: rgba(120, 200, 100, 0.45);
  border-color: #6bbf59;
  text-decoration: line-through;
}
.boss-timer {
  font-size: 13px;
  color: #f1c453;
}
.boss-state {
  flex: 1 1 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  font-size: 18px;
  color: #fff7e6;
}
.boss-state--error {
  color: #ff7466;
}
.boss-state--victory h2 {
  font-size: 28px;
  color: #f1c453;
}
.boss-reward {
  display: inline-block;
  padding: 2px 8px;
  margin: 0 4px;
  background: rgba(241, 196, 83, 0.25);
  border: 1px solid #f1c453;
  border-radius: 4px;
}
.boss-storyboard {
  font-style: italic;
  color: #b8d99a;
}
.boss-tip {
  color: rgba(255, 247, 230, 0.6);
  font-size: 12px;
}
.boss-body {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: 1.2fr 1.2fr 0.6fr;
  gap: 12px;
  min-height: 0;
}
.boss-problem,
.boss-editor,
.boss-sprite {
  background: rgba(15, 8, 2, 0.75);
  border: 2px solid #4d3613;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
}
.boss-problem__title {
  margin: 0;
  font-size: 13px;
  color: #f1c453;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.boss-problem__statement {
  margin: 0;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  color: #fff7e6;
}
.badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
}
.badge--ai {
  background: rgba(120, 220, 120, 0.25);
  border: 1px solid #6bbf59;
  color: #b8d99a;
}
.badge--golden {
  background: rgba(241, 196, 83, 0.2);
  border: 1px solid #f1c453;
  color: #f1c453;
}
.boss-sample {
  background: rgba(0, 0, 0, 0.35);
  padding: 6px 10px;
  border: 1px solid #6e4f1d;
}
.boss-sample summary {
  cursor: pointer;
  font-size: 12px;
  color: #f1c453;
}
.boss-sample__pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.boss-sample__pair pre {
  margin: 0;
  font-size: 12px;
  background: #1a0e04;
  padding: 6px;
  border: 1px solid #4d3613;
  color: #fff;
}
.boss-editor__label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #f1c453;
}
.boss-editor__label textarea {
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  background: #110a04;
  color: #f6f1e6;
  border: 2px solid #4d3613;
  padding: 8px;
  resize: vertical;
  min-height: 220px;
}
.boss-editor__bar {
  display: flex;
  gap: 8px;
}
.boss-btn {
  font-family: inherit;
  font-size: 12px;
  padding: 6px 14px;
  background: #c95e3a;
  color: #fff7e6;
  border: 2px solid #5a1a08;
  cursor: pointer;
  box-shadow: 0 2px 0 #5a1a08;
}
.boss-btn--primary {
  background: #f1c453;
  color: #2a1a08;
  border-color: #6e4f1d;
  box-shadow: 0 2px 0 #6e4f1d;
}
.boss-btn--next {
  background: #6bbf59;
  border-color: #2d5b1b;
  box-shadow: 0 2px 0 #2d5b1b;
}
.boss-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.boss-btn:active:not(:disabled) {
  transform: translateY(1px);
  box-shadow: none;
}
.boss-result {
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid #4d3613;
}
.boss-result--passed {
  border-color: #6bbf59;
  background: rgba(60, 140, 50, 0.25);
}
.boss-result--failed {
  border-color: #b3402a;
  background: rgba(140, 60, 30, 0.25);
}
.boss-result p {
  margin: 2px 0;
}
.boss-hint {
  font-style: italic;
  color: #f1c453;
}
.boss-sprite {
  align-items: center;
  text-align: center;
  gap: 8px;
}
.boss-sprite__stage {
  margin: 0;
  color: #f1c453;
}
.boss-sprite__hp {
  margin: 0;
  color: #ff7466;
}
@media (max-width: 1024px) {
  .boss-body {
    grid-template-columns: 1fr;
  }
}
</style>
