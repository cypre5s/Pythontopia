<script setup lang="ts">
/**
 * BilibiliVideoCards —— 「B站精选讲解」外部视频卡片网格。
 *
 * 纯展示型外部资源：仅渲染封面缩略图 + 元数据，整卡是跳转 B 站的外链（新标签打开），
 * 不进课件 grounding、不内嵌下载。封面用 `referrerpolicy="no-referrer"` 规避 hdslb 防盗链，
 * 加载失败回退占位。检索失败 / 空结果都降级为占位文案（不报错、不白屏）。
 * 点击卡片时 emit('opened')；打开外部链接不代表已观看，也不作为学习证据。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue';
import {
  fetchBilibiliInsight,
  searchBilibili,
  type BilibiliInsightDto,
  type BilibiliVideoDto,
} from '@/api/bilibiliVideo';
import VideoInsightView from '@/components/video/VideoInsightView.vue';

const props = withDefaults(
  defineProps<{
    keyword: string;
    limit?: number;
    heading?: string;
    /** 版块说明：解释这些是外部资源、如何使用、时长定位（空串则不显示）。 */
    caption?: string;
    /**
     * 学生问题 / 学习意图：传给 AI 讲解概述与看点排序（默认空串）。
     * 缺省时用 `keyword` 兜底，**不破坏**只传 `:keyword` 的现有调用方。
     */
    question?: string;
    /** 关联 KC 编码，影响概述与看点优先级（默认空数组）。 */
    kcCodes?: string[];
  }>(),
  {
    limit: 8,
    heading: 'B站精选讲解',
    caption:
      '外部资源 · 点击卡片前往 B 站观看。已为初学者优先精选 10 分钟以内的短视频，作为课件之外的补充讲解。',
    question: '',
    kcCodes: () => [],
  },
);

const emit = defineEmits<{ (e: 'opened'): void }>();

type LoadStatus = 'loading' | 'ready' | 'empty' | 'error';

const videos = ref<BilibiliVideoDto[]>([]);
const status = ref<LoadStatus>('loading');
const coverErrored = ref<Set<string>>(new Set());

let loadToken = 0;

// ───────────────────────────── AI 讲解 / 看点（概述 + 关键时间点） ─────────────────────────────
//
// 每张卡有「AI 讲解 / 看点」按钮，点开后在网格下方展开**全宽** insight 面板（单卡展开，
// 切换其它卡会替换面板，保持卡片网格等高不被撑乱）。拉取生命周期（loading/error）在此处理，
// VideoInsightView 只吃最终数据并渲染 overview + 可点击时间点。

type InsightStatus = 'loading' | 'ready' | 'error';
interface InsightEntry {
  status: InsightStatus;
  data: BilibiliInsightDto | null;
}

// 按 bvid 缓存 insight（reactive Map：get/has 读追踪、set 写触发，避免重复请求）。
const insightByBvid = reactive(new Map<string, InsightEntry>());
const expandedBvid = ref<string | null>(null);

const expandedVideo = computed<BilibiliVideoDto | null>(
  () => videos.value.find((v) => v.bvid === expandedBvid.value) ?? null,
);
const expandedEntry = computed<InsightEntry | null>(() =>
  expandedBvid.value ? insightByBvid.get(expandedBvid.value) ?? null : null,
);

async function loadInsight(video: BilibiliVideoDto): Promise<void> {
  const bvid = video.bvid;
  insightByBvid.set(bvid, { status: 'loading', data: null });
  // question 缺省 → 用关键词兜底（兼容只传 :keyword 的旧调用方）。
  const question = (props.question ?? '').trim() || props.keyword.trim();
  try {
    const data = await fetchBilibiliInsight(bvid, question, props.kcCodes ?? []);
    insightByBvid.set(bvid, { status: 'ready', data });
  } catch {
    insightByBvid.set(bvid, { status: 'error', data: null });
  }
}

async function toggleInsight(video: BilibiliVideoDto): Promise<void> {
  if (expandedBvid.value === video.bvid) {
    expandedBvid.value = null;
    return;
  }
  expandedBvid.value = video.bvid;
  const existing = insightByBvid.get(video.bvid);
  if (existing && existing.status === 'ready') return; // 已成功拉取过，直接复用缓存
  await loadInsight(video);
}

function collapseInsight(): void {
  expandedBvid.value = null;
}

function retryExpandedInsight(): void {
  const video = expandedVideo.value;
  if (video) void loadInsight(video);
}

function formatDuration(sec: number): string {
  if (!sec || sec <= 0) return '';
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = Math.floor(sec % 60);
  const ss = String(s).padStart(2, '0');
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${m}:${ss}`;
}

function formatCount(n: number): string {
  if (!n || n <= 0) return '0';
  if (n >= 100_000_000) return `${(n / 100_000_000).toFixed(1)}亿`;
  if (n >= 10_000) return `${(n / 10_000).toFixed(1)}万`;
  return String(n);
}

function onCoverError(bvid: string): void {
  if (coverErrored.value.has(bvid)) return;
  const next = new Set(coverErrored.value);
  next.add(bvid);
  coverErrored.value = next;
}

function onOpen(): void {
  emit('opened');
}

async function load(): Promise<void> {
  const keyword = props.keyword.trim();
  coverErrored.value = new Set();
  // 关键词变化 → 旧视频的 insight / 展开态全部作废，避免串台。
  expandedBvid.value = null;
  insightByBvid.clear();
  if (!keyword) {
    videos.value = [];
    status.value = 'empty';
    return;
  }
  const current = ++loadToken;
  status.value = 'loading';
  try {
    const data = await searchBilibili(keyword, props.limit);
    if (current !== loadToken) return;
    videos.value = data;
    status.value = data.length > 0 ? 'ready' : 'empty';
  } catch {
    if (current !== loadToken) return;
    videos.value = [];
    status.value = 'error';
  }
}

onMounted(load);
watch(() => props.keyword, load);
</script>

<template>
  <section
    class="bili-cards"
    data-testid="bili-cards"
  >
    <header class="bili-cards__head">
      <h3 class="bili-cards__title">
        {{ heading }}
      </h3>
      <span class="bili-cards__src">外部资源·B站</span>
    </header>

    <p
      v-if="caption"
      class="bili-cards__caption"
      data-testid="bili-cards-caption"
    >
      {{ caption }}
    </p>

    <p
      v-if="status === 'loading'"
      class="bili-cards__hint"
      data-testid="bili-cards-loading"
    >
      正在检索 B 站精选讲解…
    </p>

    <ul
      v-else-if="status === 'ready'"
      class="bili-cards__grid"
    >
      <li
        v-for="v in videos"
        :key="v.bvid"
        class="bili-card"
      >
        <a
          class="bili-card__link"
          :href="v.url"
          target="_blank"
          rel="noopener noreferrer"
          :title="v.title"
          @click="onOpen"
        >
          <div class="bili-card__cover">
            <img
              v-if="v.coverUrl && !coverErrored.has(v.bvid)"
              :src="v.coverUrl"
              :alt="v.title"
              class="bili-card__img"
              referrerpolicy="no-referrer"
              loading="lazy"
              @error="onCoverError(v.bvid)"
            >
            <div
              v-else
              class="bili-card__cover-fallback"
              aria-hidden="true"
            >
              ▶
            </div>
            <span
              v-if="formatDuration(v.durationSec)"
              class="bili-card__dur"
            >{{ formatDuration(v.durationSec) }}</span>
            <span class="bili-card__badge">外部资源·B站</span>
          </div>
          <p class="bili-card__name">{{ v.title }}</p>
          <p
            class="bili-card__desc"
            :class="{ 'bili-card__desc--empty': !v.aiSummary }"
          >
            <span
              class="bili-card__ai-tag"
              :title="v.aiSummary ? 'AI 依据标题/简介/时长生成的速览，仅供参考' : 'AI 速览暂不可用'"
            >AI</span>
            <span class="bili-card__ai-text">{{ v.aiSummary || '点击前往 B 站观看' }}</span>
          </p>
          <div class="bili-card__meta">
            <span class="bili-card__up">UP主 {{ v.author || '—' }}</span>
            <span class="bili-card__play">▶ {{ formatCount(v.play) }}</span>
          </div>
        </a>
        <div class="bili-card__actions">
          <button
            type="button"
            class="bili-card__insight-btn"
            :class="{ 'bili-card__insight-btn--active': expandedBvid === v.bvid }"
            :aria-expanded="expandedBvid === v.bvid"
            data-testid="bili-insight-toggle"
            @click="toggleInsight(v)"
          >
            <span class="bili-card__insight-tag">AI</span>
            <span>{{ expandedBvid === v.bvid ? '收起看点' : '讲解 / 看点' }}</span>
          </button>
        </div>
      </li>
    </ul>

    <p
      v-else
      class="bili-cards__hint"
      data-testid="bili-cards-empty"
    >
      <template v-if="status === 'error'">
        B 站检索暂不可用（网络 / 登录态 / 风控均可能导致），可
        <button
          type="button"
          class="bili-cards__retry"
          data-testid="bili-cards-retry"
          @click="load"
        >
          重试
        </button>
        或回到课件问答。
      </template>
      <template v-else>
        暂无相关 B 站讲解，可换个关键词或使用课件问答。
      </template>
    </p>

    <!-- AI 讲解 / 看点：全宽内联面板（单卡展开，不撑乱上方等高网格）。 -->
    <section
      v-if="expandedVideo"
      class="bili-insight-panel"
      data-testid="bili-insight-panel"
    >
      <header class="bili-insight-panel__head">
        <span class="bili-insight-panel__title">
          <span class="bili-card__insight-tag">AI</span>
          讲解 / 看点 · {{ expandedVideo?.title }}
        </span>
        <button
          type="button"
          class="bili-insight-panel__close"
          data-testid="bili-insight-close"
          @click="collapseInsight"
        >
          收起
        </button>
      </header>

      <p
        v-if="!expandedEntry || expandedEntry.status === 'loading'"
        class="bili-cards__hint"
        data-testid="bili-insight-loading"
      >
        正在生成 AI 讲解 / 看点…
      </p>
      <p
        v-else-if="expandedEntry.status === 'error'"
        class="bili-cards__hint"
        data-testid="bili-insight-error"
      >
        AI 讲解暂不可用（网络 / 登录态 / 模型均可能导致），可
        <button
          type="button"
          class="bili-cards__retry"
          data-testid="bili-insight-retry"
          @click="retryExpandedInsight"
        >
          重试
        </button>
        或直接前往 B 站观看。
      </p>
      <VideoInsightView
        v-else-if="expandedEntry.data"
        :bvid="expandedEntry.data.bvid"
        :overview="expandedEntry.data.overview"
        :key-points="expandedEntry.data.keyPoints"
        :degraded="expandedEntry.data.degraded"
        @jump="onOpen"
      />
    </section>
  </section>
</template>

<style scoped>
.bili-cards {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.bili-cards__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}
.bili-cards__title {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  font-weight: 700;
  color: var(--pyt-color-text-hint);
  letter-spacing: 0.5px;
}
.bili-cards__src {
  font-size: var(--pyt-font-size-xs);
  padding: 2px 8px;
  background: var(--pyt-color-info-bg);
  color: var(--pyt-color-info);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  white-space: nowrap;
}
/* 版块说明：让学生知道这些是外部 B站 资源、时长定位与用法（而非课件内容）。 */
.bili-cards__caption {
  margin: 0;
  font-size: var(--pyt-font-size-xs);
  line-height: 1.5;
  color: var(--pyt-color-text-muted);
}
.bili-cards__hint {
  margin: 0;
  padding: var(--pyt-space-3);
  font-size: var(--pyt-font-size-sm);
  line-height: 1.5;
  color: var(--pyt-color-text-hint);
  text-align: center;
  background: var(--pyt-color-bg-soft);
  border: 1px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}
/* 错误占位里的「重试」：像素小按钮，与 travel-chip 同语言。 */
.bili-cards__retry {
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
.bili-cards__retry:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}

.bili-cards__grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  /* 等宽列：auto-fill + 1fr 让每行卡片宽度严格相同（不足一行的卡片也不被拉伸成异形）。 */
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  /* 行内卡片拉伸到等高，配合 __link height:100% + meta margin-top:auto 让版式整齐。 */
  align-items: stretch;
  /* 跨行等高：固定每行轨道为 1fr，让不同行的卡片也严格同高（消除参差网格）。 */
  grid-auto-rows: 1fr;
  gap: var(--pyt-space-3);
}

.bili-card {
  min-width: 0;
  /* 列布局：封面/标题链接在上，「AI 讲解/看点」按钮在下；网格等高仍靠 grid-auto-rows:1fr。 */
  display: flex;
  flex-direction: column;
}
.bili-card__link {
  /* flex:1 撑满卡片（减去底部按钮）高度，配合 meta margin-top:auto 把元信息压到底部；
     卡片等高由 .bili-card 吃满 grid-auto-rows:1fr 保证（无需再 height:100%）。 */
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-1);
  text-decoration: none;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  padding: var(--pyt-space-2);
  /* 星露谷硬位移投影（与 travel-bar / lecture-frame 一致的像素质感）。 */
  box-shadow: 2px 2px 0 var(--pyt-color-shadow);
  transition: border-color var(--pyt-motion-base), transform var(--pyt-motion-base),
    box-shadow var(--pyt-motion-base);
}
.bili-card__link:hover {
  border-color: var(--pyt-color-text-primary);
  transform: translateY(-2px);
  box-shadow: 3px 4px 0 var(--pyt-color-shadow);
}
.bili-card__link:active {
  transform: translateY(0);
  box-shadow: 1px 1px 0 var(--pyt-color-shadow);
}
@media (prefers-reduced-motion: reduce) {
  .bili-card__link {
    transition: none;
  }
  .bili-card__link:hover {
    transform: none;
  }
}

.bili-card__cover {
  position: relative;
  width: 100%;
  /* 用 padding 比例盒（16:10 → 10/16=62.5%）而非 aspect-ratio：
     在弹性列 / 不同浏览器里都严格按「宽度 × 62.5%」定高，
     无论 B站封面原始尺寸如何，所有卡片封面都等高（彻底消除参差）。 */
  height: 0;
  padding-top: 62.5%;
  overflow: hidden;
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-terminal-bg);
  /* 卡片随 grid-auto-rows 拉伸时封面不被压扁：锁定比例，多余空间留给下方 meta。 */
  flex: 0 0 auto;
}
/* 封面图 / 占位都绝对铺满比例盒，object-fit:cover 裁切到统一画幅。 */
.bili-card__img,
.bili-card__cover-fallback {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.bili-card__img {
  object-fit: cover;
  display: block;
}
.bili-card__cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: var(--pyt-color-terminal-text);
}
.bili-card__dur {
  position: absolute;
  right: 4px;
  bottom: 4px;
  padding: 1px 6px;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-on-dark);
  background: rgba(42, 26, 5, 0.82);
  border-radius: var(--pyt-radius-sm);
}
.bili-card__badge {
  position: absolute;
  left: 4px;
  top: 4px;
  padding: 1px 6px;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-info);
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
}

.bili-card__name {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  line-height: 1.35;
  /* 预留 2 行高度：1 行标题与 2 行标题占位一致，文字行数差异不再撑出参差。 */
  min-height: calc(2 * 1.35em);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
/* 每个视频的「AI 速览」：后端 LLM 依据标题+简介+时长生成的一句话，标注 AI、2 行截断。 */
.bili-card__desc {
  margin: 0;
  font-size: var(--pyt-font-size-xs);
  line-height: 1.4;
  /* 预留 2 行高度：与标题同理，速览行数差异不影响卡片高度。 */
  min-height: calc(2 * 1.4em);
  color: var(--pyt-color-text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
/* 「AI」小标记：明示这句是 AI 生成（防幻觉/可信度提示），随文字行内排布、被 line-clamp 一起截断。 */
.bili-card__ai-tag {
  display: inline-block;
  margin-right: 4px;
  padding: 0 4px;
  font-size: 9px;
  font-weight: 700;
  line-height: 1.4;
  vertical-align: text-top;
  color: var(--pyt-color-info);
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
}
.bili-card__desc--empty {
  font-style: italic;
  color: var(--pyt-color-text-hint);
}
.bili-card__desc--empty .bili-card__ai-text {
  font-style: italic;
}
.bili-card__meta {
  /* 推到卡片底部，使同一行卡片的 UP主/播放量基线对齐（等高卡片的收尾）。 */
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-1);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-hint);
}
.bili-card__up {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bili-card__play {
  flex: none;
}

/* 「AI 讲解 / 看点」按钮区：在卡片链接下方，独立于外链 <a>（避免嵌套交互元素）。 */
.bili-card__actions {
  flex: 0 0 auto;
  margin-top: var(--pyt-space-1);
  display: flex;
}
.bili-card__insight-btn {
  flex: 1 1 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-family: inherit;
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
  padding: 4px 8px;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  box-shadow: 2px 2px 0 var(--pyt-color-shadow);
  cursor: pointer;
  transition: transform var(--pyt-motion-base), box-shadow var(--pyt-motion-base),
    border-color var(--pyt-motion-base), background var(--pyt-motion-base);
}
.bili-card__insight-btn:hover {
  border-color: var(--pyt-color-text-primary);
}
.bili-card__insight-btn:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}
.bili-card__insight-btn--active {
  background: var(--pyt-color-info-bg);
  border-color: var(--pyt-color-text-primary);
}
@media (prefers-reduced-motion: reduce) {
  .bili-card__insight-btn {
    transition: none;
  }
}
.bili-card__insight-tag {
  display: inline-block;
  padding: 0 4px;
  font-size: 9px;
  font-weight: 700;
  line-height: 1.5;
  color: var(--pyt-color-info);
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
}

/* 全宽 insight 面板：单卡展开，承载 overview + 关键时间点（不挤压上方等高网格）。 */
.bili-insight-panel {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-3);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  box-shadow: 2px 2px 0 var(--pyt-color-shadow);
}
.bili-insight-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}
.bili-insight-panel__title {
  min-width: 0;
  font-size: var(--pyt-font-size-sm);
  font-weight: 700;
  color: var(--pyt-color-text-hint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bili-insight-panel__close {
  flex: none;
  font-family: inherit;
  font-size: var(--pyt-font-size-xs);
  padding: 2px 10px;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  box-shadow: 2px 2px 0 var(--pyt-color-shadow);
  cursor: pointer;
}
.bili-insight-panel__close:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}
</style>
