<template>
  <div class="dialogue-layer" role="dialog" aria-label="剧情对白">
    <img
      v-if="portrait"
      class="portrait"
      :src="portrait"
      :alt="speaker"
      @error="imageError = true"
    />
    <section class="dialogue-box">
      <p class="eyebrow">{{ speaker }} <span>· 小镇居民</span></p>
      <p class="dialogue-text" @click="reveal">{{ displayed }}</p>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="dialogue-actions">
        <button
          v-for="choice in line?.choices ?? []"
          :key="choice.id"
          :disabled="busy"
          @click="advance(choice.id)"
        >
          {{ choice.label ?? choice.text }}</button
        ><button
          v-if="!line?.choices?.length"
          class="primary"
          :disabled="busy"
          @click="advance()"
        >
          {{ typing ? "显示全文" : line?.next ? "继续" : "记下了" }}</button
        ><button class="text-button" @click="$emit('close')">稍后再聊</button>
      </div>
      <div v-if="!typing && !line?.next" class="dialogue-objectives">
        <button
          v-if="npc.key === 'librarian_doc'"
          @click="
            $emit('close');
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
const props = defineProps<{
    npc: Npc;
    actions: { quest: Quest; node: QuestNode }[];
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
  timer = setInterval(() => {
    displayed.value = text.slice(0, ++i);
    if (i >= text.length) reveal();
  }, 20);
}
async function advance(choice?: string) {
  if (typing.value) {
    reveal();
    return;
  }
  if (!line.value) return;
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
    if (choice) lastChoice = choice;
    if (result.next_id) {
      nodeId.value = result.next_id;
      type();
    } else emit("complete", lastChoice);
    for (const effect of result.effects) {
      if (effect.type === "open_activity") {
        session.open({
          kind:
            effect.kind === "practice" || effect.kind === "assessment"
              ? "practice"
              : "lesson",
          activity_id: effect.activity_id,
        });
        emit("close");
      }
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : "对白推进失败";
  } finally {
    busy.value = false;
  }
}
watch(
  () => props.npc.key,
  () => {
    nodeId.value = props.npc.dialogue[0]?.id ?? "";
    imageError.value = false;
    lastChoice = undefined;
    type();
  },
);
watch(nodeId, type, { immediate: true });
onBeforeUnmount(() => clearInterval(timer));
</script>
