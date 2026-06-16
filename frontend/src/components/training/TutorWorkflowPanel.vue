<script setup lang="ts">
/**
 * TutorWorkflowPanel —— 做题页「AI 导学助手」（对标 Alethicode UnifiedAgentPanel 收敛版）。
 *
 * - 阶段卡片式导学：每个动作 → 一张卡片（题目导读/思路分析/骨架/错因诊断/AC复盘/迁移题/
 *   知识复盘/执行轨迹/可视化/Parsons 拼装/对话），由后端工作流状态机权威推进；
 * - available_actions 渲染成「下一步」按钮链，关键节点感知环境（带 submission_id / code）；
 * - 基于学生画像作答（后端 learner_block 注入）+ 与百晓工坊联动（一键去工坊生成针对本题资源）；
 * - 防泄答：后端 prompt 硬约束不给可直接提交的完整答案。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import MarkdownView from '@/components/common/MarkdownView.vue';
import { useSseStream } from '@/composables/useSseStream';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import {
  getActiveWorkflowSession,
  recordTutorWorkflowFeedback,
  type AiReplyCardDto,
  type ErrorDiagnosisCardDto,
  type ExecutionTraceCardDto,
  type IdeateAnalysisCardDto,
  type KnowledgeReviewCardDto,
  type ParsonsCardDto,
  type PostAcCardDto,
  type ProblemGuideCardDto,
  type SkeletonCodeCardDto,
  type TransferProblemCardDto,
  type VisualizeCardDto,
  type WorkflowActionDto,
  type WorkflowCardKey,
  type WorkflowCardPayload,
  type WorkflowEvent,
  type WorkflowRunResultDto,
} from '@/api/tutorWorkflow';

const props = defineProps<{
  problemId: number;
  kcCodes: string[];
  submissionId?: number | null;
  initialEventRequest?: { event: WorkflowEvent; nonce: number } | null;
}>();

const emit = defineEmits<{
  (e: 'insert-code', code: string): void;
  (e: 'close'): void;
}>();

const learning = useLearningStore();
const toast = useToastStore();
const router = useRouter();

interface CardEntry {
  key: WorkflowCardKey;
  payload: WorkflowCardPayload;
  seq: number;
  userMessage?: string;
}

const cards = ref<CardEntry[]>([]);
const availableActions = ref<WorkflowActionDto[]>([]);
const phase = ref<string>('READING');
const sessionKey = ref('');
const chatInput = ref('');
const bodyRef = ref<HTMLElement | null>(null);
const loaded = ref(false);
const lastInitialNonce = ref(0);
const cardFeedback = ref<Record<string, 'helpful' | 'not_helpful'>>({});

const sse = useSseStream<WorkflowRunResultDto>('/api/tutor/workflow/runs');
const running = computed(
  () => sse.status.value === 'streaming' || sse.status.value === 'connecting',
);
const recommendedAction = computed<WorkflowActionDto | null>(
  () => availableActions.value.find((action) => action.recommended) ?? null,
);

// 起手动作 + 始终可用的 4 个辅助动作（与后端 PHASE_ACTIONS + AUX 对齐；后端最终以 available_actions 为准）。
const STARTER_ACTIONS: WorkflowActionDto[] = [
  { key: 'problem_guide', label: '题目导读', event: 'READING' },
  { key: 'ideate', label: '思路分析', event: 'IDEATING' },
  { key: 'skeleton', label: '骨架代码', event: 'SKELETON' },
];
const AUX_ACTIONS: WorkflowActionDto[] = [
  { key: 'knowledge_review', label: '知识点复盘', event: 'KNOWLEDGE_REVIEW' },
  { key: 'execution_trace', label: '执行轨迹讲解', event: 'EXECUTION_TRACE' },
  { key: 'visualize', label: '画一下', event: 'VISUALIZE' },
  { key: 'parsons', label: '拼装挑战', event: 'PARSONS' },
];

function withAux(actions: WorkflowActionDto[]): WorkflowActionDto[] {
  const seen = new Set(actions.map((a) => a.key));
  return [...actions, ...AUX_ACTIONS.filter((a) => !seen.has(a.key))];
}

function scrollBottom(): void {
  void nextTick(() => {
    const el = bodyRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

onMounted(load);

async function load(): Promise<void> {
  try {
    const snap = await getActiveWorkflowSession(props.problemId);
    sessionKey.value = snap.sessionKey;
    cards.value = snap.cards.map((c) => ({ key: c.cardKey, payload: c.payload, seq: c.seq }));
    phase.value = snap.phase;
  } catch {
    cards.value = [];
  }
  availableActions.value = withAux(STARTER_ACTIONS);
  loaded.value = true;
  void consumeInitialRequest();
  scrollBottom();
}

/** 关键节点感知环境：按事件携带 submission_id / 当前编辑器代码 / intent 等。 */
function eventDataFor(event: WorkflowEvent): Record<string, unknown> | undefined {
  if (event === 'EXECUTION_TRACE' || event === 'VISUALIZE') {
    const code = (learning.training.code ?? '').trim();
    return code ? { code } : undefined;
  }
  if (event === 'ERROR_FEEDBACK' || event === 'AC_REVIEW') {
    return props.submissionId ? { submission_id: props.submissionId } : undefined;
  }
  return undefined;
}

async function runEvent(event: WorkflowEvent, eventData?: Record<string, unknown>): Promise<void> {
  if (running.value) return;
  const body: Record<string, unknown> = { problemId: props.problemId, event };
  if (eventData) body.eventData = eventData;
  await sse.start(body);
  if (sse.status.value === 'done' && sse.result.value) {
    const r = sse.result.value;
    sessionKey.value = r.session_id || sessionKey.value;
    phase.value = r.phase;
    if (r.card && r.card_key) {
      const entry: CardEntry = { key: r.card_key, payload: r.card, seq: r.seq };
      const msg = eventData?.message;
      if (typeof msg === 'string' && msg) entry.userMessage = msg;
      cards.value = [...cards.value, entry];
    }
    if (Array.isArray(r.available_actions) && r.available_actions.length > 0) {
      availableActions.value = withAux(r.available_actions);
    }
    scrollBottom();
  } else {
    toast.show(sse.error.value?.message ?? '导学助手暂时没响应，再试一次', 'error');
  }
}

async function consumeInitialRequest(): Promise<void> {
  const request = props.initialEventRequest;
  if (!loaded.value || !request || request.nonce === lastInitialNonce.value) return;
  if (running.value) return;
  lastInitialNonce.value = request.nonce;
  await runEvent(request.event, eventDataFor(request.event));
}

watch(
  () => props.initialEventRequest,
  () => {
    void consumeInitialRequest();
  },
  { deep: true },
);

function onAction(action: WorkflowActionDto): void {
  void recordFeedback({
    eventType: 'ACTION_CLICKED',
    actionKey: action.key,
    payload: { event: action.event },
  });
  void runEvent(action.event, eventDataFor(action.event));
}

async function recordFeedback(request: {
  eventType: 'ACTION_CLICKED' | 'CHECKPOINT_COMPLETED' | 'CARD_HELPFUL' | 'CARD_NOT_HELPFUL';
  actionKey?: string;
  cardKey?: string;
  payload?: Record<string, unknown>;
}): Promise<void> {
  if (!sessionKey.value) return;
  try {
    await recordTutorWorkflowFeedback(sessionKey.value, request);
  } catch {
    // Feedback is a learning signal side channel; never block the tutor workflow.
  }
}

async function markCardFeedback(entry: CardEntry, value: 'helpful' | 'not_helpful'): Promise<void> {
  const id = cardId(entry);
  const eventType = value === 'helpful' ? 'CARD_HELPFUL' : 'CARD_NOT_HELPFUL';
  await recordFeedback({
    eventType,
    cardKey: entry.key,
    payload: { cardId: id, seq: entry.seq },
  });
  cardFeedback.value = { ...cardFeedback.value, [id]: value };
}

function sendChat(): void {
  const text = chatInput.value.trim();
  if (!text || running.value) return;
  chatInput.value = '';
  void runEvent('CHAT', { message: text });
}

function insertSkeleton(code: string): void {
  emit('insert-code', code);
  toast.show('已把骨架代码插入编辑器', 'success');
}

function goWorkshop(): void {
  void router.push({ path: '/workshop' });
}

function goProfile(): void {
  void router.push({ path: '/profile' });
}

// ── 类型化访问器（按 card key 把联合 payload 收窄给模板用，避免 any） ──
const asGuide = (p: WorkflowCardPayload): ProblemGuideCardDto => p as ProblemGuideCardDto;
const asIdeate = (p: WorkflowCardPayload): IdeateAnalysisCardDto => p as IdeateAnalysisCardDto;
const asSkeleton = (p: WorkflowCardPayload): SkeletonCodeCardDto => p as SkeletonCodeCardDto;
const asError = (p: WorkflowCardPayload): ErrorDiagnosisCardDto => p as ErrorDiagnosisCardDto;
const asPostAc = (p: WorkflowCardPayload): PostAcCardDto => p as PostAcCardDto;
const asTransfer = (p: WorkflowCardPayload): TransferProblemCardDto => p as TransferProblemCardDto;
const asReply = (p: WorkflowCardPayload): AiReplyCardDto => p as AiReplyCardDto;
const asReview = (p: WorkflowCardPayload): KnowledgeReviewCardDto => p as KnowledgeReviewCardDto;
const asTrace = (p: WorkflowCardPayload): ExecutionTraceCardDto => p as ExecutionTraceCardDto;
const asVisualize = (p: WorkflowCardPayload): VisualizeCardDto => p as VisualizeCardDto;
const asParsons = (p: WorkflowCardPayload): ParsonsCardDto => p as ParsonsCardDto;

const CARD_TITLES: Record<WorkflowCardKey, string> = {
  problem_guide: '题目导读',
  ideate_analysis: '思路分析',
  skeleton_code: '骨架代码',
  error_diagnosis: '错因诊断',
  post_ac: 'AC 复盘',
  transfer_problem: '迁移练习',
  ai_reply: '百晓回复',
  knowledge_review: '知识点复盘',
  execution_trace: '执行轨迹',
  visualize: '可视化图解',
  parsons: '拼装挑战',
};

// ── Parsons 拼装：点选积木拼顺序，自检对照 correct_order ──
const parsonsPicks = ref<Record<string, string[]>>({});
const parsonsResult = ref<Record<string, 'correct' | 'wrong' | ''>>({});
function parsonsPick(cardId: string, blockId: string): void {
  const cur = parsonsPicks.value[cardId] ? [...parsonsPicks.value[cardId]] : [];
  const at = cur.indexOf(blockId);
  if (at >= 0) cur.splice(at, 1);
  else cur.push(blockId);
  parsonsPicks.value = { ...parsonsPicks.value, [cardId]: cur };
  parsonsResult.value = { ...parsonsResult.value, [cardId]: '' };
}
function parsonsCheck(cardId: string, correct: string[]): void {
  const cur = parsonsPicks.value[cardId] ?? [];
  const ok = cur.length === correct.length && cur.every((id, i) => id === correct[i]);
  parsonsResult.value = { ...parsonsResult.value, [cardId]: ok ? 'correct' : 'wrong' };
}
function cardId(entry: CardEntry): string {
  return (entry.payload as { card_id?: string }).card_id ?? `seq-${entry.seq}`;
}
</script>

<template>
  <section
    class="tw-panel"
    data-testid="tutor-workflow-panel"
  >
    <header class="tw-panel__head">
      <div>
        <h3>AI 导学助手</h3>
        <span class="tw-panel__phase">阶段：{{ phase }}</span>
      </div>
      <div class="tw-panel__head-actions">
        <button
          type="button"
          class="tw-link"
          title="查看我的学习画像"
          @click="goProfile"
        >
          我的画像
        </button>
        <button
          type="button"
          class="tw-link"
          title="去百晓工坊生成针对本题的资源"
          @click="goWorkshop"
        >
          去工坊
        </button>
        <button
          type="button"
          class="tw-close"
          aria-label="关闭"
          @click="emit('close')"
        >
          ×
        </button>
      </div>
    </header>

    <div
      ref="bodyRef"
      class="tw-panel__body"
    >
      <p
        v-if="cards.length === 0 && !running"
        class="tw-empty"
      >
        点下面的「下一步」开始：先<strong>题目导读</strong>，再<strong>思路分析</strong>、<strong>骨架代码</strong>，
        提交后可看<strong>错因诊断</strong>/<strong>AC 复盘</strong>。助教只引导、不给完整答案。
      </p>

      <article
        v-for="entry in cards"
        :key="entry.seq"
        class="tw-card"
        :data-card="entry.key"
      >
        <p
          v-if="entry.userMessage"
          class="tw-card__user"
        >
          🙋 {{ entry.userMessage }}
        </p>
        <header class="tw-card__title">
          {{ CARD_TITLES[entry.key] }}
        </header>

        <!-- 题目导读 -->
        <template v-if="entry.key === 'problem_guide'">
          <p class="tw-card__lead">
            {{ asGuide(entry.payload).problem_restatement }}
          </p>
          <p class="tw-kv"><b>输入/输出关注：</b>{{ asGuide(entry.payload).input_output_focus }}</p>
          <p class="tw-kv"><b>关键观察：</b>{{ asGuide(entry.payload).key_observation }}</p>
          <ul class="tw-list">
            <li
              v-for="(q, i) in asGuide(entry.payload).starter_questions"
              :key="i"
            >
              {{ q }}
            </li>
          </ul>
        </template>

        <!-- 思路分析 -->
        <template v-else-if="entry.key === 'ideate_analysis'">
          <p class="tw-card__lead">
            {{ asIdeate(entry.payload).analysis }}
          </p>
          <ol class="tw-list tw-list--ol">
            <li
              v-for="(s, i) in asIdeate(entry.payload).steps"
              :key="i"
            >
              {{ s }}
            </li>
          </ol>
          <p
            v-if="asIdeate(entry.payload).misconception_alert"
            class="tw-alert"
          >
            ⚠ {{ asIdeate(entry.payload).misconception_alert }}
          </p>
        </template>

        <!-- 骨架代码 -->
        <template v-else-if="entry.key === 'skeleton_code'">
          <p class="tw-card__lead">
            {{ asSkeleton(entry.payload).description }}
          </p>
          <pre class="tw-code"><code>{{ asSkeleton(entry.payload).skeleton }}</code></pre>
          <button
            type="button"
            class="tw-btn"
            @click="insertSkeleton(asSkeleton(entry.payload).skeleton)"
          >
            ↧ 插入骨架到编辑器
          </button>
        </template>

        <!-- 错因诊断 -->
        <template v-else-if="entry.key === 'error_diagnosis'">
          <p class="tw-kv"><b>根因：</b>{{ asError(entry.payload).root_cause }}</p>
          <p class="tw-kv"><b>程序现在在做：</b>{{ asError(entry.payload).what_program_is_doing }}</p>
          <p class="tw-kv"><b>应有行为：</b>{{ asError(entry.payload).expected_behavior }}</p>
          <p class="tw-kv"><b>修复方向：</b>{{ asError(entry.payload).fix_direction }}</p>
          <p
            v-if="asError(entry.payload).encouragement"
            class="tw-encourage"
          >
            💪 {{ asError(entry.payload).encouragement }}
          </p>
        </template>

        <!-- AC 复盘 -->
        <template v-else-if="entry.key === 'post_ac'">
          <p class="tw-card__lead">
            {{ asPostAc(entry.payload).success_summary }}
          </p>
          <p class="tw-kv"><b>关键一步：</b>{{ asPostAc(entry.payload).key_action }}</p>
          <ul class="tw-list">
            <li
              v-for="(n, i) in asPostAc(entry.payload).code_quality_notes"
              :key="i"
            >
              {{ n }}
            </li>
          </ul>
          <p class="tw-kv"><b>下一步练习：</b>{{ asPostAc(entry.payload).next_practice_direction }}</p>
        </template>

        <!-- 迁移练习 -->
        <template v-else-if="entry.key === 'transfer_problem'">
          <p class="tw-card__lead">
            <b>{{ asTransfer(entry.payload).title }}</b>
          </p>
          <p>{{ asTransfer(entry.payload).description }}</p>
          <p class="tw-kv"><b>输入：</b>{{ asTransfer(entry.payload).input_description }}</p>
          <p class="tw-kv"><b>输出：</b>{{ asTransfer(entry.payload).output_description }}</p>
          <div
            v-for="(s, i) in asTransfer(entry.payload).samples"
            :key="i"
            class="tw-sample"
          >
            <pre><code>输入：{{ s.input }}
输出：{{ s.output }}</code></pre>
          </div>
          <p
            v-if="asTransfer(entry.payload).hint"
            class="tw-kv"
          >
            <b>提示：</b>{{ asTransfer(entry.payload).hint }}
          </p>
        </template>

        <!-- 百晓回复（CHAT） -->
        <template v-else-if="entry.key === 'ai_reply'">
          <MarkdownView :source="asReply(entry.payload).content" />
        </template>

        <!-- 知识点复盘 -->
        <template v-else-if="entry.key === 'knowledge_review'">
          <MarkdownView :source="asReview(entry.payload).review_content" />
          <ul
            v-if="asReview(entry.payload).courseware_refs.length"
            class="tw-refs"
          >
            <li
              v-for="(r, i) in asReview(entry.payload).courseware_refs"
              :key="i"
            >
              <RouterLink
                v-if="r.page_id"
                :to="`/library?pageId=${r.page_id}`"
              >
                第 {{ r.page_no }} 页
              </RouterLink>
              <span v-else>第 {{ r.page_no }} 页</span>
            </li>
          </ul>
          <ul class="tw-list">
            <li
              v-for="(s, i) in asReview(entry.payload).practice_suggestions"
              :key="i"
            >
              {{ s }}
            </li>
          </ul>
        </template>

        <!-- 执行轨迹 -->
        <template v-else-if="entry.key === 'execution_trace'">
          <p class="tw-card__lead">
            {{ asTrace(entry.payload).summary }}
          </p>
          <p class="tw-kv"><b>追踪输入：</b>{{ asTrace(entry.payload).input_used }}</p>
          <ol class="tw-list tw-list--ol">
            <li
              v-for="(st, i) in asTrace(entry.payload).steps"
              :key="i"
            >
              <code>{{ st.line_ref }}</code> · {{ st.action }}
            </li>
          </ol>
          <p
            v-if="asTrace(entry.payload).key_insight"
            class="tw-kv"
          >
            <b>关键点：</b>{{ asTrace(entry.payload).key_insight }}
          </p>
        </template>

        <!-- 可视化（mermaid/ascii 源码 + 无障碍替代文本） -->
        <template v-else-if="entry.key === 'visualize'">
          <p class="tw-card__lead">
            {{ asVisualize(entry.payload).title }}
          </p>
          <pre class="tw-code tw-diagram"><code>{{ asVisualize(entry.payload).diagram }}</code></pre>
          <p
            v-if="asVisualize(entry.payload).caption"
            class="tw-kv"
          >
            {{ asVisualize(entry.payload).caption }}
          </p>
        </template>

        <!-- Parsons 拼装：点选积木拼顺序，自检 -->
        <template v-else-if="entry.key === 'parsons'">
          <p class="tw-card__lead">
            {{ asParsons(entry.payload).prompt }}
          </p>
          <p class="tw-kv"><b>本练目标：</b>{{ asParsons(entry.payload).drill_goal }}</p>
          <div class="tw-parsons">
            <button
              v-for="b in [...asParsons(entry.payload).blocks, ...asParsons(entry.payload).distractors]"
              :key="b.id"
              type="button"
              class="tw-parsons__block"
              :class="{ 'tw-parsons__block--picked': (parsonsPicks[cardId(entry)] || []).includes(b.id) }"
              :style="{ marginLeft: `${b.indent * 16}px` }"
              @click="parsonsPick(cardId(entry), b.id)"
            >
              <code>{{ b.code }}</code>
            </button>
          </div>
          <div class="tw-parsons__foot">
            <span class="tw-parsons__order">已选：{{ (parsonsPicks[cardId(entry)] || []).length }} 块</span>
            <button
              type="button"
              class="tw-btn"
              @click="parsonsCheck(cardId(entry), asParsons(entry.payload).correct_order)"
            >
              检查顺序
            </button>
            <span
              v-if="parsonsResult[cardId(entry)] === 'correct'"
              class="tw-ok"
            >✓ 顺序正确</span>
            <span
              v-else-if="parsonsResult[cardId(entry)] === 'wrong'"
              class="tw-no"
            >✗ 再调整一下</span>
          </div>
        </template>
        <footer class="tw-card-feedback">
          <button
            type="button"
            class="tw-card-feedback__btn"
            data-testid="card-helpful"
            :disabled="cardFeedback[cardId(entry)] === 'helpful'"
            @click="markCardFeedback(entry, 'helpful')"
          >
            有帮助
          </button>
          <button
            type="button"
            class="tw-card-feedback__btn"
            data-testid="card-not-helpful"
            :disabled="cardFeedback[cardId(entry)] === 'not_helpful'"
            @click="markCardFeedback(entry, 'not_helpful')"
          >
            没帮助
          </button>
          <span
            v-if="cardFeedback[cardId(entry)]"
            class="tw-card-feedback__state"
            data-testid="card-feedback-state"
          >
            已记录
          </span>
        </footer>
      </article>

      <p
        v-if="running"
        class="tw-running"
      >
        百晓思考中…
      </p>
    </div>

    <!-- 下一步动作链（available_actions 驱动 + 4 个辅助动作） -->
    <div
      class="tw-actions"
      data-testid="tw-actions"
    >
      <section
        v-if="recommendedAction"
        class="tw-recommend"
        data-testid="coach-recommended-action"
      >
        <span class="tw-recommend__eyebrow">推荐下一步</span>
        <strong>{{ recommendedAction.label }}</strong>
        <p v-if="recommendedAction.reason">
          {{ recommendedAction.reason }}
        </p>
      </section>
      <button
        v-for="a in availableActions"
        :key="a.key"
        type="button"
        class="tw-action"
        :class="{ 'tw-action--recommended': a.recommended }"
        :disabled="running"
        @click="onAction(a)"
      >
        <span
          v-if="a.recommended"
          class="tw-action__tag"
        >
          推荐
        </span>
        {{ a.label }}
      </button>
    </div>

    <!-- 自由提问（CHAT） -->
    <form
      class="tw-chat"
      @submit.prevent="sendChat"
    >
      <input
        v-model="chatInput"
        class="tw-chat__input"
        type="text"
        placeholder="卡住了？直接问思路、要提示…"
        :disabled="running"
      >
      <button
        type="submit"
        class="tw-chat__send"
        :disabled="running || !chatInput.trim()"
      >
        问
      </button>
    </form>
  </section>
</template>

<style scoped>
.tw-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}
.tw-panel__head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid rgba(74, 54, 29, 0.24);
  background: rgba(255, 252, 242, 0.82);
}
.tw-panel__head h3 {
  margin: 0;
  font-family: var(--pyt-font-read);
  font-size: 15px;
}
.tw-panel__phase {
  font-size: 11px;
  color: var(--pyt-color-text-hint);
}
.tw-panel__head-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
.tw-link {
  font-family: inherit;
  font-size: 11px;
  padding: 3px 8px;
  cursor: pointer;
  background: var(--pyt-color-info-bg, #e6f2fb);
  border: 1px solid var(--pyt-color-info, #2a6f97);
  color: #1b4a6b;
  border-radius: 4px;
}
.tw-close {
  width: 26px;
  height: 26px;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
}
.tw-panel__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tw-empty {
  font-size: 13px;
  line-height: 1.7;
  color: var(--pyt-color-text-hint);
}
.tw-card {
  border: 2px solid var(--pyt-color-border);
  border-radius: 8px;
  background: rgba(255, 252, 242, 0.92);
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.tw-card__user {
  margin: 0 0 2px;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
}
.tw-card__title {
  font-family: var(--pyt-font-read);
  font-weight: 700;
  font-size: 14px;
  color: var(--pyt-color-text-primary);
  border-bottom: 1px dashed rgba(74, 54, 29, 0.3);
  padding-bottom: 4px;
}
.tw-card-feedback {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  padding-top: 6px;
  border-top: 1px dashed rgba(74, 54, 29, 0.22);
}
.tw-card-feedback__btn {
  font-family: inherit;
  font-size: 11px;
  padding: 3px 8px;
  cursor: pointer;
  color: var(--pyt-color-text-muted);
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(74, 54, 29, 0.28);
  border-radius: 999px;
}
.tw-card-feedback__btn:disabled {
  opacity: 0.62;
  cursor: default;
}
.tw-card-feedback__state {
  font-size: 11px;
  color: var(--pyt-color-success, #2d6a4f);
}
.tw-card__lead {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
}
.tw-kv {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}
.tw-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.6;
  display: grid;
  gap: 2px;
}
.tw-list--ol {
  list-style: decimal;
}
.tw-alert {
  margin: 0;
  font-size: 12px;
  color: #7a5210;
  background: #f5e2c1;
  border: 1px solid #c2871f;
  border-radius: 4px;
  padding: 4px 8px;
}
.tw-encourage {
  margin: 0;
  font-size: 12px;
  color: #2d5b1b;
}
.tw-code {
  margin: 0;
  padding: 8px 10px;
  background: var(--pyt-color-terminal-bg, #111827);
  color: var(--pyt-color-terminal-text, #e8f4ff);
  border-radius: 6px;
  overflow-x: auto;
  font-family: 'Consolas', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre;
}
.tw-diagram {
  background: #1b2330;
}
.tw-sample pre {
  margin: 4px 0;
  padding: 6px 10px;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
  font-family: 'Consolas', monospace;
  font-size: 12px;
  white-space: pre-wrap;
}
.tw-refs {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tw-refs a {
  font-size: 11px;
  padding: 2px 8px;
  background: var(--pyt-color-info-bg, #e6f2fb);
  border: 1px solid var(--pyt-color-info, #2a6f97);
  color: #1b4a6b;
  border-radius: 4px;
  text-decoration: none;
}
.tw-btn {
  align-self: flex-start;
  font-family: inherit;
  font-size: 12px;
  padding: 4px 12px;
  cursor: pointer;
  background: var(--pyt-color-info, #2a6f97);
  color: #fff;
  border: 2px solid var(--pyt-color-border-strong, #2a1a05);
  border-radius: 4px;
}
.tw-parsons {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.tw-parsons__block {
  text-align: left;
  font-family: 'Consolas', monospace;
  font-size: 12px;
  padding: 4px 8px;
  cursor: pointer;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
}
.tw-parsons__block--picked {
  background: #ffe098;
  border-color: var(--pyt-color-info, #2a6f97);
}
.tw-parsons__foot {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.tw-ok {
  color: #2d5b1b;
  font-weight: 700;
}
.tw-no {
  color: #b3261e;
  font-weight: 700;
}
.tw-running {
  font-size: 12px;
  color: var(--pyt-color-text-muted);
}
.tw-actions {
  flex: 0 0 auto;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 12px;
  border-top: 1px dashed rgba(74, 54, 29, 0.3);
}
.tw-recommend {
  flex: 1 1 100%;
  display: grid;
  gap: 3px;
  padding: 8px 10px;
  border: 1px solid rgba(42, 111, 151, 0.35);
  border-radius: 8px;
  background: rgba(230, 242, 251, 0.82);
}
.tw-recommend__eyebrow {
  font-size: 10px;
  color: #1b4a6b;
}
.tw-recommend strong {
  font-family: var(--pyt-font-read);
  font-size: 13px;
}
.tw-recommend p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--pyt-color-text-muted);
}
.tw-action {
  font-family: inherit;
  font-size: 12px;
  padding: 5px 12px;
  cursor: pointer;
  background: var(--pyt-color-warning-bg, #ffe9b8);
  border: 2px solid var(--pyt-color-border-strong, #2a1a05);
  border-radius: 4px;
  box-shadow: 2px 2px 0 var(--pyt-color-border-strong, #2a1a05);
  color: var(--pyt-color-text-primary);
}
.tw-action--recommended {
  border-color: var(--pyt-color-info, #2a6f97);
  background: var(--pyt-color-info-bg, #e6f2fb);
  color: #1b4a6b;
}
.tw-action__tag {
  display: inline-block;
  margin-right: 4px;
  padding: 1px 4px;
  border-radius: 999px;
  color: #fff;
  background: var(--pyt-color-info, #2a6f97);
}
.tw-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}
.tw-chat {
  flex: 0 0 auto;
  display: flex;
  gap: 6px;
  padding: 8px 12px 10px;
}
.tw-chat__input {
  flex: 1 1 auto;
  min-width: 0;
  padding: 6px 10px;
  font: inherit;
  font-size: 13px;
  border: 2px solid var(--pyt-color-border);
  border-radius: 4px;
  background: var(--pyt-color-bg-primary, #fffdf6);
}
.tw-chat__send {
  flex: 0 0 auto;
  padding: 6px 14px;
  font-family: inherit;
  cursor: pointer;
  background: var(--pyt-color-info, #2a6f97);
  color: #fff;
  border: 2px solid var(--pyt-color-border-strong, #2a1a05);
  border-radius: 4px;
}
.tw-chat__send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
