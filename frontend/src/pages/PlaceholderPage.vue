<script setup lang="ts">
/** 未开放功能的占位页：由路由 props 传入 title / subtitle。 */
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
    linear-gradient(180deg, var(--pyt-color-bg-elevated) 0%, #f1d896 100%);
  border: 4px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  box-shadow:
    inset 0 0 0 2px rgba(212, 175, 55, 0.65),
    6px 6px 0 var(--pyt-color-shadow);
  color: #3a2a14;
  font-family: var(--pyt-font-pixel);
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
    linear-gradient(180deg, var(--pyt-color-danger) 0%, #8b3520 100%);
  clip-path: polygon(8% 100%, 50% 0%, 92% 100%);
  filter: drop-shadow(0 2px 0 rgba(0, 0, 0, 0.2));
}

.illust-wall {
  position: absolute;
  inset: 45px 20px 22px 20px;
  background: linear-gradient(180deg, #f1d896 0%, #d6b97a 100%);
  border: 3px solid var(--pyt-color-text-hint);
}

.illust-window {
  position: absolute;
  top: 14px;
  width: 22px;
  height: 22px;
  background:
    linear-gradient(180deg, #9bc1d4 0%, #6b8da8 100%);
  border: 2px solid var(--pyt-color-text-hint);
}
.illust-window::before,
.illust-window::after {
  content: '';
  position: absolute;
  background: var(--pyt-color-text-hint);
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
  background: linear-gradient(180deg, var(--pyt-color-text-hint) 0%, var(--pyt-color-text-primary) 100%);
  border: 2px solid var(--pyt-color-shadow);
  box-shadow: inset 0 0 0 2px rgba(255, 215, 0, 0.2);
}
.illust-door::after {
  content: '';
  position: absolute;
  top: 22px;
  right: 5px;
  width: 4px;
  height: 4px;
  background: var(--pyt-color-gold-bright);
  box-shadow: 0 0 4px var(--pyt-color-gold-bright);
}

.illust-lantern {
  position: absolute;
  width: 8px;
  height: 10px;
  background: var(--pyt-color-gold-bright);
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
  color: var(--pyt-color-text-primary);
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
.chip--coming { color: var(--pyt-color-danger); border-color: var(--pyt-color-danger); }
.chip--bind   { color: var(--pyt-color-info); border-color: var(--pyt-color-info); }

.placeholder-actions {
  display: flex;
  justify-content: center;
}

.placeholder-btn {
  padding: 10px 22px;
  border: 3px solid var(--pyt-color-text-primary);
  background: var(--pyt-color-gold-bright);
  color: var(--pyt-color-text-primary);
  font-family: inherit;
  font-size: 14px;
  letter-spacing: 1px;
  cursor: pointer;
  box-shadow: 3px 3px 0 var(--pyt-color-shadow);
  transition: transform 0.1s, box-shadow 0.1s;
}
.placeholder-btn:hover {
  transform: translate(1px, 1px);
  box-shadow: 2px 2px 0 var(--pyt-color-shadow);
}
.placeholder-btn:active {
  transform: translate(3px, 3px);
  box-shadow: 0 0 0 var(--pyt-color-shadow);
}
.placeholder-btn--primary {
  background: linear-gradient(180deg, var(--pyt-color-gold-bright) 0%, #f4b400 100%);
}

@media (prefers-reduced-motion: reduce) {
  .placeholder-card {
    animation: none;
  }
}
</style>
