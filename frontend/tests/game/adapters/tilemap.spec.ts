import { describe, expect, it } from 'vitest';
import {
  NPC_HINT_ABOVE_TILE,
  drawNpcSprite,
  npcHintY,
  type NpcSpawn,
} from '@game/adapters/tilemap';

describe('tilemap adapter exports', () => {
  it('exports drawNpcSprite for WorldScene NPC rendering', () => {
    expect(typeof drawNpcSprite).toBe('function');
  });

  it('NpcSpawn shape matches town_hub spawn metadata', () => {
    const npc: NpcSpawn = {
      npcKey: 'mayor_py',
      label: '镇长 Py',
      tileX: 10,
      tileY: 12,
      sprite: 'npc_mayor',
    };
    expect(npc.npcKey).toBe('mayor_py');
  });
});

describe('npcHintY', () => {
  // 回归：NPC 头顶「!」曾经只更新 x，角色上下走动后气泡留在原地，
  // 线上表现为感叹号浮在离人物很远的地方。
  it('跟着角色 y 走：角色下移 100px 时气泡也下移 100px', () => {
    const now = 12_345;
    expect(npcHintY(200, 16, now) - npcHintY(100, 16, now)).toBe(100);
  });

  it('始终在角色头顶上方（抬高量 = NPC_HINT_ABOVE_TILE * tileSize）', () => {
    // 抬高量随角色立绘高度变化（ULPC 64px 高时是 3.4 格，SRW 20px 高时是 1.5 格），
    // 所以断言引用常量而不是写死像素值——写死会让换美术时这条回归用例失效。
    expect(npcHintY(300, 16, 0)).toBeCloseTo(300 - 16 * NPC_HINT_ABOVE_TILE, 5);
  });

  it('呼吸偏移限制在 ±4px 内且会随时间变化', () => {
    const base = (t: number) => npcHintY(0, 16, t) + 16 * NPC_HINT_ABOVE_TILE;
    for (let t = 0; t < 1600; t += 50) {
      expect(Math.abs(base(t))).toBeLessThanOrEqual(4.0001);
    }
    expect(base(400)).toBeCloseTo(4, 5);
    expect(base(1200)).toBeCloseTo(-4, 5);
  });
});
