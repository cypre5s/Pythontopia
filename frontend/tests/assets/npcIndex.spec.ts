import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

interface NpcIndexEntry {
  key: string;
  /** NPC 实际出生的地图 key；未在任何地图上出现（已下架）时为 null。 */
  spawnMap: string | null;
}

interface NpcIndexFile {
  /** NPC 数据包（data/npcs/<key>/）总数，不是「地图上站着几个」—— 见 spawnMap。 */
  totalNpcs: number;
  categories: Record<string, string[]>;
  npcs: NpcIndexEntry[];
}

interface TiledObject {
  type?: string;
  properties?: Array<{ name: string; value: unknown }>;
}

interface TiledMap {
  layers: Array<{ type: string; objects?: TiledObject[] }>;
}

const repoRoot = join(process.cwd(), '..');
const npcRoot = join(repoRoot, 'data', 'npcs');

function readIndex(): NpcIndexFile {
  return JSON.parse(readFileSync(join(npcRoot, 'index.json'), 'utf8')) as NpcIndexFile;
}

/** 从 town_hub.json 的 object 层读出真实摆在地图上的 NPC key。 */
function readTownHubNpcKeys(): string[] {
  const map = JSON.parse(
    readFileSync(join(process.cwd(), 'public', 'assets', 'maps', 'town_hub.json'), 'utf8'),
  ) as TiledMap;
  const keys: string[] = [];
  for (const layer of map.layers) {
    if (layer.type !== 'objectgroup') continue;
    for (const obj of layer.objects ?? []) {
      if (obj.type !== 'npc') continue;
      const keyProp = obj.properties?.find((p) => p.name === 'key');
      if (typeof keyProp?.value === 'string') keys.push(keyProp.value);
    }
  }
  return keys.sort();
}

describe('NPC index', () => {
  it('lists every NPC directory in total, categories, and npcs', () => {
    const expectedKeys = readdirSync(npcRoot, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name)
      .sort();
    const index = readIndex();
    const categoryKeys = Object.values(index.categories).flat().sort();
    const listedKeys = index.npcs.map((npc) => npc.key).sort();

    // totalNpcs = 数据包数量（每个 key 一个目录），与「地图上站着几个」无关。
    expect(index.totalNpcs).toBe(expectedKeys.length);
    expect(categoryKeys).toEqual(expectedKeys);
    expect(listedKeys).toEqual(expectedKeys);
  });

  it('spawnMap=town_hub matches exactly the NPCs actually placed on town_hub.json', () => {
    // 回归：professor / mine_keeper / student_a / arena_guard 四个已下架 NPC 仍标着
    // spawnMap: "town_hub"，索引声称 11 个 NPC 在小镇，地图上只摆了 7 个。
    const index = readIndex();
    const claimed = index.npcs
      .filter((npc) => npc.spawnMap === 'town_hub')
      .map((npc) => npc.key)
      .sort();

    expect(claimed).toEqual(readTownHubNpcKeys());
  });

  it('every NPC entry declares spawnMap as a map key or explicit null', () => {
    for (const npc of readIndex().npcs) {
      expect(npc.spawnMap === null || typeof npc.spawnMap === 'string').toBe(true);
    }
  });
});
