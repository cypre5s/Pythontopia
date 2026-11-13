<template>
  <div
    v-if="session.workspace"
    class="overlay-host game-workspace"
    role="dialog"
    aria-modal="true"
    :aria-label="session.workspace.kind === 'map' ? '地图工作区' : '游戏工作区'"
  >
    <section
      ref="panel"
      class="workspace-shell"
      :class="[`workspace-${session.workspace.kind}`, { 'workspace-compact': !['lesson', 'material', 'practice'].includes(session.workspace.kind) }]"
      tabindex="-1"
    >
      <header class="workspace-topbar">
        <div class="game-workspace-title"><GameIcon :name="icon"/><h2>{{ title }}</h2></div>
        <div v-if="session.workspace.kind === 'practice'" id="practice-toolbar" class="practice-toolbar"></div>
        <div class="workspace-actions">
          <button
            v-if="session.canGoBack"
            class="workspace-back"
            :disabled="session.resetBusy"
            @click="session.back()"
          >
            <GameIcon name="arrowLeft"/> 返回{{ previousTitle }} <kbd>Esc</kbd></button
          >
          <button v-if="sidebarCollapsed && hasSidebar" class="workspace-sidebar-expand" type="button" :disabled="session.resetBusy" aria-expanded="false" @click="sidebar.expand()"><GameIcon name="panelOpen" />展开侧边栏</button>
        <button class="return-to-town" aria-label="关闭当前工作区" :disabled="session.storyBusy || session.resetBusy" @click="close">
          <GameIcon name="close" /><span>回到小镇</span><kbd v-if="!session.canGoBack">Esc</kbd>
        </button>
        </div>
      </header>
      <LessonWorkspace
        v-if="session.workspace.kind === 'lesson'"
        :key="`lesson-${session.workspace.activity_id}`"
        :activity-id="session.workspace.activity_id"

        :initial-page="session.workspace.page_no"
        :story-session-id="session.workspace.story_session_id"
        @complete="$emit('complete')"
      /><LessonWorkspace
        v-else-if="session.workspace.kind === 'material'"
        :key="`material-${session.workspace.material_id}`"
        activity-id=""
        :material-id="session.workspace.material_id"

        :initial-page="session.workspace.page_no"
      />
      <PracticeWorkspace
        v-else-if="session.workspace.kind === 'practice'"
        :key="`practice-${session.workspace.activity_id}`"
        toolbar-target="#practice-toolbar"
        :activity-id="session.workspace.activity_id"

        :story-session-id="session.workspace.story_session_id"
        @activity-kind="practiceKind = $event"
        @complete="$emit('complete')"
      /><QuestJournal
        v-else-if="session.workspace.kind === 'journal'"
      /><LibraryWorkspace
        v-else-if="session.workspace.kind === 'library'"
      /><MapWorkspace
        v-else-if="session.workspace.kind === 'map'"
      /><ClassroomPanel
        v-else-if="session.workspace.kind === 'classroom'"
      /><SettingsPanel v-else-if="session.workspace.kind === 'settings'" />
      <DevToolsPanel v-else-if="session.workspace.kind === 'developer' && canUseDevTools(session.user)" />
      <p v-else class="town-section" role="alert">当前功能不可用。</p>
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch, type Component } from "vue";
import { useSession } from "../shared/session";
import { useModalFocus } from "../shared/modalFocus";
import GameIcon from "../shared/GameIcon.vue";
import WorkspaceLoadState from "../shared/WorkspaceLoadState.vue";
import "./townWorkspaces.css";
import "./gameDesign.css";
import QuestJournal from "./QuestJournal.vue";
import LibraryWorkspace from "./LibraryWorkspace.vue";
import ClassroomPanel from "./ClassroomPanel.vue";
import SettingsPanel from "./SettingsPanel.vue";
import DevToolsPanel from "./DevToolsPanel.vue";
import { canUseDevTools } from "../shared/devTools";
import { createWorkspaceSidebar, provideWorkspaceSidebar } from "../shared/workspaceSidebar";
function lazyWorkspace(loader: () => Promise<{ default: Component }>) {
  return defineAsyncComponent({loader, loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, delay: 0, timeout: 20000});
}
const LessonWorkspace = lazyWorkspace(() => import("./LessonWorkspace.vue"));
const PracticeWorkspace = lazyWorkspace(() => import("./PracticeWorkspace.vue"));
const MapWorkspace = lazyWorkspace(() => import("./MapWorkspace.vue"));
const session = useSession(),
  emit = defineEmits<{ close: []; complete: [] }>(),
  panel = ref<HTMLElement>();
const sidebar = createWorkspaceSidebar(() => panel.value), sidebarCollapsed = sidebar.collapsed;
const practiceKind = ref("practice");
watch(() => session.workspace?.kind === "practice" ? session.workspace.activity_id : "", () => { practiceKind.value = "practice"; }, { flush: "sync" });
provideWorkspaceSidebar(sidebar);
const hasSidebar = computed(() => session.workspace && ["lesson", "material", "practice"].includes(session.workspace.kind));
watch(() => session.workspace?.kind, () => { sidebarCollapsed.value = false; });
const titles = {
    lesson: "资料阅读",
    material: "资料阅读",
    practice: "练习",
    journal: "任务日志",
    library: "小镇图书馆",
    map: "小镇地图",
    classroom: "我的班级",
    settings: "设置",
    developer: "开发工具",
  },
  title = computed(() =>
    session.workspace?.kind === "practice" && practiceKind.value === "assessment" ? "单元测验" : session.workspace ? titles[session.workspace.kind] : "",
  );
const icons = { lesson: "book", material: "book", practice: "practice", journal: "journal", library: "book", map: "compass", classroom: "people", settings: "settings", developer: "settings" } as const;
const icon = computed(() => icons[session.workspace!.kind]);
const previousTitle = computed(() =>
  session.previousWorkspace ? titles[session.previousWorkspace.kind] : "",
);
function close() {
  if (session.resetBusy) return;
  emit("close");
}
useModalFocus(
  panel,
  () => session.workspace?.kind === "map" ? null : session.workspace,
  () => {
    if (session.resetBusy) return;
    if (session.canGoBack) session.back();
    else close();
  },
);
function closeMapWithEscape(event: KeyboardEvent) {
  if (session.workspace?.kind !== "map" || event.key !== "Escape" || event.defaultPrevented || event.isComposing) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (!event.repeat) {
    if (session.canGoBack) session.back();
    else close();
  }
}
onMounted(() => window.addEventListener("keydown", closeMapWithEscape, true));
onBeforeUnmount(() => window.removeEventListener("keydown", closeMapWithEscape, true));
</script>
