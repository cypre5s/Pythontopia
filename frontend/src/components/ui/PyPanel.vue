<script setup lang="ts">
/**
 * PyPanel：全站统一牛皮纸 9-slice 面板容器（统一标准 §3）。
 * 统一描边 / 底色 / 内边距，组件不再各造一套面板样式。
 */
withDefaults(defineProps<{ pad?: boolean }>(), { pad: true });
</script>

<template>
  <div
    class="py-panel"
    :class="{ 'py-panel--pad': pad }"
    data-testid="py-panel"
  >
    <slot />
  </div>
</template>

<style scoped>
.py-panel {
  background: linear-gradient(
    180deg,
    var(--pyt-color-bg-elevated) 0%,
    var(--pyt-color-bg-sunken) 100%
  );
  border: 3px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  box-shadow: var(--pyt-shadow-card), inset 0 0 0 2px rgba(255, 247, 230, 0.55);
  color: var(--pyt-color-text-primary);
  min-height: 0;
}
.py-panel--pad {
  padding: var(--pyt-space-3);
}
</style>
