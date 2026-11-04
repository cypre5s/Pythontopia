<template>
  <main class="world-page">
    <div v-if="loading" class="screen-message" :class="{ 'arrival-blackout': welcome }">正在连接小镇存档…</div>
    <section v-else-if="error" class="screen-message">
      <h1>暂时无法进入小镇</h1>
      <p>{{ error }}</p>
      <button @click="load">重试</button
      ><button @click="logout">返回入场</button>
    </section>
    <template v-else-if="session.classroomId && session.world"
      ><WorldCanvas
        :key="session.classroomId"
        ref="canvas"
        :paused="Boolean(session.workspace || npc || welcome)"
        @ready="onWorldReady"
        @position="positionChanged"
        @interact="interact" /><GameHUD
        v-if="!welcome"
        :blocked="Boolean(session.workspace || npc)"
        :class="{ 'is-entering': entered }"
        @guide="guide" /><DialogueLayer
        v-if="dialogueNpc"
        :npc="dialogueNpc"
        :actions="actions"
        :settling="dialogueSaving"
        @close="npc = null"
        @complete="finishDialogue"
        @action="action" /><OverlayHost @close="session.open(null)"
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
const dialogueNpc = computed(() => npc.value);
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
  await nextTick();
  canvas.value?.guide(key);
}
async function positionChanged(player: Player) {
  if (session.world) session.world.player = player;
  if (reachBusy) return;
  const targetPositions: Record<
    string,
    { map_key: string; x: number; y: number }
  > = {
    practice_plaza: { map_key: "town_hub", x: 38, y: 24 },
    library_door: { map_key: "town_hub", x: 8, y: 22 },
    town_square: { map_key: "town_hub", x: 22, y: 34 },
    library_shelf: { map_key: "library", x: 4, y: 4 },
  };
  reachBusy = true;
  try {
    for (const quest of session.world?.quests ?? [])
      for (const node of quest.nodes) {
        const target = targetPositions[node.target];
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
}
async function interact(target: string, type: "npc" | "object") {
  if (type === "npc") {
    session.open(null);
    const resident = session.world?.npcs.find((n) => n.key === target);
    npc.value = resident ? { ...resident } : null;
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
    npc.value ||
    !session.world
  )
    return;
  if (event.key.toLowerCase() === "j" || event.key === "Escape") {
    event.preventDefault();
    session.open({ kind: event.key === "Escape" ? "settings" : "journal" });
  }
}
onMounted(() => {
  void load();
  window.addEventListener("keydown", worldShortcut);
});
onBeforeUnmount(() => window.removeEventListener("keydown", worldShortcut));
</script>
