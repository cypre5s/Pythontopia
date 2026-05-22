import { describe, it, expect, vi, beforeEach } from 'vitest';
import { isSfxEnabled, playSfx, preloadAllSfx, setSfxEnabled } from './sfx';

class MockAudio {
  src: string;
  volume = 1;
  currentTime = 0;
  preload = '';
  constructor(src: string) {
    this.src = src;
  }
  play(): Promise<void> {
    return Promise.resolve();
  }
}

describe('sfx', () => {
  beforeEach(() => {
    vi.stubGlobal('Audio', MockAudio as unknown);
    setSfxEnabled(true);
  });

  it('默认启用', () => {
    expect(isSfxEnabled()).toBe(true);
  });

  it('toggle 后 isSfxEnabled 同步', () => {
    setSfxEnabled(false);
    expect(isSfxEnabled()).toBe(false);
    setSfxEnabled(true);
    expect(isSfxEnabled()).toBe(true);
  });

  it('playSfx disabled 时不抛错', () => {
    setSfxEnabled(false);
    expect(() => playSfx('click')).not.toThrow();
  });

  it('preloadAllSfx disabled 时直接 return', () => {
    setSfxEnabled(false);
    expect(() => preloadAllSfx()).not.toThrow();
  });

  it('playSfx 所有 key 不抛错', () => {
    for (const key of ['click', 'hover', 'coin', 'success', 'error', 'jingleComplete', 'jingleLevelUp'] as const) {
      expect(() => playSfx(key)).not.toThrow();
    }
  });
});
