<template>
  <LearningSplitLayout
    class="lesson-workspace"
    :has-support="true"
    :compact="compact"
  >
    <nav
      v-if="compact"
      class="workspace-view-switch game-segmented"
      aria-label="课程与助教视图"
    >
      <button
        :aria-pressed="!showAssistant"
        aria-controls="lesson-reading-region"
        @click="switchView(false)"
      >
        课程内容
      </button>
      <button
        :aria-pressed="showAssistant"
        aria-controls="lesson-assistant-region"
        @click="switchView(true)"
      >
        目标与助教
      </button>
    </nav>
    <section
      id="lesson-reading-region"
      class="lesson-content"
      tabindex="-1"
      aria-label="课程内容"
      :hidden="compact && showAssistant"
      :inert="compact && showAssistant"
      :aria-busy="loading"
    >
      <p v-if="error" class="error lesson-state-card" role="alert">
        {{ error }}
      </p>
      <p v-if="loading" class="learning-empty lesson-state-card" role="status">
        正在读取课程内容…
      </p>
      <LessonMaterial
        v-else-if="material"
        :key="material.id"
        :material="material"
        :file-url="fileUrl"
        :pdf-page="pdfPage"
        :page-index="pageIndex"
        :media-blob="mediaBlob"
        :media-error="mediaError"
        :video="video"
        @video-element="video.videoElement.value = $event"
        @page="pdfPage = $event"
        @index="pageIndex = $event"
        @timeline="lectureSegments = $event"
      />
    </section>
    <LearningSupportPanel
      ref="supportPanel"
      v-if="material || activity"
      :key="activity?.id ?? material?.id"
      id="lesson-assistant-region"
      tabindex="-1"
      :hidden="compact && !showAssistant"
      :inert="compact && !showAssistant"
      :aria-hidden="compact && !showAssistant"
      :activity-id="activity?.id"
      :material-id="activity ? undefined : material?.id"
      :allowed="
        activity ? activity.assistant_allowed : material?.status === 'ready'
      "
      :page-no="currentContext.page_no"
      :video-time="currentContext.video_time"
      :assistant-state="{ ...currentContext, material_id: material?.id }"
      :role-name="roleName"
      :portrait-url="rolePortrait"
      :sprite="(activity?.role ?? session.world?.default_role)?.sprite"
      :goals="learningGoals"
      :outline="documentOutline"
      :time-outline="videoOutline"
      @cite="cite"
      @select-page="readRequiredPage"
      @select-time="seekVideo"
    >
      <template v-if="activity" #goals>
        <ActivityGoals :activity="activity" :progress="progress" :material-id="material?.id" :stage-title="stageTitle"
          :busy="saving || loading || progressSaving || switching" @complete="participate" @select="selectResource" @locate="locateResource">
          <template #status>
            <p v-if="participationMessage" class="learning-success" role="status">{{ participationMessage }}</p>
            <p v-if="progressError" class="error" role="alert">{{ progressError }} <button :disabled="progressSaving" @click="retryProgress">重试保存</button></p>
          </template>
        </ActivityGoals>
      </template>
      <template #goals-footer><p v-if="material?.notice" class="notice">{{ material.notice }}</p></template>
    </LearningSupportPanel>
  </LearningSplitLayout>
</template>
<script setup lang="ts">
import {
  computed,
  onMounted,
  onBeforeUnmount,
  nextTick,
  ref,
  watch,
} from "vue";
import { useSession } from "../../shared/session";
import { useWorkspaceSidebar } from "../../shared/workspaceSidebar";
import { notify } from "../../shared/api";
import { learningContext } from "../../shared/learningContext";
import LearningSupportPanel from "../support/LearningSupportPanel.vue";
import LearningSplitLayout from "../support/LearningSplitLayout.vue";
import { useLearningGoals } from "./useLearningGoals";
import { useReadingEvidence } from "./useReadingEvidence";
import { useLessonVideo } from "./useLessonVideo";
import "../support/learningUi.css";
import "./lessonUi.css";
import ActivityGoals from "./ActivityGoals.vue";
import LessonMaterial from "./LessonMaterial.vue";
import { useLessonSource } from "./useLessonSource";
import { useLessonCompletion } from "./useLessonCompletion";
const props = defineProps<{
  activityId: string;
  initialPage?: number;
  initialVideoTime?: number;
  materialId?: number;
  storySessionId?: string;
}>();
const emit = defineEmits<{ complete: [] }>();
const session = useSession();
const contextUserId = session.user?.id;
const contextClassroomId = session.classroomId;
const showAssistant = ref(false);
const compact = ref(false);
const progressSaving = ref(false);
const progressError = ref("");
const { collapsed: sidebarCollapsed } = useWorkspaceSidebar();
watch(sidebarCollapsed, (value) => {
  if (value) showAssistant.value = false;
});
let compactMedia: MediaQueryList | undefined;
const {
  activity,
  material,
  progress,
  loading,
  error,
  pageIndex,
  pdfPage,
  mediaBlob,
  mediaError,
  currentPage,
  readingKey,
  fileUrl,
  currentOwner,
  generation,
  load,
  selectPage,
  showCitation,
  switchMaterial,
} = useLessonSource({
  userId: contextUserId,
  classroomId: contextClassroomId,
  currentOwner: () =>
    session.user?.id === contextUserId &&
    session.classroomId === contextClassroomId,
  activityId: () => props.activityId,
  materialId: () => props.materialId,
  initialPage: () => props.initialPage,
  fetchActivity: session.activity,
  beforeLoad: () => {
    resetReading();
    video.resetVideo();
    completion.reset();
  },
  prepareVideo: (id) => video.configureVideo(id),
  ready: () => {
    void persistProgress();
  },
});
const {
  hasReadingCompletion,
  hasCompletion,
  learningGoals,
  videoOutline,
  documentOutline,
  lectureSegments,
} = useLearningGoals(activity, material, progress);
const roleName = computed(
    () =>
      (activity.value?.role ?? session.world?.default_role)?.name ?? "课程助教",
  ),
  rolePortrait = computed(
    () => (activity.value?.role ?? session.world?.default_role)?.portrait,
  );
const video = useLessonVideo({
  activity,
  material,
  progress,
  progressError,
  readingKey,
  classroomId: contextClassroomId,
  currentOwner,
  generation,
  initialTime: () => props.initialVideoTime,
  onSeek: () => {
    if (compact.value) void switchView(false);
  },
  registerPreparation: session.registerRestartPreparation,
});
const { videoTime, seekVideo, stopVideoTracking, flushVideoSaves } = video;
const { persistProgress, resetReading } = useReadingEvidence({
  activity,
  material,
  progress,
  progressSaving,
  progressError,
  loading,
  error,
  hasReadingCompletion,
  currentOwner,
  pageNo: () =>
    material.value && ["pdf", "presentation"].includes(material.value.kind)
      ? pdfPage.value
      : currentPage.value?.page_no,
  save: session.saveActivityProgress,
});
const currentContext = computed(() =>
  ({ ...learningContext(
    material.value,
    material.value && ["pdf", "presentation"].includes(material.value.kind)
      ? pdfPage.value
      : currentPage.value?.page_no,
    videoTime.value,
  ), material_id: activity.value?.resources.some(row => row.material_id === material.value?.id) ? material.value?.id : undefined }),
);
const completion = useLessonCompletion({
  activity,
  progress,
  hasCompletion,
  progressSaving,
  progressError,
  session,
  currentContext,
  currentOwner,
  generation,
  storySessionId: () => props.storySessionId,
  stopVideo: stopVideoTracking,
  flushVideo: flushVideoSaves,
  complete: () => emit("complete"),
});
const { saving, message: participationMessage, participate } = completion;
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
const supportPanel = ref<InstanceType<typeof LearningSupportPanel>>();
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
const switching = ref(false);
const stageTitle = computed(() => session.world?.quests.flatMap(quest => quest.nodes).find(node => node.activity_id === activity.value?.id && node.status === "ready")?.title);
async function selectResource(id: number) {
  if (switching.value || saving.value || material.value?.id === id) return;
  switching.value = true;
  try {
    stopVideoTracking();
    await flushVideoSaves();
    await persistProgress();
    if (progressError.value) return;
    await switchMaterial(id);
    if (compact.value) await switchView(false);
  } catch (cause) { progressError.value = cause instanceof Error ? cause.message : "资料切换失败，请重试。"; }
  finally { switching.value = false; }
}
async function locateResource(id: number, page?: number, seconds?: number) {
  await selectResource(id);
  if (material.value?.id !== id) return;
  if (page !== undefined) readRequiredPage(page);
  if (seconds !== undefined) { await nextTick(); seekVideo(seconds); }
}
function readRequiredPage(page: number) {
  selectPage(page);
  if (compact.value) void switchView(false);
}
async function cite(id: number, page: number) {
  try {
    if (activity.value?.resources.some(row => row.material_id === id)) {
      await locateResource(id, page);
      return;
    }
    stopVideoTracking();
    await flushVideoSaves();
    await persistProgress();
    if (progressError.value) return;
    if (await showCitation(id, page)) await switchView(false);
  } catch (cause) {
    if (currentOwner())
      notify(cause instanceof Error ? cause.message : "无法访问引用");
  }
}
async function retryProgress() {
  if (material.value?.kind !== "video") return persistProgress();
  progressSaving.value = true;
  try {
    await flushVideoSaves();
  } catch (cause) {
    if (currentOwner())
      progressError.value =
        cause instanceof Error ? cause.message : "观看记录保存失败，请重试。";
  } finally {
    progressSaving.value = false;
  }
}
watch(pageIndex, (value) => {
  if (currentOwner() && !loading.value) {
    sessionStorage.setItem(readingKey.value, String(value));
    void persistProgress();
  }
});
watch(pdfPage, (value) => {
  if (currentOwner() && !loading.value) {
    sessionStorage.setItem(`${readingKey.value}:pdf`, String(value));
    void persistProgress();
  }
});
watch(() => [props.activityId, props.materialId], load);
watch(
  () => props.initialPage,
  (page) => {
    if (page) selectPage(page);
  },
);
onMounted(() => {
  compactMedia = window.matchMedia?.("(max-width: 900px), (max-height: 540px)");
  updateCompact();
  compactMedia?.addEventListener("change", updateCompact);
  void load();
});
onBeforeUnmount(() => {
  compactMedia?.removeEventListener("change", updateCompact);
});
</script>
