<script setup lang="ts">
/**
 * StageVideoWatcher —— 主线小章节「看」：站内嵌入 B 站播放器 + 可见性计时。
 *
 * 取数：用 stage 的 video_keyword 经 `searchBilibili` 取首个精选短视频 → 取其 bvid →
 *       嵌入 `player.bilibili.com` iframe（跨域，无法读取真实播放态）。
 * 计时：跨域 iframe 无法监听 play/pause，故以「标签页可见 + 本地 watching 开关」近似
 *       「正在播放」（plan §14 已接受可被挂机刷的风险，由服务端限频/封顶兜底）。
 *       每秒累加 1，攒满 10 秒（或失焦/卸载/过线时）批量 `postVideoProgress(stageId, deltaSec)`。
 * 过线：累计达 requiredVideoSec 即停表、发 'passed'，不再上报。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { searchBilibili } from '@/api/bilibiliVideo';
import { postVideoProgress } from '@/api/mainline';
import type { StageTaskProgress } from './stageTypes';

const props = defineProps<{
  stageId: number;
  videoKeyword: string;
  requiredVideoSec: number;
  initialWatchedSec: number;
}>();

const emit = defineEmits<{
  (e: 'progress', p: StageTaskProgress): void;
  (e: 'passed'): void;
}>();

/** 攒够多少秒批量上报一次（plan §11.5 / §9：前端 10s 批量）。 */
const BATCH_SECONDS = 10;

type LoadStatus = 'loading' | 'ready' | 'empty' | 'error';

const bvid = ref<string | null>(null);
const status = ref<LoadStatus>('loading');
const watchedSec = ref<number>(Math.min(props.initialWatchedSec, props.requiredVideoSec));
const pendingDelta = ref<number>(0);
const crossed = ref<boolean>(props.initialWatchedSec >= props.requiredVideoSec);
/** 本地「正在观看」开关（近似播放态）：视频就绪后默认开，可手动暂停计时。 */
const watching = ref<boolean>(false);

let tickHandle: number | null = null;

const embedUrl = computed<string>(() =>
  bvid.value
    ? `https://player.bilibili.com/player.html?bvid=${bvid.value}&autoplay=0&danmaku=0&high_quality=1&p=1`
    : '',
);

const progressPct = computed<number>(() => {
  if (props.requiredVideoSec <= 0) return 100;
  return Math.min(100, Math.round((watchedSec.value / props.requiredVideoSec) * 100));
});

function mmss(sec: number): string {
  const s = Math.max(0, Math.floor(sec));
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m}:${String(r).padStart(2, '0')}`;
}

function currentVisibility(): DocumentVisibilityState {
  return typeof document !== 'undefined' ? document.visibilityState : 'visible';
}

function countable(): boolean {
  return watching.value && !crossed.value && currentVisibility() === 'visible';
}

async function flush(): Promise<void> {
  if (pendingDelta.value <= 0) return;
  const delta = pendingDelta.value;
  pendingDelta.value = 0;
  try {
    // 只 await 成功；以本地 watchedSec 为权威向上汇报（不读返回体，规避 DTO 形状差异）。
    await postVideoProgress(props.stageId, delta);
    emit('progress', { stageId: props.stageId, videoWatchedSec: watchedSec.value });
  } catch {
    // 失败不丢量：退回 pending，下一批重试（服务端打点幂等 + 限频）。
    pendingDelta.value += delta;
  }
}

function tick(): void {
  if (!countable()) return;
  watchedSec.value = Math.min(watchedSec.value + 1, props.requiredVideoSec);
  pendingDelta.value += 1;
  if (watchedSec.value >= props.requiredVideoSec) {
    crossed.value = true;
    watching.value = false;
    stopTick();
    void flush();
    emit('passed');
    return;
  }
  if (pendingDelta.value >= BATCH_SECONDS) {
    void flush();
  }
}

function startTick(): void {
  if (tickHandle !== null) return;
  tickHandle = window.setInterval(tick, 1000);
}

function stopTick(): void {
  if (tickHandle !== null) {
    window.clearInterval(tickHandle);
    tickHandle = null;
  }
}

function toggleWatching(): void {
  if (crossed.value) return;
  watching.value = !watching.value;
  if (!watching.value) {
    // 主动暂停：把已攒的秒数先报上去，避免暂停期间丢量。
    void flush();
  }
}

function onVisibilityChange(): void {
  // 切到后台：先把已观看秒数报上去（计时本身由 countable() 自动暂停）。
  if (currentVisibility() === 'hidden') {
    void flush();
  }
}

async function loadVideo(): Promise<void> {
  const keyword = props.videoKeyword.trim();
  if (!keyword) {
    status.value = 'empty';
    return;
  }
  status.value = 'loading';
  try {
    const list = await searchBilibili(keyword, 1);
    const first = list[0];
    if (first) {
      bvid.value = first.bvid;
      status.value = 'ready';
      if (!crossed.value) watching.value = true;
    } else {
      status.value = 'empty';
    }
  } catch {
    status.value = 'error';
  }
}

onMounted(() => {
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', onVisibilityChange);
  }
  startTick();
  void loadVideo();
});

onBeforeUnmount(() => {
  stopTick();
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
        <span class="stage-video__icon" aria-hidden="true">▶</span> 看 · 视频讲解
      </h3>
      <span
        v-if="crossed"
        class="stage-video__passed"
        data-testid="stage-video-passed"
      >已过线 ✓</span>
    </header>

    <p class="stage-video__hint">
      站内嵌入 B 站精选短视频（关键词「{{ videoKeyword }}」）。仅在本页可见时计时，
      累计观看 {{ mmss(requiredVideoSec) }} 即过线。
    </p>

    <div
      v-if="status === 'ready' && embedUrl"
      class="stage-video__frame"
    >
      <iframe
        class="stage-video__iframe"
        data-testid="stage-video-iframe"
        :src="embedUrl"
        title="B 站精选讲解"
        allowfullscreen
        referrerpolicy="no-referrer"
        scrolling="no"
        frameborder="0"
      />
    </div>
    <p
      v-else-if="status === 'loading'"
      class="stage-video__placeholder"
      data-testid="stage-video-loading"
    >
      正在检索精选讲解视频…
    </p>
    <p
      v-else
      class="stage-video__placeholder"
      data-testid="stage-video-empty"
    >
      <template v-if="status === 'error'">
        视频检索暂不可用（网络 / 风控），可
        <button
          type="button"
          class="stage-video__retry"
          data-testid="stage-video-retry"
          @click="loadVideo"
        >重试</button>
        或先去「学」与「练」。
      </template>
      <template v-else>
        暂无匹配的精选短视频，可先完成「学」与「练」。
      </template>
    </p>

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
      >已观看 {{ mmss(watchedSec) }} / {{ mmss(requiredVideoSec) }}</span>
      <button
        v-if="status === 'ready' && !crossed"
        type="button"
        class="stage-video__toggle"
        data-testid="stage-video-toggle"
        @click="toggleWatching"
      >
        {{ watching ? '⏸ 暂停计时' : '▶ 继续计时' }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.stage-video {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.stage-video__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}
.stage-video__title {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: var(--pyt-font-size-lg);
  color: var(--pyt-color-text-primary);
}
.stage-video__icon {
  color: var(--pyt-color-danger);
}
.stage-video__passed {
  font-size: var(--pyt-font-size-xs);
  padding: 2px 8px;
  background: var(--pyt-color-success-bg);
  color: var(--pyt-color-success);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  white-space: nowrap;
}
.stage-video__hint {
  margin: 0;
  font-size: var(--pyt-font-size-xs);
  line-height: 1.5;
  color: var(--pyt-color-text-muted);
}
.stage-video__frame {
  position: relative;
  width: 100%;
  height: 0;
  padding-top: 56.25%;
  overflow: hidden;
  background: var(--pyt-color-terminal-bg);
  border: 2px solid var(--pyt-color-border-strong);
  box-shadow: 3px 3px 0 var(--pyt-color-shadow);
}
.stage-video__iframe {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
}
.stage-video__placeholder {
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
.stage-video__retry {
  font-family: inherit;
  font-size: var(--pyt-font-size-xs);
  padding: 2px 10px;
  margin: 0 2px;
  background: var(--pyt-color-warning-bg);
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-color-shadow);
}
.stage-video__retry:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}
.stage-video__bar {
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}
.stage-video__count {
  font-size: var(--pyt-font-size-xs);
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-text-muted);
}
.stage-video__toggle {
  font-family: var(--pyt-font-pixel), monospace;
  font-size: var(--pyt-font-size-xs);
  padding: 2px 10px;
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
  box-shadow: 0 2px 0 var(--pyt-color-border);
}
.stage-video__toggle:active {
  transform: translateY(2px);
  box-shadow: none;
}
@media (prefers-reduced-motion: reduce) {
  .stage-video__bar-fill {
    transition: none;
  }
}
</style>
