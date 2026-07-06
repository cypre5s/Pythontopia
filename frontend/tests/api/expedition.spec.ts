import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  http: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { http } from '@/api/http';
import {
  deleteExpeditionRun,
  fetchExpeditionRun,
  requestFrontierPicks,
  saveExpeditionRun,
  type ExpeditionRunSnapshot,
} from '@/api/expedition';

const RUN: ExpeditionRunSnapshot = {
  phase: 'map',
  focusChapter: 2,
  nodes: [
    {
      id: 'L0-0',
      layer: 0,
      col: 0,
      kind: 'battle',
      kcCode: 'PY43-KC-008',
      challengeKind: 'dual_track',
      difficulty: 3,
      next: [],
      state: 'available',
    },
  ],
  layers: 1,
  currentNodeId: null,
  hp: 5,
  maxHp: 5,
  momentum: 0,
  bestMomentum: 0,
  score: 0,
  relics: [],
  isDaily: false,
  history: [],
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('expedition.ts · frontier selector', () => {
  it('requests graph-backed frontier picks through the boss proxy', async () => {
    vi.mocked(http.post).mockResolvedValueOnce({ data: { picks: [] } });

    await requestFrontierPicks({ focusChapter: 1, recentKcs: ['PY43-KC-001'], count: 4 });

    expect(http.post).toHaveBeenCalledWith(
      '/api/world/boss/select-frontier',
      { recentKcs: ['PY43-KC-001'], exclude: [], count: 4, focusChapter: 1 },
      { suppressNetworkToast: true, timeout: 3000 },
    );
  });
});

describe('expedition.ts · run persistence API', () => {
  it('GETs the active backend run and maps 204/empty to null', async () => {
    vi.mocked(http.get).mockResolvedValueOnce({ status: 204, data: undefined });
    await expect(fetchExpeditionRun()).resolves.toBeNull();
    expect(http.get).toHaveBeenCalledWith('/api/world/expedition/run', {
      suppressNetworkToast: true,
      timeout: 3000,
      validateStatus: expect.any(Function),
    });
  });

  it('returns the backend run snapshot when present', async () => {
    vi.mocked(http.get).mockResolvedValueOnce({ status: 200, data: RUN });
    await expect(fetchExpeditionRun()).resolves.toEqual(RUN);
  });

  it('PUTs the whole run snapshot without blocking optional toasts', async () => {
    vi.mocked(http.put).mockResolvedValueOnce({ data: RUN });

    await expect(saveExpeditionRun(RUN)).resolves.toEqual(RUN);

    expect(http.put).toHaveBeenCalledWith('/api/world/expedition/run', RUN, {
      suppressNetworkToast: true,
      timeout: 3000,
    });
  });

  it('DELETEs the active run on abandon/end', async () => {
    vi.mocked(http.delete).mockResolvedValueOnce({ data: undefined });

    await deleteExpeditionRun();

    expect(http.delete).toHaveBeenCalledWith('/api/world/expedition/run', {
      suppressNetworkToast: true,
      timeout: 3000,
    });
  });
});
