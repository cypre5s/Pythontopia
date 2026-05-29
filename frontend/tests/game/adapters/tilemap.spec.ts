// 重构原因：tilemap.ts 现在用 Phaser.Tilemaps API（make.tilemap + putTileAt），
//          jsdom 下无法真正渲染，本 spec 改为接口级断言：只验证 loadTownMap 返回的
//          数据结构是否符合预期（地图大小、出生点、buildings/npcs/collisions），不再
//          断言具体 Phaser 渲染调用。
import { describe, expect, it, vi } from 'vitest';
import { loadTownMap } from '@game/adapters/tilemap';
import type { ImageLike, RectangleLike, SceneLike, TextLike } from '@game/adapters/sceneTypes';

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

interface TilemapLayerMock {
  fill: ReturnType<typeof vi.fn>;
  setScale: ReturnType<typeof vi.fn>;
  setDepth: ReturnType<typeof vi.fn>;
  putTileAt: ReturnType<typeof vi.fn>;
}

function makeTilemapLayerMock(): TilemapLayerMock {
  const layer: TilemapLayerMock = {
    fill: vi.fn(() => layer),
    setScale: vi.fn(() => layer),
    setDepth: vi.fn(() => layer),
    putTileAt: vi.fn(() => layer),
  };
  return layer;
}

function createScene(): { scene: SceneLike; putTileCalls: { frame: number; tileX: number; tileY: number }[] } {
  const putTileCalls: { frame: number; tileX: number; tileY: number }[] = [];
  const tilemapStub = {
    addTilesetImage: vi.fn(() => ({})),
    createBlankLayer: vi.fn((name: string) => {
      const layer = makeTilemapLayerMock();
      layer.putTileAt.mockImplementation((frame: number, tileX: number, tileY: number) => {
        if (name === 'buildings') {
          putTileCalls.push({ frame, tileX, tileY });
        }
        return layer;
      });
      return layer;
    }),
  };

  const scene = {
    add: {
      image: vi.fn(() => chainImage()),
      tileSprite: vi.fn(() => chainImage()),
      rectangle: vi.fn(() => chainRectangle()),
      text: vi.fn(() => chainText()),
      grid: vi.fn(() => chainRectangle()),
      ellipse: vi.fn(() => chainRectangle()),
      sprite: vi.fn(() => chainImage()),
    },
    make: { tilemap: vi.fn(() => tilemapStub) },
    input: { keyboard: null },
    time: { now: 0 },
    cameras: { main: { startFollow: vi.fn(), setBounds: vi.fn() } },
    physics: { world: { setBounds: vi.fn() } },
    anims: { exists: vi.fn(() => true), create: vi.fn() },
    tweens: { add: vi.fn(), killTweensOf: vi.fn() },
  } as unknown as SceneLike;

  return { scene, putTileCalls };
}

describe('loadTownMap', () => {
  it('returns the canonical town map data with 30x20 grid', () => {
    const { scene } = createScene();

    const map = loadTownMap(scene, 'town_main');

    // R-12（commit e20cc7ad）星露谷改造：渲染 tile 由 32 改为 16（1:1 源 tile）。
    expect(map.tileSize).toBe(16);
    expect(map.width).toBe(30);
    expect(map.height).toBe(20);
    expect(map.spawn).toEqual({ tileX: 15, tileY: 11 });
  });

  it('seeds 6 buildings and 3 npcs by default', () => {
    const { scene } = createScene();

    const map = loadTownMap(scene, 'town_main');

    expect(map.buildings).toHaveLength(6);
    expect(map.buildings.map((b) => b.key)).toEqual(
      expect.arrayContaining(['profile-room', 'workshop', 'knowledge-field', 'board', 'training', 'teacher']),
    );
    expect(map.npcs).toHaveLength(3);
    expect(map.npcs.map((n) => n.npcKey)).toEqual(
      expect.arrayContaining(['mayor_py', 'librarian_loop', 'coach_debug']),
    );
  });

  it('builds a height x width collision grid marking building footprints as blocked', () => {
    const { scene } = createScene();

    const map = loadTownMap(scene, 'town_main');

    expect(map.collisions).toHaveLength(20);
    expect(map.collisions[0]).toHaveLength(30);
    const profileRoom = map.buildings.find((b) => b.key === 'profile-room');
    expect(profileRoom).toBeTruthy();
    if (profileRoom) {
      expect(map.collisions[profileRoom.tileY]?.[profileRoom.tileX]).toBe(true);
    }
    expect(map.collisions[map.spawn.tileY]?.[map.spawn.tileX]).toBe(false);
  });

  it('paints building tiles via the tilemap layer (frame >= 13)', () => {
    const { scene, putTileCalls } = createScene();

    loadTownMap(scene, 'town_main');

    expect(putTileCalls.length).toBeGreaterThan(0);
    expect(putTileCalls.every((call) => call.frame >= 13)).toBe(true);
  });
});
