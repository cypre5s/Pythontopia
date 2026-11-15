import { computed, ref } from "vue";
import type { Ref } from "vue";
import { api, ApiError, notify } from "../api";
import { randomUUID } from "../uuid";
import type { StoryView, User, Workspace, World } from "../types";
import { createReadingProgress } from "./reading";

interface StoryContext {
  user: Ref<User | null>;
  classroomId: Ref<number | null>;
  world: Ref<World | null>;
  workspace: Ref<Workspace>;
  open: (workspace: Workspace) => void;
  refresh: () => Promise<void>;
}

export function createStorySession({
  user,
  classroomId,
  world,
  workspace,
  open,
  refresh,
}: StoryContext) {
  const story = ref<StoryView | null>(null);
  const storyBusy = ref(false),
    storyDismissed = ref(false);
  const hasStory = computed(() =>
    Boolean(
      world.value?.story_available ||
        world.value?.quests.some((q) => q.story_id),
    ),
  );
  let storyGeneration = 0;
  let pendingStoryMutation: { key: string; eventId: string } | null = null;
  const reading = createReadingProgress({
    owner: () => user.value?.id,
    classroom: () => classroomId.value,
    generation: () => storyGeneration,
  });
  function applyStory(view: StoryView | null) {
    const returningFromActivity = story.value?.status === "waiting";
    story.value = view;
    storyDismissed.value = false;
    if (view?.world) world.value = view.world;
    if (view?.status === "waiting" && view.activity) {
      open({
        kind: ["lesson", "reading"].includes(view.activity.kind)
          ? "lesson"
          : "practice",
        activity_id: view.activity.id,
        ...(view.activity.page_no ? { page_no: view.activity.page_no } : {}),
        story_session_id: view.id,
      });
    } else if (
      returningFromActivity ||
      (workspace.value && "story_session_id" in workspace.value)
    )
      open(null);
  }
  async function storyRequest<T>(
    request: () => Promise<T>,
    apply: (result: T) => void,
  ) {
    if (storyBusy.value) return false;
    const generation = ++storyGeneration,
      owner = user.value?.id,
      classroom = classroomId.value;
    storyBusy.value = true;
    const current = () =>
      generation === storyGeneration &&
      user.value?.id === owner &&
      classroomId.value === classroom;
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
        () =>
          api.get<StoryView | null>(
            `/story/current?classroom_id=${classroomId.value}`,
          ),
        applyStory,
      );
    } catch (error) {
      if (error instanceof ApiError && error.status === 403)
        releaseUnavailableStory(error.message);
      else throw error;
    }
  }
  async function interactStory(target: string, kind: "npc" | "object" = "npc") {
    if (!hasStory.value && kind === "npc") return false;
    try {
      await storyRequest(
        () =>
          api.post<StoryView>("/story/interactions", {
            classroom_id: classroomId.value,
            ...(kind === "npc"
              ? { npc_key: target }
              : { interaction_id: target }),
          }),
        applyStory,
      );
      return true;
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) return false;
      throw error;
    }
  }
  async function mutateStory(
    action: "advance" | "return" | "end",
    choice?: string,
  ) {
    const currentStory = story.value;
    if (!currentStory || storyBusy.value) return false;
    const key = `${currentStory.id}:${currentStory.revision}:${action}:${choice ?? ""}`;
    if (pendingStoryMutation?.key !== key)
      pendingStoryMutation = { key, eventId: randomUUID() };
    const eventId = pendingStoryMutation.eventId;
    try {
      return await storyRequest(
        () =>
          api.post<StoryView>(`/story/sessions/${currentStory.id}/${action}`, {
            classroom_id: classroomId.value,
            event_id: eventId,
            revision: currentStory.revision,
            ...(choice ? { choice } : {}),
          }),
        (view) => {
          pendingStoryMutation = null;
          applyStory(view);
          const node = currentStory.node;
          if (
            action === "advance" &&
            node?.type === "action" &&
            node.action === "open_workspace" &&
            node.workspace &&
            view.status !== "waiting"
          )
            open({ kind: node.workspace });
        },
      );
    } catch (error) {
      if (
        !(
          error instanceof ApiError &&
          error.status === 404 &&
          story.value?.id === currentStory.id
        )
      )
        throw error;
      releaseUnavailableStory("这次互动已无法继续，请在小镇重新发起互动。");
      await refresh().catch(() => undefined);
      return false;
    }
  }
  function advanceStory(choice?: string) {
    return mutateStory("advance", choice);
  }
  function endStory() {
    return mutateStory("end");
  }
  async function returnStoryActivity() {
    const generation = storyGeneration;
    let expectedGeneration = generation;
    try {
      for (;;) {
        const pending = reading.pending;
        try {
          await pending;
        } catch (error) {
          if (error instanceof ApiError && [403, 404].includes(error.status))
            throw error;
          if (!reading.latest || generation !== storyGeneration) throw error;
          await reading.save(reading.latest.activityId, reading.latest.pageNo, reading.latest.materialId);
        }
        if (pending === reading.pending || generation !== storyGeneration)
          break;
      }
      if (generation !== storyGeneration) return false;
      const returning = mutateStory("return");
      expectedGeneration = storyGeneration;
      return await returning;
    } catch (error) {
      if (
        expectedGeneration === storyGeneration &&
        error instanceof ApiError &&
        [403, 404].includes(error.status)
      ) {
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
    reading.clear();
    notify(message);
  }
  function dismissStory() {
    storyDismissed.value = true;
  }

  function clear() {
    storyGeneration++;
    story.value = null;
    storyBusy.value = false;
    storyDismissed.value = false;
    pendingStoryMutation = null;
    reading.clear();
  }
  return {
    story,
    storyBusy,
    storyDismissed,
    hasStory,
    reading,
    clear,
    restoreStory,
    interactStory,
    advanceStory,
    endStory,
    returnStoryActivity,
    dismissStory,
    saveActivityProgress: reading.save,
  };
}
