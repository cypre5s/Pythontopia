// 隔离原因：作为业务层与 Phaser sound manager 之间的唯一音频适配层，
// 仅本文件允许直接 import phaser；业务代码统一通过本文件导出的 playBgm/playSfx 调用。
import type Phaser from 'phaser';

export type BgmKey = 'bgm_town_day' | 'bgm_town_eve' | 'bgm_bug_mine' | 'bgm_quest';
export type SfxKey =
  | 'sfx_click'
  | 'sfx_hover'
  | 'sfx_coin'
  | 'sfx_success'
  | 'sfx_error'
  | 'sfx_jump'
  | 'sfx_land'
  | 'sfx_boss_impact'
  | 'sfx_jingle_complete'
  | 'sfx_jingle_levelup';

export interface PlayBgmOptions {
  volume?: number;
  crossfadeMs?: number;
}

export interface PlaySfxOptions {
  volume?: number;
  /** 音高微调（音分 cents）：脚步/撞击等重复音随机化，避免机械重复（game feel 经验）。 */
  detune?: number;
}

const DEFAULT_BGM_VOLUME = 0.32;
const DEFAULT_SFX_VOLUME = 0.55;
const DEFAULT_CROSSFADE_MS = 600;

let currentBgmKey: BgmKey | null = null;
let currentBgm: Phaser.Sound.BaseSound | null = null;
let muted = false;
// 全局音量倍率，0..1，与 SettingsModal slider 联动
let bgmVolumeMul = 1;
let sfxVolumeMul = 1;

// 浏览器 autoplay 策略要求音频必须由用户交互触发：
// audioUnlocked=false 期间把 playBgm 入参缓存到 pendingBgm，
// 等 unlockAudio()（用户点击/按键）再补放；sfx 无需排队，直接静默跳过。
let audioUnlocked = false;
let pendingBgm: BgmKey | null = null;
let pendingBgmOptions: PlayBgmOptions | undefined;
let bgmRequestId = 0;

// 避免仅为登录页音频解锁就预载整个 Phaser 引擎。
function withPhaserGame(action: (game: Phaser.Game | null) => void): void {
  void import('@game/adapters/phaserGame').then(
    ({ getPhaserGame }) => action(getPhaserGame()),
    () => undefined,
  );
}

export function useAudio(): {
  playBgm: typeof playBgm;
  stopBgm: typeof stopBgm;
  playSfx: typeof playSfx;
  setMuted: typeof setMuted;
  isMuted: () => boolean;
  getCurrentBgmKey: () => BgmKey | null;
  unlockAudio: typeof unlockAudio;
  setBgmVolume: typeof setBgmVolume;
  setSfxVolume: typeof setSfxVolume;
  getBgmVolume: () => number;
  getSfxVolume: () => number;
} {
  return {
    playBgm,
    stopBgm,
    playSfx,
    setMuted,
    isMuted: () => muted,
    getCurrentBgmKey: () => currentBgmKey,
    unlockAudio,
    setBgmVolume,
    setSfxVolume,
    getBgmVolume: () => bgmVolumeMul,
    getSfxVolume: () => sfxVolumeMul,
  };
}

/**
 * 设置 BGM 音量倍率（0..1）。立即影响当前正在播放的 BGM，新启动的 BGM 也按此倍率。
 */
export function setBgmVolume(value: number): void {
  bgmVolumeMul = Math.max(0, Math.min(1, value));
  if (currentBgm) {
    const sound = currentBgm as unknown as { setVolume?: (v: number) => void; volume?: number };
    const targetVolume = DEFAULT_BGM_VOLUME * bgmVolumeMul;
    if (typeof sound.setVolume === 'function') {
      sound.setVolume(targetVolume);
    } else if (typeof sound.volume === 'number') {
      sound.volume = targetVolume;
    }
  }
}

/**
 * 设置 SFX 音量倍率（0..1）。仅影响下次 playSfx 调用，已发出的 SFX 不回拨。
 */
export function setSfxVolume(value: number): void {
  sfxVolumeMul = Math.max(0, Math.min(1, value));
}

export function playBgm(key: BgmKey, options: PlayBgmOptions = {}): void {
  const requestId = ++bgmRequestId;
  if (muted) {
    currentBgmKey = key;
    return;
  }
  if (!audioUnlocked) {
    pendingBgm = key;
    pendingBgmOptions = options;
    currentBgmKey = key;
    return;
  }
  withPhaserGame((game) => {
    if (requestId !== bgmRequestId || muted) return;
    if (!game) {
      currentBgmKey = key;
      return;
    }
    if (currentBgmKey === key && currentBgm && currentBgm.isPlaying) {
      return;
    }

    const next = game.sound.add(key, { loop: true, volume: 0 });
    next.play();

    const targetVolume = (options.volume ?? DEFAULT_BGM_VOLUME) * bgmVolumeMul;
    const duration = options.crossfadeMs ?? DEFAULT_CROSSFADE_MS;
    const scene = game.scene.scenes[0];

    if (currentBgm) {
      const prev = currentBgm;
      if (scene) {
        scene.tweens.add({
          targets: prev,
          volume: 0,
          duration,
          onComplete: () => prev.stop(),
        });
      } else {
        prev.stop();
      }
    }

    if (scene) {
      scene.tweens.add({
        targets: next,
        volume: targetVolume,
        duration,
      });
    } else {
      // 没场景时无法做 tween，降级为直接设音量
      const withVolume = next as unknown as { setVolume?: (v: number) => void; volume?: number };
      if (typeof withVolume.setVolume === 'function') {
        withVolume.setVolume(targetVolume);
      } else if (typeof withVolume.volume === 'number') {
        withVolume.volume = targetVolume;
      }
    }

    currentBgm = next;
    currentBgmKey = key;
  });
}

export function stopBgm(): void {
  bgmRequestId += 1;
  pendingBgm = null;
  pendingBgmOptions = undefined;
  if (currentBgm) {
    currentBgm.stop();
    currentBgm = null;
    currentBgmKey = null;
  }
}

export function playSfx(key: SfxKey, options: PlaySfxOptions = {}): void {
  if (muted) {
    return;
  }
  if (!audioUnlocked) {
    return;
  }
  const base = options.volume ?? DEFAULT_SFX_VOLUME;
  const config: { volume: number; detune?: number } = { volume: base * sfxVolumeMul };
  if (typeof options.detune === 'number') {
    config.detune = options.detune;
  }
  withPhaserGame((game) => {
    if (!muted) game?.sound.play(key, config);
  });
}

export function unlockAudio(): void {
  if (audioUnlocked) {
    return;
  }
  audioUnlocked = true;
  if (pendingBgm) {
    const key = pendingBgm;
    const opts = pendingBgmOptions;
    pendingBgm = null;
    pendingBgmOptions = undefined;
    currentBgmKey = null;
    playBgm(key, opts);
  }
}

if (typeof window !== 'undefined') {
  const unlockOnUserGesture = (): void => {
    unlockAudio();
    window.removeEventListener('click', unlockOnUserGesture);
    window.removeEventListener('keydown', unlockOnUserGesture);
    window.removeEventListener('touchstart', unlockOnUserGesture);
  };
  window.addEventListener('click', unlockOnUserGesture, { once: true });
  window.addEventListener('keydown', unlockOnUserGesture, { once: true });
  window.addEventListener('touchstart', unlockOnUserGesture, { once: true });
}

export function setMuted(value: boolean): void {
  muted = value;
  if (value) bgmRequestId += 1;
  withPhaserGame((game) => {
    if (game) game.sound.mute = value;
  });
  if (value && currentBgm) {
    currentBgm.pause();
  } else if (!value && currentBgm) {
    currentBgm.resume();
  }
}
