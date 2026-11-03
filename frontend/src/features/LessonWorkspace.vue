<template>
  <div
    class="lesson-workspace learning-layout"
    :class="{ 'reading-only': !activity, 'is-compact': compact }"
  >
    <nav
      v-if="activity && compact"
      class="workspace-view-switch"
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
        向助教提问
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
      <header class="content-heading">
        <p class="eyebrow">{{ activity ? "教学活动" : "图书馆阅读" }}</p>
        <h2>{{ activity?.title ?? material?.title ?? "正在读取课程" }}</h2>
        <div v-if="material" class="learning-meta">
          <span>{{ materialLabel }}</span
          ><span>资料版本 {{ material.version }}</span
          ><span v-if="material.kind === 'text' && material.pages.length"
            >第 {{ pageIndex + 1 }} / {{ material.pages.length }} 页内容</span
          >
        </div>
        <p v-if="material?.notice" class="notice">{{ material.notice }}</p>
      </header>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="loading" class="learning-empty" role="status">
        正在读取课程内容…
      </p>
      <template v-else-if="material">
        <p v-if="material.status !== 'ready'" class="notice">
          资料处理状态：{{ material.status }}。内容就绪后才可阅读。
        </p>
        <template v-else>
          <nav
            v-if="material.kind === 'text' && material.pages.length"
            class="reader-toolbar"
            aria-label="课件分页"
          >
            <button :disabled="pageIndex <= 0" @click="pageIndex--">
              ← 上一页
            </button>
            <span class="reader-position" aria-live="polite"
              >资料第 <strong>{{ currentPage?.page_no }}</strong> 页<span
                >文本阅读</span
              ></span
            >
            <button
              :disabled="pageIndex >= material.pages.length - 1"
              @click="pageIndex++"
            >
              下一页 →
            </button>
          </nav>
          <article
            v-if="material.kind === 'text' && currentPage"
            class="lesson-text"
            aria-label="当前课件文本"
          >
            <h3 v-if="currentPage.title">{{ currentPage.title }}</h3>
            <p>{{ currentPage.text }}</p>
          </article>
          <PdfReader
            v-if="['pdf', 'presentation'].includes(material.kind)"
            :url="fileUrl"
            :page="pdfPage"
            @page="pdfPage = $event"
          />
          <details
            v-if="
              ['pdf', 'presentation'].includes(material.kind) &&
              pdfTextPage?.text
            "
            class="lesson-extracted-text"
          >
            <summary>当前第 {{ pdfPage }} 页提取文本（可复制）</summary>
            <article class="lesson-text">
              <p>{{ pdfTextPage.text }}</p>
            </article>
          </details>
          <video
            v-if="material.kind === 'video' && mediaBlob"
            controls
            :src="mediaBlob"
            @timeupdate="updateTime"
            @loadedmetadata="restoreVideoPosition"
            preload="metadata"
            class="course-video"
            aria-label="课程视频"
          ></video>
          <p v-if="material.kind === 'video' && !mediaBlob" class="notice">
            {{ mediaError || "正在加载受保护的视频文件…" }}
          </p>
          <p
            v-if="
              !material.pages.length &&
              !['pdf', 'presentation', 'video'].includes(material.kind)
            "
            class="learning-empty"
          >
            当前版本没有可展示内容。
          </p>
        </template>
      </template>
      <footer v-if="activity" class="lesson-footer">
        <div>
          <strong>留下一次学习记录</strong>
          <p>阅读参与单独保存，知识掌握仍由正式练习与测验判定。</p>
          <p v-if="participationMessage" class="learning-success" role="status">
            {{ participationMessage }}
          </p>
        </div>
        <button
          class="primary"
          :disabled="
            saving || loading || !material || material.status !== 'ready'
          "
          @click="participate"
        >
          {{ saving ? "保存中…" : "记录本次学习" }}
        </button>
      </footer>
    </section>
    <AssistantPanel
      v-if="activity"
      id="lesson-assistant-region"
      tabindex="-1"
      :hidden="compact && !showAssistant"
      :inert="compact && !showAssistant"
      :aria-hidden="compact && !showAssistant"
      :activity-id="activity.id"
      :allowed="activity.assistant_allowed"
      :page-no="currentContext.page_no"
      :video-time="currentContext.video_time"
      :role-name="roleName"
      @cite="cite"
    />
  </div>
</template>
<script setup lang="ts">
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
import { api, notify } from "../shared/api";
import type { Activity, Material } from "../shared/types";
import { learningContext } from "../shared/learningContext";
import AssistantPanel from "./AssistantPanel.vue";
import "./learningUi.css";
const PdfReader = defineAsyncComponent(() => import("./PdfReader.vue"));
const props = defineProps<{
    activityId: string;
    initialPage?: number;
    materialId?: number;
    materialVersion?: number;
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
const controller = new AbortController(),
  contextUserId = session.user?.id,
  contextClassroomId = session.classroomId;
let compactMedia: MediaQueryList | undefined;
const currentPage = computed(() => material.value?.pages[pageIndex.value]),
  roleName = computed(
    () =>
      session.world?.npcs.find((n) => n.key === activity.value?.npc_key)
        ?.name ?? "课程助教",
  ),
  materialLabel = computed(
    () =>
      ({
        text: "文本资料",
        pdf: "PDF 课件",
        presentation: "转换后的课件",
        video: "课程视频",
      })[material.value?.kind ?? ""] ?? "课程资料",
  );
const readingKey = computed(
  () =>
    `read:${contextUserId}:${contextClassroomId}:${material.value ? `material-${material.value.id}-${material.value.version}` : props.activityId}`,
);
const fileUrl = computed(() =>
  material.value
    ? `/materials/${material.value.id}/versions/${material.value.version}/file?classroom_id=${session.classroomId}`
    : "",
);
const pdfTextPage = computed(() =>
  material.value?.pages.find((p) => p.page_no === pdfPage.value),
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
  showAssistant.value = assistant;
  await nextTick();
  document
    .getElementById(
      assistant ? "lesson-assistant-region" : "lesson-reading-region",
    )
    ?.focus({ preventScroll: true });
}
async function load() {
  loading.value = true;
  error.value = "";
  participationMessage.value = "";
  mediaError.value = "";
  if (mediaBlob.value) URL.revokeObjectURL(mediaBlob.value);
  mediaBlob.value = "";
  try {
    if (props.materialId) {
      activity.value = undefined;
      material.value = await api.get<Material>(
        `/materials/${props.materialId}/versions/${props.materialVersion}?classroom_id=${session.classroomId}`,
      );
    } else {
      activity.value = await session.activity(props.activityId);
      material.value = activity.value.material ?? undefined;
    }
    const saved = Number(sessionStorage.getItem(readingKey.value) ?? 0);
    const requested = props.initialPage
      ? (material.value?.pages.findIndex(
          (p) => p.page_no === props.initialPage,
        ) ?? 0)
      : saved;
    pageIndex.value = Math.min(
      Math.max(0, Number.isFinite(requested) ? requested : 0),
      Math.max(0, (material.value?.pages.length ?? 1) - 1),
    );
    const savedPdf = Number(
      sessionStorage.getItem(`${readingKey.value}:pdf`) ?? 1,
    );
    pdfPage.value =
      props.initialPage ??
      Math.max(1, Number.isFinite(savedPdf) ? savedPdf : 1);
    if (material.value?.kind === "video") {
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
    loading.value = false;
  }
}
async function participate() {
  if (!activity.value) return;
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
async function cite(id: number, version: number, page: number) {
  try {
    if (id !== material.value?.id || version !== material.value.version)
      material.value = await api.get<Material>(
        `/materials/${id}/versions/${version}?classroom_id=${session.classroomId}`,
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
function restoreVideoPosition(event: Event) {
  const video = event.target as HTMLVideoElement,
    saved = Number(sessionStorage.getItem(`${readingKey.value}:video`) ?? 0);
  if (Number.isFinite(saved) && saved > 0 && Number.isFinite(video.duration))
    video.currentTime = Math.min(saved, Math.max(0, video.duration - 0.1));
}
watch(pageIndex, (value) => {
  if (!loading.value) sessionStorage.setItem(readingKey.value, String(value));
});
watch(pdfPage, (value) => {
  if (!loading.value)
    sessionStorage.setItem(`${readingKey.value}:pdf`, String(value));
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
  void load();
});
onBeforeUnmount(() => {
  controller.abort();
  compactMedia?.removeEventListener("change", updateCompact);
  if (mediaBlob.value) URL.revokeObjectURL(mediaBlob.value);
});
</script>
