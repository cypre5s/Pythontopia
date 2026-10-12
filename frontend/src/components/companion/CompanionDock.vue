<script setup lang="ts">
/**
 * CompanionDock —— 全局「随身助教」：折叠态一个像素小人，展开态一条极简 ask 栏。
 * 复用现有 /api/tutor/ask（companion store）+ MarkdownView + 当前页面上下文，
 * 答案下方给「去练 / 看课件 / 进矿洞」动作芯片，把答疑接回看→学→练→测主线。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import MarkdownView from '@/components/common/MarkdownView.vue';
import { useCompanionStore } from '@/stores/companion';
import { useCompanionContext } from '@/composables/useCompanionContext';
import { useSessionStore } from '@/stores/session';

const store = useCompanionStore();
const session = useSessionStore();
const router = useRouter();
const route = useRoute();
const ctx = useCompanionContext(() => ({ path: route.path, query: route.query }));
const draft = ref<string>('');
const pendingImages = ref<string[]>([]);
// 洞察气泡内「查看证据」展开态；切到另一条洞察时重置。
const insightEvidenceOpen = ref<boolean>(false);
const canLoadStudentSignals = computed<boolean>(() => session.role !== 'TEACHER');

const lastEvidencePageId = computed<number | null>(() => {
  for (let i = store.messages.length - 1; i >= 0; i -= 1) {
    const message = store.messages[i];
    if (message && message.role === 'assistant' && message.evidencePageIds.length > 0) {
      return message.evidencePageIds[0] ?? null;
    }
  }
  return null;
});

// 「已验证」角标只挂在最近一条助教消息上（code_runner 真跑验证后由 store.codeVerified 驱动）。
const lastAssistantIndex = computed<number>(() => {
  for (let i = store.messages.length - 1; i >= 0; i -= 1) {
    if (store.messages[i]?.role === 'assistant') return i;
  }
  return -1;
});

async function send(): Promise<void> {
  const text = draft.value;
  if (!text.trim() || store.busy) return;
  const images = pendingImages.value;
  draft.value = '';
  await store.ask(text, ctx.value, images);
  pendingImages.value = [];
}

function readImage(file: File): Promise<string | null> {
  if (!file.type.startsWith('image/')) {
    return Promise.resolve(null);
  }
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = () => {
      resolve(typeof reader.result === 'string' ? reader.result : null);
    };
    reader.onerror = () => resolve(null);
    reader.readAsDataURL(file);
  });
}

async function addImageFiles(files: File[]): Promise<void> {
  for (const file of files) {
    if (pendingImages.value.length >= 3) break;
    const dataUrl = await readImage(file);
    if (dataUrl) {
      pendingImages.value = [...pendingImages.value, dataUrl].slice(0, 3);
    }
  }
}

async function onAttachChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  await addImageFiles(Array.from(input.files ?? []));
  input.value = '';
}

async function onPaste(event: ClipboardEvent): Promise<void> {
  const files = Array.from(event.clipboardData?.items ?? [])
    .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
    .map((item) => item.getAsFile())
    .filter((file): file is File => file !== null);
  if (files.length === 0) return;
  event.preventDefault();
  await addImageFiles(files);
}

function removeImage(index: number): void {
  pendingImages.value = pendingImages.value.filter((_, i) => i !== index);
}

function goTrain(): void {
  void router.push({ path: '/training' });
}
function goLibrary(): void {
  const pageId = lastEvidencePageId.value;
  void router.push(
    pageId != null ? { path: '/library', query: { pageId: String(pageId) } } : { path: '/library' },
  );
}
function goMine(): void {
  void router.push({ path: '/bug-mine' });
}

function openNudge(): void {
  store.dismissNudge();
  void router.push({ path: '/bug-mine' });
}

// 切到新洞察（或被关闭）时收起证据，避免残留上一条的展开态。
watch(
  () => store.latestInsight?.id,
  () => {
    insightEvidenceOpen.value = false;
  },
);

function refreshStudentSignals(): void {
  if (!canLoadStudentSignals.value) {
    store.nudge = null;
    store.latestInsight = null;
    return;
  }
  void store.loadNudge();
  void store.loadInsight();
}

watch(() => session.role, refreshStudentSignals);

onMounted(refreshStudentSignals);
</script>

<template>
  <div class="companion">
    <div
      v-if="!store.open && store.latestInsight"
      class="companion__insight"
      data-testid="companion-insight"
    >
      <button
        type="button"
        class="companion__insight-close"
        data-testid="companion-insight-close"
        aria-label="关闭洞察"
        @click="store.dismissInsight()"
      >
        ×
      </button>
      <p class="companion__insight-text">
        💡 我发现你在「{{ store.latestInsight.text }}」上反复栽，要一起看看吗？
      </p>
      <button
        type="button"
        class="companion__insight-evidence-toggle"
        data-testid="companion-insight-evidence-toggle"
        :aria-expanded="insightEvidenceOpen"
        @click="insightEvidenceOpen = !insightEvidenceOpen"
      >
        {{ insightEvidenceOpen ? '收起线索' : '看看线索' }}
      </button>
      <p
        v-if="insightEvidenceOpen"
        class="companion__insight-evidence"
        data-testid="companion-insight-evidence"
      >
        线索<span
          v-for="eid in store.latestInsight.evidenceEventIds"
          :key="eid"
          class="companion__insight-clue"
        >#{{ eid }}</span>
      </p>
    </div>

    <div
      v-if="!store.open && store.nudge"
      class="companion__nudge"
      data-testid="companion-nudge"
    >
      <button
        type="button"
        class="companion__nudge-body"
        data-testid="companion-nudge-go"
        @click="openNudge"
      >
        {{ store.nudge.text }}
      </button>
      <button
        type="button"
        class="companion__nudge-close"
        data-testid="companion-nudge-close"
        aria-label="关闭提醒"
        @click="store.dismissNudge()"
      >
        ×
      </button>
    </div>

    <button
      v-if="!store.open"
      type="button"
      class="companion__fab"
      data-testid="companion-fab"
      aria-label="打开随身助教"
      @click="store.toggle()"
    >
      <span
        class="companion__sprite"
        aria-hidden="true"
      />
    </button>

    <section
      v-else
      class="companion__panel"
      data-testid="companion-panel"
      @paste="onPaste"
    >
      <header class="companion__head">
        <span
          class="companion__sprite companion__sprite--sm"
          aria-hidden="true"
        />
        <span class="companion__title">随身助教</span>
        <button
          type="button"
          class="companion__close"
          data-testid="companion-close"
          @click="store.toggle()"
        >
          收起
        </button>
      </header>

      <div class="companion__messages">
        <p
          v-if="store.messages.length === 0"
          class="companion__hint"
        >
          问我任何 Python 问题，我会带着课件证据回答。
        </p>
        <div
          v-for="(message, index) in store.messages"
          :key="index"
          class="msg"
          :class="`msg--${message.role}`"
        >
          <MarkdownView
            v-if="message.role === 'assistant' && message.content"
            :source="message.content"
          />
          <span v-else>{{ message.content }}</span>
          <div
            v-if="message.role === 'assistant' && message.evidencePageIds.length > 0"
            class="msg__evidence"
          >
            <span
              v-for="pid in message.evidencePageIds"
              :key="pid"
              class="msg__page"
            >课件 p.{{ pid }}</span>
          </div>
          <span
            v-if="message.role === 'assistant' && store.codeVerified && index === lastAssistantIndex"
            class="msg__verified"
            data-testid="companion-verified"
            title="答案已结合真实代码运行结果验证"
          >已验证</span>
        </div>
      </div>

      <div class="companion__actions">
        <button
          type="button"
          class="chip"
          data-testid="companion-action-train"
          @click="goTrain"
        >
          去练
        </button>
        <button
          v-if="lastEvidencePageId != null"
          type="button"
          class="chip"
          data-testid="companion-action-library"
          @click="goLibrary"
        >
          看课件
        </button>
        <button
          type="button"
          class="chip"
          data-testid="companion-action-mine"
          @click="goMine"
        >
          进矿洞
        </button>
      </div>

      <div
        v-if="pendingImages.length > 0"
        class="companion__thumbs"
      >
        <button
          v-for="(image, index) in pendingImages"
          :key="image"
          type="button"
          class="companion__thumb"
          :data-testid="`companion-thumb-${index}`"
          aria-label="移除图片"
          @click="removeImage(index)"
        >
          <img
            :src="image"
            alt=""
          >
        </button>
      </div>

      <form
        class="companion__compose"
        @submit.prevent="send"
      >
        <label class="companion__tool">
          图
          <input
            type="file"
            class="companion__file"
            data-testid="companion-attach"
            accept="image/*"
            multiple
            @change="onAttachChange"
          >
        </label>
        <input
          v-model="draft"
          type="text"
          class="companion__input"
          data-testid="companion-input"
          placeholder="问点什么…"
        >
        <button
          type="submit"
          class="companion__send"
          data-testid="companion-send"
          :disabled="store.busy"
        >
          发送
        </button>
      </form>
    </section>
  </div>
</template>

<style scoped>
.companion {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 60;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--pyt-space-2);
}

.companion__nudge {
  display: flex;
  align-items: stretch;
  max-width: min(280px, calc(100vw - 32px));
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-primary);
  border-radius: var(--pyt-radius-lg);
  box-shadow: 0 3px 0 var(--pyt-color-primary);
  overflow: hidden;
}
.companion__nudge-body {
  flex: 1 1 auto;
  text-align: left;
  padding: 8px 10px;
  border: none;
  background: transparent;
  color: var(--pyt-color-text-primary);
  font-size: var(--pyt-font-size-sm);
  line-height: 1.4;
  cursor: pointer;
}
.companion__nudge-body:hover {
  background: var(--pyt-color-warning-bg);
}
.companion__nudge-close {
  flex: 0 0 auto;
  width: 26px;
  border: none;
  border-left: 1px solid var(--pyt-color-border-soft);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-hint);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.companion__insight {
  position: relative;
  max-width: min(280px, calc(100vw - 32px));
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-gold);
  border-radius: var(--pyt-radius-lg);
  box-shadow: 0 3px 0 var(--pyt-color-primary);
}
@media (prefers-reduced-motion: no-preference) {
  .companion__insight {
    animation: companion-insight-pop 160ms ease-out;
  }
}
.companion__insight-close {
  position: absolute;
  top: 4px;
  right: 6px;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: var(--pyt-color-text-hint);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}
.companion__insight-text {
  margin: 0;
  padding-right: 16px;
  font-size: var(--pyt-font-size-sm);
  line-height: 1.4;
  color: var(--pyt-color-text-primary);
}
.companion__insight-evidence-toggle {
  align-self: flex-start;
  padding: 2px 10px;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-hint);
  font-size: var(--pyt-font-size-xs);
  font-weight: 600;
  cursor: pointer;
}
.companion__insight-evidence-toggle:hover {
  background: var(--pyt-color-warning-bg);
}
.companion__insight-evidence {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin: 0;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-hint);
}
.companion__insight-clue {
  padding: 0 6px;
  background: var(--pyt-color-bg-sunken);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-full);
}
@keyframes companion-insight-pop {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.companion__fab {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 3px solid var(--pyt-color-primary);
  background: var(--pyt-color-gold);
  box-shadow: 0 3px 0 var(--pyt-color-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}
.companion__fab:hover {
  background: var(--pyt-color-gold-bright);
}

.companion__sprite {
  width: 40px;
  height: 40px;
  background-image: url(/assets/pixel/sprites/npc-composites/npc_tutor_ai.png);
  background-repeat: no-repeat;
  background-size: 360px 160px;
  background-position: 0 -80px;
  image-rendering: pixelated;
}
.companion__sprite--sm {
  width: 24px;
  height: 24px;
  background-size: 216px 96px;
  background-position: 0 -48px;
}

.companion__panel {
  width: min(360px, calc(100vw - 32px));
  height: min(520px, calc(100vh - 96px));
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, var(--pyt-color-bg-elevated) 0%, var(--pyt-color-bg-soft) 100%);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.28);
  overflow: hidden;
}

.companion__head {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border-bottom: 1px solid var(--pyt-color-border-soft);
  background: var(--pyt-color-bg-elevated);
}
.companion__title {
  font-weight: 700;
  color: var(--pyt-color-text-primary);
  flex: 1 1 auto;
}
.companion__close {
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-md);
  padding: 2px 10px;
  font-size: var(--pyt-font-size-xs);
  cursor: pointer;
}

.companion__messages {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: var(--pyt-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.companion__hint {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
}
.msg {
  font-size: var(--pyt-font-size-sm);
  line-height: 1.5;
  max-width: 92%;
  padding: var(--pyt-space-2);
  border-radius: var(--pyt-radius-md);
}
.msg--user {
  align-self: flex-end;
  background: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
}
.msg--assistant {
  align-self: flex-start;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border-soft);
  color: var(--pyt-color-text-primary);
}
.msg__evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}
.msg__page {
  font-size: var(--pyt-font-size-xs);
  background: var(--pyt-color-bg-sunken);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-full);
  padding: 0 6px;
  color: var(--pyt-color-text-hint);
}
.msg__verified {
  display: inline-flex;
  align-items: center;
  margin-top: 4px;
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
  color: var(--pyt-color-success, #2e7d32);
  background: var(--pyt-color-success-bg, rgba(46, 125, 50, 0.12));
  border: 1px solid var(--pyt-color-success, #2e7d32);
  border-radius: var(--pyt-radius-full);
  padding: 0 8px;
}
.msg__verified::before {
  content: '✓';
  margin-right: 3px;
}

.companion__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--pyt-space-1);
  padding: 0 var(--pyt-space-3) var(--pyt-space-2);
}
.chip {
  font-size: var(--pyt-font-size-xs);
  font-weight: 600;
  padding: 3px 10px;
  border-radius: var(--pyt-radius-full);
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
  cursor: pointer;
}
.chip:hover {
  background: var(--pyt-color-warning-bg);
}

.companion__thumbs {
  display: flex;
  gap: var(--pyt-space-1);
  padding: 0 var(--pyt-space-3) var(--pyt-space-2);
}
.companion__thumb {
  width: 44px;
  height: 44px;
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-elevated);
  padding: 2px;
  cursor: pointer;
}
.companion__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  image-rendering: auto;
  border-radius: calc(var(--pyt-radius-md) - 2px);
}

.companion__compose {
  display: flex;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-2) var(--pyt-space-3) var(--pyt-space-3);
  border-top: 1px solid var(--pyt-color-border-soft);
}
.companion__tool {
  flex: 0 0 auto;
  min-width: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
  cursor: pointer;
}
.companion__file {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}
.companion__input {
  flex: 1 1 auto;
  min-width: 0;
  padding: 6px 10px;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-primary);
  color: var(--pyt-color-text-primary);
  font-size: var(--pyt-font-size-sm);
}
.companion__send {
  flex: 0 0 auto;
  padding: 6px 14px;
  border: 2px solid var(--pyt-color-primary);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
  font-weight: 700;
  cursor: pointer;
}
.companion__send:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .companion {
    right: 10px;
    bottom: 10px;
  }
}
</style>
