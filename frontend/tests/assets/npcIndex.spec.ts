import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

interface NpcIndexEntry {
  key: string;
}

interface NpcIndexFile {
  totalNpcs: number;
  categories: Record<string, string[]>;
  npcs: NpcIndexEntry[];
}

const repoRoot = join(process.cwd(), '..');
const npcRoot = join(repoRoot, 'data', 'npcs');

describe('NPC index', () => {
  it('lists every NPC directory in total, categories, and npcs', () => {
    const expectedKeys = readdirSync(npcRoot, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name)
      .sort();
    const index = JSON.parse(readFileSync(join(npcRoot, 'index.json'), 'utf8')) as NpcIndexFile;
    const categoryKeys = Object.values(index.categories).flat().sort();
    const listedKeys = index.npcs.map((npc) => npc.key).sort();

    expect(index.totalNpcs).toBe(expectedKeys.length);
    expect(categoryKeys).toEqual(expectedKeys);
    expect(listedKeys).toEqual(expectedKeys);
  });
});
