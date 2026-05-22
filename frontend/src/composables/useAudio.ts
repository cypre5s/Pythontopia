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
} {
  return {
    playBgm,
    stopBgm,
    playSfx,
    setMuted,
    isMuted: () => muted,
    getCurrentBgmKey: () => currentBgmKey,
    unlockAudio,
  };
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

  const targetVolume = options.volume ?? DEFAULT_BGM_VOLUME;
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
  game.sound.play(key, { volume: options.volume ?? DEFAULT_SFX_VOLUME });
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
