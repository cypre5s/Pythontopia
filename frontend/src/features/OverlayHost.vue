<template>
  <div
    v-if="session.workspace"
    class="overlay-host"
    role="dialog"
    aria-modal="true"
    :aria-label="session.workspace.kind === 'map' ? '地图工作区' : '游戏工作区'"
  >
    <section
      ref="panel"
      class="workspace-shell"
      :class="`workspace-${session.workspace.kind}`"
      tabindex="-1"
    >
      <header class="workspace-topbar" :class="{ 'map-workspace-topbar': session.workspace.kind === 'map' }">
        <div v-if="session.workspace.kind === 'map'" class="map-workspace-heading"><strong>小镇地图</strong><span>{{ currentMapName }}</span></div>
        <div v-else class="workspace-heading">
          <button
            v-if="session.canGoBack"
            class="workspace-back"
            @click="session.back()"
          >
            <GameIcon name="arrowLeft"/> 返回{{ previousTitle }} <kbd>Esc</kbd></button
          ><span>{{ title }}</span
          ><small>已暂停世界移动</small>
        </div>
        <button class="return-to-town" aria-label="关闭当前工作区" :disabled="session.storyBusy" @click="close">
          <GameIcon name="close" /><span>回到小镇</span><kbd v-if="session.workspace.kind !== 'map' && !session.canGoBack">Esc</kbd>
        </button>
      </header>
      <LessonWorkspace
        v-if="session.workspace.kind === 'lesson'"
        :key="`lesson-${session.workspace.activity_id}-${session.workspace.activity_version ?? 'current'}`"
        :activity-id="session.workspace.activity_id"
        :activity-version="session.workspace.activity_version"
        :initial-page="session.workspace.page_no"
        :story-session-id="session.workspace.story_session_id"
        @complete="$emit('complete')"
      /><LessonWorkspace
        v-else-if="session.workspace.kind === 'material'"
        :key="`material-${session.workspace.material_id}-${session.workspace.version}`"
        activity-id=""
        :material-id="session.workspace.material_id"
        :material-version="session.workspace.version"
        :initial-page="session.workspace.page_no"
      />
      <PracticeWorkspace
        v-else-if="session.workspace.kind === 'practice'"
        :key="`practice-${session.workspace.activity_id}-${session.workspace.activity_version ?? 'current'}`"
        :activity-id="session.workspace.activity_id"
        :activity-version="session.workspace.activity_version"
        :story-session-id="session.workspace.story_session_id"
        @complete="$emit('complete')"
      /><QuestJournal
        v-else-if="session.workspace.kind === 'journal'"
      /><LibraryWorkspace
        v-else-if="session.workspace.kind === 'library'"
      /><MapWorkspace
        v-else-if="session.workspace.kind === 'map'"
      /><ClassroomPanel
        v-else-if="session.workspace.kind === 'classroom'"
      /><SettingsPanel v-else />
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, type Component } from "vue";
import { useSession } from "../shared/session";
import { useModalFocus } from "../shared/modalFocus";
import GameIcon from "../shared/GameIcon.vue";
import WorkspaceLoadState from "../shared/WorkspaceLoadState.vue";
import QuestJournal from "./QuestJournal.vue";
import LibraryWorkspace from "./LibraryWorkspace.vue";
import ClassroomPanel from "./ClassroomPanel.vue";
import SettingsPanel from "./SettingsPanel.vue";
import { getWorldMap, worldConfig } from "../game/config/worldConfig";
function lazyWorkspace(loader: () => Promise<{ default: Component }>) {
  return defineAsyncComponent({loader, loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, delay: 0, timeout: 20000});
}
const LessonWorkspace = lazyWorkspace(() => import("./LessonWorkspace.vue"));
const PracticeWorkspace = lazyWorkspace(() => import("./PracticeWorkspace.vue"));
const MapWorkspace = lazyWorkspace(() => import("./MapWorkspace.vue"));
const session = useSession(),
  emit = defineEmits<{ close: []; complete: [] }>(),
  panel = ref<HTMLElement>();
const titles = {
    lesson: "教学工作区",
    material: "资料阅读",
    practice: "练习工作区",
    journal: "任务日志",
    library: "小镇图书馆",
    map: "小镇地图",
    classroom: "班级",
    settings: "设置",
  },
  title = computed(() =>
    session.workspace ? titles[session.workspace.kind] : "",
  );
const previousTitle = computed(() =>
  session.previousWorkspace ? titles[session.previousWorkspace.kind] : "",
);
const currentMapName = computed(() => getWorldMap(session.world?.player.map_key ?? worldConfig.default_spawn.map_key).label);
function close() {
  emit("close");
}
useModalFocus(
  panel,
  () => session.workspace?.kind === "map" ? null : session.workspace,
  () => {
    if (session.canGoBack) session.back();
    else close();
  },
);
function closeMapWithEscape(event: KeyboardEvent) {
  if (session.workspace?.kind !== "map" || event.key !== "Escape" || event.defaultPrevented || event.isComposing) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (!event.repeat) close();
}
onMounted(() => window.addEventListener("keydown", closeMapWithEscape, true));
onBeforeUnmount(() => window.removeEventListener("keydown", closeMapWithEscape, true));
</script>

<style scoped>
.workspace-topbar .return-to-town { color:#f5e7ce;background:#203b32e8;border:1px solid #a9987170;border-radius:24px;min-height:44px;padding:8px 16px;box-shadow:0 2px 8px #10231b25; }
.workspace-topbar .return-to-town:hover { background:#355646;color:#fff2d6; }
.workspace-topbar .return-to-town > span { display:inline; }
.workspace-shell.workspace-map { width:min(1440px,100%);border:0;border-radius:0;background:transparent;box-shadow:none; }
.map-workspace-topbar { min-height:70px;padding:4px 8px 16px;background:transparent;border:0;color:#fff0cc; }
.map-workspace-heading { display:flex;align-items:baseline;gap:14px;color:#fff0cc; }
.map-workspace-heading strong { font-size:1.4rem; }.map-workspace-heading span { padding-left:14px;border-left:1px solid #a9987170;font-size:.9rem;font-weight:400;color:#cfbe98; }
@media(max-width:700px) { .map-workspace-topbar { padding:4px 4px 12px;min-height:62px; }.map-workspace-heading { gap:9px; }.map-workspace-heading span { padding-left:9px;font-size:.75rem; }.map-workspace-heading strong { font-size:1.1rem; } }
</style>
