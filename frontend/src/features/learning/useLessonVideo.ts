import { onMounted, onBeforeUnmount, ref } from "vue";
import type { Ref } from "vue";
import { api } from "../../shared/api";
import type { Activity, ActivityProgress } from "../../shared/types";
import { createVideoProgressQueue } from "../../shared/videoProgress";

interface VideoContext {
  activity: Ref<Activity | undefined>;
  progress: Ref<ActivityProgress | undefined>;
  progressError: Ref<string>;
  readingKey: Ref<string>;
  classroomId: number | null;
  currentOwner: () => boolean;
  generation: () => number;
  initialTime: () => number | undefined;
  onSeek: () => void;
  registerPreparation: (prepare: () => Promise<void>) => () => void;
}

export function useLessonVideo({
  activity,
  progress,
  progressError,
  readingKey,
  classroomId: contextClassroomId,
  currentOwner,
  generation: currentGeneration,
  initialTime,
  onSeek,
  registerPreparation,
}: VideoContext) {
  const videoElement = ref<HTMLVideoElement>();
  const videoTime = ref(0);
  let videoQueue:
    | ReturnType<typeof createVideoProgressQueue<ActivityProgress>>
    | undefined;
  let videoTimer: ReturnType<typeof setInterval> | undefined;
  let videoTracking = false,
    videoSessionRate = 1;
  let unregisterVideoPreparation: (() => void) | undefined;
  const videoTasks = new Set<Promise<void>>();
  function resetVideo() {
    clearInterval(videoTimer);
    videoTracking = false;
    if (videoQueue) trackVideoTask(videoQueue.dispose());
    videoQueue = undefined;
  }
  function configureVideo(materialId: number) {
    const generation = currentGeneration();
    if (activity.value?.learning?.video && contextClassroomId !== null) {
      const activityId = activity.value.id;
      videoQueue = createVideoProgressQueue<ActivityProgress>({
        classroomId: contextClassroomId,
        materialId: materialId,
        active: () =>
          currentOwner() &&
          currentGeneration() === generation &&
          activity.value?.id === activityId,
        send: (packet) =>
          api.post<ActivityProgress>(
            `/activities/${activityId}/video-progress`,
            packet,
          ),
        saved: (saved) => {
          progress.value = saved;
          progressError.value = "";
        },
        failed: (cause) => {
          progressError.value =
            cause instanceof Error
              ? cause.message
              : "观看记录保存失败，请重试。";
        },
      });
    }
  }
  function updateTime(event: Event) {
    videoTime.value = (event.target as HTMLVideoElement).currentTime;
    sessionStorage.setItem(
      `${readingKey.value}:video`,
      String(videoTime.value),
    );
  }
  function trackVideoTask(task: Promise<void>) {
    videoTasks.add(task);
    void task.finally(() => videoTasks.delete(task));
  }
  function startVideoTracking() {
    const video = videoElement.value;
    if (
      !videoQueue ||
      !video ||
      video.paused ||
      video.seeking ||
      document.hidden ||
      videoTracking
    )
      return;
    videoTracking = true;
    videoSessionRate = videoEvidenceRate(video);
    trackVideoTask(
      videoQueue.sample(
        videoEvidencePosition(video),
        true,
        true,
        videoSessionRate,
      ),
    );
    clearInterval(videoTimer);
    videoTimer = setInterval(() => {
      if (!videoQueue || !videoElement.value) return;
      if (
        document.hidden ||
        videoElement.value.paused ||
        videoElement.value.seeking
      )
        stopVideoTracking();
      else if (videoEvidenceRate(videoElement.value) !== videoSessionRate)
        videoRateChanged();
      else
        trackVideoTask(
          videoQueue.sample(
            videoEvidencePosition(videoElement.value),
            true,
            false,
            videoSessionRate,
          ),
        );
    }, 5000);
  }
  function stopVideoTracking() {
    clearInterval(videoTimer);
    if (videoQueue && videoElement.value && videoTracking)
      trackVideoTask(
        videoQueue.sample(
          videoEvidencePosition(videoElement.value),
          false,
          false,
          videoSessionRate,
        ),
      );
    videoTracking = false;
  }
  function videoEvidencePosition(video: HTMLVideoElement) {
    const duration = activity.value?.material?.duration_seconds;
    return Math.max(
      0,
      Math.min(
        video.currentTime,
        typeof duration === "number" && duration > 0 ? duration : Infinity,
      ),
    );
  }
  function videoEvidenceRate(video: HTMLVideoElement) {
    return Math.min(
      4,
      Math.max(
        0.25,
        Number.isFinite(video.playbackRate) ? video.playbackRate : 1,
      ),
    );
  }
  function videoRateChanged() {
    if (
      !videoElement.value ||
      (videoTracking &&
        videoEvidenceRate(videoElement.value) === videoSessionRate)
    )
      return;
    stopVideoTracking();
    resumeVideoTracking();
  }
  function resumeVideoTracking() {
    if (!videoElement.value?.paused) startVideoTracking();
  }
  function videoVisibilityChanged() {
    if (document.hidden) stopVideoTracking();
    else resumeVideoTracking();
  }
  function seekVideo(seconds: number) {
    const video = videoElement.value;
    if (!video) return;
    stopVideoTracking();
    video.currentTime = Math.max(
      0,
      Math.min(
        seconds,
        Number.isFinite(video.duration) ? video.duration : seconds,
      ),
    );
    onSeek();
  }
  async function flushVideoSaves() {
    for (;;) {
      const queue = videoQueue;
      if (queue && currentOwner()) await queue.flush();
      await Promise.all([...videoTasks]);
      if (queue === videoQueue && !videoTasks.size) break;
    }
    if (videoQueue?.hasPending())
      throw new Error(progressError.value || "观看记录尚未保存，请重试。");
  }
  function restoreVideoPosition(event: Event) {
    const video = event.target as HTMLVideoElement,
      saved =
        initialTime() ??
        Number(sessionStorage.getItem(`${readingKey.value}:video`) ?? 0);
    if (Number.isFinite(saved) && saved > 0 && Number.isFinite(video.duration))
      video.currentTime = Math.min(saved, Math.max(0, video.duration - 0.1));
  }

  onMounted(() => {
    document.addEventListener("visibilitychange", videoVisibilityChanged);
    unregisterVideoPreparation = registerPreparation(async () => {
      stopVideoTracking();
      await flushVideoSaves();
    });
  });
  onBeforeUnmount(() => {
    resetVideo();
    document.removeEventListener("visibilitychange", videoVisibilityChanged);
    void Promise.all([...videoTasks]).finally(() =>
      unregisterVideoPreparation?.(),
    );
  });
  return {
    videoElement,
    videoTime,
    updateTime,
    startVideoTracking,
    stopVideoTracking,
    resumeVideoTracking,
    videoRateChanged,
    seekVideo,
    restoreVideoPosition,
    flushVideoSaves,
    resetVideo,
    configureVideo,
  };
}
