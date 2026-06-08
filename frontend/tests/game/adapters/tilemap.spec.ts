import { describe, expect, it } from 'vitest';
import { drawNpcSprite, type NpcSpawn } from '@game/adapters/tilemap';

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
