import { defineStore } from 'pinia';

/**
 * Pythontopia HUD 跨场景桥接 store。
 *
 * 定位：
 *   - HudShell.vue 渲染顶部状态条 + 底部快捷栏，所有数据从本 store 读取；
 *   - 业务层（WorldScene / WorldPage / TownScene 等）只调用 actions，不直接改 state；
 *   - 与 world / calendar / inventory / learning 等业务 store 解耦，避免循环依赖：
 *     上层场景在每帧/事件回调里把需要的标量 patch 进 `meta`，HUD 单向读取。
 *
 * 设计要点：
 *   1. `activeSlot` 互斥单选：同一时刻最多一个面板高亮，符合像素小镇 UI 一次只看一个面板的直觉。
 *   2. `topBar` / `bottomBar` 三态可见性：`shown` 完整显示、`collapsed` 仅留窄条占位、`hidden` 完全收起。
 *      战斗 / 剧情 / 矿洞等强沉浸场景可以 `hidden`，回到地图 `shown`。
 *   3. `meta` 只做扁平标量缓存，不存 DTO；避免引用类型把 store 变成业务数据快照。
 *   4. 不引入任何后端 API；本 store 纯前端桥接，不参与 W3-CTRL bootstrap 链路。
 */
export type HudVisibility = 'shown' | 'hidden' | 'collapsed';
export type HudQuickSlot = 'quest' | 'dialog' | 'inventory' | 'map' | 'skill';
export type HudBarPart = 'top' | 'bottom';

export interface HudMeta {
  /** 与后端 WorldService.weather 对齐：'SUNNY' | 'RAINY' | 'CLOUDY' | 'SNOWY' | ... */
  weather: string;
  dayIndex: number;
  /** 'MORNING' | 'NOON' | 'AFTERNOON' | 'EVENING' | 'NIGHT' | 'DAWN' */
  timeOfDay: string;
  /** 'SPRING' | 'SUMMER' | 'AUTUMN' | 'WINTER'（注意：大写常量，与画面图标 key 对齐） */
  season: string;
  /** 玩家精力 0-100 */
  energy: number;
  /** 玩家金币 */
  coin: number;
  /** KC 平均掌握度 0-100 */
  masteryAvg: number;
}

export interface HudState {
  topBar: HudVisibility;
  bottomBar: HudVisibility;
  /** 当前底部快捷栏激活的面板 (互斥，null 表示全部关闭) */
  activeSlot: HudQuickSlot | null;
  /** 顶部状态条要显示的元数据 */
  meta: HudMeta;
  /**
   * R-13：设置弹窗开关。与 5 个 quickslot 互斥单选语义不同 —— 设置是「独立模态」，
   * 不抢占 activeSlot，避免关掉地图后还要再点一次设置。
   * 默认 false；reset() 拨回 false。
   */
  settingsOpen: boolean;
}

function defaultMeta(): HudMeta {
  return {
    weather: 'SUNNY',
    dayIndex: 1,
    timeOfDay: 'MORNING',
    season: 'SPRING',
    energy: 100,
    coin: 0,
    masteryAvg: 0,
  };
}

const BGM_LABELS: Record<string, string> = {
  bgm_town_day: '小镇·白日',
  bgm_town_eve: '小镇·黄昏',
  bgm_bug_mine: 'Bug 矿洞',
  bgm_quest: '校园钟声',
};

/** R-13 迭代 14：把 BGM key 翻成人类可读名（HUD 显示用） */
export function bgmLabel(key: string | null): string {
  if (!key) return '静默';
  return BGM_LABELS[key] ?? key;
}

function defaultState(): HudState {
  return {
    topBar: 'shown',
    bottomBar: 'shown',
    activeSlot: null,
    meta: defaultMeta(),
    settingsOpen: false,
  };
}

export const useHudStore = defineStore('hud', {
  state: (): HudState => defaultState(),
  getters: {
    isQuestOpen: (state): boolean => state.activeSlot === 'quest',
    isDialogOpen: (state): boolean => state.activeSlot === 'dialog',
    isInventoryOpen: (state): boolean => state.activeSlot === 'inventory',
    isMapOpen: (state): boolean => state.activeSlot === 'map',
    isSkillOpen: (state): boolean => state.activeSlot === 'skill',
    /**
     * 底部快捷栏可交互判定：仅当 bottomBar !== 'hidden' 时按钮才允许点击。
     * UI 层据此决定 disabled 属性 / 是否渲染。
     */
    canToggleSlot: (state): boolean => state.bottomBar !== 'hidden',
  },
  actions: {
    /**
     * 切换底部快捷栏激活 slot。
     *   - 当前 slot === 目标 slot → 关闭（设为 null）；
     *   - 否则切换到目标 slot；
     *   - 若 bottomBar 处于 hidden（canToggleSlot === false）则忽略调用，
     *     避免在被隐藏的 HUD 上由于程序化代码无意中弹出面板。
     */
    toggleSlot(slot: HudQuickSlot): void {
      if (this.bottomBar === 'hidden') {
        return;
      }
      this.activeSlot = this.activeSlot === slot ? null : slot;
    },
    closeAll(): void {
      this.activeSlot = null;
    },
    setVisibility(part: HudBarPart, vis: HudVisibility): void {
      if (part === 'top') {
        this.topBar = vis;
      } else {
        this.bottomBar = vis;
      }
    },
    /**
     * 局部 patch meta，未传字段保持原值，便于上层 store 按需推送。
     * 示例：world store 推进 dayIndex 时只需 `syncMeta({ dayIndex: n })`，无需重写整个 meta。
     */
    syncMeta(patch: Partial<HudMeta>): void {
      this.meta = { ...this.meta, ...patch };
    },
    reset(): void {
      const fresh = defaultState();
      this.topBar = fresh.topBar;
      this.bottomBar = fresh.bottomBar;
      this.activeSlot = fresh.activeSlot;
      this.meta = fresh.meta;
      this.settingsOpen = fresh.settingsOpen;
    },
    /** R-13：打开设置弹窗（独立于 5 个 quickslot 互斥语义） */
    openSettings(): void {
      this.settingsOpen = true;
    },
    /** R-13：关闭设置弹窗 */
    closeSettings(): void {
      this.settingsOpen = false;
    },
    /** R-13：切换设置弹窗开关 */
    toggleSettings(): void {
      this.settingsOpen = !this.settingsOpen;
    },
  },
});
