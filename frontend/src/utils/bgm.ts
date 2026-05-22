export type BgmKey = 'bgm_town_day' | 'bgm_town_eve' | 'bgm_bug_mine' | 'bgm_quest';

const BGM_URL: Record<BgmKey, string> = {
  bgm_town_day: '/assets/pixel/bgm/track_01.mp3',
  bgm_town_eve: '/assets/pixel/bgm/track_02.ogg',
  bgm_bug_mine: '/assets/pixel/bgm/track_03.ogg',
  bgm_quest: '/assets/pixel/bgm/track_04.mp3',
};

const DEFAULT_VOLUME = 0.35;
const DEFAULT_FADE_MS = 800;

let currentKey: BgmKey | null = null;
let currentAudio: HTMLAudioElement | null = null;
let targetVolume = DEFAULT_VOLUME;
let enabled =
  typeof window !== 'undefined' && window.localStorage?.getItem('pyt:bgm') !== 'off';

function persistEnabled(): void {
  if (typeof window !== 'undefined') {
    window.localStorage?.setItem('pyt:bgm', enabled ? 'on' : 'off');
  }
}

function fadeAudio(audio: HTMLAudioElement, from: number, to: number, ms: number): void {
  const steps = Math.max(8, Math.round(ms / 40));
  const dv = (to - from) / steps;
  let i = 0;
  audio.volume = Math.max(0, Math.min(1, from));
  const handle = setInterval(() => {
    i += 1;
    audio.volume = Math.max(0, Math.min(1, from + dv * i));
    if (i >= steps) {
      clearInterval(handle);
      audio.volume = Math.max(0, Math.min(1, to));
      if (to <= 0) {
        try {
          audio.pause();
        } catch (e) {
          void e;
        }
      }
    }
  }, ms / steps);
}

export function setBgmEnabled(value: boolean): void {
  enabled = value;
  persistEnabled();
  if (!value && currentAudio) {
    stopBgm(0);
  }
}

export function isBgmEnabled(): boolean {
  return enabled;
}

export function setBgmVolume(value: number): void {
  targetVolume = Math.max(0, Math.min(1, value));
  if (currentAudio) {
    currentAudio.volume = targetVolume;
  }
}

export function getCurrentBgm(): BgmKey | null {
  return currentKey;
}

export function playBgm(key: BgmKey, fadeMs: number = DEFAULT_FADE_MS): void {
  if (!enabled) {
    return;
  }
  if (typeof Audio === 'undefined') {
    return;
  }
  if (currentKey === key && currentAudio && !currentAudio.paused) {
    return;
  }
  const next = new Audio(BGM_URL[key]);
  next.loop = true;
  next.preload = 'auto';
  next.volume = 0;
  const playPromise = next.play();
  if (playPromise && typeof playPromise.catch === 'function') {
    playPromise.catch(() => undefined);
  }
  fadeAudio(next, 0, targetVolume, fadeMs);
  const old = currentAudio;
  if (old) {
    fadeAudio(old, old.volume, 0, fadeMs);
  }
  currentAudio = next;
  currentKey = key;
}

export function stopBgm(fadeMs: number = DEFAULT_FADE_MS): void {
  const old = currentAudio;
  if (!old) {
    return;
  }
  if (fadeMs <= 0) {
    try {
      old.pause();
    } catch (e) {
      void e;
    }
  } else {
    fadeAudio(old, old.volume, 0, fadeMs);
  }
  currentAudio = null;
  currentKey = null;
}
