<script setup lang="ts">
/**
 * LecturePage —— R-12 主线 Phase 2：章节「视频讲解」页（星露谷像素风）。
 *
 * 进入方式：章节老师 NPC 对话里的 open_panel 效果（route="/lecture/:chapter"）。
 * 行为：
 *   - 拉本章视频清单（超星任务点视频）。有视频 → <video> 播放器 + 右侧小节列表；
 *   - 没有视频（本课程线下授课、超星无在线视频）→ 优雅降级到「看 PPT / 课件问答 / 做题」，
 *     保证主线闭环不被卡住。
 *   - 底部固定「学习闭环」动作条：看本章 PPT → /library；课件问答 → /tutor；做题 → /training。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import { CHAPTERS } from '@/data/chapters';
import {
  listChapterVideos,
  videoStreamUrl,
  type CourseVideoDto,
} from '@/api/courseVideo';

const props = defineProps<{ chapter: number }>();
const router = useRouter();

const loading = ref(true);
const videos = ref<CourseVideoDto[]>([]);
const activeId = ref<number | null>(null);

const chapterName = computed<string>(
  () => CHAPTERS.find((c) => c.order === props.chapter)?.name ?? `第 ${props.chapter} 章`,
);

/** 章 → 课程文档 id（PPT）。课程包 7 份文档 id 为 186..192，对应第 1..7 章。 */
const chapterDocId = computed<number>(() => 185 + props.chapter);

const playable = computed<CourseVideoDto[]>(() => videos.value.filter((v) => v.available));
const activeVideo = computed<CourseVideoDto | null>(
  () => playable.value.find((v) => v.id === activeId.value) ?? playable.value[0] ?? null,
);
const activeSrc = computed<string>(() =>
  activeVideo.value ? videoStreamUrl(activeVideo.value.id) : '',
);

function formatDuration(sec: number | null): string {
  if (!sec || sec <= 0) return '';
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    videos.value = await listChapterVideos(props.chapter);
    activeId.value = playable.value[0]?.id ?? null;
  } finally {
    loading.value = false;
  }
}

function selectVideo(id: number): void {
  activeId.value = id;
}

function goPpt(): void {
  void router.push({ path: '/library', query: { docId: String(chapterDocId.value), page: '1' } });
}
function goTutor(): void {
  void router.push('/tutor');
}
function goTraining(): void {
  void router.push('/training');
}
function goWorld(): void {
  void router.push('/world');
}

onMounted(load);
watch(() => props.chapter, load);
</script>

<template>
  <AppLayout>
    <section
      class="lecture-page"
      data-testid="lecture-page"
    >
      <header class="lecture-head">
        <h1 class="lecture-title">{{ chapterName }} · 视频讲解</h1>
        <p class="lecture-sub">先看讲解视频，再翻 PPT、提问、做题，逐步完成本章。</p>
      </header>

      <div class="lecture-body">
        <!-- 左：播放区 -->
        <div class="lecture-stage">
          <p
            v-if="loading"
            class="lecture-muted"
            data-testid="lecture-loading"
          >
            正在加载本章视频…
          </p>

          <template v-else-if="activeVideo">
            <video
              :key="activeVideo.id"
              class="lecture-video"
              controls
              preload="metadata"
              :src="activeSrc"
              data-testid="lecture-video"
            >
              你的浏览器不支持内嵌视频播放。
            </video>
            <h2 class="lecture-vtitle">{{ activeVideo.title }}</h2>
          </template>

          <!-- 无视频优雅降级：不阻塞主线 -->
          <div
            v-else
            class="lecture-empty"
            data-testid="lecture-empty"
          >
            <div
              class="lecture-empty-illust"
              aria-hidden="true"
            >
              🎬
            </div>
            <p class="lecture-empty-title">本章暂无在线视频讲解</p>
            <p class="lecture-empty-sub">
              该课程为线下授课、超星未上传任务点视频。<br>
              没关系——可以直接看本章 PPT、向课件助教提问，并开始做题。
            </p>
          </div>
        </div>

        <!-- 右：小节列表（有视频时） -->
        <aside
          v-if="playable.length > 0"
          class="lecture-list"
        >
          <h3 class="lecture-list-title">本章小节（{{ playable.length }}）</h3>
          <ul>
            <li
              v-for="v in playable"
              :key="v.id"
            >
              <button
                type="button"
                class="lecture-list-item"
                :class="{ 'is-active': activeVideo && v.id === activeVideo.id }"
                @click="selectVideo(v.id)"
              >
                <span class="lecture-list-name">{{ v.title }}</span>
                <span
                  v-if="formatDuration(v.durationSec)"
                  class="lecture-list-dur"
                >{{ formatDuration(v.durationSec) }}</span>
              </button>
            </li>
          </ul>
        </aside>
      </div>

      <!-- 学习闭环动作条（永远展示） -->
      <nav
        class="lecture-actions"
        aria-label="本章学习闭环"
      >
        <button
          type="button"
          class="lecture-btn lecture-btn--primary"
          data-testid="lecture-goto-ppt"
          @click="goPpt"
        >
          📖 看本章 PPT
        </button>
        <button
          type="button"
          class="lecture-btn"
          data-testid="lecture-goto-tutor"
          @click="goTutor"
        >
          💬 课件问答
        </button>
        <button
          type="button"
          class="lecture-btn"
          data-testid="lecture-goto-training"
          @click="goTraining"
        >
          ⌨️ 去做题
        </button>
        <button
          type="button"
          class="lecture-btn lecture-btn--ghost"
          data-testid="lecture-back"
          @click="goWorld"
        >
          ← 返回小镇
        </button>
      </nav>
    </section>
  </AppLayout>
</template>

<style scoped>
.lecture-page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 28px 20px 32px;
  background:
    radial-gradient(ellipse at top, rgba(255, 247, 230, 0.95) 0%, rgba(241, 216, 150, 0.55) 60%, rgba(212, 175, 55, 0.18) 100%);
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12', monospace);
  color: #3a2a14;
}

.lecture-head {
  text-align: center;
}
.lecture-title {
  margin: 0 0 6px;
  font-size: 24px;
  color: #4d3613;
  text-shadow: 2px 2px 0 rgba(212, 175, 55, 0.55);
  letter-spacing: 1px;
}
.lecture-sub {
  margin: 0;
  font-size: 13px;
  color: #5d4524;
}

.lecture-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 300px);
  gap: 16px;
  align-items: start;
}
@media (max-width: 860px) {
  .lecture-body {
    grid-template-columns: 1fr;
  }
}

.lecture-stage {
  background: linear-gradient(180deg, #fff7e6 0%, #f1d896 100%);
  border: 4px solid #6e4f1d;
  border-radius: 14px;
  box-shadow: inset 0 0 0 2px rgba(212, 175, 55, 0.6), 6px 6px 0 #2a1a05;
  padding: 16px;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.lecture-video {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #1a1208;
  border: 3px solid #2a1a05;
  border-radius: 8px;
}
.lecture-vtitle {
  margin: 0;
  font-size: 16px;
  color: #4d3613;
}
.lecture-muted {
  color: #6b5631;
  text-align: center;
  margin: auto;
}

.lecture-empty {
  margin: auto;
  text-align: center;
  padding: 24px;
}
.lecture-empty-illust {
  font-size: 56px;
  margin-bottom: 10px;
  filter: drop-shadow(2px 2px 0 rgba(0, 0, 0, 0.15));
}
.lecture-empty-title {
  margin: 0 0 8px;
  font-size: 18px;
  color: #b8442d;
}
.lecture-empty-sub {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #5d4524;
}

.lecture-list {
  background: linear-gradient(180deg, #fff7e6 0%, #f1d896 100%);
  border: 4px solid #6e4f1d;
  border-radius: 14px;
  box-shadow: inset 0 0 0 2px rgba(212, 175, 55, 0.6), 6px 6px 0 #2a1a05;
  padding: 14px;
}
.lecture-list-title {
  margin: 0 0 10px;
  font-size: 14px;
  color: #4d3613;
}
.lecture-list ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.lecture-list-item {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  font-family: inherit;
  font-size: 12px;
  text-align: left;
  color: #4d3613;
  background: rgba(255, 247, 230, 0.7);
  border: 2px solid #c9a84a;
  border-radius: 8px;
  cursor: pointer;
}
.lecture-list-item:hover {
  background: #fff3d0;
}
.lecture-list-item.is-active {
  background: #ffd166;
  border-color: #4d3613;
  box-shadow: 2px 2px 0 #2a1a05;
}
.lecture-list-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lecture-list-dur {
  flex: none;
  color: #6b5631;
}

.lecture-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}
.lecture-btn {
  padding: 10px 18px;
  border: 3px solid #4d3613;
  background: #fff3d0;
  color: #4d3613;
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.5px;
  cursor: pointer;
  box-shadow: 3px 3px 0 #2a1a05;
  transition: transform 0.1s, box-shadow 0.1s;
}
.lecture-btn:hover {
  transform: translate(1px, 1px);
  box-shadow: 2px 2px 0 #2a1a05;
}
.lecture-btn:active {
  transform: translate(3px, 3px);
  box-shadow: 0 0 0 #2a1a05;
}
.lecture-btn--primary {
  background: linear-gradient(180deg, #ffd166 0%, #f4b400 100%);
}
.lecture-btn--ghost {
  background: rgba(255, 247, 230, 0.6);
}
</style>
