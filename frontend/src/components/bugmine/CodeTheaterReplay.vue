<script setup lang="ts">
/**
 * CodeTheaterReplay —— 错题矿洞「调试剧场」回放挂件（C-debug-theater 归属，新增、不动 bug-mine 既有逻辑）。
 *
 * 把一次失败提交的执行轨迹慢放一遍：定位发散 / 出错行 → 高亮 → 接 DebugAgent 三阶引导（换述→拆解→指方向）。
 * 红线：DebugAgent 只引导不给完整答案；Pyodide 预览 ≠ 判题（本组件只做可观测回放，不下判定）。
 *
 * 集成点（Phase 2 接线）：
 *   - port：复用 C-stage 的 CodeTheaterReplayPort（舞台播放接口）。未注入时走纯 DOM 静态高亮回退。
 *   - hintLayers：把后端会话已生成的 HINT_LAYER_1/2/3（或未来流式 DebugAgent）映射进来；缺省回退苏格拉底脚手架。
 */
import { computed, ref, watch } from 'vue';
import PyButton from '@/components/ui/PyButton.vue';
import PyEmptyState from '@/components/ui/PyEmptyState.vue';
import {
  buildGuideLadder,
  locateDiagnosticFocus,
  sliceTraceAroundFocus,
} from '@/services/codeTheater/debug';
import type {
  CodeTheaterReplayPort,
  DebugGuideStep,
  DebugHintLayerInput,
  DiagnosticFocus,
  FocusedTraceSlice,
} from '@/services/codeTheater/debug';
import type { Trace, TraceEvent } from '@/services/codeTheater/types';

const props = withDefaults(
  defineProps<{
    /** 失败提交的执行轨迹（C-trace-engine 产出；契约 §5.2）。 */
    trace: Trace;
    /** 期望标准输出（判题样例），用于无异常 / 非死循环时定位首个输出偏离点。 */
    expectedStdout?: string;
    /** DebugAgent 三阶提示；缺省回退苏格拉底脚手架。 */
    hintLayers?: DebugHintLayerInput[];
    /** 舞台播放端口（C-stage 适配器）；缺省 null 走纯 DOM 回退。 */
    port?: CodeTheaterReplayPort | null;
    sliceBefore?: number;
    sliceAfter?: number;
  }>(),
  {
    expectedStdout: undefined,
    hintLayers: undefined,
    port: null,
    sliceBefore: 8,
    sliceAfter: 6,
  },
);

const KIND_LABELS: Record<DiagnosticFocus['kind'], string> = {
  exception: '运行时绊倒',
  infinite_loop: '死循环',
  output_divergence: '输出跑偏',
  last_step: '未抛错·疑似偏差',
  none: '无焦点',
};

const STAGE_LABELS: Record<DebugGuideStep['stage'], string> = {
  rephrase: '换述',
  decompose: '拆解',
  point: '指方向',
};

const focus = computed<DiagnosticFocus>(() =>
  locateDiagnosticFocus(
    props.trace,
    props.expectedStdout != null ? { expectedStdout: props.expectedStdout } : {},
  ),
);

const slice = computed<FocusedTraceSlice>(() =>
  sliceTraceAroundFocus(props.trace, focus.value, {
    before: props.sliceBefore,
    after: props.sliceAfter,
  }),
);

const ladder = computed<DebugGuideStep[]>(() =>
  buildGuideLadder({
    focus: focus.value,
    ...(props.hintLayers != null ? { hintLayers: props.hintLayers } : {}),
  }),
);

const sourceLines = computed<string[]>(() => props.trace.source.split('\n'));
const variableRows = computed(() =>
  Object.entries(focus.value.variables).map(([name, value]) => ({ name, value })),
);
const sliceEvents = computed<TraceEvent[]>(() => slice.value.trace.events);

const selectedStep = ref<number>(-1);
const revealedCount = ref<number>(0);

watch(
  () => props.trace,
  () => {
    selectedStep.value = focus.value.found ? focus.value.step : -1;
    revealedCount.value = 0;
  },
  { immediate: true },
);

const activeLine = computed<number>(() => {
  const event = sliceEvents.value.find((item) => item.step === selectedStep.value);
  return event ? event.line : focus.value.line;
});

const revealedSteps = computed<DebugGuideStep[]>(() => ladder.value.slice(0, revealedCount.value));
const hasMoreHints = computed<boolean>(() => revealedCount.value < ladder.value.length);
const stageReady = computed<boolean>(() => props.port != null);

function lineClass(lineNumber: number): Record<string, boolean> {
  return {
    'code__line--focus': focus.value.found && lineNumber === focus.value.line,
    'code__line--active': lineNumber === activeLine.value && lineNumber !== focus.value.line,
  };
}

function eventLabel(event: TraceEvent): string {
  switch (event.kind) {
    case 'assign':
      return `${event.varName}=${event.value}`;
    case 'loop_iter':
      return `loop#${event.iter}`;
    case 'branch':
      return event.result ? 'if✓' : 'if✗';
    case 'call':
      return `→${event.funcName}`;
    case 'return':
      return `←${event.funcName}`;
    case 'output':
      return 'print';
    case 'exception':
      return event.excType;
    default:
      return event.kind;
  }
}

function selectStep(step: number): void {
  selectedStep.value = step;
  props.port?.seekToStep(step);
}

function revealNextHint(): void {
  if (revealedCount.value < ladder.value.length) {
    revealedCount.value += 1;
  }
}

async function replayOnStage(): Promise<void> {
  const port = props.port;
  if (!port) {
    return;
  }
  await port.load(slice.value);
  if (focus.value.found) {
    port.highlightLine(focus.value.line);
  }
  port.play();
}
</script>

<template>
  <section
    class="theater"
    data-testid="code-theater-replay"
  >
    <header class="theater__head">
      <div class="theater__titles">
        <h3 class="theater__title">
          调试剧场
        </h3>
        <span class="theater__subtitle">
          失败不是终点：把这次执行慢放一遍，看清傀儡在哪一步走偏
        </span>
      </div>
      <span
        class="theater__kind"
        :class="`theater__kind--${focus.kind}`"
        data-testid="focus-kind"
      >{{ KIND_LABELS[focus.kind] }}</span>
    </header>

    <PyEmptyState
      v-if="!focus.found"
      title="没有可回放的轨迹"
      description="这次提交没有产生可观测的执行步骤，换个角度先读读题面与报错。"
      data-testid="theater-empty"
    />

    <template v-else>
      <!-- 诊断焦点：一句话现象 + 出错行 + 变量快照 -->
      <section
        class="focus"
        data-testid="diagnostic-focus"
      >
        <p
          class="focus__phenomenon"
          data-testid="focus-phenomenon"
        >
          {{ focus.phenomenon }}
        </p>
        <div class="focus__meta">
          <span class="chip">出错行 第 {{ focus.line }} 行</span>
          <span
            v-if="focus.excType"
            class="chip chip--warn"
          >{{ focus.excType }}</span>
          <span
            v-if="focus.observedIterations"
            class="chip"
          >已绕 {{ focus.observedIterations }}+ 圈</span>
        </div>
        <div
          v-if="focus.kind === 'output_divergence'"
          class="focus__diff"
        >
          <p class="kv">
            <span class="kv__key">期望</span><code>{{ focus.expectedLine || '(空)' }}</code>
          </p>
          <p class="kv">
            <span class="kv__key">实际</span><code>{{ focus.actualLine || '(空)' }}</code>
          </p>
        </div>
        <div
          v-if="variableRows.length"
          class="snapshot"
          data-testid="variable-snapshot"
        >
          <span class="snapshot__label">出错时变量快照</span>
          <ul class="snapshot__list">
            <li
              v-for="row in variableRows"
              :key="row.name"
              class="snapshot__item"
            >
              <span class="snapshot__name">{{ row.name }}</span>
              <code class="snapshot__value">{{ row.value }}</code>
            </li>
          </ul>
        </div>
      </section>

      <!-- 源码 + 焦点高亮 -->
      <section class="stage">
        <ol
          class="code"
          data-testid="code-view"
        >
          <li
            v-for="(text, index) in sourceLines"
            :key="index"
            class="code__line"
            :class="lineClass(index + 1)"
            :data-line="index + 1"
          >
            <span class="code__no">{{ index + 1 }}</span>
            <span class="code__text">{{ text || ' ' }}</span>
            <span
              v-if="focus.found && index + 1 === focus.line"
              class="code__marker"
              aria-hidden="true"
            >✦</span>
          </li>
        </ol>

        <div class="stage__bar">
          <PyButton
            variant="primary"
            size="sm"
            :disabled="!stageReady"
            test-id="replay-on-stage"
            @click="replayOnStage"
          >
            在剧场舞台重演
          </PyButton>
          <span
            v-if="!stageReady"
            class="stage__note"
            data-testid="stage-pending"
          >舞台未接入（Phase 2 由 C-stage 提供播放端口）</span>
        </div>

        <!-- 回放时间线：截到出错前后的关键步 -->
        <ol
          class="timeline"
          data-testid="replay-timeline"
        >
          <li
            v-for="event in sliceEvents"
            :key="event.step"
          >
            <button
              type="button"
              class="timeline__step"
              :class="{
                'timeline__step--active': event.step === selectedStep,
                'timeline__step--focus': event.step === slice.focusStep,
              }"
              :data-step="event.step"
              :data-testid="`replay-step-${event.step}`"
              @click="selectStep(event.step)"
            >
              <span class="timeline__no">L{{ event.line }}</span>
              <span class="timeline__kind">{{ eventLabel(event) }}</span>
            </button>
          </li>
        </ol>
      </section>

      <!-- DebugAgent 引导式诊断：阶梯提示，绝不直接给答案 -->
      <section
        class="guide"
        data-testid="guide-ladder"
      >
        <div class="guide__head">
          <h4 class="guide__title">
            引导式诊断
          </h4>
          <span
            class="guide__redline"
            data-testid="guide-redline"
          >DebugAgent 只引导不给答案，逐阶解锁</span>
        </div>

        <ol class="guide__steps">
          <li
            v-for="step in revealedSteps"
            :key="step.order"
            class="guide__step"
            :data-testid="`guide-step-${step.order}`"
          >
            <div class="guide__step-head">
              <span class="guide__stage">第 {{ step.order }} 阶 · {{ STAGE_LABELS[step.stage] }}</span>
              <span
                v-if="step.redacted"
                class="chip chip--warn"
                data-testid="guide-redacted"
              >已脱敏</span>
            </div>
            <p class="guide__headline">
              {{ step.headline }}
            </p>
            <p class="guide__body">
              {{ step.body }}
            </p>
          </li>
        </ol>

        <PyEmptyState
          v-if="revealedSteps.length === 0"
          title="先自己想一想"
          description="点下方按钮逐阶解锁引导，每一阶都把球踢回给你思考。"
        />

        <div class="guide__actions">
          <PyButton
            v-if="hasMoreHints"
            variant="secondary"
            size="sm"
            test-id="reveal-hint"
            @click="revealNextHint"
          >
            {{ revealedCount === 0 ? '解锁第 1 阶引导' : '再给一阶提示' }}
          </PyButton>
          <span
            v-else
            class="guide__done"
            data-testid="guide-exhausted"
          >三阶引导已给完——剩下的最后一步，留给你自己改一版再重试。</span>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.theater {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: var(--pyt-space-3);
  color: var(--pyt-color-text-primary);
}

.theater__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}
.theater__titles {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.theater__title {
  margin: 0;
  font-size: var(--pyt-font-size-lg);
}
.theater__subtitle {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.theater__kind {
  flex: 0 0 auto;
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
  padding: 2px 10px;
  border-radius: var(--pyt-radius-full);
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-sunken);
  color: var(--pyt-color-text-hint);
}
.theater__kind--exception {
  background: var(--pyt-color-warning-bg);
  color: var(--pyt-color-primary-dark);
  border-color: var(--pyt-color-gold);
}
.theater__kind--infinite_loop {
  background: var(--pyt-color-accent-bg);
  color: var(--pyt-color-accent);
  border-color: var(--pyt-color-accent);
}

/* Focus */
.focus {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border-soft);
  border-left: 3px solid var(--pyt-color-gold);
  border-radius: var(--pyt-radius-md);
  padding: var(--pyt-space-2) var(--pyt-space-3);
}
.focus__phenomenon {
  margin: 0;
  font-size: var(--pyt-font-size-base);
  font-weight: 600;
  line-height: 1.5;
}
.focus__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--pyt-space-1);
}
.focus__diff {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.chip {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: var(--pyt-radius-full);
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
  font-size: var(--pyt-font-size-xs);
  font-weight: 600;
  white-space: nowrap;
}
.chip--warn {
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
}

.snapshot {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-1);
}
.snapshot__label {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.snapshot__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: var(--pyt-space-1);
}
.snapshot__item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-sm);
  padding: 1px 6px;
}
.snapshot__name {
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.snapshot__value {
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-primary);
}

/* Stage / code */
.stage {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.code {
  list-style: none;
  margin: 0;
  padding: var(--pyt-space-2);
  background: var(--pyt-color-terminal-bg, #1d1b16);
  color: var(--pyt-color-terminal-text, #e8e0cc);
  border-radius: var(--pyt-radius-md);
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-xs);
  max-height: 240px;
  overflow: auto;
}
.code__line {
  display: flex;
  align-items: baseline;
  gap: var(--pyt-space-2);
  padding: 1px 4px;
  border-radius: var(--pyt-radius-sm);
  white-space: pre;
}
.code__line--focus {
  background: rgba(214, 158, 46, 0.28);
  outline: 1px solid var(--pyt-color-gold);
}
.code__line--active {
  background: rgba(255, 255, 255, 0.08);
}
.code__no {
  flex: 0 0 auto;
  width: 2.2em;
  text-align: right;
  opacity: 0.55;
  user-select: none;
}
.code__text {
  flex: 1 1 auto;
  overflow-wrap: anywhere;
}
.code__marker {
  flex: 0 0 auto;
  color: var(--pyt-color-gold);
}

.stage__bar {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  flex-wrap: wrap;
}
.stage__note {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  gap: var(--pyt-space-1);
  overflow-x: auto;
}
.timeline__step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  min-width: 54px;
  padding: 3px 6px;
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-hint);
  cursor: pointer;
  font-size: 10px;
}
.timeline__step--active {
  border-color: var(--pyt-color-primary);
  background: var(--pyt-color-bg-elevated);
}
.timeline__step--focus {
  outline: 2px solid var(--pyt-color-gold);
}
.timeline__no {
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.timeline__kind {
  font-family: var(--pyt-font-mono);
  overflow: hidden;
  max-width: 6em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Guide ladder */
.guide {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  background: var(--pyt-color-info-bg, var(--pyt-color-bg-soft));
  border: 1px solid var(--pyt-color-info, var(--pyt-color-border-soft));
  border-radius: var(--pyt-radius-md);
  padding: var(--pyt-space-2) var(--pyt-space-3);
}
.guide__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--pyt-space-2);
  flex-wrap: wrap;
}
.guide__title {
  margin: 0;
  font-size: var(--pyt-font-size-base);
}
.guide__redline {
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
  color: var(--pyt-color-danger, #b4451f);
}
.guide__steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.guide__step {
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-sm);
  padding: var(--pyt-space-2);
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.guide__step-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}
.guide__stage {
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
  color: var(--pyt-color-accent);
}
.guide__headline {
  margin: 0;
  font-weight: 600;
  font-size: var(--pyt-font-size-sm);
}
.guide__body {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
  line-height: 1.5;
}
.guide__actions {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
}
.guide__done {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.kv {
  display: flex;
  gap: var(--pyt-space-1);
  margin: 0;
  font-size: var(--pyt-font-size-sm);
}
.kv__key {
  flex: 0 0 auto;
  color: var(--pyt-color-text-muted);
  font-weight: 600;
}
code {
  font-family: var(--pyt-font-mono);
  overflow-wrap: anywhere;
}
</style>
