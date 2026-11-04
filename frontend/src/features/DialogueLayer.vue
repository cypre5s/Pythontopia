<template>
  <div
    ref="panel"
    class="dialogue-layer"
    role="dialog"
    aria-modal="true"
    aria-label="剧情对白"
    tabindex="-1"
    @keydown="navigateResponses"
  >
    <div class="dialogue-stage" aria-hidden="true">
    <img
      v-if="portrait"
      class="portrait"
      :src="portrait"
      alt=""
      @error="imageError = true"
    />
    </div>
    <section class="dialogue-box">
      <div class="dialogue-heading">
        <p class="eyebrow">{{ speaker }} <span>· 小镇居民</span></p>
        <button class="dialogue-skip" :disabled="busy || settling || skipping" title="快进对白，遇到选项停下" @click="skipDialogue">
          跳过 <GameIcon name="arrowRight" />
        </button>
      </div>
      <p class="dialogue-text" :aria-label="line?.text" @click="reveal">
        {{ displayed }}
      </p>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="settling" class="dialogue-saving" role="status">
        正在记录这次对话…
      </p>
      <div class="dialogue-actions" :aria-busy="busy || settling">
        <button
          v-for="(choice, index) in typing ? [] : (line?.choices ?? [])"
          :key="choice.id"
          class="dialogue-response"
          :disabled="busy || settling"
          @click="advance(choice.id)"
        >
          <span class="response-number" aria-hidden="true">{{ index + 1 }}.</span>
          <span>{{ choice.label ?? choice.text }}</span></button
        ><button
          v-if="typing || !line?.choices?.length"
          class="primary dialogue-response"
          :disabled="busy || settling"
          @click="advance()"
        >
          {{
            settling
              ? "记录中…"
              : typing
                ? "显示全文"
                : line?.next
                  ? "继续"
                  : "记下了"
          }}</button
        >
      </div>
      <div
        v-if="!typing && !line?.next && !line?.choices?.length && !settling"
        class="dialogue-objectives"
      >
        <button
          v-if="npc.key === 'librarian_doc'"
          @click="
            close();
            session.open({ kind: 'library' });
          "
        >
          浏览开放书籍 <GameIcon name="arrowRight"/>
        </button>
        <button
          v-for="item in actions"
          :key="item.node.id"
          @click="$emit('action', item.quest, item.node)"
        >
          {{ item.node.title }} <GameIcon name="arrowRight"/>
        </button>
      </div>
      <footer class="dialogue-footer">
        <span>↑ ↓ 选择 · Enter 确认</span>
        <button class="text-button" @click="close">稍后再聊 <kbd>Esc</kbd></button>
      </footer>
    </section>
  </div>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
import { computed, ref, watch, onBeforeUnmount } from "vue";
import type { Npc, Quest, QuestNode, Dialogue } from "../shared/types";
import { api } from "../shared/api";
import { useSession } from "../shared/session";
import { useModalFocus } from "../shared/modalFocus";
import { useUiPreferences } from "../shared/uiPreferences";
const props = defineProps<{
    npc: Npc;
    actions: { quest: Quest; node: QuestNode }[];
    settling?: boolean;
  }>(),
  emit = defineEmits<{
    close: [];
    complete: [choice?: string];
    action: [quest: Quest, node: QuestNode];
  }>(),
  session = useSession(),
  nodeId = ref(props.npc.dialogue[0]?.id ?? ""),
  displayed = ref(""),
  typing = ref(false),
  imageError = ref(false),
  busy = ref(false),
  skipping = ref(false),
  error = ref("");
const panel = ref<HTMLElement>();
const preferences = useUiPreferences();
useModalFocus(panel, () => props.npc.key, close);
const line = computed(
    () =>
      props.npc.dialogue.find((d) => d.id === nodeId.value) ??
      props.npc.dialogue[0],
  ),
  speaker = computed(() => line.value?.speaker ?? props.npc.name);
const portrait = computed(() =>
  imageError.value ? "" : (line.value?.portrait ?? props.npc.portrait),
);
let timer: ReturnType<typeof setInterval> | undefined,
  lastChoice: string | undefined;
let disposed = false,
  closed = false,
  requestGeneration = 0;
function close() {
  closed = true;
  requestGeneration++;
  emit("close");
}
function reveal() {
  clearInterval(timer);
  displayed.value = line.value?.text ?? "";
  typing.value = false;
}
function type() {
  clearInterval(timer);
  displayed.value = "";
  typing.value = true;
  let i = 0;
  const text = line.value?.text ?? "";
  if (preferences.reducedMotion || skipping.value) {
    reveal();
    return;
  }
  timer = setInterval(() => {
    displayed.value = text.slice(0, ++i);
    if (i >= text.length) reveal();
  }, 20);
}
async function skipDialogue() {
  if (busy.value || props.settling || skipping.value) return;
  skipping.value = true;
  const activeNpc = props.npc;
  const visited = new Set<string>();
  try {
    reveal();
    while (!closed && !disposed && props.npc === activeNpc && line.value?.next && !line.value.choices?.length) {
      const id = nodeId.value;
      if (visited.has(id)) break;
      visited.add(id);
      await advance();
      if (error.value || nodeId.value === id) break;
    }
  } finally {
    skipping.value = false;
  }
}
function navigateResponses(event: KeyboardEvent) {
  if (!['ArrowUp', 'ArrowDown'].includes(event.key) || event.altKey || event.ctrlKey || event.metaKey) return;
  const buttons = Array.from(panel.value?.querySelectorAll<HTMLButtonElement>('.dialogue-actions button:not(:disabled), .dialogue-objectives button:not(:disabled)') ?? []);
  if (!buttons.length) return;
  event.preventDefault();
  event.stopPropagation();
  const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
  const next = index < 0 ? 0 : (index + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length;
  buttons[next]?.focus();
}
async function advance(choice?: string) {
  if (busy.value || props.settling || disposed || closed) return;
  if (typing.value) {
    reveal();
    if (!choice) return;
  }
  if (!line.value) return;
  const generation = ++requestGeneration,
    activeNpc = props.npc,
    userId = session.user?.id,
    classroomId = session.classroomId;
  const current = () =>
    !disposed &&
    !closed &&
    generation === requestGeneration &&
    props.npc === activeNpc &&
    session.user?.id === userId &&
    session.classroomId === classroomId;
  busy.value = true;
  error.value = "";
  try {
    const result = await api.post<{
      node: Dialogue;
      next_id: string | null;
      completed_root: string | null;
      effects: { type: string; activity_id: string; kind: string }[];
    }>(`/dialogues/${props.npc.key}/steps`, {
      classroom_id: session.classroomId,
      node_id: line.value.id,
      ...(choice ? { choice } : {}),
    });
    if (!current()) return;
    if (choice) lastChoice = choice;
    if (result.next_id) {
      nodeId.value = result.next_id;
      type();
    } else emit("complete", lastChoice);
    for (const effect of result.effects) {
      if (!current()) return;
      if (effect.type === "open_activity") {
        session.open({
          kind:
            effect.kind === "practice" || effect.kind === "assessment"
              ? "practice"
              : "lesson",
          activity_id: effect.activity_id,
        });
        close();
        break;
      }
    }
  } catch (e) {
    if (current())
      error.value = e instanceof Error ? e.message : "对白推进失败";
  } finally {
    if (generation === requestGeneration) busy.value = false;
  }
}
watch(
  () => props.npc,
  () => {
    requestGeneration++;
    closed = false;
    busy.value = false;
    nodeId.value = props.npc.dialogue[0]?.id ?? "";
    imageError.value = false;
    lastChoice = undefined;
    type();
  },
  { flush: "sync" },
);
watch(
  [() => session.user?.id, () => session.classroomId],
  () => {
    requestGeneration++;
    closed = true;
    busy.value = false;
  },
  { flush: "sync" },
);
watch(nodeId, type, { immediate: true });
onBeforeUnmount(() => {
  disposed = true;
  requestGeneration++;
  clearInterval(timer);
});
</script>

<style scoped>
.dialogue-layer {
  position: fixed;
  inset: 0;
  transform: none;
  width: 100%;
  max-height: none;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  padding: var(--safe-top) var(--safe-right) var(--safe-bottom) var(--safe-left);
  background: linear-gradient(180deg, #101b1810 15%, #101b1850 55%, #101b18e8 100%);
  pointer-events: auto;
}
.dialogue-stage {
  width: min(1080px, 100%);
  min-height: 0;
  flex: 0 1 auto;
  display: flex;
  padding-left: clamp(12px, 3vw, 40px);
  pointer-events: none;
  overflow: hidden;
}
.dialogue-layer .portrait {
  display: block;
  width: auto;
  height: min(48dvh, 512px);
  max-width: 46vw;
  max-height: 100%;
  object-fit: contain;
  object-position: left bottom;
  filter: drop-shadow(0 6px 12px #0008);
}
.dialogue-box {
  flex: 0 1 auto;
  width: min(1080px, 100%);
  max-height: calc(100dvh - var(--safe-top) - var(--safe-bottom));
  min-height: 0;
  padding: clamp(16px, 2vw, 28px) clamp(20px, 4vw, 56px) 12px;
  overflow: auto;
  border: 1px solid #bfa26d80;
  border-top: 2px solid #d6b979;
  border-radius: 3px;
  background: linear-gradient(115deg, #152a27fa, #142321fa);
  color: #f5efdf;
  box-shadow: 0 16px 60px #0006;
}
.dialogue-heading {
  border: 0;
  padding: 0;
  margin-bottom: 12px;
  gap: 12px;
}
.dialogue-box .eyebrow {
  margin: 0;
  color: #f0cc85;
  font-size: 1.125rem;
  letter-spacing: .06em;
}
.dialogue-box .eyebrow span { color: #b8c2b8; font-size: .8rem; }
.dialogue-skip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  min-height: 44px;
  padding: 8px 12px;
  border: 1px solid #a9946655;
  background: transparent;
  color: #ded1ac;
  font-size: .9rem;
}
.dialogue-text {
  margin: 0 0 18px;
  min-height: 2.8em;
  font-size: 1.125rem;
  line-height: 1.8;
  color: #f5efdf;
}
.dialogue-actions, .dialogue-objectives {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
  margin: 0;
  padding: 12px 0 0;
  border-top: 1px solid #c9ad7140;
}
.dialogue-actions button, .dialogue-objectives button {
  display: flex;
  align-items: baseline;
  justify-content: flex-start;
  gap: 12px;
  width: 100%;
  min-height: 44px;
  padding: 10px 14px;
  border: 1px solid transparent;
  border-radius: 2px;
  background: transparent;
  color: #e8dfc8;
  text-align: left;
  font-size: 1rem;
  line-height: 1.6;
}
.response-number { color: #c5a86b; min-width: 1.2em; }
.dialogue-actions button:hover, .dialogue-actions button:focus-visible,
.dialogue-objectives button:hover, .dialogue-objectives button:focus-visible {
  color: #ffe3a5;
  background: linear-gradient(90deg, #c7a45526, #c7a45505);
  border-color: #c7a45570;
  box-shadow: inset 3px 0 #e6c381;
}
.dialogue-box button:focus-visible { outline: 2px solid #f4d594; outline-offset: 2px; }
.dialogue-box button:disabled { opacity: .5; cursor: wait; }
.dialogue-saving { color: #c8d1c1; }
.dialogue-box .error { color: #ffb8a8; }
.dialogue-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  color: #aeb9ad;
  font-size: .75rem;
}
.dialogue-footer button { min-height: 44px; color: #c5cdbf; background: transparent; border: 0; }
.dialogue-footer kbd { border-color: #acb49c70; color: inherit; }
@media (max-width: 600px) {
  .dialogue-layer .portrait { height: min(32dvh, 280px); max-width: 55vw; }
  .dialogue-box { padding: 14px 16px 8px; }
  .dialogue-text { font-size: 1rem; }
  .dialogue-box .eyebrow span, .dialogue-footer > span { display: none; }
  .dialogue-footer { justify-content: flex-end; }
}
@media (max-height: 540px) {
  .dialogue-layer .portrait { height: 25dvh; }
  .dialogue-box { max-height: 70dvh; padding: 10px 20px 6px; }
  .dialogue-heading { margin-bottom: 4px; }
  .dialogue-text { min-height: 0; margin-bottom: 8px; }
  .dialogue-footer { margin-top: 0; }
}
</style>
