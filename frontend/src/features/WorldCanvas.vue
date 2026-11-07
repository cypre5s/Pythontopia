<template>
  <div ref="root" class="world-canvas" tabindex="-1" aria-label="Pythontopia 像素小镇">
    <div v-if="progress < 1" class="world-loading">
      正在进入小镇 {{ Math.round(progress * 100) }}%
    </div>
  </div>
  <div v-show="!paused" class="touch-controls" aria-label="小镇触摸控制">
    <button
      aria-label="向上"
      @pointerdown="direction('arrowup', true)"
      @pointerup="direction('arrowup', false)"
      @pointerleave="direction('arrowup', false)"
      @pointercancel="direction('arrowup', false)"
      @lostpointercapture="direction('arrowup', false)"
    >
      <GameIcon name="arrowUp"/>
    </button>
    <div>
      <button
        aria-label="向左"
        @pointerdown="direction('arrowleft', true)"
        @pointerup="direction('arrowleft', false)"
        @pointerleave="direction('arrowleft', false)"
        @pointercancel="direction('arrowleft', false)"
        @lostpointercapture="direction('arrowleft', false)"
      >
        <GameIcon name="arrowLeft"/></button
      ><button
        class="touch-interact"
        aria-label="互动"
        @click="game?.interact()"
      >
        互动</button
      ><button
        aria-label="向右"
        @pointerdown="direction('arrowright', true)"
        @pointerup="direction('arrowright', false)"
        @pointerleave="direction('arrowright', false)"
        @pointercancel="direction('arrowright', false)"
        @lostpointercapture="direction('arrowright', false)"
      >
        <GameIcon name="arrowRight"/>
      </button>
    </div>
    <button
      aria-label="向下"
      @pointerdown="direction('arrowdown', true)"
      @pointerup="direction('arrowdown', false)"
      @pointerleave="direction('arrowdown', false)"
      @pointercancel="direction('arrowdown', false)"
      @lostpointercapture="direction('arrowdown', false)"
    >
      <GameIcon name="arrowDown"/>
    </button>
  </div>
  <p v-show="!paused && displayHint" class="world-hint">{{ displayHint }}</p>
</template>
<script setup lang="ts">
import { useUiPreferences } from "../shared/uiPreferences";
import GameIcon from "../shared/GameIcon.vue";
import { computed, onMounted, onBeforeUnmount, ref, watch } from "vue";
import type { Player, World } from "../shared/types";
import { useSession } from "../shared/session";
import { api, ApiError, notify } from "../shared/api";
import type { WorldHandle } from "../game/adapters/world";
const props = defineProps<{ paused: boolean; welcomeDrift?: boolean }>(),
  emit = defineEmits<{
    interact: [target: string, type: "npc" | "object"];
    position: [player: Player];
    ready: [view: { x: number; y: number; zoom: number; map: string }];
  }>();
const preferences = useUiPreferences();
const session = useSession(),
  root = ref<HTMLElement>(),
  progress = ref(0),
  hint = ref("");
const touchInput = ref(false);
let pointerMedia: MediaQueryList | undefined;
function updatePointer() {
  touchInput.value = pointerMedia?.matches ?? false;
}
const displayHint = computed(() =>
  touchInput.value
    ? hint.value
        .replace(/^E · /, "互动 · ")

    : hint.value,
);
let game: WorldHandle | undefined,
  lastPosition = "";
function direction(key: string, down: boolean) {
  if (props.paused) return;
  game?.direction(key, down);
}
let saveTask: Promise<void> = Promise.resolve();
const contextClassroomId = session.classroomId,
  contextUserId = session.user?.id,
  saveController = new AbortController();
let disposed = false,
  interactionGeneration = 0;
let contextGeneration = 0;
watch([() => session.classroomId, () => session.user?.id], () => { contextGeneration++; }, { flush: "sync" });
function currentContext() {
  return (
    !disposed &&
    contextGeneration === 0 &&
    session.classroomId === contextClassroomId &&
    session.user?.id === contextUserId
  );
}
function save(player: Player) {
  const key = JSON.stringify(player);
  if (key === lastPosition) return saveTask;
  lastPosition = key;
  saveTask = saveTask
    .then(async () => {
      if (!currentContext()) return;
      await api.request("/world/position", {
        method: "PUT",
        signal: saveController.signal,
        body: JSON.stringify({
          classroom_id: contextClassroomId,
          ...player,
        }),
      });
      if (currentContext()) emit("position", player);
    })
    .catch((e) => {
      lastPosition = "";
      if (currentContext())
        notify(e instanceof Error ? e.message : "位置保存失败");
    });
  return saveTask;
}
function snapshotMapPosition() {
  const player = game?.getMapPosition?.();
  if (player && currentContext()) session.setMapPosition(player);
}
watch(() => session.workspace, workspace => {
  if (workspace?.kind === "map") snapshotMapPosition();
}, { flush: "sync" });
onMounted(async () => {
  pointerMedia = window.matchMedia("(pointer: coarse)");
  updatePointer();
  pointerMedia.addEventListener("change", updatePointer);
  if (!session.world || !root.value) return;
  const { createWorld } = await import("../game/adapters/world");
  if (!root.value || !currentContext() || !session.world) return;
  game = createWorld(
    root.value,
    session.world.player,
    session.world.npcs,
    session.world.flags,
    {
      position: save,
      mapPosition: player => { if (currentContext()) session.setMapPosition(player); },
      transition: async (portalId, source) => {
        if (!currentContext()) return null;
        await save(source);
        if (!currentContext() || lastPosition !== JSON.stringify(source)) return null;
        try {
          const result = await api.post<{ player: Player; world: World }>("/world/transition", { classroom_id: contextClassroomId, portal_id: portalId });
          if (!currentContext()) return null;
          lastPosition = JSON.stringify(result.player);
          session.world = result.world;
          session.setMapPosition(result.player);
          emit("position", result.player);
          return result.player;
        } catch (error) {
          if (!currentContext()) return null;
          if (!(error instanceof ApiError && error.status >= 400 && error.status < 500)) {
            try {
              const authoritative = await api.get<World>(`/world?classroom_id=${contextClassroomId}`);
              if (!currentContext()) return null;
              if (authoritative.player.map_key !== source.map_key) {
                lastPosition = JSON.stringify(authoritative.player);
                session.world = authoritative;
                session.setMapPosition(authoritative.player);
                emit("position", authoritative.player);
                return authoritative.player;
              }
            } catch { /* Never replay an uncertain transition; a reload can restore the saved map. */ }
          }
          if (currentContext()) notify(error instanceof Error ? error.message : "地图同步中断，请刷新后继续。");
          return null;
        }
      },
      interact: async (id, type) => {
        const generation = interactionGeneration;
        if (!currentContext() || props.paused || session.workspace) return;
        await saveTask;
        if (
          !currentContext() ||
          props.paused ||
          session.workspace ||
          generation !== interactionGeneration
        )
          return;
        interactionGeneration++;
        emit("interact", id, type);
      },
      hint: (value) => (hint.value = value),
      progress: (value) => { progress.value = value; },
      cameraReady: (view) => emit("ready", view),
      error: notify,
    },
  );
  game.setPaused(props.paused);
  game.setVolume(preferences.musicVolume);
  game.mute(!session.audioEnabled);
  game.setWelcomeDrift?.(Boolean(props.welcomeDrift) && !preferences.reducedMotion);
});
watch(
  () => session.audioEnabled,
  (v) => game?.mute(!v),
);
watch(() => preferences.musicVolume, volume => game?.setVolume(volume));
watch(
  () => session.world?.npcs,
  (v) => {
    if (v) game?.setNpcs(v);
  },
);
watch(
  () => props.paused,
  (v) => { snapshotMapPosition(); game?.setPaused(v); },
);
watch(
  [
    () => props.paused,
    () => session.workspace,
    () => session.classroomId,
    () => session.user?.id,
  ],
  () => {
    interactionGeneration++;
  },
  { flush: "sync" },
);
watch(
  () => session.world?.flags,
  (v) => {
    if (v) game?.setFlags(v);
  },
);
onBeforeUnmount(() => {
  if (currentContext()) session.clearMapPosition();
  pointerMedia?.removeEventListener("change", updatePointer);
  disposed = true;
  saveController.abort();
  game?.destroy();
  game = undefined;
});
watch(() => Boolean(props.welcomeDrift) && !preferences.reducedMotion, value => game?.setWelcomeDrift?.(value));
defineExpose({ guide: (key: string) => game?.guide(key) });
</script>
