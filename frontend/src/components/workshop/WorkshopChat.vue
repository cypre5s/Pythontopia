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
import { playSfx } from '@/composables/useAudio';
import { useSseStream } from '@/composables/useSseStream';
import ChatComposer, {
  type ComposerSubmitPayload,
} from '@/components/workshop/ChatComposer.vue';
import AgentOfficeScene from '@/components/workshop/AgentOfficeScene.vue';
import type { OfficeEvidence } from '@/components/workshop/agentOffice';
import MarkdownView from '@/components/common/MarkdownView.vue';
import TutorVisualAids, { type TutorVisualAid } from '@/components/tutor/TutorVisualAids.vue';
import BilibiliVideoCards from '@/components/video/BilibiliVideoCards.vue';
import { kcDisplayName } from '@/data/kcNames';
import { listChapters, listKnowledgeComponents } from '@/api/course';
import { getWorkshopResource } from '@/api/workshop';
import {
  buildMentionItems,
  type MentionItem,
} from '@/composables/useCoursewareMention';
import { SLASH_COMMANDS } from '@/composables/useSlashCommand';
import { routeIntent, type CourseKcRef, type IntentKind } from '@/api/intent';
import type {
  WorkshopContent,
  WorkshopCreateResponse,
  WorkshopResourceType,
} from '@/api/dto/Workshop';

interface TutorStreamResult {
  sessionId: number;
  question: string;
  answerMd: string;
  evidencePageIds: number[];
  groundingStatus: string;
  visual_aids?: TutorVisualAid[];
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
  /** kind==='video'：B站卡片检索关键词。 */
  videoKeyword?: string;
  groundingStatus?: string;
  evidencePageIds?: number[];
  visualAids?: TutorVisualAid[];
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

const messages = ref<ChatMessage[]>([]);
const mentionItems = ref<MentionItem[]>([]);
const courseKcs = ref<CourseKcRef[]>([]);
const routing = ref(false);
const scrollRef = ref<HTMLElement | null>(null);

const tutorSse = useSseStream<TutorStreamResult>('/api/tutor/ask');
const workshopSse = useSseStream<WorkshopCreateResponse>('/api/workshop/resources/stream');

const streamingAskId = ref<string | null>(null);

// 资源生成「智能体事务所」覆盖层状态（沿用原工坊生成流程）。
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
    /* ignore */
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
  } else {
    await runResource(intent, kcCodes, focusHint || payload.text);
  }
}

/**
 * VIDEO 意图：不走生成/SSE，直接出一条「视频卡片」气泡，由 BilibiliVideoCards 按关键词
 * 现取现渲染（外部资源）。关键词 = Python + KC 显示名 + 学习重点（去重去空、限长）。
 */
function runVideo(kcCodes: string[], focus: string): void {
  const meta = commandOf('VIDEO');
  const kcPart = kcCodes.map((c) => kcDisplayName(c)).filter(Boolean).join(' ');
  const keyword = `Python ${kcPart} ${focus}`.replace(/\s+/g, ' ').trim().slice(0, 60);
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

  await tutorSse.start({ question, kcCodes, context });

  streamingAskId.value = null;
  if (tutorSse.status.value === 'done' && tutorSse.result.value) {
    playSfx('sfx_success');
    const r = tutorSse.result.value;
    updateMessage(id, {
      text: r.answerMd || tutorSse.tokens.value,
      groundingStatus: r.groundingStatus,
      evidencePageIds: r.evidencePageIds,
      visualAids: r.visual_aids ?? [],
      streaming: false,
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
  const meta = commandOf(type);
  if (kcCodes.length === 0) {
    // 资源类工具需要 KC：没锚定到就提示先 @ 一个知识点。
    pushMessage({
      id: genId(),
      role: 'assistant',
      kind: 'error',
      text: `生成「${meta.label}」需要先选中知识点：用 @ 选一个，或把主题说得更具体些。`,
    });
    return;
  }

  officeStreamState.value = 'streaming';
  officeTokenPreview.value = '';
  officeEvidence.value = null;

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
  }
}

async function onOfficeConfirm(): Promise<void> {
  const pending = pendingResource.value;
  const evidence = officeEvidence.value;
  officeEvidence.value = null;
  officeStreamState.value = 'idle';
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
        /* mainQuest store unavailable */
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
    <div
      ref="scrollRef"
      class="chat__scroll"
    >
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
            v-if="m.groundingStatus"
            class="msg__grounding"
          >
            <span
              class="g-chip"
              :class="groundingInfo(m.groundingStatus).cls"
            >{{ groundingInfo(m.groundingStatus).text }}</span>
            <span
              v-if="m.evidencePageIds && m.evidencePageIds.length"
              class="evidence"
            >
              <button
                v-for="pid in m.evidencePageIds"
                :key="pid"
                type="button"
                class="evidence__badge"
                :title="`跳转到课件第 ${pid} 页`"
                @click="goToPage(pid)"
              >p.{{ pid }}</button>
            </span>
          </div>
          <MarkdownView :source="m.text" />
          <span
            v-if="m.streaming"
            class="cursor"
          >▌</span>
          <TutorVisualAids
            v-if="!m.streaming && m.visualAids && m.visualAids.length"
            :aids="m.visualAids"
          />
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

/* welcome */
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

/* messages */
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
.msg--video {
  align-self: stretch;
  max-width: 100%;
  width: 100%;
  background: #f5ecd4;
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

/* answer grounding */
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
.evidence {
  display: inline-flex;
  gap: 4px;
  flex-wrap: wrap;
}
.evidence__badge {
  font-family: monospace, sans-serif;
  font-size: 11px;
  padding: 2px 6px;
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-info);
  color: #1b4a6b;
  border-radius: 4px;
  cursor: pointer;
}
.evidence__badge:hover {
  background: #a8cfe6;
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

/* resource card */
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

/* routing */
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
