<script setup lang="ts">
/**
 * StageVideoWatcher —— 主线小章节「看」：站内内置课程视频播放 + 真实观看计时。
 *
 * 取数：父级（StageLearnPage）传入本章全部内置视频 `videos`（StageVideoDto[]）与
 *       过线阈值 `requiredVideoSec`（=本章视频总时长×80%）。视频字节流用
 *       `videoStreamUrl(id)` 直连后端 `/api/course/videos/{id}`（支持 Range，匿名放行）。
 * 计时：监听 `<video>` 的 `timeupdate`，按「每次前进量」累加真实观看秒数（forward-delta）：
 *       - 仅在标签页可见且未过线时计；
 *       - 单次前进 >MAX_TICK_GAP_SEC（拖动/快进/切视频）视为跳转，不计入、只对齐基准；
 *       - totalWatched 跨视频累加，封顶 requiredVideoSec。
 *       该模型天然规避「拖到末尾秒过」（快进不计）与后台挂机刷（不可见不计）。
 * 打点：每攒满 BATCH_SECONDS 秒批量 `postVideoProgress(stageId, deltaSec)` 一次；
 *       切后台 / 暂停 / 切视频 / 卸载时 flush 已攒秒数。成功后 emit('progress')；
 *       失败退回 pending，下一批重试（服务端打点幂等 + 限频兜底）。
 * 过线：累计达 requiredVideoSec 即停表、flush 收尾、emit('passed')，并显示「已过线 ✓」。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { postVideoProgress } from '@/api/mainline';
import type { StageVideoDto } from '@/api/mainline';
import { videoStreamUrl } from '@/api/courseVideo';
import StageContextTutor from './StageContextTutor.vue';
import type { StageTaskProgress } from './stageTypes';

const props = defineProps<{
  stageId: number;
  videos: StageVideoDto[];
  requiredVideoSec: number;
  initialWatchedSec: number;
  /** 本节知识点（传给锚定问答，收敛课件 RAG 检索范围）。 */
  kcCodes?: string[];
}>();

const emit = defineEmits<{
  (e: 'progress', p: StageTaskProgress): void;
  (e: 'passed'): void;
}>();

/**
 * 攒够多少秒批量上报一次。后端限频为「每 stage 每分钟至多 2 次**有效**计入」（超频静默 no-op
 * 且不报错，见 VideoProgressThrottle）——批间隔必须 ≥30s，否则多余批次会被服务端丢弃、
 * 本地却已清零 pending，导致服务端累计时长慢于真实观看（刷新后进度回退）。
 */
const BATCH_SECONDS = 30;
/** 单次 timeupdate 前进上限：超过即视为拖动/快进/换源，不计入观看时长（防刷）。 */
const MAX_TICK_GAP_SEC = 2;

/** 过线阈值取整（父级按总时长×80% 传入，可能为小数；取整避免 floor 永不达标）。 */
const requiredSec = computed<number>(() => Math.max(0, Math.round(props.requiredVideoSec)));

function clampInitial(): number {
  const init = Math.max(0, Math.floor(props.initialWatchedSec));
  return Math.min(init, requiredSec.value);
}

/** 当前累计已观看秒数（权威展示值，封顶 requiredSec）。 */
const watchedSec = ref<number>(clampInitial());
/** 已攒、尚未成功上报的秒数（上报成功清零；失败退回重试）。 */
const pendingDelta = ref<number>(0);
/** 是否已过线（达标后停表、不再上报）。 */
const crossed = ref<boolean>(clampInitial() >= requiredSec.value);
/** 当前播放的视频 id（默认清单第 1 个）。 */
const currentId = ref<number | null>(props.videos[0]?.id ?? null);
/** 右栏「选集」是否展开（默认展开；收起后空间让给问答面板）。 */
const listOpen = ref<boolean>(true);

const currentTitle = computed<string>(
  () => props.videos.find((v) => v.id === currentId.value)?.title ?? '',
);

function toggleList(): void {
  listOpen.value = !listOpen.value;
}

/** 上一帧 timeupdate 的 currentTime，用于求前进量；切视频/换源时归零。 */
let lastTime = 0;
/** 不足 1 秒的前进量累加器（攒满 1 秒进位到 watchedSec）。 */
let carry = 0;

const currentStreamUrl = computed<string>(() =>
  currentId.value !== null ? videoStreamUrl(currentId.value) : '',
);

// 锚定「这段视频」的百晓助教问答：站内视频无字幕文本，context 用 视频标题 + 本节 KC，
// 由助教结合课件 RAG 作答。contextProvider 取「此刻」所选视频标题（切片后变化）。
const ctxKcCodes = computed<string[]>(() => props.kcCodes ?? []);
function currentVideoContext(): string {
  const v = props.videos.find((x) => x.id === currentId.value);
  const title = v?.title ? `当前视频：《${v.title}》` : '当前课程视频';
  const kc = ctxKcCodes.value.length ? `；本节知识点：${ctxKcCodes.value.join('、')}` : '';
  return `${title}${kc}`;
}

const progressPct = computed<number>(() => {
  if (requiredSec.value <= 0) return 100;
  return Math.min(100, Math.round((watchedSec.value / requiredSec.value) * 100));
});

function mmss(sec: number): string {
  const s = Math.max(0, Math.floor(sec));
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m}:${String(r).padStart(2, '0')}`;
}

function isVisible(): boolean {
  return typeof document === 'undefined' || document.visibilityState === 'visible';
}

async function flush(): Promise<void> {
  if (pendingDelta.value <= 0) return;
  // 同步清零再 await：并发 flush 看到 0 直接返回，杜绝重复上报；失败再退回。
  const delta = pendingDelta.value;
  pendingDelta.value = 0;
  try {
    // 只 await 成功；以本地 watchedSec 为权威向上汇报（不读返回体，规避 DTO 形状差异）。
    await postVideoProgress(props.stageId, delta);
    emit('progress', { stageId: props.stageId, videoWatchedSec: watchedSec.value });
  } catch {
    pendingDelta.value += delta;
  }
}

function accumulate(delta: number): void {
  carry += delta;
  while (carry >= 1 && watchedSec.value < requiredSec.value) {
    carry -= 1;
    watchedSec.value += 1;
    pendingDelta.value += 1;
  }
  if (watchedSec.value >= requiredSec.value) {
    crossed.value = true;
    carry = 0;
    void flush();
    emit('passed');
    return;
  }
  if (pendingDelta.value >= BATCH_SECONDS) {
    void flush();
  }
}

function onTimeUpdate(e: Event): void {
  const el = e.target as HTMLVideoElement | null;
  if (!el) return;
  const now = el.currentTime;
  const delta = now - lastTime;
  // 无论是否计入，都对齐基准，避免「不可见期间播放」在切回后被一次性补算。
  lastTime = now;
  if (crossed.value || !isVisible()) return;
  // 负前进（回拖）或超阈（拖动/快进/换源）只对齐基准、不计观看时长。
  if (!(delta > 0) || delta > MAX_TICK_GAP_SEC) return;
  accumulate(delta);
}

function onPause(): void {
  void flush();
}

function selectVideo(id: number): void {
  if (id === currentId.value) return;
  // 切视频前把已攒秒数报上去；新源 currentTime 归零，重置基准与小数累加器。
  void flush();
  currentId.value = id;
  lastTime = 0;
  carry = 0;
}

function onVisibilityChange(): void {
  if (!isVisible()) void flush();
}

watch(
  () => props.videos,
  (list) => {
    if (currentId.value === null || !list.some((v) => v.id === currentId.value)) {
      currentId.value = list[0]?.id ?? null;
      lastTime = 0;
      carry = 0;
    }
  },
);

onMounted(() => {
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', onVisibilityChange);
  }
});

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') {
    document.removeEventListener('visibilitychange', onVisibilityChange);
  }
  void flush();
});
</script>

<template>
  <section
    class="stage-video"
    data-testid="stage-video"
  >
    <header class="stage-video__head">
      <h3 class="stage-video__title">
        <span
          class="stage-video__icon"
          aria-hidden="true"
        >▶</span> 看 · 视频讲解
      </h3>
      <span class="stage-video__hint">仅本页可见时计时，累计观看 {{ mmss(requiredSec) }} 即过线</span>
      <span
        v-if="crossed"
        class="stage-video__passed"
        data-testid="stage-video-passed"
      >已过线 ✓</span>
    </header>

    <div
      v-if="videos.length > 0"
      class="stage-video__player"
    >
      <!-- 左：播放器 + 观看进度 -->
      <div class="stage-video__main">
        <div class="stage-video__frame">
          <video
            :key="currentId ?? 'none'"
            class="stage-video__el"
            data-testid="stage-video-el"
            :src="currentStreamUrl"
            controls
            playsinline
            preload="metadata"
            @timeupdate="onTimeUpdate"
            @pause="onPause"
          />
        </div>
        <div class="stage-video__bar">
          <div
            class="stage-video__bar-fill"
            :style="{ width: `${progressPct}%` }"
          />
        </div>
        <div class="stage-video__foot">
          <span
            class="stage-video__count"
            data-testid="stage-video-count"
          >已观看 {{ mmss(watchedSec) }} / {{ mmss(requiredSec) }}</span>
          <span
            v-if="currentTitle"
            class="stage-video__now"
          >正在播放：{{ currentTitle }}</span>
        </div>
      </div>

      <!-- 右：可收纳「选集」 + 问答面板填充省出空间（B 站式右栏） -->
      <aside class="stage-video__side">
        <section
          class="stage-video__playlist"
          :class="{ 'stage-video__playlist--open': listOpen }"
        >
          <button
            type="button"
            class="stage-video__playlist-toggle"
            :aria-expanded="listOpen"
            data-testid="stage-video-list-toggle"
            @click="toggleList"
          >
            <span class="stage-video__playlist-label">选集</span>
            <span class="stage-video__playlist-count">{{ videos.length }}P</span>
            <span
              class="stage-video__playlist-chev"
              aria-hidden="true"
            >{{ listOpen ? '▾' : '▸' }}</span>
          </button>
          <ul
            v-show="listOpen"
            class="stage-video__list"
          >
            <li
              v-for="(v, idx) in videos"
              :key="v.id"
              class="stage-video__item"
              :class="{ 'stage-video__item--current': v.id === currentId }"
              :data-testid="`stage-video-item-${v.id}`"
            >
              <button
                type="button"
                class="stage-video__pick"
                :aria-current="v.id === currentId ? 'true' : undefined"
                @click="selectVideo(v.id)"
              >
                <span
                  class="stage-video__no"
                  aria-hidden="true"
                >{{ v.id === currentId ? '▶' : idx + 1 }}</span>
                <span class="stage-video__name">{{ v.title }}</span>
                <span class="stage-video__dur">{{ mmss(v.durationSec) }}</span>
              </button>
            </li>
          </ul>
        </section>

        <!-- 锚定「这段视频」的百晓助教问答（基于视频标题 + 本节 KC + 课件 RAG 作答） -->
        <StageContextTutor
          class="stage-video__tutor"
          :kc-codes="ctxKcCodes"
          scope-label="这段视频"
          :context-provider="currentVideoContext"
        />
      </aside>
    </div>
    <p
      v-else
      class="stage-video__empty"
      data-testid="stage-video-empty"
    >
      本章暂无内置视频，可先完成「学」与「练」。
    </p>
  </section>
</template>

<style scoped>
.stage-video {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  height: 100%;
  min-height: 0;
}
.stage-video__head {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  flex: 0 0 auto;
}
.stage-video__title {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: var(--pyt-font-size-lg);
  color: var(--pyt-color-text-primary);
  white-space: nowrap;
}
.stage-video__icon {
  color: var(--pyt-color-danger);
}
.stage-video__hint {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage-video__passed {
  flex: 0 0 auto;
  font-size: var(--pyt-font-size-xs);
  padding: 2px 8px;
  background: var(--pyt-color-success-bg);
  color: var(--pyt-color-success);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  white-space: nowrap;
}

/* ── B 站式两栏：播放器大区 + 右侧选集/问答栏 ── */
.stage-video__player {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(240px, 28%, 330px);
  gap: var(--pyt-space-3);
  align-items: stretch;
}
.stage-video__main {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.stage-video__frame {
  flex: 1 1 auto;
  min-height: 200px;
  overflow: hidden;
  background: var(--pyt-color-terminal-bg);
  border: 2px solid var(--pyt-color-border-strong);
  box-shadow: 3px 3px 0 var(--pyt-color-shadow);
}
.stage-video__el {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
  background: #000;
}
.stage-video__side {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}

/* 可收纳选集（收起后空间让给下方问答） */
.stage-video__playlist {
  flex: 0 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-soft);
  overflow: hidden;
}
.stage-video__playlist--open {
  flex-shrink: 1;
}
.stage-video__playlist-toggle {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  width: 100%;
  min-height: 0;
  padding: var(--pyt-space-1) var(--pyt-space-2);
  background: var(--pyt-color-bg-sunken);
  border: 0;
  box-shadow: none;
  cursor: pointer;
  color: var(--pyt-color-text-primary);
}
.stage-video__playlist-toggle:active {
  transform: none;
  box-shadow: none;
}
.stage-video__playlist-toggle:hover {
  background: var(--pyt-color-warning-bg);
}
.stage-video__playlist-label {
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: var(--pyt-font-size-sm);
  font-weight: 700;
}
.stage-video__playlist-count {
  font-family: var(--pyt-font-pixel-mono), monospace;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.stage-video__playlist-chev {
  margin-left: auto;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.stage-video__list {
  list-style: none;
  margin: 0;
  padding: var(--pyt-space-1);
  display: grid;
  gap: var(--pyt-space-1);
  overflow-y: auto;
  max-height: 38vh;
}
.stage-video__item {
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-bg-elevated);
  transition: border-color var(--pyt-motion-base), background var(--pyt-motion-base);
}
.stage-video__item:hover {
  border-color: var(--pyt-color-gold);
}
.stage-video__item--current {
  border-color: var(--pyt-color-gold);
  background: var(--pyt-color-warning-bg);
}
.stage-video__pick {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  width: 100%;
  min-height: 0;
  padding: 6px var(--pyt-space-2);
  background: transparent;
  border: 0;
  box-shadow: none;
  text-align: left;
  cursor: pointer;
  color: var(--pyt-color-text-primary);
}
.stage-video__pick:active {
  transform: none;
  box-shadow: none;
}
.stage-video__no {
  flex: 0 0 auto;
  width: 1.4em;
  height: 1.4em;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--pyt-font-pixel-mono), monospace;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  background: var(--pyt-color-bg-sunken);
  border-radius: var(--pyt-radius-sm);
}
.stage-video__item--current .stage-video__no {
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-gold);
}
.stage-video__name {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--pyt-font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage-video__item--current .stage-video__name {
  font-weight: 600;
}
.stage-video__dur {
  flex: 0 0 auto;
  font-family: var(--pyt-font-pixel-mono), monospace;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}

/* 问答面板占满选集收起后的剩余空间 */
.stage-video__tutor {
  flex: 1 1 0;
  min-height: 160px;
}

.stage-video__empty {
  margin: 0;
  padding: var(--pyt-space-4);
  text-align: center;
  font-size: var(--pyt-font-size-sm);
  line-height: 1.5;
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
  border: 1px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}
.stage-video__bar {
  flex: 0 0 auto;
  width: 100%;
  height: 8px;
  background: var(--pyt-color-bg-sunken);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  overflow: hidden;
}
.stage-video__bar-fill {
  height: 100%;
  background: var(--pyt-color-gold);
  transition: width var(--pyt-motion-base);
}
.stage-video__foot {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}
.stage-video__count {
  font-size: var(--pyt-font-size-xs);
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-text-muted);
  white-space: nowrap;
}
.stage-video__now {
  min-width: 0;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-hint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 900px) {
  .stage-video {
    height: auto;
  }
  .stage-video__player {
    grid-template-columns: minmax(0, 1fr);
  }
  .stage-video__frame {
    flex: 0 0 auto;
    aspect-ratio: 16 / 9;
    min-height: 0;
  }
  .stage-video__list {
    max-height: 200px;
  }
  .stage-video__tutor {
    min-height: 200px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .stage-video__bar-fill {
    transition: none;
  }
}
</style>
