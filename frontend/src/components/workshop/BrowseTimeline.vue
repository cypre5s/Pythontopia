<script setup lang="ts">
/**
 * BrowseTimeline —— 联网查证（BROWSE）步骤时间线（契约 §3.1 / §3.8 / §4 B2 前端）。
 *
 * renderer="browse" 映射到本组件（接线 Phase 2）。渲染一次 BrowseRunDetailDto：
 *   有界步骤时间线（动作中文 + 可点外链 + 摘要）→ 整体结论 → 命中来源（外部链接 chips）。
 *
 * 红线（§0 / §3.8 / §8.6）：BROWSE 命中的 url / 摘要全部是「外部内容，非课件」，
 *   顶部 banner 与引用分区统一标注；只渲染 http(s) 安全链接（防 javascript:/data: 注入）；
 *   错误态显式（红色 + 可读 error + 回退 VIDEO 提示），绝不静默吞。
 * 第三方动效隔离：本组件不直接 import gsap/rive（由 F1 anim adapter 适配），仅用 CSS 过渡且尊重 reduce-motion。
 */
import { computed } from 'vue';
import {
  BROWSE_EXTERNAL_NOTICE,
  browseRunStatusLabel,
  browseRunStatusTone,
  browseStepActionLabel,
  browseUrlLabel,
  isSafeBrowseUrl,
} from '@/api/browse';
import type { BrowseRunDetailDto, BrowseStepDto } from '@/api/browse';
import type { LoopCitation } from '@/stores/learning/loopTypes';

const props = defineProps<{ run: BrowseRunDetailDto | null }>();

const steps = computed<BrowseStepDto[]>(() => props.run?.steps ?? []);
const hasError = computed<boolean>(() => Boolean(props.run?.error));
const isTruncated = computed<boolean>(() => props.run?.truncated === true);

const statusLabel = computed<string>(() => browseRunStatusLabel(props.run?.status ?? ''));
const statusTone = computed<string>(() => browseRunStatusTone(props.run?.status ?? ''));

/** 命中来源 → 外部引用 chips（复用冻结类型 LoopCitation；kind 恒为 external）。 */
const citations = computed<LoopCitation[]>(() =>
  (props.run?.citations ?? []).map(
    (url): LoopCitation => ({ kind: 'external', ref: url, label: browseUrlLabel(url) }),
  ),
);

function externalAria(url: string): string {
  return `外部链接：${url}（${BROWSE_EXTERNAL_NOTICE}）`;
}
</script>

<template>
  <section
    v-if="run"
    class="browse"
    data-testid="browse-timeline"
    aria-label="联网查证时间线"
  >
    <header class="browse__head">
      <span class="browse__title">🌐 联网查证</span>
      <span
        class="browse__status"
        :class="`browse__status--${statusTone}`"
        role="status"
        data-testid="browse-status"
      >{{ statusLabel }}</span>
    </header>

    <p
      class="browse__notice"
      data-testid="browse-external-notice"
    >
      <span aria-hidden="true">⚠️</span>
      {{ BROWSE_EXTERNAL_NOTICE }} · 仅供参考，请以课件为准
    </p>

    <p class="browse__goal">
      <span class="browse__goal-key">查证目标</span>{{ run.goal }}
    </p>

    <ol
      v-if="steps.length > 0"
      class="browse__steps"
      data-testid="browse-steps"
    >
      <li
        v-for="s in steps"
        :key="s.index"
        class="browse__step"
        data-testid="browse-step"
      >
        <span
          class="browse__action"
          data-testid="browse-step-action"
        >{{ browseStepActionLabel(s.action) }}</span>
        <div class="browse__step-body">
          <a
            v-if="isSafeBrowseUrl(s.url)"
            class="browse__link"
            :href="s.url"
            target="_blank"
            rel="noopener noreferrer"
            :aria-label="externalAria(s.url)"
            data-testid="browse-step-url"
          >{{ browseUrlLabel(s.url) }} <span aria-hidden="true">↗</span></a>
          <p class="browse__summary">{{ s.summary }}</p>
        </div>
      </li>
    </ol>
    <p
      v-else
      class="browse__empty"
      data-testid="browse-empty-steps"
    >
      暂无浏览步骤
    </p>

    <p
      v-if="isTruncated"
      class="browse__truncated"
      role="status"
      data-testid="browse-truncated"
    >
      已达浏览步数上限（外部浏览有界，结果可能不完整）
    </p>

    <p
      v-if="run.finalSummary"
      class="browse__final"
      data-testid="browse-final-summary"
    >
      {{ run.finalSummary }}
    </p>

    <div
      v-if="citations.length > 0"
      class="browse__cites"
      data-testid="browse-citations"
    >
      <p
        class="browse__cites-notice"
        data-testid="browse-citations-notice"
      >
        命中来源（{{ BROWSE_EXTERNAL_NOTICE }}）
      </p>
      <ul class="browse__cite-list">
        <li
          v-for="c in citations"
          :key="c.ref"
        >
          <a
            v-if="isSafeBrowseUrl(c.ref)"
            class="browse__chip"
            :href="c.ref"
            target="_blank"
            rel="noopener noreferrer"
            :aria-label="externalAria(c.ref)"
            data-testid="browse-citation"
          >{{ c.label }} <span aria-hidden="true">↗</span></a>
          <span
            v-else
            class="browse__chip browse__chip--plain"
            data-testid="browse-citation"
          >{{ c.ref }}</span>
        </li>
      </ul>
    </div>

    <div
      v-if="hasError"
      class="browse__error"
      role="alert"
      data-testid="browse-error"
    >
      <span
        class="browse__error-icon"
        aria-hidden="true"
      >✗</span>
      <div class="browse__error-body">
        <p class="browse__error-msg">联网查证失败：{{ run.error }}</p>
        <p
          class="browse__fallback"
          data-testid="browse-fallback"
        >
          已为你回退到「看课视频」，可改用课程视频继续学习。
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.browse {
  width: 100%;
  align-self: flex-start;
  margin-top: 8px;
  padding: 10px 12px;
  border: 1px solid var(--pyt-color-border);
  border-radius: 8px;
  background: var(--pyt-color-bg-soft);
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  color: var(--pyt-color-text-primary);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.browse__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.browse__title {
  font-weight: 700;
  color: var(--pyt-color-text-hint);
}
.browse__status {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 8px;
  border-radius: var(--pyt-radius-full);
  border: 1px solid var(--pyt-color-border);
}
.browse__status--pending {
  background: #eee;
  color: #555;
}
.browse__status--active {
  background: #f5e2c1;
  border-color: #c2871f;
  color: #7a5210;
}
.browse__status--done {
  background: #d6ead1;
  border-color: #4d6e1d;
  color: #2d5b1b;
}
.browse__status--warn {
  background: #f7ecd0;
  border-color: #b8860b;
  color: #7a5210;
}
.browse__status--failed {
  background: #f7d9d6;
  border-color: #b3261e;
  color: #b3261e;
}
.browse__notice {
  margin: 0;
  padding: 4px 8px;
  font-size: 11px;
  background: #fff2c9;
  border: 1px dashed #b8860b;
  border-radius: 6px;
  color: #7a5210;
}
.browse__goal {
  margin: 0;
  line-height: 1.4;
  word-break: break-word;
}
.browse__goal-key {
  font-weight: 700;
  color: var(--pyt-color-text-hint);
  margin-right: 6px;
}
.browse__steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.browse__step {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px;
  align-items: start;
  animation: browse-in 0.18s ease;
}
@keyframes browse-in {
  from {
    opacity: 0;
    transform: translateY(3px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.browse__action {
  flex: none;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 7px;
  border-radius: var(--pyt-radius-full);
  background: #e7eef7;
  border: 1px solid #6a86b0;
  color: #2f4a72;
  white-space: nowrap;
}
.browse__step-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.browse__link {
  font-size: 11px;
  font-weight: 700;
  color: #2f4a72;
  text-decoration: underline;
  word-break: break-all;
}
.browse__summary {
  margin: 0;
  line-height: 1.45;
  color: var(--pyt-color-text-primary);
  word-break: break-word;
}
.browse__empty {
  margin: 0;
  font-style: italic;
  color: var(--pyt-color-text-muted);
}
.browse__truncated {
  margin: 0;
  padding: 4px 8px;
  font-size: 11px;
  background: #f7ecd0;
  border: 1px solid #b8860b;
  border-radius: 6px;
  color: #7a5210;
}
.browse__final {
  margin: 0;
  padding: 6px 8px;
  line-height: 1.5;
  background: var(--pyt-color-bg);
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
  word-break: break-word;
}
.browse__cites {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.browse__cites-notice {
  margin: 0;
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-hint);
}
.browse__cite-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.browse__chip {
  display: inline-block;
  font-size: 10px;
  padding: 2px 8px;
  border-radius: var(--pyt-radius-full);
  background: #e7eef7;
  border: 1px solid #6a86b0;
  color: #2f4a72;
  text-decoration: none;
  word-break: break-all;
}
.browse__chip--plain {
  background: #efeae0;
  border-color: var(--pyt-color-border);
  color: var(--pyt-color-text-muted);
}
.browse__error {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 6px 8px;
  background: #f7d9d6;
  border: 1px solid #b3261e;
  border-radius: 6px;
}
.browse__error-icon {
  flex: none;
  font-weight: 700;
  color: #b3261e;
}
.browse__error-body {
  min-width: 0;
}
.browse__error-msg {
  margin: 0;
  font-weight: 700;
  color: #b3261e;
  word-break: break-word;
}
.browse__fallback {
  margin: 2px 0 0;
  color: #7a3a36;
}
@media (prefers-reduced-motion: reduce) {
  .browse__step {
    animation: none;
  }
}
</style>
