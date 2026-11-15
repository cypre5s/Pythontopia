import { computed, nextTick, ref, watch } from "vue";
import type { Ref } from "vue";
import type { User, Workspace } from "../types";

export function createWorkspaceNavigation(
  user: Ref<User | null>,
  classroomId: Ref<number | null>,
) {
  const workspace = ref<Workspace>(null);
  const workspaceHistory = ref<Exclude<Workspace, null>[]>([]);
  const previousWorkspace = computed(() => workspaceHistory.value.at(-1));
  const canGoBack = computed(() => workspaceHistory.value.length > 0);
  function open(next: Workspace) {
    rememberContent();
    workspaceHistory.value = [];
    workspace.value = next;
  }
  function navigate(next: Exclude<Workspace, null>) {
    rememberContent();
    if (workspace.value)
      workspaceHistory.value = [
        ...workspaceHistory.value.slice(-5),
        workspace.value,
      ];
    workspace.value = next;
  }
  function back() {
    rememberContent();
    workspace.value = workspaceHistory.value.pop() ?? null;
  }
  function rememberContent() {
    if (
      user.value &&
      classroomId.value &&
      workspace.value &&
      ["lesson", "material", "practice"].includes(workspace.value.kind)
    ) {
      sessionStorage.setItem(
        `read:${user.value.id}:${classroomId.value}:workspace`,
        JSON.stringify(workspace.value),
      );
    }
  }

  watch(workspace, async (next, previous) => {
    if (previous?.kind !== "map" || next) return;
    await nextTick();
    await nextTick();
    if (!workspace.value)
      document
        .querySelector<HTMLElement>(".world-canvas")
        ?.focus({ preventScroll: true });
  });

  function clearHistory() {
    workspaceHistory.value = [];
  }
  function reset() {
    workspace.value = null;
    clearHistory();
  }
  return {
    workspace,
    previousWorkspace,
    canGoBack,
    open,
    navigate,
    back,
    clearHistory,
    reset,
  };
}
