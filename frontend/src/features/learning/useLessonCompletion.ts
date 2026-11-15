import { ref, type Ref } from "vue";
import { api, notify } from "../../shared/api";
import type { useSession } from "../../shared/session";
import type { Activity, ActivityProgress } from "../../shared/types";
import type { learningContext } from "../../shared/learningContext";

interface CompletionContext {
  activity: Ref<Activity | undefined>;
  progress: Ref<ActivityProgress | undefined>;
  hasCompletion: Ref<boolean>;
  progressSaving: Ref<boolean>;
  progressError: Ref<string>;
  session: ReturnType<typeof useSession>;
  currentContext: Ref<ReturnType<typeof learningContext>>;
  currentOwner: () => boolean;
  generation: () => number;
  storySessionId: () => string | undefined;
  stopVideo: () => void;
  flushVideo: () => Promise<void>;
  complete: () => void;
}

/** Coordinates explicit completion with saved evidence and the active story. */
export function useLessonCompletion(context: CompletionContext) {
  const saving = ref(false);
  const message = ref("");
  let operation = 0;

  function reset() {
    operation++;
    saving.value = false;
    message.value = "";
  }

  async function participate() {
    const activity = context.activity.value;
    if (!activity || saving.value || !context.currentOwner()) return;
    const generation = context.generation();
    const request = ++operation;
    const classroomId = context.session.classroomId;
    const current = () =>
      context.currentOwner() &&
      generation === context.generation() &&
      request === operation;
    saving.value = true;
    try {
      if (context.hasCompletion.value) {
        context.stopVideo();
        await context.flushVideo();
        if (
          !current() ||
          !context.progress.value?.completed ||
          context.progressSaving.value
        )
          return;
        if (context.storySessionId()) {
          context.complete();
          return;
        }
      }
      const record = await api.post<{ record_id: string }>(
        `/activities/${activity.id}/participation`,
        { classroom_id: classroomId, ...context.currentContext.value },
      );
      if (!current()) return;
      for (const quest of context.session.world?.quests ?? []) {
        for (const node of quest.nodes) {
          if (
            node.status === "ready" &&
            node.type === "STUDY_LESSON" &&
            node.activity_id === activity.id
          ) {
            await context.session.event(quest, node, {
              record_id: record.record_id,
            });
            if (!current()) return;
          }
        }
      }
      await context.session.refresh();
      if (!current()) return;
      message.value = "本次阅读参与已保存。";
      notify("本次学习已记录，任务目标已更新。");
    } catch (cause) {
      if (!current()) return;
      const error =
        cause instanceof Error ? cause.message : "学习记录保存失败，请重试。";
      context.progressError.value = error;
      notify(error);
    } finally {
      if (current()) saving.value = false;
    }
  }
  return { saving, message, reset, participate };
}
