<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useTutorStore } from '@stores/tutor';
import { useToastStore } from '@stores/toast';
import { useSseStream } from '@/composables/useSseStream';
import { playSfx } from '@/composables/useAudio';
import MarkdownView from '@/components/common/MarkdownView.vue';
import type { TutorSessionDto } from '@/api/tutor';

interface TutorStreamResult {
  sessionId: number;
  question: string;
  answerMd: string;
  evidencePageIds: number[];
  groundingStatus: string;
}

// R-12 主线 Phase 3/4：可选上下文 props，让本面板可复用到做题页（TrainingPanel）
// 时带上「当前题目 KC + 题面摘要」，普通 /tutor 页不传则为空，行为不变。
const props = withDefaults(
  defineProps<{ kcCodes?: string[]; context?: string }>(),
  { kcCodes: () => [], context: '' },
);

const router = useRouter();
const tutor = useTutorStore();
const toast = useToastStore();

const question = ref('');
const scrollRef = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLTextAreaElement | null>(null);

// 路径修正：后端映射 POST /api/tutor/ask（此前前端误用 /ask/stream 会 404）。
const sse = useSseStream<TutorStreamResult>('/api/tutor/ask');

const isStreaming = computed(() => sse.status.value === 'streaming' || sse.status.value === 'connecting');
const canSubmit = computed(() => question.value.trim().length > 0 && !isStreaming.value);

const displayedAnswer = computed(() => {
  if (isStreaming.value || sse.status.value === 'done') {
    return sse.tokens.value || tutor.streamingAnswer;
  }
  return tutor.streamingAnswer;
});

const groundingStatus = computed<string | null>(() => {
  if (sse.result.value) return sse.result.value.groundingStatus;
  if (tutor.currentSession) return tutor.currentSession.groundingStatus;
  return null;
});

const evidencePageIds = computed<number[]>(() => {
  if (sse.result.value) return sse.result.value.evidencePageIds;
  if (tutor.currentSession) return tutor.currentSession.evidencePageIds;
  return [];
});

const GROUNDING_LABELS: Record<string, { text: string; cls: string }> = {
  PASSED: { text: '引用充分', cls: 'grounding--passed' },
  NO_EVIDENCE: { text: '通识知识', cls: 'grounding--general' },
  FAILED: { text: '未通过', cls: 'grounding--failed' },
};

function groundingInfo(status: string): { text: string; cls: string } {
  return GROUNDING_LABELS[status] ?? { text: status, cls: '' };
}

watch(
  () => sse.tokens.value,
  () => {
    tutor.setStreamingAnswer(sse.tokens.value);
    void nextTick(() => {
      scrollRef.value?.scrollTo({ top: scrollRef.value.scrollHeight, behavior: 'smooth' });
    });
  },
);

function selectSession(session: TutorSessionDto): void {
  playSfx('sfx_click');
  sse.abort();
  tutor.selectSession(session);
  question.value = '';
}

function startNew(): void {
  playSfx('sfx_click');
  sse.abort();
  tutor.startNewSession();
  question.value = '';
  toast.show('新对话已开始，请输入问题', 'info');
  void nextTick(() => {
    inputRef.value?.focus();
  });
}

async function submit(): Promise<void> {
  const q = question.value.trim();
  if (!q) return;

  playSfx('sfx_click');
  tutor.setStatus('streaming');
  tutor.setStreamingAnswer('');

  const savedQuestion = q;
  question.value = '';

  await sse.start({
    question: savedQuestion,
    kcCodes: props.kcCodes,
    context: props.context,
  });

  if (sse.status.value === 'done' && sse.result.value) {
    playSfx('sfx_success');
    const result = sse.result.value;
    const session: TutorSessionDto = {
      id: result.sessionId,
      question: result.question,
      answerMd: result.answerMd,
      evidencePageIds: result.evidencePageIds,
      groundingStatus: result.groundingStatus,
      createdAt: new Date().toISOString(),
    };
    tutor.onStreamComplete(session);
  } else if (sse.status.value === 'error' || sse.error.value) {
    playSfx('sfx_error');
    tutor.setStatus('error');
    tutor.lastError = sse.error.value?.message ?? '导师回答失败';
    toast.show(tutor.lastError ?? '导师回答失败', 'error');
  }
}

function goToPage(pageId: number): void {
  playSfx('sfx_click');
  // 证据回链按 page_id 跳转（Library 用 ?pageId 解析 page_id→docId+pageNo，
  // 而 ?page 是文档内 1-based 页码，二者语义不同，不能混用）。
  void router.push({ path: '/library', query: { pageId: String(pageId) } });
}
</script>

<template>
  <div
    class="tutor-panel"
    data-testid="tutor-panel"
  >
    <!-- Left Sidebar: Session History -->
    <aside class="sidebar">
      <div class="sidebar__head">
        <h3 class="sidebar__title">
          对话记录
        </h3>
        <button
          type="button"
          class="sidebar__new"
          data-testid="tutor-new-session"
          @click="startNew"
        >
          + 新对话
        </button>
      </div>
      <ul class="session-list">
        <li
          v-for="session in tutor.sessions"
          :key="session.id"
          class="session-item"
          :class="{ 'session-item--active': tutor.currentSession?.id === session.id }"
          @click="selectSession(session)"
        >
          <span class="session-item__q">{{ session.question }}</span>
          <span class="session-item__time">{{ session.createdAt.slice(0, 10) }}</span>
        </li>
        <li
          v-if="tutor.sessions.length === 0"
          class="session-empty"
        >
          还没有对话记录
        </li>
      </ul>
    </aside>

    <!-- Right Main: Conversation -->
    <main class="conversation">
      <!-- Grounding Status Chip -->
      <div
        v-if="groundingStatus"
        class="grounding-bar"
      >
        <span
          class="grounding-chip"
          :class="groundingInfo(groundingStatus).cls"
          data-testid="grounding-chip"
        >
          {{ groundingInfo(groundingStatus).text }}
        </span>
        <span
          v-if="evidencePageIds.length > 0"
          class="evidence-badges"
        >
          <button
            v-for="pid in evidencePageIds"
            :key="pid"
            type="button"
            class="evidence-badge"
            :title="`跳转到课件第 ${pid} 页`"
            @click="goToPage(pid)"
          >
            p.{{ pid }}
          </button>
        </span>
      </div>

      <!-- Current Question -->
      <div
        v-if="tutor.currentSession"
        class="msg msg--user"
      >
        <span class="msg__role">你</span>
        <p class="msg__text">
          {{ tutor.currentSession.question }}
        </p>
      </div>

      <!-- Answer Area -->
      <div
        ref="scrollRef"
        class="answer-area"
      >
        <div
          v-if="displayedAnswer"
          class="msg msg--tutor"
        >
          <span class="msg__role">导师</span>
          <MarkdownView :source="displayedAnswer" />
          <span
            v-if="isStreaming"
            class="streaming-cursor"
          >▌</span>
        </div>
        <p
          v-else-if="!tutor.currentSession && !isStreaming"
          class="answer-placeholder"
        >
          有什么 Python 问题？向导师提问吧
        </p>
      </div>

      <!-- Error -->
      <p
        v-if="tutor.lastError && tutor.status === 'error'"
        class="error-bar"
      >
        ⚠ {{ tutor.lastError }}
      </p>

      <!-- Input Area -->
      <div class="input-area">
        <textarea
          ref="inputRef"
          v-model="question"
          class="input-area__textarea"
          data-testid="tutor-input"
          placeholder="输入你的 Python 问题…"
          rows="2"
          :disabled="isStreaming"
          @keydown.enter.ctrl.exact="submit"
          @keydown.enter.meta.exact="submit"
        />
        <button
          type="button"
          class="input-area__submit"
          data-testid="tutor-submit"
          :disabled="!canSubmit"
          @click="submit"
        >
          {{ isStreaming ? '回答中…' : '提问' }}
        </button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.tutor-panel {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 0;
  height: 100%;
  min-height: 0;
  font-family: var(--pyt-font-pixel, monospace);
  color: #3a2a14;
}

/* ── Sidebar ── */
.sidebar {
  display: flex;
  flex-direction: column;
  border-right: 3px solid #6e4f1d;
  background: linear-gradient(180deg, #f9e7bc 0%, #f1d896 100%);
  padding: 10px;
  min-height: 0;
  overflow: hidden;
}
.sidebar__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 2px dashed rgba(110, 79, 29, 0.35);
  margin-bottom: 8px;
}
.sidebar__title {
  margin: 0;
  font-size: 14px;
  color: #4d3613;
}
.sidebar__new {
  font-family: inherit;
  font-size: 11px;
  padding: 3px 8px;
  background: #f7d774;
  color: #2a1a08;
  border: 2px solid #8c6a23;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 700;
}
.sidebar__new:hover {
  background: #fde48a;
}
.session-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
  flex: 1 1 auto;
  scrollbar-width: thin;
}
.session-item {
  padding: 6px 8px;
  background: #fff7e6;
  border: 2px solid #c9a86b;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
  transition: background 120ms, border-color 120ms;
}
.session-item:hover {
  background: #f5e2c1;
  border-color: #6e4f1d;
}
.session-item--active {
  background: #ffe098;
  border-color: #6e4f1d;
  box-shadow: inset 0 0 0 1px #6e4f1d;
}
.session-item__q {
  font-size: 12px;
  color: #4d3613;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.session-item__time {
  font-size: 10px;
  color: #8a6c2c;
}
.session-empty {
  padding: 16px 8px;
  text-align: center;
  font-size: 12px;
  color: #8a6c2c;
}

/* ── Conversation ── */
.conversation {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 12px 16px;
  gap: 10px;
}

/* Grounding bar */
.grounding-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.grounding-chip {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
}
.grounding--passed {
  background: #d6ead1;
  color: #2d5b1b;
  border: 1px solid #4d6e1d;
}
.grounding--general {
  background: #fff3cd;
  color: #8a6c2c;
  border: 1px solid #d4af37;
}
.grounding--failed {
  background: #f8d7d0;
  color: #b3402a;
  border: 1px solid #b8442d;
}
.evidence-badges {
  display: inline-flex;
  gap: 4px;
  flex-wrap: wrap;
}
.evidence-badge {
  font-family: monospace, 'PingFang SC', sans-serif;
  font-size: 11px;
  padding: 2px 6px;
  background: #d0e3f1;
  border: 1px solid #2a6f97;
  color: #1b4a6b;
  border-radius: 4px;
  cursor: pointer;
  transition: background 120ms;
}
.evidence-badge:hover {
  background: #a8cfe6;
}

/* Messages */
.msg {
  padding: 8px 12px;
  border-radius: 8px;
  border: 2px solid #c9a86b;
}
.msg--user {
  background: #fff7e6;
  align-self: flex-end;
  max-width: 80%;
}
.msg--tutor {
  background: #f5ecd4;
  align-self: flex-start;
  max-width: 90%;
}
.msg__role {
  display: block;
  font-size: 10px;
  font-weight: 700;
  color: #8a6c2c;
  margin-bottom: 2px;
}
.msg__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
}

/* Answer area */
.answer-area {
  flex: 1 1 auto;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  scrollbar-width: thin;
}
.answer-placeholder {
  margin: auto;
  padding: 32px 16px;
  text-align: center;
  font-size: 14px;
  color: #8a6c2c;
}
.streaming-cursor {
  animation: blink-cursor 0.8s steps(2) infinite;
  color: #d4af37;
  font-weight: 700;
}
@keyframes blink-cursor {
  0% { opacity: 1; }
  50% { opacity: 0; }
}

/* Error */
.error-bar {
  margin: 0;
  padding: 6px 10px;
  background: #f8d7d0;
  border: 1px solid #b8442d;
  border-radius: 4px;
  font-size: 12px;
  color: #b3402a;
}

/* Input */
.input-area {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  border-top: 2px dashed rgba(110, 79, 29, 0.35);
  padding-top: 10px;
}
.input-area__textarea {
  flex: 1 1 auto;
  padding: 8px 10px;
  background: #fff7e6;
  border: 3px solid #c9a86b;
  border-radius: 6px;
  font-family: inherit;
  font-size: 14px;
  color: #3a2a14;
  resize: none;
}
.input-area__textarea:focus {
  outline: none;
  border-color: #6e4f1d;
}
.input-area__textarea:disabled {
  opacity: 0.6;
}
.input-area__submit {
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  padding: 8px 18px;
  background: #f7d774;
  color: #2a1a08;
  border: 3px solid #8c6a23;
  border-radius: 6px;
  box-shadow: 0 3px 0 #8c6a23;
  cursor: pointer;
  transition: transform 80ms, box-shadow 80ms;
  white-space: nowrap;
}
.input-area__submit:hover:not(:disabled) {
  background: #fde48a;
}
.input-area__submit:active:not(:disabled) {
  transform: translateY(2px);
  box-shadow: 0 1px 0 #8c6a23;
}
.input-area__submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .tutor-panel {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }
  .sidebar {
    border-right: none;
    border-bottom: 3px solid #6e4f1d;
    max-height: 140px;
  }
  .session-list {
    flex-direction: row;
    overflow-x: auto;
    overflow-y: hidden;
  }
  .session-item {
    min-width: 140px;
    flex: 0 0 auto;
  }
}
</style>
