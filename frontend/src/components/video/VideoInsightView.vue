<script setup lang="ts">
/**
 * VideoInsightView —— B站视频「概述 + 关键时间点」纯展示组件。
 *
 * 只负责渲染父组件（BilibiliVideoCards）已拉取并归一好的 insight：
 *   - 一段针对学生问题的 `overview`（空则给中性占位，不伪造）。
 *   - 一组可点击的关键时间点（mm:ss + label），点击在新标签打开
 *     `https://www.bilibili.com/video/{bvid}?t={tSec}`（target=_blank rel=noopener）。
 *
 * 防幻觉红线：`degraded` 或 `keyPoints` 为空时**只显示概述 + 中性提示**，绝不渲染编造的时间点。
 * loading / error（拉取生命周期）由父组件处理，本组件只吃最终数据。
 */
import { computed } from 'vue';
import type { BilibiliKeyPoint } from '@/api/bilibiliVideo';

const props = defineProps<{
  /** B 站视频标识，用于拼跳转链接。 */
  bvid: string;
  /** 针对学生问题的概述（可能为空串）。 */
  overview: string;
  /** 关键时间点（已由 api 层归一过滤；可能为空数组）。 */
  keyPoints: BilibiliKeyPoint[];
  /** 降级标记：true 时即便有时间点也只展示概述 + 中性提示。 */
  degraded: boolean;
}>();

const emit = defineEmits<{ (e: 'jump', point: BilibiliKeyPoint): void }>();

// 降级或无时间点 → 不渲染任何跳转点（防幻觉：宁可不给，也不给假/坏的时间点）。
const showKeyPoints = computed(() => !props.degraded && props.keyPoints.length > 0);

function formatTime(sec: number): string {
  const total = Math.max(0, Math.floor(sec));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const ss = String(s).padStart(2, '0');
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${m}:${ss}`;
}

function jumpUrl(tSec: number): string {
  const t = Math.max(0, Math.floor(tSec));
  return `https://www.bilibili.com/video/${props.bvid}?t=${t}`;
}

function onJump(point: BilibiliKeyPoint): void {
  // 不阻止默认行为：让 <a target="_blank"> 原生打开新标签；外链点击不代表已观看。
  emit('jump', point);
}
</script>

<template>
  <div
    class="video-insight"
    data-testid="video-insight"
  >
    <p
      class="video-insight__overview"
      :class="{ 'video-insight__overview--empty': !overview }"
      data-testid="video-insight-overview"
    >
      <span
        class="video-insight__ai-tag"
        title="AI 依据视频真实元数据 / 官方 AI 总结生成的概述，仅供参考"
      >AI</span>
      <span class="video-insight__overview-text">{{ overview || 'AI 概述暂不可用，可直接前往 B 站观看。' }}</span>
    </p>

    <ul
      v-if="showKeyPoints"
      class="video-insight__points"
      data-testid="video-insight-points"
    >
      <li
        v-for="(kp, i) in keyPoints"
        :key="`${kp.tSec}-${i}`"
        class="video-insight__point"
      >
        <a
          class="video-insight__jump"
          :href="jumpUrl(kp.tSec)"
          target="_blank"
          rel="noopener noreferrer"
          data-testid="video-insight-jump"
          :title="`跳转到 ${formatTime(kp.tSec)} · ${kp.label}`"
          @click="onJump(kp)"
        >
          <span class="video-insight__time">{{ formatTime(kp.tSec) }}</span>
          <span class="video-insight__label">{{ kp.label }}</span>
        </a>
      </li>
    </ul>

    <p
      v-else
      class="video-insight__hint"
      data-testid="video-insight-empty"
    >
      该视频暂无可用关键时间点
    </p>
  </div>
</template>

<style scoped>
.video-insight {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
}

/* 概述段：学习正文用易读体（非像素字），带「AI」标记明示来源、防幻觉。 */
.video-insight__overview {
  margin: 0;
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-base);
  line-height: 1.6;
  color: var(--pyt-color-text-primary);
}
.video-insight__overview--empty {
  font-style: italic;
  color: var(--pyt-color-text-hint);
}
.video-insight__ai-tag {
  display: inline-block;
  margin-right: 6px;
  padding: 0 5px;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.5;
  vertical-align: text-top;
  color: var(--pyt-color-info);
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
}
.video-insight__overview-text {
  white-space: pre-line;
}

.video-insight__points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-1);
}
.video-insight__point {
  min-width: 0;
}
/* 单个时间点：可点击跳 B 站秒数。像素描边 + 硬位移投影，与卡片同语言。 */
.video-insight__jump {
  display: flex;
  align-items: baseline;
  gap: var(--pyt-space-2);
  width: 100%;
  padding: var(--pyt-space-2);
  text-decoration: none;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  box-shadow: 2px 2px 0 var(--pyt-color-shadow);
  transition: border-color var(--pyt-motion-base), transform var(--pyt-motion-base),
    box-shadow var(--pyt-motion-base);
}
.video-insight__jump:hover {
  border-color: var(--pyt-color-text-primary);
  transform: translateY(-1px);
  box-shadow: 3px 3px 0 var(--pyt-color-shadow);
}
.video-insight__jump:active {
  transform: translateY(0);
  box-shadow: 1px 1px 0 var(--pyt-color-shadow);
}
@media (prefers-reduced-motion: reduce) {
  .video-insight__jump {
    transition: none;
  }
  .video-insight__jump:hover {
    transform: none;
  }
}
/* 时间码：等宽数字，像素徽章质感，定位「可点跳转」。 */
.video-insight__time {
  flex: 0 0 auto;
  min-width: 48px;
  padding: 1px 6px;
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
  text-align: center;
  color: var(--pyt-color-info);
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
}
.video-insight__label {
  flex: 1 1 auto;
  min-width: 0;
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-sm);
  line-height: 1.5;
}

.video-insight__hint {
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
</style>
