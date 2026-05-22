import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { celebrateMature } from './celebrate';

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

describe('celebrateMature', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    vi.stubGlobal('Audio', MockAudio as unknown);
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
  });

  it('调用不抛错（target 为 null 时挂载到 body）', () => {
    expect(() => celebrateMature(null)).not.toThrow();
  });

  it('调用后 DOM 出现 .pyt-celebrate 覆盖层', () => {
    celebrateMature(null);
    const overlays = document.querySelectorAll('.pyt-celebrate');
    expect(overlays.length).toBe(1);
    const overlay = overlays[0]!;
    expect(overlay.querySelector('.pyt-celebrate__burst')).not.toBeNull();
    expect(overlay.querySelectorAll('.pyt-celebrate__piece').length).toBeGreaterThan(0);
  });

  it('1.2 秒后覆盖层自动移除', () => {
    celebrateMature(null);
    expect(document.querySelectorAll('.pyt-celebrate').length).toBe(1);
    vi.advanceTimersByTime(1199);
    expect(document.querySelectorAll('.pyt-celebrate').length).toBe(1);
    vi.advanceTimersByTime(2);
    expect(document.querySelectorAll('.pyt-celebrate').length).toBe(0);
  });

  it('指定 target 时覆盖层挂载到该元素下', () => {
    const host = document.createElement('section');
    document.body.appendChild(host);
    celebrateMature(host);
    expect(host.querySelectorAll('.pyt-celebrate').length).toBe(1);
    expect(document.body.children).toContain(host);
  });
});
