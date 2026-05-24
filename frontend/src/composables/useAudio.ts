// 隔离原因：作为业务层与 Phaser sound manager 之间的唯一音频适配层，
// 仅本文件允许直接 import phaser；业务代码统一通过本文件导出的 playBgm/playSfx 调用。
import type Phaser from 'phaser';
import { getPhaserGame } from '@game/adapters/phaserGame';

export type BgmKey = 'bgm_town_day' | 'bgm_town_eve' | 'bgm_bug_mine' | 'bgm_quest';
export type SfxKey =
  | 'sfx_click'
  | 'sfx_hover'
  | 'sfx_coin'
  | 'sfx_success'
  | 'sfx_error'
  | 'sfx_jingle_complete'
  | 'sfx_jingle_levelup';

export interface PlayBgmOptions {
  volume?: number;
  crossfadeMs?: number;
}

export interface PlaySfxOptions {
  volume?: number;
}

const DEFAULT_BGM_VOLUME = 0.32;
const DEFAULT_SFX_VOLUME = 0.55;
const DEFAULT_CROSSFADE_MS = 600;

let currentBgmKey: BgmKey | null = null;
let currentBgm: Phaser.Sound.BaseSound | null = null;
let muted = false;
// R-13 迭代 11：全局音量倍率，0..1，与 SettingsModal slider 联动
let bgmVolumeMul = 1;
let sfxVolumeMul = 1;

// W4-BGM：浏览器 autoplay 策略要求音频必须由用户交互触发；
// 我们在 audioUnlocked=false 期间把 playBgm 入参缓存到 pendingBgm，
// 等到 unlockAudio() 被调用（用户点击/按键）再补放，sfx 无需排队直接静默跳过。
let audioUnlocked = false;
let pendingBgm: BgmKey | null = null;
let pendingBgmOptions: PlayBgmOptions | undefined;

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
 * R-13 迭代 11：设置 BGM 音量倍率（0..1）。
 * 立即影响当前正在播放的 BGM；新启动的 BGM 也会按此倍率。
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
 * R-13 迭代 11：设置 SFX 音量倍率（0..1）。
 * 仅影响下次 playSfx 调用，已经发出的 SFX 不被回拨。
 */
export function setSfxVolume(value: number): void {
  sfxVolumeMul = Math.max(0, Math.min(1, value));
}

export function playBgm(key: BgmKey, options: PlayBgmOptions = {}): void {
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
  const game = getPhaserGame();
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
}

export function stopBgm(): void {
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
  const game = getPhaserGame();
  if (!game) {
    return;
  }
  const base = options.volume ?? DEFAULT_SFX_VOLUME;
  game.sound.play(key, { volume: base * sfxVolumeMul });
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
  const game = getPhaserGame();
  if (game) {
    game.sound.mute = value;
  }
  if (value && currentBgm) {
    currentBgm.pause();
  } else if (!value && currentBgm) {
    currentBgm.resume();
  }
}
