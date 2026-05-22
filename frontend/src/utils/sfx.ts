const SFX_PATH = {
  click: '/assets/pixel/sfx/click/click.ogg',
  hover: '/assets/pixel/sfx/hover/hover.ogg',
  coin: '/assets/pixel/sfx/coin/coin.ogg',
  success: '/assets/pixel/sfx/success.ogg',
  error: '/assets/pixel/sfx/error.ogg',
  jingleComplete: '/assets/pixel/sfx/jingle-complete.ogg',
  jingleLevelUp: '/assets/pixel/sfx/jingle-level-up.ogg',
} as const;

export type SfxKey = keyof typeof SFX_PATH;

const cache = new Map<SfxKey, HTMLAudioElement>();

let enabled = typeof window !== 'undefined' && window.localStorage?.getItem('pyt:sfx') !== 'off';

export function setSfxEnabled(value: boolean): void {
  enabled = value;
  if (typeof window !== 'undefined') {
    window.localStorage?.setItem('pyt:sfx', value ? 'on' : 'off');
  }
}

export function isSfxEnabled(): boolean {
  return enabled;
}

function getAudio(key: SfxKey): HTMLAudioElement | null {
  if (typeof Audio === 'undefined') {
    return null;
  }
  const cached = cache.get(key);
  if (cached) {
    return cached;
  }
  const audio = new Audio(SFX_PATH[key]);
  audio.preload = 'auto';
  audio.volume = 0.4;
  cache.set(key, audio);
  return audio;
}

export function playSfx(key: SfxKey): void {
  if (!enabled) {
    return;
  }
  const audio = getAudio(key);
  if (!audio) {
    return;
  }
  try {
    audio.currentTime = 0;
    void audio.play().catch(() => undefined);
  } catch (e) {
    void e;
  }
}

export function preloadAllSfx(): void {
  if (!enabled) {
    return;
  }
  for (const key of Object.keys(SFX_PATH) as SfxKey[]) {
    getAudio(key);
  }
}
