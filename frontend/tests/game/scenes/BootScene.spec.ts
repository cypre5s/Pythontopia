import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it, vi } from 'vitest';
import { BootScene } from '@/game/scenes/BootScene';

describe('BootScene asset manifest', () => {
  it('loads only existing public audio assets', () => {
    const audioUrls: string[] = [];
    const loader = {
      on: vi.fn(),
      image: vi.fn(),
      spritesheet: vi.fn(),
      audio: vi.fn((_key: string, url: string | string[]) => {
        if (Array.isArray(url)) {
          audioUrls.push(...url);
          return;
        }
        audioUrls.push(url);
      }),
    };

    BootScene.preload.call({ load: loader });

    expect(audioUrls.length).toBeGreaterThan(0);
    for (const url of audioUrls) {
      expect(existsSync(join(process.cwd(), 'public', url))).toBe(true);
    }
  });
});
