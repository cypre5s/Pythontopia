import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useMoodStore } from '@/stores/mood';
import { fetchWorldState } from '@/api/learner';

vi.mock('@/api/learner', () => ({
  fetchWorldState: vi.fn(),
}));

const MOOD_EVENT = 'pythontopia:mood-changed';

function captureMoodEvents(): { moods: string[]; stop: () => void } {
  const moods: string[] = [];
  const handler = (event: Event): void => {
    moods.push((event as CustomEvent<{ mood: string }>).detail.mood);
  };
  window.addEventListener(MOOD_EVENT, handler);
  return { moods, stop: () => window.removeEventListener(MOOD_EVENT, handler) };
}

describe('mood store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.clear();
    vi.mocked(fetchWorldState).mockReset();
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  it('refresh() 写入 state 并在启用时派发 mood-changed 事件', async () => {
    vi.mocked(fetchWorldState).mockResolvedValue({ mood: 'rainy', streak: -3, emotionScore: 0.2 });
    const events = captureMoodEvents();

    const store = useMoodStore();
    await store.refresh();

    expect(fetchWorldState).toHaveBeenCalledTimes(1);
    expect(store.mood).toBe('rainy');
    expect(store.streak).toBe(-3);
    expect(store.emotionScore).toBeCloseTo(0.2);
    expect(store.loading).toBe(false);
    expect(events.moods).toEqual(['rainy']);
    events.stop();
  });

  it('refresh() 失败时降级到 sunny 且不抛错、不派发', async () => {
    vi.mocked(fetchWorldState).mockRejectedValue(new Error('boom'));
    const events = captureMoodEvents();

    const store = useMoodStore();
    store.mood = 'rainy';
    await expect(store.refresh()).resolves.toBeUndefined();

    expect(store.mood).toBe('sunny');
    expect(store.loading).toBe(false);
    expect(events.moods).toEqual([]);
    events.stop();
  });

  it('已关闭氛围（pyt_mood_disabled）时 refresh 仍更新 state 但不派发事件', async () => {
    window.localStorage.setItem('pyt_mood_disabled', '1');
    vi.mocked(fetchWorldState).mockResolvedValue({ mood: 'rainy', streak: -2, emotionScore: 0.1 });
    const events = captureMoodEvents();

    const store = useMoodStore();
    expect(store.disabled).toBe(true);

    await store.refresh();

    expect(store.mood).toBe('rainy');
    expect(events.moods).toEqual([]);
    events.stop();
  });

  it('setDisabled(true) 持久化并派发 sunny 清场事件', () => {
    const events = captureMoodEvents();

    const store = useMoodStore();
    store.mood = 'rainy';
    store.setDisabled(true);

    expect(window.localStorage.getItem('pyt_mood_disabled')).toBe('1');
    expect(store.disabled).toBe(true);
    expect(events.moods).toEqual(['sunny']);
    events.stop();
  });

  it('setDisabled(false) 清除持久化并按当前 mood 恢复派发', () => {
    window.localStorage.setItem('pyt_mood_disabled', '1');
    const events = captureMoodEvents();

    const store = useMoodStore();
    store.mood = 'cloudy';
    store.setDisabled(false);

    expect(window.localStorage.getItem('pyt_mood_disabled')).toBeNull();
    expect(store.disabled).toBe(false);
    expect(events.moods).toEqual(['cloudy']);
    events.stop();
  });
});
