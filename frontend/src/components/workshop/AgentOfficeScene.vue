<script setup lang="ts">
/**
 * 像素「智能体事务所」执行动画：把多智能体资源生成的 SSE trace 渲染成可视过场。
 *   - 舞台层：Agent NPC 在工位演「工作 / 待机」，总管走位到当前工位派活，完工打✓；审查官核对。
 *   - 仪表层（右侧）：整体进度条 + 步骤分解 + 实时执行日志（哪个 Agent 在做什么 / 耗时 / 通过·重试）。
 *   - 交付前硬确认「证据卡」：逐条标注 grounding / safety + 课本页码 / 通识标注，学生确认后才收下。
 *
 * 纯展示组件：消费父级传入的 SSE 状态，通过 emit 把「确认收下 / 取消」交回父级。
 * 像素美术复用已审计的 LPC npc-composites（9×4 walkcycle，64px），不新增二进制。
 */
import { computed } from 'vue';
import type { SsePlan, SseTraceEvent } from '@/composables/useSseStream';
import { playUiClick, playUiHover } from '@/composables/useStardewFx';
import type { OfficeEvidence } from './agentOffice';

const props = defineProps<{
  traces: SseTraceEvent[];
  status: 'idle' | 'streaming' | 'done' | 'error';
  tokenPreview: string;
  errorMessage: string | null;
  evidence: OfficeEvidence | null;
  /**
   * 工位清单（来自资源流的 `plan` SSE 事件）：存在时按真实节点动态渲染工位 + 相位驱动；
   * 缺省（旧后端）时回退旧 4 工位逻辑。
   */
  plan?: SsePlan | null;
}>();

const emit = defineEmits<{
  (e: 'confirm'): void;
  (e: 'cancel'): void;
}>();

type StationState = 'pending' | 'active' | 'done' | 'retry' | 'error';

/** 统一工位视图模型：plan 模式与回退模式产出相同形状，模板只迭代它。 */
interface StationVm {
  key: string;
  /** 后端 trace node（plan 模式）或阶段键（回退模式），用于匹配 trace。 */
  node: string;
  /** 工位台名（plan 模式 = 真实 agent_label）。 */
  label: string;
  /** 右栏清单条目名（plan 模式 = agent_label；回退 = 阶段中文名）。 */
  stepLabel: string;
  /** 工位下方角色小字。 */
  role: string;
  sprite: string;
  state: StationState;
  elapsedMs: number;
}

const SPRITE_BASE = '/assets/pixel/sprites/npc-composites';
const SUPERVISOR = { sprite: 'npc_mayor', name: '总管 Py' };
// Agent 串门（涌现协作）：反思/交接 NPC 走到同事工位通报「需要重点照顾」的学生。
const VISIT = { sprite: 'npc_tutor_ai', name: '导学 AI' };

function spriteUrl(sprite: string): string {
  return `url(${SPRITE_BASE}/${sprite}.png)`;
}

function statusZh(status: string): string {
  if (status === 'fail') return '失败';
  if (status === 'retry') return '重试';
  if (status === 'running') return '进行中';
  return '完成';
}

// ── plan 模式：kind / designer → 已审计 NPC sprite（均已在 BootScene 预加载，不新增素材） ──
const KIND_ROLE: Record<string, string> = {
  evidence: '证据检索',
  designer: '内容生成',
  grounding: '引用审查',
  hallucination: '防幻觉',
  safety: '安全审查',
};
const KIND_SPRITE: Record<string, string> = {
  evidence: 'npc_librarian',
  designer: 'npc_workshop',
  grounding: 'npc_sage',
  hallucination: 'npc_student',
  safety: 'npc_guard',
};
const DESIGNER_SPRITE_BY_TYPE: Record<string, string> = {
  HANDOUT: 'npc_professor',
  MINDMAP: 'npc_tutor_ai',
  PRACTICE: 'npc_trainer',
  CODE_LAB: 'npc_coach',
  READING: 'npc_workshop',
  STORYBOARD: 'npc_gardener',
};

function designerSprite(type: string): string {
  return DESIGNER_SPRITE_BY_TYPE[type] ?? KIND_SPRITE.designer ?? 'npc_workshop';
}

/** 去掉设计师节点的 `.attemptN` 后缀，使 attempt0/1（重试）映射到同一工位。 */
function nodeBase(node: string): string {
  return node.replace(/\.attempt\d+$/, '');
}

/** SseTraceEvent 未声明 note（反思/交接 trace 透传字段）：按可选字段安全读取，不引入 any。 */
function traceNote(t: SseTraceEvent): string | undefined {
  return (t as SseTraceEvent & { note?: string }).note;
}

/** 反思 / 交接类节点：触发「串门」过场，且不计入工位与执行日志流水线。 */
const HANDOFF_NODES = new Set(['reflection', 'handoff']);
function isHandoffTrace(t: SseTraceEvent): boolean {
  return HANDOFF_NODES.has(nodeBase(t.node));
}

/** plan 模式：取某工位「最新一条」trace（按 base 名匹配，兼容重试）。 */
function latestTrace(node: string): SseTraceEvent | null {
  const base = nodeBase(node);
  let found: SseTraceEvent | null = null;
  for (const t of props.traces) {
    if (nodeBase(t.node) === base) found = t;
  }
  return found;
}

function planStationState(node: string): { state: StationState; elapsedMs: number } {
  const t = latestTrace(node);
  if (!t) return { state: 'pending', elapsedMs: 0 };
  if (t.phase === 'start') return { state: 'active', elapsedMs: 0 };
  // finish（或旧式无 phase 的 trace）→ 按状态落定。
  const elapsedMs = t.elapsed_ms ?? 0;
  if (t.status === 'fail') return { state: 'error', elapsedMs };
  if (t.status === 'retry') return { state: 'retry', elapsedMs };
  return { state: 'done', elapsedMs };
}

// ── 回退模式（无 plan 事件）：保留旧 4 工位 + stageOf 正则，确保旧后端不崩 ──
type StageKey = 'evidence' | 'generate' | 'grounding' | 'safety';
const STAGE_ORDER: readonly StageKey[] = ['evidence', 'generate', 'grounding', 'safety'];
interface LegacyAgent { stage: StageKey; sprite: string; name: string; role: string; }
const LEGACY_AGENTS: readonly LegacyAgent[] = [
  { stage: 'evidence', sprite: 'npc_librarian', name: '馆员 Doc', role: '证据检索' },
  { stage: 'generate', sprite: 'npc_workshop', name: '设计师 Make', role: '内容生成' },
  { stage: 'grounding', sprite: 'npc_sage', name: '审查官 Sage', role: '引用审查' },
  { stage: 'safety', sprite: 'npc_guard', name: '安全员 Safe', role: '安全审查' },
];
const STAGE_LABEL: Record<StageKey, string> = {
  evidence: '证据检索',
  generate: '内容生成',
  grounding: '引用审查',
  safety: '安全审查',
};

function stageOf(node: string): StageKey | null {
  if (node === 'evidence_fetcher') return 'evidence';
  if (node === 'grounding_critic' || node === 'hallucination_detector') return 'grounding';
  if (node === 'safety_critic') return 'safety';
  if (/attempt/i.test(node) || /agent/i.test(node)) return 'generate';
  return null;
}

function legacyStations(): StationVm[] {
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
  return LEGACY_AGENTS.map((a) => {
    let state: StationState;
    if (failed[a.stage]) state = 'error';
    else if (done[a.stage]) state = 'done';
    else if (a.stage === firstPending && (props.status === 'streaming' || props.status === 'idle'))
      state = 'active';
    else state = 'pending';
    return {
      key: a.stage,
      node: a.stage,
      label: a.name,
      stepLabel: STAGE_LABEL[a.stage],
      role: a.role,
      sprite: a.sprite,
      state,
      elapsedMs: 0,
    };
  });
}

const stations = computed<StationVm[]>(() => {
  const plan = props.plan;
  if (plan && plan.nodes.length > 0) {
    return plan.nodes.map((n) => {
      const { state, elapsedMs } = planStationState(n.node);
      const sprite = n.kind === 'designer'
        ? designerSprite(plan.type)
        : (KIND_SPRITE[n.kind] ?? 'npc_workshop');
      return {
        key: n.node,
        node: n.node,
        label: n.agent_label,
        stepLabel: n.agent_label,
        role: KIND_ROLE[n.kind] ?? n.kind,
        sprite,
        state,
        elapsedMs,
      };
    });
  }
  return legacyStations();
});

const activeIndex = computed<number>(() => stations.value.findIndex((s) => s.state === 'active'));

// ── Agent 串门（涌现协作可视）：反思/交接 trace → 一名 NPC 走到同事工位通报 ──
// 取最后一条反思/交接 trace；其 note 文本回退到含「照顾」的合理默认文案。
const visit = computed<{ note: string } | null>(() => {
  let found: SseTraceEvent | null = null;
  for (const t of props.traces) {
    if (isHandoffTrace(t)) found = t;
  }
  if (!found) return null;
  return { note: traceNote(found) ?? '这位同学需要重点照顾' };
});

// ── 工位间「接力棒」交接动画（复用 MarvisPanel 语义，横向版） ──
// 连线 i 连接工位 i → 工位 i+1：上游完工后线点亮；上游完工且下游在干 → 流动 + 接力棒右滑。
const connectorIndices = computed<number[]>(() =>
  stations.value.length > 1 ? stations.value.slice(0, -1).map((_, i) => i) : [],
);

type LinkClass = 'office-link--pending' | 'office-link--filled' | 'office-link--flow';
function officeLinkClass(i: number): LinkClass {
  const a = stations.value[i];
  const b = stations.value[i + 1];
  if (!a || !b) return 'office-link--pending';
  const upstreamDone = a.state === 'done' || a.state === 'retry';
  if (!upstreamDone) return 'office-link--pending';
  if (b.state === 'active') return 'office-link--flow';
  return 'office-link--filled';
}

function officeLinkFlowing(i: number): boolean {
  return officeLinkClass(i) === 'office-link--flow';
}

// 连线段在两个工位中心之间：从中心 i 到中心 i+1（按工位数均分整条工位带）。
function connectorStyle(i: number): Record<string, string> {
  const count = stations.value.length || 1;
  const unit = 100 / count;
  return { left: `${(i + 0.5) * unit}%`, width: `${unit}%` };
}

// 已完成工位（含重试，不含失败）用于进度条；回退模式无 retry。
const completedCount = computed<number>(
  () => stations.value.filter((s) => s.state === 'done' || s.state === 'retry').length,
);
const progressPct = computed<number>(() =>
  Math.round((completedCount.value / (stations.value.length || 1)) * 100),
);

const allDone = computed<boolean>(() => props.status === 'done' && props.evidence !== null);

const deskGridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${stations.value.length || 1}, 1fr)`,
}));

// 主管位置：站到当前派活工位上方；全部完工后回到交付台（最右）。
const supervisorLeftPct = computed<number>(() => {
  const count = stations.value.length || 1;
  const slot = activeIndex.value === -1 ? (allDone.value ? count - 1 : 0) : activeIndex.value;
  return (slot + 0.5) * (100 / count);
});

const supervisorLine = computed<string>(() => {
  if (props.status === 'error') return '出了点状况…';
  if (allDone.value) return '完工，待交付！';
  if (activeIndex.value === -1) return '准备派活…';
  return `${stations.value[activeIndex.value]?.label}，开工！`;
});

interface LogLine {
  stage: string;
  status: string;
  elapsedMs: number;
}
const logLines = computed<LogLine[]>(() => {
  const plan = props.plan;
  if (plan && plan.nodes.length > 0) {
    const labelOf = (node: string): string => {
      const base = nodeBase(node);
      const match = plan.nodes.find((n) => nodeBase(n.node) === base);
      return match ? match.agent_label : node;
    };
    // plan 模式日志：每节点「完工」时记一条（agent_label · 状态 · 耗时）。
    // 反思/交接节点无 phase 已天然排除，这里显式再过滤一遍以稳妥（串门只走过场，不进日志）。
    return props.traces
      .filter((t) => t.phase === 'finish' && !isHandoffTrace(t))
      .map((t) => ({
        stage: labelOf(t.node),
        status: statusZh(t.status),
        elapsedMs: t.elapsed_ms ?? 0,
      }));
  }
  return props.traces.filter((t) => !isHandoffTrace(t)).map((t) => {
    const stage = stageOf(t.node);
    const label = stage ? STAGE_LABEL[stage] : t.node;
    const agent = stage ? LEGACY_AGENTS.find((a) => a.stage === stage) : null;
    return {
      stage: agent ? `${agent.name} · ${label}` : label,
      status: statusZh(t.status),
      elapsedMs: t.elapsed_ms ?? 0,
    };
  });
});

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

        <div
          class="office-desks"
          :style="deskGridStyle"
        >
          <!-- 工位间连线 + 接力棒：上游完工点亮、下游开工流动（贴在工位 sprite 中线高度）。 -->
          <div
            class="office-track"
            aria-hidden="true"
          >
            <span
              v-for="i in connectorIndices"
              :key="`link-${i}`"
              class="office-track__seg"
              :class="officeLinkClass(i)"
              :style="connectorStyle(i)"
            >
              <span
                v-if="officeLinkFlowing(i)"
                class="office-track__baton"
              />
            </span>
          </div>
          <div
            v-for="station in stations"
            :key="station.key"
            class="office-agent"
            :class="`office-agent--${station.state}`"
            :data-testid="`office-agent-${station.key}`"
          >
            <span
              v-if="station.state === 'done'"
              class="office-agent__badge office-agent__badge--ok"
            >✓</span>
            <span
              v-else-if="station.state === 'retry'"
              class="office-agent__badge office-agent__badge--retry"
            >↻</span>
            <span
              v-else-if="station.state === 'error'"
              class="office-agent__badge office-agent__badge--err"
            >✗</span>
            <span
              v-else-if="station.state === 'active'"
              class="office-agent__badge office-agent__badge--work"
            >…</span>
            <span
              class="office-sprite"
              :style="{ backgroundImage: spriteUrl(station.sprite) }"
              :title="`${station.label} · ${station.role}`"
            />
            <span
              class="office-agent__desk"
              aria-hidden="true"
            />
            <span class="office-agent__name">{{ station.label }}</span>
            <span class="office-agent__role">{{ station.role }}</span>
          </div>
        </div>

        <!-- ── Agent 串门（涌现协作）：导学 NPC 走到工位通报「需要重点照顾」 ── -->
        <div
          v-if="visit"
          class="office-visit"
          data-testid="agent-handoff-visit"
        >
          <span
            class="office-sprite office-sprite--walk office-visit__sprite"
            :style="{ backgroundImage: spriteUrl(VISIT.sprite) }"
            :title="VISIT.name"
            aria-hidden="true"
          />
          <span class="office-visit__bubble">
            <span class="office-visit__tag">串门通报</span>
            <span
              class="office-visit__note"
              data-testid="agent-handoff-note"
            >{{ visit.note }}</span>
          </span>
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
            v-for="(station, idx) in stations"
            :key="station.key"
            class="office-step"
            :class="`office-step--${station.state}`"
            :data-testid="`office-step-${station.key}`"
          >
            <span class="office-step__marker">
              <template v-if="station.state === 'done'">✓</template>
              <template v-else-if="station.state === 'retry'">↻</template>
              <template v-else-if="station.state === 'error'">✗</template>
              <template v-else-if="station.state === 'active'">●</template>
              <template v-else>{{ idx + 1 }}</template>
            </span>
            <span class="office-step__label">{{ station.stepLabel }}</span>
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
          多智能体已协同生成，已核对教材来源 / 安全。确认后收入「我的书房」并记一次「学」里程碑。
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
  position: relative;
  /* z-index:0 建立层叠上下文，让 .office-track(z-index:-1) 落在「地板背景之上、工位 sprite 之下」。 */
  z-index: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  align-items: end;
  height: 100%;
  gap: 4px;
}

/* ── 工位间交接连线 + 接力棒（横向，贴 sprite 中线；labels 已定高，bottom 锚点稳定） ── */
.office-track {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 70px;
  height: 3px;
  z-index: -1;
  pointer-events: none;
}
.office-track__seg {
  position: absolute;
  top: 0;
  height: 3px;
  border-radius: 2px;
  background: var(--pyt-color-border);
}
.office-track__seg.office-link--pending {
  background: repeating-linear-gradient(
    90deg,
    var(--pyt-color-border) 0 3px,
    transparent 3px 6px
  );
  opacity: 0.6;
}
.office-track__seg.office-link--filled {
  background: #4d6e1d;
}
.office-track__seg.office-link--flow {
  background: linear-gradient(90deg, #4d6e1d 0%, #d4af37 50%, #4d6e1d 100%);
  background-size: 200% 100%;
  animation: office-flow 0.9s linear infinite;
}
.office-track__baton {
  position: absolute;
  top: 50%;
  left: 0;
  width: 7px;
  height: 7px;
  margin-top: -3.5px;
  border-radius: 50%;
  background: #ffe27a;
  box-shadow: 0 0 6px 2px rgba(212, 175, 55, 0.85);
  animation: office-baton 0.9s ease-in-out infinite;
}
.office-agent {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  min-width: 0;
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
.office-agent--done .office-sprite,
.office-agent--retry .office-sprite {
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
  max-width: 100%;
  text-align: center;
  line-height: 1.15;
  overflow-wrap: anywhere;
  /* 定高 2 行：让 sprite 底部到地板的距离稳定，连线 bottom 锚点才不会随标签换行漂移。 */
  height: 24px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.office-agent__role {
  font-size: 9px;
  color: var(--pyt-color-text-hint);
  text-align: center;
  height: 11px;
  overflow: hidden;
  white-space: nowrap;
  max-width: 100%;
  text-overflow: ellipsis;
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
.office-agent__badge--retry {
  background: #c98a1c;
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
.office-step--retry {
  background: #fbe8c8;
  border-color: #c98a1c;
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
.office-step--retry .office-step__marker {
  background: #c98a1c;
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
/* ── Agent 串门（涌现协作可视）：反思/交接 NPC 走到工位通报 ── */
.office-visit {
  position: absolute;
  left: 8px;
  top: 26px;
  display: flex;
  align-items: center;
  gap: 4px;
  max-width: calc(100% - 16px);
  z-index: 4;
  pointer-events: none;
  animation: office-visit-in 460ms ease-out;
}
.office-visit__sprite {
  flex: 0 0 auto;
}
.office-visit__bubble {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-width: 220px;
  background: #fffbe9;
  border: 2px solid #8c6a23;
  border-radius: 8px;
  padding: 4px 8px;
  box-shadow: 0 2px 0 #8c6a23;
}
.office-visit__tag {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #8c6a23;
}
.office-visit__note {
  font-size: 11px;
  line-height: 1.3;
  color: #3a2a14;
  overflow-wrap: anywhere;
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
@keyframes office-flow {
  to { background-position: -200% 0; }
}
@keyframes office-baton {
  0% { left: 0; opacity: 0; }
  15% { opacity: 1; }
  85% { opacity: 1; }
  100% { left: 100%; opacity: 0; }
}
@keyframes office-visit-in {
  0% { transform: translateX(-14px); opacity: 0; }
  100% { transform: translateX(0); opacity: 1; }
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
  .office-agent__badge--work,
  .office-track__seg.office-link--flow,
  .office-track__baton,
  .office-visit {
    animation: none !important;
  }
}
</style>
