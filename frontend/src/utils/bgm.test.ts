import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  getCurrentBgm,
  isBgmEnabled,
  playBgm,
  setBgmEnabled,
  setBgmVolume,
  stopBgm,
} from './bgm';

class MockAudio {
  src: string;
  volume = 0;
  loop = false;
  preload = '';
  paused = true;
  played: boolean = false;
  constructor(src: string) {
    this.src = src;
  }
  play(): Promise<void> {
    this.paused = false;
    this.played = true;
    return Promise.resolve();
  }
  pause(): void {
    this.paused = true;
  }
}

describe('bgm engine', () => {
  beforeEach(() => {
    vi.stubGlobal('Audio', MockAudio as unknown);
    vi.useFakeTimers();
    setBgmEnabled(true);
    stopBgm(0);
  });

  it('默认启用', () => {
    expect(isBgmEnabled()).toBe(true);
  });

  it('toggle disabled 后 playBgm 不报错且不切歌', () => {
    setBgmEnabled(false);
    expect(() => playBgm('bgm_town_day')).not.toThrow();
    expect(getCurrentBgm()).toBeNull();
  });

  it('playBgm 设置 currentKey', () => {
    playBgm('bgm_town_day', 0);
    expect(getCurrentBgm()).toBe('bgm_town_day');
  });

  it('同 key 重复 play 不重启', () => {
    playBgm('bgm_town_day', 0);
    const first = getCurrentBgm();
    playBgm('bgm_town_day', 0);
    expect(getCurrentBgm()).toBe(first);
  });

  it('不同 key 切歌后 currentKey 改变', () => {
    playBgm('bgm_town_day', 0);
    playBgm('bgm_bug_mine', 0);
    expect(getCurrentBgm()).toBe('bgm_bug_mine');
  });

  it('stopBgm 清空 currentKey', () => {
    playBgm('bgm_town_day', 0);
    stopBgm(0);
    expect(getCurrentBgm()).toBeNull();
  });

  it('setBgmVolume 不抛错', () => {
    expect(() => setBgmVolume(0.5)).not.toThrow();
    expect(() => setBgmVolume(2)).not.toThrow();
    expect(() => setBgmVolume(-1)).not.toThrow();
  });

  it('setBgmEnabled persists to localStorage', () => {
    setBgmEnabled(false);
    expect(window.localStorage.getItem('pyt:bgm')).toBe('off');
    setBgmEnabled(true);
    expect(window.localStorage.getItem('pyt:bgm')).toBe('on');
  });
});
