<script setup lang="ts">
/**
 * 章节「视频讲解」页：固定视口 + 右侧学习侧栏，减少整页上下滚动。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import PyButton from '@/components/ui/PyButton.vue';
import { CHAPTERS, formatKcNumber } from '@/data/chapters';
import {
  listChapterVideos,
  videoStreamUrl,
  type CourseVideoDto,
} from '@/api/courseVideo';
import { kcDisplayName } from '@/data/kcNames';
import { getWorkshopResource, listWorkshopResources } from '@/api/workshop';
import { useToastStore } from '@/stores/toast';
import PptView from '@/components/workshop/PptView.vue';
import BilibiliVideoCards from '@/components/video/BilibiliVideoCards.vue';
import TutorPanel from '@/components/tutor/TutorPanel.vue';
import { useTutorStore } from '@stores/tutor';
import type { HandoutContent, WorkshopResourceDto } from '@/api/dto/Workshop';

type AsideMode = 'guide' | 'tutor';

const props = defineProps<{ chapter: number }>();
const router = useRouter();
const toast = useToastStore();
const tutor = useTutorStore();

const asideMode = ref<AsideMode>('guide');

const loading = ref(true);
const videos = ref<CourseVideoDto[]>([]);
const activeId = ref<number | null>(null);
// 看课页视频 RAG：跟踪当前播放时刻（秒），提问时随 videoId 回传后端合成「视频上下文」。
const videoTimeSec = ref(0);

let mediaReported = false;

const showPpt = ref(false);
const pptResource = ref<WorkshopResourceDto | null>(null);
const pptLoading = ref(false);

const chapterName = computed<string>(
  () => CHAPTERS.find((c) => c.order === props.chapter)?.name ?? `第 ${props.chapter} 章`,
);

const chapterKcCodes = computed<string[]>(() => {
  const ch = CHAPTERS.find((c) => c.order === props.chapter);
  if (!ch) return [];
  const codes: string[] = [];
  for (let n = ch.startKc; n <= ch.endKc; n += 1) codes.push(formatKcNumber(n));
  return codes;
});

// 「B站精选讲解」检索关键词：章节名 + Python + 首个 KC 中文名（kcDisplayName 兜底「本知识点」）。
const biliKeyword = computed<string>(
  () => `${chapterName.value} Python ${kcDisplayName(chapterKcCodes.value[0] ?? '')}`,
);

// R-15：旧 mainQuest 媒体消费打点已退役。本页只做会话内「已学习」标记（不再写主线进度）。
const mediaConsumed = ref(false);

const chapterDocId = computed<number>(() => 185 + props.chapter);

const playable = computed<CourseVideoDto[]>(() => videos.value.filter((v) => v.available));
const activeVideo = computed<CourseVideoDto | null>(
  () => playable.value.find((v) => v.id === activeId.value) ?? playable.value[0] ?? null,
);
const activeSrc = computed<string>(() =>
  activeVideo.value ? videoStreamUrl(activeVideo.value.id) : '',
);

const tutorContext = computed<string>(() => {
  const parts = [`学生正在学习「${chapterName.value}」视频讲解页`];
  if (activeVideo.value?.title) {
    parts.push(`当前小节：${activeVideo.value.title}`);
  }
  parts.push('请结合课件 RAG 回答，优先解释概念与思路，不要直接给出可抄交的完整作业答案。');
  return parts.join('；');
});

function formatDuration(sec: number | null): string {
  if (!sec || sec <= 0) return '';
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

function reportMediaConsumed(_mediaType: string): void {
  if (mediaReported) return;
  mediaReported = true;
  mediaConsumed.value = true;
  toast.show('已记录本章多模态讲解学习', 'success');
}

function onVideoProgress(e: Event): void {
  const v = e.target as HTMLVideoElement;
  videoTimeSec.value = Math.floor(v.currentTime);
  if (v.duration > 0 && v.currentTime / v.duration >= 0.9) {
    reportMediaConsumed('video');
  }
}

async function load(): Promise<void> {
  loading.value = true;
  mediaReported = false;
  mediaConsumed.value = false;
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

async function goPpt(): Promise<void> {
  if (pptLoading.value) return;
  pptLoading.value = true;
  try {
    const list = await listWorkshopResources({ type: 'HANDOUT', limit: 50 });
    const wanted = new Set(chapterKcCodes.value);
    const match = list.find((r) => r.kcCodes.some((c) => wanted.has(c)));
    if (match) {
      pptResource.value = await getWorkshopResource(match.id);
      showPpt.value = true;
      reportMediaConsumed('ppt');
      return;
    }
    toast.show('本章暂无生成的 PPT，已打开课件原文；可在百晓工坊生成讲义 PPT', 'info');
    void router.push({ path: '/library', query: { docId: String(chapterDocId.value), page: '1' } });
  } catch {
    void router.push({ path: '/library', query: { docId: String(chapterDocId.value), page: '1' } });
  } finally {
    pptLoading.value = false;
  }
}
function closePpt(): void {
  showPpt.value = false;
}
async function openTutorAside(): Promise<void> {
  asideMode.value = 'tutor';
  if (tutor.sessions.length === 0 && tutor.status === 'idle') {
    await tutor.loadSessions();
  }
}

function openTutorFullPage(): void {
  // 直接进 /workshop：WorkshopPage 不消费 from/chapter query，故不传。
  void router.push('/workshop');
}
function goTraining(): void {
  void router.push('/training');
}
function goWorld(): void {
  void router.push('/world');
}

onMounted(load);
watch(() => props.chapter, () => {
  asideMode.value = 'guide';
  load();
});
</script>

<template>
  <AppLayout>
    <section
      class="lecture-page"
      data-testid="lecture-page"
    >
      <header class="lecture-top">
        <div class="lecture-top__title">
          <h1 class="lecture-title">
            {{ chapterName }}
          </h1>
          <span
            v-if="mediaConsumed"
            class="lecture-done"
            title="本章多模态讲解已学习"
          >✓ 已学习</span>
        </div>
        <PyButton
          test-id="lecture-back"
          variant="secondary"
          size="sm"
          @click="goWorld"
        >
          ← 小镇
        </PyButton>
      </header>

      <div class="lecture-layout">
        <!-- 主区：仅视频 / 空状态，占满剩余高度 -->
        <main
          class="lecture-main lecture-frame"
          aria-label="视频播放"
        >
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
              @timeupdate="onVideoProgress"
              @ended="reportMediaConsumed('video')"
            >
              你的浏览器不支持内嵌视频播放。
            </video>
            <p class="lecture-vtitle">
              {{ activeVideo.title }}
            </p>
          </template>

          <div
            v-else
            class="lecture-empty"
            data-testid="lecture-empty"
          >
            <p class="lecture-empty-title">
              本章暂无在线视频
            </p>
            <p class="lecture-empty-sub">
              可直接在右侧：看 PPT、问 AI 助教或做题。下面是 B 站精选讲解（外部资源）：
            </p>
            <BilibiliVideoCards
              class="lecture-empty-bili"
              :keyword="biliKeyword"
              heading="本章 B站精选"
              @consumed="reportMediaConsumed('video')"
            />
          </div>
        </main>

        <!-- 侧栏：学习闭环 + 小节 + B站精选（内部滚动，页面不滚） -->
        <aside
          class="lecture-aside lecture-frame"
          aria-label="本章学习与导航"
        >
          <div
            class="aside-tabs"
            role="tablist"
            aria-label="侧栏视图"
          >
            <button
              type="button"
              role="tab"
              class="aside-tab"
              :class="{ 'aside-tab--active': asideMode === 'guide' }"
              :aria-selected="asideMode === 'guide'"
              data-testid="lecture-aside-guide"
              @click="asideMode = 'guide'"
            >
              目录
            </button>
            <button
              type="button"
              role="tab"
              class="aside-tab"
              :class="{ 'aside-tab--active': asideMode === 'tutor' }"
              :aria-selected="asideMode === 'tutor'"
              data-testid="lecture-goto-tutor"
              @click="openTutorAside"
            >
              AI 助教
            </button>
          </div>

          <div
            v-if="asideMode === 'tutor'"
            class="aside-tutor"
            data-testid="lecture-tutor-embed"
          >
            <TutorPanel
              embedded
              :kc-codes="chapterKcCodes"
              :context="tutorContext"
              :video-id="activeVideo?.id"
              :video-time-sec="videoTimeSec"
            />
            <button
              type="button"
              class="aside-tutor-full"
              @click="openTutorFullPage"
            >
              在独立页打开 AI 助教
            </button>
          </div>

          <template v-else>
            <nav
              class="aside-block aside-block--actions"
              aria-label="本章学习闭环"
            >
              <h2 class="aside-heading">
                学习闭环
              </h2>
              <div class="aside-actions">
                <PyButton
                  class="aside-span2"
                  variant="primary"
                  block
                  test-id="lecture-goto-training"
                  @click="goTraining"
                >
                  看完去做题
                </PyButton>
                <PyButton
                  variant="secondary"
                  block
                  test-id="lecture-goto-ppt"
                  :disabled="pptLoading"
                  @click="goPpt"
                >
                  {{ pptLoading ? '加载…' : 'PPT' }}
                </PyButton>
                <PyButton
                  variant="secondary"
                  block
                  @click="openTutorAside"
                >
                  AI 助教
                </PyButton>
                <PyButton
                  class="aside-span2"
                  variant="secondary"
                  block
                  @click="goWorld"
                >
                  小镇
                </PyButton>
              </div>
            </nav>

            <section
              v-if="playable.length > 0"
              class="aside-block aside-block--videos"
            >
              <h2 class="aside-heading">
                本章小节（{{ playable.length }}）
              </h2>
              <ul class="aside-video-list">
                <li
                  v-for="v in playable"
                  :key="v.id"
                >
                  <button
                    type="button"
                    class="aside-video-item"
                    :class="{ 'is-active': activeVideo && v.id === activeVideo.id }"
                    @click="selectVideo(v.id)"
                  >
                    <span class="aside-video-name">{{ v.title }}</span>
                    <span
                      v-if="formatDuration(v.durationSec)"
                      class="aside-video-dur"
                    >{{ formatDuration(v.durationSec) }}</span>
                  </button>
                </li>
              </ul>
            </section>

            <section
              class="aside-block aside-block--bili"
              data-testid="lecture-bili-aside"
            >
              <BilibiliVideoCards
                :keyword="biliKeyword"
                :limit="6"
                heading="本章 B站精选"
                @consumed="reportMediaConsumed('video')"
              />
            </section>
          </template>
        </aside>
      </div>
    </section>

    <Teleport to="body">
      <div
        v-if="showPpt && pptResource"
        class="lecture-ppt-overlay"
      >
        <PptView
          :title="pptResource.title"
          :kc-codes="pptResource.kcCodes"
          :content="(pptResource.content as HandoutContent)"
          :grounding-status="pptResource.groundingStatus"
          :evidence-page-ids="pptResource.evidencePageIds"
          @close="closePpt"
        />
      </div>
    </Teleport>
  </AppLayout>
</template>

<style scoped>
.lecture-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: calc(100dvh - 56px);
  padding: 10px 14px 12px;
  overflow: hidden;
  background:
    radial-gradient(ellipse at top, rgba(255, 247, 230, 0.95) 0%, rgba(241, 216, 150, 0.55) 60%, rgba(212, 175, 55, 0.18) 100%);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}

.lecture-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex: 0 0 auto;
  padding: 8px 12px;
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  box-shadow: 3px 3px 0 var(--pyt-color-shadow);
}
.lecture-top__title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}
.lecture-title {
  margin: 0;
  font-size: 17px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lecture-done {
  font-size: 11px;
  color: var(--pyt-color-bg-elevated);
  background: var(--pyt-color-success);
  border: 1px solid var(--pyt-color-success);
  border-radius: 999px;
  padding: 2px 8px;
}

.lecture-layout {
  flex: 1 1 auto;
  display: flex;
  align-items: stretch;
  gap: 12px;
  min-height: 0;
  overflow: hidden;
}

/* 定高 flex 框架皮肤（牛皮纸渐变 + 像素描边 + 硬投影），主区/侧栏共用。
   刻意不复用 PyPanel：其内边距(--pad)与软 card 投影会冲掉这里的 overflow 调校和像素硬框风格；
   故由本类承载「皮肤」，.lecture-main / .lecture-aside 各自只管 flex 布局，避免三处重复。 */
.lecture-frame {
  background: linear-gradient(180deg, var(--pyt-color-bg-elevated) 0%, var(--pyt-color-bg-sunken) 100%);
  border: 3px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  box-shadow: inset 0 0 0 2px rgba(212, 175, 55, 0.5), 4px 4px 0 var(--pyt-color-shadow);
  min-height: 0;
  overflow: hidden;
}

.lecture-main {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
}
.lecture-video {
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  max-height: 100%;
  object-fit: contain;
  background: var(--pyt-color-shadow);
  border: 2px solid var(--pyt-color-shadow);
  border-radius: var(--pyt-radius-lg);
}
.lecture-vtitle {
  flex: 0 0 auto;
  margin: 0;
  font-size: 13px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lecture-muted {
  margin: auto;
  color: var(--pyt-color-text-hint);
  font-size: 13px;
  text-align: center;
}
.lecture-empty {
  margin: auto;
  width: 100%;
  max-width: 760px;
  max-height: 100%;
  overflow-y: auto;
  text-align: center;
  padding: 16px;
}
.lecture-empty-bili {
  margin-top: 16px;
  text-align: left;
}
.lecture-empty-title {
  margin: 0 0 6px;
  font-size: 16px;
  color: var(--pyt-color-danger);
}
.lecture-empty-sub {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--pyt-color-text-hint);
}

.lecture-aside {
  flex: 0 0 clamp(260px, 30vw, 360px);
  display: flex;
  flex-direction: column;
  gap: 0;
}

.aside-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  flex: 0 0 auto;
  border-bottom: 2px solid var(--pyt-color-text-hint);
}
.aside-tab {
  padding: 8px 6px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  background: var(--pyt-color-bg-soft);
  border: none;
  border-right: 1px solid var(--pyt-color-border);
  cursor: pointer;
  color: var(--pyt-color-text-hint);
}
.aside-tab:last-child {
  border-right: none;
}
.aside-tab--active {
  background: var(--pyt-color-gold-bright);
  color: var(--pyt-color-text-primary);
}
.aside-tutor {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.aside-tutor :deep(.tutor-panel) {
  flex: 1 1 auto;
  min-height: 0;
}
.aside-tutor-full {
  flex: 0 0 auto;
  margin: 0;
  padding: 6px;
  font-family: inherit;
  font-size: 10px;
  text-align: center;
  background: transparent;
  border: none;
  border-top: 1px dashed var(--pyt-color-border);
  color: var(--pyt-color-text-hint);
  cursor: pointer;
  text-decoration: underline;
}
.aside-tutor-full:hover {
  color: var(--pyt-color-text-primary);
}

.aside-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  min-height: 0;
}
.aside-block--actions {
  flex: 0 0 auto;
  border-bottom: 2px dashed var(--pyt-color-border);
}
.aside-block--videos {
  flex: 0 1 auto;
  max-height: 38%;
  overflow: hidden;
}
.aside-block--illust {
  flex: 0 1 auto;
  overflow: hidden;
  border-top: 2px dashed var(--pyt-color-border);
}
.aside-block--bili {
  flex: 1 1 auto;
  min-height: 120px;
  overflow-y: auto;
  border-top: 2px dashed var(--pyt-color-border);
}

.aside-heading {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-color-text-hint);
  letter-spacing: 0.5px;
}

.aside-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.aside-span2 {
  grid-column: span 2;
}

.aside-video-list,
.aside-illust-list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  scrollbar-width: thin;
}
.aside-video-item {
  width: 100%;
  display: flex;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 8px;
  font-family: inherit;
  font-size: 11px;
  text-align: left;
  background: rgba(255, 247, 230, 0.75);
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
  cursor: pointer;
  color: var(--pyt-color-text-primary);
}
.aside-video-item:hover {
  background: var(--pyt-color-bg-soft);
}
.aside-video-item.is-active {
  background: var(--pyt-color-gold-bright);
  border-color: var(--pyt-color-text-primary);
}
.aside-video-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.aside-video-dur {
  flex: none;
  color: var(--pyt-color-text-hint);
}

.aside-illust-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 4px;
  background: rgba(255, 247, 230, 0.75);
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
  cursor: pointer;
  font-family: inherit;
  position: relative;
}
.aside-illust-item:hover {
  border-color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-soft);
}
.aside-illust-thumb {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  object-fit: cover;
  border: 1px solid var(--pyt-color-shadow);
  border-radius: 3px;
  image-rendering: pixelated;
  background: var(--pyt-color-shadow);
}
.aside-illust-label {
  flex: 1;
  font-size: 11px;
  text-align: left;
  line-height: 1.3;
}
.aside-illust-badge {
  position: absolute;
  top: 6px;
  left: 40px;
  font-size: 9px;
  color: var(--pyt-color-text-on-dark);
  background: var(--pyt-color-text-muted);
  border-radius: 999px;
  padding: 1px 5px;
}
.aside-muted {
  margin: 0;
  font-size: 11px;
  color: var(--pyt-color-text-hint);
}

.lecture-lightbox {
  position: fixed;
  inset: 0;
  z-index: var(--pyt-z-overlay);
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  cursor: zoom-out;
}
.lecture-lightbox-img {
  max-width: min(90vw, 640px);
  max-height: 80vh;
  border: 4px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  image-rendering: pixelated;
}
.lecture-lightbox-cap {
  color: var(--pyt-color-text-on-dark);
  font-family: var(--pyt-font-pixel);
  font-size: 13px;
}
.lecture-ppt-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--pyt-z-modal);
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  flex-direction: column;
}

@media (max-width: 860px) {
  .lecture-page {
    height: auto;
    min-height: calc(100dvh - 56px);
    overflow: visible;
  }
  .lecture-layout {
    flex-direction: column;
    overflow: visible;
  }
  .lecture-aside {
    flex: none;
    width: auto;
    max-height: none;
  }
  .aside-block--videos {
    max-height: 200px;
  }
  .lecture-main {
    min-height: 220px;
  }
  .lecture-video {
    aspect-ratio: 16 / 9;
    flex: none;
  }
}
</style>
