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

  test('committed maps include low_obstacle objects for jumpable terrain', () => {
    const mapFiles = ['town_hub.json', 'farm_north.json', 'campus_east.json', 'port_west.json'];
    const counts = mapFiles.map((file) => {
      const map = JSON.parse(readFileSync(path.join(actualMapsDir, file), 'utf-8')) as {
        layers: Array<{ type: string; objects?: Array<{ type?: string }> }>;
      };
      return map.layers
        .filter((layer) => layer.type === 'objectgroup')
        .flatMap((layer) => layer.objects ?? [])
        .filter((object) => object.type === 'low_obstacle').length;
    });

    expect(counts.every((count) => count > 0)).toBe(true);
  });

  test('jumpable low obstacles use dedicated fence/stump sprite metadata and form a hub path', () => {
    const town = JSON.parse(readFileSync(path.join(actualMapsDir, 'town_hub.json'), 'utf-8')) as {
      layers: Array<{
        type: string;
        objects?: Array<{
          name?: string;
          type?: string;
          properties?: Array<{ name?: string; value?: unknown }>;
        }>;
      }>;
    };
    const lowObstacles = town.layers
      .filter((layer) => layer.type === 'objectgroup')
      .flatMap((layer) => layer.objects ?? [])
      .filter((object) => object.type === 'low_obstacle');

    const propsFor = (object: (typeof lowObstacles)[number]): Record<string, unknown> =>
      Object.fromEntries((object.properties ?? []).map((prop) => [String(prop.name), prop.value]));

    expect(lowObstacles.filter((object) => object.name?.startsWith('low_path_fence_')).length).toBeGreaterThanOrEqual(8);
    expect(
      lowObstacles.every((object) => {
        const props = propsFor(object);
        return props['sprite'] === 'low_fence_16' && typeof props['frame'] === 'number';
      }),
    ).toBe(true);
  });
});
