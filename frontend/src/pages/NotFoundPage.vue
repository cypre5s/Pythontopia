<script setup lang="ts">
// R-13：catch-all 路由对应的 404 兜底页。
//
// 重要原因：之前 router 没有 catch-all，访问 /shop-east 等未注册路径直接渲染
// 空 RouterView，看起来就是白屏。本组件提供「至少有内容」的兜底，永远不让
// 用户陷入沉默的白页面。

import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import { playUiClick } from '@/composables/useStardewFx';

const route = useRoute();
const router = useRouter();

const attemptedPath = computed(() => route.fullPath);

// R-13 迭代 24：8 秒自动跳回主世界，倒计时显示，避免玩家迷路
const countdown = ref<number>(8);
let countdownTimer: number | null = null;

function backToWorld(): void {
  playUiClick();
  if (countdownTimer !== null) {
    window.clearInterval(countdownTimer);
    countdownTimer = null;
  }
  void router.push({ name: 'world' });
}
function backToLogin(): void {
  playUiClick();
  void router.push({ name: 'login' });
}
function backHistory(): void {
  playUiClick();
  if (typeof window !== 'undefined' && window.history.length > 1) {
    window.history.back();
  } else {
    backToWorld();
  }
}

onMounted(() => {
  if (typeof window === 'undefined') return;
  countdownTimer = window.setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      backToWorld();
    }
  }, 1000);
});

onUnmounted(() => {
  if (countdownTimer !== null) {
    window.clearInterval(countdownTimer);
    countdownTimer = null;
  }
});
</script>

<template>
  <AppLayout>
    <section
      class="not-found-page"
      data-testid="not-found-page"
    >
      <h1 class="not-found-page__title">
        404 · 该页面不存在
      </h1>
      <p class="not-found-page__hint">
        你访问的路径 <code data-testid="not-found-path">{{ attemptedPath }}</code> 还没有被定义。
        如果你是从游戏内进入的，请回到主世界并联系商人/NPC 重新触发。
      </p>
      <p
        v-if="countdown > 0"
        class="not-found-page__countdown"
        data-testid="not-found-countdown"
      >
        {{ countdown }} 秒后自动回到主世界
      </p>
      <div class="not-found-page__actions">
        <button
          type="button"
          class="btn btn-primary"
          @click="backToWorld"
        >
          回到主世界
        </button>
        <button
          type="button"
          class="btn btn-secondary"
          @click="backHistory"
        >
          返回上一页
        </button>
        <button
          type="button"
          class="btn btn-secondary"
          @click="backToLogin"
        >
          回到登录
        </button>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.not-found-page {
  max-width: 540px;
  margin: 64px auto;
  padding: 24px 28px;
  background: rgba(28, 24, 16, 0.94);
  border: 3px solid #d4af37;
  border-radius: 10px;
  color: #fff7e6;
  font-family: var(--pyt-font-pixel), 'PingFang SC', 'Noto Sans CJK SC', monospace;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  text-align: center;
}

.not-found-page__title {
  margin: 0 0 12px;
  font-size: 22px;
  color: #ffd166;
  text-shadow: 1px 1px 0 #2a1a05;
  letter-spacing: 1px;
}

.not-found-page__hint {
  font-size: 14px;
  color: #fff7e6;
  margin: 0 0 14px;
  line-height: 1.6;
}

.not-found-page__countdown {
  font-size: 13px;
  color: #ffd166;
  margin: 0 0 20px;
  animation: not-found-pulse 1s ease-in-out infinite;
}

@keyframes not-found-pulse {
  0%, 100% { opacity: 0.65; }
  50% { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .not-found-page__countdown {
    animation: none;
  }
}

.not-found-page__hint code {
  background: rgba(0, 0, 0, 0.4);
  padding: 2px 6px;
  border-radius: 4px;
  color: #ffd166;
}

.not-found-page__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.btn {
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-family: inherit;
  font-size: 13px;
  transition: background 120ms, transform 80ms;
}

.btn-primary {
  background: #d4af37;
  border: 2px solid #f1c453;
  color: #2a1a05;
  font-weight: 700;
}

.btn-primary:hover {
  background: #f1c453;
}

.btn-secondary {
  background: rgba(212, 175, 55, 0.18);
  border: 2px solid #d4af37;
  color: #fff7e6;
}

.btn-secondary:hover {
  background: rgba(212, 175, 55, 0.34);
}

.btn:active {
  transform: translateY(1px);
}

@media (max-width: 640px) {
  .not-found-page {
    margin: 24px 12px;
    padding: 16px;
  }
}
</style>
