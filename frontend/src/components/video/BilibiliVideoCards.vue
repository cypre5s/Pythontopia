<script setup lang="ts">
/**
 * BilibiliVideoCards —— 「B站精选讲解」外部视频卡片网格。
 *
 * 纯展示型外部资源：仅渲染封面缩略图 + 元数据，整卡是跳转 B 站的外链（新标签打开），
 * 不进课件 grounding、不内嵌下载。封面用 `referrerpolicy="no-referrer"` 规避 hdslb 防盗链，
 * 加载失败回退占位。检索失败 / 空结果都降级为占位文案（不报错、不白屏）。
 * 点击卡片时 emit('consumed')，供父组件计入多模态学习闭环。
 */
import { onMounted, ref, watch } from 'vue';
import { searchBilibili, type BilibiliVideoDto } from '@/api/bilibiliVideo';

const props = withDefaults(
  defineProps<{
    keyword: string;
    limit?: number;
    heading?: string;
  }>(),
  {
    limit: 8,
    heading: 'B站精选讲解',
  },
);

const emit = defineEmits<{ (e: 'consumed'): void }>();

type LoadStatus = 'loading' | 'ready' | 'empty' | 'error';

const videos = ref<BilibiliVideoDto[]>([]);
const status = ref<LoadStatus>('loading');
const coverErrored = ref<Set<string>>(new Set());

let loadToken = 0;

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
  emit('consumed');
}

async function load(): Promise<void> {
  const keyword = props.keyword.trim();
  coverErrored.value = new Set();
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
      <h3 class="bili-cards__title">{{ heading }}</h3>
      <span class="bili-cards__src">外部资源·B站</span>
    </header>

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
            :class="{ 'bili-card__desc--empty': !v.description }"
          >{{ v.description || 'UP 主未填写简介' }}</p>
          <div class="bili-card__meta">
            <span class="bili-card__up">UP主 {{ v.author || '—' }}</span>
            <span class="bili-card__play">▶ {{ formatCount(v.play) }}</span>
          </div>
        </a>
      </li>
    </ul>

    <p
      v-else
      class="bili-cards__hint"
      data-testid="bili-cards-empty"
    >
      {{ status === 'error'
        ? 'B 站检索暂不可用，可稍后再试或回到课件问答。'
        : '暂无相关 B 站讲解，可换个关键词或使用课件问答。' }}
    </p>
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
  display: flex;
}
.bili-card__link {
  /* height:100% 吃满 grid 行高 → 同一行卡片等高；flex 列布局把 meta 压到底部对齐。 */
  flex: 1 1 auto;
  height: 100%;
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
  aspect-ratio: 16 / 10;
  overflow: hidden;
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-terminal-bg);
  /* 卡片随 grid-auto-rows 拉伸时封面不被压扁：锁定比例，多余空间留给下方 meta。 */
  flex: 0 0 auto;
}
.bili-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.bili-card__cover-fallback {
  width: 100%;
  height: 100%;
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
/* 每个视频的「讲解」：UP 主简介（真实数据），2 行截断保证卡片版式整齐。 */
.bili-card__desc {
  margin: 0;
  font-size: var(--pyt-font-size-xs);
  line-height: 1.4;
  /* 预留 2 行高度：与标题同理，简介行数差异不影响卡片高度。 */
  min-height: calc(2 * 1.4em);
  color: var(--pyt-color-text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.bili-card__desc--empty {
  font-style: italic;
  color: var(--pyt-color-text-hint);
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
</style>
