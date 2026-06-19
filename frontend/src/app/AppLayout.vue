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
    <a
      class="skip-link"
      href="#main-content"
    >跳到主内容</a>
    <AppHeader v-if="!isFullscreen" />
    <main
      id="main-content"
      class="app-main"
      tabindex="-1"
      :class="{ 'app-main--fullscreen': isFullscreen }"
    >
      <slot />
    </main>
    <CompanionDock v-if="showCompanion" />
  </div>
</template>

<style scoped>
/* 跳到主内容（a11y · WCAG 2.4.1 绕过重复块）：键盘用户首个 Tab 即可跳过头部直达主区；
   平时移出视口隐藏，获焦时滑入。main 加 tabindex=-1 以便成为跳转后的焦点落点。 */
.skip-link {
  position: fixed;
  left: 8px;
  top: -48px;
  z-index: 1000;
  padding: 8px 14px;
  border: 2px solid #263426;
  background: #fff8e1;
  color: #263426;
  font-weight: 700;
  text-decoration: none;
  transition: top 0.15s ease;
}

.skip-link:focus {
  top: 8px;
}

.app-main--fullscreen {
  min-height: 100vh;
}
</style>
