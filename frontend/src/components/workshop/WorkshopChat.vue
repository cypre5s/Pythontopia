<script setup lang="ts">
/**
 * 百晓工坊对话流（合并 导师问答 + 资源生成 + 学习管家），17 工具分发中枢。
 *
 * 一条消息进来：
 *   1. ChatComposer 给出 { text, manualKcCodes, contextHint, forcedIntent }；
 *   2. forcedIntent 为空 → 调 /api/intent/route（带最近 6 轮摘要 / 上轮意图 / 本会话纠错 / scope）
 *      自动判定工具 + 锚定 KC；否则直接用；
 *   3. 按 17 工具分发表执行（蓝本 §0）：
 *      - tutor_sse：ASK / ASKME / EXPLAIN_CODE；FIX_BUG 复用 BugMineService → DebugAgent 真链；
 *      - workshop_sse：6 类资源 → /api/workshop/resources/stream + AgentStudioStream 思考链；
 *      - orchestrator_sse：PLAN → /api/orchestrator/turn/stream（同步右侧处理进度）；
 *      - frontend 零 LLM：VIDEO / REVIEW / PROGRESS / NAVIGATE / MY_STUDY。
 *   4. 置信分档：自由文本 <0.55 出澄清气泡；0.55~0.7 正常执行 + 答后纠错 chips。
 *
 * 资源详情由父级 WorkshopPage 的 store openResourceId 打开弹窗，不再把资源 id 写进 URL。
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useLearningStore } from "@stores/learning";
import { useMainlineStore } from "@stores/mainline";
import { useToastStore } from "@stores/toast";
import { useWorkshopActivityStore } from "@stores/workshopActivity";
import { playSfx } from "@/composables/useAudio";
import { useSseStream, type SseTraceEvent } from "@/composables/useSseStream";
import ChatComposer, {
  type ComposerSubmitPayload,
} from "@/components/workshop/ChatComposer.vue";
import HandoutView from "@/components/workshop/HandoutView.vue";
import MindMapView from "@/components/workshop/MindMapView.vue";
import PracticeList from "@/components/workshop/PracticeList.vue";
import CodeLabView from "@/components/workshop/CodeLabView.vue";
import ReadingList from "@/components/workshop/ReadingList.vue";
import StoryboardView from "@/components/workshop/StoryboardView.vue";
import WorkshopAvatar from "@/components/workshop/WorkshopAvatar.vue";
import WelcomePanel from "@/components/workshop/WelcomePanel.vue";
import ClarifyBubble from "@/components/workshop/ClarifyBubble.vue";
import QuizModeBar from "@/components/workshop/QuizModeBar.vue";
import ProgressCard from "@/components/workshop/ProgressCard.vue";
import PlanCard from "@/components/workshop/PlanCard.vue";
import ToolTraceLine from "@/components/workshop/ToolTraceLine.vue";
import ToolCallTimeline from "@/components/workshop/ToolCallTimeline.vue";
import AnswerFeedback from "@/components/workshop/AnswerFeedback.vue";
import MarkdownView from "@/components/common/MarkdownView.vue";
import { annotateCitations } from "@/utils/citations";
import TutorVisualAids, {
  type TutorVisualAid,
} from "@/components/tutor/TutorVisualAids.vue";
import CoursewareEvidence from "@/components/tutor/CoursewareEvidence.vue";
import BilibiliVideoCards from "@/components/video/BilibiliVideoCards.vue";
import BrowseTimeline from "@/components/workshop/BrowseTimeline.vue";
import {
  styleEngaged,
  type CognitiveStyle,
  type EvidenceCitation,
  type TutorAskRequest,
} from "@/api/tutor";
import {
  recordWorkshopChatHistoryTurn,
  type WorkshopChatHistoryRecordRequest,
  type WorkshopChatHistoryTurnDto,
} from "@/api/workshopHistory";
import { kcDisplayName } from "@/data/kcNames";
import { listChapters, listKnowledgeComponents } from "@/api/course";
import { getWorkshopResource } from "@/api/workshop";
import { getBugMineErrorBook, openBugMineSession } from "@/api/bugMine";
import { postLearningEvent } from "@/api/learningEvent";
import {
  attachmentIcon,
  buildUploadInjection,
  mergeUploadContext,
  type UploadAttachmentRef,
} from "@/api/upload";
import {
  buildMentionItems,
  type MentionItem,
} from "@/composables/useCoursewareMention";
import {
  createBrowseRun,
  getBrowseRun,
  type BrowseRunDetailDto,
} from "@/api/browse";
import { SLASH_COMMANDS } from "@/composables/useSlashCommand";
import {
  isResourceIntent,
  routeIntent,
  type CourseKcRef,
  type IntentCorrectionRef,
  type IntentKind,
  type IntentRouteRequest,
  type IntentTurnRef,
  type WorkshopInteractionContextRef,
  type WorkshopRouteDecision,
} from "@/api/intent";
import type {
  OrchestratorPlanStep,
  OrchestratorResult,
  OrchestratorTurnRequest,
} from "@/api/orchestrator";
import type {
  WorkshopContent,
  WorkshopCreateResponse,
  WorkshopResourceType,
  WorkshopResourceDto,
  WorkshopResourceListItemDto,
  HandoutContent,
  MindMapContent,
  PracticeContent,
  CodeLabContent,
  ReadingContent,
  StoryboardContent,
} from "@/api/dto/Workshop";

// 答案气泡的纯视图投影（SSE result → 渲染模型）抽在 chatAnswerView.ts，可独立单测。
import {
  deriveGroundingStatus,
  groundingInfo,
  normalizeAnswerText,
  pageIdsToCitations,
  reviewBadges,
  toCitations,
  type TutorReview,
  type TutorStreamResult,
} from "@/components/workshop/chatAnswerView";

/** 一次工具执行所需的全部上下文：澄清 pick / 纠错改派 / 「接着」chip 据此重放。 */
interface DispatchContext {
  text: string;
  kcCodes: string[];
  focusHint: string;
  contextHint: string;
  /** NAVIGATE 专用地标（路由响应的 target）。 */
  target: string;
  /** 仅服务端验证后的失败提交引用可触发真实 DebugAgent/BugMine 链。 */
  latestSubmissionId?: number;
  /** 路由服务返回的完整权威决策；复合拓扑交由现有 Orchestrator 执行。 */
  routeDecision?: WorkshopRouteDecision;
  routeExecutionToken?: string;
  /** 已从当前真实交互取得的上下文引用，不在前端补造。 */
  interaction?: WorkshopInteractionContextRef;
}

/** tutor_sse 工具族的差异化选项（同一 /api/tutor/ask 管线）。 */
interface AskOptions {
  /** 气泡徽标 / lastIntent 归档用：ASKME / EXPLAIN_CODE / FIX_BUG，缺省 ASK。 */
  intent?: IntentKind;
  /** 蓝本 §0 的 context 前缀 directive（考官 / 讲代码 / 修报错），graph 不感知新端点。 */
  directive?: string;
  /** 即时换讲法：带 style 字段重问同一问题。 */
  style?: CognitiveStyle;
  images?: string[];
}

/** 错误气泡「重试」chip 的参数缓存（重放上次 runAsk / runResource）。 */
type RetryInfo =
  | {
      mode: "ask";
      question: string;
      kcCodes: string[];
      context: string;
      opts: AskOptions;
    }
  | {
      mode: "resource";
      type: WorkshopResourceType;
      kcCodes: string[];
      focusHint: string;
    }
  | { mode: "browse"; ctx: DispatchContext };

interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  kind:
    | "text"
    | "answer"
    | "resource"
    | "video"
    | "browse"
    | "error"
    | "clarify"
    | "plan"
    | "progress"
    | "mystudy"
    | "navigate"
    | "secondary";
  text: string;
  streaming?: boolean;
  intent?: IntentKind;
  toolLabel?: string;
  toolIcon?: string;
  kcCodes?: string[];
  /** role==='user'：本轮随发的上传附件（仿 Claude Code），在用户气泡上方渲染。 */
  attachments?: UploadAttachmentRef[];
  /** role==='user'：本轮已发送给导师的图片数量；图片本身不写入聊天历史。 */
  imageCount?: number;
  /** kind==='video' / kind==='answer'：B站卡片检索关键词（Python 优先、清洗后）。 */
  videoKeyword?: string;
  /** kind==='browse'：联网查证会话详情（外部内容，非课件）。 */
  browseRun?: BrowseRunDetailDto | null;
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
  /** kind==='answer' 且 live：本次答疑 trace 快照，渲染为折叠「⚙ 工具调用」行。 */
  traces?: SseTraceEvent[];
  /** kind==='answer' 且 live：画像个性化说明 → 气泡顶「✨ 为你定制」小标签的 title。 */
  personalizationNote?: string;
  /** kind==='plan'：仅展示服务端提供的学生可读路由解释，不展示内部信号字段。 */
  personalizationSummary?: string;
  /** kind==='plan' 且 C3：三条受限候选比较与选中路线的学生可读摘要。 */
  searchSummary?: string;
  /** kind==='answer'：考考我出题（多轮填空）→ 气泡内渲染可填空输入 + 提交作答按钮。 */
  quiz?: {
    round: number;
    stem: string;
    blanks: Array<{ index: number; hint: string }>;
  };
  /** kind==='answer'：用于答案内自动嵌入工具（相关 B站视频检索词 / 生成入口）。 */
  suggestTopic?: string;
  resourceId?: number;
  resourceType?: WorkshopResourceType;
  resourceTitle?: string;
  resourceGrounding?: string;
  /** kind==='resource' 就地内联浏览：安全审查状态（PASSED/FAILED/NO_EVIDENCE）。 */
  resourceSafety?: string;
  /** kind==='resource' 就地内联浏览：资源摘要（可空）。 */
  resourceSummary?: string;
  /** kind==='resource' 就地内联浏览：关联知识点（可空）。 */
  resourceKcCodes?: string[];
  /**
   * kind==='resource' 就地内联浏览：完整 content。由 getWorkshopResource 取回后**直传**子视图渲染，
   * 绝不复用 learning.resources.current 单槽——同屏多份资源若共用单槽会互相覆盖。
   */
  resourceContent?: WorkshopContent;
  /** kind==='resource'：内联是否展开（生成完那条默认展开全文；历史卡可点开）。 */
  resourceExpanded?: boolean;
  /** kind==='resource'：历史资源卡懒取 content 时的载入态。 */
  resourceLoading?: boolean;
  /** kind==='clarify'：低置信路由的候选工具（ClarifyBubble options）。 */
  clarifyOptions?: Array<{ intent: string; label: string; icon: string }>;
  /** kind==='clarify'：路由器给出的犹豫原因。 */
  clarifyReason?: string;
  /** kind==='clarify'：已 pick 过（防重复执行）。 */
  clarifyResolved?: boolean;
  /** 中置信纠错 chips（「不是想要这个？」），点击改派执行。 */
  correctionOptions?: Array<{ intent: string; label: string; icon: string }>;
  /** 澄清 pick / 纠错改派 / 「接着」chip 重放执行所需的上下文。 */
  dispatchCtx?: DispatchContext;
  /** kind==='secondary'：组合意图的次级动作（「接着：xx →」chip）。 */
  secondaryIntent?: IntentKind;
  /** kind==='secondary'：chip 已点击执行过。 */
  secondaryUsed?: boolean;
  /** kind==='plan'：学习总管产出的看学练测步骤（PlanCard steps）。 */
  planSteps?: OrchestratorPlanStep[];
  /** kind==='plan'：后端已落库的 loop_run id，供恢复、回放与验收查询。 */
  loopRunId?: number;
  /** kind==='mystudy'：书房命中的资源（≤5 张卡）。 */
  mystudyItems?: WorkshopResourceListItemDto[];
  /** kind==='navigate'：确认气泡的目标路由 / 人话地名。 */
  navRoute?: string;
  navLabel?: string;
  /** kind==='error'：「重试」chip 的参数缓存。 */
  retry?: RetryInfo;
}

const emit = defineEmits<{
  (event: "open-resource", resourceId: number): void;
  (event: "history-saved", turn: WorkshopChatHistoryTurnDto): void;
}>();

const router = useRouter();
const route = useRoute();
const learning = useLearningStore();
const mainline = useMainlineStore();
const toast = useToastStore();
const activity = useWorkshopActivityStore();

const messages = ref<ChatMessage[]>([]);
const mentionItems = ref<MentionItem[]>([]);
const courseKcs = ref<CourseKcRef[]>([]);
const routing = ref(false);
const scrollRef = ref<HTMLElement | null>(null);
const composerRef = ref<{
  focus: () => void;
  prefill: (text: string) => void;
} | null>(null);

const tutorSse = useSseStream<TutorStreamResult>("/api/tutor/ask");
const workshopSse = useSseStream<WorkshopCreateResponse>(
  "/api/workshop/resources/stream",
);
// PLAN 专用：学习总管 orchestrator 流（与 GuideOverlay 同端点，独立实例避免串台）。
const orchestratorSse = useSseStream<OrchestratorResult>(
  "/api/orchestrator/turn/stream",
);

// 答疑流式期间「工具调用生命周期」时间轴的数据源（证据检索→作答→三审，实时 trace）。
const liveAskTraces = computed(() => tutorSse.traces.value);

const streamingAskId = ref<string | null>(null);
// PLAN 流式：当前正被 STATE_DELTA 实时点亮步骤的 plan 卡 id（dispatch 进度写入它）。
const streamingPlanId = ref<string | null>(null);
// 工具结果已展示但历史 POST 还没返回时，也算忙碌：避免「新对话」抢先清掉尚未保存的当前轮。
const pendingHistorySaves = ref(0);
// 本组件正在跑的 orchestrator 活动「运行令牌」（activity.start 返回值）。
// 镜像/收尾都持它写 store：向导（GuideOverlay）接管开新活动后，本组件过期流的数据被 store 忽略。
const planRunToken = ref<number | null>(null);
const askRunToken = ref<number | null>(null);
const resourceRunToken = ref<number | null>(null);
// 学生点了「⏹ 停止」：runAsk 收尾时据此跳过错误兜底（气泡已标「（已中断）」）。
const askInterrupted = ref(false);

// 会话窗口 id：本组件挂载（= 新开窗口）即生成；同一窗口内的多次提问共享同一值，
// 让「聊天记录」按会话分组而非每问一条。「新对话」或回看历史会话时会切换它。
const conversationId = ref<string>(genId());

// 续聊提示：从「聊天记录」回看一段旧会话后置 true，对话流顶部提示「新提问会并入这段会话」。
// 「新对话」或全新窗口为 false。仅作 UI 提示，落库的归并由 conversationId 决定。
const continuing = ref(false);

// 本会话累计的全部上传 id（含本轮）：B 通路 scoped 检索据此限定范围；
// 「新对话」/ 回看历史会话即清空（新会话视为无上传）。
const sessionUploadIds = ref<number[]>([]);

/** 记住本轮就绪附件的上传 id（去重），供后续轮次 scoped 检索。 */
function rememberUploads(atts: UploadAttachmentRef[]): void {
  if (atts.length === 0) return;
  const merged = new Set(sessionUploadIds.value);
  for (const a of atts) merged.add(a.id);
  sessionUploadIds.value = [...merged];
}

// 考核模式状态机（ASKME · 多轮填空）：开考后 1-3 轮，每轮出填空题、学生气泡内填空提交；QuizModeBar 驻顶。
const quiz = ref<{
  kcCodes: string[];
  round: number;
  total: 3;
  topic: string;
} | null>(null);
// 每条填空题（按消息 id）的学生填空草稿，提交时拼成作答串送评卷。
const quizDrafts = ref<Record<string, string[]>>({});

// 本会话纠错记录（≤3 条 FIFO）：学生点纠错 chips 改派后记入，随路由请求上送，
// 让 IntentRouterAgent 学会该学生的口头习惯。
const sessionCorrections = ref<IntentCorrectionRef[]>([]);

/** 开新对话窗口：清空当前对话流并换一个会话 id（此后提问归入新会话）。 */
function newChat(): void {
  if (busy.value) {
    toast.show("当前回复正在生成或保存，稍等一下再开新对话", "info");
    return;
  }
  playSfx("sfx_click");
  messages.value = [];
  streamingAskId.value = null;
  quiz.value = null;
  sessionCorrections.value = [];
  sessionUploadIds.value = [];
  conversationId.value = genId();
  continuing.value = false;
  scrollToBottom(true);
}

function prefillDraft(text: string): void {
  composerRef.value?.prefill(text);
  scrollToBottom(false);
}

/**
 * 历史回看：把一整段会话（同一 conversationId 的多轮问答，按时间正序）回放进对话流，
 * 并把当前会话 id 切到这段会话——于是学生可「接着这段对话继续问」。
 * 旧数据（conversationId 为 null）无法续聊，续问会新开一个会话以免污染分组。
 */
function payloadObject(
  turn: WorkshopChatHistoryTurnDto,
): Record<string, unknown> {
  return turn.responsePayload && typeof turn.responsePayload === "object"
    ? turn.responsePayload
    : {};
}

function historyAssistantMessage(
  turn: WorkshopChatHistoryTurnDto,
): ChatMessage {
  const payload = payloadObject(turn);
  const intent = asIntentKind(turn.intent);
  const meta = commandOf(intent);
  const kind = turn.responseKind as ChatMessage["kind"];
  const msg: ChatMessage = {
    id: genId(),
    role: "assistant",
    kind,
    intent,
    text: turn.responseText ?? "",
    toolLabel: turn.toolLabel ?? meta.label,
    toolIcon: meta.icon,
    streaming: false,
  };

  if (kind === "answer") {
    msg.groundingStatus = turn.groundingStatus ?? "NO_EVIDENCE";
    msg.evidencePageIds = turn.evidencePageIds;
    msg.evidence = pageIdsToCitations(turn.evidencePageIds);
    return msg;
  }
  if (kind === "plan") {
    msg.planSteps = Array.isArray(payload.planSteps)
      ? (payload.planSteps as OrchestratorPlanStep[])
      : [];
    return msg;
  }
  if (kind === "resource") {
    if (typeof payload.resourceId === "number")
      msg.resourceId = payload.resourceId;
    if (typeof payload.resourceType === "string") {
      msg.resourceType = payload.resourceType as WorkshopResourceType;
    }
    if (typeof payload.resourceTitle === "string")
      msg.resourceTitle = payload.resourceTitle;
    if (typeof payload.resourceGrounding === "string")
      msg.resourceGrounding = payload.resourceGrounding;
    if (typeof payload.resourceSafety === "string")
      msg.resourceSafety = payload.resourceSafety;
    if (typeof payload.resourceSummary === "string")
      msg.resourceSummary = payload.resourceSummary;
    if (Array.isArray(payload.resourceKcCodes))
      msg.resourceKcCodes = payload.resourceKcCodes as string[];
    if (
      payload.resourceContent &&
      typeof payload.resourceContent === "object"
    ) {
      msg.resourceContent = payload.resourceContent as WorkshopContent;
    }
    msg.resourceExpanded = false;
    return msg;
  }
  if (kind === "video") {
    if (typeof payload.videoKeyword === "string")
      msg.videoKeyword = payload.videoKeyword;
    return msg;
  }
  if (kind === "browse") {
    if (payload.browseRun && typeof payload.browseRun === "object") {
      msg.browseRun = payload.browseRun as BrowseRunDetailDto;
    }
    return msg;
  }
  if (kind === "mystudy") {
    if (Array.isArray(payload.mystudyItems)) {
      msg.mystudyItems = payload.mystudyItems as WorkshopResourceListItemDto[];
    }
    return msg;
  }
  if (kind === "navigate") {
    if (typeof payload.navRoute === "string") msg.navRoute = payload.navRoute;
    if (typeof payload.navLabel === "string") msg.navLabel = payload.navLabel;
    return msg;
  }
  if (kind === "clarify") {
    if (Array.isArray(payload.clarifyOptions)) {
      msg.clarifyOptions = payload.clarifyOptions as Array<{
        intent: string;
        label: string;
        icon: string;
      }>;
    }
    if (typeof payload.clarifyReason === "string")
      msg.clarifyReason = payload.clarifyReason;
  }
  return msg;
}

function loadConversation(turns: WorkshopChatHistoryTurnDto[]): void {
  const ordered = [...turns].sort((a, b) =>
    a.createdAt.localeCompare(b.createdAt),
  );
  const replay: ChatMessage[] = [];
  for (const turn of ordered) {
    replay.push({
      id: genId(),
      role: "user",
      kind: "text",
      text: turn.userText,
    });
    replay.push(historyAssistantMessage(turn));
  }
  messages.value = replay;
  quiz.value = null;
  sessionUploadIds.value = [];
  const existing = ordered.find((s) => s.conversationId)?.conversationId;
  conversationId.value = existing ?? genId();
  // 仅当这段历史真有 conversationId 才算「可续聊」；旧数据（null）续问会另起会话，不提示并入。
  continuing.value = Boolean(existing);
  scrollToBottom(true);
}
defineExpose({ loadConversation, newChat, prefillDraft });

// 资源生成「实时思考」状态：驱动 AgentStudioStream（替代旧的像素事务所动画 + 证据卡覆盖层）。
// 仅 'streaming' 时显示思考链；完成即就地内联展示资源，出错即出错误气泡，随后归 'idle'。
const officeStreamState = ref<"idle" | "streaming" | "done" | "error">("idle");
const officeTokenPreview = ref("");
const officeStreamLabel = ref("");
const browseBusy = ref(false);

// orchestrator 协同活动：GuideOverlay 或本组件 PLAN 写入 workshopActivity 时，
// AgentStudioStream 渲染思考链（右侧处理进度同步更新）。
const isOrchestratorActive = computed(
  () => activity.kind === "orchestrator" && activity.status === "streaming",
);

const busy = computed(
  () =>
    routing.value ||
    tutorSse.status.value === "streaming" ||
    tutorSse.status.value === "connecting" ||
    orchestratorSse.status.value === "streaming" ||
    orchestratorSse.status.value === "connecting" ||
    browseBusy.value ||
    officeStreamState.value === "streaming" ||
    isOrchestratorActive.value ||
    pendingHistorySaves.value > 0,
);

function commandOf(intent: IntentKind): { label: string; icon: string } {
  const cmd = SLASH_COMMANDS.find((c) => c.intent === intent);
  return { label: cmd?.label ?? intent, icon: cmd?.icon ?? "•" };
}

function asIntentKind(intent: string | null | undefined): IntentKind {
  const hit = SLASH_COMMANDS.find((c) => c.intent === intent);
  return (hit?.intent ?? "ASK") as IntentKind;
}

function assistantMessageById(id: string | null): ChatMessage | null {
  if (!id) return null;
  return (
    messages.value.find((m) => m.id === id && m.role === "assistant") ?? null
  );
}

function historyPayloadFor(m: ChatMessage): Record<string, unknown> {
  if (m.kind === "plan") return { planSteps: m.planSteps ?? [] };
  if (m.kind === "resource") {
    return {
      resourceId: m.resourceId,
      resourceType: m.resourceType,
      resourceTitle: m.resourceTitle,
      resourceGrounding: m.resourceGrounding,
      resourceSafety: m.resourceSafety,
      resourceSummary: m.resourceSummary,
      resourceKcCodes: m.resourceKcCodes ?? [],
      resourceContent: m.resourceContent ?? null,
    };
  }
  if (m.kind === "video") return { videoKeyword: m.videoKeyword ?? "" };
  if (m.kind === "browse") return { browseRun: m.browseRun ?? null };
  if (m.kind === "progress")
    return { masterySnapshot: learning.field.masterySnapshot };
  if (m.kind === "mystudy") return { mystudyItems: m.mystudyItems ?? [] };
  if (m.kind === "navigate")
    return { navRoute: m.navRoute ?? "", navLabel: m.navLabel ?? "" };
  if (m.kind === "clarify") {
    return {
      clarifyOptions: m.clarifyOptions ?? [],
      clarifyReason: m.clarifyReason ?? "",
    };
  }
  if (m.kind === "error") return { retryMode: m.retry?.mode ?? null };
  if (m.kind === "secondary")
    return { secondaryIntent: m.secondaryIntent ?? null };
  return {};
}

function responseTextForHistory(m: ChatMessage): string {
  if (m.text.trim()) return m.text.trim();
  if (m.kind === "plan") return "学习规划";
  if (m.kind === "resource") return m.resourceTitle ?? m.toolLabel ?? "资源";
  if (m.kind === "video") return m.videoKeyword ?? "相关视频";
  if (m.kind === "browse")
    return m.browseRun?.finalSummary || m.text || "联网查证";
  if (m.kind === "progress") return "学情报告";
  if (m.kind === "mystudy")
    return m.mystudyItems?.length ? "我的书房检索结果" : "";
  if (m.kind === "navigate") return m.navLabel ? `带你去${m.navLabel}` : "导航";
  if (m.kind === "clarify") return m.clarifyReason ?? "需要澄清意图";
  if (m.kind === "secondary")
    return m.secondaryIntent
      ? `接着：${commandOf(m.secondaryIntent).label}`
      : "";
  return "";
}

function groundingStatusForHistory(m: ChatMessage): string | null {
  return m.groundingStatus ?? m.resourceGrounding ?? null;
}

function emitSavedWorkshopTurn(
  request: WorkshopChatHistoryRecordRequest,
  recordId: number,
  m: ChatMessage,
): void {
  emit("history-saved", {
    id: `workshop-${recordId}`,
    conversationId: request.conversationId,
    userText: request.userText,
    intent: request.intent,
    responseKind: request.responseKind,
    toolLabel: request.toolLabel ?? null,
    responseText: request.responseText ?? null,
    responsePayload: request.responsePayload ?? {},
    evidencePageIds: m.evidencePageIds ?? [],
    groundingStatus: groundingStatusForHistory(m),
    createdAt: new Date().toISOString(),
  });
}

/**
 * 答疑「开始回答」即向左栏「历史问答」发一条占位记录（responseText 暂空、groundingStatus 暂 null），
 * 用与最终记录相同的 id（local-<messageId>）。流式完成后 emitSavedAnswerTurn 再用同一 id 覆盖更新，
 * 左栏据 id 去重 merge——于是学生一发问、记录立刻出现在左侧，而非等整段答案生成完才出现。
 */
function emitProvisionalAnswerTurn(
  messageId: string,
  question: string,
  intent: IntentKind,
  kcCodes: string[],
): void {
  const meta = commandOf(intent);
  emit("history-saved", {
    id: `local-${messageId}`,
    conversationId: conversationId.value,
    userText: question,
    intent,
    responseKind: "answer",
    toolLabel: intent === "ASK" ? "问答" : meta.label,
    responseText: "",
    responsePayload: { kcCodes },
    evidencePageIds: [],
    groundingStatus: null,
    createdAt: new Date().toISOString(),
  });
}

function emitSavedAnswerTurn(
  messageId: string,
  question: string,
  intent: IntentKind,
  result: TutorStreamResult,
  text: string,
  kcCodes: string[],
): void {
  const meta = commandOf(intent);
  emit("history-saved", {
    id: `local-${messageId}`,
    conversationId: conversationId.value,
    userText: question,
    intent,
    responseKind: "answer",
    toolLabel: intent === "ASK" ? "问答" : meta.label,
    responseText: text,
    responsePayload: {
      kcCodes,
      evidenceNote: result.evidence_note ?? "",
      personalizationNote: result.personalization_note ?? "",
      visualAids: result.visual_aids ?? [],
    },
    evidencePageIds: result.evidence_page_ids ?? [],
    groundingStatus: deriveGroundingStatus(result),
    createdAt: new Date().toISOString(),
  });
}

async function persistWorkshopHistoryTurn(
  ctx: DispatchContext,
  messageId: string | null,
): Promise<void> {
  const m = assistantMessageById(messageId);
  if (!m || m.kind === "answer" || m.kind === "text") return;
  const request: WorkshopChatHistoryRecordRequest = {
    conversationId: conversationId.value,
    userText: ctx.text,
    intent: m.intent ?? "ASK",
    responseKind: m.kind,
    toolLabel: m.toolLabel ?? (m.intent ? commandOf(m.intent).label : null),
    responseText: responseTextForHistory(m),
    responsePayload: historyPayloadFor(m),
  };
  pendingHistorySaves.value += 1;
  try {
    const response = await recordWorkshopChatHistoryTurn(request);
    emitSavedWorkshopTurn(request, response.id, m);
  } catch {
    /* 历史保存失败不打断当前工具结果。 */
  } finally {
    pendingHistorySaves.value = Math.max(0, pendingHistorySaves.value - 1);
  }
}

/**
 * 文本以已知斜杠命令 token 起手（如欢迎屏快捷语「/考考我 循环」，或手敲 / 命令但没走下拉菜单选中），
 * 解析出强制意图并剥离 token——否则「/考考我 循环」会被当普通文本送进意图路由器，置信不足而兜底澄清。
 */
function parseLeadingSlashCommand(
  text: string,
): { intent: IntentKind; text: string } | null {
  const trimmed = text.trimStart();
  if (!trimmed.startsWith("/")) return null;
  for (const cmd of SLASH_COMMANDS) {
    if (trimmed === cmd.token || trimmed.startsWith(`${cmd.token} `)) {
      return {
        intent: cmd.intent,
        text: trimmed.slice(cmd.token.length).trim(),
      };
    }
  }
  return null;
}

// ============ tutor_sse 工具族的 context 前缀 directive（蓝本 §0 固定文案） ============
// 考考我（多轮填空，契约 C4）：QUIZ_TURN_<n> 触发后端出填空题（不引课件）；
// QUIZ_JUDGE 触发评卷（仅命中课件才引 PPT）。标记须在 context 内供 graph 识别。
function quizAskDirective(round: number): string {
  return (
    `QUIZ_TURN_${round}\n` +
    `【考核·第${round}/3轮】你是考官。就该知识点出一道"填空题"：题干用 (1) (2) 标出 1~3 个空，` +
    `循序渐进引导学生掌握；本轮不要给答案、不要引用课件。`
  );
}
function quizJudgeDirective(round: number): string {
  return (
    `QUIZ_JUDGE\n` +
    `【考核·第${round}/3轮·评卷】判断学生填空对/部分对/错并简短点评；` +
    `仅当命中课件时才引用 PPT 帮其巩固或弄清错因，未命中则不要提及课件。`
  );
}
const EXPLAIN_CODE_DIRECTIVE =
  "【讲代码】逐行解释下面代码的执行过程与每行意图，标出常见陷阱；不要重写或优化成完整答案。";

function genId(): string {
  try {
    if (
      typeof crypto !== "undefined" &&
      typeof crypto.randomUUID === "function"
    ) {
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
  messages.value = messages.value.map((m) =>
    m.id === id ? { ...m, ...patch } : m,
  );
}

// ============ 滚动权归用户：仅当学生位于底部 ±40px 时才自动跟随 ============
const atBottom = ref(true);

function onScroll(): void {
  const el = scrollRef.value;
  if (!el) return;
  atBottom.value = el.scrollTop + el.clientHeight >= el.scrollHeight - 40;
}

function scrollToBottom(force = false): void {
  if (!force && !atBottom.value) return;
  void nextTick(() => {
    const el = scrollRef.value;
    if (!el) return;
    if (typeof el.scrollTo === "function") {
      el.scrollTo({ top: el.scrollHeight, behavior: "smooth" });
    } else {
      el.scrollTop = el.scrollHeight;
    }
  });
}

// ============ token 渲染 rAF 节流：合帧批量 flush，避免高频 token 抖动 ============
let pendingTokens: string | null = null;
let tokenRafId: number | null = null;

function flushPendingTokens(): void {
  if (tokenRafId !== null) {
    cancelAnimationFrame(tokenRafId);
    tokenRafId = null;
  }
  if (pendingTokens !== null && streamingAskId.value) {
    updateMessage(streamingAskId.value, { text: pendingTokens });
  }
  pendingTokens = null;
}

watch(tutorSse.tokens, (t) => {
  if (!streamingAskId.value) return;
  pendingTokens = t;
  if (tokenRafId !== null) return;
  tokenRafId = requestAnimationFrame(() => {
    tokenRafId = null;
    if (pendingTokens !== null && streamingAskId.value) {
      updateMessage(streamingAskId.value, { text: pendingTokens });
      pendingTokens = null;
      scrollToBottom();
    }
  });
});

onUnmounted(() => {
  accountContextActive = false;
  tutorSse.abort();
  workshopSse.abort();
  orchestratorSse.abort();
  if (tokenRafId !== null) cancelAnimationFrame(tokenRafId);
});

watch(
  [() => props.ownerId, () => session.user?.id ?? null],
  ([ownerId, sessionOwnerId]) => {
    if (ownerId === componentOwnerId && sessionOwnerId === componentOwnerId) return;
    accountContextActive = false;
    tutorSse.abort();
    workshopSse.abort();
    orchestratorSse.abort();
  },
);

watch(workshopSse.tokens, (t) => {
  officeTokenPreview.value = t.slice(0, 200);
});

// ASK：答疑流的 traces/plan 镜像进右侧 workshopActivity，驱动学生可读的处理进度。
watch(tutorSse.traces, (tr) => {
  if (askRunToken.value === null) return;
  activity.syncTraces("ask", tr, askRunToken.value);
});
watch(tutorSse.plan, (p) => {
  if (askRunToken.value === null) return;
  activity.syncPlan("ask", p, askRunToken.value);
});

// 资源生成与答疑复用同一进度抽屉；仅镜像既有 SSE，不发起额外请求。
watch(workshopSse.traces, (tr) => {
  if (resourceRunToken.value === null) return;
  activity.syncTraces("resource", tr, resourceRunToken.value);
});
watch(workshopSse.plan, (p) => {
  if (resourceRunToken.value === null) return;
  activity.syncPlan("resource", p, resourceRunToken.value);
});

// PLAN：orchestrator 流的 traces/plan 镜像进 workshopActivity（进度时间线自动更新）。
// 仅当本组件持有运行令牌时镜像——过期的流（如向导已开新活动）不得覆盖共享状态。
watch(orchestratorSse.traces, (tr) => {
  if (planRunToken.value === null) return;
  activity.syncTraces("orchestrator", tr, planRunToken.value);
});
watch(orchestratorSse.plan, (p) => {
  if (planRunToken.value === null) return;
  activity.syncPlan("orchestrator", p, planRunToken.value);
});
// PLAN 流式：dispatch 经 AG-UI STATE_DELTA 推送的真实 plan 步骤进度，实时写入当前 plan 卡点亮步骤。
watch(orchestratorSse.state, (st) => {
  if (!streamingPlanId.value) return;
  const steps = st.plan_steps;
  if (Array.isArray(steps)) {
    updateMessage(streamingPlanId.value, {
      planSteps: steps as OrchestratorPlanStep[],
    });
    scrollToBottom();
  }
});
// 运行可发现性：把后端预创建 loop run 的 CUSTOM 事件投影到当前计划卡。
watch(orchestratorSse.events, (events) => {
  if (!streamingPlanId.value) return;
  for (let index = events.length - 1; index >= 0; index -= 1) {
    const event = events[index];
    if (event?.type !== "CUSTOM" || event.name !== "runtime_run") continue;
    const value = event.value;
    if (!value || typeof value !== "object") return;
    const loopRunId = (value as Record<string, unknown>)["loop_run_id"];
    if (
      typeof loopRunId === "number" &&
      Number.isSafeInteger(loopRunId) &&
      loopRunId > 0
    ) {
      updateMessage(streamingPlanId.value, { loopRunId });
    }
    return;
  }
});

// ============ 多轮路由上下文 ============
/** 计入路由上下文的消息 kind（错误 / 澄清 / 「接着」chip 是 UI 噪音，不上送）。 */
const ROUTE_TURN_KINDS = new Set<ChatMessage["kind"]>([
  "text",
  "answer",
  "resource",
  "video",
  "browse",
  "plan",
  "progress",
  "mystudy",
  "navigate",
]);

/** 一条消息的路由摘要：user 原文截 200 字；assistant 取 kind + 标题/前 120 字。 */
function turnSummary(m: ChatMessage): string {
  if (m.role === "user") return m.text.slice(0, 200);
  const body =
    m.resourceTitle ?? (m.text || m.toolLabel || m.videoKeyword || "");
  return `[${m.kind}] ${body}`.slice(0, 120);
}

/** 最近 ≤6 条消息摘要（时间正序），供路由器做指代消解。 */
function buildRecentTurns(): IntentTurnRef[] {
  return messages.value
    .filter((m) => ROUTE_TURN_KINDS.has(m.kind))
    .slice(-6)
    .map((m) => ({ role: m.role, content: turnSummary(m) }));
}

/** 最近一条已执行意图的 assistant 消息 → lastIntent / lastKcCodes。 */
function lastExecutedIntent(): {
  intent: IntentKind;
  kcCodes: string[];
} | null {
  for (let i = messages.value.length - 1; i >= 0; i -= 1) {
    const m = messages.value[i];
    if (m && m.role === "assistant" && m.intent) {
      return {
        intent: m.intent,
        kcCodes: m.kcCodes ?? m.resourceKcCodes ?? [],
      };
    }
  }
  return null;
}

/** 纠错记录入队（≤3 条 FIFO）。 */
function pushCorrection(message: string, correctedIntent: string): void {
  const next = [...sessionCorrections.value, { message, correctedIntent }];
  while (next.length > 3) next.shift();
  sessionCorrections.value = next;
}

// ============ 置信分档：澄清气泡 / 纠错 chips ============
/** 澄清候选：路由猜测的意图置顶 + SLASH_COMMANDS 前排常用工具，共 5 个。 */
function buildClarifyOptions(
  guess: IntentKind,
  serverCandidates: IntentKind[] = [],
): Array<{ intent: string; label: string; icon: string }> {
  const picks: Array<{ intent: string; label: string; icon: string }> = [];
  const seen = new Set<string>();
  const add = (intent: IntentKind): void => {
    if (seen.has(intent) || picks.length >= 5) return;
    const meta = commandOf(intent);
    seen.add(intent);
    picks.push({ intent, label: meta.label, icon: meta.icon });
  };
  if (serverCandidates.length > 0) {
    for (const candidate of serverCandidates) add(candidate);
    return picks;
  }
  add(guess);
  for (const cmd of SLASH_COMMANDS) {
    if (picks.length >= 5) break;
    add(cmd.intent);
  }
  return picks;
}

function pushClarify(
  guess: IntentKind,
  ctx: DispatchContext,
  reason: string,
  serverCandidates: IntentKind[] = [],
): string {
  const id = genId();
  pushMessage({
    id,
    role: "assistant",
    kind: "clarify",
    text: "",
    clarifyOptions: buildClarifyOptions(guess, serverCandidates),
    clarifyReason: reason,
    dispatchCtx: ctx,
  });
  return id;
}

function onClarifyPick(m: ChatMessage, intentStr: string): void {
  if (m.clarifyResolved || !m.dispatchCtx) return;
  const cmd = SLASH_COMMANDS.find((c) => c.intent === intentStr);
  if (!cmd) return;
  playSfx("sfx_click");
  updateMessage(m.id, { clarifyResolved: true });
  void routeSelectedIntent(cmd.intent, m.dispatchCtx);
}

/** 纠错备选池：通用兜底工具（执行过的意图除外，取 3 个）。 */
const CORRECTION_POOL: IntentKind[] = ["ASK", "HANDOUT", "PRACTICE", "VIDEO"];

function attachCorrections(
  messageId: string,
  executed: IntentKind,
  ctx: DispatchContext,
): void {
  const options = CORRECTION_POOL.filter((k) => k !== executed)
    .slice(0, 3)
    .map((k) => {
      const meta = commandOf(k);
      return { intent: k, label: meta.label, icon: meta.icon };
    });
  updateMessage(messageId, { correctionOptions: options, dispatchCtx: ctx });
}

function onCorrectionPick(m: ChatMessage, intentStr: string): void {
  const cmd = SLASH_COMMANDS.find((c) => c.intent === intentStr);
  if (!cmd || !m.dispatchCtx) return;
  playSfx("sfx_click");
  pushCorrection(m.dispatchCtx.text, cmd.intent);
  updateMessage(m.id, { correctionOptions: [] });
  void routeSelectedIntent(cmd.intent, m.dispatchCtx);
}

// ============ 组合意图：「接着：xx →」chip ============
function pushSecondary(intent: IntentKind, ctx: DispatchContext): void {
  pushMessage({
    id: genId(),
    role: "assistant",
    kind: "secondary",
    text: "",
    secondaryIntent: intent,
    dispatchCtx: ctx,
  });
}

function onSecondaryPick(m: ChatMessage): void {
  if (!m.secondaryIntent || !m.dispatchCtx || m.secondaryUsed) return;
  playSfx("sfx_click");
  updateMessage(m.id, { secondaryUsed: true });
  void routeSelectedIntent(m.secondaryIntent, m.dispatchCtx);
}

/** FIX_BUG 只发送错题本中的 submission 引用；代码、题目与 verdict 由 Java 按当前用户回填。 */
async function interactionForRequestedIntent(
  requestedIntent: IntentKind | null,
): Promise<WorkshopInteractionContextRef | undefined> {
  if (requestedIntent !== "FIX_BUG") return undefined;
  const base: WorkshopInteractionContextRef = {
    sessionId: conversationId.value,
    currentScene: "town_hub",
    currentStage: "workshop",
    frontendAction: "open_bug_mine",
  };
  try {
    const errorBook = await getBugMineErrorBook();
    const candidate = errorBook[0];
    return candidate
      ? { ...base, latestSubmissionId: candidate.submissionId }
      : base;
  } catch {
    return base;
  }
}

/** 澄清、纠错和组合动作的统一策略门；显式选择也不能直接 dispatch。 */
async function routeSelectedIntent(
  requestedIntent: IntentKind,
  ctx: DispatchContext,
): Promise<void> {
  const interaction = await interactionForRequestedIntent(requestedIntent);
  const recentTurns = buildRecentTurns();
  const last = lastExecutedIntent();
  const req: IntentRouteRequest = {
    message: ctx.text,
    manualKcCodes: ctx.kcCodes,
    courseKcs: courseKcs.value,
    scope: quiz.value ? "quiz" : "default",
    source: "contextual_suggestion",
    requestedIntent,
  };
  if (interaction) req.interaction = interaction;
  if (recentTurns.length > 0) req.recentTurns = recentTurns;
  if (last) {
    req.lastIntent = last.intent;
    req.lastKcCodes = last.kcCodes;
  }
  if (sessionCorrections.value.length > 0) {
    req.sessionCorrections = [...sessionCorrections.value];
  }
  try {
    const res = await routeIntent(req);
    const routedCtx: DispatchContext = {
      ...ctx,
      kcCodes: res.kcCodes.length > 0 ? res.kcCodes : ctx.kcCodes,
      focusHint: res.focusHint || ctx.focusHint,
      target: res.target || ctx.target,
    };
    if (interaction?.latestSubmissionId) {
      routedCtx.latestSubmissionId = interaction.latestSubmissionId;
    }
    if (interaction) routedCtx.interaction = interaction;
    if (res.routeDecision) routedCtx.routeDecision = res.routeDecision;
    if (res.routeExecutionToken)
      routedCtx.routeExecutionToken = res.routeExecutionToken;
    if (res.needsClarification || res.routeDecision?.task.needsClarification) {
      const clarifyId = pushClarify(
        res.intent,
        routedCtx,
        res.clarificationPrompt ||
          res.routeDecision?.task.clarificationQuestion ||
          res.reason,
        res.candidateIntents ?? [],
      );
      void persistWorkshopHistoryTurn(routedCtx, clarifyId);
      return;
    }
    const msgId = await dispatchRoutedIntent(res.intent, routedCtx);
    void persistWorkshopHistoryTurn(routedCtx, msgId);
  } catch {
    const clarifyId = pushClarify(
      requestedIntent,
      ctx,
      "路由服务暂时不可用，未执行任何工具，请稍后重试。",
      [requestedIntent],
    );
    void persistWorkshopHistoryTurn(ctx, clarifyId);
  }
}

// ============ 主入口：一条消息 → 路由 → 分发 ============
async function onSubmit(payload: ComposerSubmitPayload): Promise<void> {
  playSfx("sfx_click");

  // 快捷语 / 手敲未走菜单的斜杠命令（如「/考考我 循环」）：解析成强制意图 + 剥离 token，
  // 否则会被当普通文本送进路由器，置信不足而兜底澄清。
  if (!payload.forcedIntent) {
    const parsed = parseLeadingSlashCommand(payload.text);
    if (parsed) {
      payload = {
        ...payload,
        forcedIntent: parsed.intent,
        source: "slash_command",
        text: parsed.text,
      };
    }
  }

  // 本轮随发的已就绪上传附件（向后兼容：旧调用无此字段时取空数组）。
  const attachments = payload.attachments ?? [];
  const images = payload.images ?? [];
  rememberUploads(attachments);

  if (
    images.length > 0 &&
    (quiz.value ||
      (payload.forcedIntent &&
        payload.forcedIntent !== "ASK" &&
        payload.forcedIntent !== "EXPLAIN_CODE"))
  ) {
    pushMessage({
      id: genId(),
      role: "assistant",
      kind: "error",
      text: "图片输入目前用于导师问答和代码讲解；请退出考核或取消当前工具后重新发送。",
    });
    return;
  }

  // 考核模式：普通输入即作答（不再路由，直接评卷下一轮）；/ 强制工具仍可临时打断（逃生门）。
  if (quiz.value && !payload.forcedIntent) {
    const quizUserMsg: ChatMessage = {
      id: genId(),
      role: "user",
      kind: "text",
      text: payload.text,
    };
    if (attachments.length > 0) quizUserMsg.attachments = attachments;
    pushMessage(quizUserMsg);
    scrollToBottom(true);
    await runQuizAnswer(payload.text);
    return;
  }

  // 多轮路由上下文取「本条消息之前」的历史 → 在 push 用户气泡前先行计算。
  const recentTurns = buildRecentTurns();
  const last = lastExecutedIntent();

  const userMsg: ChatMessage = {
    id: genId(),
    role: "user",
    kind: "text",
    text: payload.text,
  };
  if (payload.forcedIntent) {
    const meta = commandOf(payload.forcedIntent);
    userMsg.toolLabel = meta.label;
    userMsg.toolIcon = meta.icon;
  }
  if (payload.manualKcCodes.length > 0) userMsg.kcCodes = payload.manualKcCodes;
  if (attachments.length > 0) userMsg.attachments = attachments;
  if (images.length > 0) userMsg.imageCount = images.length;
  pushMessage(userMsg);
  scrollToBottom(true);

  let intent: IntentKind | null = payload.forcedIntent;
  let kcCodes: string[] = [...payload.manualKcCodes];
  let focusHint = "";
  let target = "";
  let confidence: number | null = null;
  let secondary: IntentKind | null = null;
  let routeReason = "";
  let routeNeedsClarification = false;
  let routeClarificationPrompt = "";
  let routeCandidates: IntentKind[] = [];
  let routedInteraction: WorkshopInteractionContextRef | undefined;
  let routeDecision: WorkshopRouteDecision | undefined;
  let routeExecutionToken: string | undefined;

  // 所有入口统一经过 Graph 的确定性策略门。显式工具只跳过语义消歧，不跳过 scope / 上下文 / allowlist。
  routing.value = true;
  try {
    routedInteraction = await interactionForRequestedIntent(
      payload.forcedIntent,
    );
    const req: IntentRouteRequest = {
      message: payload.text,
      manualKcCodes: payload.manualKcCodes,
      courseKcs: courseKcs.value,
      scope: quiz.value ? "quiz" : "default",
      source: payload.source ?? "composer_text",
    };
    if (payload.forcedIntent) req.requestedIntent = payload.forcedIntent;
    else if (images.length > 0) req.requestedIntent = "ASK";
    if (routedInteraction) req.interaction = routedInteraction;
    if (
      payload.source === "capability_launcher" &&
      payload.capabilityNamespace
    ) {
      req.capabilityNamespace = payload.capabilityNamespace;
    }
    if (recentTurns.length > 0) req.recentTurns = recentTurns;
    if (last) {
      req.lastIntent = last.intent;
      req.lastKcCodes = last.kcCodes;
    }
    if (sessionCorrections.value.length > 0) {
      req.sessionCorrections = [...sessionCorrections.value];
    }
    const res = await routeIntent(req);
    intent = res.intent;
    if (kcCodes.length === 0) kcCodes = res.kcCodes; // 没手动 KC 时用路由解析的 KC
    focusHint = res.focusHint;
    target = typeof res.target === "string" ? res.target : "";
    confidence = typeof res.confidence === "number" ? res.confidence : null;
    secondary = res.secondaryIntent ?? null;
    routeReason = res.reason ?? "";
    routeNeedsClarification =
      res.needsClarification === true ||
      res.routeDecision?.task.needsClarification === true;
    routeClarificationPrompt =
      res.clarificationPrompt ||
      res.routeDecision?.task.clarificationQuestion ||
      "";
    routeCandidates = Array.isArray(res.candidateIntents)
      ? res.candidateIntents
      : [];
    routeDecision = res.routeDecision;
    routeExecutionToken = res.routeExecutionToken;
  } catch {
    // 路由失败必须 fail closed；不再静默把请求降级为 Tutor。
    if (!intent) intent = "ASK";
    routeNeedsClarification = true;
    routeClarificationPrompt =
      "路由服务暂时不可用，未执行任何工具，请稍后重试。";
    routeCandidates = [intent];
  } finally {
    routing.value = false;
  }
  if (!intent) intent = "ASK";

  // 上传注入（契约 §3.5，红线：只进带标签 context、绝不当课件证据）：
  //   A 通路 = 本轮附件全文（截断）；B 通路 = scoped 检索本会话早先上传（默认不每问都检索）。
  // 仅 tutor_sse 工具族（ASK/ASKME/EXPLAIN_CODE）消费 contextHint；其余意图忽略，无副作用。
  // 无附件且本会话无早先上传时返回空串、不发任何网络请求。
  const uploadInjection = await buildUploadInjection(
    payload.text,
    attachments,
    sessionUploadIds.value,
  );

  const ctx: DispatchContext = {
    text: payload.text,
    kcCodes,
    focusHint,
    contextHint: mergeUploadContext(payload.contextHint, uploadInjection),
    target,
  };
  if (routedInteraction?.latestSubmissionId) {
    ctx.latestSubmissionId = routedInteraction.latestSubmissionId;
  }
  if (routedInteraction) ctx.interaction = routedInteraction;
  if (routeDecision) ctx.routeDecision = routeDecision;
  if (routeExecutionToken) ctx.routeExecutionToken = routeExecutionToken;

  // 服务端确定性策略门优先；自由文本置信度 <0.55 时也不调用工具。
  if (routeNeedsClarification || (confidence !== null && confidence < 0.55)) {
    const clarifyId = pushClarify(
      intent,
      ctx,
      routeClarificationPrompt || routeReason,
      routeCandidates,
    );
    void persistWorkshopHistoryTurn(ctx, clarifyId);
    return;
  }

  const msgId = images.length > 0
    ? intent === "ASK" || intent === "EXPLAIN_CODE"
      ? await dispatchIntent(intent, ctx, images)
      : null
    : await dispatchRoutedIntent(intent, ctx);
  if (images.length > 0 && msgId === null) {
    pushMessage({
      id: genId(),
      role: "assistant",
      kind: "error",
      text: "当前请求未进入导师问答，图片没有发送给其他工具。请改用导师问答或代码讲解。",
    });
    return;
  }
  void persistWorkshopHistoryTurn(ctx, msgId);

  // 中置信（0.45~0.7）：已正常执行，答后在该 assistant 消息挂纠错 chips。
  if (msgId && confidence !== null && confidence < 0.7) {
    attachCorrections(msgId, intent, ctx);
  }

  // 组合意图：主动作完成后出「接着：xx →」chip（考核模式中不打扰）。
  if (
    !isCompoundRoute(ctx.routeDecision) &&
    secondary &&
    secondary !== intent &&
    !quiz.value
  ) {
    pushSecondary(secondary, ctx);
  }
}

const COMPOUND_TOPOLOGIES = new Set([
  "SEQUENTIAL",
  "PARALLEL",
  "PLANNER_EXECUTOR",
]);

function isCompoundRoute(decision: WorkshopRouteDecision | undefined): boolean {
  return decision
    ? COMPOUND_TOPOLOGIES.has(decision.orchestration.topology)
    : false;
}

/** 复合拓扑只交给 Supervisor 一次；普通拓扑保持现有单工具分发表。 */
async function dispatchRoutedIntent(
  intent: IntentKind,
  ctx: DispatchContext,
): Promise<string | null> {
  if (isCompoundRoute(ctx.routeDecision)) return runPlan(ctx.text, ctx);
  return dispatchIntent(intent, ctx);
}

/** 17 工具分发表（蓝本 §0）：按 execMode 分派到 tutor / workshop / orchestrator / browse / 纯前端。 */
async function dispatchIntent(
  intent: IntentKind,
  ctx: DispatchContext,
  images: string[] = [],
): Promise<string | null> {
  if (intent === "ASK") {
    return runAsk(ctx.text, ctx.kcCodes, ctx.contextHint, { images });
  }
  if (intent === "ASKME") {
    return startQuiz(ctx);
  }
  if (intent === "EXPLAIN_CODE") {
    return runAsk(ctx.text, ctx.kcCodes, ctx.contextHint, {
      intent: "EXPLAIN_CODE",
      directive: EXPLAIN_CODE_DIRECTIVE,
      images,
    });
  }
  if (intent === "FIX_BUG") {
    return runDebug(ctx);
  }
  if (intent === "VIDEO") {
    return runVideo(ctx.kcCodes, ctx.focusHint || ctx.text);
  }
  if (intent === "BROWSE") {
    return runBrowse(ctx);
  }
  if (intent === "REVIEW") {
    runReview(ctx.kcCodes);
    return null;
  }
  if (intent === "PLAN") {
    return runPlan(ctx.text, ctx);
  }
  if (intent === "PROGRESS") {
    return runProgress();
  }
  if (intent === "NAVIGATE") {
    return runNavigate(ctx);
  }
  if (intent === "MY_STUDY") {
    return runMyStudy(ctx);
  }
  if (ctx.kcCodes.length === 0) {
    // 不强制 @KC：资源类意图但没锚定到知识点时，不报错，先把学生的问题答了（答案优先）。
    return runAsk(ctx.text, ctx.kcCodes, ctx.contextHint);
  }
  return runResource(intent, ctx.kcCodes, ctx.focusHint || ctx.text);
}

/** 复用现有 BugMineService → DebugAgentClient → /agents/debug/analyze 真链，不走 Tutor SSE。 */
async function runDebug(ctx: DispatchContext): Promise<string> {
  const meta = commandOf("FIX_BUG");
  const id = genId();
  if (!ctx.latestSubmissionId) {
    pushMessage({
      id,
      role: "assistant",
      kind: "error",
      intent: "FIX_BUG",
      text: "没有可验证的失败提交，先去训练场完成一次判题，再回来定位错误。",
      toolIcon: meta.icon,
      toolLabel: meta.label,
    });
    return id;
  }
  try {
    const session = await openBugMineSession({
      submissionId: ctx.latestSubmissionId,
    });
    learning.setPendingBugMineEntry({ sessionId: session.id });
    pushMessage({
      id,
      role: "assistant",
      kind: "navigate",
      intent: "FIX_BUG",
      text: "已由 DebugAgent 建立真实失败提交的调试会话，带你查看反例、变量轨迹和分层提示。",
      navRoute: "/bug-mine",
      navLabel: "错题矿洞",
      toolIcon: meta.icon,
      toolLabel: meta.label,
    });
    playSfx("sfx_success");
    await router.push("/bug-mine");
  } catch (error) {
    pushMessage({
      id,
      role: "assistant",
      kind: "error",
      intent: "FIX_BUG",
      text:
        error instanceof Error
          ? error.message
          : "DebugAgent 暂时不可用，请稍后重试。",
      toolIcon: meta.icon,
      toolLabel: meta.label,
    });
    playSfx("sfx_error");
  }
  return id;
}

// ============ ASKME 考核模式状态机 ============
const quizKcLabel = computed<string>(() => {
  const code = quiz.value?.kcCodes[0];
  return code ? kcDisplayName(code) : "";
});

/** 开考：进入 quiz 模式（第 1/3 轮），考官出第一道填空题（不引课件）。 */
async function startQuiz(ctx: DispatchContext): Promise<string> {
  quiz.value = {
    kcCodes: [...ctx.kcCodes],
    round: 1,
    total: 3,
    topic: ctx.text,
  };
  toast.show("进入考核模式：共 3 轮填空小测，在题目气泡里直接填空提交", "info");
  return runAsk(ctx.text, ctx.kcCodes, ctx.contextHint, {
    intent: "ASKME",
    directive: quizAskDirective(1),
  });
}

/** 学生作答（来自气泡填空或输入框）：先评卷（命中课件才引 PPT），再出下一轮填空题；3 轮后收尾。 */
async function runQuizAnswer(answer: string): Promise<void> {
  const q = quiz.value;
  if (!q) return;
  await runAsk(answer, q.kcCodes, "", {
    intent: "ASKME",
    directive: quizJudgeDirective(q.round),
  });
  if (!quiz.value) return; // 评卷途中学生手动退出
  if (q.round >= q.total) {
    finishQuiz();
    return;
  }
  const nextRound = q.round + 1;
  quiz.value = { ...q, round: nextRound };
  // 出下一轮填空题（沿用本次考核主题）。
  await runAsk(q.topic, q.kcCodes, "", {
    intent: "ASKME",
    directive: quizAskDirective(nextRound),
  });
}

/** 记录某题某空的填写草稿（ref 深响应，嵌套赋值即触发更新）。 */
function setQuizDraft(id: string, index: number, value: string): void {
  if (!quizDrafts.value[id]) quizDrafts.value[id] = [];
  quizDrafts.value[id][index] = value;
}

/** 气泡内「提交作答」：把该题各空草稿拼成作答串，作为用户气泡推入并触发评卷。 */
async function submitQuizFill(m: ChatMessage): Promise<void> {
  if (!quiz.value || !m.quiz) return;
  const draft = quizDrafts.value[m.id] ?? [];
  const answer = m.quiz.blanks
    .map((b, i) => `(${b.index}) ${(draft[i] ?? "").trim()}`)
    .join("  ");
  pushMessage({ id: genId(), role: "user", kind: "text", text: answer });
  scrollToBottom(true);
  await runQuizAnswer(answer);
}

function finishQuiz(): void {
  const q = quiz.value;
  quiz.value = null;
  playSfx("sfx_success");
  toast.show("考核结束：3 轮小测完成，小结与掌握度建议见上方点评", "success");
  // 考核完成回流学习事件（后端通用审计落库）。
  // TODO(LearningEvent)：后续由后端把 quiz_finished 消费进画像「知识基础/情绪坚持」维度与任务 evidence。
  if (q) {
    void postLearningEvent({
      eventType: "quiz_finished",
      payload: { kcCodes: q.kcCodes, rounds: q.total },
    }).catch(() => {
      /* 回流失败静默，不打扰学生 */
    });
  }
}

function exitQuiz(): void {
  playSfx("sfx_click");
  quiz.value = null;
  toast.show("已退出考核模式", "info");
}

// 面向「非科班 Python 初学者」：B站检索词一律以「Python」打头、并加「教程」偏向入门讲解；
// 未提其它语言时默认 Python（后端再按语言/领域过滤掉机器人/C 语言等跑题结果）。
function buildVideoKeyword(topic: string, kcCodes: string[]): string {
  const kcPart = kcCodes
    .map((c) => kcDisplayName(c))
    .filter(Boolean)
    .join(" ");
  const cleanTopic = (topic || "")
    .replace(/[?？!！。.,，、:：]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  return `Python ${kcPart} ${cleanTopic} 教程`
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 50);
}

// 学生语句是否表达「想看视频」——B站讲解仅在该意图或 / 命令下触发，不再每答必带。
const VIDEO_INTENT_TOKENS = [
  "视频",
  "b站",
  "bilibili",
  "讲解视频",
  "看视频",
  "教学视频",
  "up主",
  "找视频",
  "推荐视频",
  "影片",
];
function wantsVideo(text: string): boolean {
  const t = (text || "").toLowerCase();
  return VIDEO_INTENT_TOKENS.some((k) => t.includes(k));
}

/**
 * VIDEO 意图：不走生成/SSE，直接出一条「视频卡片」气泡，由 BilibiliVideoCards 按关键词
 * 现取现渲染（外部资源）。关键词 = Python + KC 显示名 + 学习重点（去重去空、限长）。
 */
function runVideo(kcCodes: string[], focus: string): string {
  const meta = commandOf("VIDEO");
  const keyword = buildVideoKeyword(focus, kcCodes);
  playSfx("sfx_success");
  const id = genId();
  pushMessage({
    id,
    role: "assistant",
    kind: "video",
    intent: "VIDEO",
    text: "",
    videoKeyword: keyword,
    toolIcon: meta.icon,
    toolLabel: meta.label,
  });
  return id;
}

async function runBrowse(ctx: DispatchContext): Promise<string> {
  const meta = commandOf("BROWSE");
  const id = genId();
  const goal = (ctx.focusHint || ctx.text).trim();
  pushMessage({
    id,
    role: "assistant",
    kind: "browse",
    intent: "BROWSE",
    text: "正在联网查证…",
    streaming: true,
    browseRun: null,
    toolIcon: meta.icon,
    toolLabel: meta.label,
  });
  browseBusy.value = true;
  try {
    const created = await createBrowseRun({ goal });
    const detail = await getBrowseRun(created.id);
    const text =
      detail.finalSummary ||
      (detail.error
        ? `联网查证失败：${detail.error}`
        : "联网查证已完成，见下方步骤与来源。");
    updateMessage(id, {
      text,
      browseRun: detail,
      streaming: false,
    });
    playSfx(detail.status === "FAILED" ? "sfx_error" : "sfx_success");
  } catch (err) {
    const message =
      err instanceof Error ? err.message : "联网查证暂时不可用，请稍后重试";
    playSfx("sfx_error");
    updateMessage(id, {
      kind: "error",
      text: message,
      streaming: false,
      retry: { mode: "browse", ctx },
    });
  } finally {
    browseBusy.value = false;
  }
  scrollToBottom();
  return id;
}

/**
 * REVIEW 意图：记忆矿脉回声复习。不走生成/SSE，引导学生去错题矿洞按 FSRS 到期 KC 复盘；
 * kcCodes 为后端 ReviewSignal 算出的待复习知识点（可空），仅用于提示，矿洞页自取到期排序。
 */
function runReview(kcCodes: string[]): void {
  playSfx("sfx_success");
  const names = kcCodes.map((c) => kcDisplayName(c)).filter(Boolean);
  const tip = names.length
    ? `记忆矿脉回声：「${names.join("、")}」快遗忘了，带你去错题矿洞复盘。`
    : "记忆矿脉回声：带你去错题矿洞复盘最该巩固的知识点。";
  toast.show(tip, "info");
  void router.push("/bug-mine");
}

/** 答疑答案内「看相关视频」入口：按答案主题 / KC 出一条 B站视频气泡。 */
function onAnswerVideo(m: ChatMessage): void {
  runVideo(m.kcCodes ?? [], m.suggestTopic ?? "");
}

/** 答疑答案内「生成讲义 / 练习」入口：按答案 KC 直接生成。 */
function onAnswerGenerate(m: ChatMessage, type: WorkshopResourceType): void {
  void runResource(type, m.kcCodes ?? [], m.suggestTopic ?? "");
}

function onAnswerFeedback(m: ChatMessage, up: boolean): void {
  void postLearningEvent({
    eventType: "answer_feedback",
    payload: {
      messageId: m.id,
      useful: up,
      intent: m.intent ?? "ASK",
      kcCodes: m.kcCodes ?? [],
    },
  }).catch(() => {
    /* 轻反馈失败不打断对话 */
  });
}

// ============ tutor_sse：ASK / ASKME / EXPLAIN_CODE 共用管线 ============
async function runAsk(
  question: string,
  kcCodes: string[],
  context: string,
  opts: AskOptions = {},
): Promise<string> {
  const id = genId();
  const intent: IntentKind = opts.intent ?? "ASK";
  const initial: ChatMessage = {
    id,
    role: "assistant",
    kind: "answer",
    intent,
    text: "",
    streaming: true,
  };
  if (intent !== "ASK") {
    // 非默认答疑的 tutor 工具（考考我/讲代码/修报错）：气泡带工具徽标。
    const meta = commandOf(intent);
    initial.toolLabel = meta.label;
    initial.toolIcon = meta.icon;
  }
  pushMessage(initial);
  streamingAskId.value = id;
  askInterrupted.value = false;
  // 一开始回答就向左栏「历史问答」发占位记录；完成后用同一 id 覆盖更新（见 emitSavedAnswerTurn）。
  emitProvisionalAnswerTurn(id, question, intent, kcCodes);

  // 工具差异以 context 前缀 directive 注入（蓝本 §0），graph 不需要新端点。
  const mergedContext = opts.directive
    ? context
      ? `${opts.directive}\n${context}`
      : opts.directive
    : context;
  const body: TutorAskRequest = {
    question,
    kcCodes,
    context: mergedContext,
    conversationId: conversationId.value,
  };
  if (opts.style) body.style = opts.style;
  if (opts.images?.length) body.images = opts.images;

  const askLabel = question.trim()
    ? `答疑：${question.trim().slice(0, 30)}`
    : "答疑";
  const runToken = activity.start("ask", askLabel);
  askRunToken.value = runToken;
  await tutorSse.start(body);
  activity.finish(tutorSse.status.value === "done", runToken);
  if (askRunToken.value === runToken) askRunToken.value = null;
  // 收尾：把 rAF 攒着的最后一段 token 落进消息（中断/出错时也能看到已生成部分）。
  flushPendingTokens();
  streamingAskId.value = null;

  if (askInterrupted.value) {
    // 学生手动「⏹ 停止」：气泡已由 onStopStream 标记（已中断），不按错误处理。
    askInterrupted.value = false;
    return id;
  }

  if (tutorSse.status.value === "done" && tutorSse.result.value) {
    playSfx("sfx_success");
    const r = tutorSse.result.value;
    const answerText = normalizeAnswerText(r, tutorSse.tokens.value);
    updateMessage(id, {
      text: answerText,
      groundingStatus: deriveGroundingStatus(r),
      evidencePageIds: r.evidence_page_ids ?? [],
      evidence: toCitations(r),
      // 三审徽章只在 live 答疑写入；review 缺省（旧后端）时不写，气泡自然不显徽章。
      ...(r.review ? { review: r.review } : {}),
      // 课件证据总览说明（live 才有；为空则不写）。
      ...(r.evidence_note ? { evidenceNote: r.evidence_note } : {}),
      // 画像个性化说明 →「✨ 为你定制」小标签（live 才有；为空则不写）。
      ...(r.personalization_note
        ? { personalizationNote: r.personalization_note }
        : {}),
      // 考考我出题（多轮填空）：带 blanks 时气泡内渲染填空 input + 提交作答。
      ...(r.quiz &&
      r.quiz.phase === "ask" &&
      Array.isArray(r.quiz.blanks) &&
      r.quiz.blanks.length > 0
        ? {
            quiz: {
              round: r.quiz.round ?? quiz.value?.round ?? 1,
              stem: r.quiz.stem ?? r.answer_md ?? "",
              blanks: r.quiz.blanks.map((b) => ({
                index: b.index,
                hint: b.hint ?? "",
              })),
            },
          }
        : {}),
      visualAids: r.visual_aids ?? [],
      // 工具调用轨迹快照 → 折叠「⚙ 工具调用」行（ToolTraceLine）。
      traces: [...tutorSse.traces.value],
      streaming: false,
      // 工具产出按需触发：「看相关视频/生成讲义」按钮常驻为手动入口；
      // 「相关 B站讲解」仅当学生语句表达视频意图（wantsVideo）时才自动内嵌。
      // 图解由后端按「要图」意图返回（visual_aids 为空则不显示），无需前端再判。
      suggestTopic: question.trim(),
      kcCodes,
      showVideo: wantsVideo(question),
      videoKeyword: buildVideoKeyword(question, kcCodes),
    });
    emitSavedAnswerTurn(id, question, intent, r, answerText, kcCodes);
  } else {
    playSfx("sfx_error");
    updateMessage(id, {
      kind: "error",
      text: tutorSse.error.value?.message ?? "AI 助教暂时没答上来，再试一次",
      streaming: false,
      retry: { mode: "ask", question, kcCodes, context, opts },
    });
  }
  scrollToBottom();
  return id;
}

/** 「⏹ 停止」：中断导师流，保留已生成部分并标注（已中断）。 */
function onStopStream(m: ChatMessage): void {
  if (!m.streaming) return;
  playSfx("sfx_click");
  askInterrupted.value = true;
  tutorSse.abort();
  flushPendingTokens();
  const base = tutorSse.tokens.value;
  updateMessage(m.id, {
    streaming: false,
    text: base ? `${base}\n\n（已中断）` : "（已中断）",
  });
}

/** 错误气泡「↻ 重试」：重放缓存的上次 runAsk / runResource 参数。 */
function onRetry(m: ChatMessage): void {
  const r = m.retry;
  if (!r || busy.value) return;
  playSfx("sfx_click");
  if (r.mode === "ask") {
    void runAsk(r.question, r.kcCodes, r.context, r.opts);
  } else if (r.mode === "resource") {
    void runResource(r.type, r.kcCodes, r.focusHint);
  } else {
    void runBrowse(r.ctx);
  }
}

// ============ 换讲法闭环：图解 / 逻辑链 / 示例上手 ============
const STYLE_CHIPS: Array<{ style: CognitiveStyle; label: string }> = [
  { style: "visual", label: "✦ 图解" },
  { style: "logical", label: "⛓ 逻辑链" },
  { style: "trial", label: "⚒ 示例上手" },
];

function onStyleChip(m: ChatMessage, style: CognitiveStyle): void {
  playSfx("sfx_click");
  // 因材施教闭环：学生显式采纳某讲法 → 回流画像 cognitive_style（失败静默）。
  void styleEngaged(style, m.kcCodes?.[0]).catch(() => {
    /* 画像回流失败静默 */
  });
  void runAsk(m.suggestTopic ?? m.text.slice(0, 200), m.kcCodes ?? [], "", {
    style,
  });
}

// ============ orchestrator_sse：PLAN 学习规划 ============
const PLAN_PANEL_ROUTES: Record<string, string> = {
  library: "/library",
  training: "/training",
  problems: "/problems",
  workshop: "/workshop",
  tutor: "/workshop",
  examples: "/examples",
  review: "/review-hall",
};

function routePersonalizationSummary(
  decision: WorkshopRouteDecision | undefined,
): string | undefined {
  return decision?.personalization?.explanations.find(
    (explanation) => explanation.trim().length > 0,
  );
}

/** C3 搜索摘要只引用公开候选 label，不透出候选 id、成本数值或内部推理。 */
function routeSearchSummary(
  decision: WorkshopRouteDecision | undefined,
): string | undefined {
  const search = decision?.search;
  if (!search) return undefined;
  const selected = search.candidates.find(
    (candidate) => candidate.candidateId === search.selectedCandidateId,
  );
  if (!selected) return undefined;
  return `已比较 ${search.candidates.length} 条受限学习路径，优先执行「${selected.label}」`;
}

async function runPlan(text: string, ctx?: DispatchContext): Promise<string> {
  const runToken = activity.start("orchestrator", "学习规划");
  planRunToken.value = runToken;

  // history 取最近 6 条 role/content；当前这条消息已在 onSubmit push，须从历史中剔除。
  const turns = buildRecentTurns();
  const tail = turns[turns.length - 1];
  const history =
    tail && tail.role === "user" && tail.content === text.slice(0, 200)
      ? turns.slice(0, -1)
      : turns;

  const body: OrchestratorTurnRequest = {
    sessionId: conversationId.value,
    message: text,
    history,
    courseKcCodes: courseKcs.value.map((k) => k.code),
  };
  if (ctx?.kcCodes.length) body.recentKcCodes = [...ctx.kcCodes];
  if (ctx?.routeDecision) body.routeDecision = ctx.routeDecision;
  if (ctx?.routeExecutionToken)
    body.routeExecutionToken = ctx.routeExecutionToken;
  if (ctx?.interaction) body.interaction = ctx.interaction;

  // 先落一张流式 plan 卡：dispatch 进度经 STATE_DELTA → watch(orchestratorSse.state) 实时点亮步骤。
  const meta = commandOf("PLAN");
  const id = genId();
  streamingPlanId.value = id;
  pushMessage({
    id,
    role: "assistant",
    kind: "plan",
    intent: "PLAN",
    text: "",
    planSteps: [],
    streaming: true,
    toolIcon: meta.icon,
    toolLabel: meta.label,
    personalizationSummary: routePersonalizationSummary(ctx?.routeDecision),
    searchSummary: routeSearchSummary(ctx?.routeDecision),
  });

  await orchestratorSse.start(body);
  streamingPlanId.value = null;

  const result = orchestratorSse.result.value;
  const ok = orchestratorSse.status.value === "done" && result !== null;
  // 持令牌收尾：若向导（GuideOverlay）已开新活动，本次过期收尾被 store 忽略。
  activity.finish(ok, runToken);
  if (planRunToken.value === runToken) planRunToken.value = null;

  if (!ok || !result) {
    playSfx("sfx_error");
    updateMessage(id, {
      kind: "error",
      text:
        orchestratorSse.error.value?.message ??
        "学习规划暂时没排出来，再试一次",
      streaming: false,
    });
    return id;
  }

  playSfx("sfx_success");
  // 最终态：用 STATE_DELTA 累积的真实进度（status 准确）覆盖；state 缺失时回退 result.plan。
  const liveSteps = orchestratorSse.state.value.plan_steps;
  const finalSteps =
    Array.isArray(liveSteps) && liveSteps.length > 0
      ? (liveSteps as OrchestratorPlanStep[])
      : (result.plan ?? []);
  updateMessage(id, {
    text: result.reply_md || orchestratorSse.tokens.value,
    planSteps: finalSteps,
    streaming: false,
  });
  return id;
}

/** PlanCard open-step 载荷（与 PlanCard 的 PlanStep 结构兼容，只取深链所需字段）。 */
interface PlanOpenStep {
  panel: string | null;
  kc_codes?: string[];
  resource_type?: string | null;
}

/**
 * PlanCard「前往」：panel 简称 → 实际路由（以路由表为准；lecture 需要章号）。
 * 深链上下文：步骤锚定的 KC 以 `kc` query 随行（练习/图书馆/复盘/训练场等保留 KC 上下文）；
 * 去工坊（workshop/tutor）再带 `type`（resource_type），落地后可一键接着生成。
 */
function onPlanOpen(step: PlanOpenStep): void {
  playSfx("sfx_click");
  const panel = step.panel ?? "";
  if (!panel) return;
  const kcCodes = step.kc_codes ?? [];
  const query: Record<string, string> = {};
  if (kcCodes.length > 0) query.kc = kcCodes.join(",");
  if ((panel === "workshop" || panel === "tutor") && step.resource_type) {
    query.type = step.resource_type;
  }
  if (panel === "lecture") {
    void router.push({
      path: `/lecture/${mainline.firstActionableChapter}`,
      query,
    });
    return;
  }
  if (panel === "training") {
    toast.show("去训练场练一练", "info");
  }
  void router.push({ path: PLAN_PANEL_ROUTES[panel] ?? "/world", query });
}

// ============ 纯前端：PROGRESS 学情报告 ============
async function runProgress(): Promise<string> {
  if (learning.field.masterySnapshot.length === 0) {
    try {
      await learning.loadField();
    } catch {
      /* 学情拉取失败：仍出卡，由卡内空态兜底 */
    }
  }
  playSfx("sfx_success");
  const meta = commandOf("PROGRESS");
  const id = genId();
  pushMessage({
    id,
    role: "assistant",
    kind: "progress",
    intent: "PROGRESS",
    text: "",
    toolIcon: meta.icon,
    toolLabel: meta.label,
  });
  return id;
}

/** ProgressCard「去练」：toast 提示后直达训练场。 */
function onPracticeKc(kcCode: string): void {
  playSfx("sfx_click");
  toast.show(`去训练场练「${kcDisplayName(kcCode)}」`, "info");
  void router.push("/training");
}

// ============ 纯前端：NAVIGATE 带路 ============
const NAV_TARGETS: Record<string, { route: string; label: string }> = {
  library: { route: "/library", label: "图书馆" },
  arena: { route: "/expedition", label: "无尽远征" },
  bugmine: { route: "/bug-mine", label: "错题矿洞" },
  world: { route: "/world", label: "小镇广场" },
  workshop: { route: "/workshop", label: "百晓工坊" },
};

async function runNavigate(ctx: DispatchContext): Promise<string> {
  const hit = ctx.target ? NAV_TARGETS[ctx.target] : undefined;
  if (!hit) {
    // 未知地标：回退答疑，把学生的话正常答掉。
    return runAsk(ctx.text, ctx.kcCodes, ctx.contextHint);
  }
  playSfx("sfx_success");
  const meta = commandOf("NAVIGATE");
  const id = genId();
  pushMessage({
    id,
    role: "assistant",
    kind: "navigate",
    intent: "NAVIGATE",
    text: `带你去${hit.label}`,
    navRoute: hit.route,
    navLabel: hit.label,
    toolIcon: meta.icon,
    toolLabel: meta.label,
  });
  return id;
}

function onNavigateGo(m: ChatMessage): void {
  if (!m.navRoute) return;
  playSfx("sfx_click");
  void router.push(m.navRoute);
}

// ============ 纯前端：MY_STUDY 找我的资料 ============
async function runMyStudy(ctx: DispatchContext): Promise<string> {
  try {
    await learning.loadResources();
  } catch {
    /* 列表拉取失败：按现有缓存过滤 */
  }

  // 过滤词：focusHint 整句 + 空白切分（≥2 字）+ 路由锚定 KC 的 code / 显示名。
  const terms: string[] = [];
  const hint = ctx.focusHint.trim();
  if (hint) {
    terms.push(hint.toLowerCase());
    for (const part of hint.split(/\s+/)) {
      if (part.length >= 2) terms.push(part.toLowerCase());
    }
  }
  for (const code of ctx.kcCodes) {
    terms.push(code.toLowerCase());
    const name = kcDisplayName(code);
    if (name && name !== code) terms.push(name.toLowerCase());
  }

  const list = learning.resources.list;
  const textLower = ctx.text.toLowerCase();
  const matched =
    terms.length === 0
      ? list
      : list.filter((item) => {
          // 标题反向包含：学生消息里直接提到了某资源标题。
          const titleLower = item.title.toLowerCase();
          if (titleLower.length >= 2 && textLower.includes(titleLower))
            return true;
          const kcNames = item.kcCodes.map((c) => kcDisplayName(c)).join(" ");
          const hay =
            `${item.title} ${commandOf(item.type).label} ${item.kcCodes.join(" ")} ${kcNames}`.toLowerCase();
          return terms.some((t) => hay.includes(t));
        });
  const shown = matched.slice(0, 5);

  playSfx("sfx_success");
  const meta = commandOf("MY_STUDY");
  const id = genId();
  pushMessage({
    id,
    role: "assistant",
    kind: "mystudy",
    intent: "MY_STUDY",
    text:
      shown.length > 0
        ? ""
        : "书房里还没找到相关的资源。直接说想学什么，或用 /讲义 /练习题 现做一份？",
    mystudyItems: shown,
    toolIcon: meta.icon,
    toolLabel: meta.label,
  });
  return id;
}

// ============ workshop_sse：6 类资源生成 ============
async function runResource(
  type: WorkshopResourceType,
  kcCodes: string[],
  focusHint: string,
): Promise<string> {
  // 不强制 @KC：调用方（dispatchIntent）已保证无 KC 时改走答疑；这里仅作防御性兜底。
  if (kcCodes.length === 0) {
    return runAsk(focusHint, kcCodes, "");
  }

  officeStreamState.value = "streaming";
  officeTokenPreview.value = "";
  officeStreamLabel.value = `生成${commandOf(type).label}`;
  const runToken = activity.start("resource", officeStreamLabel.value);
  resourceRunToken.value = runToken;

  const trimmed = focusHint.trim();
  const reqPayload =
    trimmed.length > 0
      ? { kcCodes, type, focusHint: trimmed }
      : { kcCodes, type };
  learning.streamCreateResource({ kcCodes, type });

  await workshopSse.start(reqPayload);
  const resourceOk =
    workshopSse.status.value === "done" && workshopSse.result.value !== null;
  activity.finish(resourceOk, runToken);
  if (resourceRunToken.value === runToken) resourceRunToken.value = null;

  if (resourceOk && workshopSse.result.value) {
    playSfx("sfx_success");
    const result = workshopSse.result.value;

    // 取回完整资源（含 content）用于「在对话里就地浏览」：content 直传子视图，
    // 绝不写入 learning.resources.current 单槽（同屏多份资源会互相覆盖）。
    let detail: WorkshopResourceDto | null = null;
    try {
      detail = await getWorkshopResource(result.resourceId);
    } catch {
      detail = null;
    }

    // 同步「我的书房」列表（历史资源库入口保留），但不依赖单槽。
    await learning.loadResources();
    const listItem = learning.resources.list.find(
      (r) => r.id === result.resourceId,
    );
    const meta = commandOf(type);
    const grounding: string =
      detail?.groundingStatus ??
      listItem?.groundingStatus ??
      (result.status === "FAILED" ? "FAILED" : "PASSED");

    const msg: ChatMessage = {
      id: genId(),
      role: "assistant",
      kind: "resource",
      intent: type,
      text: "",
      resourceId: result.resourceId,
      resourceType: type,
      resourceTitle: detail?.title ?? listItem?.title ?? meta.label,
      resourceGrounding: grounding,
      toolIcon: meta.icon,
      toolLabel: meta.label,
      // 生成完那条默认展开 → 直接在对话气泡内联展示全文。
      resourceExpanded: true,
    };
    if (detail) {
      msg.resourceContent = detail.content;
      msg.resourceSafety = detail.safetyStatus;
      msg.resourceKcCodes = detail.kcCodes;
      if (detail.summary) msg.resourceSummary = detail.summary;
    }
    pushMessage(msg);

    officeStreamState.value = "idle";
    officeTokenPreview.value = "";
    officeStreamLabel.value = "";

    // R-15：旧 mainQuest「学」里程碑钩子已退役（主线进度由后端 mainline stage 三件套权威）。
    toast.show("已生成并收入「我的书房」", "success");
    return msg.id;
  }

  officeStreamState.value = "error";
  playSfx("sfx_error");
  const errId = genId();
  pushMessage({
    id: errId,
    role: "assistant",
    kind: "error",
    text: workshopSse.error.value?.message ?? "资源生成失败，请稍后重试。",
    retry: { mode: "resource", type, kcCodes, focusHint },
  });
  officeStreamState.value = "idle";
  officeTokenPreview.value = "";
  officeStreamLabel.value = "";
  return errId;
}

/**
 * 资源卡内联展开/收起。历史资源卡（无 content）首次展开时按 resourceId 懒取详情，
 * content 直传子视图——绝不复用 learning.resources.current 单槽。
 */
async function toggleResource(m: ChatMessage): Promise<void> {
  playSfx("sfx_click");
  const next = !m.resourceExpanded;
  if (next && !m.resourceContent && m.resourceId && !m.resourceLoading) {
    updateMessage(m.id, { resourceExpanded: true, resourceLoading: true });
    try {
      const detail = await getWorkshopResource(m.resourceId);
      const patch: Partial<ChatMessage> = {
        resourceLoading: false,
        resourceContent: detail.content,
        resourceType: detail.type,
        resourceTitle: detail.title,
        resourceGrounding: detail.groundingStatus,
        resourceSafety: detail.safetyStatus,
        resourceKcCodes: detail.kcCodes,
      };
      if (detail.summary) patch.resourceSummary = detail.summary;
      updateMessage(m.id, patch);
    } catch {
      updateMessage(m.id, { resourceLoading: false, resourceExpanded: false });
      toast.show("资源载入失败，请到「我的书房」查看", "error");
    }
    return;
  }
  updateMessage(m.id, { resourceExpanded: next });
}

function openResource(resourceId: number): void {
  playSfx("sfx_click");
  emit("open-resource", resourceId);
}

function goToPage(pageId: number): void {
  playSfx("sfx_click");
  void router.push({ path: "/library", query: { pageId: String(pageId) } });
}

// ============ 深链落地：/workshop?kc=...&type=...（向导计划「前往工坊」带来的上下文） ============
/** query 值归一成单个非空字符串（vue-router 的 LocationQueryValue 可能是数组/null）。 */
function firstQueryValue(value: unknown): string | null {
  const raw = Array.isArray(value) ? value[0] : value;
  return typeof raw === "string" && raw.trim().length > 0 ? raw.trim() : null;
}

/**
 * 把深链携带的 kc + 资源类型化作一枚「接着」chip：点击才按该上下文生成，绝不自动开火，
 * 也绝不触发 orchestrator 重新规划——向导的计划留在共享活动状态里原样不动，
 * 学生从工坊返回或反复进出也不会重复生成向导计划。
 */
function consumeDeepLink(): void {
  const kcStr = firstQueryValue(route.query.kc);
  if (!kcStr) return;
  const kcCodes = kcStr
    .split(",")
    .map((code) => code.trim())
    .filter((code) => code.length > 0);
  if (kcCodes.length === 0) return;

  const typeStr = firstQueryValue(route.query.type);
  if (!typeStr) return;
  const intent = typeStr as IntentKind;
  if (!isResourceIntent(intent)) return;

  pushSecondary(intent, {
    text: "",
    kcCodes,
    focusHint: "",
    contextHint: "",
    target: "",
  });
}

// 深链签名（kc|type）：挂载后 query 才就位（如路由异步恢复），或人在工坊内点计划
// 「前往工坊」就地换上下文（组件不重挂）时，靠签名变化补消费；无效签名由 consumeDeepLink 自滤。
const deepLinkSignature = computed(
  () =>
    `${firstQueryValue(route.query.kc) ?? ""}|${firstQueryValue(route.query.type) ?? ""}`,
);
watch(deepLinkSignature, () => {
  consumeDeepLink();
});

// ============ 角色化：欢迎屏快捷语 / 轻反馈 ============
function onWelcomeQuick(text: string): void {
  void onSubmit({
    text,
    manualKcCodes: [],
    contextHint: "",
    forcedIntent: null,
    attachments: [],
  });
}

onMounted(async () => {
  consumeDeepLink();
  try {
    const [chapters, kcs] = await Promise.all([
      listChapters(),
      listKnowledgeComponents(),
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
  <div class="chat" data-testid="workshop-chat">
    <div class="chat__topbar">
      <span class="chat__topbar-title">✦ AI 答疑</span>
      <button
        type="button"
        class="chat__newchat"
        data-testid="workshop-new-chat"
        :disabled="busy"
        :title="
          busy
            ? '当前回复正在生成或保存，稍等一下再开新对话'
            : '开一段新对话（之前的会进「聊天记录」）'
        "
        @click="newChat"
      >
        ＋ 新对话
      </button>
    </div>

    <!-- 考核模式驻顶条（ASKME）：第 N/3 轮 + 退出。 -->
    <QuizModeBar
      v-if="quiz"
      class="chat__quizbar"
      data-testid="quiz-mode-bar"
      :round="quiz.round"
      :total="quiz.total"
      :kc-label="quizKcLabel"
      @exit="exitQuiz"
    />

    <div ref="scrollRef" class="chat__scroll" @scroll.passive="onScroll">
      <!-- 续聊提示：回看历史会话后，明确告诉学生「接着问会并入这段会话」。 -->
      <div
        v-if="continuing && messages.length > 0"
        class="chat__continue"
        data-testid="workshop-continue-hint"
      >
        <span class="chat__continue-icon" aria-hidden="true">↩</span>
        <span class="chat__continue-text"
          >正在继续这段会话，新的提问会并入同一条「聊天记录」</span
        >
        <button
          type="button"
          class="chat__continue-new"
          title="改为开一段全新对话"
          @click="newChat"
        >
          开新对话
        </button>
      </div>

      <!-- 欢迎屏：百晓立绘 + 快捷提问 chips + 能力一览（点快捷语即发送）。 -->
      <WelcomePanel
        v-if="messages.length === 0"
        data-testid="workshop-welcome"
        @quick="onWelcomeQuick"
      />

      <template v-for="m in messages" :key="m.id">
        <!-- 用户气泡（附件 pill 渲染在气泡上方） -->
        <template v-if="m.role === 'user'">
          <div
            v-if="m.attachments && m.attachments.length"
            class="user-attachments"
            data-testid="user-attachments"
          >
            <span
              v-for="a in m.attachments"
              :key="a.id"
              class="user-attach"
              :title="a.filename"
            >
              <span class="user-attach__icon" aria-hidden="true">{{
                attachmentIcon(a.ext)
              }}</span>
              <span class="user-attach__name">{{ a.filename }}</span>
              <span class="user-attach__meta">{{ a.charCount }} 字</span>
            </span>
          </div>
          <div
            v-if="m.imageCount"
            class="user-attachments"
            data-testid="user-images"
          >
            <span class="user-attach">
              <span class="user-attach__icon" aria-hidden="true">🖼️</span>
              <span class="user-attach__name">{{ m.imageCount }} 张图片</span>
            </span>
          </div>
          <div class="msg msg--user">
            <div
              v-if="m.toolLabel || (m.kcCodes && m.kcCodes.length)"
              class="msg__tags"
            >
              <span v-if="m.toolLabel" class="msg__tool"
                >{{ m.toolIcon }} {{ m.toolLabel }}</span
              >
              <span v-if="m.kcCodes && m.kcCodes.length" class="msg__kc"
                >@{{ m.kcCodes.length }} 知识点</span
              >
            </div>
            <p class="msg__text">
              {{ m.text }}
            </p>
          </div>
        </template>

        <!-- 助手消息行：左侧百晓像素头像 + 右侧气泡 -->
        <div v-else class="msg-row" :data-kind="m.kind">
          <div class="msg-row__avatar">
            <WorkshopAvatar :streaming="m.streaming === true" />
          </div>
          <div class="msg-row__body">
            <!-- 助手：答疑（ASK / ASKME / EXPLAIN_CODE / FIX_BUG 共用） -->
            <div v-if="m.kind === 'answer'" class="msg msg--assistant">
              <div
                v-if="m.personalizationNote"
                class="msg__pnote"
                :title="m.personalizationNote"
              >
                ✨ 为你定制
              </div>
              <div v-if="m.toolLabel" class="msg__tags">
                <span class="msg__tool"
                  >{{ m.toolIcon }} {{ m.toolLabel }}</span
                >
              </div>
              <div
                v-if="m.groundingStatus || (m.review && !m.streaming)"
                class="msg__grounding"
              >
                <span
                  v-if="m.groundingStatus"
                  class="g-chip"
                  :class="groundingInfo(m.groundingStatus).cls"
                  >{{ groundingInfo(m.groundingStatus).text }}</span
                >
                <!-- 三审徽章：仅 live 答疑（带 review）显示；历史回放无 review → 不显示。 -->
                <template v-if="m.review && !m.streaming">
                  <span
                    v-for="b in reviewBadges(m.review)"
                    :key="b.key"
                    class="r-badge"
                    :class="b.passed ? 'r-badge--ok' : 'r-badge--no'"
                    :title="b.title"
                    >{{ b.passed ? "✓" : "✗" }} {{ b.label }}</span
                  >
                </template>
                <!-- 折叠「⚙ 工具调用」轨迹（渐进披露），挂在三审徽章行尾部。 -->
                <ToolTraceLine
                  v-if="!m.streaming && m.traces && m.traces.length"
                  class="msg__traceline"
                  :traces="m.traces"
                />
              </div>
              <MarkdownView
                :source="annotateCitations(m.text, m.evidence ?? [])"
                @cite-click="(p) => goToPage(p.pageId)"
              />
              <!-- 考考我·填空作答（多轮）：题干见上方，每个空一个输入框，提交即评卷并出下一轮。 -->
              <div
                v-if="m.quiz && !m.streaming"
                class="quiz-fill"
                data-testid="quiz-fill"
              >
                <div
                  v-for="(b, i) in m.quiz.blanks"
                  :key="b.index"
                  class="quiz-fill__row"
                >
                  <label class="quiz-fill__label">({{ b.index }})</label>
                  <input
                    class="quiz-fill__input"
                    type="text"
                    :placeholder="b.hint || '在此填空'"
                    :value="quizDrafts[m.id]?.[i] || ''"
                    :disabled="!quiz || busy"
                    @input="
                      setQuizDraft(
                        m.id,
                        i,
                        ($event.target as HTMLInputElement).value,
                      )
                    "
                    @keydown.enter.prevent="submitQuizFill(m)"
                  />
                </div>
                <button
                  type="button"
                  class="quiz-fill__submit"
                  :disabled="!quiz || busy"
                  @click="submitQuizFill(m)"
                >
                  提交作答
                </button>
              </div>
              <span v-if="m.streaming" class="cursor">▌</span>
              <!-- 流式期间：工具调用生命周期实时时间轴（证据检索 → 作答 → 三审）。 -->
              <ToolCallTimeline
                v-if="m.streaming"
                :traces="liveAskTraces"
                live
              />
              <div v-if="m.streaming" class="msg__stoprow">
                <button
                  type="button"
                  class="stop-chip"
                  data-testid="stop-stream"
                  @click="onStopStream(m)"
                >
                  ⏹ 停止
                </button>
              </div>
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
              <div v-if="!m.streaming && m.suggestTopic" class="msg__tools">
                <div class="msg__tool-actions">
                  <span class="msg__tool-hint">接下来</span>
                  <button
                    type="button"
                    class="tool-chip"
                    @click="onAnswerVideo(m)"
                  >
                    ▶ 看相关视频
                  </button>
                  <!-- R-14 讲义解耦：去掉「出练习」入口，讲义不再直接挂练习；练习走训练场。 -->
                  <template v-if="m.kcCodes && m.kcCodes.length">
                    <button
                      type="button"
                      class="tool-chip"
                      @click="onAnswerGenerate(m, 'HANDOUT')"
                    >
                      ✎ 生成讲义
                    </button>
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
              <!-- 换讲法闭环：图解 / 逻辑链 / 示例上手 → 带 style 重问 + styleEngaged 回流。 -->
              <div
                v-if="!m.streaming && m.intent === 'ASK' && m.suggestTopic"
                class="msg__styles"
                data-testid="style-chips"
              >
                <span class="msg__tool-hint">换个讲法</span>
                <button
                  v-for="c in STYLE_CHIPS"
                  :key="c.style"
                  type="button"
                  class="tool-chip"
                  :disabled="busy"
                  @click="onStyleChip(m, c.style)"
                >
                  {{ c.label }}
                </button>
              </div>
              <AnswerFeedback
                v-if="!m.streaming"
                class="msg__feedback"
                :message-id="m.id"
                @feedback="(up) => onAnswerFeedback(m, up)"
              />
            </div>

            <!-- 助手：资源卡（就地内联浏览全文 + 「我的书房」历史库入口） -->
            <div
              v-else-if="m.kind === 'resource'"
              class="msg msg--assistant msg--resource"
              data-testid="resource-card"
            >
              <div class="resource-head">
                <span class="resource-head__icon">{{ m.toolIcon }}</span>
                <button
                  type="button"
                  class="resource-head__body"
                  :title="m.resourceExpanded ? '收起全文' : '展开全文'"
                  @click="toggleResource(m)"
                >
                  <span class="resource-head__title">{{
                    m.resourceTitle
                  }}</span>
                  <span class="resource-head__meta">
                    {{ m.toolLabel }} ·
                    {{ groundingInfo(m.resourceGrounding ?? "").text }}
                    <template v-if="m.resourceSafety">
                      · 安全{{
                        m.resourceSafety === "PASSED" ? "通过" : "注意"
                      }}
                    </template>
                  </span>
                </button>
                <span class="resource-head__actions">
                  <button
                    type="button"
                    class="resource-head__toggle"
                    data-testid="resource-toggle"
                    @click="toggleResource(m)"
                  >
                    {{ m.resourceExpanded ? "收起 ▲" : "展开 ▼" }}
                  </button>
                  <button
                    type="button"
                    class="resource-head__open"
                    title="在「我的书房」打开（独立大图浏览）"
                    @click="openResource(m.resourceId as number)"
                  >
                    书房 →
                  </button>
                </span>
              </div>

              <div
                v-if="m.resourceExpanded"
                class="resource-body"
                data-testid="resource-inline"
              >
                <p v-if="m.resourceSummary" class="resource-body__summary">
                  {{ m.resourceSummary }}
                </p>
                <p v-if="m.resourceLoading" class="resource-body__loading">
                  正在载入资源全文…
                </p>
                <template v-else-if="m.resourceContent && m.resourceType">
                  <HandoutView
                    v-if="m.resourceType === 'HANDOUT'"
                    :content="m.resourceContent as HandoutContent"
                  />
                  <MindMapView
                    v-else-if="m.resourceType === 'MINDMAP'"
                    :content="m.resourceContent as MindMapContent"
                  />
                  <PracticeList
                    v-else-if="m.resourceType === 'PRACTICE'"
                    :content="m.resourceContent as PracticeContent"
                  />
                  <CodeLabView
                    v-else-if="m.resourceType === 'CODE_LAB'"
                    :content="m.resourceContent as CodeLabContent"
                  />
                  <ReadingList
                    v-else-if="m.resourceType === 'READING'"
                    :content="m.resourceContent as ReadingContent"
                  />
                  <StoryboardView
                    v-else-if="m.resourceType === 'STORYBOARD'"
                    :content="m.resourceContent as StoryboardContent"
                  />
                </template>
                <p v-else class="resource-body__empty">
                  暂无法在此内联展示，请点「书房 →」查看。
                </p>
              </div>
            </div>

            <!-- 助手：B站视频卡片（外部资源，按意图检索，不走生成） -->
            <div
              v-else-if="m.kind === 'video'"
              class="msg msg--assistant msg--video"
            >
              <div v-if="m.toolLabel" class="msg__tags">
                <span class="msg__tool"
                  >{{ m.toolIcon }} {{ m.toolLabel }}</span
                >
              </div>
              <BilibiliVideoCards
                :keyword="m.videoKeyword ?? ''"
                heading="B站精选讲解"
              />
            </div>

            <!-- 助手：联网查证（BROWSE，外部内容，非课件） -->
            <div
              v-else-if="m.kind === 'browse'"
              class="msg msg--assistant msg--browse"
              data-testid="browse-msg"
            >
              <div v-if="m.toolLabel" class="msg__tags">
                <span class="msg__tool"
                  >{{ m.toolIcon }} {{ m.toolLabel }}</span
                >
              </div>
              <p
                v-if="m.text && (m.streaming || !m.browseRun)"
                class="msg__text"
              >
                {{ m.text }}
                <span v-if="m.streaming" class="cursor">▌</span>
              </p>
              <BrowseTimeline :run="m.browseRun ?? null" />
            </div>

            <!-- 助手：学习计划卡（PLAN，orchestrator 产出 看/学/练/测 步骤） -->
            <div
              v-else-if="m.kind === 'plan'"
              class="msg msg--assistant msg--plan"
              data-testid="plan-card-msg"
              :data-loop-run-id="m.loopRunId ?? undefined"
              :data-streaming="String(Boolean(m.streaming))"
            >
              <div v-if="m.toolLabel" class="msg__tags">
                <span class="msg__tool"
                  >{{ m.toolIcon }} {{ m.toolLabel }}</span
                >
              </div>
              <p
                v-if="m.personalizationSummary"
                class="plan-personalization"
                data-testid="plan-personalization"
              >
                ✨ 个性化策略：{{ m.personalizationSummary }}
              </p>
              <p
                v-if="m.searchSummary"
                class="plan-personalization"
                data-testid="plan-search"
              >
                🔀 {{ m.searchSummary }}
              </p>
              <MarkdownView v-if="m.text" :source="m.text" />
              <PlanCard :steps="m.planSteps ?? []" @open-step="onPlanOpen" />
            </div>

            <!-- 助手：学情卡（PROGRESS，纯前端读 masterySnapshot） -->
            <div
              v-else-if="m.kind === 'progress'"
              class="msg msg--assistant msg--progress"
              data-testid="progress-card-msg"
            >
              <div v-if="m.toolLabel" class="msg__tags">
                <span class="msg__tool"
                  >{{ m.toolIcon }} {{ m.toolLabel }}</span
                >
              </div>
              <ProgressCard
                :snapshot="learning.field.masterySnapshot"
                @practice="onPracticeKc"
              />
            </div>

            <!-- 助手：书房检索结果（MY_STUDY，≤5 张资源卡，复用资源卡头样式） -->
            <div
              v-else-if="m.kind === 'mystudy'"
              class="msg msg--assistant msg--mystudy"
              data-testid="mystudy-msg"
            >
              <div v-if="m.toolLabel" class="msg__tags">
                <span class="msg__tool"
                  >{{ m.toolIcon }} {{ m.toolLabel }}</span
                >
              </div>
              <p v-if="m.text" class="msg__text">
                {{ m.text }}
              </p>
              <div
                v-for="item in m.mystudyItems ?? []"
                :key="item.id"
                class="resource-head mystudy-item"
                data-testid="mystudy-item"
              >
                <span class="resource-head__icon">{{
                  commandOf(item.type).icon
                }}</span>
                <button
                  type="button"
                  class="resource-head__body"
                  title="在「我的书房」打开"
                  @click="openResource(item.id)"
                >
                  <span class="resource-head__title">{{ item.title }}</span>
                  <span class="resource-head__meta">
                    {{ commandOf(item.type).label }} ·
                    {{ groundingInfo(item.groundingStatus).text }}
                  </span>
                </button>
                <span class="resource-head__actions">
                  <button
                    type="button"
                    class="resource-head__open"
                    @click="openResource(item.id)"
                  >
                    打开 →
                  </button>
                </span>
              </div>
            </div>

            <!-- 助手：带路确认（NAVIGATE，纯前端路由跳转） -->
            <div
              v-else-if="m.kind === 'navigate'"
              class="msg msg--assistant msg--navigate"
              data-testid="navigate-msg"
            >
              <div v-if="m.toolLabel" class="msg__tags">
                <span class="msg__tool"
                  >{{ m.toolIcon }} {{ m.toolLabel }}</span
                >
              </div>
              <p class="msg__text">
                {{ m.text }}
              </p>
              <button
                type="button"
                class="tool-chip tool-chip--go"
                data-testid="navigate-go"
                @click="onNavigateGo(m)"
              >
                ➤ 前往{{ m.navLabel }}
              </button>
            </div>

            <!-- 助手：低置信澄清气泡（点选项即按该工具执行原话） -->
            <div
              v-else-if="m.kind === 'clarify'"
              class="msg msg--assistant msg--clarify"
              data-testid="clarify-msg"
            >
              <ClarifyBubble
                :options="m.clarifyOptions ?? []"
                :reason="m.clarifyReason ?? ''"
                @pick="onClarifyPick(m, $event)"
              />
            </div>

            <!-- 助手：组合意图「接着：xx →」chip -->
            <div
              v-else-if="m.kind === 'secondary'"
              class="msg--secondary"
              data-testid="secondary-chip"
            >
              <button
                v-if="!m.secondaryUsed"
                type="button"
                class="tool-chip tool-chip--secondary"
                :disabled="busy"
                data-testid="secondary-go"
                @click="onSecondaryPick(m)"
              >
                接着：{{ commandOf(m.secondaryIntent ?? "ASK").label }} →
              </button>
              <p v-else class="msg__text msg__text--dim">
                已接着执行：{{ commandOf(m.secondaryIntent ?? "ASK").label }}
              </p>
            </div>

            <!-- 助手：错误（带「↻ 重试」参数重放） -->
            <div
              v-else-if="m.kind === 'error'"
              class="msg msg--assistant msg--error"
            >
              <p class="msg__text">
                {{ m.text }}
              </p>
              <button
                v-if="m.retry"
                type="button"
                class="tool-chip tool-chip--retry"
                :disabled="busy"
                data-testid="retry-chip"
                @click="onRetry(m)"
              >
                ↻ 重试
              </button>
            </div>

            <!-- 纠错 chips：中置信执行后挂在该 assistant 消息下（点击改派执行）。 -->
            <div
              v-if="
                !m.streaming &&
                m.correctionOptions &&
                m.correctionOptions.length
              "
              class="msg-followup"
              data-testid="correction-chips"
            >
              <span class="msg__tool-hint">不是想要这个？</span>
              <button
                v-for="opt in m.correctionOptions"
                :key="opt.intent"
                type="button"
                class="tool-chip"
                :disabled="busy"
                @click="onCorrectionPick(m, opt.intent)"
              >
                {{ opt.icon }} {{ opt.label }}
              </button>
            </div>
          </div>
        </div>
      </template>

      <div v-if="routing" class="routing" data-testid="routing-indicator">
        正在判断该用哪个工具…
      </div>

      <!-- 统一处理进度：资源生成 / 学习总管协同都复用与「提问」一致的时间轴。 -->
      <ToolCallTimeline
        v-if="officeStreamState === 'streaming' && !isOrchestratorActive"
        :traces="workshopSse.traces.value"
        :live="true"
      />
      <ToolCallTimeline
        v-if="isOrchestratorActive"
        :traces="activity.traces"
        :live="true"
      />
    </div>

    <div class="chat__composer">
      <ChatComposer
        ref="composerRef"
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
  font-family:
    ui-sans-serif,
    system-ui,
    -apple-system,
    BlinkMacSystemFont,
    "PingFang SC",
    "Microsoft YaHei",
    sans-serif;
  color: #3f2e16;
}
.chat__topbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  /* 与抽屉表头 .workshop-drawer__bar 统一为同一固定高度（box-sizing 含内边距与下边框），
     使各面板顶部的虚线分隔线落在同一水平线上对齐（WorkshopPage 同步）。 */
  min-height: 44px;
  box-sizing: border-box;
  padding: 8px 14px;
  border-bottom: 1px solid #dcc28b;
  background: #fff8e9;
}
.chat__topbar-title {
  font-size: 13px;
  font-weight: 650;
  color: #493416;
  letter-spacing: 0;
}
.chat__newchat {
  font: inherit;
  font-size: 12px;
  padding: 5px 10px;
  background: #fffaf0;
  border: 1px solid #d4b875;
  border-radius: 8px;
  color: #654f2d;
  cursor: pointer;
}
.chat__newchat:hover {
  background: #f4e4bc;
  border-color: #c89c4c;
}
.chat__newchat:disabled {
  opacity: 0.55;
  cursor: wait;
  transform: none;
}
.chat__quizbar {
  flex: 0 0 auto;
}
.chat__scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 22px;
  background: #fffdf7;
  scrollbar-width: thin;
}
.chat__composer {
  flex: 0 0 auto;
  padding: 12px 18px 14px;
  border-top: 1px solid #dcc28b;
  background: #fff8e9;
}

/* 续聊提示条 */
.chat__continue {
  flex: 0 0 auto;
  align-self: stretch;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  font-size: 12px;
  color: #654a20;
  background: #f8ebca;
  border: 1px solid #d8b86e;
  border-radius: 8px;
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
  font: inherit;
  font-size: 12px;
  padding: 4px 8px;
  background: #fffaf0;
  border: 1px solid #cba45a;
  border-radius: 8px;
  color: #654a20;
  cursor: pointer;
}
.chat__continue-new:hover {
  background: #fff;
}

/* 助手消息行：左侧头像 + 右侧气泡列 */
.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  align-self: flex-start;
  width: 100%;
  max-width: 92%;
}
.msg-row__avatar {
  flex: 0 0 auto;
}
.msg-row__body {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.msg-row__body > .msg {
  align-self: flex-start;
  max-width: 100%;
}

/* 消息气泡 */
.msg {
  padding: 10px 13px;
  border-radius: 8px;
  border: 1px solid #dcc28b;
  max-width: 90%;
  box-shadow: none;
}
.msg--user {
  background: #f3dfad;
  border-color: #c89c4c;
  color: #493416;
  align-self: flex-end;
  max-width: 76%;
}
/* 用户气泡上方的附件 pill 行（右对齐贴合气泡）。 */
.user-attachments {
  align-self: flex-end;
  max-width: 76%;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px;
}
.user-attach {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 3px 8px;
  font-size: 11px;
  border-radius: 8px;
  background: #fff8e9;
  border: 1px solid #d4b875;
  color: #493416;
}
.user-attach__name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}
.user-attach__meta {
  color: #78684e;
}
.msg--assistant {
  background: #fffdf7;
  color: #3f2e16;
  align-self: flex-start;
}
.msg--error {
  background: #fff1ef;
  border-color: #e2ada5;
  color: #9e3528;
}
/* 视频卡片气泡与答疑气泡同宽（同为撑满行 + 左对齐），避免比上方回答更宽显得突兀。 */
.msg--video {
  align-self: flex-start;
  width: 100%;
  background: #fffdf7;
}
.msg--browse {
  align-self: flex-start;
  width: 100%;
  background: #fffdf7;
}
/* 计划 / 学情 / 书房检索卡：撑满行，像素卡片底色。 */
.msg--plan,
.msg--progress,
.msg--mystudy {
  width: 100%;
  background: #fffaf0;
}
.plan-personalization {
  margin: 4px 0 8px;
  padding: 6px 8px;
  color: #654a20;
  background: #f8ebca;
  border-left: 3px solid #d8b86e;
  font-size: 12px;
  line-height: 1.5;
}
.msg--navigate {
  background: #fffdf7;
}
.msg--clarify {
  background: #fffaf0;
  border-style: solid;
}
/* 「接着：xx →」chip：无气泡边框，仅一枚胶囊按钮。 */
.msg--secondary {
  align-self: flex-start;
}
.msg__text--dim {
  color: #78684e;
  font-size: 12px;
}
/* 答案内嵌的工具入口 + 相关 B站讲解。 */
.msg__tools {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px solid #dcc28b;
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
  color: #78684e;
  margin-right: 2px;
}
.msg__tool-videos {
  margin-top: 2px;
}
/* 换讲法 chips 行（图解 / 逻辑链 / 示例上手）。 */
.msg__styles {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  border-top: 1px solid #dcc28b;
  padding-top: 8px;
}
/* 纠错 chips：贴在气泡正下方的无框小行。 */
.msg-followup {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding-left: 4px;
}
.tool-chip {
  font: inherit;
  font-size: 12px;
  padding: 5px 10px;
  background: #fffaf0;
  border: 1px solid #d4b875;
  border-radius: 8px;
  color: #493416;
  cursor: pointer;
}
.tool-chip:hover {
  background: #f4e4bc;
  border-color: #c89c4c;
}
.tool-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.tool-chip--secondary {
  font-size: 12px;
  padding: 6px 12px;
  background: #f8ebca;
  border-color: #cba45a;
}
.tool-chip--go {
  margin-top: 6px;
  font-weight: 700;
}
.tool-chip--retry {
  margin-top: 6px;
  border-color: #e2ada5;
  color: #9e3528;
  background: #fff7f6;
}
/* 「⏹ 停止」：流式中可中断。 */
.msg__stoprow {
  margin-top: 6px;
}
.stop-chip {
  font: inherit;
  font-size: 12px;
  padding: 5px 10px;
  background: #fffaf0;
  border: 1px solid #e2ada5;
  border-radius: 8px;
  color: #9e3528;
  cursor: pointer;
}
.stop-chip:hover {
  background: #fff1ef;
}
/* 「✨ 为你定制」画像个性化小标签。 */
.msg__pnote {
  display: inline-block;
  margin-bottom: 4px;
  padding: 1px 8px;
  font-size: 10px;
  font-weight: 700;
  color: #654a20;
  background: #f8ebca;
  border: 1px solid #d8b86e;
  border-radius: 8px;
  cursor: help;
}
.msg__tags {
  display: flex;
  gap: 6px;
  margin-bottom: 4px;
}
.msg__tool {
  font-size: 10px;
  font-weight: 700;
  color: #654a20;
  background: #fff7e4;
  border: 1px solid #d4b875;
  border-radius: 8px;
  padding: 1px 8px;
}
.msg__kc {
  font-size: 10px;
  font-weight: 700;
  color: #654a20;
  background: #f8ebca;
  border: 1px solid #d8b86e;
  border-radius: 8px;
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
/* 工具调用轨迹折叠行挂在徽章行尾部。 */
.msg__traceline {
  margin-left: auto;
}
.g-chip {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 700;
}
.g--passed {
  background: #edf5ef;
  color: #36513f;
  border: 1px solid #b7c9bb;
}
.g--general {
  background: #fff7ea;
  color: #6a4b22;
  border: 1px solid #e0c48f;
}
.g--failed {
  background: #fff1ef;
  color: #9e3528;
  border: 1px solid #e2ada5;
}
/* 三审徽章：审查官 / 校对官 / 安全员，hover(title) 看具体理由。 */
.r-badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px 7px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 700;
  border: 1px solid #dcc28b;
  white-space: nowrap;
  cursor: help;
}
.r-badge--ok {
  background: #edf5ef;
  color: #36513f;
  border-color: #b7c9bb;
}
.r-badge--no {
  background: #fff1ef;
  color: #9e3528;
  border-color: #e2ada5;
}
/* 考考我·填空作答 */
.quiz-fill {
  display: grid;
  gap: 6px;
  margin-top: 8px;
  padding: 10px;
  background: #fff8e9;
  border: 1px solid #d4b875;
  border-radius: 8px;
}
.quiz-fill__row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.quiz-fill__label {
  flex: 0 0 auto;
  font-weight: 700;
  color: #654a20;
  font-size: 13px;
}
.quiz-fill__input {
  flex: 1 1 auto;
  min-width: 0;
  padding: 5px 8px;
  font: inherit;
  font-size: 13px;
  border: 1px solid #d4b875;
  border-radius: 8px;
  background: #fffdf7;
  color: #3f2e16;
}
.quiz-fill__input:focus {
  outline: none;
  border-color: #c89c4c;
  box-shadow: 0 0 0 2px rgba(200, 156, 76, 0.16);
}
.quiz-fill__submit {
  justify-self: start;
  margin-top: 2px;
  padding: 6px 12px;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  background: #8a6423;
  color: #fff;
  border: 1px solid #654a20;
  border-radius: 8px;
}
.quiz-fill__submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.cursor {
  animation: blink 0.8s steps(2) infinite;
  color: #8a6423;
  font-weight: 700;
}
@keyframes blink {
  0% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

/* 资源卡：就地内联浏览（生成完直接展开全文；可折叠；「书房 →」仍作历史库大图入口） */
.msg--resource {
  align-self: flex-start;
  width: 100%;
  background: #fffaf0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.resource-head {
  display: grid;
  grid-template-columns: 28px 1fr auto;
  gap: 8px;
  align-items: center;
}
.resource-head__icon {
  font-size: 20px;
  text-align: center;
  color: #8a6423;
}
.resource-head__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  text-align: left;
  font: inherit;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  color: inherit;
}
.resource-head__title {
  font-weight: 700;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.resource-head__meta {
  font-size: 11px;
  color: #78684e;
}
.resource-head__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.resource-head__toggle,
.resource-head__open {
  font: inherit;
  font-size: 12px;
  padding: 5px 8px;
  border-radius: 8px;
  cursor: pointer;
}
.resource-head__toggle {
  background: #fffaf0;
  border: 1px solid #d4b875;
  color: #493416;
}
.resource-head__toggle:hover {
  background: #f4e4bc;
}
.resource-head__open {
  background: #f3dfad;
  border: 1px solid #c89c4c;
  color: #493416;
  font-weight: 700;
}
.resource-head__open:hover {
  background: #fff;
}
.resource-body {
  border-top: 1px solid #dcc28b;
  padding-top: 8px;
}
.resource-body__summary {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.55;
  color: #493416;
}
.resource-body__loading,
.resource-body__empty {
  margin: 0;
  font-size: 12px;
  color: #78684e;
}
/* MY_STUDY 命中资源行（复用资源卡头样式）。 */
.mystudy-item {
  border-top: 1px solid #dcc28b;
  padding-top: 6px;
  margin-top: 6px;
}

/* 意图分流提示 */
.routing {
  align-self: flex-start;
  font-size: 12px;
  color: #78684e;
  padding: 6px 12px;
  background: #fff8e9;
  border: 1px solid #dcc28b;
  border-radius: 8px;
}
</style>
