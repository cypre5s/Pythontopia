<template>
  <div
    v-if="session.workspace"
    class="overlay-host"
    role="dialog"
    aria-modal="true"
    aria-label="游戏工作区"
  >
    <section
      ref="panel"
      class="workspace-shell"
      :class="`workspace-${session.workspace.kind}`"
      tabindex="-1"
    >
      <header class="workspace-topbar">
        <div class="workspace-heading">
          <button
            v-if="session.canGoBack"
            class="workspace-back"
            @click="session.back()"
          >
            <GameIcon name="arrowLeft"/> 返回{{ previousTitle }} <kbd>Esc</kbd></button
          ><span>{{ title }}</span
          ><small>已暂停世界移动</small>
        </div>
        <button class="return-to-town" aria-label="关闭当前工作区" @click="close">
          <GameIcon name="close" /><span>回到小镇</span><kbd v-if="!session.canGoBack">Esc</kbd>
        </button>
      </header>
      <LessonWorkspace
        v-if="session.workspace.kind === 'lesson'"
        :key="session.workspace.activity_id"
        :activity-id="session.workspace.activity_id"
        :initial-page="session.workspace.page_no"
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
        :key="session.workspace.activity_id"
        :activity-id="session.workspace.activity_id"
      /><QuestJournal
        v-else-if="session.workspace.kind === 'journal'"
      /><LibraryWorkspace
        v-else-if="session.workspace.kind === 'library'"
      /><ClassroomPanel
        v-else-if="session.workspace.kind === 'classroom'"
      /><SettingsPanel v-else />
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent, ref, type Component } from "vue";
import { useSession } from "../shared/session";
import { useModalFocus } from "../shared/modalFocus";
import GameIcon from "../shared/GameIcon.vue";
import WorkspaceLoadState from "../shared/WorkspaceLoadState.vue";
import QuestJournal from "./QuestJournal.vue";
import LibraryWorkspace from "./LibraryWorkspace.vue";
import ClassroomPanel from "./ClassroomPanel.vue";
import SettingsPanel from "./SettingsPanel.vue";
function lazyWorkspace(loader: () => Promise<{ default: Component }>) {
  return defineAsyncComponent({loader, loadingComponent: WorkspaceLoadState, errorComponent: WorkspaceLoadState, delay: 0, timeout: 20000});
}
const LessonWorkspace = lazyWorkspace(() => import("./LessonWorkspace.vue"));
const PracticeWorkspace = lazyWorkspace(() => import("./PracticeWorkspace.vue"));
const session = useSession(),
  emit = defineEmits<{ close: [] }>(),
  panel = ref<HTMLElement>();
const titles = {
    lesson: "教学工作区",
    material: "资料阅读",
    practice: "练习工作区",
    journal: "任务日志",
    library: "小镇图书馆",
    classroom: "班级",
    settings: "设置",
  },
  title = computed(() =>
    session.workspace ? titles[session.workspace.kind] : "",
  );
const previousTitle = computed(() =>
  session.previousWorkspace ? titles[session.previousWorkspace.kind] : "",
);
function close() {
  emit("close");
}
useModalFocus(
  panel,
  () => session.workspace,
  () => {
    if (session.canGoBack) session.back();
    else close();
  },
);
</script>

<style scoped>
.workspace-topbar .return-to-town { color:#f5e7ce;background:#203b32e8;border:1px solid #a9987170;border-radius:24px;min-height:44px;padding:8px 16px;box-shadow:0 2px 8px #10231b25; }
.workspace-topbar .return-to-town:hover { background:#355646;color:#fff2d6; }
.workspace-topbar .return-to-town > span { display:inline; }
</style>
