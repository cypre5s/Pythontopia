<template>
  <div
    class="lesson-workspace learning-layout"
    :class="{ 'reading-only': !activity }"
  >
    <section class="lesson-content">
      <header class="content-heading">
        <p class="eyebrow">
          {{ activity?.kind === "lesson" ? "教学活动" : "开放材料" }}
        </p>
        <h2>{{ activity?.title ?? material?.title }}</h2>
        <p v-if="material?.notice" class="notice">{{ material.notice }}</p>
      </header>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="loading">正在读取课程…</p>
      <template v-else-if="material"
        ><div v-if="material.status !== 'ready'" class="notice">
          资料处理状态：{{ material.status }}。尚不能作为课程内容阅读。
        </div>
        <template v-else
          ><div
            v-if="material.kind === 'text' && material.pages?.length"
            class="reader-toolbar"
          >
            <button :disabled="pageIndex <= 0" @click="pageIndex--">
              ← 上一页</button
            ><span
              >原课件第 {{ currentPage?.page_no ?? pageIndex + 1 }} 页 ·
              文本版</span
            ><button
              :disabled="pageIndex >= material.pages.length - 1"
              @click="pageIndex++"
            >
              下一页 →
            </button>
          </div>
          <article
            v-if="material.kind === 'text' && currentPage"
            class="lesson-text"
          >
            <p class="eyebrow">{{ currentPage.title }}</p>
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
            preload="metadata"
            class="course-video"
          ></video>
          <p v-if="material.kind === 'video' && !mediaBlob" class="notice">
            {{ mediaError || "正在加载受保护的视频文件…" }}
          </p>
          <p
            v-if="
              !material.pages?.length &&
              !['pdf', 'presentation', 'video'].includes(material.kind)
            "
            class="notice"
          >
            当前版本没有可展示内容。
          </p></template
        ></template
      >
      <footer v-if="activity" class="lesson-footer">
        <span>阅读参与记录与正式测验成绩分别保存。</span
        ><button
          class="primary"
          :disabled="saving || !material || material.status !== 'ready'"
          @click="participate"
        >
          {{ saving ? "保存中…" : "记录本次学习" }}
        </button>
      </footer>
    </section>
    <button
      class="mobile-assistant-toggle"
      @click="showAssistant = !showAssistant"
    >
      {{ showAssistant ? "返回课程" : "向助教提问" }}</button
    ><AssistantPanel
      v-if="activity"
      :class="{ 'mobile-visible': showAssistant }"
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
  ref,
  watch,
  defineAsyncComponent,
} from "vue";
import { useSession } from "../shared/session";
import { api, notify } from "../shared/api";
import type { Activity, Material } from "../shared/types";
import { learningContext } from "../shared/learningContext";
import AssistantPanel from "./AssistantPanel.vue";
const PdfReader = defineAsyncComponent(() => import("./PdfReader.vue")),
  props = defineProps<{
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
  mediaBlob = ref(""),
  mediaError = ref("");
const controller = new AbortController();
const currentPage = computed(() => material.value?.pages?.[pageIndex.value]),
  roleName = computed(
    () =>
      session.world?.npcs.find((n) => n.key === activity.value?.npc_key)
        ?.name ?? "课程助教",
  );
const fileUrl = computed(() =>
  material.value
    ? `/materials/${material.value.id}/versions/${material.value.version}/file?classroom_id=${session.classroomId}`
    : "",
);
const pdfTextPage = computed(() =>
  material.value?.pages.find((page) => page.page_no === pdfPage.value),
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
async function load() {
  loading.value = true;
  error.value = "";
  if (mediaBlob.value) URL.revokeObjectURL(mediaBlob.value);
  mediaBlob.value = "";
  try {
    if (props.materialId) {
      material.value = await api.get<Material>(
        `/materials/${props.materialId}/versions/${props.materialVersion}?classroom_id=${session.classroomId}`,
      );
    } else {
      activity.value = await session.activity(props.activityId);
    }
    if (activity.value) material.value = activity.value.material ?? undefined;
    const saved = Number(
      sessionStorage.getItem(
        `read:${session.user?.id}:${props.materialId ? `material-${props.materialId}-${props.materialVersion}` : props.activityId}`,
      ) ?? 0,
    );
    pageIndex.value = props.initialPage
      ? Math.max(
          0,
          material.value?.pages.findIndex(
            (p) => p.page_no === props.initialPage,
          ) ?? 0,
        )
      : saved;
    pdfPage.value = props.initialPage ?? 1;
    if (material.value?.kind === "video") {
      try {
        const blob = await api.blob(fileUrl.value, controller.signal);
        mediaBlob.value = URL.createObjectURL(blob);
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
      {
        classroom_id: session.classroomId,
        ...currentContext.value,
      },
    );
    for (const quest of session.world?.quests ?? [])
      for (const node of quest.nodes)
        if (
          node.status === "ready" &&
          node.type === "STUDY_LESSON" &&
          node.activity_id === activity.value.id
        ) {
          await session.event(quest, node, { record_id: record.record_id });
        }
    await session.refresh();
    notify("本次学习已记录，任务目标已更新。");
  } catch (e) {
    notify(e instanceof Error ? e.message : "保存失败");
  } finally {
    saving.value = false;
  }
}
async function cite(id: number, version: number, page: number) {
  if (id === material.value?.id && version === material.value.version) {
    const index = material.value.pages.findIndex((p) => p.page_no === page);
    if (index >= 0) pageIndex.value = index;
    pdfPage.value = page;
    showAssistant.value = false;
  } else {
    try {
      material.value = await api.get<Material>(
        `/materials/${id}/versions/${version}?classroom_id=${session.classroomId}`,
      );
      pageIndex.value = Math.max(
        0,
        material.value.pages.findIndex((p) => p.page_no === page),
      );
      pdfPage.value = page;
      showAssistant.value = false;
    } catch (e) {
      notify(e instanceof Error ? e.message : "无法访问引用");
    }
  }
}
function updateTime(e: Event) {
  videoTime.value = (e.target as HTMLVideoElement).currentTime;
}
watch(pageIndex, (value) =>
  sessionStorage.setItem(
    `read:${session.user?.id}:${props.materialId ? `material-${props.materialId}-${props.materialVersion}` : props.activityId}`,
    String(value),
  ),
);
watch(() => props.activityId, load);
watch(
  () => props.initialPage,
  (p) => {
    if (p) {
      const index =
        material.value?.pages.findIndex((v) => v.page_no === p) ?? -1;
      if (index >= 0) pageIndex.value = index;
      pdfPage.value = p;
    }
  },
);
onMounted(load);
onBeforeUnmount(() => {
  controller.abort();
  if (mediaBlob.value) URL.revokeObjectURL(mediaBlob.value);
});
</script>
