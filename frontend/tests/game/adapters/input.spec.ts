import { describe, expect, it, vi } from 'vitest';
import { createDirectionController } from '@/game/adapters/input';
import type { KeyboardCursorLike, KeyboardKeyLike, SceneLike } from '@/game/adapters/sceneTypes';

function key(isDown = false): KeyboardKeyLike {
  return { isDown };
}

function sceneWithKeys(keys: Record<string, KeyboardKeyLike>, cursors: KeyboardCursorLike): SceneLike {
  return {
    input: {
      keyboard: {
        createCursorKeys: vi.fn(() => cursors),
        addKey: vi.fn((name: string) => keys[name] ?? key()),
      },
    },
  } as unknown as SceneLike;
}

describe('createDirectionController', () => {
  it('uses Genshin-like keyboard controls with F as interact', () => {
    const keys = {
      W: key(),
      A: key(),
      S: key(),
      D: key(),
      F: key(true),
    };
    const scene = sceneWithKeys(keys, {
      up: key(),
      down: key(),
      left: key(),
      right: key(),
    });

    const controller = createDirectionController(scene);

    expect(scene.input.keyboard?.addKey).toHaveBeenCalledWith('W');
    expect(scene.input.keyboard?.addKey).toHaveBeenCalledWith('A');
    expect(scene.input.keyboard?.addKey).toHaveBeenCalledWith('S');
    expect(scene.input.keyboard?.addKey).toHaveBeenCalledWith('D');
    expect(scene.input.keyboard?.addKey).toHaveBeenCalledWith('F');
    expect(controller.isActionPressed()).toBe(true);
  });

  it('returns eight-direction movement from simultaneous keys', () => {
    const keys = {
      W: key(true),
      A: key(),
      S: key(),
      D: key(true),
      F: key(),
    };
    const scene = sceneWithKeys(keys, {
      up: key(),
      down: key(),
      left: key(),
      right: key(),
    });

    expect(createDirectionController(scene).getDirection()).toBe('UP_RIGHT');
  });

  it('keeps arrow keys as movement aliases', () => {
    const keys = {
      W: key(),
      A: key(),
      S: key(),
      D: key(),
      F: key(),
    };
    const scene = sceneWithKeys(keys, {
      up: key(),
      down: key(true),
      left: key(true),
      right: key(),
    });

    expect(createDirectionController(scene).getDirection()).toBe('DOWN_LEFT');
  });
});
