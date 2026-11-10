<template>
  <main class="world-page">
    <div v-if="loading" class="screen-message" :class="{ 'arrival-blackout': welcome }">正在连接小镇存档…</div>
    <section v-else-if="error" class="screen-message">
      <h1>暂时无法进入小镇</h1>
      <p>{{ error }}</p>
      <button @click="load">重试</button
      ><button @click="logout">返回入场</button>
    </section>
    <template v-else-if="session.classroomId && (session.world || session.resetBusy)"
      ><WorldCanvas
        v-if="session.world"
        :key="session.classroomId"
        ref="canvas"
        :paused="Boolean(session.workspace || dialogueNpc || activeStory?.node || welcome || session.storyBusy || session.resetBusy)"
        @ready="onWorldReady"
        @position="positionChanged"
        @interact="interact" /><GameHUD
        v-if="!welcome"
        :blocked="Boolean(session.workspace || dialogueNpc || activeStory?.node || session.storyBusy || session.resetBusy)"
        :class="{ 'is-entering': entered }"
        @guide="guide" /><DialogueLayer
        v-if="!silentAction && (dialogueNpc || activeStory?.node)"
        :npc="dialogueNpc"
        :story="activeStory"
        :actions="actions"
        :settling="dialogueSaving"
        @close="closeDialogue"
        @complete="finishDialogue"
        @action="action" />
      <div v-if="silentAction && !welcome" class="world-action-status">
        <template v-if="automaticActionError">
          <p role="alert">{{ automaticActionError }}</p>
          <button type="button" :disabled="session.storyBusy" @click="runAutomaticAction">重试操作</button>
          <button type="button" :disabled="session.storyBusy" @click="endAutomaticAction">结束本次互动</button>
        </template>
        <p v-else role="status">正在继续互动…</p>
      </div>
      <OverlayHost @close="closeWorkspace" @complete="closeWorkspace"
    /></template>
    <section v-else class="arrival-page">

      <strong class="arrival-brand">PYTHONTOPIA</strong>
      <div class="arrival-panel">
      <p class="eyebrow">居民证已就绪</p>
      <h1>先加入你的班级</h1>
      <p>向教师领取加入码，开放的章节会出现在小镇里。</p>
      <form @submit.prevent="join">
        <input
          v-model="joinCode"
          aria-label="班级加入码"
          placeholder="输入加入码"
          required
        /><button class="primary">加入班级</button>
      </form>
      <button class="text-button" @click="logout">退出账号</button>
      </div>
    </section>
    <ArrivalWelcome v-if="welcome && !proceeding && session.world && session.classroomId && !loading && !error" :ready="worldReady && backdropReady" :name="session.user?.name ?? ''" @continue="finishArrival" />
  </main>
</template>
<script setup lang="ts">
import {
  computed,
  nextTick,
  onMounted,
  onBeforeUnmount,
  ref,
  watch,
} from "vue";
import { useRouter } from "vue-router";
import WorldCanvas from "./WorldCanvas.vue";
import GameHUD from "./GameHUD.vue";
import DialogueLayer from "./DialogueLayer.vue";
import OverlayHost from "./OverlayHost.vue";
import { useSession } from "../shared/session";
import { api, notify } from "../shared/api";
import type { Npc, Player, Quest, QuestNode } from "../shared/types";
import { isTextFocus } from "../game/pathfinding";
import { resolveWorldTarget } from "../game/config/worldConfig";
import { arrivalActive, arrivalCamera, arrivalFinished, backdropReady, startArrival, endArrival, type ArrivalCamera } from "../shared/arrival";
import ArrivalWelcome from "./ArrivalWelcome.vue";
const welcome = ref(true);
if (!arrivalActive.value) startArrival();
const worldReady = ref(false);
const entered = ref(false);
const proceeding = ref(false);
const playerCamera = ref<ArrivalCamera | null>(null);
function onWorldReady(camera: ArrivalCamera) {
  playerCamera.value = camera;
  worldReady.value = true;
}
watch(arrivalFinished, value => {
  if (!value || !proceeding.value) return;
  entered.value = true;
  welcome.value = false;
  sessionStorage.removeItem("reborn-arrival");
});
function finishArrival() {
  if (proceeding.value || !playerCamera.value || !backdropReady.value) return;
  proceeding.value = true;
  arrivalCamera.value = playerCamera.value;
}
const session = useSession(),
  router = useRouter(),
  loading = ref(true),
  error = ref(""),
  npc = ref<Npc | null>(null),
  dialogueSaving = ref(false),
  joinCode = ref(""),
  canvas = ref<InstanceType<typeof WorldCanvas>>(),
  activityNpcs = ref<Record<string, string>>({});
let reachBusy = false;
let reachTask: Promise<void> = Promise.resolve();
let unregisterRestart: (() => void) | undefined;
watch(() => session.restartCompleted, () => {
  loading.value = true;
  window.location.reload();
}, { flush: "sync" });
const actions = computed(
  () =>
    session.world?.quests.flatMap((quest) =>
      quest.nodes
        .filter(
          (node) =>
            node.status === "ready" &&
            node.activity_id &&
            activityNpcs.value[node.activity_id] === npc.value?.key,
        )
        .map((node) => ({ quest, node })),
    ) ?? [],
);
const activeStory = computed(() =>
  session.story?.status === "active" && !session.storyDismissed && !session.workspace ? session.story : undefined,
);
const silentAction = computed(() => activeStory.value?.node?.type === "action" && !activeStory.value.node.text
  ? `${activeStory.value.id}:${activeStory.value.revision}`
  : null);
const automaticActionError = ref("");
let requestedAction: string | null = null;
async function runAutomaticAction() {
  const key = silentAction.value;
  if (!key || session.storyBusy) return;
  automaticActionError.value = "";
  try {
    await session.advanceStory();
  } catch (failure) {
    if (silentAction.value === key)
      automaticActionError.value = failure instanceof Error ? failure.message : "互动未完成，请重试。";
  }
}
async function endAutomaticAction() {
  try {
    await session.endStory();
  } catch (failure) {
    automaticActionError.value = failure instanceof Error ? failure.message : "未能结束互动，请重试。";
  }
}
watch([silentAction, welcome, loading, () => session.storyBusy], ([key, entering, connecting, busy]) => {
  if (!key) {
    requestedAction = null;
    return;
  }
  if (entering || connecting || busy || key === requestedAction) return;
  requestedAction = key;
  void runAutomaticAction();
}, { immediate: true });
const dialogueNpc = computed(() => {
  if (!activeStory.value?.node) return npc.value;
  const resident = session.world?.npcs.find(n => n.key === activeStory.value?.npc_key);
  return resident ? { ...resident, dialogue: [activeStory.value.node] } : null;
});
function closeDialogue() {
  npc.value = null;
  session.dismissStory();
}
async function closeWorkspace() {
  try {
    if (session.story?.status === "waiting") await session.returnStoryActivity();
    else session.open(null);
  } catch (error) {
    notify(error instanceof Error ? error.message : "活动进度保存失败，请重试。");
  }
}
watch(() => session.navigationTarget, async target => {
  if (!target) return;
  session.navigationTarget = null;
  await guide(target);
});
watch(
  () => session.workspace,
  (workspace) => {
    if (workspace) npc.value = null;
  },
  { flush: "sync" },
);
watch(
  () => session.world,
  async (world) => {
    for (const id of new Set(
      world?.quests.flatMap((q) =>
        q.nodes
          .filter((n) => n.status === "ready" && n.activity_id)
          .map((n) => n.activity_id!),
      ) ?? [],
    )) {
      if (activityNpcs.value[id]) continue;
      try {
        activityNpcs.value[id] = (await session.activity(id)).npc_key;
      } catch {
        /* Unpublished content remains unavailable. */
      }
    }
  },
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    if (!session.world) await session.load();
    await session.restoreStory();
    if (!session.classroomId) endArrival();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "连接失败";
  } finally {
    loading.value = false;
  }
}
async function join() {
  try {
    await api.post("/classrooms/join", { join_code: joinCode.value });
    worldReady.value = false;
    startArrival();
    await session.load();
    await load();
  } catch (e) {
    endArrival();
    notify(e instanceof Error ? e.message : "加入失败");
  }
}
async function logout() {
  try {
    await api.post("/auth/logout", {});
  } finally {
    sessionStorage.removeItem("reborn-arrival");
    session.reset();
    await router.push("/");
  }
}
async function guide(key: string) {
  session.open(null);
  npc.value = null;
  session.dismissStory();
  await nextTick();
  canvas.value?.guide(key);
}
async function positionChanged(player: Player) {
  if (session.resetBusy) return;
  if (session.world) session.world.player = player;
  if (reachBusy) return;
  reachBusy = true;
  reachTask = (async () => {
  try {
    for (const quest of session.world?.quests ?? [])
      for (const node of quest.nodes) {
        const target = resolveWorldTarget(node.target, session.world?.npcs ?? [], session.world);
        if (
          node.status === "ready" &&
          node.type === "REACH_LOCATION" &&
          target &&
          player.map_key === target.map_key &&
          Math.abs(player.tile_x - target.x) +
            Math.abs(player.tile_y - target.y) <=
            3
        )
          await session.event(quest, node, {
            map_key: player.map_key,
            tile_x: player.tile_x,
            tile_y: player.tile_y,
          });
      }
  } catch (e) {
    notify(e instanceof Error ? e.message : "到达记录失败");
  } finally {
    reachBusy = false;
  }
  })();
  await reachTask;
}
async function interact(target: string, type: "npc" | "object") {
  if (type === "npc") {
    session.open(null);
    try {
      if (await session.interactStory(target)) return;
    } catch (error) {
      notify(error instanceof Error ? error.message : "无法读取这段对话，请重试。");
      return;
    }
    const resident = session.world?.npcs.find((n) => n.key === target);
    npc.value = resident ? { ...resident } : null;
    return;
  }
  if (session.world?.interactions !== undefined) {
    try { await session.interactStory(target, "object"); }
    catch (error) { notify(error instanceof Error ? error.message : "互动未完成，请重试。"); }
    return;
  }
  try {
    for (const quest of session.world?.quests ?? [])
      for (const node of quest.nodes)
        if (
          node.status === "ready" &&
          node.type === "INTERACT_OBJECT" &&
          node.target === target
        )
          await session.event(quest, node, {
            object_id: target,
            map_key: session.world?.player.map_key,
          });
  } catch (e) {
    notify(e instanceof Error ? e.message : "互动未完成");
  }
  if (target === "library_shelf") session.open({ kind: "library" });
}
async function finishDialogue(choice?: string) {
  if (!npc.value || dialogueSaving.value) return;
  const activeNpc = npc.value;
  const key = activeNpc.key;
  dialogueSaving.value = true;
  try {
    for (const quest of [...(session.world?.quests ?? [])])
      for (const node of quest.nodes) {
        if (node.status !== "ready") continue;
        if (
          (node.type === "TALK_TO_NPC" || node.type === "REPORT_BACK") &&
          node.target === key
        )
          await session.event(quest, node, { npc_key: key });
        else if (
          node.type === "PLAY_DIALOGUE" &&
          node.target === key &&
          (!node.choices?.length || choice)
        )
          await session.event(quest, node, {
            npc_key: key,
            dialogue_id: node.dialogue_id ?? node.target,
            choice,
          });
      }
    await session.refresh();
  } catch (e) {
    notify(e instanceof Error ? e.message : "对白进度保存失败");
  }
  if (npc.value === activeNpc) npc.value = null;
  dialogueSaving.value = false;
}
function action(_quest: Quest, node: QuestNode) {
  npc.value = null;
  if (node.activity_id)
    session.open({
      kind: node.type === "STUDY_LESSON" ? "lesson" : "practice",
      activity_id: node.activity_id,
    });
}
function worldShortcut(event: KeyboardEvent) {
  if (
    event.defaultPrevented ||
    event.isComposing ||
    event.repeat ||
    event.ctrlKey ||
    event.metaKey ||
    event.altKey ||
    isTextFocus(document.activeElement) ||
    welcome.value ||
    session.workspace ||
    dialogueNpc.value || activeStory.value?.node ||
    session.storyBusy ||
    session.resetBusy ||
    !session.world
  )
    return;
  if (event.key.toLowerCase() === "j" || event.key === "Escape") {
    event.preventDefault();
    session.open({ kind: event.key === "Escape" ? "settings" : "journal" });
  }
}
onMounted(() => {
  unregisterRestart = session.registerRestartPreparation(async () => {
    await canvas.value?.flushSaves();
    await reachTask;
  });
  void load();
  window.addEventListener("keydown", worldShortcut);
});
onBeforeUnmount(() => {
  unregisterRestart?.();
  window.removeEventListener("keydown", worldShortcut);
});
</script>

<style scoped>
.world-action-status {
  position: absolute;
  z-index: 30;
  bottom: max(24px, var(--safe-bottom));
  left: 50%;
  width: max-content;
  max-width: calc(100% - 32px);
  transform: translateX(-50%);
  padding: 12px 16px;
  border: 1px solid #af8b50;
  border-radius: 8px;
  color: #f0dfbb;
  background: #30291f;
}
.world-action-status p { margin: 0; }
.world-action-status button { min-height: 44px; margin: 8px 8px 0 0; }
</style>
