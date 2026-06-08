import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useNpcStore } from '@stores/npc';
import type { NpcDialogStateDto } from '@/api/dto/NpcDialogStateDto';

const mocks = vi.hoisted(() => ({
  fetchNpcState: vi.fn(),
  talkToNpc: vi.fn(),
}));

vi.mock('@/api/worldNpc', () => mocks);

function makeState(
  npcKey: string,
  completed: string[] = [],
): NpcDialogStateDto {
  return {
    npcKey,
    currentNodeId: null,
    completedNodeIds: completed,
    lastTalkedAt: null,
    flags: {},
  };
}

describe('npc store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.fetchNpcState.mockReset();
    mocks.talkToNpc.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loadState() GET /api/world/npcs/{key}/state', async () => {
    mocks.fetchNpcState.mockResolvedValue(makeState('mayor_py'));
    const store = useNpcStore();
    const result = await store.loadState('mayor_py');
    expect(mocks.fetchNpcState).toHaveBeenCalledWith('mayor_py');
    expect(result?.npcKey).toBe('mayor_py');
    expect(store.isMet('mayor_py')).toBe(true);
    expect(store.isMet('stranger')).toBe(false);
  });

  it('loadState() captures error and returns null', async () => {
    mocks.fetchNpcState.mockRejectedValue(new Error('404'));
    const store = useNpcStore();
    const result = await store.loadState('ghost');
    expect(result).toBeNull();
    expect(store.error).toBe('404');
    expect(store.isMet('ghost')).toBe(false);
  });

  it('talk() POST /talk and merges response into states', async () => {
    const store = useNpcStore();
    store.setBootstrap([makeState('mayor_py')]);
    mocks.talkToNpc.mockResolvedValue({
      npcKey: 'mayor_py',
      currentNodeId: 'intro',
      completedNodeIds: ['greet'],
      lastTalkedAt: '2026-05-22T08:00:00Z',
    });
    await store.talk('mayor_py', { nodeId: 'greet', nextNodeId: 'intro', effects: [] });
    expect(mocks.talkToNpc).toHaveBeenCalledWith('mayor_py', {
      nodeId: 'greet',
      nextNodeId: 'intro',
      effects: [],
    });
    expect(store.getState('mayor_py')?.currentNodeId).toBe('intro');
    expect(store.getState('mayor_py')?.completedNodeIds).toEqual(['greet']);
  });

  it('setBootstrap replaces all states with a keyed map', () => {
    const store = useNpcStore();
    store.setBootstrap([makeState('a'), makeState('b')]);
    expect(Object.keys(store.states).sort()).toEqual(['a', 'b']);
    store.setBootstrap([makeState('c')]);
    expect(Object.keys(store.states)).toEqual(['c']);
  });
});
