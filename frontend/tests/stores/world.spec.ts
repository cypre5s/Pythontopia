import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useWorldStore } from '@/stores/world';
import { fetchWorldSession, patchPlayerState } from '@/api/world';

vi.mock('@/api/world', () => ({
  fetchWorldSession: vi.fn(async () => ({
    worldSessionId: 1,
    dayIndex: 1,
    weather: 'SUNNY',
    inGameMinutes: 480,
    player: {
      playerStateId: 2,
      avatarKey: 'student_default',
      mapKey: 'town_main',
      tileX: 15,
      tileY: 11,
      direction: 'DOWN',
    },
    npcs: [{ npcKey: 'mayor_py', mood: 'NEUTRAL' }],
  })),
  patchPlayerState: vi.fn(async (req) => ({
    playerStateId: 2,
    avatarKey: 'student_default',
    mapKey: 'town_main',
    ...req,
  })),
}));

describe('world store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.useFakeTimers();
    vi.setSystemTime(1000);
    vi.mocked(patchPlayerState).mockClear();
    vi.mocked(fetchWorldSession).mockClear();
  });

  it('loads world session from typed API', async () => {
    const store = useWorldStore();

    await store.loadSession();

    expect(fetchWorldSession).toHaveBeenCalledTimes(1);
    expect(store.session?.dayIndex).toBe(1);
    expect(store.npcStates[0]?.npcKey).toBe('mayor_py');
  });

  it('throttles player tile persistence with trailing update', async () => {
    const store = useWorldStore();
    await store.updatePlayerTile({ tileX: 15, tileY: 12, direction: 'DOWN' });
    vi.setSystemTime(1100);

    await store.updatePlayerTile({ tileX: 16, tileY: 12, direction: 'RIGHT' });

    expect(patchPlayerState).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(400);
    expect(patchPlayerState).toHaveBeenCalledTimes(2);
    expect(patchPlayerState).toHaveBeenLastCalledWith({ tileX: 16, tileY: 12, direction: 'RIGHT' });
  });
});
