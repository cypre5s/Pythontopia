<script setup lang="ts">
import AppHeader from '@/components/system/AppHeader.vue';
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main">
      <slot />
    </main>
  </div>
</template>
