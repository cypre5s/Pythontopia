import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useFeynmanStore } from '@/stores/feynman';

const mocks = vi.hoisted(() => ({
  startFeynmanSession: vi.fn(),
  teachFeynmanSession: vi.fn(),
  huntFeynmanBug: vi.fn(),
  judgeFeynmanSession: vi.fn(),
}));

vi.mock('@/api/feynman', () => mocks);

const session = {
  id: 11,
  userId: 3,
  kcCode: 'PY43-KC-010',
  status: 'TAUGHT' as const,
  tuteeState: {
    misconceptions: [
      {
        tag: 'off_by_one_range',
        evidenceEventIds: [101],
        resolved: true,
      },
    ],
  },
};

describe('feynman store judge truth', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    mocks.startFeynmanSession.mockResolvedValue(session);
  });

  it.each(['ERROR', 'NA'] as const)(
    'preserves backend %s instead of coercing it to FAILED',
    async (status) => {
      mocks.judgeFeynmanSession.mockResolvedValue({
        sessionId: 11,
        passed: false,
        status,
        message: `后端 ${status}`,
      });
      const store = useFeynmanStore();
      await store.startSession('PY43-KC-010');

      const result = await store.judge();

      expect(result.status).toBe(status);
      expect(store.latestJudgeResult?.status).toBe(status);
      expect(store.currentSession?.status).toBe('TAUGHT');
    },
  );

  it('projects PASSED back into the current session so the UI cannot score it again', async () => {
    mocks.judgeFeynmanSession.mockResolvedValue({
      sessionId: 11,
      passed: true,
      status: 'PASSED',
      message: '教学成功',
    });
    const store = useFeynmanStore();
    await store.startSession('PY43-KC-010');

    await store.judge();

    expect(store.latestJudgeResult?.status).toBe('PASSED');
    expect(store.currentSession?.status).toBe('JUDGED_PASS');
  });
});
