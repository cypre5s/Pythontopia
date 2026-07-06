<script setup lang="ts">
/**
 * 代码剧场页（机关广场入口）——「只有游戏能做」的具身执行可视化 MVP 入口。
 *
 * 学生读 NPC 真实需求 → 写/改真实 Python → 运行：经 codeTheater 管线把执行轨迹整形为
 * 「执行步骤视图」+ 用「世界条件」（每个目标是否被作用到）替代隐藏断言给出可见反馈。
 * 若当前存在 Phaser 单例（如从 /world 进入），同时驱动 CodeTheaterScene 让傀儡走代码（渐进增强、
 * 守卫式 no-op）。Pyodide 预览仅用于「看执行 / 学习预览」，掌握度仍以服务端判题为准。
 */
import { computed, onMounted, ref } from 'vue';
import PythonCodeEditor from '@/components/training/PythonCodeEditor.vue';
import TheaterStageCanvas from '@/components/codeTheater/TheaterStageCanvas.vue';
import {
  runCodeForQuest,
  runFreeVisualization,
  type QuestRunResult,
} from '@/services/codeTheater/pipeline';
import { TraceExecutionError } from '@/services/codeTheater/trace';
import { CODE_THEATER_QUESTS, type CodeTheaterQuest } from '@/data/codeTheater/quests';
import type { TraceEvent } from '@/services/codeTheater/types';
import { useCodeTheaterStore } from '@stores/codeTheater';

const quests = CODE_THEATER_QUESTS;
const activeQuestId = ref<string>(quests[0]?.id ?? '');
const activeQuest = computed<CodeTheaterQuest>(() => {
  const found = quests.find((q) => q.id === activeQuestId.value) ?? quests[0];
  if (!found) throw new Error('代码剧场任务清单为空');
  return found;
});

const code = ref<string>(activeQuest.value.starterCode ?? '');
const running = ref(false);
const errorMsg = ref<string>('');
const result = ref<QuestRunResult | null>(null);

// 自由可视化模式：从训练场带着「学生这次提交的代码 + 调试输入」进来，只看执行、不绑世界目标。
const ctStore = useCodeTheaterStore();
const freeMode = ref(false);
const freeLabel = ref('');
const freeStdin = ref('');

// coverage 与 sequence 两种世界目标都带 targets/targetNoun/unit/actionVerb，目标清单 UI 通用。
const targetGoal = computed(() => {
  const goal = activeQuest.value.worldGoal;
  return goal.kind === 'coverage' || goal.kind === 'sequence' ? goal : null;
});
const worldGoalTargets = computed<readonly number[]>(() => targetGoal.value?.targets ?? []);
const goalUnit = computed<string>(() => targetGoal.value?.unit ?? '个');
const goalNoun = computed<string>(() => targetGoal.value?.targetNoun ?? '');
const goalVerb = computed<string>(
  () => targetGoal.value?.actionVerb ?? targetGoal.value?.actionName ?? '',
);

/** 切换任务：重置代码为该任务起手代码、清空上轮结果。 */
function selectQuest(id: string): void {
  if (id === activeQuestId.value || running.value) return;
  activeQuestId.value = id;
  code.value = activeQuest.value.starterCode ?? '';
  result.value = null;
  errorMsg.value = '';
}

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

/** 渐进增强：存在 Phaser 单例时让傀儡在机关广场走代码；不存在则纯 DOM 视图已足够。 */
async function driveStage(
  commands: QuestRunResult['commands'],
  trace: QuestRunResult['trace'],
): Promise<void> {
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
      // 关键：把 Trace 一并喂给舞台——脚本梯才有逐行源码、死循环截断才会触发「傀儡原地踱步」。
      scene.loadCodeTheater(commands, { trace });
    }
  } catch {
    /* Phaser 不可用：DOM 执行视图已是完整学习反馈，忽略 */
  }
}

async function run(): Promise<void> {
  if (running.value) return;
  running.value = true;
  errorMsg.value = '';
  try {
    if (freeMode.value) {
      const res = await runFreeVisualization(code.value, freeStdin.value);
      // 自由模式无世界目标：用中性 world 占位，模板里世界面板对自由模式隐藏。
      result.value = {
        trace: res.trace,
        commands: res.commands,
        world: { achieved: true, satisfied: [], unmet: [], outOfRange: [], feedback: '' },
      };
      await driveStage(res.commands, res.trace);
    } else {
      const res = await runCodeForQuest(code.value, activeQuest.value);
      result.value = res;
      await driveStage(res.commands, res.trace);
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
  if (freeMode.value) {
    // 自由模式保留带进来的代码，只清运行结果，便于改完再跑。
    result.value = null;
    errorMsg.value = '';
    return;
  }
  code.value = activeQuest.value.starterCode ?? '';
  result.value = null;
  errorMsg.value = '';
}

onMounted(() => {
  // 训练场 WA/RE「去代码剧场看执行」交接：有待消费载荷则进自由模式并自动跑一遍。
  const pending = ctStore.consumeFreeRun();
  if (!pending) return;
  freeMode.value = true;
  freeLabel.value = pending.label;
  freeStdin.value = pending.stdin;
  code.value = pending.code;
  void run();
});
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
      v-if="freeMode"
      class="ct-free"
      data-testid="ct-free-banner"
    >
      <span
        class="ct-free__icon"
        aria-hidden="true"
      >🔍</span>
      <div class="ct-free__body">
        <strong>自由可视化 · 看你自己的代码怎么跑</strong>
        <span>{{ freeLabel ? `来自训练场：${freeLabel}` : '来自训练场的提交' }}——学习预览不判分；可在下方改代码后再「▶ 运行」。</span>
      </div>
      <RouterLink
        class="ct-free__back"
        to="/training"
      >
        ← 返回训练场
      </RouterLink>
    </section>

    <nav
      v-if="!freeMode"
      class="ct-quest-tabs"
      data-testid="ct-quest-tabs"
      aria-label="代码剧场任务"
    >
      <button
        v-for="q in quests"
        :key="q.id"
        type="button"
        class="ct-tab"
        :class="{ 'ct-tab--active': q.id === activeQuestId }"
        :aria-pressed="q.id === activeQuestId"
        :data-testid="`ct-tab-${q.id}`"
        :disabled="running"
        @click="selectQuest(q.id)"
      >
        {{ q.title }}
      </button>
    </nav>

    <section
      v-if="!freeMode"
      class="ct-quest"
      data-testid="ct-quest"
    >
      <div class="ct-quest__title">
        <span class="ct-quest__icon">🎯</span>
        <span>{{ activeQuest.title }}</span>
        <span class="ct-quest__kc">{{ activeQuest.kcCodes.join(' / ') }}</span>
      </div>
      <p class="ct-quest__prompt">
        {{ activeQuest.prompt }}
      </p>
    </section>

    <section
      class="ct-stage"
      data-testid="ct-stage"
      aria-label="代码剧场舞台：傀儡走代码"
    >
      <TheaterStageCanvas />
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
              :aria-busy="running"
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
          role="alert"
        >
          {{ errorMsg }}
        </p>

        <template v-if="result">
          <div
            v-if="!freeMode"
            class="ct-world"
            :class="result.world.achieved ? 'ct-world--ok' : 'ct-world--miss'"
            data-testid="ct-world"
            role="status"
            aria-live="polite"
          >
            <div class="ct-world__head">
              {{ result.world.achieved ? '✅ 世界目标达成' : '🚧 还没全部完成' }}
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
                    ? 'ct-field--done'
                    : 'ct-field--pending'
                "
                :aria-label="
                  result.world.satisfied.includes(target)
                    ? `第 ${target} ${goalUnit}${goalNoun}：已${goalVerb}`
                    : `第 ${target} ${goalUnit}${goalNoun}：还没${goalVerb}`
                "
              >
                <span aria-hidden="true">{{ result.world.satisfied.includes(target) ? '🟢' : '⚪' }}</span>
                第{{ target }}{{ goalUnit }}{{ goalNoun }}
              </span>
            </div>
            <p
              v-if="result.world.outOfRange.length"
              class="ct-world__waste"
              data-testid="ct-waste"
            >
              ⚠ 误触了不该碰的{{ goalNoun }}：{{
                result.world.outOfRange.map((t) => `第 ${t} ${goalUnit}${goalNoun}`).join('、')
              }}
            </p>
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
          点「▶ 运行」，让傀儡按你的代码去完成任务——看它做对了几个、漏了哪个。
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
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-primary);
}
.ct-head__title {
  margin: 0 0 4px;
  font-size: 20px;
  color: var(--pyt-color-primary);
}
.ct-head__sub {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--pyt-color-text-muted);
}
.ct-free {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  padding: 10px 14px;
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
  background: var(--pyt-color-warning-bg);
}
.ct-free__icon {
  font-size: 22px;
  line-height: 1;
}
.ct-free__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.ct-free__body strong {
  font-size: 14px;
  color: var(--pyt-color-primary-dark);
}
.ct-free__body span {
  font-size: 12px;
  line-height: 1.5;
  color: var(--pyt-color-text-hint);
}
.ct-free__back {
  flex-shrink: 0;
  font-size: 12px;
  padding: 5px 12px;
  border-radius: var(--pyt-radius-lg);
  border: 2px solid var(--pyt-color-border-strong);
  background: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
  text-decoration: none;
}
.ct-free__back:hover {
  background: var(--pyt-color-gold-bright);
}
.ct-quest-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.ct-tab {
  font-family: inherit;
  font-size: 12px;
  padding: 5px 12px;
  border-radius: var(--pyt-radius-lg);
  cursor: pointer;
  border: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
}
.ct-tab:hover:not(:disabled) {
  background: var(--pyt-color-warning-bg);
}
.ct-tab:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.ct-tab--active {
  background: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
  border-color: var(--pyt-color-border-strong);
  font-weight: 700;
}
.ct-quest {
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  background: var(--pyt-color-bg-soft);
  padding: 12px 14px;
  margin-bottom: 14px;
}
.ct-quest__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--pyt-color-primary-dark);
}
.ct-quest__kc {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: var(--pyt-radius-full);
  background: var(--pyt-color-success-bg);
  color: var(--pyt-color-success);
  border: 1px solid var(--pyt-color-success);
}
.ct-quest__prompt {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.7;
}
/* 舞台与编辑器外壳保留蓝黑暗色：与内嵌 CodeMirror 暗色编辑器配套（同 EndlessPage 的终端深色例外）。 */
.ct-stage {
  height: 360px;
  margin-bottom: 14px;
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
  overflow: hidden;
  background: #141821;
}
@media (max-width: 900px) {
  .ct-stage {
    height: 280px;
  }
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
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
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
  color: var(--pyt-color-text-on-dark);
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
  border-radius: var(--pyt-radius-lg);
  cursor: pointer;
  border: 2px solid var(--pyt-color-border-strong);
}
.ct-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.ct-btn--primary {
  background: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
  box-shadow: 0 2px 0 var(--pyt-color-border-strong);
}
.ct-btn--primary:hover:not(:disabled) {
  background: var(--pyt-color-gold-bright);
}
.ct-btn--ghost {
  background: transparent;
  color: var(--pyt-color-text-on-dark);
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
  border-radius: var(--pyt-radius-lg);
  background: var(--pyt-color-danger-bg);
  border: 1px solid var(--pyt-color-danger);
  color: var(--pyt-color-danger);
}
.ct-world {
  padding: 12px 14px;
  border-radius: var(--pyt-radius-lg);
  border: 2px solid;
}
.ct-world--ok {
  background: var(--pyt-color-success-bg);
  border-color: var(--pyt-color-success);
}
.ct-world--miss {
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-gold);
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
  border-radius: var(--pyt-radius-lg);
  border: 1px solid;
}
.ct-field--done {
  background: var(--pyt-color-success-bg);
  border-color: var(--pyt-color-success);
  color: var(--pyt-color-success);
}
.ct-field--pending {
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-warning);
  color: var(--pyt-color-text-hint);
}
.ct-world__waste {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--pyt-color-danger);
}
.ct-steps {
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  background: var(--pyt-color-bg-elevated);
  overflow: hidden;
}
.ct-steps__head {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-color-primary-dark);
  background: var(--pyt-color-bg-sunken);
  border-bottom: 1px solid var(--pyt-color-border);
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
  color: var(--pyt-color-danger);
  font-weight: 700;
}
.ct-step__line {
  display: inline-block;
  min-width: 34px;
  color: var(--pyt-color-text-muted);
}
.ct-step__desc {
  color: var(--pyt-color-text-primary);
}
.ct-empty {
  margin: 0;
  padding: 18px 12px;
  font-size: 13px;
  color: var(--pyt-color-text-muted);
}
</style>
