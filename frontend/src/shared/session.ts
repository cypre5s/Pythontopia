import { defineStore } from "pinia";
import { ref, watch } from "vue";
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
  const drafts = ref<Record<string, string>>({}),
    audioEnabled = ref(false);
  async function load() {
    user.value = await api.get<User>("/auth/me");
    classes.value = await api.get<Classroom[]>("/classrooms");
    if (classroomId.value === null) {
      const saved = Number(
        sessionStorage.getItem(`classroom:${user.value.id}`),
      );
      if (classes.value.some((c) => c.id === saved)) classroomId.value = saved;
    }
    if (!classes.value.some((c) => c.id === classroomId.value))
      classroomId.value = classes.value[0]?.id ?? null;
    if (classroomId.value) await refresh();
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
    workspace.value = next;
  }
  watch(classroomId, (id) => {
    if (user.value && id)
      sessionStorage.setItem(`classroom:${user.value.id}`, String(id));
  });
  function reset() {
    user.value = null;
    classes.value = [];
    classroomId.value = null;
    world.value = null;
    workspace.value = null;
    drafts.value = {};
    trackedQuest.value = "";
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
    reset,
  };
});
