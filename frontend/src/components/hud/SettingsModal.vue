<script setup lang="ts">
// 设置弹窗：5 标签页（音频/显示/学习/节奏/通知）。

import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useHudStore } from '@stores/hud';
import { useSessionStore } from '@stores/session';
import { useGameSettingsStore } from '@stores/gameSettings';
import { useAudio } from '@/composables/useAudio';
import { useI18n } from '@/composables/useI18n';
import { SUPPORTED_LOCALES, type Locale } from '@/i18n';
import { playUiClick, setStardewFxMuted } from '@/composables/useStardewFx';
import { useReducedMotion } from '@/composables/useReducedMotion';

const hud = useHudStore();
const session = useSessionStore();
const settings = useGameSettingsStore();
const router = useRouter();
const audio = useAudio();
const { locale, setLocale } = useI18n();
const reducedMotion = useReducedMotion();

// ── 标签页定义 ──────────────────────────────────────────────
type TabKey = 'audio' | 'display' | 'education' | 'rhythm' | 'notifications';

interface TabDef {
  key: TabKey;
  icon: string;
  label: string;
}

const tabs: TabDef[] = [
  { key: 'audio', icon: '♪', label: '音频' },
  { key: 'display', icon: '[]', label: '显示' },
  { key: 'education', icon: '=', label: '学习' },
  { key: 'rhythm', icon: 'T', label: '节奏' },
  { key: 'notifications', icon: '!', label: '通知' },
];

const activeTab = ref<TabKey>('audio');

// ── 音频：本地 ref 双向绑定，watch 即时推送 ─────────────────
const muted = ref(audio.isMuted());
const bgmVolume = ref(Math.round(audio.getBgmVolume() * 100));
const sfxVolume = ref(Math.round(audio.getSfxVolume() * 100));
const ambientVolume = ref(settings.audio.ambientVolume);
const uiSfxVolume = ref(settings.audio.uiSfxVolume);

watch(bgmVolume, (v) => {
  const clamped = Math.max(0, Math.min(100, Number(v)));
  audio.setBgmVolume(clamped / 100);
  settings.updateAudio({ bgmVolume: clamped });
});
watch(sfxVolume, (v) => {
  const clamped = Math.max(0, Math.min(100, Number(v)));
  audio.setSfxVolume(clamped / 100);
  settings.updateAudio({ sfxVolume: clamped });
});
watch(ambientVolume, (v) => {
  settings.updateAudio({ ambientVolume: Math.max(0, Math.min(100, Number(v))) });
});
watch(uiSfxVolume, (v) => {
  settings.updateAudio({ uiSfxVolume: Math.max(0, Math.min(100, Number(v))) });
});

// 面板打开时同步 audio composable 的最新值
watch(
  () => hud.settingsOpen,
  (open) => {
    if (open) {
      muted.value = audio.isMuted();
      bgmVolume.value = Math.round(audio.getBgmVolume() * 100);
      sfxVolume.value = Math.round(audio.getSfxVolume() * 100);
      ambientVolume.value = settings.audio.ambientVolume;
      uiSfxVolume.value = settings.audio.uiSfxVolume;
      activeTab.value = 'audio';
    }
  },
);

function onMutedToggle(event: Event): void {
  const target = event.target as HTMLInputElement;
  muted.value = target.checked;
  audio.setMuted(muted.value);
  setStardewFxMuted(muted.value);
  settings.updateAudio({ muted: muted.value });
}

// ── 显示设置 ────────────────────────────────────────────────
function toggleFullscreen(): void {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen().catch(() => {});
  } else {
    document.exitFullscreen().catch(() => {});
  }
  settings.updateDisplay({ fullscreen: !settings.display.fullscreen });
}

function setPixelPerfect(event: Event): void {
  settings.updateDisplay({ pixelPerfect: (event.target as HTMLInputElement).checked });
}

function setUiScale(event: Event): void {
  settings.updateDisplay({ uiScale: (event.target as HTMLSelectElement).value as 'small' | 'medium' | 'large' });
}

function setColorMode(event: Event): void {
  settings.updateDisplay({ colorMode: (event.target as HTMLSelectElement).value as 'default' | 'high-contrast' | 'colorblind' });
}

function setShowVignette(event: Event): void {
  settings.updateDisplay({ showVignette: (event.target as HTMLInputElement).checked });
}

function setShowParticles(event: Event): void {
  settings.updateDisplay({ showParticles: (event.target as HTMLInputElement).checked });
}

// ── 学习设置 ────────────────────────────────────────────────
function setTextSpeed(event: Event): void {
  settings.updateEducation({ textSpeed: (event.target as HTMLSelectElement).value as 'slow' | 'normal' | 'fast' | 'instant' });
}

function setAutoAdvanceDialog(event: Event): void {
  settings.updateEducation({ autoAdvanceDialog: (event.target as HTMLInputElement).checked });
}

function setAutoAdvanceDelay(event: Event): void {
  settings.updateEducation({ autoAdvanceDelayMs: Number((event.target as HTMLInputElement).value) });
}

const codeFontSize = ref(settings.education.codeFontSize);
watch(codeFontSize, (v) => {
  settings.updateEducation({ codeFontSize: Math.max(12, Math.min(20, Number(v))) });
});
// 面板打开时同步
watch(() => hud.settingsOpen, (open) => {
  if (open) codeFontSize.value = settings.education.codeFontSize;
});

function setDifficulty(event: Event): void {
  settings.updateEducation({ difficulty: (event.target as HTMLSelectElement).value as 'easy' | 'normal' | 'hard' });
}

function setHintFrequency(event: Event): void {
  settings.updateEducation({ hintFrequency: (event.target as HTMLSelectElement).value as 'never' | 'sometimes' | 'always' });
}

function setFocusMode(event: Event): void {
  settings.updateEducation({ focusMode: (event.target as HTMLInputElement).checked });
}

// ── 节奏设置（番茄钟 + 学习提醒） ─────────────────────────
function setPomodoroEnabled(event: Event): void {
  settings.updateEducation({ pomodoroEnabled: (event.target as HTMLInputElement).checked });
}

const pomodoroWork = ref(settings.education.pomodoroWorkMin);
const pomodoroBreak = ref(settings.education.pomodoroBreakMin);
watch(pomodoroWork, (v) => {
  settings.updateEducation({ pomodoroWorkMin: Math.max(5, Math.min(120, Number(v))) });
});
watch(pomodoroBreak, (v) => {
  settings.updateEducation({ pomodoroBreakMin: Math.max(1, Math.min(30, Number(v))) });
});

function setStudyReminder(event: Event): void {
  settings.updateEducation({ studyReminder: (event.target as HTMLInputElement).checked });
}

const studyReminderInterval = ref(settings.education.studyReminderIntervalMin);
watch(studyReminderInterval, (v) => {
  settings.updateEducation({ studyReminderIntervalMin: Math.max(10, Math.min(240, Number(v))) });
});

// 面板打开时同步节奏 ref
watch(() => hud.settingsOpen, (open) => {
  if (open) {
    pomodoroWork.value = settings.education.pomodoroWorkMin;
    pomodoroBreak.value = settings.education.pomodoroBreakMin;
    studyReminderInterval.value = settings.education.studyReminderIntervalMin;
  }
});

// ── 通知设置 ────────────────────────────────────────────────
function setNotification(key: keyof typeof settings.notifications, event: Event): void {
  settings.updateNotifications({ [key]: (event.target as HTMLInputElement).checked });
}

// ── 语言切换 ────────────────────────────────────────────────
function onChangeLocale(event: Event): void {
  setLocale((event.target as HTMLSelectElement).value as Locale);
}

const localeOptions = computed(() =>
  SUPPORTED_LOCALES.map((code) => ({
    code,
    label: code === 'zh-CN' ? '中文（简体）' : 'English',
  })),
);

// ── 面板通用操作 ────────────────────────────────────────────
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

function onResetHelp(): void {
  playUiClick();
  try {
    window.localStorage.removeItem('pyt_helped_v1');
    window.localStorage.removeItem('pyt_first_world_visit');
  } catch { /* 隐私模式忽略 */ }
  hud.closeSettings();
  if (typeof window !== 'undefined') {
    void router.push({ name: 'world' }).then(() => {
      window.location.reload();
    });
  }
}

function onResetAll(): void {
  playUiClick();
  settings.resetAll();
  // 同步本地 ref
  muted.value = settings.audio.muted;
  bgmVolume.value = settings.audio.bgmVolume;
  sfxVolume.value = settings.audio.sfxVolume;
  ambientVolume.value = settings.audio.ambientVolume;
  uiSfxVolume.value = settings.audio.uiSfxVolume;
  codeFontSize.value = settings.education.codeFontSize;
  pomodoroWork.value = settings.education.pomodoroWorkMin;
  pomodoroBreak.value = settings.education.pomodoroBreakMin;
  studyReminderInterval.value = settings.education.studyReminderIntervalMin;
  // 同步 audio composable
  audio.setMuted(settings.audio.muted);
  audio.setBgmVolume(settings.audio.bgmVolume / 100);
  audio.setSfxVolume(settings.audio.sfxVolume / 100);
  setStardewFxMuted(settings.audio.muted);
}

function onMaskClick(event: MouseEvent): void {
  if (event.target === event.currentTarget) close();
}

function onKey(event: KeyboardEvent): void {
  if (!hud.settingsOpen) return;
  if (event.key === 'Escape') {
    close();
    event.preventDefault();
  }
}

onMounted(() => {
  if (typeof window !== 'undefined') window.addEventListener('keydown', onKey);
});
onUnmounted(() => {
  if (typeof window !== 'undefined') window.removeEventListener('keydown', onKey);
});
</script>

<template>
  <div
    v-if="hud.settingsOpen"
    class="settings-mask"
    data-testid="settings-modal"
    role="dialog"
    aria-modal="true"
    aria-label="设置"
    @click="onMaskClick"
  >
    <section class="settings-card pyt-panel" data-testid="settings-card">
      <!-- 标题栏 -->
      <header class="settings-card__head pyt-panel-header">
        <h2 class="pyt-panel-header__title">设置</h2>
        <button
          type="button"
          class="settings-card__close pyt-btn pyt-btn--ghost"
          data-testid="settings-close"
          aria-label="关闭设置"
          @click="close"
        >✕</button>
      </header>

      <!-- 标签栏 -->
      <nav class="settings-tabs" role="tablist">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          role="tab"
          class="settings-tab"
          :class="{ 'settings-tab--active': activeTab === tab.key }"
          :aria-selected="activeTab === tab.key"
          @click="activeTab = tab.key"
        >
          <span class="settings-tab__icon">{{ tab.icon }}</span>
          <span class="settings-tab__label">{{ tab.label }}</span>
        </button>
      </nav>

      <!-- 内容区 -->
      <div class="settings-content pyt-scroll">
        <!-- ═══ 音频 ═══ -->
        <div v-if="activeTab === 'audio'" class="settings-pane">
          <label class="settings-row">
            <span class="settings-row__label">全局静音</span>
            <span class="settings-row__control">
              <input
                type="checkbox"
                class="pyt-toggle"
                data-testid="settings-mute"
                :checked="muted"
                @change="onMutedToggle"
              >
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">BGM 音量</span>
            <span class="settings-row__control settings-row__control--slider">
              <input v-model.number="bgmVolume" type="range" min="0" max="100" class="pyt-slider" data-testid="settings-bgm">
              <span class="settings-row__value">{{ bgmVolume }}</span>
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">SFX 音量</span>
            <span class="settings-row__control settings-row__control--slider">
              <input v-model.number="sfxVolume" type="range" min="0" max="100" class="pyt-slider" data-testid="settings-sfx">
              <span class="settings-row__value">{{ sfxVolume }}</span>
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">环境音量</span>
            <span class="settings-row__control settings-row__control--slider">
              <input v-model.number="ambientVolume" type="range" min="0" max="100" class="pyt-slider">
              <span class="settings-row__value">{{ ambientVolume }}</span>
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">UI 音效</span>
            <span class="settings-row__control settings-row__control--slider">
              <input v-model.number="uiSfxVolume" type="range" min="0" max="100" class="pyt-slider">
              <span class="settings-row__value">{{ uiSfxVolume }}</span>
            </span>
          </label>
        </div>

        <!-- ═══ 显示 ═══ -->
        <div v-else-if="activeTab === 'display'" class="settings-pane">
          <label class="settings-row">
            <span class="settings-row__label">全屏</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.display.fullscreen" @change="toggleFullscreen">
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">像素锐化</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.display.pixelPerfect" @change="setPixelPerfect">
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">UI 缩放</span>
            <span class="settings-row__control">
              <select class="pyt-select" :value="settings.display.uiScale" @change="setUiScale">
                <option value="small">小</option>
                <option value="medium">中</option>
                <option value="large">大</option>
              </select>
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">色彩模式</span>
            <span class="settings-row__control">
              <select class="pyt-select" :value="settings.display.colorMode" @change="setColorMode">
                <option value="default">默认</option>
                <option value="high-contrast">高对比</option>
                <option value="colorblind">色盲友好</option>
              </select>
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">暗角效果</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.display.showVignette" @change="setShowVignette">
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">粒子效果</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.display.showParticles" @change="setShowParticles">
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">语言</span>
            <span class="settings-row__control">
              <select class="pyt-select" :value="locale" data-testid="settings-locale" @change="onChangeLocale">
                <option v-for="opt in localeOptions" :key="opt.code" :value="opt.code">{{ opt.label }}</option>
              </select>
            </span>
          </label>
          <div class="settings-row settings-row--info" data-testid="settings-reduced-motion">
            <span class="settings-row__label">动效偏好</span>
            <span class="settings-row__value settings-row__value--info">
              {{ reducedMotion ? '已减少（来自系统设置）' : '正常' }}
            </span>
          </div>
        </div>

        <!-- ═══ 学习 ═══ -->
        <div v-else-if="activeTab === 'education'" class="settings-pane">
          <label class="settings-row">
            <span class="settings-row__label">文字速度</span>
            <span class="settings-row__control">
              <select class="pyt-select" :value="settings.education.textSpeed" @change="setTextSpeed">
                <option value="slow">慢</option>
                <option value="normal">中</option>
                <option value="fast">快</option>
                <option value="instant">即显</option>
              </select>
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">自动推进对话</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.education.autoAdvanceDialog" @change="setAutoAdvanceDialog">
            </span>
          </label>
          <label v-if="settings.education.autoAdvanceDialog" class="settings-row settings-row--sub">
            <span class="settings-row__label">推进延迟</span>
            <span class="settings-row__control settings-row__control--slider">
              <input
                type="range"
                class="pyt-slider"
                min="1000"
                max="8000"
                step="500"
                :value="settings.education.autoAdvanceDelayMs"
                @input="setAutoAdvanceDelay"
              >
              <span class="settings-row__value">{{ (settings.education.autoAdvanceDelayMs / 1000).toFixed(1) }}s</span>
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">代码字体大小</span>
            <span class="settings-row__control settings-row__control--slider">
              <input v-model.number="codeFontSize" type="range" min="12" max="20" step="1" class="pyt-slider">
              <span class="settings-row__value">{{ codeFontSize }}px</span>
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">难度偏好</span>
            <span class="settings-row__control">
              <select class="pyt-select" :value="settings.education.difficulty" @change="setDifficulty">
                <option value="easy">轻松</option>
                <option value="normal">标准</option>
                <option value="hard">挑战</option>
              </select>
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">提示频率</span>
            <span class="settings-row__control">
              <select class="pyt-select" :value="settings.education.hintFrequency" @change="setHintFrequency">
                <option value="never">从不</option>
                <option value="sometimes">偶尔</option>
                <option value="always">频繁</option>
              </select>
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">专注模式</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.education.focusMode" @change="setFocusMode">
            </span>
          </label>
        </div>

        <!-- ═══ 节奏 ═══ -->
        <div v-else-if="activeTab === 'rhythm'" class="settings-pane">
          <label class="settings-row">
            <span class="settings-row__label">番茄钟</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.education.pomodoroEnabled" @change="setPomodoroEnabled">
            </span>
          </label>
          <template v-if="settings.education.pomodoroEnabled">
            <label class="settings-row settings-row--sub">
              <span class="settings-row__label">工作时长</span>
              <span class="settings-row__control settings-row__control--slider">
                <input v-model.number="pomodoroWork" type="range" min="5" max="120" step="5" class="pyt-slider">
                <span class="settings-row__value">{{ pomodoroWork }}min</span>
              </span>
            </label>
            <label class="settings-row settings-row--sub">
              <span class="settings-row__label">休息时长</span>
              <span class="settings-row__control settings-row__control--slider">
                <input v-model.number="pomodoroBreak" type="range" min="1" max="30" step="1" class="pyt-slider">
                <span class="settings-row__value">{{ pomodoroBreak }}min</span>
              </span>
            </label>
          </template>
          <hr class="pyt-divider">
          <label class="settings-row">
            <span class="settings-row__label">学习提醒</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.education.studyReminder" @change="setStudyReminder">
            </span>
          </label>
          <label v-if="settings.education.studyReminder" class="settings-row settings-row--sub">
            <span class="settings-row__label">提醒间隔</span>
            <span class="settings-row__control settings-row__control--slider">
              <input v-model.number="studyReminderInterval" type="range" min="10" max="240" step="10" class="pyt-slider">
              <span class="settings-row__value">{{ studyReminderInterval }}min</span>
            </span>
          </label>
        </div>

        <!-- ═══ 通知 ═══ -->
        <div v-else-if="activeTab === 'notifications'" class="settings-pane">
          <label class="settings-row">
            <span class="settings-row__label">成就弹窗</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.notifications.achievementPopup" @change="setNotification('achievementPopup', $event)">
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">任务完成提示</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.notifications.questComplete" @change="setNotification('questComplete', $event)">
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">NPC 提醒</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.notifications.npcReminder" @change="setNotification('npcReminder', $event)">
            </span>
          </label>
          <hr class="pyt-divider">
          <label class="settings-row">
            <span class="settings-row__label">进度对老师可见</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.notifications.progressShareWithTeacher" @change="setNotification('progressShareWithTeacher', $event)">
            </span>
          </label>
          <label class="settings-row">
            <span class="settings-row__label">进度对同学可见</span>
            <span class="settings-row__control">
              <input type="checkbox" class="pyt-toggle" :checked="settings.notifications.progressShareWithClassmates" @change="setNotification('progressShareWithClassmates', $event)">
            </span>
          </label>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <footer class="settings-card__foot">
        <button type="button" class="pyt-btn" data-testid="settings-reset-help" @click="onResetHelp">重看引导</button>
        <button type="button" class="pyt-btn" @click="onResetAll">重置设置</button>
        <button type="button" class="pyt-btn pyt-btn--danger" data-testid="settings-logout" @click="onLogout">退出登录</button>
        <button type="button" class="pyt-btn pyt-btn--primary settings-card__ok" data-testid="settings-ok" @click="close">完成</button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
/* ── 遮罩层 ──────────────────────────────────────────────── */
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

/* ── 面板卡片 ────────────────────────────────────────────── */
.settings-card {
  width: min(520px, calc(100vw - 32px));
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.settings-card__close {
  width: 32px;
  height: 32px;
  font-size: 14px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ── 标签栏 ──────────────────────────────────────────────── */
.settings-tabs {
  display: flex;
  background: rgba(0, 0, 0, 0.25);
  border-bottom: 2px solid var(--pyt-panel-border-dark);
  overflow-x: auto;
  scrollbar-width: none;
}
.settings-tabs::-webkit-scrollbar {
  display: none;
}

.settings-tab {
  flex: 1 1 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 6px 6px;
  background: transparent;
  border: none;
  border-bottom: 3px solid transparent;
  color: var(--pyt-text-secondary);
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12', 'PingFang SC', monospace);
  font-size: 11px;
  cursor: pointer;
  transition: color 120ms, border-color 120ms, background 120ms;
  white-space: nowrap;
}

.settings-tab:hover {
  color: var(--pyt-text-primary);
  background: rgba(255, 247, 230, 0.05);
}

.settings-tab--active {
  color: var(--pyt-text-accent);
  border-bottom-color: var(--pyt-text-accent);
  background: rgba(255, 209, 102, 0.08);
}

.settings-tab__icon {
  font-size: 16px;
  line-height: 1;
}

.settings-tab__label {
  letter-spacing: 0.5px;
}

/* ── 内容区 ──────────────────────────────────────────────── */
.settings-content {
  flex: 1 1 auto;
  overflow-y: auto;
  min-height: 0;
}

.settings-pane {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 20px;
}

/* ── 设置行 ──────────────────────────────────────────────── */
.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
}

.settings-row--sub {
  padding-left: 16px;
  opacity: 0.9;
}

.settings-row--info {
  cursor: default;
}

.settings-row__label {
  flex: 0 0 auto;
  color: var(--pyt-text-accent);
  font-size: 13px;
  white-space: nowrap;
}

.settings-row__control {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
}

.settings-row__control--slider {
  flex: 1 1 auto;
  gap: 8px;
}

.settings-row__value {
  color: var(--pyt-text-primary);
  font-weight: 700;
  font-size: 12px;
  min-width: 36px;
  text-align: right;
}

.settings-row__value--info {
  color: var(--pyt-text-secondary);
  font-weight: 400;
}

/* ── 像素风滑块 ──────────────────────────────────────────── */
.pyt-slider {
  flex: 1 1 auto;
  height: 6px;
  accent-color: var(--pyt-text-accent);
  cursor: pointer;
}

/* ── 像素风 Toggle 开关 ─────────────────────────────────── */
.pyt-toggle {
  appearance: none;
  -webkit-appearance: none;
  width: 36px;
  height: 20px;
  background: rgba(255, 247, 230, 0.12);
  border: 2px solid var(--pyt-panel-border-dark);
  border-radius: 2px;
  cursor: pointer;
  position: relative;
  transition: background 150ms, border-color 150ms;
}

.pyt-toggle::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 12px;
  height: 12px;
  background: var(--pyt-text-secondary);
  border-radius: 1px;
  transition: transform 150ms, background 150ms;
}

.pyt-toggle:checked {
  background: rgba(212, 175, 55, 0.3);
  border-color: var(--pyt-text-accent);
}

.pyt-toggle:checked::after {
  transform: translateX(16px);
  background: var(--pyt-text-accent);
}

/* ── 像素风下拉 ──────────────────────────────────────────── */
.pyt-select {
  background: rgba(0, 0, 0, 0.3);
  border: 2px solid var(--pyt-panel-border-dark);
  color: var(--pyt-text-primary);
  padding: 4px 8px;
  border-radius: 2px;
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12', 'PingFang SC', monospace);
  font-size: 12px;
  cursor: pointer;
}

.pyt-select:focus {
  border-color: var(--pyt-text-accent);
  outline: none;
}

/* ── 底部操作栏 ──────────────────────────────────────────── */
.settings-card__foot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-top: 2px solid var(--pyt-panel-border-dark);
  background: rgba(0, 0, 0, 0.2);
}

.settings-card__ok {
  margin-left: auto;
  min-width: 72px;
}

/* ── 分隔线复用 pixel-ui ────────────────────────────────── */
.settings-pane > .pyt-divider {
  margin: 4px 0;
}

/* ── 移动端适配 ──────────────────────────────────────────── */
@media (max-width: 540px) {
  .settings-pane {
    padding: 12px 14px;
    gap: 8px;
  }

  .settings-tab {
    padding: 6px 4px 4px;
    font-size: 10px;
  }

  .settings-tab__icon {
    font-size: 14px;
  }

  .settings-row {
    flex-wrap: wrap;
  }

  .settings-row__control--slider {
    flex-basis: 100%;
  }

  .settings-card__foot {
    flex-wrap: wrap;
    gap: 6px;
  }

  .settings-card__foot .pyt-btn {
    font-size: 11px;
    padding: 6px 10px;
  }
}
</style>
