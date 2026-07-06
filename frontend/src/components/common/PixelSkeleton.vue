<script setup lang="ts">
/** 通用骨架屏占位：羊皮纸色微光条，用于异步内容（加载运行时 / 列表拉取等）的占位。 */
defineOptions({ name: 'PixelSkeleton' });

withDefaults(
  defineProps<{
    width?: string;
    height?: string;
    rounded?: boolean;
  }>(),
  {
    width: '100%',
    height: '14px',
    rounded: true,
  },
);
</script>

<template>
  <span
    class="pyt-skel"
    :class="{ 'pyt-skel--round': rounded }"
    :style="{ width, height }"
    aria-hidden="true"
  />
</template>

<style scoped>
.pyt-skel {
  display: inline-block;
  vertical-align: middle;
  background: linear-gradient(
    90deg,
    rgba(92, 74, 50, 0.1) 25%,
    rgba(92, 74, 50, 0.22) 37%,
    rgba(92, 74, 50, 0.1) 63%
  );
  background-size: 400% 100%;
  animation: pyt-skel-shimmer 1.4s ease infinite;
  border-radius: 3px;
}

.pyt-skel--round {
  border-radius: 6px;
}

@keyframes pyt-skel-shimmer {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: 0 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .pyt-skel {
    animation: none;
  }
}
</style>
