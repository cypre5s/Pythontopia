import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useLearningStore } from '@/stores/learning';
import { finishProfileInterview, openProfileInterview, sendProfileTurn } from '@/api/learner';

vi.mock('@/api/learner', () => ({
  getLearnerProfile: vi.fn(async () => null),
  openProfileInterview: vi.fn(async () => ({
    interviewSessionId: '00000000-0000-0000-0000-000000000001',
    status: 'OPEN',
    history: [],
    draft: null,
  })),
  sendProfileTurn: vi.fn(async () => ({
    nextQuestion: null,
    done: true,
    dimensionsFilled: [
      'major',
      'base_level',
      'learning_goal',
      'cognitive_style',
      'learning_preference',
      'error_patterns',
      'practice_capability',
      'emotion_persistence',
    ],
    profileDraft: {
      profileId: null,
      userId: 1,
      packId: 43,
      major: 'CS',
      narrativeSummary: '已生成画像',
    },
    evidenceAnchors: [],
    safetyResult: { passed: true, reasons: [] },
    groundingResult: { passed: true, reasons: [] },
  })),
  finishProfileInterview: vi.fn(async () => undefined),
}));

describe('learning profile store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(openProfileInterview).mockClear();
    vi.mocked(sendProfileTurn).mockClear();
    vi.mocked(finishProfileInterview).mockClear();
  });

  it('opens interview session and updates dimensions after a turn', async () => {
    const store = useLearningStore();

    await store.openInterview();
    await store.sendTurn('我是计算机大一，目标是期末考试。');

    expect(openProfileInterview).toHaveBeenCalledTimes(1);
    // 多轮记忆：首轮 priorHistory 为空数组，随轮次累积传给 ProfileAgent。
    expect(sendProfileTurn).toHaveBeenCalledWith(
      '00000000-0000-0000-0000-000000000001',
      { userMessage: '我是计算机大一，目标是期末考试。', history: [] },
    );
    expect(store.profileInterview.dimensionsFilled).toHaveLength(8);
    expect(store.profile?.major).toBe('CS');
  });

  it('removes the optimistic user message when a turn request fails', async () => {
    const store = useLearningStore();
    await store.openInterview();
    vi.mocked(sendProfileTurn).mockRejectedValueOnce(new Error('network unavailable'));

    await expect(store.sendTurn('我的回答')).rejects.toThrow('network unavailable');

    expect(store.profileInterview.history).toEqual([]);
    expect(store.profileInterview.loading).toBe(false);
  });
});
