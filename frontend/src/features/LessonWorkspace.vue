<template>
  <LearningSplitLayout class="lesson-workspace" :has-support="true" :compact="compact">
    <nav v-if="compact" class="workspace-view-switch game-segmented" aria-label="课程与助教视图">
      <button :aria-pressed="!showAssistant" aria-controls="lesson-reading-region" @click="switchView(false)">课程内容</button>
      <button :aria-pressed="showAssistant" aria-controls="lesson-assistant-region" @click="switchView(true)">目标与助教</button>
    </nav>
    <section id="lesson-reading-region" class="lesson-content" tabindex="-1" aria-label="课程内容" :hidden="compact && showAssistant" :inert="compact && showAssistant" :aria-busy="loading">
      <p v-if="error" class="error lesson-state-card" role="alert">{{ error }}</p>
      <p v-if="loading" class="learning-empty lesson-state-card" role="status">正在读取课程内容…</p>
      <template v-else-if="material">
        <p v-if="material.status !== 'ready'" class="notice lesson-state-card">资料尚未就绪，请稍后再来阅读。</p>
        <template v-else>
          <PdfReader v-if="['pdf', 'presentation'].includes(material.kind)" :url="fileUrl" :page="pdfPage" :title="material.title" @page="pdfPage = $event" />
          <template v-else>
            <div class="lesson-reading-card">
              <header class="content-heading"><h2>{{ material.title }}</h2></header>
              <article v-if="material.kind === 'text' && currentPage" class="lesson-text" aria-label="当前课件文本" tabindex="0">
                <h3 v-if="currentPage.title">{{ currentPage.title }}</h3><p>{{ currentPage.text }}</p>
              </article>
              <video v-if="material.kind === 'video' && mediaBlob" ref="videoElement" controls :src="mediaBlob" @timeupdate="updateTime" @loadedmetadata="restoreVideoPosition" @play="startVideoTracking" @playing="startVideoTracking" @pause="stopVideoTracking" @ended="stopVideoTracking" @waiting="stopVideoTracking" @seeking="stopVideoTracking" @seeked="resumeVideoTracking" @ratechange="videoRateChanged" preload="metadata" class="course-video" aria-label="课程视频"></video>
              <p v-if="material.kind === 'video' && !mediaBlob" class="notice">{{ mediaError || "正在加载视频…" }}</p>
              <p v-if="!material.pages.length && !['video'].includes(material.kind)" class="learning-empty">当前材料没有可展示内容。</p>
            </div>
            <nav v-if="material.kind === 'text' && material.pages.length" class="reader-toolbar lesson-pagination" aria-label="课件分页">
              <button :disabled="pageIndex <= 0" @click="pageIndex--"><GameIcon name="arrowLeft" /> 上一页</button>
              <span class="reader-position" aria-live="polite">{{ material.title }}<span>第 {{ currentPage?.page_no }} / {{ material.pages.length }} 页</span><progress class="reader-position-progress" :value="pageIndex + 1" :max="material.pages.length" aria-label="阅读位置" /></span>
              <button :disabled="pageIndex >= material.pages.length - 1" @click="pageIndex++">下一页 <GameIcon name="arrowRight" /></button>
            </nav>
          </template>
        </template>
      </template>
    </section>
    <LearningSupportPanel v-if="material || activity" :key="activity?.id ?? material?.id" id="lesson-assistant-region" tabindex="-1" :hidden="compact && !showAssistant" :inert="compact && !showAssistant" :aria-hidden="compact && !showAssistant" :activity-id="activity?.id" :material-id="activity ? undefined : material?.id" :allowed="activity ? activity.assistant_allowed : material?.status === 'ready'" :page-no="currentContext.page_no" :video-time="currentContext.video_time" :role-name="roleName" :portrait-url="rolePortrait" :goals="learningGoals" :outline="documentOutline" @cite="cite" @select-page="readRequiredPage" @select-time="seekVideo">
      <template #goals-footer>
        <p v-if="material?.notice" class="notice">{{ material.notice }}</p>
        <footer v-if="activity" class="lesson-footer">
          <div>
            <p>{{ hasCompletion ? `完成本次学习后，与${roleName}继续交谈。` : "阅读参与单独保存，知识掌握仍由正式练习与测验判定。" }}</p>
            <p v-if="participationMessage" class="learning-success" role="status">{{ participationMessage }}</p>
            <p v-if="progressError" class="error" role="alert">{{ progressError }} <button :disabled="progressSaving" @click="retryProgress">重试保存</button></p>
          </div>
          <button class="primary" :disabled="saving || loading || progressSaving || !material || material.status !== 'ready' || Boolean(hasCompletion && !progress?.completed)" @click="participate">{{ saving || progressSaving ? "保存中…" : hasCompletion ? activity.learning?.video ? "完成观看" : "完成阅读" : "记录本次学习" }}</button>
        </footer>
      </template>
    </LearningSupportPanel>
  </LearningSplitLayout>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
import {
  computed,
  onMounted,
  onBeforeUnmount,
  nextTick,
  ref,
  watch,
  defineAsyncComponent,
} from "vue";
import { useSession } from "../shared/session";
import { useWorkspaceSidebar } from "../shared/workspaceSidebar";
import { api, notify } from "../shared/api";
import type { Activity, ActivityProgress, Material } from "../shared/types";
import { learningContext } from "../shared/learningContext";
import { migrateReadingPosition, pdfResumePage } from "../shared/readingPosition";
import { createVideoProgressQueue } from "../shared/videoProgress";
import LearningSupportPanel from "./LearningSupportPanel.vue";
import LearningSplitLayout from "./LearningSplitLayout.vue";
import "./learningUi.css";
const PdfReader = defineAsyncComponent(() => import("./PdfReader.vue"));
const props = defineProps<{
    activityId: string;

    initialPage?: number;
    materialId?: number;

    storySessionId?: string;
  }>(),
  session = useSession(),
  activity = ref<Activity>(),
  material = ref<Material>(),
  loading = ref(true),
  error = ref(""),
  pageIndex = ref(0),
  pdfPage = ref(1),
  videoTime = ref(0),
  saving = ref(false),
  showAssistant = ref(false),
  compact = ref(false),
  mediaBlob = ref(""),
  mediaError = ref(""),
  participationMessage = ref("");
const videoElement = ref<HTMLVideoElement>();
const emit = defineEmits<{ complete: [] }>();
const { collapsed: sidebarCollapsed } = useWorkspaceSidebar();
watch(sidebarCollapsed, value => { if (value) showAssistant.value = false; });
const progress = ref<ActivityProgress>(), progressSaving = ref(false);
const progressError = ref("");
let disposed = false, loadGeneration = 0, progressGeneration = 0;
let queuedPage = "";
let videoQueue: ReturnType<typeof createVideoProgressQueue<ActivityProgress>> | undefined,
  videoTimer: ReturnType<typeof setInterval> | undefined,
  videoTracking = false,
  videoSessionRate = 1,
  unregisterVideoPreparation: (() => void) | undefined;
const videoTasks = new Set<Promise<void>>();
const controller = new AbortController(),
  contextUserId = session.user?.id,
  contextClassroomId = session.classroomId;
let compactMedia: MediaQueryList | undefined;
const currentOwner = () => !disposed && session.user?.id === contextUserId && session.classroomId === contextClassroomId;
const hasReadingCompletion = computed(() => Boolean(activity.value?.completion || activity.value?.learning?.reading));
const hasCompletion = computed(() => hasReadingCompletion.value || Boolean(activity.value?.learning?.video));
const requiredPageIds = computed(() => progress.value?.required_page_ids ?? activity.value?.completion?.required_page_ids ?? material.value?.pages.filter(page => {
  const rule = activity.value?.learning?.reading;
  return rule && (rule.scope === "all" || (page.page_no >= (rule.start_page ?? 1) && page.page_no <= (rule.end_page ?? Infinity)));
}).map(page => Number(page.id)) ?? []);
const requiredVisited = computed(() => progress.value?.read_count ?? requiredPageIds.value.filter(id => progress.value?.visited_pages.includes(id)).length);
const requiredPages = computed(() => material.value?.pages.filter(page => requiredPageIds.value.includes(Number(page.id))) ?? []);
const currentPage = computed(() => material.value?.pages[pageIndex.value]),
  roleName = computed(
    () =>
      session.world?.npcs.find((n) => n.key === (activity.value?.npc_key ?? "librarian_doc"))
        ?.name ?? "图书管理员",
  ),
  rolePortrait = computed(() => session.world?.npcs.find(n => n.key === (activity.value?.npc_key ?? "librarian_doc"))?.portrait);
const learningGoals = computed(() => {
  const title = material.value?.title ?? "当前资料", learning = activity.value?.learning;
  if (learning?.video) {
    const watched = Math.floor(progress.value?.watched_seconds ?? 0), required = progress.value?.required_seconds ?? learning.video.minimum_seconds;
    return [{ title: `观看《${title}》`, detail: `有效播放不少于 ${formatTime(required)}，跳转片段不计观看时长。`, completed: progress.value?.completed ?? false,
      progress: { value: watched, max: required, label: `有效观看 ${formatTime(watched)} / ${formatTime(required)}` },
      items: (learning.highlights?.video ?? []).map(item => ({ title: `${item.label} · ${formatTime(item.start_seconds)}–${formatTime(item.end_seconds)}`, timeSeconds: item.start_seconds })) }];
  }
  const total = progress.value?.read_total ?? requiredPageIds.value.length;
  return [{ title: `${material.value?.kind === "video" ? "观看" : "阅读"}《${title}》`, detail: hasReadingCompletion.value ? learning?.reading?.scope === "all" ? "阅读完整份课件。" : "阅读本次指定的页面。" : material.value?.kind === "video" ? "补充视频可按自己的节奏观看，不计正式成绩。" : `共 ${material.value?.pages.length ?? 0} 页，可使用底部分页继续阅读。`,
    completed: hasReadingCompletion.value && Boolean(progress.value?.completed),
    ...(hasReadingCompletion.value ? { progress: { value: requiredVisited.value, max: total, label: `已阅读 ${requiredVisited.value} / ${total} 页` } } : {}),
    items: requiredPages.value.map(page => ({ title: page.title || `第 ${page.page_no} 页`, pageNo: page.page_no, completed: progress.value?.visited_pages.includes(Number(page.id)) ?? false })) }];
});
const documentOutline = computed(() => material.value?.kind === "video" ? [] : material.value?.pages.map(page => {
  const highlight = activity.value?.learning?.highlights?.pages?.find(item => page.page_no >= item.start_page && page.page_no <= item.end_page);
  return { pageNo: page.page_no, title: page.title, important: Boolean(highlight), highlightLabel: highlight?.label };
}) ?? []);
function formatTime(value: number) { const seconds = Math.max(0, Math.floor(value)); return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`; }
const readingKey = computed(
  () =>
    `read:${contextUserId}:${contextClassroomId}:${material.value ? `material-${material.value.id}` : props.activityId}`,
);
const fileUrl = computed(() =>
  material.value
    ? `/materials/${material.value.id}/file?classroom_id=${session.classroomId}`
    : "",
);

const currentContext = computed(() =>
  learningContext(
    material.value,
    material.value && ["pdf", "presentation"].includes(material.value.kind)
      ? pdfPage.value
      : currentPage.value?.page_no,
    videoTime.value,
  ),
);
function updateCompact() {
  if (!compactMedia) return;
  if (compactMedia.matches && !compact.value)
    showAssistant.value = Boolean(
      document
        .getElementById("lesson-assistant-region")
        ?.contains(document.activeElement),
    );
  compact.value = compactMedia.matches;
}
async function switchView(assistant: boolean) {
  if (assistant) sidebarCollapsed.value = false;
  showAssistant.value = assistant;
  await nextTick();
  document
    .getElementById(
      assistant ? "lesson-assistant-region" : "lesson-reading-region",
    )
    ?.focus({ preventScroll: true });
}
function readRequiredPage(page: number) {
  const index = material.value?.pages.findIndex(item => item.page_no === page) ?? -1;
  if (index >= 0) pageIndex.value = index;
  pdfPage.value = page;
  if (compact.value) void switchView(false);
}
async function load() {
  const generation = ++loadGeneration;
  ++progressGeneration;
  queuedPage = "";
  clearInterval(videoTimer);
  videoTracking = false;
  if (videoQueue) trackVideoTask(videoQueue.dispose());
  videoQueue = undefined;
  loading.value = true;
  error.value = "";
  participationMessage.value = "";
  mediaError.value = "";
  if (mediaBlob.value) URL.revokeObjectURL(mediaBlob.value);
  mediaBlob.value = "";
  try {
    if (props.materialId) {
      const loaded = await api.get<Material>(
        `/materials/${props.materialId}?classroom_id=${session.classroomId}`,
      );
      if (!currentOwner() || generation !== loadGeneration) return;
      activity.value = undefined;
      material.value = loaded;
    } else {
      const loaded = await session.activity(props.activityId);
      if (!currentOwner() || generation !== loadGeneration) return;
      activity.value = loaded;
      material.value = activity.value.material ?? undefined;
    }
    if (!currentOwner() || generation !== loadGeneration) return;
    progress.value = activity.value?.progress;
    if (hasCompletion.value && activity.value) {
      progress.value = await api.get<ActivityProgress>(`/activities/${activity.value.id}/progress?classroom_id=${contextClassroomId}`);
      if (!currentOwner() || generation !== loadGeneration) return;
    }
    if (material.value && contextUserId !== undefined && contextClassroomId !== null)
      migrateReadingPosition(sessionStorage, contextUserId, contextClassroomId, material.value.id);
    const saved = Number(sessionStorage.getItem(readingKey.value) ?? 0);
    const resumePage = props.initialPage ?? progress.value?.page_no;
    const requested = resumePage
      ? (material.value?.pages.findIndex(
          (p) => p.page_no === resumePage,
        ) ?? 0)
      : saved;
    pageIndex.value = Math.min(
      Math.max(0, Number.isFinite(requested) ? requested : 0),
      Math.max(0, (material.value?.pages.length ?? 1) - 1),
    );
    pdfPage.value = pdfResumePage(
      material.value?.pages ?? [], resumePage,
      sessionStorage.getItem(`${readingKey.value}:pdf`), pageIndex.value,
    );
    if (material.value?.kind === "video") {
      if (activity.value?.learning?.video && contextClassroomId !== null) {
        const activityId = activity.value.id;
        videoQueue = createVideoProgressQueue<ActivityProgress>({
          classroomId: contextClassroomId,
          materialId: material.value.id,
          active: () => currentOwner() && loadGeneration === generation && activity.value?.id === activityId,
          send: packet => api.post<ActivityProgress>(`/activities/${activityId}/video-progress`, packet),
          saved: saved => { progress.value = saved; progressError.value = ""; },
          failed: cause => { progressError.value = cause instanceof Error ? cause.message : "观看记录保存失败，请重试。"; },
        });
      }
      try {
        mediaBlob.value = URL.createObjectURL(
          await api.blob(fileUrl.value, controller.signal),
        );
      } catch (e) {
        mediaError.value = e instanceof Error ? e.message : "视频不可用";
      }
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : "课程读取失败";
  } finally {
    if (currentOwner() && generation === loadGeneration) {
      loading.value = false;
      void persistProgress();
    }
  }
}
async function persistProgress() {
  if (!currentOwner() || loading.value || error.value || !hasReadingCompletion.value || material.value?.status !== "ready" || !activity.value) return;
  if (material.value.id !== activity.value.material?.id) return;
  const page = ["pdf", "presentation"].includes(material.value.kind) ? pdfPage.value : currentPage.value?.page_no;
  if (!page) return;
  const pageKey = `${activity.value.id}:${page}`;
  if (queuedPage === pageKey) return;
  queuedPage = pageKey;
  const generation = ++progressGeneration;
  progressSaving.value = true;
  progressError.value = "";
  try {
    const saved = await session.saveActivityProgress(activity.value.id, page);
    if (currentOwner() && generation === progressGeneration) progress.value = saved;
  } catch (error) {
    if (queuedPage === pageKey) queuedPage = "";
    if (currentOwner() && generation === progressGeneration) progressError.value = error instanceof Error ? error.message : "阅读位置保存失败，请重试。";
  } finally {
    if (currentOwner() && generation === progressGeneration) progressSaving.value = false;
  }
}
async function participate() {
  if (!activity.value || saving.value) return;
  if (hasCompletion.value) {
    saving.value = true;
    try {
      if (activity.value.learning?.video) {
        stopVideoTracking();
        await flushVideoSaves();
      }
      if (!currentOwner() || !progress.value?.completed || progressSaving.value) return;
      if (props.storySessionId) { emit("complete"); return; }
    } catch (cause) {
      if (currentOwner()) progressError.value = cause instanceof Error ? cause.message : "学习记录保存失败，请重试。";
      return;
    } finally {
      if (currentOwner()) saving.value = false;
    }
  }
  saving.value = true;
  try {
    const record = await api.post<{ record_id: string }>(
      `/activities/${activity.value.id}/participation`,
      { classroom_id: session.classroomId, ...currentContext.value },
    );
    for (const quest of session.world?.quests ?? [])
      for (const node of quest.nodes)
        if (
          node.status === "ready" &&
          node.type === "STUDY_LESSON" &&
          node.activity_id === activity.value.id
        )
          await session.event(quest, node, { record_id: record.record_id });
    await session.refresh();
    participationMessage.value = "本次阅读参与已保存。";
    notify("本次学习已记录，任务目标已更新。");
  } catch (e) {
    notify(e instanceof Error ? e.message : "保存失败");
  } finally {
    saving.value = false;
  }
}
async function cite(id: number, page: number) {
  try {
    if (id !== material.value?.id)
      material.value = await api.get<Material>(
        `/materials/${id}?classroom_id=${session.classroomId}`,
      );
    const index = material.value.pages.findIndex((p) => p.page_no === page);
    if (index >= 0) pageIndex.value = index;
    pdfPage.value = page;
    await switchView(false);
  } catch (e) {
    notify(e instanceof Error ? e.message : "无法访问引用");
  }
}
function updateTime(event: Event) {
  videoTime.value = (event.target as HTMLVideoElement).currentTime;
  sessionStorage.setItem(`${readingKey.value}:video`, String(videoTime.value));
}
function trackVideoTask(task: Promise<void>) {
  videoTasks.add(task);
  void task.finally(() => videoTasks.delete(task));
}
function startVideoTracking() {
  const video = videoElement.value;
  if (!videoQueue || !video || video.paused || video.seeking || document.hidden || videoTracking) return;
  videoTracking = true;
  videoSessionRate = videoEvidenceRate(video);
  trackVideoTask(videoQueue.sample(videoEvidencePosition(video), true, true, videoSessionRate));
  clearInterval(videoTimer);
  videoTimer = setInterval(() => {
    if (!videoQueue || !videoElement.value) return;
    if (document.hidden || videoElement.value.paused || videoElement.value.seeking) stopVideoTracking();
    else if (videoEvidenceRate(videoElement.value) !== videoSessionRate) videoRateChanged();
    else trackVideoTask(videoQueue.sample(videoEvidencePosition(videoElement.value), true, false, videoSessionRate));
  }, 5000);
}
function stopVideoTracking() {
  clearInterval(videoTimer);
  if (videoQueue && videoElement.value && videoTracking)
    trackVideoTask(videoQueue.sample(videoEvidencePosition(videoElement.value), false, false, videoSessionRate));
  videoTracking = false;
}
function videoEvidencePosition(video: HTMLVideoElement) {
  const duration = activity.value?.material?.duration_seconds;
  return Math.max(0, Math.min(video.currentTime, typeof duration === "number" && duration > 0 ? duration : Infinity));
}
function videoEvidenceRate(video: HTMLVideoElement) { return Math.min(4, Math.max(.25, Number.isFinite(video.playbackRate) ? video.playbackRate : 1)); }
function videoRateChanged() {
  if (!videoElement.value || (videoTracking && videoEvidenceRate(videoElement.value) === videoSessionRate)) return;
  stopVideoTracking();
  resumeVideoTracking();
}
function resumeVideoTracking() { if (!videoElement.value?.paused) startVideoTracking(); }
function videoVisibilityChanged() { if (document.hidden) stopVideoTracking(); else resumeVideoTracking(); }
function seekVideo(seconds: number) {
  const video = videoElement.value;
  if (!video) return;
  stopVideoTracking();
  video.currentTime = Math.max(0, Math.min(seconds, Number.isFinite(video.duration) ? video.duration : seconds));
  if (compact.value) void switchView(false);
}
async function flushVideoSaves() {
  for (;;) {
    const queue = videoQueue;
    if (queue && currentOwner()) await queue.flush();
    await Promise.all([...videoTasks]);
    if (queue === videoQueue && !videoTasks.size) break;
  }
  if (videoQueue?.hasPending()) throw new Error(progressError.value || "观看记录尚未保存，请重试。");
}
async function retryProgress() {
  if (!activity.value?.learning?.video) return persistProgress();
  progressSaving.value = true;
  try { await flushVideoSaves(); }
  catch (cause) { if (currentOwner()) progressError.value = cause instanceof Error ? cause.message : "观看记录保存失败，请重试。"; }
  finally { progressSaving.value = false; }
}
function restoreVideoPosition(event: Event) {
  const video = event.target as HTMLVideoElement,
    saved = Number(sessionStorage.getItem(`${readingKey.value}:video`) ?? 0);
  if (Number.isFinite(saved) && saved > 0 && Number.isFinite(video.duration))
    video.currentTime = Math.min(saved, Math.max(0, video.duration - 0.1));
}
watch(pageIndex, (value) => {
  if (!loading.value) {
    sessionStorage.setItem(readingKey.value, String(value));
    void persistProgress();
  }
});
watch(pdfPage, (value) => {
  if (!loading.value) {
    sessionStorage.setItem(`${readingKey.value}:pdf`, String(value));
    void persistProgress();
  }
});
watch(() => props.activityId, load);
watch(
  () => props.initialPage,
  (page) => {
    if (page) {
      const index =
        material.value?.pages.findIndex((p) => p.page_no === page) ?? -1;
      if (index >= 0) pageIndex.value = index;
      pdfPage.value = page;
    }
  },
);
onMounted(() => {
  compactMedia = window.matchMedia?.("(max-width: 900px), (max-height: 540px)");
  updateCompact();
  compactMedia?.addEventListener("change", updateCompact);
  document.addEventListener("visibilitychange", videoVisibilityChanged);
  unregisterVideoPreparation = session.registerRestartPreparation(async () => { stopVideoTracking(); await flushVideoSaves(); });
  void load();
});
onBeforeUnmount(() => {
  clearInterval(videoTimer);
  videoTracking = false;
  if (videoQueue) trackVideoTask(videoQueue.dispose());
  disposed = true;
  controller.abort();
  compactMedia?.removeEventListener("change", updateCompact);
  document.removeEventListener("visibilitychange", videoVisibilityChanged);
  void Promise.all([...videoTasks]).finally(() => unregisterVideoPreparation?.());
  if (mediaBlob.value) URL.revokeObjectURL(mediaBlob.value);
});
</script>
