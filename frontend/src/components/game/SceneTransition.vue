<script setup lang="ts">
// 场景切换过渡遮罩，监听 pythontopia:scene-transition 事件。
import { ref, onMounted, onBeforeUnmount } from 'vue';

const active = ref(false);
const phase = ref<'out' | 'in'>('out');

function onTransitionStart(event: Event): void {
  const detail = (event as CustomEvent<{ onMidpoint?: () => void }>).detail;
  active.value = true;
  phase.value = 'out';
  setTimeout(() => {
    phase.value = 'in';
    if (detail?.onMidpoint) detail.onMidpoint();
  }, 400);
  setTimeout(() => {
    active.value = false;
  }, 800);
}

onMounted(() => {
  window.addEventListener('pythontopia:scene-transition', onTransitionStart);
});
onBeforeUnmount(() => {
  window.removeEventListener('pythontopia:scene-transition', onTransitionStart);
});
</script>

<template>
  <transition name="pyt-fade">
    <div
      v-if="active"
      class="scene-transition"
      :class="[`scene-transition--${phase}`]"
      aria-hidden="true"
    >
      <div class="scene-transition__overlay" />
    </div>
  </transition>
</template>

<style scoped>
.scene-transition {
  position: fixed;
  inset: 0;
  z-index: 500;
  pointer-events: none;
  overflow: hidden;
}

.scene-transition__overlay {
  position: absolute;
  inset: 0;
  background: #1c2b18;
}

.scene-transition--out .scene-transition__overlay {
  animation: dissolve-out 400ms steps(6) forwards;
}
.scene-transition--in .scene-transition__overlay {
  animation: dissolve-in 400ms steps(6) forwards;
}

@keyframes dissolve-out {
  0%   { opacity: 0; }
  100% { opacity: 1; }
}
@keyframes dissolve-in {
  0%   { opacity: 1; }
  100% { opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .scene-transition--out .scene-transition__overlay,
  .scene-transition--in .scene-transition__overlay {
    animation-duration: 80ms;
  }
}
</style>
