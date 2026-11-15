<template>
  <p v-if="material.status !== 'ready'" class="notice lesson-state-card">
    资料尚未就绪，请稍后再来阅读。
  </p>
  <template v-else>
    <PdfReader
      @timeline="emit('timeline', $event)"
      v-if="['pdf', 'presentation'].includes(material.kind)"
      :url="fileUrl"
      :page="pdfPage"
      :title="material.title"
      :kind="material.kind"
      :material-id="material.id"
      @page="emit('page', $event)"
    />
    <template v-else>
      <div class="lesson-reading-card">
        <h2 class="game-card-title reader-card-title">
          <GameIcon :name="material.kind === 'video' ? 'video' : 'book'" />{{
            material.kind === "video" ? "视频浏览" : "文本浏览"
          }}<span :title="material.title">{{ material.title }}</span>
        </h2>
        <article
          v-if="material.kind === 'text' && currentPage"
          class="lesson-text"
          aria-label="当前课件文本"
          tabindex="0"
        >
          <h3 v-if="currentPage.title">{{ currentPage.title }}</h3>
          <p>{{ currentPage.text }}</p>
        </article>
        <video
          v-if="material.kind === 'video' && mediaBlob"
          :ref="setVideoElement"
          controls
          :src="mediaBlob"
          @timeupdate="video.updateTime"
          @loadedmetadata="video.restoreVideoPosition"
          @play="video.startVideoTracking"
          @playing="video.startVideoTracking"
          @pause="video.stopVideoTracking"
          @ended="video.stopVideoTracking"
          @waiting="video.stopVideoTracking"
          @seeking="video.stopVideoTracking"
          @seeked="video.resumeVideoTracking"
          @ratechange="video.videoRateChanged"
          preload="metadata"
          class="course-video"
          aria-label="课程视频"
        ></video>
        <p v-if="material.kind === 'video' && !mediaBlob" class="notice">
          {{ mediaError || "正在加载视频…" }}
        </p>
        <p
          v-if="!material.pages.length && !['video'].includes(material.kind)"
          class="learning-empty"
        >
          当前材料没有可展示内容。
        </p>
      </div>
      <nav
        v-if="material.kind === 'text' && material.pages.length"
        class="reader-toolbar lesson-pagination"
        aria-label="课件分页"
      >
        <button
          :disabled="pageIndex <= 0"
          @click="emit('index', pageIndex - 1)"
        >
          <GameIcon name="arrowLeft" /> 上一页
        </button>
        <span class="reader-position" aria-live="polite"
          >{{ material.title
          }}<span
            >第 {{ currentPage?.page_no }} /
            {{ material.pages.length }} 页</span
          ><progress
            class="reader-position-progress"
            :value="pageIndex + 1"
            :max="material.pages.length"
            aria-label="阅读位置"
        /></span>
        <button
          :disabled="pageIndex >= material.pages.length - 1"
          @click="emit('index', pageIndex + 1)"
        >
          下一页 <GameIcon name="arrowRight" />
        </button>
      </nav>
    </template>
  </template>
</template>
<script setup lang="ts">
import {
  computed,
  defineAsyncComponent,
  type ComponentPublicInstance,
} from "vue";
import GameIcon from "../../shared/GameIcon.vue";
import type { Material } from "../../shared/types";
import type { LectureSegment } from "../../shared/lectureTimeline";
import type { useLessonVideo } from "./useLessonVideo";
const PdfReader = defineAsyncComponent(() => import("./PdfReader.vue"));
const props = defineProps<{
  material: Material;
  fileUrl: string;
  pdfPage: number;
  pageIndex: number;
  mediaBlob: string;
  mediaError: string;
  video: ReturnType<typeof useLessonVideo>;
}>();
const emit = defineEmits<{
  page: [page: number];
  index: [index: number];
  timeline: [segments: LectureSegment[]];
  videoElement: [element: HTMLVideoElement | undefined];
}>();
const currentPage = computed(() => props.material.pages[props.pageIndex]);
function setVideoElement(element: Element | ComponentPublicInstance | null) {
  emit("videoElement", element instanceof HTMLVideoElement ? element : undefined);
}
</script>
