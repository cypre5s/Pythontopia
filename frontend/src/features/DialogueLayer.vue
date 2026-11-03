<template>
  <div
    ref="panel"
    class="dialogue-layer"
    role="dialog"
    aria-modal="true"
    aria-label="剧情对白"
    tabindex="-1"
  >
    <img
      v-if="portrait"
      class="portrait"
      :src="portrait"
      alt=""
      @error="imageError = true"
    />
    <section class="dialogue-box">
      <div class="dialogue-heading">
        <p class="eyebrow">{{ speaker }} <span>· 小镇居民</span></p>
        <span class="dialogue-caption">小镇对话</span>
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
          v-for="choice in line?.choices ?? []"
          :key="choice.id"
          :disabled="busy || settling"
          @click="advance(choice.id)"
        >
          {{ choice.label ?? choice.text }}</button
        ><button
          v-if="!line?.choices?.length"
          class="primary"
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
        ><button class="text-button" @click="close">
          稍后再聊 <kbd>Esc</kbd>
        </button>
      </div>
      <div
        v-if="!typing && !line?.next && !settling"
        class="dialogue-objectives"
      >
        <button
          v-if="npc.key === 'librarian_doc'"
          @click="
            close();
            session.open({ kind: 'library' });
          "
        >
          浏览开放书籍 →
        </button>
        <button
          v-for="item in actions"
          :key="item.node.id"
          @click="$emit('action', item.quest, item.node)"
        >
          {{ item.node.title }} →
        </button>
      </div>
    </section>
  </div>
</template>
<script setup lang="ts">
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
  if (preferences.reducedMotion) {
    reveal();
    return;
  }
  timer = setInterval(() => {
    displayed.value = text.slice(0, ++i);
    if (i >= text.length) reveal();
  }, 20);
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
