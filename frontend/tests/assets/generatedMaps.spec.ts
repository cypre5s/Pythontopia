import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, test } from 'vitest';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../../..');
const actualMapsDir = path.join(repoRoot, 'frontend', 'public', 'assets', 'maps');

function readComparableJson(filePath: string): unknown {
  const parsed = JSON.parse(readFileSync(filePath, 'utf-8')) as Record<string, unknown>;
  if (path.basename(filePath) === 'index.json') {
    return { ...parsed, generatedAt: '<ignored>' };
  }
  return parsed;
}

function hashComparableJson(filePath: string): string {
  return createHash('sha256').update(JSON.stringify(readComparableJson(filePath))).digest('hex');
}

describe('generated Tiled maps', () => {
  test('root map generator reproduces committed map artifacts', () => {
    const tempRoot = mkdtempSync(path.join(tmpdir(), 'pythontopia-maps-'));
    try {
      const tempScript = path.join(tempRoot, 'scripts', 'generate-maps.mjs');
      mkdirSync(path.dirname(tempScript), { recursive: true });
      cpSync(path.join(repoRoot, 'scripts', 'generate-maps.mjs'), tempScript);

      execFileSync('node', [tempScript], { stdio: 'pipe' });

      const generatedMapsDir = path.join(tempRoot, 'frontend', 'public', 'assets', 'maps');
      const files = [
        'town_hub.json',
        'farm_north.json',
        'mine_south.json',
        'campus_east.json',
        'port_west.json',
        'index.json',
      ];

      const mismatches = files.filter(
        (file) => hashComparableJson(path.join(generatedMapsDir, file)) !== hashComparableJson(path.join(actualMapsDir, file)),
      );

      expect(mismatches).toEqual([]);
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });
});
