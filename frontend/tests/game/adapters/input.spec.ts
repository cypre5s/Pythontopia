import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  computeDirection8,
  createCompositeAdapter,
  createDirectionController,
  emptyIntent,
  getInputAdapter,
  hasAnyInput,
  setInputAdapterForTest,
  type InputAdapter,
  type InputIntent,
} from '@/game/adapters/input';
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
    expect(scene.input.keyboard?.addKey).toHaveBeenCalledWith('E');
    expect(controller.isActionPressed()).toBe(true);
  });

  it('uses Space for jumping without making Space an interaction key', () => {
    const keys = {
      W: key(),
      A: key(),
      S: key(),
      D: key(),
      F: key(),
      E: key(),
      SPACE: key(true),
    };
    const scene = sceneWithKeys(keys, {
      up: key(),
      down: key(),
      left: key(),
      right: key(),
    });

    const controller = createDirectionController(scene);

    expect(controller.isJumpPressed()).toBe(true);
    expect(controller.isActionPressed()).toBe(false);
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

describe('computeDirection8', () => {
  it('returns E for pure positive x axis', () => {
    expect(computeDirection8(1, 0)).toBe('E');
  });

  it('returns NW for negative x and negative y of equal magnitude', () => {
    expect(computeDirection8(-0.5, -0.5)).toBe('NW');
  });

  it('returns null when both axes are zero', () => {
    expect(computeDirection8(0, 0)).toBeNull();
  });

  it('discretises all 8 cardinal/intercardinal directions', () => {
    // 屏幕坐标系：+y 表示向下 (S)，-y 表示向上 (N)。
    expect(computeDirection8(0, -1)).toBe('N');
    expect(computeDirection8(1, -1)).toBe('NE');
    expect(computeDirection8(1, 1)).toBe('SE');
    expect(computeDirection8(0, 1)).toBe('S');
    expect(computeDirection8(-1, 1)).toBe('SW');
    expect(computeDirection8(-1, 0)).toBe('W');
    expect(computeDirection8(-1, -1)).toBe('NW');
  });

  it('keeps E sector around small dy noise', () => {
    expect(computeDirection8(1, 0.1)).toBe('E');
    expect(computeDirection8(1, -0.1)).toBe('E');
  });
});

describe('hasAnyInput / emptyIntent', () => {
  it('treats emptyIntent as no input', () => {
    expect(hasAnyInput(emptyIntent())).toBe(false);
  });

  it('detects move, run, action, cancel as input', () => {
    expect(hasAnyInput({ ...emptyIntent(), move: { dx: 0.5, dy: 0 } })).toBe(true);
    expect(hasAnyInput({ ...emptyIntent(), run: true })).toBe(true);
    expect(hasAnyInput({ ...emptyIntent(), actionPressed: true })).toBe(true);
    expect(hasAnyInput({ ...emptyIntent(), cancelPressed: true })).toBe(true);
  });
});

describe('createCompositeAdapter priority gamepad > keyboard > touch', () => {
  function makeMockAdapter(intent: InputIntent): InputAdapter {
    return {
      poll: vi.fn(() => intent),
      attach: vi.fn(),
      detach: vi.fn(),
    };
  }

  it('returns gamepad intent when gamepad has any input', () => {
    const gamepadIntent: InputIntent = {
      move: { dx: 0.6, dy: 0 },
      direction: 'E',
      run: false,
      actionPressed: false,
      cancelPressed: false,
    };
    const keyboardIntent: InputIntent = {
      move: { dx: -1, dy: 0 },
      direction: 'W',
      run: false,
      actionPressed: false,
      cancelPressed: false,
    };
    const touchIntent: InputIntent = {
      move: { dx: 0, dy: 0.5 },
      direction: 'S',
      run: false,
      actionPressed: false,
      cancelPressed: false,
    };
    const composite = createCompositeAdapter(
      makeMockAdapter(keyboardIntent),
      makeMockAdapter(touchIntent),
      makeMockAdapter(gamepadIntent),
    );
    expect(composite.poll()).toEqual(gamepadIntent);
  });

  it('falls back to keyboard when gamepad has no input', () => {
    const keyboardIntent: InputIntent = {
      move: { dx: 1, dy: 0 },
      direction: 'E',
      run: true,
      actionPressed: false,
      cancelPressed: false,
    };
    const composite = createCompositeAdapter(
      makeMockAdapter(keyboardIntent),
      makeMockAdapter({
        move: { dx: 0, dy: 0.3 },
        direction: 'S',
        run: false,
        actionPressed: false,
        cancelPressed: false,
      }),
      makeMockAdapter(emptyIntent()),
    );
    expect(composite.poll()).toEqual(keyboardIntent);
  });

  it('falls back to touch only when gamepad and keyboard are idle', () => {
    const touchIntent: InputIntent = {
      move: { dx: 0, dy: 0.4 },
      direction: 'S',
      run: false,
      actionPressed: false,
      cancelPressed: false,
    };
    const composite = createCompositeAdapter(
      makeMockAdapter(emptyIntent()),
      makeMockAdapter(touchIntent),
      makeMockAdapter(emptyIntent()),
    );
    expect(composite.poll()).toEqual(touchIntent);
  });

  it('returns empty intent when nothing is active', () => {
    const composite = createCompositeAdapter(
      makeMockAdapter(emptyIntent()),
      makeMockAdapter(emptyIntent()),
      makeMockAdapter(emptyIntent()),
    );
    expect(composite.poll()).toEqual(emptyIntent());
  });

  it('forwards attach/detach to every sub adapter', () => {
    const kb = makeMockAdapter(emptyIntent());
    const tc = makeMockAdapter(emptyIntent());
    const gp = makeMockAdapter(emptyIntent());
    const composite = createCompositeAdapter(kb, tc, gp);
    const fakeScene = {} as unknown as Parameters<InputAdapter['attach']>[0];
    composite.attach(fakeScene);
    expect(kb.attach).toHaveBeenCalledWith(fakeScene);
    expect(tc.attach).toHaveBeenCalledWith(fakeScene);
    expect(gp.attach).toHaveBeenCalledWith(fakeScene);
    composite.detach();
    expect(kb.detach).toHaveBeenCalledTimes(1);
    expect(tc.detach).toHaveBeenCalledTimes(1);
    expect(gp.detach).toHaveBeenCalledTimes(1);
  });

  it('always polls every sub adapter even when gamepad wins, so edges stay in sync', () => {
    const kbPoll = vi.fn(() => emptyIntent());
    const tcPoll = vi.fn(() => emptyIntent());
    const gpPoll = vi.fn(() => ({
      ...emptyIntent(),
      actionPressed: true,
    }));
    const composite = createCompositeAdapter(
      { poll: kbPoll, attach: vi.fn(), detach: vi.fn() },
      { poll: tcPoll, attach: vi.fn(), detach: vi.fn() },
      { poll: gpPoll, attach: vi.fn(), detach: vi.fn() },
    );
    composite.poll();
    expect(kbPoll).toHaveBeenCalledTimes(1);
    expect(tcPoll).toHaveBeenCalledTimes(1);
    expect(gpPoll).toHaveBeenCalledTimes(1);
  });
});

describe('getInputAdapter / setInputAdapterForTest', () => {
  afterEach(() => {
    setInputAdapterForTest(null);
  });

  it('returns the injected adapter when set for test', () => {
    const mock: InputAdapter = {
      poll: vi.fn(() => emptyIntent()),
      attach: vi.fn(),
      detach: vi.fn(),
    };
    setInputAdapterForTest(mock);
    expect(getInputAdapter()).toBe(mock);
  });

  it('builds a fresh default adapter after reset', () => {
    const first = getInputAdapter();
    setInputAdapterForTest(null);
    const second = getInputAdapter();
    expect(typeof first.poll).toBe('function');
    expect(typeof second.poll).toBe('function');
    // After reset, the singleton is rebuilt, so identity differs.
    expect(first).not.toBe(second);
  });
});
