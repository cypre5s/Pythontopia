import { defineStore } from "pinia";
import { computed, ref, watch } from "vue";
import { api } from "./api";
import type {
  Activity,
  Classroom,
  EventEvidence,
  Quest,
  QuestNode,
  User,
  Workspace,
  World,
} from "./types";
export const useSession = defineStore("session", () => {
  const user = ref<User | null>(null),
    classes = ref<Classroom[]>([]),
    classroomId = ref<number | null>(null),
    world = ref<World | null>(null),
    workspace = ref<Workspace>(null),
    trackedQuest = ref("");
  const workspaceHistory = ref<Exclude<Workspace, null>[]>([]);
  const previousWorkspace = computed(() => workspaceHistory.value.at(-1));
  const canGoBack = computed(() => workspaceHistory.value.length > 0);
  const drafts = ref<Record<string, string>>({}),
    audioEnabled = ref(false);
  let loadGeneration = 0;
  async function load() {
    const generation = ++loadGeneration;
    const token = api.token();
    const current = () =>
      generation === loadGeneration && api.token() === token;
    const loadedUser = await api.get<User>("/auth/me");
    if (!current()) return;
    const loadedClasses = await api.get<Classroom[]>("/classrooms");
    if (!current()) return;
    let selectedId =
      user.value?.id === loadedUser.id ? classroomId.value : null;
    if (selectedId === null) {
      const saved = Number(
        sessionStorage.getItem(`classroom:${loadedUser.id}`),
      );
      if (loadedClasses.some((c) => c.id === saved)) selectedId = saved;
    }
    if (!loadedClasses.some((c) => c.id === selectedId))
      selectedId = loadedClasses[0]?.id ?? null;
    const loadedWorld = selectedId
      ? await api.get<World>(`/world?classroom_id=${selectedId}`)
      : null;
    if (!current()) return;
    user.value = loadedUser;
    classes.value = loadedClasses;
    classroomId.value = selectedId;
    world.value = loadedWorld;
  }
  async function refresh() {
    const id = classroomId.value,
      owner = user.value?.id;
    if (!id) return;
    const result = await api.get<World>(`/world?classroom_id=${id}`);
    if (classroomId.value === id && user.value?.id === owner)
      world.value = result;
  }
  async function event(quest: Quest, node: QuestNode, evidence: EventEvidence) {
    const id = classroomId.value,
      owner = user.value?.id;
    const result = await api.post<{ world: World }>("/events", {
      classroom_id: id,
      event_id: crypto.randomUUID(),
      quest_id: quest.id,
      node_id: node.id,
      evidence,
    });
    if (classroomId.value === id && user.value?.id === owner)
      world.value = result.world;
  }
  async function activity(id: string) {
    return api.get<Activity>(
      `/activities/${id}?classroom_id=${classroomId.value}`,
    );
  }
  function open(next: Workspace) {
    workspaceHistory.value = [];
    workspace.value = next;
  }
  function navigate(next: Exclude<Workspace, null>) {
    if (workspace.value)
      workspaceHistory.value = [
        ...workspaceHistory.value.slice(-5),
        workspace.value,
      ];
    workspace.value = next;
  }
  function back() {
    workspace.value = workspaceHistory.value.pop() ?? null;
  }
  watch(classroomId, (id) => {
    if (user.value && id)
      sessionStorage.setItem(`classroom:${user.value.id}`, String(id));
  });
  function reset() {
    loadGeneration++;
    user.value = null;
    classes.value = [];
    classroomId.value = null;
    world.value = null;
    workspace.value = null;
    drafts.value = {};
    trackedQuest.value = "";
    workspaceHistory.value = [];
    audioEnabled.value = false;
    api.setToken(null);
  }
  return {
    user,
    classes,
    classroomId,
    world,
    workspace,
    trackedQuest,
    drafts,
    audioEnabled,
    load,
    refresh,
    event,
    activity,
    open,
    navigate,
    back,
    canGoBack,
    previousWorkspace,
    reset,
  };
});
