import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useProbeStore } from '@/stores/probe';
import { submitProbe } from '@/api/learner';

vi.mock('@/api/learner', () => ({
  submitProbe: vi.fn(async () => undefined),
}));

/** 探针持久化键按登录用户分命名空间；未登录落到 `anon`。 */
const ANON_PERSIST_KEY = 'pyt_probe_answered@u:anon';

function persistedAnswered(key: string = ANON_PERSIST_KEY): string[] {
  const raw = window.localStorage.getItem(key);
  return raw ? (JSON.parse(raw) as string[]) : [];
}

function loginAs(username: string): void {
  window.localStorage.setItem(
    'pythontopia.user',
    JSON.stringify({ id: 1, username, displayName: username, role: 'STUDENT' }),
  );
}

describe('probe store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.clear();
    vi.mocked(submitProbe).mockReset();
    vi.mocked(submitProbe).mockResolvedValue(undefined);
  });

  it('shouldShow is true for an unseen probe, false once shown in-session', () => {
    const store = useProbeStore();
    expect(store.shouldShow('cog_intro')).toBe(true);
    store.markShown('cog_intro');
    expect(store.shouldShow('cog_intro')).toBe(false);
  });

  it('markAnswered persists to localStorage and permanently blocks re-show', () => {
    const store = useProbeStore();
    store.markAnswered('cog_intro');
    expect(store.shouldShow('cog_intro')).toBe(false);
    expect(store.answered.has('cog_intro')).toBe(true);
    expect(persistedAnswered()).toContain('cog_intro');
  });

  it('hydrates the answered set from localStorage on init', () => {
    window.localStorage.setItem(ANON_PERSIST_KEY, JSON.stringify(['cog_intro']));
    const store = useProbeStore();
    expect(store.answered.has('cog_intro')).toBe(true);
    expect(store.shouldShow('cog_intro')).toBe(false);
  });

  it('submit() POSTs the choice, then marks answered + persists', async () => {
    const store = useProbeStore();
    await store.submit('cog_intro', 'visual');
    expect(submitProbe).toHaveBeenCalledTimes(1);
    expect(submitProbe).toHaveBeenCalledWith('cog_intro', 'visual');
    expect(store.answered.has('cog_intro')).toBe(true);
    expect(persistedAnswered()).toContain('cog_intro');
  });

  it('submit() still marks answered when the API rejects (never blocks the player)', async () => {
    vi.mocked(submitProbe).mockRejectedValueOnce(new Error('PROBE_INVALID'));
    const store = useProbeStore();
    await expect(store.submit('cog_intro', 'logical')).resolves.toBeUndefined();
    expect(store.answered.has('cog_intro')).toBe(true);
    expect(persistedAnswered()).toContain('cog_intro');
  });

  // ③ fail_react 探针：练习失败 → emotion_persistence（retry/hint/skip）。
  it('request() marks a probe as requested without affecting shouldShow gating', () => {
    const store = useProbeStore();
    expect(store.isRequested('fail_react')).toBe(false);
    store.request('fail_react');
    expect(store.isRequested('fail_react')).toBe(true);
    // request 只是触发信号，本身不消费可弹资格。
    expect(store.shouldShow('fail_react')).toBe(true);
  });

  it.each(['retry', 'hint', 'skip'])(
    'submit() maps the fail_react choice "%s" verbatim then marks answered + persists',
    async (choiceId) => {
      const store = useProbeStore();
      await store.submit('fail_react', choiceId);
      expect(submitProbe).toHaveBeenCalledTimes(1);
      expect(submitProbe).toHaveBeenCalledWith('fail_react', choiceId);
      expect(store.answered.has('fail_react')).toBe(true);
      expect(persistedAnswered()).toContain('fail_react');
      expect(store.shouldShow('fail_react')).toBe(false);
    },
  );

  it('submit("fail_react", ...) stays fail-silent when the API rejects', async () => {
    vi.mocked(submitProbe).mockRejectedValueOnce(new Error('PROBE_INVALID'));
    const store = useProbeStore();
    await expect(store.submit('fail_react', 'skip')).resolves.toBeUndefined();
    expect(store.answered.has('fail_react')).toBe(true);
    expect(persistedAnswered()).toContain('fail_react');
  });

  it('an already answered fail_react is never re-shown even after request()', () => {
    const store = useProbeStore();
    store.markAnswered('fail_react');
    store.request('fail_react');
    expect(store.isRequested('fail_react')).toBe(true);
    expect(store.shouldShow('fail_react')).toBe(false);
  });
});
