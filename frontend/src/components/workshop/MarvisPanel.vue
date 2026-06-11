<script setup lang="ts">
/**
 * MarvisPanel —— 工坊侧栏常驻「智能体工作室」面板。
 *
 * 多 Agent 可视化 + 实时「正在做什么」活动条：
 *   - 空闲态：像素智能体待命名单（R-16 阵容 12 人：百晓分诊/馆员/导师/考官/调试员/设计师/
 *     辩论官/审查官/校对官/安全员/学习总管/规划师）。
 *   - 运行态：一条**纵向工位流水线**，按真实 SSE `plan` + `trace` 实时驱动：
 *       · 顶部「实时活动条」展示当前在岗小人 + 人话标签 + spinner + 已等 Ns；
 *       · 下方每个工位有状态圈（待命/进行中/完成/重试/未过）；
 *       · 工位之间的**连接线在交接时点亮流动**，一颗发光「接力棒」沿线下滑，
 *         直观表达「上一个智能体把活交给下一个」。
 *   - 完工态：全部工位打勾，活动条转为「协同完成 · 已真实调用 N 个智能体」。
 *
 * 数据来自 useWorkshopActivityStore（只读）；答疑(ASK)与资源生成两条流都发 `plan`，
 * 故两者都会点亮流水线。像素美术复用已审计的 LPC npc-composites（与 AgentOfficeScene 同源）。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useWorkshopActivityStore } from '@stores/workshopActivity';
import type { SseTraceEvent } from '@/composables/useSseStream';

const activity = useWorkshopActivityStore();

// ── 像素小人（LPC npc-composites，与 AgentOfficeScene 同一套素材） ──
const SPRITE_BASE = '/assets/pixel/sprites/npc-composites';
function spriteBg(sprite: string): string {
  return `url(${SPRITE_BASE}/${sprite}.png)`;
}

// node → 在岗小人 sprite（对齐 AgentOfficeScene 的节点→sprite 映射）。
function nodeSprite(node: string): string {
  if (node === 'evidence_fetcher') return 'npc_librarian';
  if (node === 'grounding_critic') return 'npc_sage';
  if (node === 'hallucination_detector') return 'npc_student';
  if (node === 'safety_critic') return 'npc_guard';
  // R-16 答疑管线新工位（注意放在 TutorAgent 精确匹配之前/同级，revise 是带后缀节点名）。
  if (node === 'TutorAgent.revise') return 'npc_professor';
  if (node === 'code_runner') return 'npc_miner';
  if (node === 'tutor_diagram') return 'npc_gardener';
  if (node === 'solver') return 'npc_student';
  if (node === 'critic') return 'npc_sage';
  if (node === 'referee') return 'npc_mayor';
  if (node === 'TutorAgent' || node === 'image_understanding') return 'npc_tutor_ai';
  // P1 真协同节点
  if (node.startsWith('dispatch.StoryboardAgent')) return 'npc_gardener';
  if (node.startsWith('dispatch.ResourceDesignerAgent')) return 'npc_workshop';
  if (node.startsWith('dispatch.PracticeAgent')) return 'npc_trainer';
  if (node.startsWith('dispatch.PathPlannerAgent')) return 'npc_professor';
  if (node.startsWith('dispatch.DebugAgent')) return 'npc_coach';
  if (node === 'replan' || node.startsWith('replan')) return 'npc_mayor';
  if (node === 'debate' || node.startsWith('debate')) return 'npc_sage';
  if (/attempt/i.test(node) || /agent/i.test(node)) return 'npc_workshop';
  return 'npc_tutor_ai';
}
// plan 工位 kind → sprite（tutor：答疑流的「导师·作答」工位）。
const KIND_SPRITE: Record<string, string> = {
  evidence: 'npc_librarian',
  tutor: 'npc_tutor_ai',
  designer: 'npc_workshop',
  grounding: 'npc_sage',
  hallucination: 'npc_student',
  safety: 'npc_guard',
  // P1 真协同新增工位类型
  storyboard: 'npc_gardener',
  practice: 'npc_trainer',
  path: 'npc_professor',
  debug: 'npc_coach',
  replan: 'npc_mayor',
  debate: 'npc_sage',
};

const activeSprite = computed<string>(() => nodeSprite(activity.activeNode));

// 节点 → 人话标签（对齐 graph 节点名）。
function nodeLabel(node: string): string {
  if (!node) return '处理中';
  if (node === 'evidence_fetcher') return '正在检索课件证据';
  if (node === 'grounding_critic') return '正在核对课本引用';
  if (node === 'hallucination_detector') return '正在防幻觉核查';
  if (node === 'safety_critic') return '正在安全审查';
  // R-16 答疑管线新工位
  if (node === 'TutorAgent.revise') return '审查打回，导师返工修订中';
  if (node === 'code_runner') return '正在沙箱实跑验证代码';
  if (node === 'tutor_diagram') return '正在绘制图解';
  if (node === 'solver') return '辩论·正方正在给出解法';
  if (node === 'critic') return '辩论·反方正在挑错质疑';
  if (node === 'referee') return '辩论·裁判正在裁决定稿';
  if (node === 'TutorAgent' || node === 'image_understanding') return '正在作答（课件 RAG）';
  // P1 真协同节点
  if (node.startsWith('dispatch.StoryboardAgent')) return '正在制作教学动画';
  if (node.startsWith('dispatch.ResourceDesignerAgent')) return '正在生成学习资源';
  if (node.startsWith('dispatch.PracticeAgent')) return '正在出练习题';
  if (node.startsWith('dispatch.PathPlannerAgent')) return '正在规划学习路径';
  if (node.startsWith('dispatch.DebugAgent')) return '正在分析调试（矿洞补练）';
  if (node === 'replan' || node.startsWith('replan')) return '总管重规划中';
  if (node === 'debate' || node.startsWith('debate')) return '正在辩论复核';
  if (/attempt/i.test(node) || /agent/i.test(node)) return '正在生成学习资源';
  return '处理中';
}

const humanLabel = computed<string>(() => nodeLabel(activity.activeNode));

// 实时「已等 Ns」：处理中时每秒刷新 now。
const now = ref<number>(Date.now());
let timer: ReturnType<typeof setInterval> | null = null;
onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now();
  }, 1000);
});
onBeforeUnmount(() => {
  if (timer !== null) clearInterval(timer);
});

const elapsedSeconds = computed<number>(() => {
  if (!activity.isBusy || activity.startedAt === 0) return 0;
  return Math.max(0, Math.floor((now.value - activity.startedAt) / 1000));
});

// ── 工位（plan 模式）：node → 最新 trace 相位 → 状态 ──
type StationState = 'pending' | 'active' | 'done' | 'retry' | 'error';

function baseNode(node: string): string {
  return node.replace(/\.attempt\d+$/, '');
}

function lastTraceFor(node: string): SseTraceEvent | null {
  const target = baseNode(node);
  let last: SseTraceEvent | null = null;
  for (const t of activity.traces) {
    if (baseNode(t.node) === target) last = t;
  }
  return last;
}

function stationState(node: string): StationState {
  const last = lastTraceFor(node);
  if (!last) return 'pending';
  if (last.phase === 'start' || last.status === 'running') return 'active';
  if (last.status === 'fail') return 'error';
  if (last.status === 'retry') return 'retry';
  return 'done';
}

function stationElapsed(node: string): number {
  const last = lastTraceFor(node);
  if (!last || last.phase === 'start') return 0;
  return last.elapsed_ms ?? 0;
}

interface StationVm {
  node: string;
  label: string;
  state: StationState;
  sprite: string;
  elapsedMs: number;
}

const stations = computed<StationVm[]>(() => {
  const plan = activity.plan;
  if (!plan) return [];
  return plan.nodes.map((n) => ({
    node: n.node,
    label: n.agent_label,
    state: stationState(n.node),
    sprite: KIND_SPRITE[n.kind] ?? 'npc_workshop',
    elapsedMs: stationElapsed(n.node),
  }));
});

const completedCount = computed<number>(
  () => stations.value.filter((s) => s.state === 'done' || s.state === 'retry').length,
);

/** 连接线状态：上游完工后线亮起；上游完工且下游正在干 → 流动（接力棒下滑）。 */
function connectorClass(i: number): string {
  const a = stations.value[i];
  const b = stations.value[i + 1];
  if (!a || !b) return 'tl__line--pending';
  const upstreamDone = a.state === 'done' || a.state === 'retry';
  if (!upstreamDone) return 'tl__line--pending';
  if (b.state === 'active') return 'tl__line--flow';
  return 'tl__line--filled';
}

function isFlowing(i: number): boolean {
  return connectorClass(i) === 'tl__line--flow';
}

function stateGlyph(state: StationState): string {
  if (state === 'done') return '✓';
  if (state === 'retry') return '↻';
  if (state === 'error') return '✗';
  if (state === 'active') return '▶';
  return '·';
}

function subText(s: StationVm): string {
  if (s.state === 'active') return '进行中…';
  if (s.state === 'done') return s.elapsedMs > 0 ? `完成 · ${s.elapsedMs}ms` : '完成';
  if (s.state === 'retry') return '重试后通过';
  if (s.state === 'error') return '未通过';
  return '待命';
}

const statusText = computed<string>(() => {
  if (activity.isBusy) return '协同中';
  if (activity.status === 'done') return '已完成';
  if (activity.status === 'error') return '出错';
  return '待命';
});

// 完工/出错的活动条文案（不再显示 spinner）。
const finishLine = computed<string>(() => {
  if (activity.status === 'error') return '协同中断，请重试';
  const n = stations.value.length;
  return n > 0 ? `协同完成 · 已真实调用 ${n} 个智能体` : '协同完成';
});

// ── 空闲态待命名单（像素小人 + 角色，与工作室工位一致） ──
interface RosterMember {
  name: string;
  role: string;
  sprite: string;
}
// R-16 阵容 6→12：跟上 16 工具 + 协作管线（考官/调试员/辩论官/总管/规划师/分诊）。
const IDLE_ROSTER: readonly RosterMember[] = [
  { name: '百晓', role: '意图分诊', sprite: 'npc_tutor_ai' },
  { name: '馆员', role: '证据检索', sprite: 'npc_librarian' },
  { name: '导师', role: '答疑作答', sprite: 'npc_professor' },
  { name: '考官', role: '考考我·口试', sprite: 'npc_trainer' },
  { name: '调试员', role: '沙箱验证·修报错', sprite: 'npc_miner' },
  { name: '设计师', role: '六类资源生成', sprite: 'npc_workshop' },
  { name: '辩论官', role: '难题三方复核', sprite: 'npc_merchant' },
  { name: '审查官', role: '引用审查·打回返工', sprite: 'npc_sage' },
  { name: '校对官', role: '防幻觉', sprite: 'npc_student' },
  { name: '安全员', role: '安全审查', sprite: 'npc_guard' },
  { name: '学习总管', role: '看学练测编排', sprite: 'npc_mayor' },
  { name: '规划师', role: '学习路径规划', sprite: 'npc_sailor' },
];
</script>

<template>
  <section
    class="marvis"
    data-testid="marvis-panel"
  >
    <header class="marvis__head">
      <span class="marvis__title">✦ 多智能体工作室</span>
      <span
        class="marvis__status"
        :class="`marvis__status--${activity.status}`"
      >{{ statusText }}</span>
    </header>

    <!-- 运行/完工态：实时活动条 + 纵向工位流水线（交接动画） -->
    <div
      v-if="activity.hasRun"
      class="marvis__run"
      data-testid="marvis-active"
    >
      <p
        v-if="activity.label"
        class="marvis__task"
      >
        {{ activity.label }}
      </p>

      <!-- 实时活动条：当前在岗小人 + 人话标签 -->
      <div
        class="marvis__bar"
        :class="{ 'marvis__bar--done': !activity.isBusy }"
      >
        <span
          class="marvis__sprite marvis__sprite--bar"
          :class="{ 'marvis__sprite--working': activity.isBusy }"
          :style="{ backgroundImage: spriteBg(activeSprite) }"
          aria-hidden="true"
        />
        <span class="marvis__bar-body">
          <span class="marvis__bar-label">{{ activity.isBusy ? humanLabel : finishLine }}</span>
          <span class="marvis__bar-sub">
            <span
              v-if="activity.isBusy"
              class="marvis__spinner"
              aria-hidden="true"
            />
            <span
              v-if="activity.isBusy && elapsedSeconds > 0"
              class="marvis__elapsed"
            >已等 {{ elapsedSeconds }}s</span>
            <span
              v-else-if="!activity.isBusy && stations.length > 0"
              class="marvis__elapsed"
            >{{ completedCount }}/{{ stations.length }} 工位</span>
          </span>
        </span>
      </div>

      <!-- 纵向工位流水线：连接线在交接时流动 + 接力棒下滑 -->
      <ol
        v-if="stations.length > 0"
        class="tl"
      >
        <li
          v-for="(s, i) in stations"
          :key="s.node"
          class="tl__row"
          :class="`tl__row--${s.state}`"
          :data-testid="`marvis-station-${i}`"
        >
          <span class="tl__rail">
            <span
              v-if="i > 0"
              class="tl__line"
              :class="connectorClass(i - 1)"
              aria-hidden="true"
            >
              <span
                v-if="isFlowing(i - 1)"
                class="tl__baton"
              />
            </span>
            <span class="tl__node">
              <span
                class="tl__avatar"
                :class="{ 'tl__avatar--working': s.state === 'active' }"
                :style="{ backgroundImage: spriteBg(s.sprite) }"
                aria-hidden="true"
              />
              <span
                class="tl__glyph"
                :class="`tl__glyph--${s.state}`"
              >{{ stateGlyph(s.state) }}</span>
            </span>
          </span>
          <span class="tl__content">
            <span class="tl__label">{{ s.label }}</span>
            <span class="tl__sub">{{ subText(s) }}</span>
          </span>
        </li>
      </ol>
    </div>

    <!-- 空闲：像素智能体小人待命名单 -->
    <div
      v-else
      class="marvis__idle"
      data-testid="marvis-idle"
    >
      <p class="marvis__idle-hint">
        说说你想学什么，智能体团队随时待命。
      </p>
      <ul class="marvis__roster">
        <li
          v-for="member in IDLE_ROSTER"
          :key="member.name"
          class="marvis__member"
          :title="`${member.name} · ${member.role}`"
        >
          <span
            class="marvis__sprite marvis__sprite--lg marvis__sprite--idle"
            :style="{ backgroundImage: spriteBg(member.sprite) }"
            aria-hidden="true"
          />
          <span class="marvis__member-name">{{ member.name }}</span>
          <span class="marvis__member-role">{{ member.role }}</span>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.marvis {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  background: linear-gradient(180deg, #fff7e6 0%, #f3e4be 100%);
  border: 3px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  box-shadow: inset 0 0 0 2px rgba(255, 247, 230, 0.55);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}
.marvis__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.marvis__title {
  font-size: 13px;
  font-weight: 700;
}
.marvis__status {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: var(--pyt-radius-full);
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-muted);
}
.marvis__status--streaming {
  background: var(--pyt-color-info-bg);
  color: var(--pyt-color-info);
  border-color: var(--pyt-color-info);
  animation: marvis-pip 1.1s ease-in-out infinite;
}
.marvis__status--done {
  background: var(--pyt-color-success-bg);
  color: var(--pyt-color-success);
  border-color: var(--pyt-color-success);
}
.marvis__status--error {
  background: var(--pyt-color-danger-bg);
  color: var(--pyt-color-danger);
  border-color: var(--pyt-color-danger);
}

/* ── 像素小人（LPC 9×4 walkcycle，第 3 行=朝下，第 0 列=待机帧；按显示尺寸等比缩放） ── */
.marvis__sprite {
  flex: none;
  background-repeat: no-repeat;
  image-rendering: pixelated;
  filter: drop-shadow(0 2px 1px rgba(0, 0, 0, 0.28));
}
.marvis__sprite--lg {
  width: 44px;
  height: 44px;
  background-size: 396px 176px;
  background-position: 0 -88px;
}
.marvis__sprite--bar {
  width: 34px;
  height: 34px;
  background-size: 306px 136px;
  background-position: 0 -68px;
}
.marvis__sprite--idle {
  animation: marvis-idle 2.6s ease-in-out infinite;
}
.marvis__sprite--working {
  animation: marvis-work 0.55s ease-in-out infinite;
}

/* ── 运行态 ── */
.marvis__run {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.marvis__task {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--pyt-color-text-hint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.marvis__bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: rgba(255, 247, 230, 0.7);
  border: 1px solid var(--pyt-color-info);
  border-radius: var(--pyt-radius-md);
  box-shadow: inset 0 0 0 1px rgba(42, 111, 151, 0.12);
}
.marvis__bar--done {
  border-color: var(--pyt-color-success);
}
.marvis__bar-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.marvis__spinner {
  width: 10px;
  height: 10px;
  flex: none;
  border: 2px solid rgba(42, 111, 151, 0.25);
  border-top-color: var(--pyt-color-info);
  border-radius: 50%;
  animation: marvis-spin 0.9s linear infinite;
}
.marvis__bar-label {
  font-size: 12px;
  color: var(--pyt-color-info);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.marvis__bar--done .marvis__bar-label {
  color: var(--pyt-color-success);
}
.marvis__bar-sub {
  display: flex;
  align-items: center;
  gap: 6px;
}
.marvis__elapsed {
  flex: none;
  font-size: 11px;
  color: var(--pyt-color-text-muted);
  font-variant-numeric: tabular-nums;
}

/* ── 纵向工位流水线 + 交接动画 ── */
.tl {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.tl__row {
  display: grid;
  grid-template-columns: 34px 1fr;
  gap: 8px;
  align-items: stretch;
}
.tl__rail {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 34px;
}
/* 连接线：在两个工位之间（位于下游工位 node 上方）。 */
.tl__line {
  position: relative;
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--pyt-color-border);
}
.tl__line--pending {
  background: repeating-linear-gradient(
    180deg,
    var(--pyt-color-border) 0 3px,
    transparent 3px 6px
  );
  opacity: 0.7;
}
.tl__line--filled {
  background: #4d6e1d;
}
.tl__line--flow {
  background: linear-gradient(180deg, #4d6e1d 0%, #d4af37 50%, #4d6e1d 100%);
  background-size: 100% 200%;
  animation: tl-flow 0.9s linear infinite;
}
/* 接力棒：发光小点沿连接线下滑，表达「上一个智能体把活交给下一个」。 */
.tl__baton {
  position: absolute;
  left: 50%;
  top: 0;
  width: 7px;
  height: 7px;
  margin-left: -3.5px;
  border-radius: 50%;
  background: #ffe27a;
  box-shadow: 0 0 6px 2px rgba(212, 175, 55, 0.85);
  animation: tl-baton 0.9s ease-in-out infinite;
}
.tl__node {
  position: relative;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-soft);
}
.tl__row--active .tl__node {
  border-color: var(--pyt-color-info);
  box-shadow: 0 0 0 0 rgba(42, 111, 151, 0.5);
  animation: tl-pulse 1.2s ease-out infinite;
}
.tl__row--done .tl__node,
.tl__row--retry .tl__node {
  border-color: #4d6e1d;
}
.tl__row--error .tl__node {
  border-color: var(--pyt-color-danger);
}
.tl__row--pending .tl__node {
  opacity: 0.55;
}
.tl__avatar {
  width: 28px;
  height: 28px;
  background-repeat: no-repeat;
  background-size: 252px 112px;
  background-position: 0 -56px;
  image-rendering: pixelated;
}
.tl__avatar--working {
  animation: marvis-work 0.55s ease-in-out infinite;
}
.tl__glyph {
  position: absolute;
  right: -3px;
  bottom: -3px;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: var(--pyt-color-border);
}
.tl__glyph--active {
  background: var(--pyt-color-info);
}
.tl__glyph--done {
  background: #4d6e1d;
}
.tl__glyph--retry {
  background: #c98a1c;
}
.tl__glyph--error {
  background: var(--pyt-color-danger);
}
.tl__glyph--pending {
  background: transparent;
  color: var(--pyt-color-text-muted);
}
.tl__content {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 1px;
  min-width: 0;
  padding: 2px 0 6px;
}
.tl__label {
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tl__row--pending .tl__label {
  color: var(--pyt-color-text-muted);
  font-weight: 400;
}
.tl__sub {
  font-size: 10px;
  color: var(--pyt-color-text-muted);
}
.tl__row--active .tl__sub {
  color: var(--pyt-color-info);
}
.tl__row--done .tl__sub,
.tl__row--retry .tl__sub {
  color: var(--pyt-color-success);
}
.tl__row--error .tl__sub {
  color: var(--pyt-color-danger);
}

/* ── 空闲：像素小人待命名单 ── */
.marvis__idle-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--pyt-color-text-hint);
}
.marvis__roster {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px 4px;
}
.marvis__member {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  min-width: 0;
}
.marvis__member-name {
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.marvis__member-role {
  font-size: 9px;
  color: var(--pyt-color-text-muted);
  text-align: center;
}

@keyframes marvis-spin {
  to { transform: rotate(360deg); }
}
@keyframes marvis-idle {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
}
@keyframes marvis-work {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px) scale(1.05); }
}
@keyframes marvis-pip {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}
@keyframes tl-flow {
  to { background-position: 0 -200%; }
}
@keyframes tl-baton {
  0% { top: -2px; opacity: 0; }
  20% { opacity: 1; }
  80% { opacity: 1; }
  100% { top: 12px; opacity: 0; }
}
@keyframes tl-pulse {
  0% { box-shadow: 0 0 0 0 rgba(42, 111, 151, 0.5); }
  100% { box-shadow: 0 0 0 7px rgba(42, 111, 151, 0); }
}
@media (prefers-reduced-motion: reduce) {
  .marvis__spinner,
  .marvis__sprite--idle,
  .marvis__sprite--working,
  .tl__avatar--working,
  .tl__line--flow,
  .tl__baton,
  .marvis__status--streaming,
  .tl__row--active .tl__node {
    animation: none;
  }
}
</style>
