<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppHeader from '@/components/system/AppHeader.vue';

const route = useRoute();
const isFullscreen = computed(() => route.meta.fullscreen === true);
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
  </div>
</template>

<style scoped>
.app-main--fullscreen {
  min-height: 100vh;
}
</style>
