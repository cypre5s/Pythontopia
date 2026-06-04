<script setup lang="ts">
/**
 * 像素「智能体事务所」执行动画（P3-a2）。
 *
 * 把多智能体资源生成的 SSE trace 变成可见、可信、低门槛的过场：
 *   - 舞台层：4 个 Agent NPC 在工位演「工作 / 待机」，主管(总管)走位到当前工位派活，
 *     完工的 Agent 打✓汇报；审查官翻书核对。
 *   - 仪表层（右侧）：整体进度条 + 步骤分解 + 实时执行日志（哪个 Agent 在做什么 / 耗时 / 通过·重试）。
 *   - 交付前硬确认「证据卡」：逐条标注 grounding / safety + 课本页码 / 通识标注，学生确认后才收下。
 *
 * 纯展示组件：消费父级传入的 SSE 状态，通过 emit 把「确认收下 / 取消」交回父级。
 * 美术全部复用已审计的 CC-BY-SA-3.0 LPC npc-composites（9×4 walkcycle，64px），不新增二进制。
 */
import { computed } from 'vue';
import type { SseTraceEvent } from '@/composables/useSseStream';
import { playUiClick, playUiHover } from '@/composables/useStardewFx';
import type { OfficeEvidence } from './agentOffice';

const props = defineProps<{
  traces: SseTraceEvent[];
  status: 'idle' | 'streaming' | 'done' | 'error';
  tokenPreview: string;
  errorMessage: string | null;
  evidence: OfficeEvidence | null;
}>();

const emit = defineEmits<{
  (e: 'confirm'): void;
  (e: 'cancel'): void;
}>();

type StageKey = 'evidence' | 'generate' | 'grounding' | 'safety';
type StageState = 'pending' | 'active' | 'done' | 'error';

interface OfficeAgent {
  stage: StageKey;
  sprite: string;
  name: string;
  role: string;
  /** 工作时的动作描述（日志 / 气泡）。 */
  action: string;
}

const SPRITE_BASE = '/assets/pixel/sprites/npc-composites';
const STAGE_ORDER: readonly StageKey[] = ['evidence', 'generate', 'grounding', 'safety'];

const AGENTS: readonly OfficeAgent[] = [
  { stage: 'evidence', sprite: 'npc_librarian', name: '馆员 Doc', role: '证据检索', action: '翻阅课本检索证据' },
  { stage: 'generate', sprite: 'npc_workshop', name: '设计师 Make', role: '内容生成', action: '撰写学习资源' },
  { stage: 'grounding', sprite: 'npc_sage', name: '审查官 Sage', role: '引用审查', action: '逐条核对课本页码' },
  { stage: 'safety', sprite: 'npc_guard', name: '安全员 Safe', role: '安全审查', action: '排查泄答与风险' },
];

const SUPERVISOR = { sprite: 'npc_mayor', name: '总管 Py' };

const STAGE_LABEL: Record<StageKey, string> = {
  evidence: '证据检索',
  generate: '内容生成',
  grounding: '引用审查',
  safety: '安全审查',
};

function spriteUrl(sprite: string): string {
  return `url(${SPRITE_BASE}/${sprite}.png)`;
}

/** 把真实后端 trace node 归类到 4 个工位阶段。 */
function stageOf(node: string): StageKey | null {
  if (node === 'evidence_fetcher') return 'evidence';
  if (node === 'grounding_critic' || node === 'hallucination_detector') return 'grounding';
  if (node === 'safety_critic') return 'safety';
  if (/attempt/i.test(node) || /agent/i.test(node)) return 'generate';
  return null;
}

const stageStates = computed<Record<StageKey, StageState>>(() => {
  const done: Record<StageKey, boolean> = {
    evidence: false, generate: false, grounding: false, safety: false,
  };
  const failed: Record<StageKey, boolean> = {
    evidence: false, generate: false, grounding: false, safety: false,
  };
  for (const t of props.traces) {
    const stage = stageOf(t.node);
    if (!stage) continue;
    if (t.status === 'fail') failed[stage] = true;
    else done[stage] = true;
  }
  const firstPending = STAGE_ORDER.find((s) => !done[s] && !failed[s]) ?? null;
  const result = {} as Record<StageKey, StageState>;
  for (const s of STAGE_ORDER) {
    if (failed[s]) result[s] = 'error';
    else if (done[s]) result[s] = 'done';
    else if (s === firstPending && (props.status === 'streaming' || props.status === 'idle'))
      result[s] = 'active';
    else result[s] = 'pending';
  }
  return result;
});

const activeIndex = computed<number>(() => {
  const idx = STAGE_ORDER.findIndex((s) => stageStates.value[s] === 'active');
  return idx === -1 ? -1 : idx;
});

const doneCount = computed<number>(
  () => STAGE_ORDER.filter((s) => stageStates.value[s] === 'done').length,
);
const progressPct = computed<number>(() => Math.round((doneCount.value / STAGE_ORDER.length) * 100));

const allDone = computed<boolean>(() => props.status === 'done' && props.evidence !== null);

// 主管位置：站到当前派活工位上方；全部完工后回到交付台（最右）。
const supervisorLeftPct = computed<number>(() => {
  const slot = activeIndex.value === -1 ? (allDone.value ? AGENTS.length - 1 : 0) : activeIndex.value;
  // 4 工位均分，主管对齐到工位中心。
  return (slot + 0.5) * (100 / AGENTS.length);
});

const supervisorLine = computed<string>(() => {
  if (props.status === 'error') return '出了点状况…';
  if (allDone.value) return '完工，待交付！';
  if (activeIndex.value === -1) return '准备派活…';
  return `${AGENTS[activeIndex.value]?.name}，开工！`;
});

interface LogLine {
  stage: string;
  status: string;
  elapsedMs: number;
  note: string;
}
const logLines = computed<LogLine[]>(() =>
  props.traces.map((t) => {
    const stage = stageOf(t.node);
    const label = stage ? STAGE_LABEL[stage] : t.node;
    const statusZh =
      t.status === 'fail' ? '失败' : t.status === 'retry' ? '重试' : '完成';
    const agent = stage ? AGENTS.find((a) => a.stage === stage) : null;
    return {
      stage: agent ? `${agent.name} · ${label}` : label,
      status: statusZh,
      elapsedMs: t.elapsed_ms ?? 0,
      note: agent?.action ?? '',
    };
  }),
);

function agentStateClass(stage: StageKey): string {
  return `office-agent--${stageStates.value[stage]}`;
}

function onConfirm(): void {
  playUiClick();
  emit('confirm');
}
function onCancel(): void {
  playUiClick();
  emit('cancel');
}

const groundingText = computed<string>(() => {
  const ev = props.evidence;
  if (!ev) return '';
  if (ev.pageNos.length > 0) {
    return `内容依据：课本第 ${ev.pageNos.join('、')} 页`;
  }
  if (ev.basedOnGeneral || ev.groundingStatus === 'NO_EVIDENCE') {
    return '内容依据：基于通识知识（未命中课件证据，已显式标注）';
  }
  return '内容依据：已通过引用审查';
});
</script>

<template>
  <section
    class="office"
    data-testid="agent-office"
  >
    <div class="office-main">
      <!-- ── 舞台层：像素事务所 ── -->
      <div
        class="office-stage"
        data-testid="office-stage"
      >
        <div
          class="office-supervisor"
          :style="{ left: `${supervisorLeftPct}%` }"
          data-testid="office-supervisor"
        >
          <span class="office-bubble">{{ supervisorLine }}</span>
          <span
            class="office-sprite office-sprite--boss"
            :class="{ 'office-sprite--walk': status === 'streaming' }"
            :style="{ backgroundImage: spriteUrl(SUPERVISOR.sprite) }"
            :title="SUPERVISOR.name"
          />
        </div>

        <div class="office-desks">
          <div
            v-for="agent in AGENTS"
            :key="agent.stage"
            class="office-agent"
            :class="agentStateClass(agent.stage)"
            :data-testid="`office-agent-${agent.stage}`"
          >
            <span
              v-if="stageStates[agent.stage] === 'done'"
              class="office-agent__badge office-agent__badge--ok"
            >✓</span>
            <span
              v-else-if="stageStates[agent.stage] === 'error'"
              class="office-agent__badge office-agent__badge--err"
            >✗</span>
            <span
              v-else-if="stageStates[agent.stage] === 'active'"
              class="office-agent__badge office-agent__badge--work"
            >…</span>
            <span
              class="office-sprite"
              :style="{ backgroundImage: spriteUrl(agent.sprite) }"
              :title="`${agent.name} · ${agent.role}`"
            />
            <span
              class="office-agent__desk"
              aria-hidden="true"
            />
            <span class="office-agent__name">{{ agent.name }}</span>
            <span class="office-agent__role">{{ agent.role }}</span>
          </div>
        </div>
      </div>

      <!-- ── 仪表层：进度 + 步骤 + 日志 ── -->
      <aside
        class="office-rail"
        data-testid="office-rail"
      >
        <div class="office-progress">
          <span class="office-progress__label">整体进度</span>
          <span class="office-progress__bar">
            <span
              class="office-progress__fill"
              :style="{ width: `${progressPct}%` }"
            />
          </span>
          <span class="office-progress__pct">{{ progressPct }}%</span>
        </div>

        <ol class="office-steps">
          <li
            v-for="(stage, idx) in STAGE_ORDER"
            :key="stage"
            class="office-step"
            :class="`office-step--${stageStates[stage]}`"
            :data-testid="`office-step-${stage}`"
          >
            <span class="office-step__marker">
              <template v-if="stageStates[stage] === 'done'">✓</template>
              <template v-else-if="stageStates[stage] === 'error'">✗</template>
              <template v-else-if="stageStates[stage] === 'active'">●</template>
              <template v-else>{{ idx + 1 }}</template>
            </span>
            <span class="office-step__label">{{ STAGE_LABEL[stage] }}</span>
          </li>
        </ol>

        <div
          class="office-log"
          data-testid="office-log"
        >
          <span class="office-log__title">执行日志</span>
          <ul class="office-log__list">
            <li
              v-for="(line, i) in logLines"
              :key="i"
              class="office-log__line"
            >
              <span class="office-log__stage">{{ line.stage }}</span>
              <span class="office-log__status">{{ line.status }}</span>
              <span class="office-log__time">{{ line.elapsedMs }}ms</span>
            </li>
            <li
              v-if="status === 'streaming' && logLines.length === 0"
              class="office-log__line office-log__line--muted"
            >
              总管正在派活…
            </li>
          </ul>
          <p
            v-if="tokenPreview"
            class="office-log__preview"
            data-testid="office-preview"
          >
            {{ tokenPreview }}{{ tokenPreview.length >= 200 ? '…' : '' }}
          </p>
        </div>

        <p
          v-if="status === 'error'"
          class="office-error"
          data-testid="office-error"
        >
          ⚠ {{ errorMessage ?? '生成失败' }}
        </p>
      </aside>
    </div>

    <!-- ── 交付前硬确认：证据卡 ── -->
    <div
      v-if="allDone && evidence"
      class="office-evidence"
      data-testid="office-evidence-card"
      role="dialog"
      aria-label="交付证据确认卡"
    >
      <div class="office-evidence__card">
        <h4 class="office-evidence__title">
          📋 交付证据确认
        </h4>
        <ul class="office-evidence__list">
          <li
            class="office-evidence__row"
            :class="{
              'office-evidence__row--ok': evidence.groundingStatus === 'PASSED',
              'office-evidence__row--warn': evidence.groundingStatus !== 'PASSED',
            }"
          >
            <span class="office-evidence__k">引用审查</span>
            <span class="office-evidence__v">{{ groundingText }}</span>
          </li>
          <li
            class="office-evidence__row"
            :class="{
              'office-evidence__row--ok': evidence.safetyStatus === 'PASSED',
              'office-evidence__row--warn': evidence.safetyStatus !== 'PASSED',
            }"
          >
            <span class="office-evidence__k">安全审查</span>
            <span class="office-evidence__v">
              {{ evidence.safetyStatus === 'PASSED' ? '通过（无泄答 / 风险内容）' : '未通过，请谨慎参考' }}
            </span>
          </li>
        </ul>
        <p class="office-evidence__hint">
          多智能体已协同生成并通过防幻觉 / 安全审查。确认后收入「我的书房」并记一次「学」里程碑。
        </p>
        <div class="office-evidence__actions">
          <button
            type="button"
            class="office-evidence__btn office-evidence__btn--primary"
            data-testid="office-evidence-confirm"
            @click="onConfirm"
            @mouseenter="playUiHover"
          >
            确认收下并查看
          </button>
          <button
            type="button"
            class="office-evidence__btn"
            data-testid="office-evidence-cancel"
            @click="onCancel"
            @mouseenter="playUiHover"
          >
            我再想想
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.office {
  position: relative;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  background: var(--pyt-color-bg-elevated);
  overflow: hidden;
}
.office-main {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 0;
}
/* ── 舞台 ── */
.office-stage {
  position: relative;
  min-height: 200px;
  padding: 28px 12px 12px;
  background:
    linear-gradient(180deg, #e7d6a8 0%, #d8c089 55%, #b9925a 55%, #a87f48 100%);
  overflow: hidden;
}
.office-supervisor {
  position: absolute;
  top: 2px;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: left 600ms ease;
  z-index: 3;
}
.office-bubble {
  font-size: 10px;
  color: #3a2a14;
  background: #fffbe9;
  border: 1px solid var(--pyt-color-text-hint);
  border-radius: 6px;
  padding: 1px 6px;
  white-space: nowrap;
  margin-bottom: 1px;
}
.office-desks {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  align-items: end;
  height: 100%;
  gap: 4px;
}
.office-agent {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
}
.office-sprite {
  width: 64px;
  height: 64px;
  background-repeat: no-repeat;
  background-size: 576px 256px;
  /* 第 3 行 = 朝下（front），第 0 列 = 待机帧（frame 18）。 */
  background-position: 0 -128px;
  image-rendering: pixelated;
  filter: drop-shadow(0 3px 2px rgba(0, 0, 0, 0.3));
}
.office-agent--active .office-sprite {
  animation: office-work 0.5s ease-in-out infinite;
}
.office-agent--pending .office-sprite {
  opacity: 0.82;
  animation: office-idle 2.4s ease-in-out infinite;
}
.office-agent--done .office-sprite {
  opacity: 0.95;
}
.office-agent--error .office-sprite {
  filter: drop-shadow(0 3px 2px rgba(0, 0, 0, 0.3)) saturate(0.4);
}
.office-sprite--boss {
  width: 56px;
  height: 56px;
  background-size: 504px 224px;
  background-position: 0 -112px;
}
.office-sprite--walk {
  animation: office-bob 0.45s ease-in-out infinite;
}
.office-agent__desk {
  width: 56px;
  height: 10px;
  background: var(--pyt-color-text-hint);
  border-radius: 2px 2px 0 0;
  box-shadow: 0 2px 0 var(--pyt-color-text-primary);
  margin-top: -6px;
}
.office-agent__name {
  font-size: 10px;
  font-weight: 700;
  color: #3a2a14;
}
.office-agent__role {
  font-size: 9px;
  color: var(--pyt-color-text-hint);
}
.office-agent__badge {
  position: absolute;
  top: 0;
  right: 18px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  z-index: 2;
}
.office-agent__badge--ok {
  background: #4d6e1d;
}
.office-agent__badge--err {
  background: var(--pyt-color-danger);
}
.office-agent__badge--work {
  background: #d4af37;
  animation: office-blink 0.9s steps(2) infinite;
}
/* ── 仪表层 ── */
.office-rail {
  padding: 10px 12px;
  border-left: 2px dashed var(--pyt-color-border);
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
.office-progress {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--pyt-color-text-primary);
}
.office-progress__label {
  flex: 0 0 auto;
  font-weight: 700;
}
.office-progress__bar {
  flex: 1 1 auto;
  height: 8px;
  background: rgba(0, 0, 0, 0.18);
  border: 1px solid var(--pyt-color-text-muted);
  border-radius: 4px;
  overflow: hidden;
}
.office-progress__fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #6bbf59, #d4af37);
  transition: width 360ms ease;
}
.office-progress__pct {
  flex: 0 0 auto;
  font-weight: 700;
  min-width: 32px;
  text-align: right;
}
.office-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.office-step {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  padding: 3px 6px;
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
  background: var(--pyt-color-bg-soft);
}
.office-step--done {
  background: #d6ead1;
  border-color: #4d6e1d;
}
.office-step--active {
  background: #ffe098;
  border-color: #d4af37;
  animation: office-blink 1.2s ease-in-out infinite;
}
.office-step--error {
  background: #f8d7d0;
  border-color: var(--pyt-color-danger);
}
.office-step__marker {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--pyt-color-border);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.office-step--done .office-step__marker {
  background: #4d6e1d;
}
.office-step--active .office-step__marker {
  background: #d4af37;
}
.office-step--error .office-step__marker {
  background: var(--pyt-color-danger);
}
.office-step__label {
  color: #3a2a14;
}
.office-log {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-height: 0;
}
.office-log__title {
  font-size: 10px;
  font-weight: 700;
  color: var(--pyt-color-text-muted);
}
.office-log__list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 84px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.office-log__line {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 6px;
  font-size: 10px;
  color: #3a2a14;
  background: var(--pyt-color-bg-soft);
  border-radius: 3px;
  padding: 2px 6px;
}
.office-log__line--muted {
  color: var(--pyt-color-text-muted);
  grid-template-columns: 1fr;
}
.office-log__status {
  color: #4d6e1d;
}
.office-log__time {
  color: var(--pyt-color-text-muted);
}
.office-log__preview {
  margin: 2px 0 0;
  font-size: 10px;
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
  border: 1px dashed var(--pyt-color-border);
  border-radius: 3px;
  padding: 4px 6px;
  max-height: 48px;
  overflow: hidden;
}
.office-error {
  margin: 0;
  font-size: 11px;
  color: #b3402a;
}
/* ── 证据卡 ── */
.office-evidence {
  position: absolute;
  inset: 0;
  background: rgba(20, 14, 6, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 5;
}
.office-evidence__card {
  width: min(380px, calc(100% - 24px));
  background: #fffbe9;
  border: 3px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  padding: 14px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.4);
}
.office-evidence__title {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--pyt-color-text-primary);
}
.office-evidence__list {
  list-style: none;
  margin: 0 0 8px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.office-evidence__row {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px;
  font-size: 12px;
  padding: 6px 8px;
  border-radius: 4px;
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
}
.office-evidence__row--ok {
  border-color: #4d6e1d;
  background: #eef6e8;
}
.office-evidence__row--warn {
  border-color: var(--pyt-color-danger);
  background: #fbeae6;
}
.office-evidence__k {
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.office-evidence__v {
  color: #3a2a14;
}
.office-evidence__hint {
  margin: 0 0 10px;
  font-size: 11px;
  color: var(--pyt-color-text-hint);
  line-height: 1.5;
}
.office-evidence__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
.office-evidence__btn {
  font-family: inherit;
  font-size: 12px;
  padding: 6px 12px;
  border: 2px solid #8c6a23;
  border-radius: 4px;
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
}
.office-evidence__btn--primary {
  background: #f7d774;
  color: #2a1a08;
  font-weight: 700;
  box-shadow: 0 2px 0 #8c6a23;
}
.office-evidence__btn--primary:active {
  transform: translateY(2px);
  box-shadow: none;
}
@keyframes office-work {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-4px) scale(1.04); }
}
@keyframes office-idle {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
}
@keyframes office-bob {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}
@keyframes office-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}
@media (max-width: 900px) {
  .office-main {
    grid-template-columns: 1fr;
  }
  .office-rail {
    border-left: none;
    border-top: 2px dashed var(--pyt-color-border);
  }
}
@media (prefers-reduced-motion: reduce) {
  .office-sprite,
  .office-step--active,
  .office-agent__badge--work {
    animation: none !important;
  }
}
</style>
