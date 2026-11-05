<template>
  <div class="world-ui" :inert="blocked" :aria-hidden="blocked || undefined">
    <header class="game-hud">
      <div class="resident-badge">

        <div>
          <strong>{{ session.user?.name }}</strong
          ><small :title="classroomName">{{ classroomName }}</small>
        </div>
      </div>
    </header>
    <aside
      v-if="tracked"
      class="quest-tracker"

      aria-label="当前委托"
    >
<h2 class="quest-heading"><GameIcon name="message" /><span class="quest-title">{{ tracked.title }}</span></h2>
      <div id="tracked-quest-details">

        <div
          class="quest-progress"
          role="progressbar"
          aria-label="委托步骤进度"
          :aria-valuenow="completed"
          :aria-valuemax="total"
          aria-valuemin="0"
          :aria-valuetext="`已完成 ${completed} / ${total} 步`"
        >
          <span
            :style="{ width: `${total ? (completed / total) * 100 : 0}%` }"
          />
        </div>
        <p class="quest-next-label">
          {{ tracked.status === "complete" ? "委托已完成" : "当前目标" }}
        </p>
        <p class="quest-next">
          {{ node ? displayQuestTitle(node) : "全部目标已达成，去看看下一段故事吧。" }}
        </p>
        <p v-if="readyCount > 1" class="quest-parallel">
          还有 {{ readyCount - 1 }} 个目标可在日志中查看
        </p>
        <button v-if="node?.navigation_target" class="quest-go" @click="$emit('guide', node.navigation_target)">
          <span>立即前往</span><GameIcon name="compass" />
        </button>
        <button v-else-if="node?.activity_id && !tracked?.story_id" class="quest-go" @click="openActivity">
          <span>{{
            node.type === "STUDY_LESSON" ? "继续学习" : "开始练习"
          }}</span
          ><GameIcon name="chevron" />
        </button>
        <button
          v-else-if="node?.target"
          class="quest-go"
          @click="$emit('guide', node.target)"
        >
          <span>立即前往</span><GameIcon name="compass" />
        </button>
        <button
          v-else
          class="quest-go"
          @click="session.open({ kind: 'journal' })"
        >
          查看委托详情<GameIcon name="chevron" />
        </button>
      </div>
    </aside>
    <nav class="hud-dock" aria-label="小镇菜单">
      <button data-world-focus @click="session.open({ kind: 'journal' })">
        <GameIcon name="journal" /><span>任务日志</span><kbd>J</kbd>
      </button>
      <button @click="session.open({ kind: 'library' })">
        <GameIcon name="book" /><span>学习资料</span>
      </button>
      <button @click="session.open({ kind: 'map' })">
        <GameIcon name="compass" /><span>地图</span>
      </button>
      <button @click="session.open({ kind: 'classroom' })">
        <GameIcon name="people" /><span>我的班级</span>
      </button>
      <button aria-label="打开设置" @click="session.open({ kind: 'settings' })">
        <GameIcon name="settings" /><span>设置</span><kbd>Esc</kbd>
      </button>
    </nav>
  </div>
</template>
<script setup lang="ts">
import { computed } from "vue";
import { useSession } from "../shared/session";
import GameIcon from "../shared/GameIcon.vue";
import { displayQuestTitle } from "../game/config/worldConfig";
defineProps<{ blocked?: boolean }>();
const session = useSession();

defineEmits<{ guide: [key: string] }>();
const tracked = computed(
  () =>
    session.world?.quests.find(
      (q) => q.id === session.trackedQuest && q.status !== "complete",
    ) ??
    session.world?.quests.find(
      (q) => q.kind === "main" && q.status !== "complete",
    ) ??
    session.world?.quests.find((q) => q.status !== "complete") ??
    session.world?.quests.at(-1),
);
const node = computed(() =>
  tracked.value?.nodes.find((n) => n.status === "ready"),
);
const readyCount = computed(
  () => tracked.value?.nodes.filter((n) => n.status === "ready").length ?? 0,
);
const required = computed(
  () =>
    tracked.value?.nodes.filter((n) => !n.optional && n.status !== "skipped") ??
    [],
);
const completed = computed(
  () => required.value.filter((n) => n.status === "complete").length,
);
const total = computed(() => required.value.length);

const classroomName = computed(
  () =>
    session.classes.find((c) => c.id === session.classroomId)?.name ??
    "我的班级",
);
function openActivity() {
  if (node.value?.activity_id)
    session.open({
      kind: node.value.type === "STUDY_LESSON" ? "lesson" : "practice",
      activity_id: node.value.activity_id,
    });
}
</script>

<style scoped>
.world-ui {
  --hud-inset: clamp(20px, 3vw, 64px);
  display: grid;
  grid-template: minmax(0, 1fr) auto / minmax(0, 1fr) minmax(0, 1fr);
  padding: max(var(--safe-top), 32px) max(var(--safe-right), var(--hud-inset)) max(var(--safe-bottom), 28px) max(var(--safe-left), var(--hud-inset));
  gap: 24px;
  background: linear-gradient(0deg, #102820b8, transparent 24%);
}
.world-ui .game-hud {
  grid-area: 2 / 2;
  align-self: end;
  justify-self: end;
  display: block;
  pointer-events: none;
}
.resident-badge {
  display: block;
  max-width: min(28rem, 38vw);
  background: none;
  border: 0;
  border-radius: 0;
  padding: 0 0 4px;
  box-shadow: none;
  text-align: right;
  color: #fff3d4;
  text-shadow: none;
}
.resident-badge strong { font-size: clamp(1.1rem, 1.5vw, 1.5rem); font-weight: 600; }
.resident-badge small { color: #e0d1ab; font-size: .9rem; margin-top: 8px; }
.world-ui .quest-tracker {
  grid-area: 1 / 1;
  justify-self: start;
  width: min(26rem, 100%);
  max-height: 100%;
  padding: 4px 12px 4px 0;
  background: none;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  text-shadow: 0 1px 3px #10271f, 0 0 5px #10271fcc;
}
.quest-heading {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin: 0 !important;
  padding: 0;
  color: #f3d28b;
  font-size: clamp(1.05rem, 1.4vw, 1.25rem) !important;
  line-height: 1.4 !important;
}
.quest-heading > .game-icon { width: 20px; height: 20px; margin-top: 3px; filter: drop-shadow(0 1px 2px #071b13); }
.quest-title { overflow-wrap: anywhere; }
#tracked-quest-details { padding: 0 0 0 29px; }
.quest-progress { display: none; }
.quest-tracker p.quest-next-label { color: #d4c8a8; margin: 6px 0 2px; }
.quest-tracker p.quest-next { font-size: 1rem; color: #fff7e2; line-height: 1.45; margin: 0 0 2px; }
.quest-go {
  display: inline-flex;
  width: auto;
  padding: 4px 0;
  gap: 10px;
  min-height: 44px;
  color: #efd08e;
  background: transparent;
  border: 0;
  border-radius: 0;
  text-decoration: underline;
  text-underline-offset: 5px;
}
.quest-go:hover { background: transparent; color: #fff3c6; }
.world-ui .hud-dock {
  grid-area: 2 / 1;
  justify-self: start;
  align-self: end;
  display: flex;
  gap: clamp(8px, 1.5vw, 28px);
  padding: 4px;
  border: 0;
  border-radius: 0;
  background: none;
  box-shadow: none;
  overflow: visible;
}
.hud-dock button {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 6px;
  min-width: 66px;
  font-size: .9rem;
  color: #fff2d4;
  text-shadow: 0 2px 3px #071c16;
  border: 0;
  border-radius: 4px;
}
.hud-dock button > .game-icon {
  width: 52px;
  height: 52px;
  padding: 12px;
  color: #edd39b;
  background: #18372ce8;
  border: 1px solid #bba16b;
  border-radius: 5px;
  box-shadow: inset 0 0 0 3px #d9bf7520, 0 3px 0 #0b211bb0;
}
.hud-dock button:hover { background: #ead29b12; color: #ffe4a4; }
.hud-dock button:hover > .game-icon { background: #365442; border-color: #f4d591; }
.hud-dock kbd { display: none; }
@media (max-width: 700px) {
  .world-ui { grid-template-columns: minmax(0, 1fr); padding: var(--safe-top) var(--safe-right) var(--safe-bottom) var(--safe-left); gap: 12px; }
  .world-ui .quest-tracker { width: min(23rem, 100%); }
  .world-ui .game-hud { grid-area: 2 / 1; margin-bottom: 100px; }
  .resident-badge { display: block; max-width: 65vw; }
  .resident-badge strong { font-size: 1rem; }
  .resident-badge small { font-size: .8rem; margin-top: 3px; }
  .world-ui .hud-dock { grid-area: 2 / 1; justify-self: start; gap: 6px; }
  .hud-dock button { min-width: 60px; padding: 4px; font-size: .8rem; }
  .hud-dock button > .game-icon { width: 44px; height: 44px; padding: 10px; }
}
@media (max-height: 540px) {
  .world-ui { padding-top: var(--safe-top); padding-bottom: var(--safe-bottom); gap: 8px; }
  .world-ui .quest-tracker { width: min(24rem, 100%); }

  .quest-tracker p.quest-next { font-size: 1rem; margin-bottom: 0; }
  .quest-progress { margin: 2px 0; }
  .quest-tracker p.quest-next-label { margin-top: 4px; }
  .hud-dock button > .game-icon { width: 40px; height: 40px; padding: 9px; }
  .hud-dock button { gap: 4px; font-size: .8rem; }
}
.quest-tracker .quest-heading,
.quest-tracker .quest-next-label,
.quest-tracker .quest-next,
.quest-tracker .quest-parallel,
.quest-tracker .quest-go {
  text-shadow: 0 1px 2px #071b13, 1px 0 2px #071b13cc, -1px 0 2px #071b13cc, 0 2px 5px #071b13;
}
</style>

<style scoped>
.is-entering .quest-tracker { animation: hud-from-left .5s ease-out both; }
.is-entering .hud-dock button { animation: hud-from-bottom .45s ease-out both; }
.is-entering .hud-dock button:nth-child(1) { animation-delay: .08s; }
.is-entering .hud-dock button:nth-child(2) { animation-delay: .14s; }
.is-entering .hud-dock button:nth-child(3) { animation-delay: .20s; }
.is-entering .hud-dock button:nth-child(4) { animation-delay: .26s; }
.is-entering .hud-dock button:nth-child(5) { animation-delay: .32s; }
.is-entering .resident-badge { animation: hud-from-bottom .55s .18s ease-out both; }
@keyframes hud-from-left { from {opacity:0; transform:translateX(-12px)} to {opacity:1;transform:translateX(0)} }
@keyframes hud-from-bottom { from {opacity:0; transform:translateY(12px)} to {opacity:1;transform:translateY(0)} }
@media(prefers-reduced-motion:reduce) { .is-entering .quest-tracker, .is-entering .hud-dock button, .is-entering .resident-badge {animation:none} }
:global([data-reduced-motion="true"]) .is-entering .quest-tracker,
:global([data-reduced-motion="true"]) .is-entering .hud-dock button,
:global([data-reduced-motion="true"]) .is-entering .resident-badge {animation:none}
</style>
