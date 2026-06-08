<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppHeader from '@/components/system/AppHeader.vue';
import CompanionDock from '@/components/companion/CompanionDock.vue';

const route = useRoute();
const isFullscreen = computed(() => route.meta.fullscreen === true);
// 随身助教全局常驻；个别页面可用 route.meta.hideCompanion 关闭（如纯像素操作页遮挡时）。
const showCompanion = computed(() => route.meta.hideCompanion !== true);
</script>

<template>
  <div class="app-shell">
    <AppHeader v-if="!isFullscreen" />
    <main
      class="app-main"
      :class="{ 'app-main--fullscreen': isFullscreen }"
    >
      <slot />
    </main>
    <CompanionDock v-if="showCompanion" />
  </div>
</template>

<style scoped>
.app-main--fullscreen {
  min-height: 100vh;
}
</style>
