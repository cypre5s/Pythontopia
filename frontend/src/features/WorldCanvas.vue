<template>
  <div ref="root" class="world-canvas" aria-label="Pythontopia 像素小镇">
    <div v-if="progress < 1" class="world-loading">
      正在进入小镇 {{ Math.round(progress * 100) }}%
    </div>
  </div>
  <div class="touch-controls">
    <button
      aria-label="向上"
      @pointerdown="direction('arrowup', true)"
      @pointerup="direction('arrowup', false)"
      @pointerleave="direction('arrowup', false)"
    >
      ↑
    </button>
    <div>
      <button
        aria-label="向左"
        @pointerdown="direction('arrowleft', true)"
        @pointerup="direction('arrowleft', false)"
        @pointerleave="direction('arrowleft', false)"
      >
        ←</button
      ><button aria-label="互动" @click="game?.interact()">E</button
      ><button
        aria-label="向右"
        @pointerdown="direction('arrowright', true)"
        @pointerup="direction('arrowright', false)"
        @pointerleave="direction('arrowright', false)"
      >
        →
      </button>
    </div>
    <button
      aria-label="向下"
      @pointerdown="direction('arrowdown', true)"
      @pointerup="direction('arrowdown', false)"
      @pointerleave="direction('arrowdown', false)"
    >
      ↓
    </button>
  </div>
  <p class="world-hint">{{ hint }}</p>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from "vue";
import type { Player } from "../shared/types";
import { useSession } from "../shared/session";
import { api, notify } from "../shared/api";
import type { WorldHandle } from "../game/adapters/world";
const props = defineProps<{ paused: boolean }>(),
  emit = defineEmits<{
    interact: [target: string, type: "npc" | "object"];
    position: [player: Player];
  }>();
const session = useSession(),
  root = ref<HTMLElement>(),
  progress = ref(0),
  hint = ref("");
let game: WorldHandle | undefined,
  lastPosition = "";
function direction(key: string, down: boolean) {
  game?.direction(key, down);
}
let saveTask: Promise<void> = Promise.resolve();
const contextClassroomId = session.classroomId,
  contextUserId = session.user?.id,
  saveController = new AbortController();
let disposed = false;
function save(player: Player) {
  const key = JSON.stringify(player);
  if (key === lastPosition) return saveTask;
  lastPosition = key;
  saveTask = saveTask
    .then(async () => {
      if (
        disposed ||
        session.classroomId !== contextClassroomId ||
        session.user?.id !== contextUserId
      )
        return;
      await api.request("/world/position", {
        method: "PUT",
        signal: saveController.signal,
        body: JSON.stringify({
          classroom_id: contextClassroomId,
          ...player,
        }),
      });
      emit("position", player);
    })
    .catch((e) => {
      lastPosition = "";
      if (!disposed) notify(e instanceof Error ? e.message : "位置保存失败");
    });
  return saveTask;
}
onMounted(async () => {
  if (!session.world || !root.value) return;
  const { createWorld } = await import("../game/adapters/world");
  if (!root.value) return;
  game = createWorld(
    root.value,
    session.world.player,
    session.world.npcs,
    session.world.flags,
    {
      position: save,
      interact: async (id, type) => {
        await saveTask;
        emit("interact", id, type);
      },
      hint: (value) => (hint.value = value),
      progress: (value) => (progress.value = value),
      error: notify,
    },
  );
  game.setPaused(props.paused);
});
watch(
  () => session.audioEnabled,
  (v) => game?.mute(!v),
);
watch(
  () => session.world?.npcs,
  (v) => {
    if (v) game?.setNpcs(v);
  },
);
watch(
  () => props.paused,
  (v) => game?.setPaused(v),
);
watch(
  () => session.world?.flags,
  (v) => {
    if (v) game?.setFlags(v);
  },
);
onBeforeUnmount(() => {
  disposed = true;
  saveController.abort();
  game?.destroy();
  game = undefined;
});
defineExpose({ guide: (key: string) => game?.guide(key) });
</script>
