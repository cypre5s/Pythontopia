<template>
  <div
    v-if="session.workspace"
    class="overlay-host"
    role="dialog"
    aria-modal="true"
    aria-label="游戏工作区"
    @pointerdown.self="close"
  >
    <section ref="panel" class="workspace-shell" tabindex="-1">
      <header class="workspace-topbar">
        <span>{{ title }}</span
        ><button aria-label="关闭当前工作区" @click="close">
          关闭 <kbd>Esc</kbd>
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
import {
  computed,
  defineAsyncComponent,
  nextTick,
  onMounted,
  onBeforeUnmount,
  ref,
  watch,
} from "vue";
import { useSession } from "../shared/session";
import { isTextFocus } from "../game/pathfinding";
const LessonWorkspace = defineAsyncComponent(
    () => import("./LessonWorkspace.vue"),
  ),
  PracticeWorkspace = defineAsyncComponent(
    () => import("./PracticeWorkspace.vue"),
  ),
  QuestJournal = defineAsyncComponent(() => import("./QuestJournal.vue")),
  LibraryWorkspace = defineAsyncComponent(
    () => import("./LibraryWorkspace.vue"),
  ),
  ClassroomPanel = defineAsyncComponent(() => import("./ClassroomPanel.vue")),
  SettingsPanel = defineAsyncComponent(() => import("./SettingsPanel.vue"));
const session = useSession(),
  emit = defineEmits<{ close: [] }>(),
  panel = ref<HTMLElement>();
let previousFocus: HTMLElement | null = null;
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
function close() {
  emit("close");
}
function keydown(e: KeyboardEvent) {
  if (e.key === "Escape" && session.workspace) {
    e.preventDefault();
    e.stopImmediatePropagation();
    close();
    return;
  }
  if (
    e.key.toLowerCase() === "j" &&
    !isTextFocus(document.activeElement) &&
    !session.workspace
  ) {
    session.open({ kind: "journal" });
    return;
  }
  if (e.key === "Tab" && session.workspace && panel.value) {
    const focusable = Array.from(
      panel.value.querySelectorAll<HTMLElement>(
        'button:not([disabled]),a,input,textarea,select,[tabindex="0"]',
      ),
    );
    const first = focusable[0],
      last = focusable[focusable.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last?.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first?.focus();
    }
  }
}
watch(
  () => session.workspace,
  async (next, old) => {
    if (next && !old) {
      previousFocus = document.activeElement as HTMLElement;
      await nextTick();
      panel.value?.focus();
    }
    if (!next && old) previousFocus?.focus();
  },
  { flush: "post" },
);
onMounted(() => window.addEventListener("keydown", keydown, true));
onBeforeUnmount(() => window.removeEventListener("keydown", keydown, true));
</script>
