<script setup lang="ts">
/**
 * PlaceholderPage —— 星露谷风格占位页（R-13 §-1 升级）。
 *
 * 旧版本只是 title + subtitle 裸文字，进入 6 个建筑（民居/灯塔/谷仓/鸡舍）后玩家
 * 一眼就觉得「这不是个游戏页」。本次改造保留路由 props 兼容，
 * 增加：
 *   1. 牛皮纸 9-slice 风格内容板，与 DialogModal / WorkshopPanel 配色统一；
 *   2. 像素插画占位（CSS 绘制门 + 窗 + 装饰物），不依赖额外资源；
 *   3. 返回小镇按钮 + 「下次开放预告」chip；
 *   4. 进入时 0.4s fade-in，避免硬切。
 */
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';

defineProps<{
  title: string;
  subtitle: string;
}>();

const router = useRouter();

function back(): void {
  void router.push('/world');
}
</script>

<template>
  <AppLayout>
    <section
      class="placeholder-page"
      data-testid="placeholder-page"
    >
      <div class="placeholder-card">
        <div
          class="placeholder-illust"
          aria-hidden="true"
        >
          <div class="illust-roof" />
          <div class="illust-wall">
            <div class="illust-window window--left" />
            <div class="illust-window window--right" />
            <div class="illust-door" />
            <div class="illust-lantern lantern--left" />
            <div class="illust-lantern lantern--right" />
          </div>
          <div class="illust-ground">
            <div class="illust-flower flower--a" />
            <div class="illust-flower flower--b" />
            <div class="illust-flower flower--c" />
          </div>
        </div>

        <h1 class="placeholder-title">
          {{ title }}
        </h1>
        <p class="placeholder-sub">
          {{ subtitle }}
        </p>

        <div class="placeholder-chips">
          <span class="chip chip--coming">即将开放</span>
          <span class="chip chip--bind">由本章节主线推进解锁</span>
        </div>

        <div class="placeholder-actions">
          <button
            type="button"
            class="placeholder-btn placeholder-btn--primary"
            data-testid="placeholder-back"
            @click="back"
          >
            ← 返回 Pythontopia 小镇
          </button>
        </div>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.placeholder-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background:
    radial-gradient(ellipse at top, rgba(255, 247, 230, 0.95) 0%, rgba(241, 216, 150, 0.6) 60%, rgba(212, 175, 55, 0.2) 100%);
}

.placeholder-card {
  position: relative;
  width: min(560px, 100%);
  padding: 36px 32px 28px;
  background:
    repeating-linear-gradient(135deg, rgba(212, 175, 55, 0.07) 0 8px, transparent 8px 18px),
    linear-gradient(180deg, #fff7e6 0%, #f1d896 100%);
  border: 4px solid #6e4f1d;
  border-radius: 14px;
  box-shadow:
    inset 0 0 0 2px rgba(212, 175, 55, 0.65),
    6px 6px 0 #2a1a05;
  color: #3a2a14;
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12', monospace);
  text-align: center;
  animation: placeholder-in 0.4s ease-out;
}

@keyframes placeholder-in {
  from { transform: translateY(12px); opacity: 0; }
  to   { transform: translateY(0); opacity: 1; }
}

.placeholder-illust {
  position: relative;
  width: 200px;
  height: 160px;
  margin: 0 auto 20px;
  image-rendering: pixelated;
}

.illust-roof {
  position: absolute;
  inset: 0 20px auto 20px;
  height: 50px;
  background:
    linear-gradient(180deg, #b8442d 0%, #8b3520 100%);
  clip-path: polygon(8% 100%, 50% 0%, 92% 100%);
  filter: drop-shadow(0 2px 0 rgba(0, 0, 0, 0.2));
}

.illust-wall {
  position: absolute;
  inset: 45px 20px 22px 20px;
  background: linear-gradient(180deg, #f1d896 0%, #d6b97a 100%);
  border: 3px solid #6e4f1d;
}

.illust-window {
  position: absolute;
  top: 14px;
  width: 22px;
  height: 22px;
  background:
    linear-gradient(180deg, #9bc1d4 0%, #6b8da8 100%);
  border: 2px solid #6e4f1d;
}
.illust-window::before,
.illust-window::after {
  content: '';
  position: absolute;
  background: #6e4f1d;
}
.illust-window::before {
  inset: 0 50% 0 50%;
  width: 2px;
  transform: translateX(-1px);
}
.illust-window::after {
  inset: 50% 0 50% 0;
  height: 2px;
  transform: translateY(-1px);
}
.window--left  { left: 12px; }
.window--right { right: 12px; }

.illust-door {
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 28px;
  height: 42px;
  transform: translateX(-50%);
  background: linear-gradient(180deg, #6e4f1d 0%, #4d3613 100%);
  border: 2px solid #2a1a05;
  box-shadow: inset 0 0 0 2px rgba(255, 215, 0, 0.2);
}
.illust-door::after {
  content: '';
  position: absolute;
  top: 22px;
  right: 5px;
  width: 4px;
  height: 4px;
  background: #ffd166;
  box-shadow: 0 0 4px #ffd166;
}

.illust-lantern {
  position: absolute;
  width: 8px;
  height: 10px;
  background: #ffd166;
  box-shadow: 0 0 10px rgba(255, 209, 102, 0.75);
  top: 32px;
}
.lantern--left  { left: -6px; }
.lantern--right { right: -6px; }

.illust-ground {
  position: absolute;
  inset: auto 0 0 0;
  height: 16px;
  background: linear-gradient(180deg, #82b56a 0%, #6ba059 100%);
}
.illust-flower {
  position: absolute;
  bottom: 4px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.flower--a { left: 18px;  background: #f06; }
.flower--b { left: 92px;  background: #fc0; }
.flower--c { right: 18px; background: #9cf; }

.placeholder-title {
  margin: 0 0 8px;
  font-size: 26px;
  color: #4d3613;
  text-shadow: 2px 2px 0 rgba(212, 175, 55, 0.55);
  letter-spacing: 1px;
}

.placeholder-sub {
  margin: 0 0 18px;
  color: #5d4524;
  font-size: 14px;
  line-height: 1.6;
}

.placeholder-chips {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0 0 22px;
}
.chip {
  padding: 4px 12px;
  font-size: 11px;
  background: rgba(255, 247, 230, 0.6);
  border: 2px solid #d4af37;
  border-radius: 999px;
}
.chip--coming { color: #b8442d; border-color: #b8442d; }
.chip--bind   { color: #2a6f97; border-color: #2a6f97; }

.placeholder-actions {
  display: flex;
  justify-content: center;
}

.placeholder-btn {
  padding: 10px 22px;
  border: 3px solid #4d3613;
  background: #ffd166;
  color: #4d3613;
  font-family: inherit;
  font-size: 14px;
  letter-spacing: 1px;
  cursor: pointer;
  box-shadow: 3px 3px 0 #2a1a05;
  transition: transform 0.1s, box-shadow 0.1s;
}
.placeholder-btn:hover {
  transform: translate(1px, 1px);
  box-shadow: 2px 2px 0 #2a1a05;
}
.placeholder-btn:active {
  transform: translate(3px, 3px);
  box-shadow: 0 0 0 #2a1a05;
}
.placeholder-btn--primary {
  background: linear-gradient(180deg, #ffd166 0%, #f4b400 100%);
}

@media (prefers-reduced-motion: reduce) {
  .placeholder-card {
    animation: none;
  }
}
</style>
