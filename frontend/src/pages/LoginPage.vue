<script setup lang="ts">
// 登录按钮点击是首次可靠的 user gesture，借此解锁 audio context，
// 让玩家进入 /world 时 WorldScene 的 BGM 不被浏览器 autoplay 策略静音。
// onMounted 主动 fetch /healthz 预检后端是否就绪，避免登录卡 10 秒才报「无法连接服务器」。
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

const DEMO_USERNAME = 'demo-stu-01';
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
    <!-- 远景层：星露谷暖色像素小镇 hero（AI 原创，CC-BY-SA 3.0+ 入库）
         位置：/assets/pixel/ui/login-hero-stardew.png
         样式：cover 撑满容器，pixelated 渲染保留像素感。 -->
    <div
      class="auth-page__hero"
      aria-hidden="true"
    />

    <!-- 主场景：星露谷小镇全景 -->
    <div
      class="auth-page__bg-art"
      aria-hidden="true"
    >
      <svg
        viewBox="0 0 480 220"
        preserveAspectRatio="xMidYEnd slice"
        xmlns="http://www.w3.org/2000/svg"
        shape-rendering="crispEdges"
      >
        <!-- 草地 3 层（深→浅做日光感） -->
        <rect
          x="0"
          y="130"
          width="480"
          height="90"
          fill="#a8d088"
        />
        <rect
          x="0"
          y="150"
          width="480"
          height="70"
          fill="#82b56a"
        />
        <rect
          x="0"
          y="180"
          width="480"
          height="40"
          fill="#6ba059"
        />
        <!-- 远栏栅 -->
        <g
          stroke="#6e4f1d"
          stroke-width="1.5"
        >
          <line
            x1="0"
            y1="148"
            x2="480"
            y2="148"
          />
          <line
            x1="0"
            y1="146"
            x2="480"
            y2="146"
          />
        </g>
        <g fill="#8a6c2c">
          <rect
            x="20"
            y="142"
            width="2"
            height="14"
          />
          <rect
            x="60"
            y="142"
            width="2"
            height="14"
          />
          <rect
            x="100"
            y="142"
            width="2"
            height="14"
          />
          <rect
            x="140"
            y="142"
            width="2"
            height="14"
          />
          <rect
            x="180"
            y="142"
            width="2"
            height="14"
          />
          <rect
            x="220"
            y="142"
            width="2"
            height="14"
          />
          <rect
            x="260"
            y="142"
            width="2"
            height="14"
          />
          <rect
            x="300"
            y="142"
            width="2"
            height="14"
          />
          <rect
            x="340"
            y="142"
            width="2"
            height="14"
          />
          <rect
            x="380"
            y="142"
            width="2"
            height="14"
          />
          <rect
            x="420"
            y="142"
            width="2"
            height="14"
          />
          <rect
            x="460"
            y="142"
            width="2"
            height="14"
          />
        </g>
        <!-- 房子 1：红屋顶 -->
        <rect
          x="30"
          y="100"
          width="50"
          height="45"
          fill="#c9a86b"
        />
        <polygon
          points="25,100 55,72 85,100"
          fill="#b8442d"
        />
        <rect
          x="50"
          y="118"
          width="10"
          height="27"
          fill="#4d3613"
        />
        <rect
          x="36"
          y="110"
          width="6"
          height="6"
          fill="#fff7e6"
        />
        <rect
          x="68"
          y="110"
          width="6"
          height="6"
          fill="#fff7e6"
        />
        <!-- 烟囱 -->
        <rect
          x="70"
          y="80"
          width="6"
          height="14"
          fill="#6e4f1d"
        />
        <!-- 烟 -->
        <ellipse
          cx="73"
          cy="76"
          rx="6"
          ry="3"
          fill="#fff7e6"
          opacity="0.6"
        />
        <ellipse
          cx="76"
          cy="68"
          rx="5"
          ry="3"
          fill="#fff7e6"
          opacity="0.4"
        />

        <!-- 房子 2：蓝屋顶（镇长 Py 镇厅，最高最大） -->
        <rect
          x="195"
          y="78"
          width="60"
          height="68"
          fill="#fff7e6"
        />
        <polygon
          points="185,78 225,40 265,78"
          fill="#2a6f97"
        />
        <rect
          x="210"
          y="108"
          width="20"
          height="38"
          fill="#4d3613"
        />
        <rect
          x="215"
          y="116"
          width="10"
          height="14"
          fill="#ffd166"
        />
        <rect
          x="200"
          y="92"
          width="8"
          height="8"
          fill="#9bc1d4"
        />
        <rect
          x="242"
          y="92"
          width="8"
          height="8"
          fill="#9bc1d4"
        />
        <!-- 大门匾额：Pythontopia 镇厅 -->
        <rect
          x="206"
          y="100"
          width="38"
          height="6"
          fill="#d4af37"
        />
        <!-- 钟塔顶上小金球 -->
        <circle
          cx="225"
          cy="36"
          r="4"
          fill="#ffd166"
        />

        <!-- 房子 3：黄屋顶 -->
        <rect
          x="305"
          y="105"
          width="44"
          height="40"
          fill="#f1c453"
        />
        <polygon
          points="298,105 327,80 356,105"
          fill="#8a6c2c"
        />
        <rect
          x="320"
          y="120"
          width="12"
          height="25"
          fill="#4d3613"
        />

        <!-- 房子 4：橘屋顶 -->
        <rect
          x="385"
          y="108"
          width="42"
          height="38"
          fill="#c2871f"
        />
        <polygon
          points="378,108 406,82 434,108"
          fill="#6e4f1d"
        />
        <rect
          x="396"
          y="124"
          width="12"
          height="22"
          fill="#4d3613"
        />

        <!-- 树 4 棵 -->
        <g>
          <rect
            x="115"
            y="118"
            width="6"
            height="22"
            fill="#6e4f1d"
          />
          <circle
            cx="118"
            cy="112"
            r="14"
            fill="#5fa54a"
          />
          <circle
            cx="111"
            cy="118"
            r="9"
            fill="#73b66a"
          />
        </g>
        <g>
          <rect
            x="160"
            y="120"
            width="6"
            height="20"
            fill="#6e4f1d"
          />
          <circle
            cx="163"
            cy="115"
            r="12"
            fill="#73b66a"
          />
        </g>
        <g>
          <rect
            x="275"
            y="120"
            width="6"
            height="22"
            fill="#6e4f1d"
          />
          <circle
            cx="278"
            cy="114"
            r="13"
            fill="#5fa54a"
          />
        </g>
        <g>
          <rect
            x="365"
            y="124"
            width="5"
            height="18"
            fill="#6e4f1d"
          />
          <circle
            cx="367"
            cy="118"
            r="10"
            fill="#73b66a"
          />
        </g>

        <!-- 灯柱（点亮） -->
        <rect
          x="90"
          y="120"
          width="2"
          height="28"
          fill="#4d3613"
        />
        <rect
          x="86"
          y="116"
          width="10"
          height="6"
          fill="#ffd166"
        />
        <rect
          x="340"
          y="120"
          width="2"
          height="28"
          fill="#4d3613"
        />
        <rect
          x="336"
          y="116"
          width="10"
          height="6"
          fill="#ffd166"
        />

        <!-- 田地（橙色 patch + 多色小花） -->
        <rect
          x="0"
          y="160"
          width="36"
          height="28"
          fill="#c08a55"
        />
        <circle
          cx="6"
          cy="166"
          r="2"
          fill="#f06"
        />
        <circle
          cx="14"
          cy="174"
          r="2"
          fill="#fc0"
        />
        <circle
          cx="24"
          cy="168"
          r="2"
          fill="#9cf"
        />
        <circle
          cx="30"
          cy="180"
          r="2"
          fill="#f06"
        />
        <rect
          x="448"
          y="168"
          width="32"
          height="24"
          fill="#c08a55"
        />
        <circle
          cx="456"
          cy="176"
          r="2"
          fill="#fc0"
        />
        <circle
          cx="468"
          cy="184"
          r="2"
          fill="#f06"
        />

        <!-- 镇长 Py 像素角色（站在镇厅门口） -->
        <g transform="translate(220,116)">
          <!-- 头 -->
          <rect
            x="0"
            y="0"
            width="10"
            height="10"
            fill="#f4caa5"
          />
          <!-- 头发：紫色 Python 头巾 -->
          <rect
            x="0"
            y="-2"
            width="10"
            height="4"
            fill="#3776ab"
          />
          <rect
            x="-1"
            y="0"
            width="2"
            height="3"
            fill="#3776ab"
          />
          <rect
            x="9"
            y="0"
            width="2"
            height="3"
            fill="#3776ab"
          />
          <!-- 眼 -->
          <rect
            x="2"
            y="4"
            width="1"
            height="2"
            fill="#000"
          />
          <rect
            x="7"
            y="4"
            width="1"
            height="2"
            fill="#000"
          />
          <!-- 身体：黄色围巾 + 蓝衣 -->
          <rect
            x="0"
            y="10"
            width="10"
            height="2"
            fill="#ffd166"
          />
          <rect
            x="0"
            y="12"
            width="10"
            height="12"
            fill="#3776ab"
          />
          <!-- 腿 -->
          <rect
            x="1"
            y="24"
            width="3"
            height="6"
            fill="#4d3613"
          />
          <rect
            x="6"
            y="24"
            width="3"
            height="6"
            fill="#4d3613"
          />
        </g>

        <!-- Python 蛇 logo（小镇厅前盘旋的小蛇，主题元素） -->
        <g transform="translate(168,144)">
          <rect
            x="0"
            y="0"
            width="3"
            height="3"
            fill="#3776ab"
          />
          <rect
            x="3"
            y="0"
            width="3"
            height="3"
            fill="#3776ab"
          />
          <rect
            x="6"
            y="0"
            width="3"
            height="3"
            fill="#3776ab"
          />
          <rect
            x="9"
            y="0"
            width="3"
            height="3"
            fill="#ffd166"
          />
          <rect
            x="12"
            y="0"
            width="3"
            height="3"
            fill="#ffd166"
          />
          <rect
            x="12"
            y="3"
            width="3"
            height="3"
            fill="#ffd166"
          />
          <rect
            x="9"
            y="3"
            width="3"
            height="3"
            fill="#ffd166"
          />
          <rect
            x="6"
            y="3"
            width="3"
            height="3"
            fill="#ffd166"
          />
          <rect
            x="0"
            y="3"
            width="3"
            height="3"
            fill="#3776ab"
          />
          <rect
            x="0"
            y="6"
            width="3"
            height="3"
            fill="#ffd166"
          />
          <rect
            x="0"
            y="6"
            width="3"
            height="3"
            fill="#3776ab"
          />
          <!-- 蛇眼 -->
          <rect
            x="1"
            y="1"
            width="1"
            height="1"
            fill="#fff"
          />
          <rect
            x="13"
            y="1"
            width="1"
            height="1"
            fill="#fff"
          />
        </g>
      </svg>
    </div>

    <section class="auth-copy auth-copy--pixel">
      <div class="auth-title-frame">
        <h1 class="auth-title">
          Pythontopia
        </h1>
      </div>
      <p class="auth-subtitle">
        Python 学习像素小镇 · 35 主线 + 7 章 boss + 无尽闯关
      </p>
      <ul
        class="auth-feature-pills"
        aria-label="特色"
      >
        <li>5 重审查 AI 出题</li>
        <li>真课件 PDF 渲染</li>
        <li>65 KC 自适应路径</li>
      </ul>
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
        快速体验：演示学生 demo-stu-01 / P@ssw0rd1
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
  background: #faf3e0;
  overflow: hidden;
}

.auth-page__hero,
.auth-page__bg-art {
  position: absolute;
  left: 0;
  right: 0;
  pointer-events: none;
}
.auth-page__hero {
  top: 0;
  bottom: 0;
  height: 100%;
  z-index: 0;
  background-image: url('/assets/pixel/ui/login-hero-stardew.png');
  background-size: cover;
  background-position: center bottom;
  background-repeat: no-repeat;
  image-rendering: pixelated;
  -ms-interpolation-mode: nearest-neighbor;
}
.auth-page__bg-art {
  bottom: 0;
  height: 55%;
  z-index: 0;
  opacity: 0;
  pointer-events: none;
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

.auth-title-frame {
  display: inline-block;
  padding: 4px 18px;
  background:
    repeating-linear-gradient(135deg, rgba(212, 175, 55, 0.18) 0 6px, transparent 6px 14px),
    rgba(255, 247, 230, 0.92);
  border: 4px solid #d4af37;
  border-radius: var(--pyt-radius-lg);
  box-shadow: 4px 4px 0 var(--pyt-color-text-hint);
  margin-bottom: 12px;
}

.auth-title {
  margin: 0;
  font-family: var(--pyt-font-pixel);
  font-size: 60px;
  line-height: 1.05;
  color: var(--pyt-color-text-primary);
  text-shadow:
    2px 2px 0 #d4af37,
    4px 4px 0 rgba(77, 54, 19, 0.35);
  letter-spacing: 2px;
}

.auth-subtitle {
  margin: 0;
  font-size: 16px;
  color: var(--pyt-color-text-primary);
  font-weight: 600;
  background: rgba(255, 247, 230, 0.84);
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
}

.auth-feature-pills {
  list-style: none;
  padding: 0;
  margin: 12px 0 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.auth-feature-pills li {
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  padding: 2px 10px;
  background: rgba(74, 144, 226, 0.15);
  border: 1px solid #4a90e2;
  color: #1e3a8a;
  border-radius: 3px;
}

.auth-card--pixel {
  border: 3px solid var(--pyt-color-text-primary);
  border-radius: var(--pyt-radius-lg);
  background: var(--pyt-color-bg-elevated);
  box-shadow:
    6px 6px 0 var(--pyt-color-text-primary),
    0 16px 32px rgba(77, 54, 19, 0.18);
  padding: 32px;
}

.auth-card--pixel h2 {
  margin: 0 0 18px;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
  font-size: 22px;
}

.auth-health-banner {
  margin: 0 0 14px;
  padding: 10px 12px;
  border: 2px solid var(--pyt-color-danger);
  border-radius: 6px;
  background: var(--pyt-color-danger-bg);
  color: #7a1f0f;
  font-size: 13px;
  line-height: 1.5;
  box-shadow: 2px 2px 0 var(--pyt-color-danger);
}

.auth-demo-btn {
  margin-top: 16px;
  width: 100%;
  min-height: 44px;
  border: 2px dashed var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
  font-weight: 700;
  border-radius: var(--pyt-radius-lg);
  box-shadow: 2px 2px 0 var(--pyt-color-text-hint);
  transition: transform 80ms ease;
}

.auth-demo-btn:hover:not(:disabled) {
  background: var(--pyt-color-warning-bg);
}

.auth-demo-btn:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 var(--pyt-color-text-hint);
}

.auth-demo-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.auth-link {
  display: inline-block;
  margin-top: 14px;
  color: var(--pyt-color-info);
  font-weight: 600;
}

:deep(.auth-form input.has-error) {
  border-color: var(--pyt-color-danger);
  background: var(--pyt-color-danger-bg);
}

:deep(.auth-form button[type='submit']) {
  background: var(--pyt-color-gold-bright);
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: 6px;
  color: var(--pyt-color-text-primary);
  font-weight: 800;
  box-shadow: 3px 3px 0 var(--pyt-color-text-primary);
  min-height: 44px;
}

:deep(.auth-form button[type='submit']:disabled) {
  opacity: 0.6;
  cursor: progress;
}
</style>
