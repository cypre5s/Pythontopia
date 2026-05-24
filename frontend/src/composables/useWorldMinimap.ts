// 隔离原因：
// MiniMap.vue 是纯展示组件（不依赖 Phaser / store），上层 Page 必须自行准备
// `{ tilemap, playerX, playerY, npcs, mapName, exits }`。本 composable 是
// 业务层与 5 张 Tiled JSON 之间的唯一桥，负责把：
//   - 仓库根 `frontend/public/assets/maps/<mapKey>.json` 的 4 层 tilemap +
//     objects（exit / npc）转成 MiniMap 需要的 `MiniMapTilemap` + `NpcMarker[]`
//     + `MinimapExitMarker[]`；
//   - useWorldStore().player.{mapKey, tileX, tileY} 转成响应式的 playerX/Y。
//
// 落地约束：
// 1. 不创建第二个 Phaser.Game 实例：MiniMap 走纯 fetch + DOM canvas，绝不复用
//    BootScene/WorldScene 的资源加载链；
// 2. 5 张地图 JSON 总和约 7 MB，但解析后的缩略 ground/collision 数组每张 ≤ 50×40 =
//    2000 个 cell，足够轻量；fetch 结果走模块级 Map 缓存，整个会话每张图最多拉一次；
// 3. fail-safe：fetch 失败 / JSON 结构异常 / SSR window 缺失时静默退化为 null
//    tilemap，MiniMap 自然降级到「无地图数据」占位；
// 4. 不耦合 Phaser tilemap：minimap 直接读 JSON，避免 Phaser 还没启动就被请求
//    时拿到空场景。即使 WorldScene 在 restart fade 期间也能正常显示。

import { ref, watch, type Ref } from 'vue';
import { useWorldStore } from '@stores/world';
import type {
  MinimapExitMarker,
  MinimapExitSide,
} from '@/components/hud/MiniMap.vue';

const TILE_SIZE = 16;

const SUPPORTED_MAP_KEYS = [
  'town_hub',
  'farm_north',
  'mine_south',
  'campus_east',
  'port_west',
] as const;
type MapKey = (typeof SUPPORTED_MAP_KEYS)[number];

/**
 * R-13 修复：后端旧数据可能返回 `town-beta` `student-default` 等横杠/旧命名，
 * 这里做命名规范化：横杠→下划线，旧 `town-beta` → `town_hub`，未匹配则 fallback。
 */
function normalizeMapKey(value: unknown): MapKey {
  if (typeof value !== 'string') return 'town_hub';
  const norm = value.replace(/-/g, '_');
  const LEGACY_ALIAS: Record<string, MapKey> = {
    town_beta: 'town_hub',
    town: 'town_hub',
    town_main: 'town_hub',
    farm: 'farm_north',
    mine: 'mine_south',
    campus: 'campus_east',
    port: 'port_west',
  };
  if ((SUPPORTED_MAP_KEYS as readonly string[]).includes(norm)) return norm as MapKey;
  if (LEGACY_ALIAS[norm]) return LEGACY_ALIAS[norm];
  return 'town_hub';
}

export interface MinimapTilemapShape {
  width: number;
  height: number;
  ground?: number[][];
  collision?: boolean[][];
}

export interface MinimapNpcMarker {
  id: string;
  x: number;
  y: number;
}

export interface MinimapMapData {
  mapKey: MapKey;
  mapName: string;
  tilemap: MinimapTilemapShape;
  npcs: MinimapNpcMarker[];
  exits: MinimapExitMarker[];
}

export interface UseWorldMinimapReturn {
  /** 当前激活地图的轻量 minimap data；尚未加载 / 玩家未 bootstrap 时为 null */
  data: Ref<MinimapMapData | null>;
  /** 玩家 tile 坐标（一旦 worldStore.player 变化即同步刷新） */
  playerX: Ref<number>;
  playerY: Ref<number>;
  /** 玩家面向（4 方向），用于未来扩展（如朝向箭头） */
  playerFacing: Ref<'UP' | 'DOWN' | 'LEFT' | 'RIGHT'>;
  /** 当前正在 fetch 的地图 key（用于上层显示 spinner 等） */
  loading: Ref<boolean>;
}

const cache: Map<MapKey, MinimapMapData> = new Map();
const inflight: Map<MapKey, Promise<MinimapMapData | null>> = new Map();

interface TiledProperty {
  name?: unknown;
  value?: unknown;
}

interface TiledObject {
  id?: number;
  name?: string;
  type?: string;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  properties?: TiledProperty[];
}

interface TiledObjectGroup {
  type?: string;
  objects?: TiledObject[];
}

interface TiledTileLayer {
  type?: string;
  name?: string;
  width?: number;
  height?: number;
  data?: number[];
}

type TiledLayer = TiledTileLayer | TiledObjectGroup;

interface TiledMapJson {
  width?: number;
  height?: number;
  tilewidth?: number;
  layers?: TiledLayer[];
  properties?: TiledProperty[];
}

interface TiledMapIndexEntry {
  key?: string;
  label?: string;
  spawn?: { tileX?: number; tileY?: number };
}

interface TiledMapIndex {
  maps?: TiledMapIndexEntry[];
}

let mapIndexPromise: Promise<TiledMapIndex> | null = null;

async function fetchMapIndex(): Promise<TiledMapIndex> {
  if (!mapIndexPromise) {
    mapIndexPromise = fetch('/assets/maps/index.json')
      .then((res) => (res.ok ? (res.json() as Promise<TiledMapIndex>) : { maps: [] }))
      .catch(() => ({ maps: [] }));
  }
  return mapIndexPromise;
}

function collectProps(props: unknown): Record<string, unknown> {
  if (!Array.isArray(props)) return {};
  const out: Record<string, unknown> = {};
  for (const item of props as TiledProperty[]) {
    if (item && typeof item.name === 'string') {
      out[item.name] = item.value;
    }
  }
  return out;
}

function sideFromExit(obj: TiledObject, mapW: number, mapH: number): MinimapExitSide {
  const cx = (obj.x ?? 0) + (obj.width ?? 0) / 2;
  const cy = (obj.y ?? 0) + (obj.height ?? 0) / 2;
  const w = mapW * TILE_SIZE;
  const h = mapH * TILE_SIZE;
  // 距离最近的那条边即为 side；阈值优先按 X 还是 Y 由"到边距离"比较决定。
  const distLeft = cx;
  const distRight = w - cx;
  const distTop = cy;
  const distBottom = h - cy;
  const min = Math.min(distLeft, distRight, distTop, distBottom);
  if (min === distTop) return 'north';
  if (min === distBottom) return 'south';
  if (min === distLeft) return 'west';
  return 'east';
}

function parseTiledMap(json: TiledMapJson, mapKey: MapKey, index: TiledMapIndex): MinimapMapData {
  const width = Math.max(1, Number(json.width ?? 1));
  const height = Math.max(1, Number(json.height ?? 1));
  const ground: number[][] = Array.from({ length: height }, () =>
    Array.from({ length: width }, () => 0),
  );
  const collision: boolean[][] = Array.from({ length: height }, () =>
    Array.from({ length: width }, () => false),
  );

  const layers = json.layers ?? [];
  for (const layer of layers) {
    if ((layer as TiledTileLayer).type === 'tilelayer') {
      const tl = layer as TiledTileLayer;
      const name = (tl.name ?? '').toLowerCase();
      const lw = Math.max(1, Number(tl.width ?? width));
      const data = tl.data ?? [];
      if (name === 'ground') {
        for (let y = 0; y < height; y += 1) {
          for (let x = 0; x < width; x += 1) {
            const cell = data[y * lw + x];
            if (typeof cell === 'number' && cell > 0) {
              const row = ground[y];
              if (row) row[x] = cell;
            }
          }
        }
      } else if (name === 'buildings' || name === 'collision') {
        for (let y = 0; y < height; y += 1) {
          for (let x = 0; x < width; x += 1) {
            const cell = data[y * lw + x];
            if (typeof cell === 'number' && cell > 0) {
              const row = collision[y];
              if (row) row[x] = true;
            }
          }
        }
      }
    }
  }

  const npcs: MinimapNpcMarker[] = [];
  const exits: MinimapExitMarker[] = [];
  const seenExits = new Set<string>();
  for (const layer of layers) {
    const og = layer as TiledObjectGroup;
    if (og.type !== 'objectgroup') continue;
    const objects = og.objects ?? [];
    for (const obj of objects) {
      const type = String(obj.type ?? '');
      const tileX = Math.floor((obj.x ?? 0) / TILE_SIZE);
      const tileY = Math.floor((obj.y ?? 0) / TILE_SIZE);
      const props = collectProps(obj.properties);
      if (type === 'npc') {
        const key = String(props['key'] ?? props['npcKey'] ?? obj.name ?? `npc-${tileX}-${tileY}`);
        npcs.push({ id: key, x: tileX, y: tileY });
      } else if (type === 'exit') {
        const target = String(props['target'] ?? props['toMap'] ?? '');
        if (!target) continue;
        const side = sideFromExit(obj, width, height);
        const dedupKey = `${side}:${target}`;
        if (seenExits.has(dedupKey)) continue;
        seenExits.add(dedupKey);
        const targetEntry = index.maps?.find((m) => m.key === target);
        const exitMarker: MinimapExitMarker = { side, toMap: target };
        if (targetEntry?.label) {
          exitMarker.label = targetEntry.label;
        }
        exits.push(exitMarker);
      }
    }
  }

  const indexEntry = index.maps?.find((m) => m.key === mapKey);
  const rawLabel = (collectProps(json.properties)['label'] as string | undefined) ?? indexEntry?.label ?? mapKey;
  return {
    mapKey,
    mapName: rawLabel,
    tilemap: { width, height, ground, collision },
    npcs,
    exits,
  };
}

async function loadMinimapData(mapKey: MapKey): Promise<MinimapMapData | null> {
  if (cache.has(mapKey)) {
    return cache.get(mapKey) ?? null;
  }
  const existing = inflight.get(mapKey);
  if (existing) {
    return existing;
  }
  const promise = (async () => {
    if (typeof fetch !== 'function') return null;
    try {
      const [mapJsonRes, index] = await Promise.all([
        fetch(`/assets/maps/${mapKey}.json`),
        fetchMapIndex(),
      ]);
      if (!mapJsonRes.ok) return null;
      const json = (await mapJsonRes.json()) as TiledMapJson;
      const data = parseTiledMap(json, mapKey, index);
      cache.set(mapKey, data);
      return data;
    } catch {
      return null;
    } finally {
      inflight.delete(mapKey);
    }
  })();
  inflight.set(mapKey, promise);
  return promise;
}

/**
 * 仅供测试使用：清空模块级 cache + inflight + mapIndexPromise，
 * 避免一个 spec 写入污染后续 spec。
 */
export function __resetMinimapCacheForTest(): void {
  cache.clear();
  inflight.clear();
  mapIndexPromise = null;
}

export function useWorldMinimap(): UseWorldMinimapReturn {
  const world = useWorldStore();
  const data = ref<MinimapMapData | null>(null);
  const playerX = ref(0);
  const playerY = ref(0);
  const playerFacing = ref<'UP' | 'DOWN' | 'LEFT' | 'RIGHT'>('DOWN');
  const loading = ref(false);

  async function refreshForMap(mapKey: MapKey): Promise<void> {
    loading.value = true;
    try {
      const next = await loadMinimapData(mapKey);
      // R-13 修复：用 normalizeMapKey 后即认为命中，不再要求 strict equal
      // （旧后端 town-beta 永远不等于 town_hub，导致 minimap 永远空）
      data.value = next;
    } finally {
      loading.value = false;
    }
  }

  watch(
    () => world.player?.mapKey ?? null,
    (key) => {
      // R-13 修复：永远 fallback 到合法 mapKey，避免 minimap 在旧后端数据下消失
      void refreshForMap(normalizeMapKey(key));
    },
    { immediate: true },
  );

  watch(
    () => [world.player?.tileX ?? 0, world.player?.tileY ?? 0, world.player?.direction ?? 'DOWN'] as const,
    ([x, y, dir]) => {
      playerX.value = Number(x);
      playerY.value = Number(y);
      if (dir === 'UP' || dir === 'DOWN' || dir === 'LEFT' || dir === 'RIGHT') {
        playerFacing.value = dir;
      }
    },
    { immediate: true },
  );

  return { data, playerX, playerY, playerFacing, loading };
}
