<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useSessionStore } from '@stores/session';
import { useWorldStore } from '@stores/world';
import { playUiClick } from '@/composables/useStardewFx';

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
  playUiClick();
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
      <span class="header-status__name">{{ session.displayName }}</span>
      <span class="header-status__weather">{{ dayWeather }}</span>
      <button
        class="header-status__logout"
        aria-label="退出登录"
        type="button"
        @click="logout"
      >
        退出
      </button>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 16px;
  background: var(--pyt-color-bg-elevated, #fff7e6);
  border-bottom: 2px solid var(--pyt-color-border, #c9a86b);
  min-height: 48px;
}

.brand {
  font-weight: 700;
  color: var(--pyt-color-text-primary, #4d3613);
  text-decoration: none;
  font-size: 16px;
  letter-spacing: 0.5px;
}

.header-status {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--pyt-color-text-primary, #4d3613);
}

.header-status__logout {
  padding: 4px 10px;
  background: rgba(184, 68, 45, 0.12);
  border: 2px solid var(--pyt-color-danger, #b8442d);
  color: var(--pyt-color-danger, #b8442d);
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: background 120ms;
}

.header-status__logout:hover {
  background: rgba(184, 68, 45, 0.22);
}

@media (max-width: 640px) {
  .app-header {
    padding: 6px 10px;
    min-height: 40px;
    gap: 8px;
  }
  .brand {
    font-size: 14px;
  }
  .header-status {
    gap: 6px;
    font-size: 11px;
  }
  /* 窄屏只保留 weather + 退出，displayName 让位 */
  .header-status__name {
    display: none;
  }
}
</style>
