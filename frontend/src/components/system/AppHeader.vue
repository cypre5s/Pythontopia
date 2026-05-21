<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useSessionStore } from '@stores/session';
import { useWorldStore } from '@stores/world';

const session = useSessionStore();
const world = useWorldStore();
const router = useRouter();

const dayWeather = computed(() => {
  if (!world.session) {
    return '第 1 天 / 晴';
  }
  return `第 ${world.session.dayIndex} 天 / ${world.session.weather}`;
});

function logout(): void {
  session.logout();
  void router.push({ name: 'login' });
}
</script>

<template>
  <header class="app-header">
    <RouterLink
      class="brand"
      to="/town"
    >
      Pythontopia
    </RouterLink>
    <div class="header-status">
      <span>{{ session.displayName }}</span>
      <span>{{ dayWeather }}</span>
      <button
        aria-label="退出登录"
        type="button"
        @click="logout"
      >
        退出
      </button>
    </div>
  </header>
</template>
