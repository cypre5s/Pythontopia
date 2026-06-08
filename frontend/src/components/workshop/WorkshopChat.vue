<script setup lang="ts">
/**
 * 百晓工坊对话流（合并 导师问答 + 资源生成）。
 *
 * 一条消息进来：
 *   1. ChatComposer 给出 { text, manualKcCodes, contextHint, forcedIntent }；
 *   2. forcedIntent 为空 → 调 /api/intent/route 自动判定工具 + 锚定 KC；否则直接用；
 *   3. ASK → 复用 /api/tutor/ask SSE，答案内联成气泡（grounding + 证据页 + 图解）；
 *   4. 6 类资源 → 复用 /api/workshop/resources/stream SSE + 事务所动画 + 证据卡，
 *      确认后出一张「资源卡」气泡，并 loadResources 刷新右侧书房抽屉。
 *
 * 不自己渲染 6 类资源全文——资源详情仍由抽屉里的 ResourceDetailPanel（`?id` 驱动）负责，
 * 资源卡上的「在书房查看」emit('open-resource', id) 让父级打开抽屉并定位。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import { useWorkshopActivityStore } from '@stores/workshopActivity';
import { playSfx } from '@/composables/useAudio';
import { useSseStream } from '@/composables/useSseStream';
import ChatComposer, {
  type ComposerSubmitPayload,
} from '@/components/workshop/ChatComposer.vue';
import AgentOfficeScene from '@/components/workshop/AgentOfficeScene.vue';
import type { OfficeEvidence } from '@/components/workshop/agentOffice';
import MarkdownView from '@/components/common/MarkdownView.vue';
import TutorVisualAids, { type TutorVisualAid } from '@/components/tutor/TutorVisualAids.vue';
import CoursewareEvidence from '@/components/tutor/CoursewareEvidence.vue';
import BilibiliVideoCards from '@/components/video/BilibiliVideoCards.vue';
import type { EvidenceCitation } from '@/api/tutor';
import { kcDisplayName } from '@/data/kcNames';
import { listChapters, listKnowledgeComponents } from '@/api/course';
import { getWorkshopResource } from '@/api/workshop';
import {
  buildMentionItems,
  type MentionItem,
} from '@/composables/useCoursewareMention';
import { SLASH_COMMANDS } from '@/composables/useSlashCommand';
import { routeIntent, type CourseKcRef, type IntentKind } from '@/api/intent';
import { type TutorSessionDto } from '@/api/tutor';
import type {
  WorkshopContent,
  WorkshopCreateResponse,
  WorkshopResourceType,
} from '@/api/dto/Workshop';

/** 单道审查结果（与 graph `_build_result` 的 review.* 同形：passed + reasons）。 */
interface TutorReviewItem {
  passed: boolean;
  reasons: string[];
}

/** 三道审查明细：审查官（引用）/ 校对官（防幻觉）/ 安全员（安全）。 */
interface TutorReview {
  grounding: TutorReviewItem;
  hallucination: TutorReviewItem;
  safety: TutorReviewItem;
}

/**
 * 导师答疑 SSE 的 `result` 事件载荷——后端按原样转发 graph 的 snake_case JSON，
 * 故字段**必须**用 snake_case 对齐（写成 camelCase 会导致接地状态 / 证据不渲染）。
 */
interface TutorStreamResult {
  answer_md?: string;
  evidence_page_ids?: number[];
  /** 命中的课件证据明细（页码 + 原文片段）。 */
  evidence?: Array<{ page_id: number; page_no: number; snippet?: string; score?: number }>;
  grounding_result?: { passed: boolean; reasons: string[] };
  based_on_general_knowledge?: boolean;
  safety_result?: { passed: boolean; reasons: string[] };
  visual_aids?: TutorVisualAid[];
  /** 三道审查明细（导师 → 审查官 → 校对官 → 安全员），供答案气泡渲染「三审徽章」。 */
  review?: TutorReview;
  /** 「课件证据」整组一句话总览说明（结合提问/上下文/画像；可空）。 */
  evidence_note?: string;
}

/** 由 graph 的 grounding_result / based_on_general_knowledge 归一成前端 g-chip 用的状态串。 */
function deriveGroundingStatus(r: TutorStreamResult): string {
  if (r.based_on_general_knowledge) return 'NO_EVIDENCE';
  if (r.grounding_result && r.grounding_result.passed === false) return 'FAILED';
  return 'PASSED';
}

/** 把 result.evidence（snake_case）投影成「课件证据」面板用的 EvidenceCitation（camelCase）。 */
function toCitations(r: TutorStreamResult): EvidenceCitation[] {
  return (r.evidence ?? []).map((e) => {
    const citation: EvidenceCitation = {
      pageId: e.page_id,
      pageNo: e.page_no,
      snippet: e.snippet ?? '',
    };
    // exactOptionalPropertyTypes：score 仅在确有数值时赋值，避免写入 undefined。
    if (typeof e.score === 'number') citation.score = e.score;
    return citation;
  });
}

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  kind: 'text' | 'answer' | 'resource' | 'video' | 'error';
  text: string;
  streaming?: boolean;
  intent?: IntentKind;
  toolLabel?: string;
  toolIcon?: string;
  kcCodes?: string[];
  /** kind==='video' / kind==='answer'：B站卡片检索关键词（Python 优先、清洗后）。 */
  videoKeyword?: string;
  /** kind==='answer'：是否自动内嵌「相关 B站讲解」（按学生语句的视频意图判断）。 */
  showVideo?: boolean;
  groundingStatus?: string;
  evidencePageIds?: number[];
  /** kind==='answer'：命中的课件证据（页码 + 原文片段），由「课件证据」面板展示。 */
  evidence?: EvidenceCitation[];
  /** kind==='answer' 且 live：课件证据整组一句话总览说明（历史回放不带）。 */
  evidenceNote?: string;
  /**
   * kind==='answer' 且 live 答疑：三道审查明细，渲染为答案气泡的「三审徽章」。
   * 历史回放不带（loadConversation 不设此字段）→ 旧会话不显示徽章，仅 live 显示。
   */
  review?: TutorReview;
  visualAids?: TutorVisualAid[];
  /** kind==='answer'：用于答案内自动嵌入工具（相关 B站视频检索词 / 生成入口）。 */
  suggestTopic?: string;
  resourceId?: number;
  resourceType?: WorkshopResourceType;
  resourceTitle?: string;
  resourceGrounding?: string;
}

const emit = defineEmits<{
  (event: 'open-resource', resourceId: number): void;
}>();

const router = useRouter();
const learning = useLearningStore();
const toast = useToastStore();
const activity = useWorkshopActivityStore();

const messages = ref<ChatMessage[]>([]);
const mentionItems = ref<MentionItem[]>([]);
const courseKcs = ref<CourseKcRef[]>([]);
const routing = ref(false);
const scrollRef = ref<HTMLElement | null>(null);

const tutorSse = useSseStream<TutorStreamResult>('/api/tutor/ask');
const workshopSse = useSseStream<WorkshopCreateResponse>('/api/workshop/resources/stream');

const streamingAskId = ref<string | null>(null);

// 会话窗口 id：本组件挂载（= 新开窗口）即生成；同一窗口内的多次提问共享同一值，
// 让「聊天记录」按会话分组而非每问一条。「新对话」或回看历史会话时会切换它。
const conversationId = ref<string>(genId());

// 续聊提示：从「聊天记录」回看一段旧会话后置 true，对话流顶部提示「新提问会并入这段会话」。
// 「新对话」或全新窗口为 false。仅作 UI 提示，落库的归并由 conversationId 决定。
const continuing = ref(false);

/** 开新对话窗口：清空当前对话流并换一个会话 id（此后提问归入新会话）。 */
function newChat(): void {
  playSfx('sfx_click');
  messages.value = [];
  streamingAskId.value = null;
  conversationId.value = genId();
  continuing.value = false;
  scrollToBottom();
}

/**
 * 历史回看：把一整段会话（同一 conversationId 的多轮问答，按时间正序）回放进对话流，
 * 并把当前会话 id 切到这段会话——于是学生可「接着这段对话继续问」。
 * 旧数据（conversationId 为 null）无法续聊，续问会新开一个会话以免污染分组。
 */
function loadConversation(sessions: TutorSessionDto[]): void {
  const ordered = [...sessions].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const replay: ChatMessage[] = [];
  for (const s of ordered) {
    replay.push({ id: genId(), role: 'user', kind: 'text', text: s.question });
    replay.push({
      id: genId(),
      role: 'assistant',
      kind: s.answerMd ? 'answer' : 'error',
      intent: 'ASK',
      text: s.answerMd ?? '（这条历史问答没有保存答案）',
      groundingStatus: s.groundingStatus,
      evidencePageIds: s.evidencePageIds,
      streaming: false,
    });
  }
  messages.value = replay;
  const existing = ordered.find((s) => s.conversationId)?.conversationId;
  conversationId.value = existing ?? genId();
  // 仅当这段历史真有 conversationId 才算「可续聊」；旧数据（null）续问会另起会话，不提示并入。
  continuing.value = Boolean(existing);
  scrollToBottom();
}
defineExpose({ loadConversation, newChat });

// 资源生成「智能体事务所」覆盖层状态。
const officeStreamState = ref<'idle' | 'streaming' | 'done' | 'error'>('idle');
const officeTokenPreview = ref('');
const officeEvidence = ref<OfficeEvidence | null>(null);
const pendingResource = ref<{
  resourceId: number;
  type: WorkshopResourceType;
  kcCodes: string[];
} | null>(null);

const busy = computed(
  () =>
    routing.value ||
    tutorSse.status.value === 'streaming' ||
    tutorSse.status.value === 'connecting' ||
    officeStreamState.value === 'streaming',
);

const GROUNDING_LABELS: Record<string, { text: string; cls: string }> = {
  PASSED: { text: '引用充分', cls: 'g--passed' },
  NO_EVIDENCE: { text: '通识知识', cls: 'g--general' },
  FAILED: { text: '未通过', cls: 'g--failed' },
};

function groundingInfo(status: string): { text: string; cls: string } {
  return GROUNDING_LABELS[status] ?? { text: status, cls: '' };
}

/** 一枚「三审徽章」视图模型：通过/未通过 + hover 显示该审查的具体理由。 */
interface ReviewBadge {
  key: string;
  label: string;
  passed: boolean;
  title: string;
}

/**
 * 把 review 三道审查映射成 3 枚徽章（审查官·引用 / 校对官·防幻觉 / 安全员·安全）。
 * title 汇总该审查 reasons，鼠标悬停即可看到「为什么过 / 为什么没过」。
 */
function reviewBadges(review: TutorReview): ReviewBadge[] {
  const make = (
    key: string,
    label: string,
    qualifier: string,
    item: TutorReviewItem,
  ): ReviewBadge => {
    const reasons = (item.reasons ?? []).filter((r) => r && r.trim().length > 0);
    const head = `${label}（${qualifier}）：${item.passed ? '已通过' : '未通过'}`;
    return {
      key,
      label,
      passed: item.passed,
      title: reasons.length > 0 ? `${head}\n${reasons.join('\n')}` : head,
    };
  };
  return [
    make('grounding', '审查官', '引用', review.grounding),
    make('hallucination', '校对官', '防幻觉', review.hallucination),
    make('safety', '安全员', '安全', review.safety),
  ];
}

function commandOf(intent: IntentKind): { label: string; icon: string } {
  const cmd = SLASH_COMMANDS.find((c) => c.intent === intent);
  return { label: cmd?.label ?? intent, icon: cmd?.icon ?? '•' };
}

function genId(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
  } catch {
    /* 忽略 */
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function pushMessage(msg: ChatMessage): void {
  messages.value = [...messages.value, msg];
  scrollToBottom();
}

function updateMessage(id: string, patch: Partial<ChatMessage>): void {
  messages.value = messages.value.map((m) => (m.id === id ? { ...m, ...patch } : m));
}

function scrollToBottom(): void {
  void nextTick(() => {
    scrollRef.value?.scrollTo({ top: scrollRef.value.scrollHeight, behavior: 'smooth' });
  });
}

// 从资源内容里递归收集 page_no（用于证据卡「课本第 X 页」）。
function collectPageNos(content: WorkshopContent | undefined): number[] {
  const set = new Set<number>();
  const walk = (node: unknown): void => {
    if (Array.isArray(node)) {
      node.forEach(walk);
      return;
    }
    if (node && typeof node === 'object') {
      for (const [key, value] of Object.entries(node as Record<string, unknown>)) {
        if (key === 'page_no' && typeof value === 'number') set.add(value);
        else walk(value);
      }
    }
  };
  walk(content);
  return [...set].sort((a, b) => a - b);
}

watch(tutorSse.tokens, (t) => {
  if (streamingAskId.value) updateMessage(streamingAskId.value, { text: t });
  scrollToBottom();
});

watch(workshopSse.tokens, (t) => {
  officeTokenPreview.value = t.slice(0, 200);
});

// 把 SSE 实时状态镜像给侧栏 marvis（只在对应 kind 活跃时同步，避免两条流串台）。
watch(tutorSse.traces, (t) => activity.syncTraces('ask', t));
// 答疑流也发 plan（馆员→导师→审查官→校对官→安全员），镜像给 marvis 点亮工位与交接动画。
watch(tutorSse.plan, (p) => activity.syncPlan('ask', p));
watch(workshopSse.traces, (t) => activity.syncTraces('resource', t));
watch(workshopSse.plan, (p) => activity.syncPlan('resource', p));

async function onSubmit(payload: ComposerSubmitPayload): Promise<void> {
  playSfx('sfx_click');
  const userMsg: ChatMessage = {
    id: genId(),
    role: 'user',
    kind: 'text',
    text: payload.text,
  };
  if (payload.forcedIntent) {
    const meta = commandOf(payload.forcedIntent);
    userMsg.toolLabel = meta.label;
    userMsg.toolIcon = meta.icon;
  }
  if (payload.manualKcCodes.length > 0) userMsg.kcCodes = payload.manualKcCodes;
  pushMessage(userMsg);

  let intent: IntentKind | null = payload.forcedIntent;
  let kcCodes: string[] = [...payload.manualKcCodes];
  let focusHint = '';

  if (!intent) {
    routing.value = true;
    try {
      const res = await routeIntent({
        message: payload.text,
        manualKcCodes: payload.manualKcCodes,
        courseKcs: courseKcs.value,
      });
      intent = res.intent;
      kcCodes = res.kcCodes;
      focusHint = res.focusHint;
    } catch {
      // 分类失败兜底：按答疑处理（不需要 KC，永远可用）。
      intent = 'ASK';
      kcCodes = [...payload.manualKcCodes];
    } finally {
      routing.value = false;
    }
  }

  if (intent === 'ASK') {
    await runAsk(payload.text, kcCodes, payload.contextHint);
  } else if (intent === 'VIDEO') {
    runVideo(kcCodes, focusHint || payload.text);
  } else if (intent === 'REVIEW') {
    runReview(kcCodes);
  } else if (kcCodes.length === 0) {
    // 不强制 @KC：资源类意图但没锚定到知识点时，不再报错，先把学生的问题答了（答案优先）。
    // 答疑走课件 RAG，检索不到再 LLM。
    await runAsk(payload.text, kcCodes, payload.contextHint);
  } else {
    await runResource(intent, kcCodes, focusHint || payload.text);
  }
}

// 面向「非科班 Python 初学者」：B站检索词一律以「Python」打头、并加「教程」偏向入门讲解；
// 未提其它语言时默认 Python（后端再按语言/领域过滤掉机器人/C 语言等跑题结果）。
function buildVideoKeyword(topic: string, kcCodes: string[]): string {
  const kcPart = kcCodes.map((c) => kcDisplayName(c)).filter(Boolean).join(' ');
  const cleanTopic = (topic || '')
    .replace(/[?？!！。.,，、:：]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return `Python ${kcPart} ${cleanTopic} 教程`.replace(/\s+/g, ' ').trim().slice(0, 50);
}

// 学生语句是否表达「想看视频」——B站讲解仅在该意图或 / 命令下触发，不再每答必带。
const VIDEO_INTENT_TOKENS = [
  '视频', 'b站', 'bilibili', '讲解视频', '看视频', '教学视频', 'up主', '找视频', '推荐视频', '影片',
];
function wantsVideo(text: string): boolean {
  const t = (text || '').toLowerCase();
  return VIDEO_INTENT_TOKENS.some((k) => t.includes(k));
}

/**
 * VIDEO 意图：不走生成/SSE，直接出一条「视频卡片」气泡，由 BilibiliVideoCards 按关键词
 * 现取现渲染（外部资源）。关键词 = Python + KC 显示名 + 学习重点（去重去空、限长）。
 */
function runVideo(kcCodes: string[], focus: string): void {
  const meta = commandOf('VIDEO');
  const keyword = buildVideoKeyword(focus, kcCodes);
  playSfx('sfx_success');
  pushMessage({
    id: genId(),
    role: 'assistant',
    kind: 'video',
    intent: 'VIDEO',
    text: '',
    videoKeyword: keyword,
    toolIcon: meta.icon,
    toolLabel: meta.label,
  });
}

/**
 * REVIEW 意图：记忆矿脉回声复习。不走生成/SSE，引导学生去错题矿洞按 FSRS 到期 KC 复盘；
 * kcCodes 为后端 ReviewSignal 算出的待复习知识点（可空），仅用于提示，矿洞页自取到期排序。
 */
function runReview(kcCodes: string[]): void {
  playSfx('sfx_success');
  const names = kcCodes.map((c) => kcDisplayName(c)).filter(Boolean);
  const tip = names.length
    ? `记忆矿脉回声：「${names.join('、')}」快遗忘了，带你去错题矿洞复盘。`
    : '记忆矿脉回声：带你去错题矿洞复盘最该巩固的知识点。';
  toast.show(tip, 'info');
  void router.push('/bug-mine');
}

/** 答疑答案内「看相关视频」入口：按答案主题 / KC 出一条 B站视频气泡。 */
function onAnswerVideo(m: ChatMessage): void {
  runVideo(m.kcCodes ?? [], m.suggestTopic ?? '');
}

/** 答疑答案内「生成讲义 / 练习」入口：按答案 KC 直接生成。 */
function onAnswerGenerate(m: ChatMessage, type: WorkshopResourceType): void {
  void runResource(type, m.kcCodes ?? [], m.suggestTopic ?? '');
}

async function runAsk(question: string, kcCodes: string[], context: string): Promise<void> {
  const id = genId();
  pushMessage({
    id,
    role: 'assistant',
    kind: 'answer',
    intent: 'ASK',
    text: '',
    streaming: true,
  });
  streamingAskId.value = id;
  activity.start('ask', `答疑：${question.trim().slice(0, 24)}`);

  await tutorSse.start({ question, kcCodes, context, conversationId: conversationId.value });

  streamingAskId.value = null;
  activity.finish(tutorSse.status.value === 'done');
  // 完工后「智能体工作室」保持终态（全部工位打勾 / 协同完成）常驻，不自动收回待命名单；
  // 直到学生下一次提问（activity.start 重置）才换成新一轮流水线。
  if (tutorSse.status.value === 'done' && tutorSse.result.value) {
    playSfx('sfx_success');
    const r = tutorSse.result.value;
    updateMessage(id, {
      text: r.answer_md || tutorSse.tokens.value,
      groundingStatus: deriveGroundingStatus(r),
      evidencePageIds: r.evidence_page_ids ?? [],
      evidence: toCitations(r),
      // 三审徽章只在 live 答疑写入；review 缺省（旧后端）时不写，气泡自然不显徽章。
      ...(r.review ? { review: r.review } : {}),
      // 课件证据总览说明（live 才有；为空则不写）。
      ...(r.evidence_note ? { evidenceNote: r.evidence_note } : {}),
      visualAids: r.visual_aids ?? [],
      streaming: false,
      // 工具产出按需触发：「看相关视频/生成讲义/出练习」按钮常驻为手动入口；
      // 「相关 B站讲解」仅当学生语句表达视频意图（wantsVideo）时才自动内嵌。
      // 图解由后端按「要图」意图返回（visual_aids 为空则不显示），无需前端再判。
      suggestTopic: question.trim(),
      kcCodes,
      showVideo: wantsVideo(question),
      videoKeyword: buildVideoKeyword(question, kcCodes),
    });
  } else {
    playSfx('sfx_error');
    updateMessage(id, {
      kind: 'error',
      text: tutorSse.error.value?.message ?? 'AI 助教暂时没答上来，再试一次',
      streaming: false,
    });
  }
  scrollToBottom();
}

async function runResource(
  type: WorkshopResourceType,
  kcCodes: string[],
  focusHint: string,
): Promise<void> {
  // 不强制 @KC：调用方（onSubmit）已保证无 KC 时改走答疑；这里仅作防御性兜底。
  if (kcCodes.length === 0) {
    await runAsk(focusHint, kcCodes, '');
    return;
  }

  officeStreamState.value = 'streaming';
  officeTokenPreview.value = '';
  officeEvidence.value = null;
  activity.start('resource', `生成${commandOf(type).label}`);

  const trimmed = focusHint.trim();
  const reqPayload =
    trimmed.length > 0 ? { kcCodes, type, focusHint: trimmed } : { kcCodes, type };
  learning.streamCreateResource({ kcCodes, type });

  await workshopSse.start(reqPayload);

  if (workshopSse.status.value === 'done' && workshopSse.result.value) {
    officeStreamState.value = 'done';
    playSfx('sfx_success');
    const result = workshopSse.result.value;
    pendingResource.value = { resourceId: result.resourceId, type, kcCodes };
    try {
      const detail = await getWorkshopResource(result.resourceId);
      officeEvidence.value = {
        resourceId: result.resourceId,
        runId: result.runId,
        status: result.status,
        groundingStatus: detail.groundingStatus,
        safetyStatus: detail.safetyStatus,
        pageNos: collectPageNos(detail.content),
        basedOnGeneral: detail.groundingStatus === 'NO_EVIDENCE',
      };
    } catch {
      officeEvidence.value = {
        resourceId: result.resourceId,
        runId: result.runId,
        status: result.status,
        groundingStatus: result.status === 'FAILED' ? 'FAILED' : 'PASSED',
        safetyStatus: 'PASSED',
        pageNos: [],
        basedOnGeneral: result.status === 'PARTIAL',
      };
    }
    activity.finish(true);
  } else {
    officeStreamState.value = 'error';
    playSfx('sfx_error');
    pushMessage({
      id: genId(),
      role: 'assistant',
      kind: 'error',
      text: workshopSse.error.value?.message ?? '资源生成失败，请稍后重试。',
    });
    officeStreamState.value = 'idle';
    activity.idle();
  }
}

async function onOfficeConfirm(): Promise<void> {
  const pending = pendingResource.value;
  const evidence = officeEvidence.value;
  officeEvidence.value = null;
  officeStreamState.value = 'idle';
  activity.idle();
  if (!pending) return;
  playSfx('sfx_coin');
  await learning.loadResources();

  const detail = learning.resources.list.find((r) => r.id === pending.resourceId);
  const meta = commandOf(pending.type);
  pushMessage({
    id: genId(),
    role: 'assistant',
    kind: 'resource',
    intent: pending.type,
    text: '',
    resourceId: pending.resourceId,
    resourceType: pending.type,
    resourceTitle: detail?.title ?? meta.label,
    resourceGrounding: evidence?.groundingStatus ?? detail?.groundingStatus ?? 'PASSED',
    toolIcon: meta.icon,
    toolLabel: meta.label,
  });

  if (typeof window !== 'undefined') {
    void import('@stores/mainQuest').then((mod) => {
      try {
        mod.useMainQuestStore().onResourceCreated(pending.type, pending.kcCodes);
      } catch {
        /* mainQuest store 不可用，忽略 */
      }
    });
  }
  toast.show('已收入「我的书房」', 'success');
  pendingResource.value = null;
}

function onOfficeCancel(): void {
  officeEvidence.value = null;
  officeStreamState.value = 'idle';
  pendingResource.value = null;
  activity.idle();
}

function openResource(resourceId: number): void {
  playSfx('sfx_click');
  emit('open-resource', resourceId);
}

function goToPage(pageId: number): void {
  playSfx('sfx_click');
  void router.push({ path: '/library', query: { pageId: String(pageId) } });
}

onMounted(async () => {
  try {
    const [chapters, kcs] = await Promise.all([
      listChapters(43),
      listKnowledgeComponents(43),
    ]);
    mentionItems.value = buildMentionItems(chapters, kcs);
    courseKcs.value = kcs.map((kc) => ({ code: kc.kcCode, name: kc.kcName }));
  } catch {
    mentionItems.value = [];
    courseKcs.value = [];
  }
});
</script>

<template>
  <div
    class="chat"
    data-testid="workshop-chat"
  >
    <div class="chat__topbar">
      <span class="chat__topbar-title">✦ AI 答疑</span>
      <button
        type="button"
        class="chat__newchat"
        data-testid="workshop-new-chat"
        title="开一段新对话（之前的会进「聊天记录」）"
        @click="newChat"
      >
        ＋ 新对话
      </button>
    </div>
    <div
      ref="scrollRef"
      class="chat__scroll"
    >
      <!-- 续聊提示：回看历史会话后，明确告诉学生「接着问会并入这段会话」。 -->
      <div
        v-if="continuing && messages.length > 0"
        class="chat__continue"
        data-testid="workshop-continue-hint"
      >
        <span
          class="chat__continue-icon"
          aria-hidden="true"
        >↩</span>
        <span class="chat__continue-text">正在继续这段会话，新的提问会并入同一条「聊天记录」</span>
        <button
          type="button"
          class="chat__continue-new"
          title="改为开一段全新对话"
          @click="newChat"
        >
          开新对话
        </button>
      </div>
      <div
        v-if="messages.length === 0"
        class="chat__welcome"
      >
        <p class="chat__welcome-title">
          想学什么直接说
        </p>
        <p class="chat__welcome-body">
          比如「循环怎么用」，AI 助教来帮你
        </p>
        <details
          class="chat__more"
          data-testid="workshop-welcome-more"
        >
          <summary class="chat__more-summary">
            更多用法
          </summary>
          <div class="chat__more-body">
            <p>输入 <b>@</b> 引用知识点</p>
            <p>输入 <b>/</b> 选讲解方式</p>
            <p class="chat__more-kinds">
              讲义、思维导图、练习、代码、阅读、动画、B站视频
            </p>
          </div>
        </details>
      </div>

      <template
        v-for="m in messages"
        :key="m.id"
      >
        <!-- 用户气泡 -->
        <div
          v-if="m.role === 'user'"
          class="msg msg--user"
        >
          <div
            v-if="m.toolLabel || (m.kcCodes && m.kcCodes.length)"
            class="msg__tags"
          >
            <span
              v-if="m.toolLabel"
              class="msg__tool"
            >{{ m.toolIcon }} {{ m.toolLabel }}</span>
            <span
              v-if="m.kcCodes && m.kcCodes.length"
              class="msg__kc"
            >@{{ m.kcCodes.length }} 知识点</span>
          </div>
          <p class="msg__text">
            {{ m.text }}
          </p>
        </div>

        <!-- 助手：答疑 -->
        <div
          v-else-if="m.kind === 'answer'"
          class="msg msg--assistant"
        >
          <div
            v-if="m.groundingStatus || (m.review && !m.streaming)"
            class="msg__grounding"
          >
            <span
              v-if="m.groundingStatus"
              class="g-chip"
              :class="groundingInfo(m.groundingStatus).cls"
            >{{ groundingInfo(m.groundingStatus).text }}</span>
            <!-- 三审徽章：仅 live 答疑（带 review）显示；历史回放无 review → 不显示。 -->
            <template v-if="m.review && !m.streaming">
              <span
                v-for="b in reviewBadges(m.review)"
                :key="b.key"
                class="r-badge"
                :class="b.passed ? 'r-badge--ok' : 'r-badge--no'"
                :title="b.title"
              >{{ b.passed ? '✓' : '✗' }} {{ b.label }}</span>
            </template>
          </div>
          <MarkdownView :source="m.text" />
          <span
            v-if="m.streaming"
            class="cursor"
          >▌</span>
          <!-- 课件证据：让学生看见系统命中了哪段课件原文（点页码回链图书馆核对）。 -->
          <CoursewareEvidence
            v-if="!m.streaming"
            :citations="m.evidence ?? []"
            :grounding-status="m.groundingStatus ?? ''"
            :evidence-note="m.evidenceNote ?? ''"
            @open-page="goToPage"
          />
          <TutorVisualAids
            v-if="!m.streaming && m.visualAids && m.visualAids.length"
            :aids="m.visualAids"
          />
          <!-- 工具产出按需触发：动作按钮常驻（手动入口）；B站讲解仅在视频意图下自动内嵌。 -->
          <div
            v-if="!m.streaming && m.suggestTopic"
            class="msg__tools"
          >
            <div class="msg__tool-actions">
              <span class="msg__tool-hint">接下来</span>
              <button
                type="button"
                class="tool-chip"
                @click="onAnswerVideo(m)"
              >▶ 看相关视频</button>
              <template v-if="m.kcCodes && m.kcCodes.length">
                <button
                  type="button"
                  class="tool-chip"
                  @click="onAnswerGenerate(m, 'HANDOUT')"
                >✎ 生成讲义</button>
                <button
                  type="button"
                  class="tool-chip"
                  @click="onAnswerGenerate(m, 'PRACTICE')"
                >✪ 出练习</button>
              </template>
            </div>
            <BilibiliVideoCards
              v-if="m.showVideo"
              class="msg__tool-videos"
              :keyword="m.videoKeyword ?? ''"
              :limit="3"
              heading="相关 B站讲解"
            />
          </div>
        </div>

        <!-- 助手：资源卡 -->
        <button
          v-else-if="m.kind === 'resource'"
          type="button"
          class="msg msg--assistant resource-card"
          data-testid="resource-card"
          @click="openResource(m.resourceId as number)"
        >
          <span class="resource-card__icon">{{ m.toolIcon }}</span>
          <span class="resource-card__body">
            <span class="resource-card__title">{{ m.resourceTitle }}</span>
            <span class="resource-card__meta">
              {{ m.toolLabel }} ·
              {{ groundingInfo(m.resourceGrounding ?? '').text }} · 已收入书房
            </span>
          </span>
          <span class="resource-card__cta">在书房查看 →</span>
        </button>

        <!-- 助手：B站视频卡片（外部资源，按意图检索，不走生成） -->
        <div
          v-else-if="m.kind === 'video'"
          class="msg msg--assistant msg--video"
        >
          <div
            v-if="m.toolLabel"
            class="msg__tags"
          >
            <span class="msg__tool">{{ m.toolIcon }} {{ m.toolLabel }}</span>
          </div>
          <BilibiliVideoCards
            :keyword="m.videoKeyword ?? ''"
            heading="B站精选讲解"
          />
        </div>

        <!-- 助手：错误 / 澄清 -->
        <div
          v-else-if="m.kind === 'error'"
          class="msg msg--assistant msg--error"
        >
          <p class="msg__text">
            {{ m.text }}
          </p>
        </div>
      </template>

      <div
        v-if="routing"
        class="routing"
        data-testid="routing-indicator"
      >
        正在判断该用哪个工具…
      </div>

      <!-- 资源生成：智能体事务所动画 + 证据卡 -->
      <AgentOfficeScene
        v-if="officeStreamState !== 'idle'"
        :traces="workshopSse.traces.value"
        :status="officeStreamState"
        :token-preview="officeTokenPreview"
        :error-message="workshopSse.error.value?.message ?? null"
        :evidence="officeEvidence"
        :plan="workshopSse.plan.value"
        @confirm="onOfficeConfirm"
        @cancel="onOfficeCancel"
      />
    </div>

    <div class="chat__composer">
      <ChatComposer
        :mention-items="mentionItems"
        :busy="busy"
        @submit="onSubmit"
      />
    </div>
  </div>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  font-family: var(--pyt-font-pixel);
  color: #3a2a14;
}
.chat__topbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 12px;
  border-bottom: 2px dashed rgba(110, 79, 29, 0.4);
  background: rgba(255, 247, 230, 0.55);
}
.chat__topbar-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-color-text-hint);
  letter-spacing: 0.5px;
}
.chat__newchat {
  font-family: inherit;
  font-size: 12px;
  padding: 3px 12px;
  background: #fff8df;
  border: 2px solid #8c6a23;
  border-radius: 999px;
  color: #2a1a08;
  cursor: pointer;
  box-shadow: 2px 2px 0 rgba(140, 106, 35, 0.5);
}
.chat__newchat:hover {
  background: #ffe098;
}
.chat__newchat:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}
.chat__scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  scrollbar-width: thin;
}
.chat__composer {
  flex: 0 0 auto;
  padding: 10px 14px 12px;
  border-top: 2px dashed rgba(110, 79, 29, 0.4);
  background: rgba(255, 247, 230, 0.55);
}

/* 续聊提示条 */
.chat__continue {
  flex: 0 0 auto;
  align-self: stretch;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  font-size: 11px;
  color: #1b4a6b;
  background: var(--pyt-color-info-bg);
  border: 1px dashed var(--pyt-color-info);
  border-radius: var(--pyt-radius-md);
}
.chat__continue-icon {
  font-weight: 700;
}
.chat__continue-text {
  flex: 1 1 auto;
  line-height: 1.4;
}
.chat__continue-new {
  flex: 0 0 auto;
  font-family: inherit;
  font-size: 11px;
  padding: 2px 8px;
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-info);
  border-radius: 999px;
  color: #1b4a6b;
  cursor: pointer;
}
.chat__continue-new:hover {
  background: #fff;
}

/* 欢迎区 */
.chat__welcome {
  margin: auto;
  max-width: 460px;
  text-align: center;
  padding: 24px 16px;
  color: var(--pyt-color-text-muted);
}
.chat__welcome-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.chat__welcome-body {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.6;
}
.chat__more {
  max-width: 260px;
  margin: 0 auto;
  font-size: 12px;
  text-align: left;
}
.chat__more-summary {
  text-align: center;
  font-weight: 700;
  color: var(--pyt-color-text-hint);
  cursor: pointer;
  list-style: none;
}
.chat__more-summary::-webkit-details-marker {
  display: none;
}
.chat__more-summary:hover {
  color: var(--pyt-color-text-primary);
}
.chat__more-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
  line-height: 1.6;
}
.chat__more-body p {
  margin: 0;
}
.chat__more-kinds {
  color: var(--pyt-color-text-muted);
}

/* 消息气泡 */
.msg {
  padding: 8px 12px;
  border-radius: var(--pyt-radius-lg);
  border: 2px solid var(--pyt-color-border);
  max-width: 90%;
}
.msg--user {
  background: var(--pyt-color-bg-elevated);
  align-self: flex-end;
  max-width: 80%;
}
.msg--assistant {
  background: #f5ecd4;
  align-self: flex-start;
}
.msg--error {
  background: #f8d7d0;
  border-color: var(--pyt-color-danger);
  color: #b3402a;
}
/* 视频卡片气泡与答疑气泡同宽（同为 90% + 左对齐），避免比上方回答更宽显得突兀。 */
.msg--video {
  align-self: flex-start;
  max-width: 90%;
  width: 100%;
  background: #f5ecd4;
}
/* 答案内嵌的工具入口 + 相关 B站讲解。 */
.msg__tools {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px dashed rgba(110, 79, 29, 0.35);
  padding-top: 8px;
}
.msg__tool-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.msg__tool-hint {
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-muted);
  margin-right: 2px;
}
.msg__tool-videos {
  margin-top: 2px;
}
.tool-chip {
  font-family: inherit;
  font-size: 11px;
  padding: 3px 10px;
  background: #fff8df;
  border: 1px solid #8c6a23;
  border-radius: 999px;
  color: #2a1a08;
  cursor: pointer;
}
.tool-chip:hover {
  background: #ffe098;
}
.msg__tags {
  display: flex;
  gap: 6px;
  margin-bottom: 4px;
}
.msg__tool {
  font-size: 10px;
  font-weight: 700;
  color: #2a1a08;
  background: #f7d774;
  border: 1px solid #8c6a23;
  border-radius: 999px;
  padding: 1px 8px;
}
.msg__kc {
  font-size: 10px;
  font-weight: 700;
  color: #2d5b1b;
  background: #d6ead1;
  border: 1px solid #4d6e1d;
  border-radius: 999px;
  padding: 1px 8px;
}
.msg__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 答疑接地状态 */
.msg__grounding {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.g-chip {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
}
.g--passed {
  background: #d6ead1;
  color: #2d5b1b;
  border: 1px solid #4d6e1d;
}
.g--general {
  background: #fff3cd;
  color: var(--pyt-color-text-muted);
  border: 1px solid #d4af37;
}
.g--failed {
  background: #f8d7d0;
  color: #b3402a;
  border: 1px solid var(--pyt-color-danger);
}
/* 三审徽章：审查官 / 校对官 / 安全员，hover(title) 看具体理由。 */
.r-badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  border: 1px solid var(--pyt-color-border);
  white-space: nowrap;
  cursor: help;
}
.r-badge--ok {
  background: #d6ead1;
  color: #2d5b1b;
  border-color: #4d6e1d;
}
.r-badge--no {
  background: #f8d7d0;
  color: #b3402a;
  border-color: var(--pyt-color-danger);
}
.cursor {
  animation: blink 0.8s steps(2) infinite;
  color: #d4af37;
  font-weight: 700;
}
@keyframes blink {
  0% { opacity: 1; }
  50% { opacity: 0; }
}

/* 资源卡 */
.resource-card {
  display: grid;
  grid-template-columns: 32px 1fr auto;
  gap: 10px;
  align-items: center;
  cursor: pointer;
  text-align: left;
  font: inherit;
  background: #fff8df;
  transition: background 120ms, transform 80ms;
}
.resource-card:hover {
  background: #fff2c9;
}
.resource-card:active {
  transform: translateY(1px);
}
.resource-card__icon {
  font-size: 20px;
  text-align: center;
  color: #8c6a23;
}
.resource-card__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.resource-card__title {
  font-weight: 700;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.resource-card__meta {
  font-size: 11px;
  color: var(--pyt-color-text-muted);
}
.resource-card__cta {
  font-size: 12px;
  font-weight: 700;
  color: #1b4a6b;
  white-space: nowrap;
}

/* 意图分流提示 */
.routing {
  align-self: flex-start;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
  padding: 6px 12px;
  background: rgba(255, 247, 230, 0.7);
  border: 2px dashed rgba(110, 79, 29, 0.35);
  border-radius: var(--pyt-radius-lg);
}
</style>
