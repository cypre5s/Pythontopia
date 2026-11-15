import { nextTick, ref } from "vue";
import type { Ref } from "vue";
import { api, notify } from "../api";
import {
  clearClassroomCaches,
  restoreExperienceCaches,
  type ExperienceClientState,
} from "../devTools";
import type { Classroom, User, World } from "../types";
import type { createReadingProgress } from "./reading";

interface ExperienceContext {
  user: Ref<User | null>;
  classes: Ref<Classroom[]>;
  classroomId: Ref<number | null>;
  world: Ref<World | null>;
  reading: ReturnType<typeof createReadingProgress>;
  invalidateRequests: () => void;
  resetClientState: () => void;
}

export function createExperienceCoordinator({
  user,
  classes,
  classroomId,
  world,
  reading,
  invalidateRequests,
  resetClientState,
}: ExperienceContext) {
  let restartGeneration = 0;
  const restartPreparations = new Set<() => Promise<void>>();
  const resetBusy = ref(false),
    restartCompleted = ref(0);
  function registerRestartPreparation(prepare: () => Promise<void>) {
    restartPreparations.add(prepare);
    return () => {
      restartPreparations.delete(prepare);
    };
  }
  async function prepareExperience() {
    await Promise.all([...restartPreparations].map((prepare) => prepare()));
    await reading.pending;
  }
  async function restartCurrentClassroom(experience?: {
    path: string;
    body: Record<string, unknown>;
    restore?: boolean;
  }) {
    if (resetBusy.value) return false;
    if (!user.value?.dev_tools_enabled || user.value.role !== "student")
      throw new Error("开发工具不可用。");
    const owner = user.value.id,
      classroom = classroomId.value,
      token = api.token();
    const path =
      experience?.path ??
      (user.value.town_preview
        ? "/teacher-town/progress/reset"
        : "/dev/progress/reset");
    if (!classroom || !classes.value.some((item) => item.id === classroom))
      throw new Error("请先选择已加入的班级。");
    const generation = ++restartGeneration,
      previousWorld = world.value;
    const current = () =>
      generation === restartGeneration &&
      user.value?.id === owner &&
      classroomId.value === classroom &&
      api.token() === token;
    resetBusy.value = true;
    try {
      await Promise.all([...restartPreparations].map((prepare) => prepare()));
      if (!current()) return false;
      for (;;) {
        const pending = reading.pending;
        await pending;
        if (!current()) return false;
        if (pending === reading.pending) break;
      }
      invalidateRequests();
      world.value = null;
      await nextTick();
      const result = await api.post<{
        ok: boolean;
        user_id: number;
        classroom_id: number;
        client_state?: ExperienceClientState;
      }>(
        path,
        experience?.body ?? { classroom_id: classroom, confirmed: true },
      );
      if (!current()) return false;
      if (
        !result.ok ||
        result.user_id !== owner ||
        result.classroom_id !== classroom
      )
        throw new Error("重新开始的结果与当前账号或班级不一致，请刷新后核对。");
      try {
        if (experience?.restore)
          restoreExperienceCaches(owner, classroom, result.client_state ?? {});
        else if (
          !experience &&
          clearClassroomCaches(owner, classroom).failedStorage
        )
          notify(
            "学习记录已重置，本机缓存未能完全清除；请清除本站缓存后重新进入。",
          );
        if (experience)
          sessionStorage.setItem(
            `experience-resume:${owner}:${classroom}`,
            JSON.stringify(
              result.client_state?.workspace ?? { kind: "journal" },
            ),
          );
      } catch {
        notify(
          "学习记录已重置，本机缓存未能完全清除；请清除本站缓存后重新进入。",
        );
      }
      resetClientState();
      restartCompleted.value++;
      return true;
    } catch (error) {
      if (!current()) return false;
      world.value = previousWorld;
      throw error;
    } finally {
      if (current()) resetBusy.value = false;
    }
  }

  function invalidate() {
    restartGeneration++;
    resetBusy.value = false;
  }
  return {
    resetBusy,
    restartCompleted,
    registerRestartPreparation,
    prepareExperience,
    restartCurrentClassroom,
    invalidate,
    get generation() {
      return restartGeneration;
    },
  };
}
