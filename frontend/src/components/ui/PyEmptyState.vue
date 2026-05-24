<script setup lang="ts">
/**
 * PyEmptyState：Pythontopia 全端空状态卡片。
 *
 * 设计依据：
 *   - docs/stardew/07-ui-microcopy.md §4 空状态文案（主 ≤12 字 / 副 ≤18 字 / 必须给"下一步"动作指引）
 *   - docs/stardew/00-visual-system-overview.md §3 边框 + §10 可访问性（不依赖颜色 + role=status）
 *   - docs/stardew/01-palette-tokens-css.md（米黄主背景 + 灰阶次级文字 + 暖褐边框 + radius-md=4px）
 *
 * 落地约束：
 *   1. 主文案 title 必填；副文案 description 可选，缺省时若提供 i18nKey 会从 i18n
 *      `empty.<i18nKey>` 自动取（与 `frontend/src/i18n/zh-CN.json` 现有 10 条空状态对齐）。
 *   2. icon 可传 emoji 或图片 URL：含 `.png|.jpg|.svg|.webp` 后缀视为图片，渲染 <img>；
 *      其余视为 emoji/Unicode，渲染 <span>。emoji 默认走 `aria-hidden=true` + sr-only 文字替代，
 *      满足 docs/stardew/07-ui-microcopy.md §10 屏读要求。
 *   3. 默认 slot 用于放置"下一步动作"按钮（例如 PyFrame variant=button），不放则隐藏 actions 区域。
 *   4. role="status" + aria-live="polite"：空状态从有数据 → 空数据时屏读器通知用户；不打断已有阅读。
 *   5. 颜色全部走 `--pyt-r12-*` 并带 fallback 到现有 `--pyt-*`；未引入 tokens-r12.css 时也能正常渲染。
 */
import { computed } from 'vue';
import { hasKey, t } from '@/i18n';

interface PyEmptyStateProps {
  title: string;
  description?: string;
  icon?: string;
  i18nKey?: string;
}

const props = withDefaults(defineProps<PyEmptyStateProps>(), {
  description: '',
  icon: '',
  i18nKey: '',
});

const IMAGE_EXT_RE = /\.(png|jpg|jpeg|svg|webp|gif)(\?.*)?$/i;

const isImageIcon = computed<boolean>(() => {
  if (!props.icon) {
    return false;
  }
  return IMAGE_EXT_RE.test(props.icon) || props.icon.startsWith('/') || props.icon.startsWith('http');
});

const iconSrc = computed<string>(() => props.icon ?? '');

const resolvedDescription = computed<string>(() => {
  if (props.description) {
    return props.description;
  }
  if (props.i18nKey) {
    const key = `empty.${props.i18nKey}`;
    if (hasKey(key)) {
      return t(key);
    }
  }
  return '';
});

const hasIcon = computed<boolean>(() => props.icon.length > 0);
const hasDescription = computed<boolean>(() => resolvedDescription.value.length > 0);
</script>

<template>
  <div
    class="py-empty-state"
    role="status"
    aria-live="polite"
    :data-i18n-key="i18nKey || null"
    data-testid="py-empty-state"
  >
    <div
      v-if="hasIcon"
      class="py-empty-state__icon"
      data-testid="py-empty-state-icon"
    >
      <img
        v-if="isImageIcon"
        :src="iconSrc"
        alt=""
        aria-hidden="true"
        class="py-empty-state__icon-img"
      >
      <span
        v-else
        class="py-empty-state__icon-glyph"
        aria-hidden="true"
      >
        {{ icon }}
      </span>
    </div>
    <div
      v-else
      class="py-empty-state__placeholder"
      aria-hidden="true"
    >
      <span class="py-empty-state__placeholder-dot" />
      <span class="py-empty-state__placeholder-dot" />
      <span class="py-empty-state__placeholder-dot" />
    </div>

    <h3
      class="py-empty-state__title"
      data-testid="py-empty-state-title"
    >
      {{ title }}
    </h3>

    <p
      v-if="hasDescription"
      class="py-empty-state__description"
      data-testid="py-empty-state-description"
    >
      {{ resolvedDescription }}
    </p>

    <div
      v-if="$slots.default"
      class="py-empty-state__actions"
      data-testid="py-empty-state-actions"
    >
      <slot />
    </div>
  </div>
</template>

<style scoped>
.py-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--pyt-r12-space-2, 8px);
  min-height: 160px;
  padding: var(--pyt-r12-space-5, 24px) var(--pyt-r12-space-4, 16px);
  border: 1px dashed var(--pyt-r12-color-primary-500, var(--pyt-color-border, #c9a86b));
  border-radius: var(--pyt-r12-radius-md, 4px);
  background-color: var(--pyt-r12-color-primary-50, var(--pyt-color-bg-primary, #fef7e3));
  color: var(--pyt-r12-color-primary-900, var(--pyt-color-text-primary, #4d3613));
  font-family: var(--pyt-r12-font-pixel-cn, var(--pyt-font-pixel, system-ui));
  text-align: center;
}

.py-empty-state__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  margin-bottom: var(--pyt-r12-space-1, 4px);
  image-rendering: pixelated;
}

.py-empty-state__icon-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  image-rendering: pixelated;
}

.py-empty-state__icon-glyph {
  font-size: 36px;
  line-height: 1;
}

.py-empty-state__placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--pyt-r12-space-1, 4px);
  width: 48px;
  height: 48px;
  margin-bottom: var(--pyt-r12-space-1, 4px);
}

.py-empty-state__placeholder-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: var(--pyt-r12-color-gray-300, #d8c293);
}

.py-empty-state__title {
  margin: 0;
  font-size: var(--pyt-r12-font-size-md, 14px);
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: 0.5px;
}

.py-empty-state__description {
  margin: 0;
  max-width: 320px;
  font-size: var(--pyt-r12-font-size-sm, 12px);
  color: var(--pyt-r12-color-neutral, var(--pyt-color-text-muted, #8b6b47));
  line-height: 1.5;
}

.py-empty-state__actions {
  margin-top: var(--pyt-r12-space-2, 8px);
  display: inline-flex;
  flex-wrap: wrap;
  gap: var(--pyt-r12-space-2, 8px);
  justify-content: center;
}
</style>
