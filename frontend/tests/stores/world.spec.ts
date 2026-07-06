import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useWorldStore } from '@/stores/world';
import { useCalendarStore } from '@/stores/calendar';
import { useLearningStore } from '@/stores/learning';
import { useNpcStore } from '@/stores/npc';
import { useSkillStore } from '@/stores/skill';
import { fetchWorldSession, patchPlayerState } from '@/api/world';
import { fetchGameBootstrap, postWorldSleep } from '@/api/game';

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

vi.mock('@/api/game', () => ({
  fetchGameBootstrap: vi.fn(),
  postWorldSleep: vi.fn(),
}));

describe('world store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.useFakeTimers();
    vi.setSystemTime(1000);
    vi.mocked(patchPlayerState).mockClear();
    vi.mocked(fetchWorldSession).mockClear();
    vi.mocked(fetchGameBootstrap).mockReset();
    vi.mocked(postWorldSleep).mockReset();
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

  it('preserves the local map after persisting movement (mapKey not clobbered by patch response)', async () => {
    const store = useWorldStore();
    store.player = {
      playerStateId: 2,
      avatarKey: 'student_default',
      mapKey: 'town_hub',
      tileX: 15,
      tileY: 2,
      direction: 'DOWN',
    };

    await store.updatePlayerTile({ tileX: 16, tileY: 2, direction: 'RIGHT' });
    await store.flushPlayerUpdate();

    // patchPlayerState mock 返回 mapKey:'town_main'，flushPlayerUpdate 必须保留本地 town_hub。
    expect(store.player?.mapKey).toBe('town_hub');
    expect(store.player?.tileX).toBe(16);
    expect(store.player?.direction).toBe('RIGHT');
  });

  it('bootstrap() POST /api/game/bootstrap fan-outs to all sub stores', async () => {
    vi.useRealTimers();
    vi.mocked(fetchGameBootstrap).mockResolvedValue({
      world: {
        worldSessionId: 1,
        dayIndex: 3,
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
      },
      player: {
        playerStateId: 2,
        avatarKey: 'student_default',
        mapKey: 'town_main',
        tileX: 15,
        tileY: 11,
        direction: 'DOWN',
      },
      crops: [
        {
          tileX: 2,
          tileY: 2,
          kcCode: 'PY43-KC-001',
          status: 'SEED',
          unlocked: true,
          masteryScore: 0,
          growthStage: 0,
        },
      ],
      quests: [],
      mastery: [
        {
          kcCode: 'PY43-KC-001',
          masteryScore: 0.1,
          confidence: 0.05,
          lastEventAt: null,
          latestSource: 'BOOTSTRAP',
        },
      ],
      profile: null,
      path: null,
      inventory: [
        { itemKey: 'seed_print', displayName: 'print 种子', category: 'seed', qty: 5, basePrice: 10 },
      ],
      skills: [
        {
          skillKey: 'SK-LOOP',
          displayName: '循环',
          tier: 1,
          unlockedAt: '2026-05-22T08:00:00Z',
          proficiency: 1,
        },
      ],
      skillsCatalog: [
        { skillKey: 'SK-LOOP', displayName: '循环', kcCodes: [], tier: 1 },
        { skillKey: 'SK-FUNC', displayName: '函数', kcCodes: [], tier: 2 },
      ],
      currency: { coins: 200, gems: 0 },
      calendar: {
        dayIndex: 3,
        weekday: 'WEDNESDAY',
        monthIndex: 1,
        season: 'spring',
        timeOfDay: 'MORNING',
        weather: 'SUNNY',
      },
      events: [],
      npcStates: [
        {
          npcKey: 'mayor_py',
          currentNodeId: null,
          completedNodeIds: [],
          lastTalkedAt: null,
          flags: {},
        },
      ],
      activeSlot: {
        slotIndex: 0,
        displayName: '默认存档',
        isActive: true,
        createdAt: '2026-05-22T08:00:00Z',
        lastPlayedAt: '2026-05-22T08:00:00Z',
        sceneName: 'WorldScene',
        unlockedBuildings: [],
        accumulatedPlaySeconds: 0,
        inventoryItems: [],
      },
    });

    const store = useWorldStore();
    const result = await store.bootstrap();
    expect(result).not.toBeNull();
    expect(store.bootstrapped).toBe(true);
    expect(store.session?.dayIndex).toBe(3);
    expect(store.activeSlot?.slotIndex).toBe(0);

    expect(useSkillStore().catalog).toHaveLength(2);
    expect(useSkillStore().isUnlocked('SK-LOOP')).toBe(true);
    expect(useCalendarStore().today?.dayIndex).toBe(3);
    expect(useNpcStore().isMet('mayor_py')).toBe(true);
    expect(useLearningStore().field.masterySnapshot).toHaveLength(1);
  });

  it('sleep() merges world patch and refreshes calendar', async () => {
    vi.useRealTimers();
    const store = useWorldStore();
    store.session = {
      worldSessionId: 1,
      dayIndex: 3,
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
      npcs: [],
    };
    vi.mocked(postWorldSleep).mockResolvedValue({
      world: {
        worldSessionId: 1,
        dayIndex: 4,
        weather: 'RAINY',
        inGameMinutes: 360,
      },
      calendar: {
        dayIndex: 4,
        weekday: 'THURSDAY',
        monthIndex: 1,
        season: 'spring',
        timeOfDay: 'MORNING',
        weather: 'RAINY',
      },
      events: [],
    });

    await store.sleep();

    expect(postWorldSleep).toHaveBeenCalledTimes(1);
    expect(store.session?.dayIndex).toBe(4);
    expect(store.session?.weather).toBe('RAINY');
    expect(store.session?.player.tileX).toBe(15); // 旧 player 不被清空
    expect(useCalendarStore().today?.dayIndex).toBe(4);
  });

  describe('W3 save_state extension', () => {
    const slotWithExtension = {
      slotIndex: 1,
      displayName: 'Beta save',
      isActive: true,
      createdAt: '2026-05-22T08:00:00Z',
      lastPlayedAt: '2026-05-22T12:34:56Z',
      sceneName: 'MineSouthScene',
      unlockedBuildings: ['library', 'mine_entry', 'shop_main'],
      accumulatedPlaySeconds: 7325,
      inventoryItems: [
        { itemId: 'seed_print', qty: 5, slot: 0 },
        { itemId: 'pickaxe', qty: 1, slot: 1 },
      ],
    };

    it('syncFromSlot copies consumed extension fields into store state', () => {
      vi.useRealTimers();
      const store = useWorldStore();

      store.syncFromSlot(slotWithExtension);

      expect(store.sceneName).toBe('MineSouthScene');
      expect(store.accumulatedPlaySeconds).toBe(7325);
      expect(store.activeSlot).toEqual(slotWithExtension);
    });

    it('applyBootstrap with activeSlot=null resets extension fields to defaults', () => {
      vi.useRealTimers();
      const store = useWorldStore();
      store.syncFromSlot(slotWithExtension);

      store.applyBootstrap({
        world: {
          worldSessionId: 9,
          dayIndex: 5,
          weather: 'SUNNY',
          inGameMinutes: 480,
          player: {
            playerStateId: 2,
            avatarKey: 'student_default',
            mapKey: 'town_main',
            tileX: 1,
            tileY: 2,
            direction: 'DOWN',
          },
          npcs: [],
        },
        player: {
          playerStateId: 2,
          avatarKey: 'student_default',
          mapKey: 'town_main',
          tileX: 1,
          tileY: 2,
          direction: 'DOWN',
        },
        crops: [],
        quests: [],
        mastery: [],
        profile: null,
        path: null,
        inventory: [],
        skills: [],
        skillsCatalog: [],
        currency: { coins: 0, gems: 0 },
        calendar: {
          dayIndex: 5,
          weekday: 'FRIDAY',
          monthIndex: 1,
          season: 'spring',
          timeOfDay: 'MORNING',
          weather: 'SUNNY',
        },
        events: [],
        npcStates: [],
        activeSlot: null,
      });

      expect(store.activeSlot).toBeNull();
      expect(store.sceneName).toBe('WorldScene');
      expect(store.accumulatedPlaySeconds).toBe(0);
    });
  });
});
