<script setup lang="ts">
// R-13：游戏内设置弹窗。由 HudShell 的「设置」按钮（,）触发开关。
//
// 设计要点：
//   1. 完全 store-driven：hud.settingsOpen 控制 v-if，关闭按钮 / ESC / 蒙层点击都
//      走 hud.closeSettings()，避免父组件状态分裂；
//   2. 三档音量控制（BGM/SFX/全局静音）走现有 useAudio 适配器，不绕过 Phaser
//      sound manager；
//   3. 语言切换调用 useI18n().setLocale，立即响应（不要求重新加载页面）；
//   4. 退出登录走 useSessionStore().logout() + 路由跳 /login，与 AppHeader 退出
//      按钮行为对齐；
//   5. 像素风视觉与 HudShell 一致；移动端宽度收敛为 calc(100vw - 32px)；
//   6. a11y：role=dialog + aria-modal + 标题/关闭按钮 + 蒙层点击关闭 + 触摸友好。

import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useHudStore } from '@stores/hud';
import { useSessionStore } from '@stores/session';
import { useAudio } from '@/composables/useAudio';
import { useI18n } from '@/composables/useI18n';
import { SUPPORTED_LOCALES, type Locale } from '@/i18n';
import { playUiClick, setStardewFxMuted } from '@/composables/useStardewFx';
import { useReducedMotion } from '@/composables/useReducedMotion';

const hud = useHudStore();
const session = useSessionStore();
const router = useRouter();
const audio = useAudio();
const { locale, setLocale } = useI18n();

const muted = ref(audio.isMuted());
const bgmVolume = ref(Math.round(audio.getBgmVolume() * 100));
const sfxVolume = ref(Math.round(audio.getSfxVolume() * 100));
const reducedMotion = useReducedMotion();

// R-13 迭代 11：滑块拖动时立即生效（不等关闭弹窗）。
watch(bgmVolume, (next) => {
  audio.setBgmVolume(Math.max(0, Math.min(100, Number(next))) / 100);
});
watch(sfxVolume, (next) => {
  audio.setSfxVolume(Math.max(0, Math.min(100, Number(next))) / 100);
});

watch(
  () => hud.settingsOpen,
  (open) => {
    if (open) {
      muted.value = audio.isMuted();
    }
  },
);

function onMutedToggle(event: Event): void {
  const target = event.target as HTMLInputElement;
  muted.value = target.checked;
  audio.setMuted(muted.value);
  // R-13：Tone.js 程序化 SFX 与 Phaser sound manager 共享静音开关，
  // 不然玩家把游戏调静音，UI 点击声仍叮叮响一片。
  setStardewFxMuted(muted.value);
}

function onChangeLocale(event: Event): void {
  const target = event.target as HTMLSelectElement;
  const next = target.value as Locale;
  setLocale(next);
}

function close(): void {
  playUiClick();
  hud.closeSettings();
}

function onLogout(): void {
  playUiClick();
  session.logout();
  hud.closeSettings();
  void router.push({ name: 'login' });
}

/**
 * R-13 迭代 16：重置「新手引导已读」标记，让玩家下次进入 /world 时
 * 再次看到 FirstTimeHelpModal。对应 localStorage 键见 FirstTimeHelpModal.vue。
 */
function onResetHelp(): void {
  playUiClick();
  try {
    window.localStorage.removeItem('pyt_helped_v1');
    window.localStorage.removeItem('pyt_first_world_visit');
  } catch {
    // 隐私模式忽略
  }
  hud.closeSettings();
  // 刷一下页面让 FirstTimeHelpModal 重新触发
  if (typeof window !== 'undefined') {
    void router.push({ name: 'world' }).then(() => {
      window.location.reload();
    });
  }
}

function onMaskClick(event: MouseEvent): void {
  if (event.target === event.currentTarget) {
    close();
  }
}

function onKey(event: KeyboardEvent): void {
  if (!hud.settingsOpen) return;
  if (event.key === 'Escape') {
    close();
    event.preventDefault();
  }
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', onKey);
  }
});

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKey);
  }
});

const localeOptions = computed(() =>
  SUPPORTED_LOCALES.map((code) => ({
    code,
    label: code === 'zh-CN' ? '中文（简体）' : 'English',
  })),
);
</script>

<template>
  <div
    v-if="hud.settingsOpen"
    class="settings-mask"
    data-testid="settings-modal"
    role="dialog"
    aria-modal="true"
    :aria-label="'设置'"
    @click="onMaskClick"
  >
    <section
      class="settings-card"
      data-testid="settings-card"
    >
      <header class="settings-card__head">
        <h2 class="settings-card__title">
          设置
        </h2>
        <button
          type="button"
          class="settings-card__close"
          data-testid="settings-close"
          aria-label="关闭设置"
          @click="close"
        >
          ✕
        </button>
      </header>
      <div class="settings-card__body">
        <label class="settings-row">
          <span class="settings-row__label">全局静音</span>
          <input
            type="checkbox"
            data-testid="settings-mute"
            :checked="muted"
            @change="onMutedToggle"
          >
        </label>
        <label class="settings-row">
          <span class="settings-row__label">BGM 音量</span>
          <input
            v-model.number="bgmVolume"
            type="range"
            min="0"
            max="100"
            data-testid="settings-bgm"
          >
          <span class="settings-row__value">{{ bgmVolume }}</span>
        </label>
        <label class="settings-row">
          <span class="settings-row__label">SFX 音量</span>
          <input
            v-model.number="sfxVolume"
            type="range"
            min="0"
            max="100"
            data-testid="settings-sfx"
          >
          <span class="settings-row__value">{{ sfxVolume }}</span>
        </label>
        <label class="settings-row">
          <span class="settings-row__label">语言</span>
          <select
            :value="locale"
            data-testid="settings-locale"
            @change="onChangeLocale"
          >
            <option
              v-for="option in localeOptions"
              :key="option.code"
              :value="option.code"
            >
              {{ option.label }}
            </option>
          </select>
        </label>
        <div
          class="settings-row settings-row--info"
          data-testid="settings-reduced-motion"
        >
          <span class="settings-row__label">动效偏好</span>
          <span class="settings-row__value">
            {{ reducedMotion ? '已减少（来自系统设置）' : '正常' }}
          </span>
        </div>
      </div>
      <footer class="settings-card__foot">
        <button
          type="button"
          class="settings-card__reset"
          data-testid="settings-reset-help"
          aria-label="重新查看新手引导"
          @click="onResetHelp"
        >
          重看引导
        </button>
        <button
          type="button"
          class="settings-card__logout"
          data-testid="settings-logout"
          @click="onLogout"
        >
          退出登录
        </button>
        <button
          type="button"
          class="settings-card__ok"
          data-testid="settings-ok"
          @click="close"
        >
          完成
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.settings-mask {
  position: fixed;
  inset: 0;
  background: rgba(8, 8, 12, 0.62);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  pointer-events: auto;
  backdrop-filter: blur(2px);
}

.settings-card {
  background: rgba(28, 24, 16, 0.96);
  border: 3px solid #d4af37;
  border-radius: 10px;
  color: #fff7e6;
  font-family: 'PythontopiaPixel', 'PingFang SC', 'Noto Sans CJK SC', monospace;
  width: min(420px, calc(100vw - 32px));
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.settings-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(180deg, #4d3613 0%, #2a1a05 100%);
  border-bottom: 2px solid #d4af37;
}

.settings-card__title {
  margin: 0;
  font-size: 18px;
  color: #ffd166;
  text-shadow: 1px 1px 0 #2a1a05;
  letter-spacing: 1px;
}

.settings-card__close {
  background: transparent;
  border: 2px solid rgba(255, 247, 230, 0.4);
  color: #fff7e6;
  font-size: 14px;
  width: 32px;
  height: 32px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 120ms;
}

.settings-card__close:hover {
  background: rgba(255, 247, 230, 0.12);
}

.settings-card__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px;
  overflow-y: auto;
  font-size: 13px;
}

.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.settings-row__label {
  flex: 0 0 auto;
  color: #ffd166;
}

.settings-row input[type='range'] {
  flex: 1 1 auto;
  accent-color: #f1c453;
}

.settings-row input[type='checkbox'] {
  width: 18px;
  height: 18px;
  accent-color: #f1c453;
}

.settings-row select {
  background: #2a1a05;
  border: 2px solid #d4af37;
  color: #fff7e6;
  padding: 4px 6px;
  border-radius: 4px;
  font-family: inherit;
  font-size: 13px;
}

.settings-row__value {
  color: #fff7e6;
  font-weight: 700;
  min-width: 28px;
  text-align: right;
}

.settings-card__foot {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-top: 2px solid rgba(212, 175, 55, 0.4);
  background: rgba(0, 0, 0, 0.2);
}

.settings-card__reset {
  flex: 0 0 auto;
  padding: 8px 12px;
  background: rgba(106, 161, 214, 0.18);
  border: 2px solid #6aa1d6;
  color: #fff7e6;
  border-radius: 4px;
  cursor: pointer;
  font-family: inherit;
  font-size: 12px;
  transition: background 120ms;
}

.settings-card__reset:hover {
  background: rgba(106, 161, 214, 0.36);
}

.settings-card__logout {
  flex: 0 0 auto;
  padding: 8px 14px;
  background: rgba(230, 57, 70, 0.22);
  border: 2px solid #e63946;
  color: #fff7e6;
  border-radius: 4px;
  cursor: pointer;
  font-family: inherit;
  font-size: 13px;
  transition: background 120ms;
}

.settings-card__logout:hover {
  background: rgba(230, 57, 70, 0.42);
}

.settings-card__ok {
  flex: 1 1 auto;
  padding: 8px 14px;
  background: #d4af37;
  border: 2px solid #f1c453;
  color: #2a1a05;
  font-weight: 700;
  border-radius: 4px;
  cursor: pointer;
  font-family: inherit;
  font-size: 13px;
  transition: background 120ms, transform 80ms;
}

.settings-card__ok:hover {
  background: #f1c453;
}

.settings-card__ok:active {
  transform: translateY(1px);
}

@media (max-width: 640px) {
  .settings-card__body {
    padding: 14px 12px;
    gap: 10px;
  }
  .settings-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
