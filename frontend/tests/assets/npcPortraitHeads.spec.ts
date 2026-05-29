import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

interface NpcMetaFile {
  key?: string;
  portrait?: string;
}

const frontendRoot = process.cwd();
const repoRoot = join(frontendRoot, '..');
const npcRoot = join(repoRoot, 'data', 'npcs');
const portraitsRoot = join(frontendRoot, 'public', 'assets', 'pixel', 'portraits');

function headFileNameFor(portraitFileName: string): string {
  const stem = portraitFileName.replace(/\.png$/i, '');
  return stem.endsWith('_head') ? `${stem}.png` : `${stem}_head.png`;
}

describe('NPC portrait assets', () => {
  it('has a cropped head portrait for every NPC meta portrait', () => {
    const missing = readdirSync(npcRoot, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .flatMap((entry) => {
        const key = entry.name;
        const metaPath = join(npcRoot, key, 'meta.json');
        const meta = JSON.parse(readFileSync(metaPath, 'utf8')) as NpcMetaFile;
        const portraitFileName = (meta.portrait ?? `portrait_${meta.key ?? key}.png`).split('/').pop();
        if (!portraitFileName) return [`${key}: missing portrait filename`];

        const fullPath = join(portraitsRoot, portraitFileName);
        const headPath = join(portraitsRoot, 'heads', headFileNameFor(portraitFileName));
        const problems: string[] = [];
        if (!existsSync(fullPath)) problems.push(`${key}: missing ${portraitFileName}`);
        if (!existsSync(headPath)) problems.push(`${key}: missing heads/${headFileNameFor(portraitFileName)}`);
        return problems;
      });

    expect(missing).toEqual([]);
  });
});
