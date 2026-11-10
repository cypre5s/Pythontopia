import { randomUUID } from "./uuid";
import { defineStore } from "pinia";
import { computed, nextTick, ref, watch } from "vue";
import { api, ApiError, notify } from "./api";
import { clearClassroomCaches } from "./devTools";
import type {
  Activity,
  ActivityProgress,
  Classroom,
  EventEvidence,
  Player,
  Quest,
  QuestNode,
  StoryView,
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
  const livePlayer = ref<Player | null>(null);
  const positionGeneration = ref(0), savedPositionGeneration = ref(-1);
  const mapPosition = computed(() => {
    if (positionGeneration.value !== savedPositionGeneration.value) return null;
    const saved = world.value?.player;
    return saved ? (livePlayer.value?.map_key === saved.map_key ? livePlayer.value : saved) : null;
  });
  function setMapPosition(player: Player) {
    if (!user.value || !classroomId.value || positionGeneration.value !== savedPositionGeneration.value || player.map_key !== world.value?.player.map_key
      || !Number.isFinite(player.tile_x) || !Number.isFinite(player.tile_y)) return;
    livePlayer.value = { ...player };
  }
  function clearMapPosition() { livePlayer.value = null; }
  const workspaceHistory = ref<Exclude<Workspace, null>[]>([]);
  const previousWorkspace = computed(() => workspaceHistory.value.at(-1));
  const canGoBack = computed(() => workspaceHistory.value.length > 0);
  const drafts = ref<Record<string, string>>({}),
    audioEnabled = ref(false);
  const story = ref<StoryView | null>(null),
    storyBusy = ref(false),
    storyDismissed = ref(false),
    navigationTarget = ref<string | null>(null);
  const hasStory = computed(() => Boolean(world.value?.story_available || world.value?.quests.some(q => q.story_id)));
  let storyGeneration = 0;
  let pendingStoryMutation: { key: string; eventId: string } | null = null;
  let readingProgressTask: Promise<unknown> = Promise.resolve();
  let latestReading: { activityId: string; pageNo: number } | null = null;
  let loadGeneration = 0;
  let classroomSwitchGeneration = 0;
  let restartGeneration = 0;
  const restartPreparations = new Set<() => Promise<void>>();
  const resetBusy = ref(false), restartCompleted = ref(0);
  function registerRestartPreparation(prepare: () => Promise<void>) {
    restartPreparations.add(prepare);
    return () => { restartPreparations.delete(prepare); };
  }
  async function restartCurrentClassroom() {
    if (resetBusy.value) return false;
    if (!user.value?.dev_tools_enabled || user.value.role !== "student") throw new Error("开发工具不可用。");
    const owner = user.value.id, classroom = classroomId.value, token = api.token();
    const path = user.value.town_preview ? "/teacher-town/progress/reset" : "/dev/progress/reset";
    if (!classroom || !classes.value.some(item => item.id === classroom)) throw new Error("请先选择已加入的班级。");
    const generation = ++restartGeneration, previousWorld = world.value;
    const current = () => generation === restartGeneration && user.value?.id === owner && classroomId.value === classroom && api.token() === token;
    resetBusy.value = true;
    try {
      await Promise.all([...restartPreparations].map(prepare => prepare()));
      if (!current()) return false;
      for (;;) {
        const pending = readingProgressTask;
        await pending;
        if (!current()) return false;
        if (pending === readingProgressTask) break;
      }
      loadGeneration++;
      classroomSwitchGeneration++;
      world.value = null;
      await nextTick();
      const result = await api.post<{ ok: boolean; user_id: number; classroom_id: number }>(path, { classroom_id: classroom, confirmed: true });
      if (!current()) return false;
      if (!result.ok || result.user_id !== owner || result.classroom_id !== classroom) throw new Error("重新开始的结果与当前账号或班级不一致，请刷新后核对。");
      try {
        if (clearClassroomCaches(owner, classroom).failedStorage) notify("学习记录已重置，本机缓存未能完全清除；请清除本站缓存后重新进入。");
      }
      catch { notify("学习记录已重置，本机缓存未能完全清除；请清除本站缓存后重新进入。"); }
      storyGeneration++;
      story.value = null;
      storyBusy.value = false;
      storyDismissed.value = false;
      pendingStoryMutation = null;
      readingProgressTask = Promise.resolve();
      latestReading = null;
      navigationTarget.value = null;
      trackedQuest.value = "";
      drafts.value = {};
      workspaceHistory.value = [];
      clearMapPosition();
      restartCompleted.value++;
      return true;
    } catch (error) {
      if (!current()) return false;
      world.value = previousWorld;
      throw error;
    } finally { if (current()) resetBusy.value = false; }
  }
  async function load() {
    if (resetBusy.value) return;
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
      owner = user.value?.id, generation = restartGeneration;
    if (!id || resetBusy.value) return;
    const result = await api.get<World>(`/world?classroom_id=${id}`);
    if (classroomId.value === id && user.value?.id === owner && generation === restartGeneration && !resetBusy.value)
      world.value = result;
  }
  async function switchClassroom(id: number) {
    if (resetBusy.value) throw new Error("正在重新开始，请稍候。");
    if (!classes.value.some(item => item.id === id)) throw new Error("请先加入这个班级。");
    if (id === classroomId.value) return true;
    const generation = ++classroomSwitchGeneration;
    const owner = user.value?.id, previous = classroomId.value, token = api.token();
    const loadingGeneration = ++loadGeneration;
    const current = () => generation === classroomSwitchGeneration && loadingGeneration === loadGeneration && owner === user.value?.id && previous === classroomId.value && token === api.token();
    await readingProgressTask.catch(() => undefined);
    if (!current()) return false;
    const result = await api.get<World>(`/world?classroom_id=${id}`);
    if (!current()) return false;
    classroomId.value = id;
    world.value = result;
    drafts.value = {};
    trackedQuest.value = "";
    workspaceHistory.value = [];
    return true;
  }
  async function event(quest: Quest, node: QuestNode, evidence: EventEvidence) {
    if (resetBusy.value) return;
    const id = classroomId.value,
      owner = user.value?.id, generation = restartGeneration;
    const result = await api.post<{ world: World }>("/events", {
      classroom_id: id,
      event_id: randomUUID(),
      quest_id: quest.id,
      node_id: node.id,
      evidence,
    });
    if (classroomId.value === id && user.value?.id === owner && generation === restartGeneration && !resetBusy.value)
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
  function applyStory(view: StoryView | null) {
    const returningFromActivity = story.value?.status === "waiting";
    story.value = view;
    storyDismissed.value = false;
    if (view?.world) world.value = view.world;
    if (view?.status === "waiting" && view.activity) {
      open({
        kind: ["lesson", "reading"].includes(view.activity.kind) ? "lesson" : "practice",
        activity_id: view.activity.id,
        ...(view.activity.page_no ? { page_no: view.activity.page_no } : {}),
        story_session_id: view.id,
      });
    } else if (returningFromActivity || (workspace.value && "story_session_id" in workspace.value)) open(null);
  }
  async function storyRequest<T>(request: () => Promise<T>, apply: (result: T) => void) {
    if (storyBusy.value) return false;
    const generation = ++storyGeneration,
      owner = user.value?.id,
      classroom = classroomId.value;
    storyBusy.value = true;
    const current = () => generation === storyGeneration && user.value?.id === owner && classroomId.value === classroom;
    try {
      const result = await request();
      if (!current()) return false;
      apply(result);
      return true;
    } catch (error) {
      if (current()) throw error;
      return false;
    } finally {
      if (current()) storyBusy.value = false;
    }
  }
  async function restoreStory() {
    if (!hasStory.value || !classroomId.value) return;
    try {
      await storyRequest(
        () => api.get<StoryView | null>(`/story/current?classroom_id=${classroomId.value}`),
        applyStory,
      );
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) releaseUnavailableStory(error.message);
      else throw error;
    }
  }
  async function interactStory(target: string, kind: "npc" | "object" = "npc") {
    if (!hasStory.value && kind === "npc") return false;
    try {
      await storyRequest(
        () => api.post<StoryView>("/story/interactions", { classroom_id: classroomId.value, ...(kind === "npc" ? { npc_key: target } : { interaction_id: target }) }),
        applyStory,
      );
      return true;
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) return false;
      throw error;
    }
  }
  async function mutateStory(action: "advance" | "return" | "end", choice?: string) {
    const currentStory = story.value;
    if (!currentStory || storyBusy.value) return false;
    const key = `${currentStory.id}:${currentStory.revision}:${action}:${choice ?? ""}`;
    if (pendingStoryMutation?.key !== key) pendingStoryMutation = { key, eventId: randomUUID() };
    const eventId = pendingStoryMutation.eventId;
    try {
      return await storyRequest(
        () => api.post<StoryView>(`/story/sessions/${currentStory.id}/${action}`, {
          classroom_id: classroomId.value, event_id: eventId, revision: currentStory.revision,
          ...(choice ? { choice } : {}),
        }),
        (view) => {
          pendingStoryMutation = null;
          applyStory(view);
          const node = currentStory.node;
          if (action === "advance" && node?.type === "action" && node.action === "open_workspace" && node.workspace && view.status !== "waiting")
            open({ kind: node.workspace });
        },
      );
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 404 && story.value?.id === currentStory.id)) throw error;
      releaseUnavailableStory("这次互动已无法继续，请在小镇重新发起互动。");
      await refresh().catch(() => undefined);
      return false;
    }
  }
  function advanceStory(choice?: string) { return mutateStory("advance", choice); }
  function endStory() { return mutateStory("end"); }
  function saveActivityProgress(activityId: string, pageNo: number): Promise<ActivityProgress> {
    const owner = user.value?.id, classroom = classroomId.value, generation = storyGeneration;
    latestReading = { activityId, pageNo };
    const task = readingProgressTask.catch(() => undefined).then(async () => {
      if (owner !== user.value?.id || classroom !== classroomId.value || generation !== storyGeneration)
        throw new Error("学习会话已切换。");
      return api.post<ActivityProgress>(`/activities/${activityId}/progress`, {
        classroom_id: classroom, page_no: pageNo,
      });
    });
    readingProgressTask = task;
    return task;
  }
  async function returnStoryActivity() {
    const generation = storyGeneration;
    let expectedGeneration = generation;
    try {
      for (;;) {
        const pending = readingProgressTask;
        try { await pending; }
        catch (error) {
          if (error instanceof ApiError && [403, 404].includes(error.status)) throw error;
          if (!latestReading || generation !== storyGeneration) throw error;
          await saveActivityProgress(latestReading.activityId, latestReading.pageNo);
        }
        if (pending === readingProgressTask || generation !== storyGeneration) break;
      }
      if (generation !== storyGeneration) return false;
      const returning = mutateStory("return");
      expectedGeneration = storyGeneration;
      return await returning;
    } catch (error) {
      if (expectedGeneration === storyGeneration && error instanceof ApiError && [403, 404].includes(error.status)) {
        releaseUnavailableStory(error.message);
        return false;
      }
      throw error;
    }
  }
  function releaseUnavailableStory(message: string) {
    applyStory(null);
    storyDismissed.value = true;
    pendingStoryMutation = null;
    readingProgressTask = Promise.resolve();
    latestReading = null;
    notify(message);
  }
  function dismissStory() { storyDismissed.value = true; }
  function guideTo(key: string) {
    open(null);
    dismissStory();
    navigationTarget.value = key;
  }
  watch(workspace, async (next, previous) => {
    if (previous?.kind !== "map" || next) return;
    await nextTick();
    await nextTick();
    if (!workspace.value) document.querySelector<HTMLElement>(".world-canvas")?.focus({ preventScroll: true });
  });
  watch([() => user.value?.id, classroomId], () => {
    restartGeneration++;
    resetBusy.value = false;
    positionGeneration.value++;
    clearMapPosition();
    storyGeneration++;
    story.value = null;
    storyBusy.value = false;
    storyDismissed.value = false;
    navigationTarget.value = null;
    pendingStoryMutation = null;
    readingProgressTask = Promise.resolve();
    latestReading = null;
    if (workspace.value && "story_session_id" in workspace.value) open(null);
  }, { flush: "sync" });
  watch(world, value => { savedPositionGeneration.value = value ? positionGeneration.value : -1; }, { flush: "sync" });
  watch(() => world.value?.player.map_key, map => {
    if (livePlayer.value?.map_key !== map) clearMapPosition();
  }, { flush: "sync" });
  watch(classroomId, (id) => {
    if (user.value && id)
      sessionStorage.setItem(`classroom:${user.value.id}`, String(id));
  });
  function reset() {
    loadGeneration++;
    classroomSwitchGeneration++;
    restartGeneration++;
    resetBusy.value = false;
    user.value = null;
    classes.value = [];
    classroomId.value = null;
    world.value = null;
    clearMapPosition();
    workspace.value = null;
    drafts.value = {};
    trackedQuest.value = "";
    workspaceHistory.value = [];
    audioEnabled.value = false;
    storyGeneration++;
    story.value = null;
    storyBusy.value = false;
    storyDismissed.value = false;
    navigationTarget.value = null;
    pendingStoryMutation = null;
    readingProgressTask = Promise.resolve();
    latestReading = null;
    api.setToken(null);
  }
  return {
    user,
    classes,
    classroomId,
    world,
    mapPosition,
    setMapPosition,
    clearMapPosition,
    workspace,
    trackedQuest,
    drafts,
    audioEnabled,
    story,
    storyBusy,
    storyDismissed,
    hasStory,
    navigationTarget,
    restoreStory,
    interactStory,
    advanceStory,
    endStory,
    returnStoryActivity,
    saveActivityProgress,
    dismissStory,
    guideTo,
    load,
    refresh,
    switchClassroom,
    event,
    activity,
    open,
    navigate,
    back,
    canGoBack,
    previousWorkspace,
    reset,
    resetBusy,
    restartCompleted,
    registerRestartPreparation,
    restartCurrentClassroom,
  };
});
