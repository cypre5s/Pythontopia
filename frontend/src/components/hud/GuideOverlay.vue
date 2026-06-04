<script setup lang="ts">
/**
 * 伴学向导 overlay（P1-a / P1-d）。
 *
 * - 常驻入口（HudShell Dock 的 ✧ 向导按钮 / G 键）唤起。
 * - 多轮对话线程：气泡历史 + 流式 Markdown + 计划（看/学/练/测）+ 澄清追问 +
 *   多智能体协同摘要 + 对话抽画像提示。
 * - 计划每步可点「前往」直达对应学习面板（复用 router）。
 * - 始终挂载（v-show 思路用 class 控制），保证长任务流式不被关闭打断。
 *
 * 数据源：POST /api/orchestrator/turn/stream（Java 代理 → Python 学习总管），
 * SSE 事件 trace/token/result/error 由 useSseStream 解析。
 */
import { computed, nextTick, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useGuideStore } from '@stores/guide';
import { useHudStore } from '@stores/hud';
import { useMainQuestStore } from '@stores/mainQuest';
import { useLearningStore } from '@stores/learning';
import { useSseStream } from '@/composables/useSseStream';
import { playUiClick, playUiHover } from '@/composables/useStardewFx';
import MarkdownView from '@/components/common/MarkdownView.vue';
import type {
  OrchestratorPhase,
  OrchestratorResult,
  OrchestratorTurnRequest,
} from '@/api/orchestrator';
import type { LearnerProfileDto } from '@/api/dto/LearnerProfileDto';

const guide = useGuideStore();
const hud = useHudStore();
const mainQuest = useMainQuestStore();
const learning = useLearningStore();
const router = useRouter();

const sse = useSseStream<OrchestratorResult>('/api/orchestrator/turn/stream');

const visible = computed(() => guide.open);
const inputText = ref('');
const threadEl = ref<HTMLElement | null>(null);
const currentAssistantId = ref<string | null>(null);

const PHASE_LABEL: Record<OrchestratorPhase, string> = {
  watch: '看',
  learn: '学',
  practice: '练',
  assess: '测',
};

const PANEL_LABEL: Record<string, string> = {
  lecture: '去上课',
  library: '去书房',
  training: '去训练场',
  workshop: '去工坊',
  tutor: '找 AI 助教',
  problems: '去题库',
  examples: '看例题',
  review: '去复盘厅',
};

function panelRoute(panel: string, chapter: number): string | null {
  switch (panel) {
    case 'lecture':
      return `/lecture/${chapter}`;
    case 'library':
      return '/library';
    case 'training':
      return '/training';
    case 'workshop':
      return '/workshop';
    case 'tutor':
      return '/tutor';
    case 'problems':
      return '/problems';
    case 'examples':
      return '/examples';
    case 'review':
      return '/review-hall';
    default:
      return null;
  }
}

function buildProfileBrief(profile: LearnerProfileDto | null): string {
  if (!profile) return '';
  if (profile.narrativeSummary && profile.narrativeSummary.trim().length > 0) {
    return profile.narrativeSummary.trim();
  }
  const parts: string[] = [];
  if (profile.major) parts.push(`专业：${profile.major}`);
  if (profile.baseLevel) parts.push(`Python 自评 ${profile.baseLevel.pythonSelfRatedLevel}/5`);
  if (profile.learningGoal?.kind) parts.push(`目标：${profile.learningGoal.kind}`);
  if (profile.learningPreference?.mediaPriority?.length) {
    parts.push(`偏好：${profile.learningPreference.mediaPriority.join('/')}`);
  }
  return parts.join('；');
}

const suggestions = computed<string[]>(() => {
  const last = guide.lastAssistant;
  if (last?.needsUserInput) {
    return ['从当前章开始', '想先打好概念', '想多做题练手', '偏视频/图解'];
  }
  return ['想看视频讲解', '这步太难了', '换个例子', '下一步做什么？'];
});

async function scrollToBottom(): Promise<void> {
  await nextTick();
  const el = threadEl.value;
  if (el) el.scrollTop = el.scrollHeight;
}

watch(
  () => sse.tokens.value,
  (tok) => {
    if (currentAssistantId.value) {
      guide.updateAssistantStream(currentAssistantId.value, tok, sse.traces.value);
      void scrollToBottom();
    }
  },
);
watch(
  () => sse.traces.value,
  (tr) => {
    if (currentAssistantId.value) {
      guide.updateAssistantStream(currentAssistantId.value, sse.tokens.value, tr);
    }
  },
);

watch(visible, (open) => {
  if (open) {
    guide.ensureGreeting();
    void scrollToBottom();
  }
});

function close(): void {
  playUiClick();
  guide.closePanel();
}

async function send(raw: string): Promise<void> {
  const text = raw.trim();
  if (!text || guide.isStreaming) return;
  playUiClick();

  const chapter = mainQuest.progress.currentChapter;
  const chapterKcs = mainQuest.chapterKcCodes(chapter);
  // 在 push 用户消息之前取历史（不含本轮）。historyForRequest 是 getter（属性访问）。
  const history = guide.historyForRequest;
  guide.pushUserMessage(text);
  inputText.value = '';
  await scrollToBottom();

  const aid = guide.beginAssistant();
  currentAssistantId.value = aid;

  const body: OrchestratorTurnRequest = {
    sessionId: guide.sessionId,
    message: text,
    history,
    currentChapter: chapter,
    recentKcCodes: chapterKcs,
    recentFailures: guide.recentFailures,
    profileBrief: buildProfileBrief(learning.profile),
    courseKcCodes: learning.kcs.length ? learning.kcs : chapterKcs,
  };

  await sse.start(body);

  if (sse.result.value) {
    guide.finalizeAssistant(aid, sse.result.value, sse.tokens.value);
  } else if (sse.status.value === 'error' || sse.error.value) {
    guide.failAssistant(aid, sse.error.value?.message ?? 'AI 助教请求失败');
  } else {
    // 流结束但只收到 token（无结构化 result）：用累计文本兜底定稿。
    guide.finalizeAssistant(
      aid,
      {
        reply_md: sse.tokens.value,
        clarifying_question: null,
        needs_user_input: false,
        plan: [],
        dispatch_results: [],
        profile_updates: [],
        trace: sse.traces.value.map((t) => ({
          node: t.node,
          elapsed_ms: t.elapsed_ms,
          status: t.status,
        })),
        done: true,
      },
      sse.tokens.value,
    );
  }
  currentAssistantId.value = null;
  await scrollToBottom();
}

function onSubmit(): void {
  void send(inputText.value);
}
function onSuggestion(s: string): void {
  void send(s);
}
function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    onSubmit();
  }
}
function goToPanel(panel: string | null): void {
  if (!panel) return;
  const route = panelRoute(panel, mainQuest.progress.currentChapter);
  if (!route) return;
  playUiClick();
  guide.closePanel();
  hud.closeAll();
  void router.push(route);
}
</script>

<template>
  <div
    class="guide-overlay"
    :class="{ 'guide-overlay--open': visible }"
    data-testid="guide-overlay"
    @click.self="close"
  >
    <section
      class="guide-card"
      role="dialog"
      aria-label="AI 助教"
    >
      <header class="guide-head">
        <span class="guide-head__title">✧ AI 助教</span>
        <button
          type="button"
          class="guide-head__close"
          aria-label="关闭（ESC / G）"
          @click="close"
        >
          ✕
        </button>
      </header>

      <div
        ref="threadEl"
        class="guide-thread"
        data-testid="guide-thread"
      >
        <div
          v-for="m in guide.messages"
          :key="m.id"
          class="guide-msg"
          :class="`guide-msg--${m.role}`"
        >
          <div
            class="guide-bubble"
            :class="{ 'guide-bubble--failed': m.failed }"
          >
            <div
              v-if="m.streaming && m.traces && m.traces.length"
              class="guide-trace"
            >
              <span class="guide-trace__dot" />
              智能体协同中 · {{ m.traces[m.traces.length - 1]?.node }}
            </div>

            <MarkdownView
              v-if="m.content"
              :source="m.content"
            />
            <span
              v-else-if="m.streaming"
              class="guide-typing"
            >AI 助教思考中 ▌</span>

            <ol
              v-if="m.plan && m.plan.length"
              class="guide-plan"
              data-testid="guide-plan"
            >
              <li
                v-for="step in m.plan"
                :key="step.id"
                class="guide-plan__step"
                :class="{ 'guide-plan__step--done': step.status === 'done' }"
              >
                <span
                  class="guide-plan__phase"
                  :class="`guide-plan__phase--${step.phase}`"
                >{{ PHASE_LABEL[step.phase] }}</span>
                <span class="guide-plan__body">
                  <span class="guide-plan__title">{{ step.title }}</span>
                  <small class="guide-plan__detail">{{ step.detail }}</small>
                </span>
                <button
                  v-if="step.panel"
                  type="button"
                  class="guide-plan__go"
                  @click="goToPanel(step.panel)"
                  @mouseenter="playUiHover"
                >
                  {{ PANEL_LABEL[step.panel] ?? '前往' }}
                </button>
              </li>
            </ol>

            <p
              v-if="m.clarifyingQuestion"
              class="guide-clarify"
            >
              🧭 {{ m.clarifyingQuestion }}
            </p>

            <div
              v-if="m.dispatchResults && m.dispatchResults.length"
              class="guide-dispatch"
            >
              <span class="guide-dispatch__label">多智能体协同</span>
              <span
                v-for="d in m.dispatchResults"
                :key="d.step_id + d.agent"
                class="guide-dispatch__chip"
                :class="{ 'guide-dispatch__chip--ok': d.ok }"
                :title="d.summary"
              >{{ d.agent }} {{ d.ok ? '✓' : '…' }}</span>
            </div>

            <p
              v-if="m.profileUpdates && m.profileUpdates.length"
              class="guide-profile"
              data-testid="guide-profile-updates"
            >
              已随学更新画像：{{ m.profileUpdates.map((p) => p.dimension).join('、') }}
            </p>
          </div>
        </div>
      </div>

      <div class="guide-suggest">
        <button
          v-for="s in suggestions"
          :key="s"
          type="button"
          class="guide-suggest__chip"
          :disabled="guide.isStreaming"
          @click="onSuggestion(s)"
          @mouseenter="playUiHover"
        >
          {{ s }}
        </button>
      </div>

      <form
        class="guide-input"
        @submit.prevent="onSubmit"
      >
        <textarea
          v-model="inputText"
          class="guide-input__field"
          rows="2"
          :placeholder="guide.isStreaming ? 'AI 助教回复中…' : '说说你的目标 / 困惑（Enter 发送，Shift+Enter 换行）'"
          :disabled="guide.isStreaming"
          data-testid="guide-input"
          @keydown="onKeydown"
        />
        <button
          type="submit"
          class="guide-input__send"
          :disabled="guide.isStreaming || !inputText.trim()"
          data-testid="guide-send"
        >
          {{ guide.isStreaming ? '…' : '发送' }}
        </button>
      </form>
      <p
        v-if="guide.lastError"
        class="guide-error"
      >
        {{ guide.lastError }}
      </p>
    </section>
  </div>
</template>

<style scoped>
.guide-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  padding: 16px;
  background: rgba(8, 8, 12, 0);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition: opacity 180ms ease, background 180ms ease;
}
.guide-overlay--open {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  background: rgba(8, 8, 12, 0.45);
}
.guide-card {
  width: min(440px, calc(100vw - 32px));
  height: min(640px, calc(100vh - 32px));
  display: flex;
  flex-direction: column;
  background: var(--pyt-panel-bg);
  border: 3px solid var(--pyt-panel-border);
  border-radius: 2px;
  box-shadow: 4px 4px 0 var(--pyt-panel-border-dark), 0 8px 24px var(--pyt-panel-shadow);
  color: var(--pyt-text-primary);
  font-family: var(--pyt-font-pixel);
  image-rendering: pixelated;
  transform: translateY(12px);
  transition: transform 180ms ease;
}
.guide-overlay--open .guide-card {
  transform: translateY(0);
}
.guide-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 2px solid var(--pyt-panel-border);
  background: var(--pyt-panel-header-bg);
}
.guide-head__title {
  font-size: 14px;
  color: var(--pyt-text-accent);
  letter-spacing: 1px;
  text-shadow: 1px 1px 0 var(--pyt-color-shadow);
}
.guide-head__close {
  background: var(--pyt-btn-bg);
  border: 2px solid var(--pyt-btn-border);
  color: var(--pyt-text-primary);
  width: 26px;
  height: 26px;
  border-radius: 2px;
  cursor: pointer;
}
.guide-thread {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.guide-msg {
  display: flex;
}
.guide-msg--user {
  justify-content: flex-end;
}
.guide-msg--assistant {
  justify-content: flex-start;
}
.guide-bubble {
  max-width: 88%;
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid var(--pyt-panel-border-dark);
  background: rgba(0, 0, 0, 0.12);
  font-size: 13px;
  line-height: 1.6;
}
.guide-msg--user .guide-bubble {
  background: rgba(241, 196, 83, 0.18);
  border-color: var(--pyt-panel-border);
}
.guide-bubble--failed {
  border-color: var(--pyt-danger, #e63946);
  color: var(--pyt-danger, #e63946);
}
.guide-typing {
  color: var(--pyt-text-secondary);
  font-size: 12px;
}
.guide-trace {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--pyt-info, #6aa1d6);
  margin-bottom: 6px;
}
.guide-trace__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--pyt-info, #6aa1d6);
  animation: pyt-glow-pulse 1.1s ease-in-out infinite;
}
.guide-plan {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.guide-plan__step {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 8px;
  align-items: center;
  padding: 6px 8px;
  border: 1px solid var(--pyt-panel-border-dark);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.1);
}
.guide-plan__step--done {
  opacity: 0.65;
}
.guide-plan__phase {
  flex: 0 0 auto;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 2px;
  border: 1px solid currentColor;
}
.guide-plan__phase--watch {
  color: #6aa1d6;
}
.guide-plan__phase--learn {
  color: var(--pyt-color-gold);
}
.guide-plan__phase--practice {
  color: #e07a3a;
}
.guide-plan__phase--assess {
  color: #6bbf59;
}
.guide-plan__body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.guide-plan__title {
  font-size: 12px;
  font-weight: 700;
}
.guide-plan__detail {
  font-size: 10px;
  color: var(--pyt-text-secondary);
}
.guide-plan__go {
  flex: 0 0 auto;
  font-family: inherit;
  font-size: 11px;
  padding: 4px 8px;
  background: var(--pyt-btn-bg);
  color: var(--pyt-text-primary);
  border: 2px solid var(--pyt-btn-border);
  border-radius: 2px;
  cursor: pointer;
  white-space: nowrap;
}
.guide-plan__go:hover {
  border-color: var(--pyt-text-accent);
}
.guide-clarify {
  margin: 8px 0 0;
  padding: 6px 8px;
  font-size: 12px;
  border-left: 3px solid var(--pyt-text-accent);
  background: rgba(241, 196, 83, 0.12);
}
.guide-dispatch {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.guide-dispatch__label {
  font-size: 10px;
  color: var(--pyt-text-secondary);
}
.guide-dispatch__chip {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 2px;
  border: 1px solid var(--pyt-panel-border-dark);
  color: var(--pyt-text-secondary);
}
.guide-dispatch__chip--ok {
  border-color: var(--pyt-bar-energy, #6bbf59);
  color: var(--pyt-bar-energy, #6bbf59);
}
.guide-profile {
  margin: 8px 0 0;
  font-size: 10px;
  color: var(--pyt-info, #6aa1d6);
}
.guide-suggest {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 12px 0;
}
.guide-suggest__chip {
  font-family: inherit;
  font-size: 11px;
  padding: 4px 10px;
  background: rgba(0, 0, 0, 0.14);
  color: var(--pyt-text-secondary);
  border: 1px solid var(--pyt-panel-border-dark);
  border-radius: var(--pyt-radius-lg);
  cursor: pointer;
}
.guide-suggest__chip:hover:not(:disabled) {
  border-color: var(--pyt-text-accent);
  color: var(--pyt-text-primary);
}
.guide-suggest__chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.guide-input {
  display: flex;
  gap: 8px;
  padding: 10px 12px 12px;
  border-top: 2px dashed var(--pyt-panel-border-dark);
}
.guide-input__field {
  flex: 1 1 auto;
  resize: none;
  font-family: inherit;
  font-size: 13px;
  padding: 8px;
  border-radius: 4px;
  border: 1px solid var(--pyt-panel-border-dark);
  background: var(--pyt-panel-bg);
  color: var(--pyt-text-primary);
}
.guide-input__send {
  flex: 0 0 auto;
  align-self: stretch;
  font-family: inherit;
  font-size: 13px;
  padding: 0 16px;
  background: var(--pyt-btn-bg);
  color: var(--pyt-text-primary);
  border: 2px solid var(--pyt-text-accent);
  border-radius: 2px;
  cursor: pointer;
}
.guide-input__send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.guide-error {
  margin: 0;
  padding: 0 12px 10px;
  font-size: 11px;
  color: var(--pyt-danger, #e63946);
}
@media (max-width: 520px) {
  .guide-overlay {
    padding: 0;
  }
  .guide-card {
    width: 100vw;
    height: 100vh;
    border-radius: 0;
  }
}
</style>
