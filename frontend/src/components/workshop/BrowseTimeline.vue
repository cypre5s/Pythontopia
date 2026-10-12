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
    (citation): LoopCitation => ({
      kind: 'external',
      ref: citation.url,
      label: browseUrlLabel(citation.url),
    }),
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
      <span class="browse__title">联网查证</span>
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
          <p
            v-if="s.title"
            class="browse__step-title"
            data-testid="browse-step-title"
          >
            {{ s.title }}
          </p>
          <p
            class="browse__summary"
            data-testid="browse-step-snippet"
          >
            {{ s.snippet || s.summary }}
          </p>
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
        <p class="browse__error-msg">
          联网查证失败：{{ run.error }}
        </p>
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
  padding: 12px;
  border: 1px solid #dde5de;
  border-radius: 8px;
  background: #ffffff;
  font-family: Inter, "SF Pro Text", "Segoe UI", system-ui, sans-serif;
  font-size: 13px;
  color: #252a2e;
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
  letter-spacing: 0;
  font-weight: 700;
  color: #25332b;
}
.browse__status {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid #d7dfd8;
}
.browse__status--pending {
  background: #f4f6f5;
  color: #5b655f;
}
.browse__status--active {
  background: #fff7df;
  border-color: #ead38d;
  color: #725414;
}
.browse__status--done {
  background: #ebf6ec;
  border-color: #bcd9bf;
  color: #276037;
}
.browse__status--warn {
  background: #fff7df;
  border-color: #ead38d;
  color: #725414;
}
.browse__status--failed {
  background: #fff1ef;
  border-color: #e2ada5;
  color: #9e3528;
}
.browse__notice {
  margin: 0;
  padding: 6px 8px;
  font-size: 12px;
  background: #fff9e8;
  border: 1px solid #ead38d;
  border-radius: 6px;
  color: #725414;
}
.browse__goal {
  margin: 0;
  line-height: 1.4;
  word-break: break-word;
}
.browse__goal-key {
  font-weight: 700;
  color: #63706a;
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
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
  background: #eef5ef;
  border: 1px solid #c6d5c8;
  color: #365241;
  white-space: nowrap;
}
.browse__step-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.browse__link {
  font-size: 12px;
  font-weight: 700;
  color: #245f80;
  text-decoration: underline;
  word-break: break-all;
}
.browse__summary {
  margin: 0;
  line-height: 1.45;
  color: #33423a;
  word-break: break-word;
}

.browse__step-title {
  margin: 3px 0 1px;
  color: #203a2d;
  font-size: 0.84rem;
  font-weight: 700;
}
.browse__empty {
  margin: 0;
  font-style: italic;
  color: #6c7771;
}
.browse__truncated {
  margin: 0;
  padding: 6px 8px;
  font-size: 12px;
  background: #fff9e8;
  border: 1px solid #ead38d;
  border-radius: 6px;
  color: #725414;
}
.browse__final {
  margin: 0;
  padding: 8px 10px;
  line-height: 1.5;
  background: #f8faf8;
  border: 1px solid #dde5de;
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
  font-size: 12px;
  font-weight: 700;
  color: #63706a;
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
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #eef5ef;
  border: 1px solid #c6d5c8;
  color: #245f80;
  text-decoration: none;
  word-break: break-all;
}
.browse__chip--plain {
  background: #f4f6f5;
  border-color: #dde5de;
  color: #6c7771;
}
.browse__error {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 8px;
  background: #fff1ef;
  border: 1px solid #e2ada5;
  border-radius: 6px;
}
.browse__error-icon {
  flex: none;
  font-weight: 700;
  color: #9e3528;
}
.browse__error-body {
  min-width: 0;
}
.browse__error-msg {
  margin: 0;
  font-weight: 700;
  color: #9e3528;
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
