import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useNpcStore } from '@stores/npc';
import type { NpcDialogStateDto } from '@/api/dto/NpcDialogStateDto';

const mocks = vi.hoisted(() => ({
  fetchNpcState: vi.fn(),
  talkToNpc: vi.fn(),
  giftNpc: vi.fn(),
}));

vi.mock('@/api/worldNpc', () => mocks);

function makeState(
  npcKey: string,
  friendship = 0,
  completed: string[] = [],
): NpcDialogStateDto {
  return {
    npcKey,
    currentNodeId: null,
    completedNodeIds: completed,
    friendship,
    lastTalkedAt: null,
    flags: {},
  };
}

describe('npc store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.fetchNpcState.mockReset();
    mocks.talkToNpc.mockReset();
    mocks.giftNpc.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loadState() GET /api/world/npcs/{key}/state', async () => {
    mocks.fetchNpcState.mockResolvedValue(makeState('mayor_py', 12));
    const store = useNpcStore();
    const result = await store.loadState('mayor_py');
    expect(mocks.fetchNpcState).toHaveBeenCalledWith('mayor_py');
    expect(result?.friendship).toBe(12);
    expect(store.friendship('mayor_py')).toBe(12);
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
    store.setBootstrap([makeState('mayor_py', 5)]);
    mocks.talkToNpc.mockResolvedValue({
      npcKey: 'mayor_py',
      currentNodeId: 'intro',
      completedNodeIds: ['greet'],
      friendship: 6,
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
    expect(store.friendship('mayor_py')).toBe(6);
  });

  it('gift() POST /gift and updates friendship', async () => {
    const store = useNpcStore();
    store.setBootstrap([makeState('mayor_py', 5)]);
    mocks.giftNpc.mockResolvedValue({
      npcKey: 'mayor_py',
      itemKey: 'item_coffee',
      giftTier: 'LIKE',
      friendshipDelta: 3,
      newFriendship: 8,
    });
    const response = await store.gift('mayor_py', 'item_coffee', 1);
    expect(mocks.giftNpc).toHaveBeenCalledWith('mayor_py', { itemKey: 'item_coffee', qty: 1 });
    expect(response.giftTier).toBe('LIKE');
    expect(store.friendship('mayor_py')).toBe(8);
  });

  it('gift() before meeting does not crash and returns server response', async () => {
    const store = useNpcStore();
    mocks.giftNpc.mockResolvedValue({
      npcKey: 'stranger',
      itemKey: 'item_apple',
      giftTier: 'NEUTRAL',
      friendshipDelta: 0,
      newFriendship: 0,
    });
    const response = await store.gift('stranger', 'item_apple', 1);
    expect(response.newFriendship).toBe(0);
    expect(store.friendship('stranger')).toBe(0);
  });

  it('setBootstrap replaces all states with a keyed map', () => {
    const store = useNpcStore();
    store.setBootstrap([makeState('a', 1), makeState('b', 2)]);
    expect(Object.keys(store.states).sort()).toEqual(['a', 'b']);
    store.setBootstrap([makeState('c', 3)]);
    expect(Object.keys(store.states)).toEqual(['c']);
  });
});
