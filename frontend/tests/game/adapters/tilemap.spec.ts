import { describe, expect, it, vi } from 'vitest';
import { loadTownMap } from '@game/adapters/tilemap';
import type { ImageLike, RectangleLike, SceneLike, TextLike } from '@game/adapters/sceneTypes';

interface AddCall {
  readonly x: number;
  readonly y: number;
  readonly width?: number;
  readonly height?: number;
  readonly texture?: string;
  readonly frame?: number | string;
  readonly fillColor?: number;
}

function chainImage(): ImageLike {
  return {
    x: 0,
    y: 0,
    setOrigin: vi.fn(function setOrigin(this: ImageLike) {
      return this;
    }),
    setDepth: vi.fn(function setDepth(this: ImageLike) {
      return this;
    }),
    setScale: vi.fn(function setScale(this: ImageLike) {
      return this;
    }),
    setAlpha: vi.fn(function setAlpha(this: ImageLike) {
      return this;
    }),
    setFrame: vi.fn(function setFrame(this: ImageLike) {
      return this;
    }),
  };
}

function chainRectangle(): RectangleLike {
  return {
    x: 0,
    y: 0,
    setStrokeStyle: vi.fn(function setStrokeStyle(this: RectangleLike) {
      return this;
    }),
    setInteractive: vi.fn(function setInteractive(this: RectangleLike) {
      return this;
    }),
    on: vi.fn(function on(this: RectangleLike) {
      return this;
    }),
    setDepth: vi.fn(function setDepth(this: RectangleLike) {
      return this;
    }),
  };
}

function chainText(): TextLike {
  return {
    setOrigin: vi.fn(function setOrigin(this: TextLike) {
      return this;
    }),
    setDepth: vi.fn(function setDepth(this: TextLike) {
      return this;
    }),
  };
}

function createScene(): {
  readonly scene: SceneLike;
  readonly imageCalls: AddCall[];
  readonly rectangleCalls: AddCall[];
  readonly tileSpriteCalls: AddCall[];
  readonly gridCalls: AddCall[];
} {
  const imageCalls: AddCall[] = [];
  const rectangleCalls: AddCall[] = [];
  const tileSpriteCalls: AddCall[] = [];
  const gridCalls: AddCall[] = [];

  const scene = {
    add: {
      image(x: number, y: number, texture: string, frame?: number | string): ImageLike {
        imageCalls.push({ x, y, texture, frame });
        return chainImage();
      },
      tileSprite(
        x: number,
        y: number,
        width: number,
        height: number,
        texture: string,
        frame?: number | string,
      ): ImageLike {
        tileSpriteCalls.push({ x, y, width, height, texture, frame });
        return chainImage();
      },
      rectangle(x: number, y: number, width: number, height: number, fillColor: number): RectangleLike {
        rectangleCalls.push({ x, y, width, height, fillColor });
        return chainRectangle();
      },
      text(): TextLike {
        return chainText();
      },
      grid(x: number, y: number, width: number, height: number): RectangleLike {
        gridCalls.push({ x, y, width, height });
        return chainRectangle();
      },
    },
    input: { keyboard: null },
    time: { now: 0 },
    cameras: { main: { startFollow() {}, setBounds: vi.fn() } },
    physics: { world: { setBounds: vi.fn() } },
  } satisfies SceneLike;

  return { scene, imageCalls, rectangleCalls, tileSpriteCalls, gridCalls };
}

describe('loadTownMap', () => {
  it('renders the default town ground with kenney tiny-town frame tiles', () => {
    const { scene, imageCalls, tileSpriteCalls, gridCalls } = createScene();

    const map = loadTownMap(scene, 'town_main');
    const groundTiles = imageCalls.filter((call) => call.texture === 'kenney_tiny_town' && call.frame === 0);

    expect(groundTiles).toHaveLength(map.width * map.height);
    expect(groundTiles[0]).toMatchObject({ x: 16, y: 16, frame: 0 });
    expect(groundTiles.at(-1)).toMatchObject({ x: 944, y: 624, frame: 0 });
    expect(tileSpriteCalls).toHaveLength(0);
    expect(gridCalls).toHaveLength(0);
  });

  it('builds town buildings from kenney tiny-town frames instead of rectangle blocks', () => {
    const { scene, imageCalls, rectangleCalls } = createScene();

    loadTownMap(scene, 'town_main');
    const buildingTiles = imageCalls.filter(
      (call) => call.texture === 'kenney_tiny_town' && typeof call.frame === 'number' && call.frame >= 13,
    );

    expect(buildingTiles.length).toBeGreaterThan(0);
    expect(buildingTiles.some((call) => call.frame === 13)).toBe(true);
    expect(buildingTiles.some((call) => call.frame === 24)).toBe(true);
    expect(rectangleCalls).toHaveLength(3);
  });

  it('keeps the legacy SVG-like rectangle rendering behind an explicit fallback', () => {
    const { scene, imageCalls, rectangleCalls, tileSpriteCalls, gridCalls } = createScene();

    const map = loadTownMap(scene, 'town_main', false);

    expect(tileSpriteCalls).toHaveLength(1);
    expect(gridCalls).toHaveLength(1);
    expect(rectangleCalls.length).toBeGreaterThan(map.npcs.length);
    expect(imageCalls.some((call) => call.texture === 'kenney_tiny_town')).toBe(false);
    expect(map.spawn).toEqual({ tileX: 15, tileY: 11 });
    expect(map.buildings).toHaveLength(6);
    expect(map.npcs).toHaveLength(3);
  });
});
