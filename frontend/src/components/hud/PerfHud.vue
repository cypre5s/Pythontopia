<script setup lang="ts">
// 性能诊断框：展示 FPS（min/avg/max + 折线）/ 帧时间 / draw call / 对象 / Tween / 纹理 /
// 画布 / 堆 / 渲染后端 / 场景 / Web Vitals / 地图加载耗时等运行时指标。F3 切换显隐，默认关闭。
// 隔离约束：不直接 import phaser，只消费 phaserGame adapter 的快照 DTO
// （getPerfSnapshot / PerfSnapshot）与 perfMonitor 只读报表，符合 AGENTS「业务层无 Phaser 依赖」。
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { getPerfSnapshot, type PerfSnapshot } from '@game/adapters/phaserGame';
import { perfMonitor, type WebVitalName, type WebVitalRating } from '@/services/perfMonitor';
import { useGameSettingsStore } from '@stores/gameSettings';

// 轮询间隔：250ms（4Hz）足够观察波动，又不会让诊断框自身成为可观测开销。
const POLL_INTERVAL_MS = 250;
// 画布后备像素超过此阈值（约 2.5M ≈ 1920×1300）时提示填充率偏高。
const FILL_WARN_PIXELS = 2_500_000;
const FPS_GOOD = 55;
const FPS_WARN = 30;
// FPS 折线/统计的滚动窗口长度（样本数）。60 样本 × 250ms ≈ 15s 观察窗。
const FPS_HISTORY = 60;
// 折线图尺寸与 FPS 上限（纵轴归一化用）。
const SPARK_W = 132;
const SPARK_H = 28;
const SPARK_MAX_FPS = 70;

const gameSettings = useGameSettingsStore();

const snap = ref<PerfSnapshot | null>(null);
const fpsHistory = ref<number[]>([]);
const vitals = ref<Array<{ name: WebVitalName; value: number; rating: WebVitalRating }>>([]);
const mapLoads = ref<Array<{ map: string; avgMs: number; maxMs: number; count: number }>>([]);
let timer: number | null = null;

const visible = computed<boolean>(() => gameSettings.display.showPerfHud);

const fpsClass = computed<string>(() => {
  const s = snap.value;
  if (!s) return '';
  if (s.fps >= FPS_GOOD) return 'perf-hud__fps--good';
  if (s.fps >= FPS_WARN) return 'perf-hud__fps--warn';
  return 'perf-hud__fps--bad';
});

const fpsText = computed<string>(() => (snap.value ? String(snap.value.fps) : '--'));
const frameMsText = computed<string>(() => (snap.value ? `${snap.value.frameMs} ms` : '--'));
const objectsText = computed<string>(() => (snap.value ? String(snap.value.objects) : '--'));
const tweensText = computed<string>(() => (snap.value ? String(snap.value.tweens) : '--'));
const texturesText = computed<string>(() => (snap.value ? String(snap.value.textures) : '--'));
const canvasText = computed<string>(() =>
  snap.value ? `${snap.value.canvasW}×${snap.value.canvasH}` : '--',
);
const dprText = computed<string>(() => (snap.value ? String(snap.value.dpr) : '--'));
const rendererText = computed<string>(() => snap.value?.rendererType ?? '--');
const heapMB = computed<number | null>(() => snap.value?.heapMB ?? null);
const scenesText = computed<string>(() => snap.value?.activeScenes.join(', ') ?? '');
const drawCallsText = computed<string>(() => {
  const d = snap.value?.drawCalls;
  return d == null ? '--' : String(d);
});
const fillWarn = computed<boolean>(() => {
  const s = snap.value;
  return !!s && s.canvasW * s.canvasH > FILL_WARN_PIXELS;
});

// FPS 统计窗口：min / avg / max，用来发现「平均还行但有掉帧尖刺」的情况。
const fpsStats = computed<{ min: number; avg: number; max: number } | null>(() => {
  const h = fpsHistory.value;
  if (h.length === 0) return null;
  let min = Infinity;
  let max = -Infinity;
  let sum = 0;
  for (const f of h) {
    if (f < min) min = f;
    if (f > max) max = f;
    sum += f;
  }
  return { min, max, avg: Math.round(sum / h.length) };
});

// 折线点串：把 FPS 历史归一化到 SPARK_W×SPARK_H 的 polyline points。
const sparkPoints = computed<string>(() => {
  const h = fpsHistory.value;
  if (h.length < 2) return '';
  const step = SPARK_W / (FPS_HISTORY - 1);
  return h
    .map((fps, i) => {
      const x = i * step;
      const ratio = Math.min(fps, SPARK_MAX_FPS) / SPARK_MAX_FPS;
      const y = SPARK_H - ratio * SPARK_H;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
});

function vitalText(name: WebVitalName, value: number): string {
  // CLS 是无量纲分值（保留 3 位）；其余为毫秒。
  return name === 'CLS' ? value.toFixed(3) : `${Math.round(value)} ms`;
}

function tick(): void {
  snap.value = getPerfSnapshot();
  if (snap.value) {
    const h = fpsHistory.value;
    h.push(snap.value.fps);
    if (h.length > FPS_HISTORY) h.splice(0, h.length - FPS_HISTORY);
  }
  const report = perfMonitor.getReport();
  // Web Vitals：每个 name 取最新一条（value + rating）。
  const latest = new Map<WebVitalName, { value: number; rating: WebVitalRating }>();
  for (const v of report.vitals) {
    latest.set(v.name, { value: v.value, rating: v.rating });
  }
  const order: WebVitalName[] = ['LCP', 'FCP', 'INP', 'CLS', 'TTFB'];
  vitals.value = order
    .filter((n) => latest.has(n))
    .map((n) => ({ name: n, ...(latest.get(n) as { value: number; rating: WebVitalRating }) }));
  // 地图加载耗时：聚合 map_load:* 区段。
  mapLoads.value = report.durations
    .filter((d) => d.label.startsWith('map_load:'))
    .map((d) => ({
      map: d.label.slice('map_load:'.length),
      avgMs: Math.round(d.avgMs),
      maxMs: Math.round(d.maxMs),
      count: d.count,
    }));
}

function start(): void {
  if (timer !== null || typeof window === 'undefined') return;
  tick();
  timer = window.setInterval(tick, POLL_INTERVAL_MS);
}

function stop(): void {
  if (timer !== null) {
    window.clearInterval(timer);
    timer = null;
  }
  snap.value = null;
  fpsHistory.value = [];
  vitals.value = [];
  mapLoads.value = [];
}

function hide(): void {
  gameSettings.updateDisplay({ showPerfHud: false });
}

function onKey(event: KeyboardEvent): void {
  if (event.key === 'F3') {
    event.preventDefault();
    gameSettings.updateDisplay({ showPerfHud: !gameSettings.display.showPerfHud });
  }
}

watch(visible, (v) => {
  if (v) start();
  else stop();
});

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', onKey);
  }
  if (visible.value) start();
});

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKey);
  }
  stop();
});
</script>

<template>
  <div
    v-if="visible"
    class="perf-hud"
    role="status"
    aria-live="off"
    aria-label="性能诊断"
    data-testid="perf-hud"
  >
    <div class="perf-hud__head">
      <span class="perf-hud__title">性能诊断</span>
      <button
        type="button"
        class="perf-hud__close"
        title="关闭（F3）"
        aria-label="关闭性能诊断"
        @click="hide"
      >
        ×
      </button>
    </div>

    <div
      class="perf-hud__fps"
      :class="fpsClass"
      data-testid="perf-hud-fps"
    >
      {{ fpsText }}<small>FPS</small>
    </div>

    <div
      v-if="fpsStats"
      class="perf-hud__fpsstats"
      data-testid="perf-hud-fpsstats"
    >
      <span>min <b>{{ fpsStats.min }}</b></span>
      <span>avg <b>{{ fpsStats.avg }}</b></span>
      <span>max <b>{{ fpsStats.max }}</b></span>
    </div>

    <svg
      v-if="sparkPoints"
      class="perf-hud__spark"
      :viewBox="`0 0 ${SPARK_W} ${SPARK_H}`"
      :width="SPARK_W"
      :height="SPARK_H"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <polyline
        :points="sparkPoints"
        fill="none"
        stroke="currentColor"
        stroke-width="1"
      />
    </svg>

    <ul class="perf-hud__rows">
      <li><span>帧时间</span><b>{{ frameMsText }}</b></li>
      <li><span>draw call</span><b>{{ drawCallsText }}</b></li>
      <li><span>对象</span><b>{{ objectsText }}</b></li>
      <li><span>Tween</span><b>{{ tweensText }}</b></li>
      <li><span>纹理</span><b>{{ texturesText }}</b></li>
      <li :class="{ 'perf-hud__row--warn': fillWarn }">
        <span>画布</span><b>{{ canvasText }}</b>
      </li>
      <li><span>DPR</span><b>{{ dprText }}</b></li>
      <li v-if="heapMB !== null"><span>堆</span><b>{{ heapMB }} MB</b></li>
      <li><span>后端</span><b>{{ rendererText }}</b></li>
      <li v-if="scenesText"><span>场景</span><b>{{ scenesText }}</b></li>
    </ul>

    <template v-if="vitals.length">
      <div class="perf-hud__section">Web Vitals</div>
      <ul class="perf-hud__rows" data-testid="perf-hud-vitals">
        <li
          v-for="v in vitals"
          :key="v.name"
          :class="`perf-hud__vital--${v.rating}`"
        >
          <span>{{ v.name }}</span><b>{{ vitalText(v.name, v.value) }}</b>
        </li>
      </ul>
    </template>

    <template v-if="mapLoads.length">
      <div class="perf-hud__section">地图加载 avg/max</div>
      <ul class="perf-hud__rows" data-testid="perf-hud-maploads">
        <li
          v-for="m in mapLoads"
          :key="m.map"
        >
          <span>{{ m.map }}</span><b>{{ m.avgMs }}/{{ m.maxMs }} ×{{ m.count }}</b>
        </li>
      </ul>
    </template>

    <p v-if="fillWarn" class="perf-hud__hint">
      画布像素偏高，疑似填充率瓶颈：可降低 DPR / 渲染分辨率
    </p>
  </div>
</template>

<style scoped>
.perf-hud {
  position: fixed;
  left: 8px;
  bottom: 8px;
  z-index: 120;
  min-width: 132px;
  padding: 6px 8px 8px;
  background: rgba(12, 16, 12, 0.82);
  border: 1px solid rgba(212, 175, 55, 0.45);
  border-radius: 4px;
  color: #e8f0e0;
  font-family: var(--pyt-font-pixel);
  font-size: 11px;
  line-height: 1.4;
  pointer-events: auto;
  user-select: none;
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.4);
}

.perf-hud__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 2px;
}
.perf-hud__title {
  font-size: 10px;
  letter-spacing: 0.5px;
  color: var(--pyt-text-accent, var(--pyt-color-gold-bright));
}
.perf-hud__close {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: transparent;
  border: 1px solid rgba(212, 175, 55, 0.4);
  border-radius: 2px;
  color: #e8f0e0;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: background 120ms, color 120ms;
}
.perf-hud__close:hover {
  background: rgba(212, 175, 55, 0.3);
  color: #fff;
}

.perf-hud__fps {
  font-size: 22px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  color: #9fd88f;
}
.perf-hud__fps small {
  font-size: 10px;
  font-weight: 400;
  margin-left: 4px;
  opacity: 0.7;
}
.perf-hud__fps--good { color: #7ed47e; }
.perf-hud__fps--warn { color: #ffcc55; }
.perf-hud__fps--bad { color: #ff6b6b; }

.perf-hud__rows {
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
}
.perf-hud__rows li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.perf-hud__rows li span {
  color: rgba(232, 240, 224, 0.6);
}
.perf-hud__rows li b {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.perf-hud__row--warn b {
  color: #ffcc55;
}

.perf-hud__fpsstats {
  display: flex;
  gap: 8px;
  margin-top: 2px;
  font-size: 9px;
  color: rgba(232, 240, 224, 0.55);
}
.perf-hud__fpsstats b {
  color: #e8f0e0;
  font-variant-numeric: tabular-nums;
}
.perf-hud__spark {
  display: block;
  margin: 4px 0 2px;
  color: #7ed47e;
  opacity: 0.85;
}
.perf-hud__section {
  margin-top: 6px;
  padding-top: 4px;
  border-top: 1px dashed rgba(212, 175, 55, 0.25);
  font-size: 9px;
  letter-spacing: 0.5px;
  color: var(--pyt-text-accent, var(--pyt-color-gold-bright));
}
.perf-hud__vital--good b { color: #7ed47e; }
.perf-hud__vital--needs-improvement b { color: #ffcc55; }
.perf-hud__vital--poor b { color: #ff6b6b; }

.perf-hud__hint {
  margin: 6px 0 0;
  max-width: 180px;
  font-size: 9px;
  line-height: 1.3;
  color: #ffcc55;
}
</style>
