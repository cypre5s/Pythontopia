import { describe, expect, it, vi } from 'vitest';
import { createPlayerSprite } from '@/game/adapters/sprite';
import type { ImageLike, SceneLike } from '@/game/adapters/sceneTypes';

function imageMock(): ImageLike {
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

describe('createPlayerSprite', () => {
  it('uses LPC Sara spritesheet with idle frame and walks by direction', () => {
    const image = imageMock();
    const addImage = vi.fn(() => image);
    const scene = { add: { image: addImage } } as unknown as SceneLike;

    const player = createPlayerSprite(scene, { tileX: 2, tileY: 3, direction: 'DOWN', tileSize: 32 });

    expect(addImage).toHaveBeenCalledWith(80, 112, 'player_sara', 130);
    expect(image.setScale).toHaveBeenCalledWith(0.85);
    expect(image.setDepth).toHaveBeenCalledWith(20);

    player.moveToTile(4, 5, 'RIGHT');
    expect(player.tileX).toBe(4);
    expect(player.tileY).toBe(5);
    expect(player.direction).toBe('RIGHT');
    expect(image.x).toBe(144);
    expect(image.y).toBe(176);
    expect(image.setFrame).toHaveBeenCalledWith(144);
  });

  it('falls back to student_default when LPC sheet load throws', () => {
    const image = imageMock();
    let callCount = 0;
    const addImage = vi.fn(() => {
      callCount += 1;
      if (callCount === 1) throw new Error('LPC sheet not loaded');
      return image;
    });
    const scene = { add: { image: addImage } } as unknown as SceneLike;

    const player = createPlayerSprite(scene, { tileX: 2, tileY: 3, direction: 'DOWN', tileSize: 32 });
    expect(addImage).toHaveBeenCalledTimes(2);
    expect(addImage).toHaveBeenLastCalledWith(80, 112, 'student_default');
    expect(image.setScale).toHaveBeenCalledWith(1.35);
    expect(player.tileX).toBe(2);
  });
});
