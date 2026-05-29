import { defineStore } from 'pinia';

/** 游戏设置 store，持久化到 localStorage('pyt_game_settings')。 */

export type TextSpeed = 'slow' | 'normal' | 'fast' | 'instant';
export type DifficultyPref = 'easy' | 'normal' | 'hard';
export type HintFrequency = 'never' | 'sometimes' | 'always';
export type ColorMode = 'default' | 'high-contrast' | 'colorblind';
export type UIScale = 'small' | 'medium' | 'large';

export interface AudioSettings {
  muted: boolean;
  bgmVolume: number;
  sfxVolume: number;
  ambientVolume: number;
  uiSfxVolume: number;
}

export interface DisplaySettings {
  fullscreen: boolean;
  pixelPerfect: boolean;
  uiScale: UIScale;
  colorMode: ColorMode;
  showVignette: boolean;
  showParticles: boolean;
}

export interface EducationSettings {
  textSpeed: TextSpeed;
  autoAdvanceDialog: boolean;
  autoAdvanceDelayMs: number;
  codeFontSize: number;
  difficulty: DifficultyPref;
  hintFrequency: HintFrequency;
  focusMode: boolean;
  pomodoroEnabled: boolean;
  pomodoroWorkMin: number;
  pomodoroBreakMin: number;
  studyReminder: boolean;
  studyReminderIntervalMin: number;
}

export interface ControlSettings {
  /** 未来可扩展为自定义键位映射 */
  touchSensitivity: number;
}

export interface NotificationSettings {
  achievementPopup: boolean;
  questComplete: boolean;
  npcReminder: boolean;
  progressShareWithTeacher: boolean;
  progressShareWithClassmates: boolean;
}

export interface GameSettingsState {
  audio: AudioSettings;
  display: DisplaySettings;
  education: EducationSettings;
  controls: ControlSettings;
  notifications: NotificationSettings;
  /** ESC 暂停菜单是否打开 */
  pauseMenuOpen: boolean;
  /** Tab 快捷轮盘是否打开 */
  quickWheelOpen: boolean;
}

const STORAGE_KEY = 'pyt_game_settings';

function defaults(): GameSettingsState {
  return {
    audio: {
      muted: false,
      bgmVolume: 70,
      sfxVolume: 80,
      ambientVolume: 50,
      uiSfxVolume: 70,
    },
    display: {
      fullscreen: false,
      pixelPerfect: true,
      uiScale: 'medium',
      colorMode: 'default',
      showVignette: true,
      showParticles: true,
    },
    education: {
      textSpeed: 'normal',
      autoAdvanceDialog: false,
      autoAdvanceDelayMs: 3000,
      codeFontSize: 14,
      difficulty: 'normal',
      hintFrequency: 'sometimes',
      focusMode: false,
      pomodoroEnabled: false,
      pomodoroWorkMin: 25,
      pomodoroBreakMin: 5,
      studyReminder: true,
      studyReminderIntervalMin: 60,
    },
    controls: {
      touchSensitivity: 50,
    },
    notifications: {
      achievementPopup: true,
      questComplete: true,
      npcReminder: true,
      progressShareWithTeacher: true,
      progressShareWithClassmates: false,
    },
    pauseMenuOpen: false,
    quickWheelOpen: false,
  };
}

function loadFromStorage(): Partial<GameSettingsState> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    return JSON.parse(raw) as Partial<GameSettingsState>;
  } catch {
    return {};
  }
}

function deepMerge(base: GameSettingsState, patch: Partial<GameSettingsState>): GameSettingsState {
  const result = { ...base } as Record<string, unknown>;
  for (const key of Object.keys(patch)) {
    const pv = (patch as Record<string, unknown>)[key];
    const bv = result[key];
    if (pv != null && typeof pv === 'object' && !Array.isArray(pv) && typeof bv === 'object' && bv != null && !Array.isArray(bv)) {
      result[key] = { ...(bv as Record<string, unknown>), ...(pv as Record<string, unknown>) };
    } else if (pv !== undefined) {
      result[key] = pv;
    }
  }
  return result as unknown as GameSettingsState;
}

export const useGameSettingsStore = defineStore('gameSettings', {
  state: (): GameSettingsState => deepMerge(defaults(), loadFromStorage()),

  actions: {
    persist(): void {
      try {
        const { pauseMenuOpen, quickWheelOpen, ...rest } = this.$state;
        void pauseMenuOpen;
        void quickWheelOpen;
        localStorage.setItem(STORAGE_KEY, JSON.stringify(rest));
      } catch { /* privacy mode */ }
    },

    updateAudio(patch: Partial<AudioSettings>): void {
      this.audio = { ...this.audio, ...patch };
      this.persist();
    },

    updateDisplay(patch: Partial<DisplaySettings>): void {
      this.display = { ...this.display, ...patch };
      this.persist();
    },

    updateEducation(patch: Partial<EducationSettings>): void {
      this.education = { ...this.education, ...patch };
      this.persist();
    },

    updateControls(patch: Partial<ControlSettings>): void {
      this.controls = { ...this.controls, ...patch };
      this.persist();
    },

    updateNotifications(patch: Partial<NotificationSettings>): void {
      this.notifications = { ...this.notifications, ...patch };
      this.persist();
    },

    openPauseMenu(): void {
      this.pauseMenuOpen = true;
      this.quickWheelOpen = false;
    },
    closePauseMenu(): void {
      this.pauseMenuOpen = false;
    },
    togglePauseMenu(): void {
      if (this.pauseMenuOpen) this.closePauseMenu();
      else this.openPauseMenu();
    },

    openQuickWheel(): void {
      this.quickWheelOpen = true;
    },
    closeQuickWheel(): void {
      this.quickWheelOpen = false;
    },

    resetAll(): void {
      const fresh = defaults();
      this.$patch(fresh);
      this.persist();
    },
  },
});
