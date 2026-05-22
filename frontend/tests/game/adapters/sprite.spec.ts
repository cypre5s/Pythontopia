// 重构原因：sprite.ts 现在用 Phaser.Animations + Tweens，jsdom 下无法真正渲染。
//          本 spec 改为接口级断言：调用 createPlayerSprite 后，返回 handle 暴露的
//          tileX/tileY/direction 与初始入参一致，moveToTile 后会更新这些字段。
//          不再断言具体 Phaser API 调用细节。
import { describe, expect, it, vi } from 'vitest';
import { createPlayerSprite } from '@/game/adapters/sprite';
import type { SceneLike } from '@/game/adapters/sceneTypes';

function chainSprite() {
  const sprite = {
    x: 0,
    y: 0,
    setOrigin: vi.fn(() => sprite),
    setDepth: vi.fn(() => sprite),
    setScale: vi.fn(() => sprite),
    setFrame: vi.fn(() => sprite),
    play: vi.fn(() => sprite),
    stop: vi.fn(() => sprite),
  };
  return sprite;
}

function chainEllipse() {
  const ellipse = {
    x: 0,
    y: 0,
    setDepth: vi.fn(() => ellipse),
  };
  return ellipse;
}

function createSceneMock(): SceneLike {
  return {
    add: {
      sprite: vi.fn(() => chainSprite()),
      ellipse: vi.fn(() => chainEllipse()),
      image: vi.fn(() => chainSprite()),
      text: vi.fn(() => chainSprite()),
      rectangle: vi.fn(() => chainSprite()),
      tileSprite: vi.fn(() => chainSprite()),
      grid: vi.fn(() => chainSprite()),
    },
    anims: { exists: vi.fn(() => true), create: vi.fn() },
    tweens: { add: vi.fn(), killTweensOf: vi.fn() },
    input: { keyboard: null },
    time: { now: 0 },
    cameras: { main: { startFollow: vi.fn(), setBounds: vi.fn() } },
  } as unknown as SceneLike;
}

describe('createPlayerSprite', () => {
  it('returns a handle reflecting initial spawn tile and facing direction', () => {
    const scene = createSceneMock();

    const handle = createPlayerSprite(scene, { tileX: 2, tileY: 3, direction: 'DOWN', tileSize: 32 });

    expect(handle.tileX).toBe(2);
    expect(handle.tileY).toBe(3);
    expect(handle.direction).toBe('DOWN');
    // tile (2,3) 中心像素 (2*32 + 16, 3*32 + 16) = (80, 112)
    expect(handle.x).toBe(80);
    expect(handle.y).toBe(112);
  });

  it('moveToTile updates tile coords and facing direction on the handle', () => {
    const scene = createSceneMock();

    const handle = createPlayerSprite(scene, { tileX: 2, tileY: 3, direction: 'DOWN', tileSize: 32 });
    handle.moveToTile(4, 5, 'RIGHT');

    expect(handle.tileX).toBe(4);
    expect(handle.tileY).toBe(5);
    expect(handle.direction).toBe('RIGHT');
  });
});
