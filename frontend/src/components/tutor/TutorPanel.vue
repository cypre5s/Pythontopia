<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useTutorStore } from '@stores/tutor';
import { useToastStore } from '@stores/toast';
import { useSseStream } from '@/composables/useSseStream';
import { playSfx } from '@/composables/useAudio';
import MarkdownView from '@/components/common/MarkdownView.vue';
import CoursewareMentionMenu from '@/components/tutor/CoursewareMentionMenu.vue';
import TutorVisualAids, { type TutorVisualAid } from '@/components/tutor/TutorVisualAids.vue';
import { listChapters, listKnowledgeComponents } from '@/api/course';
import {
  buildMentionItems,
  useCoursewareMention,
  type MentionItem,
} from '@/composables/useCoursewareMention';
import {
  type TutorAskRequest,
  type TutorSessionDto,
} from '@/api/tutor';

interface TutorStreamResult {
  sessionId: number;
  question: string;
  answerMd: string;
  evidencePageIds: number[];
  groundingStatus: string;
  // Python TutorResponse 原样透传（snake_case）：多模态图解。
  visual_aids?: TutorVisualAid[];
}

// 可选上下文 props：让本面板可复用到做题页（TrainingPanel），带上「当前题目 KC + 题面摘要」；
// 普通 /tutor 页不传则为空，行为不变。
const props = withDefaults(
  defineProps<{
    kcCodes?: string[];
    context?: string;
    /** 内嵌模式：隐藏左侧会话列表，单列对话（讲解页/做题页侧栏） */
    embedded?: boolean;
    /** 看课页视频 RAG：当前视频 id（提问时随附，后端合成视频上下文）。 */
    videoId?: number;
    /** 看课页视频 RAG：当前播放时刻（秒）。 */
    videoTimeSec?: number;
  }>(),
  { kcCodes: () => [], context: '', embedded: false, videoId: undefined, videoTimeSec: undefined },
);

const router = useRouter();
const tutor = useTutorStore();
const toast = useToastStore();

const scrollRef = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLTextAreaElement | null>(null);

// 看图答疑：最多 3 张截图 / 手写代码图，
// 后端逐张经讯飞「图片理解」转描述后交 deepseek 作答。
const MAX_IMAGE_BYTES = 4 * 1024 * 1024;
const MAX_IMAGES = 3;
const fileInputRef = ref<HTMLInputElement | null>(null);
const attachedImages = ref<string[]>([]);

// `@/` 课件引用命令：输入区状态机 + 候选项（章节 / KC）。
const mentionItems = ref<MentionItem[]>([]);
const composer = useCoursewareMention(mentionItems);
const inputText = composer.text;
const mentionVisible = composer.menuVisible;
const mentionGroups = composer.groups;
const mentionActiveIndex = composer.activeIndex;
const selectedRefs = composer.selected;

// 答疑 SSE 端点：POST /api/tutor/ask（非 /ask/stream，后者 404）。
const sse = useSseStream<TutorStreamResult>('/api/tutor/ask');

const isStreaming = computed(() => sse.status.value === 'streaming' || sse.status.value === 'connecting');
const canSubmit = computed(() => inputText.value.trim().length > 0 && !isStreaming.value);

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

const visualAids = computed<TutorVisualAid[]>(
  () => sse.result.value?.visual_aids ?? [],
);

// 一次提问的原样参数（题面 / KC / 上下文 / 图片），供 runAsk 复用。
interface AskParams {
  question: string;
  kcCodes: string[];
  context: string;
  images?: string[];
}

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
  void tutor.loadConversation(session);
  composer.reset();
  clearAttachedImages();
}

function startNew(): void {
  playSfx('sfx_click');
  sse.abort();
  tutor.startNewSession();
  composer.reset();
  clearAttachedImages();
  toast.show('新对话已开始，输入 @ 可引用课件章节/知识点', 'info');
  void nextTick(() => {
    inputRef.value?.focus();
  });
}

function onInputChange(event: Event): void {
  const target = event.target as HTMLTextAreaElement;
  composer.updateFromText(target.value);
}

function onSelectMention(item: MentionItem): void {
  composer.selectItem(item);
  void nextTick(() => {
    inputRef.value?.focus();
  });
}

function onInputKeydown(event: KeyboardEvent): void {
  if (mentionVisible.value) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      composer.moveActive(1);
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      composer.moveActive(-1);
      return;
    }
    if (event.key === 'Enter' && !event.shiftKey) {
      if (composer.commitActive()) {
        event.preventDefault();
        return;
      }
    }
    if (event.key === 'Escape') {
      event.preventDefault();
      composer.close();
      return;
    }
  }
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault();
    void submit();
  }
}

function triggerImagePick(): void {
  playSfx('sfx_click');
  fileInputRef.value?.click();
}

function onImageSelected(event: Event): void {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  input.value = '';
  for (const file of files) {
    if (attachedImages.value.length >= MAX_IMAGES) {
      toast.show(`最多上传 ${MAX_IMAGES} 张图片`, 'error');
      break;
    }
    if (!/^image\/(png|jpe?g)$/.test(file.type)) {
      toast.show('仅支持 PNG / JPG 图片', 'error');
      continue;
    }
    if (file.size > MAX_IMAGE_BYTES) {
      toast.show('单张图片不能超过 4MB', 'error');
      continue;
    }
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string' && attachedImages.value.length < MAX_IMAGES) {
        attachedImages.value = [...attachedImages.value, reader.result];
      }
    };
    reader.onerror = () => toast.show('图片读取失败', 'error');
    reader.readAsDataURL(file);
  }
}

function removeImageAt(index: number): void {
  attachedImages.value = attachedImages.value.filter((_, i) => i !== index);
}

function clearAttachedImages(): void {
  attachedImages.value = [];
}

// 发起一次答疑流并处理流终态；被首次提交 submit 与「换讲法」即时重问共用。
// style 仅在显式换讲法时传入（无视后端画像置信度门槛）；首次提交不传，保持原行为。
async function runAsk(ask: AskParams): Promise<void> {
  tutor.setStatus('streaming');
  tutor.setStreamingAnswer('');

  const body: TutorAskRequest = {
    question: ask.question,
    kcCodes: ask.kcCodes,
    context: ask.context,
    conversationId: tutor.ensureConversationId(),
    ...(props.videoId != null ? { videoId: props.videoId } : {}),
    ...(props.videoTimeSec != null ? { videoTimeSec: props.videoTimeSec } : {}),
    ...(ask.images ? { images: ask.images } : {}),
  };
  await sse.start(body);

  if (sse.status.value === 'done' && sse.result.value) {
    playSfx('sfx_success');
    const result = sse.result.value;
    const session: TutorSessionDto = {
      id: result.sessionId,
      question: result.question,
      answerMd: result.answerMd,
      evidencePageIds: result.evidencePageIds,
      groundingStatus: result.groundingStatus,
      conversationId: tutor.conversationId,
      createdAt: new Date().toISOString(),
    };
    tutor.onStreamComplete(session);
  } else if (sse.status.value === 'error' || sse.error.value) {
    playSfx('sfx_error');
    tutor.setStatus('error');
    tutor.lastError = sse.error.value?.message ?? 'AI 助教回答失败';
    toast.show(tutor.lastError ?? 'AI 助教回答失败', 'error');
  }
}

async function submit(): Promise<void> {
  const q = inputText.value.trim();
  if (!q) return;

  playSfx('sfx_click');

  // `@/` 引用：选中的 KC 合并进 kcCodes（驱动 RAG 过滤），引用标签拼进 context。
  const mergedKcCodes = Array.from(
    new Set([...props.kcCodes, ...composer.selectedKcCodes.value]),
  );
  const context = [props.context, composer.contextHint.value]
    .map((part) => part.trim())
    .filter((part) => part.length > 0)
    .join(' ');

  composer.reset();

  const images = attachedImages.value.length > 0 ? [...attachedImages.value] : undefined;
  const ask: AskParams = {
    question: q,
    kcCodes: mergedKcCodes,
    context,
    ...(images ? { images } : {}),
  };
  await runAsk(ask);
  clearAttachedImages();
}

function goToPage(pageId: number): void {
  playSfx('sfx_click');
  // 证据回链按 page_id 跳转（Library 用 ?pageId 解析 page_id→docId+pageNo，
  // 而 ?page 是文档内 1-based 页码，二者语义不同，不能混用）。
  void router.push({ path: '/library', query: { pageId: String(pageId) } });
}

onMounted(async () => {
  // 进入面板即确保有会话窗口 id（首问起就归属同一 conversation，支撑多轮记忆）。
  tutor.ensureConversationId();
  // 加载课程包 43 的章节 + KC 作为 `@` 引用候选；失败不影响普通问答。
  try {
    const [chapters, kcs] = await Promise.all([
      listChapters(),
      listKnowledgeComponents(),
    ]);
    mentionItems.value = buildMentionItems(chapters, kcs);
  } catch {
    mentionItems.value = [];
  }
});
</script>

<template>
  <div
    class="tutor-panel"
    :class="{ 'tutor-panel--embedded': embedded }"
    data-testid="tutor-panel"
  >
    <div
      v-if="embedded"
      class="embedded-bar"
    >
      <span class="embedded-bar__hint">依据本章知识点和课件</span>
      <button
        type="button"
        class="embedded-bar__new"
        data-testid="tutor-new-session"
        @click="startNew"
      >
        + 新问
      </button>
    </div>

    <!-- 左栏：会话记录 -->
    <aside
      v-if="!embedded"
      class="sidebar"
    >
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

    <!-- 右栏主区：对话 -->
    <main class="conversation">
      <!-- 多轮对话 thread：历史轮次（当前轮之前，只读）+ 当前轮（含接地状态与证据回链） -->
      <div
        ref="scrollRef"
        class="answer-area"
      >
        <template
          v-for="turn in tutor.turns"
          :key="turn.id"
        >
          <div class="msg msg--user">
            <span class="msg__role">你</span>
            <p class="msg__text">
              {{ turn.question }}
            </p>
          </div>
          <div class="msg msg--tutor">
            <span class="msg__role">AI 助教</span>
            <MarkdownView :source="turn.answerMd ?? ''" />
            <div
              v-if="turn.groundingStatus"
              class="grounding-bar"
            >
              <span
                class="grounding-chip"
                :class="groundingInfo(turn.groundingStatus).cls"
              >
                {{ groundingInfo(turn.groundingStatus).text }}
              </span>
              <span
                v-if="turn.evidencePageIds.length > 0"
                class="evidence-badges"
              >
                <button
                  v-for="pid in turn.evidencePageIds"
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
          </div>
        </template>

        <!-- 当前问题 -->
        <div
          v-if="tutor.currentSession"
          class="msg msg--user"
        >
          <span class="msg__role">你</span>
          <p class="msg__text">
            {{ tutor.currentSession.question }}
          </p>
        </div>

        <!-- 当前回答（含接地状态与证据回链） -->
        <div
          v-if="displayedAnswer"
          class="msg msg--tutor"
        >
          <span class="msg__role">AI 助教</span>
          <MarkdownView :source="displayedAnswer" />
          <span
            v-if="isStreaming"
            class="streaming-cursor"
          >▌</span>
          <TutorVisualAids
            v-if="!isStreaming && visualAids.length > 0"
            :aids="visualAids"
          />
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
        </div>
        <p
          v-else-if="tutor.turns.length === 0 && !tutor.currentSession && !isStreaming"
          class="answer-placeholder"
        >
          {{ embedded ? '边看视频边问：输入 @ 可引用本章知识点' : '有什么 Python 问题？问问 AI 助教' }}
        </p>
      </div>

      <!-- 错误 -->
      <p
        v-if="tutor.lastError && tutor.status === 'error'"
        class="error-bar"
      >
        ⚠ {{ tutor.lastError }}
      </p>

      <!-- 已选课件引用（@ 命令） -->
      <div
        v-if="selectedRefs.length > 0"
        class="ref-chips"
        data-testid="tutor-ref-chips"
      >
        <span class="ref-chips__label">引用课件：</span>
        <button
          v-for="ref_ in selectedRefs"
          :key="ref_.token"
          type="button"
          class="ref-chip"
          :class="`ref-chip--${ref_.kind}`"
          :title="`移除引用 ${ref_.label}`"
          @click="composer.removeRef(ref_.token)"
        >
          <span class="ref-chip__label">{{ ref_.label }}</span>
          <span
            class="ref-chip__x"
            aria-hidden="true"
          >×</span>
        </button>
      </div>

      <!-- 看图答疑：多图附件缩略图，列在输入框上方 -->
      <div
        v-if="attachedImages.length > 0"
        class="image-attachments"
        data-testid="tutor-image-attach"
      >
        <div
          v-for="(img, i) in attachedImages"
          :key="i"
          class="image-chip"
        >
          <img
            :src="img"
            class="image-chip__thumb"
            :alt="`待识别图片 ${i + 1}`"
          >
          <button
            type="button"
            class="image-chip__remove"
            :aria-label="`移除图片 ${i + 1}`"
            @click="removeImageAt(i)"
          >
            ×
          </button>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <input
          ref="fileInputRef"
          type="file"
          accept="image/png,image/jpeg"
          multiple
          hidden
          data-testid="tutor-image-input"
          @change="onImageSelected"
        >
        <CoursewareMentionMenu
          :visible="mentionVisible"
          :groups="mentionGroups"
          :active-index="mentionActiveIndex"
          @select="onSelectMention"
        />
        <textarea
          ref="inputRef"
          :value="inputText"
          class="input-area__textarea"
          data-testid="tutor-input"
          placeholder="输入你的 Python 问题…（输入 @ 引用课件章节/知识点）"
          rows="2"
          :disabled="isStreaming"
          @input="onInputChange"
          @keydown="onInputKeydown"
        />
        <button
          type="button"
          class="input-area__attach"
          data-testid="tutor-image-button"
          title="上传截图 / 手写代码图（最多 3 张），看图问 AI 助教"
          :disabled="isStreaming || attachedImages.length >= MAX_IMAGES"
          @click="triggerImagePick"
        >
          🖼
        </button>
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
  font-family: var(--pyt-font-pixel);
  color: #3a2a14;
}
.tutor-panel--embedded {
  grid-template-columns: 1fr;
  grid-template-rows: auto 1fr;
}
.embedded-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 8px;
  border-bottom: 2px dashed rgba(110, 79, 29, 0.35);
  background: rgba(255, 247, 230, 0.6);
}
.embedded-bar__hint {
  font-size: 10px;
  color: var(--pyt-color-text-hint);
  line-height: 1.3;
}
.embedded-bar__new {
  flex: 0 0 auto;
  font-family: inherit;
  font-size: 10px;
  padding: 3px 8px;
  background: #f7d774;
  border: 2px solid #8c6a23;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 700;
}
.tutor-panel--embedded .conversation {
  padding: 8px;
  gap: 6px;
}
.tutor-panel--embedded .input-area__textarea {
  font-size: 12px;
}
.tutor-panel--embedded .input-area__submit {
  font-size: 12px;
  padding: 6px 10px;
}
.tutor-panel--embedded .msg__text,
.tutor-panel--embedded .answer-placeholder {
  font-size: 12px;
}

/* ── 侧栏 ── */
.sidebar {
  display: flex;
  flex-direction: column;
  border-right: 3px solid var(--pyt-color-text-hint);
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
  color: var(--pyt-color-text-primary);
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
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
  transition: background 120ms, border-color 120ms;
}
.session-item:hover {
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-text-hint);
}
.session-item--active {
  background: #ffe098;
  border-color: var(--pyt-color-text-hint);
  box-shadow: inset 0 0 0 1px var(--pyt-color-text-hint);
}
.session-item__q {
  font-size: 12px;
  color: var(--pyt-color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.session-item__time {
  font-size: 10px;
  color: var(--pyt-color-text-muted);
}
.session-empty {
  padding: 16px 8px;
  text-align: center;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
}

/* ── 对话区 ── */
.conversation {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 12px 16px;
  gap: 10px;
}

/* 接地状态条 */
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
  color: var(--pyt-color-text-muted);
  border: 1px solid #d4af37;
}
.grounding--failed {
  background: #f8d7d0;
  color: #b3402a;
  border: 1px solid var(--pyt-color-danger);
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
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-info);
  color: #1b4a6b;
  border-radius: 4px;
  cursor: pointer;
  transition: background 120ms;
}
.evidence-badge:hover {
  background: #a8cfe6;
}

/* 消息气泡 */
.msg {
  padding: 8px 12px;
  border-radius: var(--pyt-radius-lg);
  border: 2px solid var(--pyt-color-border);
}
.msg--user {
  background: var(--pyt-color-bg-elevated);
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
  color: var(--pyt-color-text-muted);
  margin-bottom: 2px;
}
.msg__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
}

/* 回答区 */
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
  color: var(--pyt-color-text-muted);
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

/* 错误提示 */
.error-bar {
  margin: 0;
  padding: 6px 10px;
  background: #f8d7d0;
  border: 1px solid var(--pyt-color-danger);
  border-radius: 4px;
  font-size: 12px;
  color: #b3402a;
}

/* 已选引用标签（@ 命令） */
.ref-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.ref-chips__label {
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-muted);
}
.ref-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px 2px 8px;
  font-family: inherit;
  font-size: 11px;
  border-radius: 999px;
  cursor: pointer;
  border: 1px solid var(--pyt-color-text-hint);
  background: #ffe098;
  color: var(--pyt-color-text-primary);
  transition: background 120ms;
}
.ref-chip:hover {
  background: #f7d774;
}
.ref-chip--chapter {
  background: #d6e6f4;
  border-color: var(--pyt-color-info);
  color: #1b4a6b;
}
.ref-chip--kc {
  background: #d6ead1;
  border-color: #4d6e1d;
  color: #2d5b1b;
}
.ref-chip__label {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ref-chip__x {
  font-weight: 700;
  font-size: 13px;
  line-height: 1;
}

/* 输入区 */
.input-area {
  position: relative;
  display: flex;
  gap: 8px;
  align-items: flex-end;
  border-top: 2px dashed rgba(110, 79, 29, 0.35);
  padding-top: 10px;
}
.input-area__textarea {
  flex: 1 1 auto;
  padding: 8px 10px;
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border);
  border-radius: 6px;
  font-family: inherit;
  font-size: 14px;
  color: #3a2a14;
  resize: none;
}
.input-area__textarea:focus {
  outline: none;
  border-color: var(--pyt-color-text-hint);
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

/* 看图答疑：附件按钮 + 缩略图附件条 */
.input-area__attach {
  flex: 0 0 auto;
  font-size: 16px;
  padding: 6px 10px;
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border);
  border-radius: 6px;
  cursor: pointer;
  transition: background 80ms;
}
.input-area__attach:hover:not(:disabled) {
  background: var(--pyt-color-warning-bg);
}
.input-area__attach:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.image-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px 0;
}
.image-chip {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: var(--pyt-radius-md);
  border: 1px solid var(--pyt-color-border);
  overflow: hidden;
  background: var(--pyt-color-bg-soft);
}
.image-chip__thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.image-chip__remove {
  position: absolute;
  top: 1px;
  right: 1px;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  line-height: 1;
  color: var(--pyt-color-text-on-dark);
  background: var(--pyt-color-shadow);
  border: none;
  border-radius: var(--pyt-radius-sm);
  cursor: pointer;
  opacity: 0.85;
}
.image-chip__remove:hover {
  opacity: 1;
}

/* ── 响应式 ── */
@media (max-width: 768px) {
  .tutor-panel {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }
  .sidebar {
    border-right: none;
    border-bottom: 3px solid var(--pyt-color-text-hint);
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
