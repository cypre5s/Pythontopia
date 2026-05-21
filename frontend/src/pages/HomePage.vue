<script setup lang="ts">
import { useSessionStore } from '@stores/session';

const session = useSessionStore();
</script>

<template>
  <main class="home-page">
    <h1>Pythontopia</h1>
    <p>{{ session.isAuthenticated ? session.displayName : '未登录' }}</p>
  </main>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  display: grid;
  place-content: center;
  gap: 12px;
  text-align: center;
}

h1 {
  margin: 0;
  font-size: 40px;
}

p {
  margin: 0;
  color: #52605c;
}
</style>
