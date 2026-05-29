import { describe, expect, it } from 'vitest';
import { findNearestInteraction, nextTile, shouldTurnBeforeMove } from '@/game/scenes/TownScene';
import type { TownMapData } from '@/game/adapters/tilemap';

const map: TownMapData = {
  tileSize: 32,
  width: 30,
  height: 20,
  spawn: { tileX: 15, tileY: 11 },
  buildings: [
    { key: 'workshop', label: '资源工坊', route: '/workshop', tileX: 12, tileY: 2, width: 5, height: 3 },
  ],
  npcs: [{ npcKey: 'mayor_py', label: '镇长 Py', tileX: 14, tileY: 9 }],
  lowObstacles: Array.from({ length: 20 }, () => Array.from({ length: 30 }, () => false)),
};

describe('TownScene movement and interaction helpers', () => {
  it('moves one tile diagonally for eight-direction input', () => {
    expect(nextTile(15, 11, 'UP_RIGHT', 30, 20)).toEqual({ tileX: 16, tileY: 10 });
    expect(nextTile(0, 0, 'UP_LEFT', 30, 20)).toEqual({ tileX: 0, tileY: 0 });
  });

  it('turns in place before moving when the requested facing changes', () => {
    expect(shouldTurnBeforeMove('DOWN', 'RIGHT')).toBe(true);
    expect(shouldTurnBeforeMove('RIGHT', 'RIGHT')).toBe(false);
    expect(shouldTurnBeforeMove('UP', 'UP_RIGHT')).toBe(true);
  });

  it('finds keyboard interaction targets near building doors and NPCs', () => {
    expect(findNearestInteraction(map, 14, 5)).toEqual({ kind: 'building', route: '/workshop', label: '资源工坊' });
    expect(findNearestInteraction(map, 14, 10)).toEqual({ kind: 'npc', npcKey: 'mayor_py', label: '镇长 Py' });
  });
});
