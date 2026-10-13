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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import MarkdownView from '@/components/common/MarkdownView.vue';
import { useSseStream, type SseTraceEvent } from '@/composables/useSseStream';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import {
  getActiveWorkflowSession,
  recordTutorWorkflowFeedback,
  uploadTutorWorkflowImage,
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
  type TutorEpisodePlanDto,
  type TutorEpisodeStepDto,
  type TutorWorkflowRunRequestDto,
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

interface RetryableWorkflowRequest {
  event: WorkflowEvent;
  eventData?: Record<string, unknown>;
  requestId: string;
}

const cards = ref<CardEntry[]>([]);
const availableActions = ref<WorkflowActionDto[]>([]);
const phase = ref<string>('READING');
const sessionKey = ref('');
const episodePlan = ref<TutorEpisodePlanDto | null>(null);
const planResponse = ref('');
const planRedirect = ref('');
const chatInput = ref('');
const chatFileInput = ref<HTMLInputElement | null>(null);
interface ChatImageDraft {
  file: File;
  previewUrl: string;
  attachmentId?: string;
}
const chatImages = ref<ChatImageDraft[]>([]);
const chatImageReads = ref(0);
const canSendChat = computed(() => !running.value && !chatImageReads.value
  && (chatInput.value.trim().length > 0 || chatImages.value.length > 0));
const MAX_TUTOR_CODE_LENGTH = 20_000;
const MAX_CHAT_IMAGES = 3;
const MAX_CHAT_IMAGE_BYTES = 5 * 1024 * 1024;
const bodyRef = ref<HTMLElement | null>(null);
const loaded = ref(false);
const lastInitialNonce = ref(0);
const cardFeedback = ref<Record<string, 'helpful' | 'not_helpful'>>({});
const retryableRequest = ref<RetryableWorkflowRequest | null>(null);

const sse = useSseStream<WorkflowRunResultDto>('/api/tutor/workflow/runs');
const running = computed(
  () => sse.status.value === 'streaming' || sse.status.value === 'connecting',
);
interface CoachProgressItem {
  key: string;
  label: string;
  statusLabel: string;
}
const coachProgressItems = computed<CoachProgressItem[]>(() => {
  const latestByNode = new Map<string, CoachProgressItem>();
  for (const trace of sse.traces.value) {
    const label = coachProgressLabel(trace.node);
    if (!label) continue;
    latestByNode.set(trace.node, {
      key: trace.node,
      label,
      statusLabel: coachProgressStatus(trace),
    });
  }
  return [...latestByNode.values()];
});

// 辅助动作 key（与后端 _AUX_ACTIONS 对齐）：任意阶段可用，但收进「学习工具」抽屉，不挤占主线「下一步」。
const AUX_KEYS = new Set(['visualize', 'execution_trace', 'knowledge_review', 'parsons']);

// 无会话时的起手动作（READING 阶段）+ 辅助动作；进入会话后一律以后端 available_actions 为准。
// 后端已按当前 Phase 门控（如「错因诊断/AC 复盘」需 submission 才会出现、TRANSFER 需先有复盘方向），
// 前端不再无脑补齐所有按钮——这正是「并非所有阶段都能任意调用所有动作」的落地。
const STARTER_ACTIONS: WorkflowActionDto[] = [
  { key: 'problem_guide', label: '题目导读', event: 'READING', recommended: true, reason: '先读懂题目，再动手。' },
  { key: 'ideate', label: '思路分析', event: 'IDEATING' },
  { key: 'skeleton', label: '骨架代码', event: 'SKELETON' },
];
const AUX_ACTIONS: WorkflowActionDto[] = [
  { key: 'knowledge_review', label: '知识点复盘', event: 'KNOWLEDGE_REVIEW' },
  { key: 'execution_trace', label: '执行轨迹讲解', event: 'EXECUTION_TRACE' },
  { key: 'visualize', label: '画一下', event: 'VISUALIZE' },
  { key: 'parsons', label: '拼装挑战', event: 'PARSONS' },
];
const INITIAL_ACTIONS: WorkflowActionDto[] = [...STARTER_ACTIONS, ...AUX_ACTIONS];

// 主线「下一步」动作（推进阶段）与「学习工具」（辅助、收纳进抽屉）分组。
const phaseActions = computed<WorkflowActionDto[]>(
  () => availableActions.value.filter((a) => !AUX_KEYS.has(a.key)),
);
// 推荐项可能是阶段动作，也可能是辅助动作（coach 可推荐「画一下/执行轨迹」）：始终把推荐项作为主按钮。
const primaryAction = computed<WorkflowActionDto | null>(
  () => availableActions.value.find((a) => a.recommended)
    ?? phaseActions.value[0]
    ?? availableActions.value[0]
    ?? null,
);
const secondaryPhaseActions = computed<WorkflowActionDto[]>(
  () => phaseActions.value.filter((a) => a.key !== primaryAction.value?.key),
);
// 学习工具（辅助动作）收纳进抽屉；若推荐项本身就是某个工具，则不在抽屉里重复。
const toolActions = computed<WorkflowActionDto[]>(
  () => availableActions.value.filter((a) => AUX_KEYS.has(a.key) && a.key !== primaryAction.value?.key),
);
const activePlanStep = computed<TutorEpisodeStepDto | null>(
  () => episodePlan.value?.steps.find((step) => step.step_id === episodePlan.value?.current_step_id) ?? null,
);
const planIsTerminal = computed(
  () => !episodePlan.value || ['COMPLETED', 'SURRENDERED'].includes(episodePlan.value.status),
);
const planNeedsSubmission = computed(
  () => activePlanStep.value?.expected_evidence === 'SUBMISSION_OBSERVED'
    || activePlanStep.value?.expected_evidence === 'AC_SUBMISSION'
    || activePlanStep.value?.expected_evidence === 'TRANSFER_ATTEMPT',
);
const compactAvailable = computed(() => cards.value.filter((card) => card.key === 'ai_reply').length > 4);

// 「学习工具」抽屉与「思考过程」默认收起（用户要求：这些内容平时缩小收纳）。
const toolsOpen = ref(false);
const thinkOpen = ref(false);

function coachProgressLabel(node: string | undefined): string {
  if (!node) return '';
  if (node === 'coach.load_state') return '正在判断你卡在哪里';
  if (node === 'coach.decide') return '正在选择下一步提示';
  if (node.startsWith('coach.tool.') || node.startsWith('card.')) return '正在生成导学卡';
  if (node === 'coach.guardrail') return '正在检查是否过度提示';
  if (node === 'coach.update_state') return '正在更新导学状态';
  if (node === 'evidence_fetcher') return '正在查找相关知识';
  return '';
}

function coachProgressStatus(trace: SseTraceEvent): string {
  if (trace.phase !== 'finish') return '进行中';
  if (trace.status === 'ok') return '完成';
  if (trace.status === 'retry') return '重新调整';
  if (trace.status === 'fail') return '未通过';
  return '完成';
}

function scrollBottom(): void {
  void nextTick(() => {
    const el = bodyRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

onMounted(load);

// 组件销毁（收起助教 / 切题重建 / 路由离开）即掐断在途 SSE：
// 否则后端 LLM 会继续跑到底，且 await 之后的 toast 会在别的页面弹出。
let alive = true;
onBeforeUnmount(() => {
  alive = false;
  sse.abort();
  chatImages.value.forEach((image) => revokeImagePreview(image.previewUrl));
});

async function load(): Promise<void> {
  try {
    const snap = await getActiveWorkflowSession(props.problemId);
    if (!alive) return;
    sessionKey.value = snap.sessionKey;
    cards.value = snap.cards.map((c) => ({ key: c.cardKey, payload: c.payload, seq: c.seq }));
    phase.value = snap.phase;
    episodePlan.value = snap.episodePlan ?? null;
  } catch {
    if (!alive) return;
    cards.value = [];
  }
  if (!alive) return;
  availableActions.value = INITIAL_ACTIONS;
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
  const plan = episodePlan.value;
  if (event === 'PLAN_START' && plan) {
    return {
      plan_id: plan.plan_id,
      revision: plan.revision,
      reason: '学生开始已推荐的学习计划',
      trigger_source: 'student',
      current_phase: phase.value,
    };
  }
  if (event === 'PLAN_RESPONSE' && plan && activePlanStep.value) {
    return {
      plan_id: plan.plan_id,
      revision: plan.revision,
      step_id: activePlanStep.value.step_id,
      evidence_type: 'reflection',
      response: planResponse.value.trim(),
    };
  }
  if (event === 'PLAN_STEERING' && plan) {
    return { plan_id: plan.plan_id, revision: plan.revision, signal_type: 'pause' };
  }
  return undefined;
}

function startEpisodePlan(): void {
  void runEvent('PLAN_RECOMMEND');
}

function respondToPlan(): void {
  if (!planResponse.value.trim()) return;
  const data = eventDataFor('PLAN_RESPONSE');
  if (!data) return;
  void runEvent('PLAN_RESPONSE', data);
  planResponse.value = '';
}

function steerEpisodePlan(signal: 'pause' | 'resume' | 'skip' | 'take_over' | 'redirect'): void {
  const plan = episodePlan.value;
  if (!plan) return;
  if (signal === 'redirect' && !planRedirect.value.trim()) return;
  void runEvent('PLAN_STEERING', {
    plan_id: plan.plan_id,
    revision: plan.revision,
    signal_type: signal,
    ...(signal === 'redirect' ? { redirect_instruction: planRedirect.value.trim() } : {}),
  });
  if (signal === 'redirect') planRedirect.value = '';
}

function createWorkflowRequestId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = ((bytes[6] ?? 0) & 0x0f) | 0x40;
  bytes[8] = ((bytes[8] ?? 0) & 0x3f) | 0x80;
  const hex = [...bytes].map((value) => value.toString(16).padStart(2, '0'));
  return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10).join('')}`;
}

async function runEvent(
  event: WorkflowEvent,
  eventData?: Record<string, unknown>,
  requestId = createWorkflowRequestId(),
): Promise<void> {
  if (running.value) return;
  const code = eventData?.code;
  if (typeof code === 'string' && code.length > MAX_TUTOR_CODE_LENGTH) {
    toast.show('当前代码超过导学长度限制，请先精简代码', 'error');
    return;
  }
  const body: TutorWorkflowRunRequestDto = {
    problemId: props.problemId,
    event,
    requestId,
    ...(eventData ? { eventData } : {}),
  };
  retryableRequest.value = { event, ...(eventData ? { eventData } : {}), requestId };
  await sse.start(body);
  // 组件已销毁（含 onBeforeUnmount 的 abort：abort 会把 status 置成 done 但 result 为空）→
  // 不再落卡片、更不弹「导学助手暂时没响应」到别的页面。
  if (!alive) return;
  if (sse.status.value === 'done' && sse.result.value) {
    retryableRequest.value = null;
    const r = sse.result.value;
    sessionKey.value = r.session_id || sessionKey.value;
    phase.value = r.phase;
    episodePlan.value = r.episode_plan ?? null;
    if (r.card && r.card_key) {
      const entry: CardEntry = { key: r.card_key, payload: r.card, seq: r.seq };
      const msg = eventData?.message;
      if (typeof msg === 'string' && msg) entry.userMessage = msg;
      cards.value = [...cards.value, entry];
    }
    // 严格以后端 available_actions 为准（已按 Phase 门控 + 推荐排序），不再前端补齐辅助动作。
    if (Array.isArray(r.available_actions) && r.available_actions.length > 0) {
      availableActions.value = r.available_actions;
    }
    scrollBottom();
  } else {
    toast.show(sse.error.value?.message ?? '导学助手暂时没响应，再试一次', 'error');
  }
}

function retryLastWorkflowRequest(): void {
  const request = retryableRequest.value;
  if (!request || running.value) return;
  void runEvent(request.event, request.eventData, request.requestId);
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

watch(() => props.submissionId, async () => {
  if (!loaded.value) return;
  try {
    const snapshot = await getActiveWorkflowSession(props.problemId);
    if (alive) episodePlan.value = snapshot.episodePlan ?? null;
  } catch {
    // Keep the visible plan until the next successful session refresh.
  }
});

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
  if (value === 'not_helpful' && !running.value) {
    void runEvent('AGENT_FEEDBACK', {
      message: `@card:${id} 这张“${CARD_TITLES[entry.key]}”没有解决我的问题，请换一种方式解释。`,
    });
  }
}

function onChatImagesSelected(event: Event): void {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  input.value = '';
  for (const file of files) {
    if (chatImages.value.length + chatImageReads.value >= MAX_CHAT_IMAGES) {
      toast.show(`最多上传 ${MAX_CHAT_IMAGES} 张图片`, 'error');
      break;
    }
    if (!/^image\/(png|jpe?g|gif|webp)$/.test(file.type)) {
      toast.show('仅支持 PNG / JPG / GIF / WebP 图片', 'error');
      continue;
    }
    if (file.size > MAX_CHAT_IMAGE_BYTES) {
      toast.show('单张图片不能超过 5 MiB', 'error');
      continue;
    }
    const previewUrl =
      typeof URL.createObjectURL === 'function' ? URL.createObjectURL(file) : '';
    chatImages.value = [...chatImages.value, { file, previewUrl }];
  }
}

function removeChatImage(index: number): void {
  if (chatImageReads.value > 0) return;
  const removed = chatImages.value[index];
  if (removed) revokeImagePreview(removed.previewUrl);
  chatImages.value = chatImages.value.filter((_, i) => i !== index);
}

function revokeImagePreview(previewUrl: string): void {
  if (previewUrl && typeof URL.revokeObjectURL === 'function') {
    URL.revokeObjectURL(previewUrl);
  }
}

async function sendChat(): Promise<void> {
  const draft = chatInput.value;
  const text = draft.trim();
  if ((!text && !chatImages.value.length) || running.value || chatImageReads.value) return;
  const currentCode = learning.training.code ?? '';
  if (currentCode.length > MAX_TUTOR_CODE_LENGTH) {
    toast.show('当前代码超过导学长度限制，请先精简代码', 'error');
    return;
  }
  const message = text || '请结合这张图片帮我分析。';
  const images = [...chatImages.value];
  chatImageReads.value += 1;
  try {
    const uploadResults = await Promise.allSettled(
      images.map((image) => image.attachmentId
        ? Promise.resolve({ attachmentId: image.attachmentId })
        : uploadTutorWorkflowImage(sessionKey.value, image.file)),
    );
    if (!alive) return;
    let failedUpload = false;
    const attachmentIds: string[] = [];
    uploadResults.forEach((result, index) => {
      const image = images[index];
      if (result.status === 'fulfilled' && image) {
        image.attachmentId = result.value.attachmentId;
        attachmentIds.push(result.value.attachmentId);
      } else {
        failedUpload = true;
      }
    });
    if (failedUpload) {
      toast.show('图片上传失败，请重试；已上传的图片会保留', 'error');
      return;
    }
    if (chatInput.value === draft) chatInput.value = '';
    chatImages.value = chatImages.value.filter((image) => !images.includes(image));
    images.forEach((image) => revokeImagePreview(image.previewUrl));
    await runEvent('CHAT', {
      message,
      ...(currentCode.trim() ? { code: currentCode.trim() } : {}),
      ...(attachmentIds.length
        ? { attachment_ids: attachmentIds }
        : {}),
    });
  } catch {
    toast.show('图片上传失败，请重试', 'error');
  } finally {
    chatImageReads.value -= 1;
  }
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
          <ul
            v-if="asReply(entry.payload).courseware_refs?.length"
            class="tw-refs"
            aria-label="引用课件页"
          >
            <li
              v-for="(reference, index) in asReply(entry.payload).courseware_refs"
              :key="`${reference.page_id ?? 'page'}-${index}`"
            >
              <RouterLink
                v-if="reference.page_id"
                :to="`/library?pageId=${reference.page_id}`"
              >
                参考课件第 {{ reference.page_no }} 页
              </RouterLink>
            </li>
          </ul>
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

      <section
        v-if="episodePlan"
        class="tw-plan"
        data-testid="tutor-episode-plan"
        aria-label="个性化学习计划"
      >
        <header class="tw-plan__head">
          <strong>学习计划</strong>
          <span>{{ episodePlan.status === 'RECOMMENDED' ? '待开始' : episodePlan.status === 'ACTIVE' ? '进行中' : episodePlan.status === 'PAUSED' ? '已暂停' : episodePlan.status === 'COMPLETED' ? '已完成' : '已接管' }}</span>
        </header>
        <p class="tw-plan__objective">{{ episodePlan.objective }}</p>
        <ol class="tw-plan__steps">
          <li
            v-for="step in episodePlan.steps"
            :key="step.step_id"
            :class="{ 'is-active': step.step_id === episodePlan.current_step_id, 'is-done': step.status === 'DONE' || step.status === 'SKIPPED' }"
          >
            <b>{{ step.title }}</b>
            <small>{{ step.student_visible_reason }}</small>
          </li>
        </ol>
        <p
          v-if="activePlanStep && episodePlan.status === 'ACTIVE'"
          class="tw-plan__stop"
        >
          当前目标：{{ activePlanStep.stop_condition }}
          <template v-if="planNeedsSubmission"> 正式提交后自动推进。</template>
        </p>
        <button
          v-if="episodePlan.status === 'RECOMMENDED'"
          type="button"
          class="tw-btn"
          data-testid="plan-start"
          :disabled="running"
          @click="runEvent('PLAN_START', eventDataFor('PLAN_START'))"
        >
          开始计划
        </button>
        <template v-else-if="episodePlan.status === 'ACTIVE' && !planNeedsSubmission">
          <label class="tw-plan__response-label" for="tw-plan-response">完成这一步后，用自己的话说说你理解了什么</label>
          <textarea
            id="tw-plan-response"
            v-model="planResponse"
            class="tw-plan__input"
            rows="2"
            maxlength="2000"
          />
          <button
            type="button"
            class="tw-btn"
            data-testid="plan-respond"
            :disabled="running || !planResponse.trim()"
            @click="respondToPlan"
          >
            完成当前步骤
          </button>
        </template>
        <div
          v-if="episodePlan.status === 'ACTIVE' || episodePlan.status === 'PAUSED'"
          class="tw-plan__controls"
        >
          <button
            v-if="episodePlan.status === 'ACTIVE'"
            type="button"
            class="tw-chip"
            :disabled="running"
            @click="steerEpisodePlan('pause')"
          >暂停</button>
          <button
            v-else
            type="button"
            class="tw-chip"
            :disabled="running"
            @click="steerEpisodePlan('resume')"
          >继续计划</button>
          <button
            v-if="episodePlan.status === 'ACTIVE'"
            type="button"
            class="tw-chip"
            :disabled="running"
            @click="steerEpisodePlan('skip')"
          >跳过当前步</button>
          <input
            v-model="planRedirect"
            class="tw-plan__redirect"
            maxlength="500"
            aria-label="计划调整方向"
            placeholder="需要调整时说明方向"
          >
          <button
            type="button"
            class="tw-chip"
            :disabled="running || !planRedirect.trim()"
            @click="steerEpisodePlan('redirect')"
          >调整计划</button>
          <button
            type="button"
            class="tw-chip"
            :disabled="running"
            @click="steerEpisodePlan('take_over')"
          >自行安排</button>
        </div>
      </section>

      <!-- 思考过程：默认只显示一行「思考中…」，详细节点收纳进可展开列表（用户要求缩小收纳）。 -->
      <div
        v-if="running"
        class="tw-think"
        data-testid="coach-progress"
      >
        <div class="tw-think__status">
          <span
            class="tw-think__spinner"
            aria-hidden="true"
          />
          <span class="tw-think__label">百晓思考中…</span>
          <button
            v-if="coachProgressItems.length"
            type="button"
            class="tw-think__toggle"
            @click="thinkOpen = !thinkOpen"
          >
            {{ thinkOpen ? '收起过程' : '查看过程' }}
          </button>
        </div>
        <ul
          v-if="thinkOpen && coachProgressItems.length"
          class="tw-think__steps"
        >
          <li
            v-for="item in coachProgressItems"
            :key="item.key"
          >
            <span>{{ item.label }}</span>
            <em>{{ item.statusLabel }}</em>
          </li>
        </ul>
      </div>
    </div>

    <!-- 下一步：一个主推荐 + 折叠的其它阶段动作 + 收纳的「学习工具」（按阶段门控，后端 available_actions 驱动）。 -->
    <div
      class="tw-next"
      data-testid="tw-actions"
    >
      <button
        v-if="planIsTerminal"
        type="button"
        class="tw-chip"
        data-testid="plan-recommend"
        :disabled="running"
        @click="startEpisodePlan"
      >
        {{ episodePlan ? '重新生成学习计划' : '生成个性化学习计划' }}
      </button>
      <button
        v-if="compactAvailable"
        type="button"
        class="tw-chip"
        data-testid="compact-chat"
        :disabled="running"
        @click="runEvent('COMPACT')"
      >整理对话上下文</button>
      <button
        v-if="primaryAction"
        type="button"
        class="tw-next__primary"
        data-testid="coach-recommended-action"
        :disabled="running"
        @click="onAction(primaryAction)"
      >
        <span class="tw-next__eyebrow">推荐下一步</span>
        <strong>{{ primaryAction.label }}</strong>
        <small v-if="primaryAction.reason">{{ primaryAction.reason }}</small>
      </button>

      <div
        v-if="secondaryPhaseActions.length"
        class="tw-next__alts"
      >
        <button
          v-for="a in secondaryPhaseActions"
          :key="a.key"
          type="button"
          class="tw-chip"
          :disabled="running"
          @click="onAction(a)"
        >
          {{ a.label }}
        </button>
      </div>

      <div
        v-if="toolActions.length"
        class="tw-tools"
      >
        <button
          type="button"
          class="tw-tools__toggle"
          :class="{ 'is-open': toolsOpen }"
          :aria-expanded="toolsOpen"
          @click="toolsOpen = !toolsOpen"
        >
          🧰 学习工具
          <span class="tw-tools__chev">{{ toolsOpen ? '▾' : '▸' }}</span>
        </button>
        <div
          v-if="toolsOpen"
          class="tw-tools__menu"
        >
          <button
            v-for="a in toolActions"
            :key="a.key"
            type="button"
            class="tw-chip tw-chip--tool"
            :disabled="running"
            @click="onAction(a)"
          >
            {{ a.label }}
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="retryableRequest && !running"
      class="tw-retry"
      role="status"
    >
      <span>{{ sse.error.value?.message ?? '上次导学请求未完成，可以安全重试。' }}</span>
      <button
        type="button"
        data-testid="tutor-workflow-retry"
        @click="retryLastWorkflowRequest"
      >
        重试上次请求
      </button>
    </div>

    <!-- 自由提问（CHAT） -->
    <div
      v-if="chatImages.length"
      class="tw-chat__images"
      data-testid="tw-chat-images"
    >
      <div
        v-for="(image, index) in chatImages"
        :key="image.previewUrl || index"
        class="tw-chat__image"
      >
        <img
          :src="image.previewUrl"
          :alt="`待发送图片 ${index + 1}`"
        >
        <button
          type="button"
          :aria-label="`移除图片 ${index + 1}`"
          :disabled="chatImageReads > 0"
          @click="removeChatImage(index)"
        >
          ×
        </button>
      </div>
    </div>
    <form
      class="tw-chat"
      @submit.prevent="sendChat"
    >
      <input
        ref="chatFileInput"
        type="file"
        accept="image/png,image/jpeg,image/gif,image/webp"
        multiple
        hidden
        data-testid="tw-chat-image-input"
        @change="onChatImagesSelected"
      >
      <button
        type="button"
        class="tw-chat__attach"
        aria-label="添加图片"
        :disabled="running || chatImages.length + chatImageReads >= MAX_CHAT_IMAGES"
        @click="chatFileInput?.click()"
      >
        图片
      </button>
      <input
        v-model="chatInput"
        class="tw-chat__input"
        type="text"
        placeholder="提问，或引用 @card:卡片ID / @courseware:页ID"
        :disabled="running"
      >
      <button
        type="submit"
        class="tw-chat__send"
        :disabled="!canSendChat"
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
.tw-plan {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 10px;
  border: 1px solid rgba(42, 111, 151, 0.35);
  border-radius: 8px;
  background: rgba(230, 242, 251, 0.55);
}
.tw-plan__head,
.tw-plan__controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.tw-plan__head {
  justify-content: space-between;
  color: #1b4a6b;
  font-size: 13px;
}
.tw-plan__head span,
.tw-plan__steps small,
.tw-plan__stop {
  font-size: 11px;
  color: var(--pyt-color-text-muted);
}
.tw-plan__objective,
.tw-plan__stop {
  margin: 0;
  line-height: 1.55;
}
.tw-plan__steps {
  display: grid;
  gap: 5px;
  margin: 0;
  padding-left: 20px;
}
.tw-plan__steps li {
  padding-left: 2px;
  line-height: 1.45;
}
.tw-plan__steps li.is-active b {
  color: #1b4a6b;
}
.tw-plan__steps li.is-done b {
  color: var(--pyt-color-text-hint);
  text-decoration: line-through;
}
.tw-plan__steps small {
  display: block;
}
.tw-plan__response-label {
  font-size: 11px;
  color: var(--pyt-color-text-muted);
}
.tw-plan__input,
.tw-plan__redirect {
  min-width: 0;
  padding: 5px 7px;
  font: inherit;
  font-size: 12px;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-primary, #fffdf6);
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
}
.tw-plan__input {
  resize: vertical;
}
.tw-plan__redirect {
  flex: 1 1 140px;
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
/* 思考过程：默认极简一行 +「查看过程」收纳详情。 */
.tw-think {
  border: 1px solid rgba(42, 111, 151, 0.22);
  border-radius: 8px;
  background: rgba(230, 242, 251, 0.5);
  padding: 7px 10px;
}
.tw-think__status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.tw-think__spinner {
  flex: 0 0 auto;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid rgba(42, 111, 151, 0.3);
  border-top-color: var(--pyt-color-info, #2a6f97);
  animation: tw-spin 0.7s linear infinite;
}
@keyframes tw-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .tw-think__spinner {
    animation: none;
  }
}
.tw-think__label {
  font-weight: 600;
  color: #1b4a6b;
}
.tw-think__toggle {
  margin-left: auto;
  font-family: inherit;
  font-size: 11px;
  padding: 2px 8px;
  cursor: pointer;
  color: #1b4a6b;
  background: transparent;
  border: 1px solid rgba(42, 111, 151, 0.35);
  border-radius: 999px;
}
.tw-think__steps {
  margin: 7px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 4px;
}
.tw-think__steps li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--pyt-color-text-muted);
}
.tw-think__steps em {
  flex: 0 0 auto;
  font-style: normal;
}

/* 下一步：一个主推荐大按钮 + 其它阶段动作（chips）+「学习工具」抽屉。 */
.tw-next {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px dashed rgba(74, 54, 29, 0.3);
}
.tw-next__primary {
  display: grid;
  gap: 2px;
  text-align: left;
  padding: 9px 12px;
  cursor: pointer;
  color: #14405f;
  background: var(--pyt-color-info-bg, #e6f2fb);
  border: 2px solid var(--pyt-color-info, #2a6f97);
  border-radius: 8px;
  box-shadow: 2px 2px 0 rgba(42, 111, 151, 0.4);
}
.tw-next__primary:hover:not(:disabled) {
  background: #d8ebf8;
}
.tw-next__primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}
.tw-next__eyebrow {
  font-size: 10px;
  letter-spacing: 0.5px;
  color: #2a6f97;
}
.tw-next__primary strong {
  font-family: var(--pyt-font-read);
  font-size: 14px;
}
.tw-next__primary small {
  font-size: 11px;
  line-height: 1.5;
  color: var(--pyt-color-text-muted);
}
.tw-next__alts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tw-chip {
  font-family: inherit;
  font-size: 12px;
  padding: 5px 11px;
  cursor: pointer;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: 999px;
}
.tw-chip:hover:not(:disabled) {
  background: var(--pyt-color-warning-bg, #ffe9b8);
}
.tw-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.tw-tools {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.tw-tools__toggle {
  align-self: flex-start;
  font-family: inherit;
  font-size: 12px;
  padding: 4px 10px;
  cursor: pointer;
  color: var(--pyt-color-text-hint);
  background: transparent;
  border: 1px dashed var(--pyt-color-border);
  border-radius: 999px;
}
.tw-tools__toggle.is-open {
  color: var(--pyt-color-text-primary);
  border-style: solid;
}
.tw-tools__chev {
  font-size: 10px;
}
.tw-tools__menu {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 6px;
  border: 1px solid rgba(74, 54, 29, 0.18);
  border-radius: 8px;
  background: rgba(255, 252, 242, 0.6);
}
.tw-chip--tool {
  background: rgba(237, 233, 254, 0.6);
  border-color: rgba(124, 92, 196, 0.4);
  color: #4b3b86;
}
.tw-chip--tool:hover:not(:disabled) {
  background: rgba(237, 233, 254, 0.95);
}
.tw-chat {
  flex: 0 0 auto;
  display: flex;
  gap: 6px;
  padding: 8px 12px 10px;
}
.tw-chat__attach {
  flex: 0 0 auto;
  padding: 6px 8px;
  font: inherit;
  cursor: pointer;
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
  background: var(--pyt-color-bg-soft);
}
.tw-chat__images {
  display: flex;
  gap: 6px;
  padding: 0 12px 8px;
}
.tw-chat__image {
  position: relative;
}
.tw-chat__image img {
  width: 52px;
  height: 52px;
  object-fit: cover;
  border-radius: 4px;
}
.tw-chat__image button {
  position: absolute;
  top: -5px;
  right: -5px;
  width: 18px;
  height: 18px;
  border: 1px solid var(--pyt-color-border);
  border-radius: 50%;
  cursor: pointer;
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

.tw-retry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 6px 10px;
  padding: 8px 10px;
  border: 1px solid rgba(180, 83, 9, 0.25);
  border-radius: 8px;
  background: rgba(255, 247, 237, 0.9);
  color: #9a3412;
  font: 12px/1.45 var(--pyt-font-ui, sans-serif);
}

.tw-retry button {
  flex: 0 0 auto;
  min-height: 32px;
  border: 1px solid rgba(180, 83, 9, 0.4);
  border-radius: 6px;
  padding: 0 10px;
  background: #fff;
  color: #9a3412;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}

.tw-retry button:focus-visible {
  outline: 2px solid #c2410c;
  outline-offset: 2px;
}

@media (max-width: 480px) {
  .tw-retry { align-items: flex-start; flex-direction: column; }
}
</style>
