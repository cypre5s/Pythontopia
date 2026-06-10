<script setup lang="ts">
/** ESC 暂停菜单：4 分组入口（继续 / 学习 / 我 / 系统）+ 快捷键速查 + 退出确认。 */
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useGameSettingsStore } from '@stores/gameSettings';
import { useHudStore } from '@stores/hud';
import { useToastStore } from '@stores/toast';
import { useSessionStore } from '@stores/session';
import { playUiClick } from '@/composables/useStardewFx';

const settings = useGameSettingsStore();
const hud = useHudStore();
const toast = useToastStore();
const session = useSessionStore();
const router = useRouter();

const open = computed(() => settings.pauseMenuOpen);
const showHelp = ref(false);
const showQuitConfirm = ref(false);

const ICON_BASE = '/assets/pixel/icons/ui';

interface MenuItem {
  id: string;
  icon: string;
  label: string;
  action: () => void;
}

interface MenuSection {
  id: string;
  title: string;
  items: MenuItem[];
}

function close(): void {
  showHelp.value = false;
  showQuitConfirm.value = false;
  settings.closePauseMenu();
}

// 菜单按 4 组（继续 / 学习 / 我 / 系统）组织，降低一屏认知负荷。
function go(path: string): void {
  close();
  void router.push(path);
}

const menuSections: MenuSection[] = [
  {
    id: 'resume',
    title: '继续',
    items: [
      { id: 'resume', icon: `${ICON_BASE}/home.svg`, label: '继续游戏', action: () => close() },
    ],
  },
  {
    id: 'learn',
    title: '学习',
    items: [
      { id: 'library', icon: `${ICON_BASE}/book-open.svg`, label: '课程图书馆', action: () => go('/library') },
      { id: 'training', icon: `${ICON_BASE}/shield.svg`, label: '训练场', action: () => go('/training') },
      { id: 'workshop-nav', icon: `${ICON_BASE}/notebook.svg`, label: '百晓工坊', action: () => go('/workshop') },
      { id: 'review', icon: `${ICON_BASE}/scroll-horizontal.svg`, label: '复盘大厅', action: () => go('/review-hall') },
      { id: 'arena', icon: `${ICON_BASE}/flag.svg`, label: '闯关竞技场', action: () => go('/arena/1') },
      { id: 'problems', icon: `${ICON_BASE}/book-open.svg`, label: '题目列表', action: () => go('/problems') },
      { id: 'examples', icon: `${ICON_BASE}/notebook.svg`, label: '例题列表', action: () => go('/examples') },
      { id: 'tutor', icon: `${ICON_BASE}/save.svg`, label: 'AI 助教', action: () => go('/tutor') },
    ],
  },
  {
    id: 'me',
    title: '我',
    items: [
      { id: 'profile-nav', icon: `${ICON_BASE}/shield.svg`, label: '我的画像', action: () => go('/profile') },
      {
        id: 'quest',
        icon: `${ICON_BASE}/flag.svg`,
        label: '冒险地图',
        action: () => go('/mainline-map'),
      },
      {
        id: 'skill',
        icon: `${ICON_BASE}/shield.svg`,
        label: '技能',
        action: () => {
          close();
          hud.toggleSlot('skill');
        },
      },
    ],
  },
  {
    id: 'system',
    title: '系统',
    items: [
      {
        id: 'save',
        icon: `${ICON_BASE}/save.svg`,
        label: '存档',
        action: () => {
          toast.show('已保存', 'success');
        },
      },
      {
        id: 'map',
        icon: `${ICON_BASE}/map-pin.svg`,
        label: '大地图',
        action: () => {
          close();
          hud.toggleSlot('map');
        },
      },
      {
        id: 'settings',
        icon: `${ICON_BASE}/notebook.svg`,
        label: '设置',
        action: () => {
          close();
          hud.openSettings();
        },
      },
      {
        id: 'help',
        icon: `${ICON_BASE}/book-open.svg`,
        label: '操作帮助',
        action: () => {
          showHelp.value = !showHelp.value;
          showQuitConfirm.value = false;
        },
      },
      {
        id: 'quit',
        icon: `${ICON_BASE}/scroll-horizontal.svg`,
        label: '返回主菜单',
        action: () => {
          showQuitConfirm.value = true;
          showHelp.value = false;
        },
      },
    ],
  },
];

const HOTKEYS: readonly { key: string; desc: string }[] = [
  { key: 'WASD / 方向键', desc: '移动' },
  { key: 'F / Enter', desc: '与NPC交谈' },
  { key: 'Tab', desc: '快捷轮盘' },
  { key: 'ESC', desc: '暂停菜单' },
  { key: 'Q', desc: '任务' },
  { key: 'J', desc: '对话' },
  { key: 'M', desc: '地图' },
  { key: 'K', desc: '技能' },
  { key: 'L', desc: '课程' },
  { key: 'Shift+A', desc: '闯关塔（小镇内单按 A 为左移）' },
  { key: 'T', desc: '传送' },
  { key: 'C', desc: '休息' },
  { key: ',', desc: '设置' },
];

function onMenuClick(item: MenuItem): void {
  playUiClick();
  item.action();
}

function confirmQuit(): void {
  playUiClick();
  close();
  session.logout();
  void router.push('/');
}

function cancelQuit(): void {
  playUiClick();
  showQuitConfirm.value = false;
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape' && open.value) {
    e.preventDefault();
    e.stopPropagation();
    close();
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown, { capture: true });
});
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown, { capture: true });
});
</script>

<template>
  <transition name="pyt-scale">
    <div
      v-if="open"
      class="pause-mask"
      @click.self="close"
    >
      <div class="pause-panel pyt-panel">
        <h1 class="pause-title">
          PYTHONTOPIA
        </h1>
        <div class="pause-player">
          {{ session.displayName }} · 第{{ hud.meta.dayIndex }}天
        </div>

        <div class="pause-menu">
          <section
            v-for="section in menuSections"
            :key="section.id"
            class="pause-section"
          >
            <h2 class="pause-section__title">
              {{ section.title }}
            </h2>
            <div class="pause-section__items">
              <button
                v-for="item in section.items"
                :key="item.id"
                class="pause-item"
                :data-testid="`pause-${item.id}`"
                @click="onMenuClick(item)"
              >
                <img
                  :src="item.icon"
                  :alt="item.label"
                  class="pause-item__icon-img"
                  aria-hidden="true"
                >
                <span class="pause-item__label">{{ item.label }}</span>
              </button>
            </div>
          </section>
        </div>

        <!-- 快捷键速查卡 -->
        <transition name="pyt-fade">
          <div
            v-if="showHelp"
            class="pause-help pyt-panel--inset"
          >
            <h2 class="pause-help__title">
              快捷键速查
            </h2>
            <div class="pause-help__grid">
              <template
                v-for="hk in HOTKEYS"
                :key="hk.key"
              >
                <kbd class="pause-help__key">{{ hk.key }}</kbd>
                <span class="pause-help__desc">{{ hk.desc }}</span>
              </template>
            </div>
          </div>
        </transition>

        <!-- 退出确认对话框 -->
        <transition name="pyt-fade">
          <div
            v-if="showQuitConfirm"
            class="pause-confirm pyt-panel--inset"
          >
            <p class="pause-confirm__text">
              确定要返回主菜单吗？<br>未保存的进度将丢失。
            </p>
            <div class="pause-confirm__actions">
              <button
                class="pyt-btn pyt-btn--danger"
                @click="confirmQuit"
              >
                确认退出
              </button>
              <button
                class="pyt-btn"
                @click="cancelQuit"
              >
                取消
              </button>
            </div>
          </div>
        </transition>
      </div>
    </div>
  </transition>
</template>

<style scoped>
/* ── 遮罩层 ───────────────────────────────────────────────── */
.pause-mask {
  position: fixed;
  inset: 0;
  z-index: var(--pyt-z-modal);
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(8, 8, 12, 0.75);
  backdrop-filter: blur(4px);
}

/* ── 主面板 ───────────────────────────────────────────────── */
.pause-panel {
  position: relative;
  width: 340px;
  max-height: calc(100vh - 20px);
  max-height: calc(100dvh - 20px);
  padding: 18px 24px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  overflow-y: auto;
}

/* ── 标题 ─────────────────────────────────────────────────── */
.pause-title {
  margin: 0 0 2px;
  font-size: 22px;
  letter-spacing: 2px;
  color: var(--pyt-text-accent);
  text-shadow:
    0 0 8px rgba(212, 175, 55, 0.5),
    2px 2px 0 var(--pyt-panel-border-dark);
  font-family: var(--pyt-font-pixel);
  user-select: none;
}

.pause-player {
  font-size: 13px;
  color: var(--pyt-text-secondary);
  margin-bottom: 10px;
  letter-spacing: 0.5px;
}

/* ── 菜单按钮列表 ─────────────────────────────────────────── */
.pause-menu {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.pause-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.pause-section__title {
  width: 280px;
  margin: 0 auto;
  font-size: 11px;
  letter-spacing: 1px;
  text-align: left;
  color: var(--pyt-text-secondary);
  border-bottom: 1px solid var(--pyt-panel-border-dark);
  padding-bottom: 3px;
}

.pause-section__items {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pause-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 280px;
  margin: 0 auto;
  padding: 10px 18px;
  border: 2px solid var(--pyt-btn-border);
  border-radius: 2px;
  background: var(--pyt-btn-bg);
  color: var(--pyt-text-primary);
  font-family: var(--pyt-font-pixel);
  font-size: 15px;
  cursor: pointer;
  image-rendering: pixelated;
  box-shadow: 2px 2px 0 var(--pyt-panel-border-dark);
  transition:
    transform 100ms ease,
    background 120ms ease,
    box-shadow 100ms ease;
  user-select: none;
}

.pause-item:hover {
  transform: translateX(4px);
  background: var(--pyt-btn-hover-bg);
  box-shadow: 0 2px 0 var(--pyt-panel-border-dark);
}

.pause-item:active {
  transform: translateX(4px) translateY(2px);
  box-shadow: 0 0 0 var(--pyt-panel-border-dark);
  background: var(--pyt-btn-active-bg);
}

.pause-item__icon {
  font-size: 18px;
  width: 24px;
  text-align: center;
  flex-shrink: 0;
}
.pause-item__icon-img {
  width: 20px;
  height: 20px;
  object-fit: contain;
  image-rendering: pixelated;
  filter: invert(1) brightness(0.85) sepia(0.3) hue-rotate(15deg) saturate(1.5);
  flex-shrink: 0;
}

.pause-item__label {
  flex: 1;
}

/* ── 快捷键速查卡 ─────────────────────────────────────────── */
.pause-help {
  width: 280px;
  margin: 8px auto 0;
  padding: 14px 16px 12px;
  border: 2px solid var(--pyt-panel-border-dark);
  border-radius: 2px;
  background: rgba(0, 0, 0, 0.35);
}

.pause-help__title {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--pyt-text-accent);
  text-align: center;
  letter-spacing: 1px;
}

.pause-help__grid {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 14px;
  font-size: 12px;
}

.pause-help__key {
  color: var(--pyt-text-accent);
  font-family: var(--pyt-font-pixel);
  background: rgba(212, 175, 55, 0.1);
  border: 1px solid var(--pyt-btn-border);
  border-radius: 2px;
  padding: 1px 6px;
  font-size: 11px;
  white-space: nowrap;
}

.pause-help__desc {
  color: var(--pyt-text-secondary);
  line-height: 1.8;
}

/* ── 退出确认框 ───────────────────────────────────────────── */
.pause-confirm {
  width: 280px;
  margin: 10px auto 0;
  padding: 16px;
  border: 2px solid var(--pyt-danger);
  border-radius: 2px;
  background: rgba(230, 57, 70, 0.08);
  text-align: center;
}

.pause-confirm__text {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--pyt-text-primary);
  line-height: 1.6;
}

.pause-confirm__actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

/* ── 矮屏与动效偏好适配 ─────────────────────────────────────── */
@media (max-height: 500px) {
  .pause-panel {
    padding: 10px 18px 10px;
    gap: 4px;
  }
  .pause-title { font-size: 18px; }
  .pause-player { font-size: 11px; margin-bottom: 2px; }
  .pause-menu { gap: 6px; }
  .pause-section { gap: 3px; }
  .pause-section__title { width: 240px; font-size: 10px; padding-bottom: 1px; }
  .pause-item {
    width: 240px;
    padding: 5px 12px;
    font-size: 12px;
  }
  .pause-item__icon { font-size: 14px; width: 22px; }
}
@media (prefers-reduced-motion: reduce) {
  .pause-item {
    transition: none;
  }
}
</style>
