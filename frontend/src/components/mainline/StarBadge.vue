<script setup lang="ts">
// 章节星级徽章（0–3★）。纯展示：实心 ★ = 已得星，空心 ☆ = 未得星。
// 颜色一律取 tokens.css 的 --pyt-color-* 令牌（星露谷羊皮纸亮色），不硬编码 hex。
import { computed } from 'vue';
import { clampStars } from '@stores/mainline';

const props = withDefaults(
  defineProps<{
    stars: number;
    max?: number;
  }>(),
  { max: 3 },
);

const filled = computed<number>(() => clampStars(props.stars, props.max));
const slots = computed<boolean[]>(() =>
  Array.from({ length: props.max }, (_, i) => i < filled.value),
);
const label = computed<string>(() => `${filled.value} / ${props.max} 星`);
</script>

<template>
  <span
    class="star-badge"
    data-testid="mainline-star-badge"
    role="img"
    :aria-label="label"
    :data-filled="filled"
  >
    <span
      v-for="(on, idx) in slots"
      :key="idx"
      class="star-badge__star"
      :class="on ? 'star-badge__star--on' : 'star-badge__star--off'"
      aria-hidden="true"
    >{{ on ? '★' : '☆' }}</span>
  </span>
</template>

<style scoped>
.star-badge {
  display: inline-flex;
  gap: 2px;
  font-family: var(--pyt-font-pixel);
  line-height: 1;
}
.star-badge__star {
  font-size: var(--pyt-font-size-base);
}
.star-badge__star--on {
  color: var(--pyt-color-gold-bright);
  text-shadow: 1px 1px 0 var(--pyt-color-shadow);
}
.star-badge__star--off {
  color: var(--pyt-color-border);
}
</style>
