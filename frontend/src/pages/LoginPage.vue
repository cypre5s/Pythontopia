<script setup lang="ts">
// W4-BGM：登录按钮点击是首次可靠的 user gesture，借此解锁 audio context，
// 让玩家进入 /world 时 WorldScene 的 BGM 不再被浏览器 autoplay 策略静音。
// W4-FINAL-POLISH：onMounted 主动 fetch /healthz，提前告知玩家后端是否就绪，
// 避免登录卡 10 秒才看到"无法连接服务器"。
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import LoginForm from '@/components/auth/LoginForm.vue';
import { ApiClientError } from '@api/http';
import type { LoginRequest } from '@api/dto/LoginRequest';
import { unlockAudio } from '@/composables/useAudio';
import { useSessionStore } from '@stores/session';
import { useToastStore } from '@stores/toast';

const session = useSessionStore();
const toast = useToastStore();
const router = useRouter();
const route = useRoute();

const loading = ref(false);
const backendReady = ref<boolean | null>(null);
const loginFormRef = ref<InstanceType<typeof LoginForm> | null>(null);

const DEMO_USERNAME = 'demo';
const DEMO_PASSWORD = 'P@ssw0rd1';

async function checkBackendHealth(): Promise<void> {
  try {
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), 4_000);
    const res = await fetch('/healthz', { signal: controller.signal });
    window.clearTimeout(timer);
    backendReady.value = res.ok;
  } catch {
    backendReady.value = false;
  }
}

onMounted(() => {
  void checkBackendHealth();
});

async function submit(req: LoginRequest): Promise<void> {
  if (loading.value) {
    return;
  }
  unlockAudio();
  loading.value = true;
  try {
    await session.login(req);
    toast.show(`欢迎回到 Pythontopia，${session.displayName}！`, 'info');
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/world';
    await router.push(redirect);
  } catch (error) {
    const message = error instanceof ApiClientError ? error.message : '登录失败';
    toast.show(message, 'error');
  } finally {
    loading.value = false;
  }
}

function fillAndSubmitDemo(): void {
  if (loading.value || !loginFormRef.value) {
    return;
  }
  unlockAudio();
  loginFormRef.value.fill(DEMO_USERNAME, DEMO_PASSWORD);
  loginFormRef.value.submit();
}
</script>

<template>
  <main class="auth-page auth-page--pixel">
    <div
      class="auth-page__bg-art"
      aria-hidden="true"
    >
      <svg
        viewBox="0 0 320 200"
        xmlns="http://www.w3.org/2000/svg"
      >
        <rect
          x="0"
          y="120"
          width="320"
          height="80"
          fill="#9fd082"
        />
        <rect
          x="0"
          y="140"
          width="320"
          height="60"
          fill="#7fb56a"
        />
        <rect
          x="40"
          y="80"
          width="40"
          height="40"
          fill="#c9a86b"
        />
        <polygon
          points="35,80 60,55 85,80"
          fill="#b8442d"
        />
        <rect
          x="55"
          y="95"
          width="10"
          height="20"
          fill="#4d3613"
        />
        <rect
          x="120"
          y="70"
          width="50"
          height="50"
          fill="#fff7e6"
        />
        <polygon
          points="115,70 145,40 175,70"
          fill="#2a6f97"
        />
        <rect
          x="135"
          y="90"
          width="20"
          height="30"
          fill="#4d3613"
        />
        <rect
          x="200"
          y="85"
          width="40"
          height="35"
          fill="#f1c453"
        />
        <polygon
          points="195,85 220,60 245,85"
          fill="#8a6c2c"
        />
        <rect
          x="260"
          y="90"
          width="35"
          height="30"
          fill="#c2871f"
        />
        <polygon
          points="255,90 277,65 300,90"
          fill="#6e4f1d"
        />
        <circle
          cx="270"
          cy="30"
          r="14"
          fill="#ffd166"
        />
        <rect
          x="20"
          y="170"
          width="280"
          height="4"
          fill="#4d3613"
          opacity="0.25"
        />
      </svg>
    </div>
    <section class="auth-copy auth-copy--pixel">
      <h1 class="auth-title">
        Pythontopia
      </h1>
      <p class="auth-subtitle">
        Python 学习像素小镇 · 用游戏学编程
      </p>
    </section>
    <section class="auth-card auth-card--pixel">
      <h2>登录</h2>
      <div
        v-if="backendReady === false"
        class="auth-health-banner"
        role="alert"
      >
        服务器还没就绪，请稍等 30 秒后刷新页面。<br>
        如长时间无响应，请联系演示助教。
      </div>
      <LoginForm
        ref="loginFormRef"
        :loading="loading"
        @submit="submit"
      />
      <button
        type="button"
        class="auth-demo-btn"
        :disabled="loading"
        @click="fillAndSubmitDemo"
      >
        快速体验：演示账号 demo / P@ssw0rd1
      </button>
      <RouterLink
        to="/register"
        class="auth-link"
      >
        还没有账号？创建新账号
      </RouterLink>
    </section>
  </main>
</template>

<style scoped>
.auth-page--pixel {
  position: relative;
  background:
    radial-gradient(ellipse at top, #fff3c4 0%, transparent 60%),
    linear-gradient(180deg, #faf3e0 0%, #f4ecd8 100%);
  overflow: hidden;
}

.auth-page__bg-art {
  position: absolute;
  inset: auto 0 0 0;
  height: 45%;
  pointer-events: none;
  opacity: 0.65;
  z-index: 0;
}

.auth-page__bg-art svg {
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
}

.auth-copy--pixel,
.auth-card--pixel {
  position: relative;
  z-index: 1;
}

.auth-title {
  margin: 0 0 12px;
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12', monospace);
  font-size: 56px;
  line-height: 1.1;
  color: #d4af37;
  text-shadow:
    2px 2px 0 #4d3613,
    4px 4px 0 rgba(77, 54, 19, 0.25);
  letter-spacing: 1px;
}

.auth-subtitle {
  margin: 0;
  font-size: 18px;
  color: #6e4f1d;
  font-weight: 600;
}

.auth-card--pixel {
  border: 3px solid #4d3613;
  border-radius: 12px;
  background: #fff7e6;
  box-shadow:
    6px 6px 0 #4d3613,
    0 16px 32px rgba(77, 54, 19, 0.18);
  padding: 32px;
}

.auth-card--pixel h2 {
  margin: 0 0 18px;
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12', monospace);
  color: #4d3613;
  font-size: 22px;
}

.auth-health-banner {
  margin: 0 0 14px;
  padding: 10px 12px;
  border: 2px solid #b8442d;
  border-radius: 6px;
  background: #fde2dc;
  color: #7a1f0f;
  font-size: 13px;
  line-height: 1.5;
  box-shadow: 2px 2px 0 #b8442d;
}

.auth-demo-btn {
  margin-top: 16px;
  width: 100%;
  min-height: 44px;
  border: 2px dashed #6e4f1d;
  background: #fdf3d0;
  color: #4d3613;
  cursor: pointer;
  font-weight: 700;
  border-radius: 8px;
  box-shadow: 2px 2px 0 #6e4f1d;
  transition: transform 80ms ease;
}

.auth-demo-btn:hover:not(:disabled) {
  background: #f5e2c1;
}

.auth-demo-btn:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 #6e4f1d;
}

.auth-demo-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.auth-link {
  display: inline-block;
  margin-top: 14px;
  color: #2a6f97;
  font-weight: 600;
}

:deep(.auth-form input.has-error) {
  border-color: #b8442d;
  background: #fde2dc;
}

:deep(.auth-form button[type='submit']) {
  background: #ffd166;
  border: 2px solid #4d3613;
  border-radius: 6px;
  color: #4d3613;
  font-weight: 800;
  box-shadow: 3px 3px 0 #4d3613;
}

:deep(.auth-form button[type='submit']:disabled) {
  opacity: 0.6;
  cursor: progress;
}
</style>
