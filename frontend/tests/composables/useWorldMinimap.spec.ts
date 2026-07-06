// R-13：useWorldMinimap 单测，覆盖 fetch 解析 + cache + 字段降级。
//
// 测试策略：
//   - 用 global.fetch mock 拦截 /assets/maps/<key>.json 与 /assets/maps/index.json，
//     避免 jsdom 环境真去拉网络；
//   - 用 mount 一个 noop 组件来挂载 composable 的生命周期；
//   - 跨用例 `__resetMinimapCacheForTest()` 重置 cache，避免顺序依赖。

import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, h } from 'vue';
import {
  __resetMinimapCacheForTest,
  useWorldMinimap,
  type UseWorldMinimapReturn,
} from '@/composables/useWorldMinimap';
import { useWorldStore } from '@stores/world';

interface TestHandle {
  hook: UseWorldMinimapReturn;
}

function mountHost(): TestHandle {
  const handle: TestHandle = { hook: null as unknown as UseWorldMinimapReturn };
  const Host = defineComponent({
    setup() {
      handle.hook = useWorldMinimap();
      return () => h('div');
    },
  });
  mount(Host);
  return handle;
}

const sampleIndex = {
  maps: [
    { key: 'town_hub', label: 'Pythontopia 小镇', spawn: { tileX: 25, tileY: 20 } },
    { key: 'mine_south', label: 'Bug 矿洞', spawn: { tileX: 15, tileY: 2 } },
  ],
};

const sampleTownHubMap = {
  width: 4,
  height: 4,
  tilewidth: 16,
  properties: [{ name: 'label', type: 'string', value: 'Pythontopia 小镇' }],
  layers: [
    { type: 'tilelayer', name: 'ground', width: 4, height: 4, data: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1] },
    { type: 'tilelayer', name: 'buildings', width: 4, height: 4, data: [0, 0, 0, 0, 0, 9, 9, 0, 0, 9, 9, 0, 0, 0, 0, 0] },
    {
      type: 'objectgroup',
      name: 'objects',
      objects: [
        // 南门 exit -> mine_south
        { id: 1, type: 'exit', x: 16, y: 48, width: 64, height: 16, properties: [{ name: 'target', type: 'string', value: 'mine_south' }] },
        // 一个 NPC：mayor_py at tile (2, 1)
        { id: 2, type: 'npc', x: 32, y: 16, properties: [{ name: 'key', type: 'string', value: 'mayor_py' }] },
      ],
    },
  ],
};

beforeEach(() => {
  setActivePinia(createPinia());
  __resetMinimapCacheForTest();
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    if (url.endsWith('/assets/maps/index.json')) {
      return {
        ok: true,
        json: async () => sampleIndex,
      } as unknown as Response;
    }
    if (url.endsWith('/assets/maps/town_hub.json')) {
      return {
        ok: true,
        json: async () => sampleTownHubMap,
      } as unknown as Response;
    }
    return { ok: false, json: async () => ({}) } as unknown as Response;
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('useWorldMinimap', () => {
  it('player 未 bootstrap 时 data 为 null（不抛错）', () => {
    const { hook } = mountHost();
    expect(hook.data.value).toBeNull();
  });

  it('player.mapKey 切到 town_hub 后 fetch + 解析 tilemap/exits/npcs', async () => {
    const world = useWorldStore();
    world.player = {
      playerStateId: 1,
      avatarKey: 'student_default',
      mapKey: 'town_hub',
      tileX: 25,
      tileY: 20,
      direction: 'DOWN',
    };
    const { hook } = mountHost();
    await flushPromises();
    await flushPromises();

    expect(hook.data.value?.mapKey).toBe('town_hub');
    expect(hook.data.value?.mapName).toBe('Pythontopia 小镇');
    expect(hook.data.value?.tilemap.width).toBe(4);
    expect(hook.data.value?.tilemap.height).toBe(4);
    // collision: 中心 2x2 被 buildings 占据
    expect(hook.data.value?.tilemap.collision?.[1]?.[1]).toBe(true);
    expect(hook.data.value?.tilemap.collision?.[2]?.[2]).toBe(true);
    // npc 解析
    expect(hook.data.value?.npcs).toEqual([{ id: 'mayor_py', x: 2, y: 1 }]);
    // exit 推断 side=north + label 取自 index
    expect(hook.data.value?.exits).toHaveLength(1);
    expect(hook.data.value?.exits[0]).toMatchObject({
      side: 'south',
      toMap: 'mine_south',
      label: 'Bug 矿洞',
    });
  });

  it('playerX/Y 跟随 worldStore.player.tileX/Y 响应式更新', async () => {
    const world = useWorldStore();
    world.player = {
      playerStateId: 1,
      avatarKey: 'student_default',
      mapKey: 'town_hub',
      tileX: 10,
      tileY: 12,
      direction: 'DOWN',
    };
    const { hook } = mountHost();
    await flushPromises();
    expect(hook.playerX.value).toBe(10);
    expect(hook.playerY.value).toBe(12);

    world.player = { ...world.player, tileX: 30, tileY: 5 };
    await flushPromises();
    expect(hook.playerX.value).toBe(30);
    expect(hook.playerY.value).toBe(5);
  });

  it('未知 mapKey 时 fallback 到 town_hub（R-13 修复：永不让 minimap 消失）', async () => {
    const world = useWorldStore();
    world.player = {
      playerStateId: 1,
      avatarKey: 'student_default',
      mapKey: 'unknown_zone',
      tileX: 0,
      tileY: 0,
      direction: 'DOWN',
    };
    const { hook } = mountHost();
    await flushPromises();
    await flushPromises();
    // 旧 spec 期望 null；R-13 修复后改为 fallback town_hub，避免后端旧数据让 minimap 消失
    expect(hook.data.value?.mapKey).toBe('town_hub');
  });

  it('R-14 废弃外围图别名 farm_north → town_hub', async () => {
    const world = useWorldStore();
    world.player = {
      playerStateId: 1,
      avatarKey: 'student_default',
      mapKey: 'farm_north',
      tileX: 0,
      tileY: 0,
      direction: 'DOWN',
    };
    const { hook } = mountHost();
    await flushPromises();
    await flushPromises();
    expect(hook.data.value?.mapKey).toBe('town_hub');
  });

  it('R-13 兼容旧后端横杠命名：town-beta → town_hub', async () => {
    const world = useWorldStore();
    world.player = {
      playerStateId: 1,
      avatarKey: 'student_default',
      mapKey: 'town-beta',
      tileX: 0,
      tileY: 0,
      direction: 'DOWN',
    };
    const { hook } = mountHost();
    await flushPromises();
    await flushPromises();
    expect(hook.data.value?.mapKey).toBe('town_hub');
  });
});
