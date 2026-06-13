<script setup lang="ts">
/**
 * 代码剧场页（机关广场入口）——「只有游戏能做」的具身执行可视化 MVP 入口。
 *
 * 学生读 NPC 真实需求 → 写/改真实 Python → 运行：经 codeTheater 管线把执行轨迹整形为
 * 「执行步骤视图」+ 用「世界条件」（每块旱地是否浇到）替代隐藏断言给出可见反馈。
 * 若当前存在 Phaser 单例（如从 /world 进入），同时驱动 CodeTheaterScene 让傀儡走代码（渐进增强、
 * 守卫式 no-op）。Pyodide 预览仅用于「看执行 / 学习预览」，掌握度仍以服务端判题为准。
 */
import { computed, ref } from 'vue';
import PythonCodeEditor from '@/components/training/PythonCodeEditor.vue';
import { runCodeForQuest, type QuestRunResult } from '@/services/codeTheater/pipeline';
import { TraceExecutionError } from '@/services/codeTheater/trace';
import { WATER_DRY_FIELDS_QUEST } from '@/data/codeTheater/quests';
import type { TraceEvent } from '@/services/codeTheater/types';

const quest = WATER_DRY_FIELDS_QUEST;

const code = ref<string>(quest.starterCode ?? '');
const running = ref(false);
const errorMsg = ref<string>('');
const result = ref<QuestRunResult | null>(null);

const worldGoalTargets = computed<readonly number[]>(() =>
  quest.worldGoal.kind === 'coverage' ? quest.worldGoal.targets : [],
);

/** 把一条执行事件翻译成一行人话「执行步骤」。 */
function describeEvent(event: TraceEvent): string {
  switch (event.kind) {
    case 'assign':
      return `赋值 ${event.varName} = ${event.value}`;
    case 'loop_iter':
      return `循环第 ${event.iter + 1} 轮${
        event.varName ? ` · ${event.varName} = ${event.value ?? ''}` : ''
      }`;
    case 'branch':
      return `条件 ${event.cond} → ${event.result ? '真' : '假'}`;
    case 'call':
      return `调用 ${event.funcName}(${event.args.join(', ')})`;
    case 'return':
      return `返回 ${event.value}`;
    case 'list_op':
      return `列表 ${event.target}.${event.op}${
        event.value !== undefined ? `（${event.value}）` : ''
      }`;
    case 'dict_op':
      return `字典 ${event.target}[${event.key}]`;
    case 'output':
      return `输出 ${event.text}`;
    case 'input':
      return `输入 ${event.value}`;
    case 'exception':
      return `⚠ 异常 ${event.excType}: ${event.message}`;
    case 'line':
    default:
      return '执行';
  }
}

async function run(): Promise<void> {
  if (running.value) return;
  running.value = true;
  errorMsg.value = '';
  try {
    const res = await runCodeForQuest(code.value, quest);
    result.value = res;
    // 渐进增强：存在 Phaser 单例时让傀儡在机关广场走代码；不存在则纯 DOM 视图已足够。
    try {
      const [{ getPhaserGame }, scene] = await Promise.all([
        import('@game/adapters/phaserGame'),
        import('@game/scenes/CodeTheaterScene'),
      ]);
      const game = getPhaserGame();
      if (game) {
        scene.ensureCodeTheaterSceneStarted(
          game as unknown as Parameters<typeof scene.ensureCodeTheaterSceneStarted>[0],
        );
        scene.loadCodeTheater(res.commands);
      }
    } catch {
      /* Phaser 不可用：DOM 执行视图已是完整学习反馈，忽略 */
    }
  } catch (err) {
    errorMsg.value =
      err instanceof TraceExecutionError
        ? err.message
        : '代码没能跑起来，检查一下语法再试一次。';
  } finally {
    running.value = false;
  }
}

function reset(): void {
  code.value = quest.starterCode ?? '';
  result.value = null;
  errorMsg.value = '';
}
</script>

<template>
  <div
    class="code-theater"
    data-testid="code-theater-page"
  >
    <header class="ct-head">
      <h1 class="ct-head__title">🎭 代码剧场 · 机关广场</h1>
      <p class="ct-head__sub">
        写真实 Python，看傀儡把「执行过程」一步步走给你看——循环、变量、分支不再是看不见的黑箱。
      </p>
    </header>

    <section
      class="ct-quest"
      data-testid="ct-quest"
    >
      <div class="ct-quest__title">
        <span class="ct-quest__icon">🪣</span>
        <span>{{ quest.title }}</span>
        <span class="ct-quest__kc">{{ quest.kcCodes.join(' / ') }}</span>
      </div>
      <p class="ct-quest__prompt">
        {{ quest.prompt }}
      </p>
    </section>

    <div class="ct-body">
      <div class="ct-editor">
        <div class="ct-editor__bar">
          <span class="ct-editor__label">你的代码</span>
          <div class="ct-editor__actions">
            <button
              type="button"
              class="ct-btn ct-btn--ghost"
              :disabled="running"
              @click="reset"
            >
              重置
            </button>
            <button
              type="button"
              class="ct-btn ct-btn--primary"
              data-testid="ct-run"
              :disabled="running"
              @click="run"
            >
              {{ running ? '运行中…' : '▶ 运行' }}
            </button>
          </div>
        </div>
        <div class="ct-editor__host">
          <PythonCodeEditor v-model="code" />
        </div>
      </div>

      <div class="ct-result">
        <p
          v-if="errorMsg"
          class="ct-error"
          data-testid="ct-error"
        >
          {{ errorMsg }}
        </p>

        <template v-if="result">
          <div
            class="ct-world"
            :class="result.world.achieved ? 'ct-world--ok' : 'ct-world--miss'"
            data-testid="ct-world"
          >
            <div class="ct-world__head">
              {{ result.world.achieved ? '✅ 世界目标达成' : '🌵 还没全部完成' }}
            </div>
            <p class="ct-world__feedback">
              {{ result.world.feedback }}
            </p>
            <div
              v-if="worldGoalTargets.length"
              class="ct-fields"
            >
              <span
                v-for="target in worldGoalTargets"
                :key="target"
                class="ct-field"
                :class="
                  result.world.satisfied.includes(target)
                    ? 'ct-field--watered'
                    : 'ct-field--dry'
                "
                :title="
                  result.world.satisfied.includes(target)
                    ? `第 ${target} 块：已浇透`
                    : `第 ${target} 块：还干着`
                "
              >
                {{ result.world.satisfied.includes(target) ? '🌿' : '🟫' }}
                第{{ target }}块
              </span>
            </div>
          </div>

          <div
            class="ct-steps"
            data-testid="ct-steps"
          >
            <div class="ct-steps__head">
              执行过程（{{ result.trace.stepCount }} 步{{
                result.trace.truncated ? ' · 已按步数预算截断，疑似死循环' : ''
              }}）
            </div>
            <ol class="ct-steps__list">
              <li
                v-for="event in result.trace.events"
                :key="event.step"
                class="ct-step"
                :class="{ 'ct-step--exc': event.kind === 'exception' }"
              >
                <span class="ct-step__line">L{{ event.line }}</span>
                <span class="ct-step__desc">{{ describeEvent(event) }}</span>
              </li>
            </ol>
          </div>
        </template>

        <p
          v-else-if="!errorMsg"
          class="ct-empty"
        >
          点「▶ 运行」，让傀儡按你的代码去浇地——看它走对了几块、漏了哪块。
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.code-theater {
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 22px 32px;
  font-family: var(--pyt-font-pixel), monospace;
  color: #2f2412;
  background: var(--pyt-color-bg-primary, #fbf7ec);
}
.ct-head__title {
  margin: 0 0 4px;
  font-size: 20px;
  color: #7a5210;
}
.ct-head__sub {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--pyt-color-text-muted, #6b5836);
}
.ct-quest {
  border: 2px solid #c9a86b;
  border-radius: 8px;
  background: #fff8df;
  padding: 12px 14px;
  margin-bottom: 14px;
}
.ct-quest__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: #5a3d12;
}
.ct-quest__kc {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
  background: #d6ead1;
  color: #2d5b1b;
  border: 1px solid #4d6e1d;
}
.ct-quest__prompt {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.7;
}
.ct-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}
@media (max-width: 900px) {
  .ct-body {
    grid-template-columns: 1fr;
  }
}
.ct-editor {
  display: flex;
  flex-direction: column;
  min-height: 360px;
  border: 2px solid #8c6a23;
  border-radius: 8px;
  overflow: hidden;
  background: #111827;
}
.ct-editor__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: #1b2433;
  border-bottom: 1px solid rgba(148, 163, 184, 0.25);
}
.ct-editor__label {
  font-size: 12px;
  color: #cdd9e8;
}
.ct-editor__actions {
  display: flex;
  gap: 8px;
}
.ct-editor__host {
  flex: 1 1 auto;
  min-height: 0;
}
.ct-btn {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 14px;
  border-radius: 6px;
  cursor: pointer;
  border: 2px solid #8c6a23;
}
.ct-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.ct-btn--primary {
  background: #f7d774;
  color: #2a1a08;
  box-shadow: 0 2px 0 #8c6a23;
}
.ct-btn--primary:hover:not(:disabled) {
  background: #fde48a;
}
.ct-btn--ghost {
  background: transparent;
  color: #cdd9e8;
  border-color: rgba(148, 163, 184, 0.5);
}
.ct-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}
.ct-error {
  margin: 0;
  padding: 10px 12px;
  font-size: 13px;
  border-radius: 6px;
  background: #f8d7d0;
  border: 1px solid var(--pyt-color-danger, #c0392b);
  color: #b3402a;
}
.ct-world {
  padding: 12px 14px;
  border-radius: 8px;
  border: 2px solid;
}
.ct-world--ok {
  background: #e3f3dd;
  border-color: #4d6e1d;
}
.ct-world--miss {
  background: #fff3cd;
  border-color: #d4af37;
}
.ct-world__head {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 4px;
}
.ct-world__feedback {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}
.ct-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.ct-field {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 6px;
  border: 1px solid;
}
.ct-field--watered {
  background: #d6ead1;
  border-color: #4d6e1d;
  color: #2d5b1b;
}
.ct-field--dry {
  background: #efe1c4;
  border-color: #b08a3e;
  color: #6b4e16;
}
.ct-steps {
  border: 1px solid #c9a86b;
  border-radius: 8px;
  background: #fffdf6;
  overflow: hidden;
}
.ct-steps__head {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 700;
  color: #5a3d12;
  background: #f1e8d2;
  border-bottom: 1px solid #c9a86b;
}
.ct-steps__list {
  margin: 0;
  padding: 6px 12px 10px 30px;
  max-height: 320px;
  overflow-y: auto;
}
.ct-step {
  font-size: 12px;
  line-height: 1.8;
}
.ct-step--exc {
  color: #b3402a;
  font-weight: 700;
}
.ct-step__line {
  display: inline-block;
  min-width: 34px;
  color: #8a7a55;
}
.ct-step__desc {
  color: #2f2412;
}
.ct-empty {
  margin: 0;
  padding: 18px 12px;
  font-size: 13px;
  color: var(--pyt-color-text-muted, #6b5836);
}
</style>
