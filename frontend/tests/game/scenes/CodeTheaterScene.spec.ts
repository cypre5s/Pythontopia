import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  CODE_THEATER_SCENE_KEY,
  CodeTheaterScene,
  __resetCodeTheaterForTests,
  ensureCodeTheaterSceneStarted,
  getCodeTheaterPlayer,
  loadCodeTheater,
  stopCodeTheaterScene,
  type CodeTheaterSceneContext,
} from '@/game/scenes/CodeTheaterScene';
import { makeTrace, SUM_LOOP_SOURCE, sumLoopCommands } from '../codeTheater/_fixtures';

type Spy = ReturnType<typeof vi.fn>;

function chain(methods: readonly string[]): Record<string, Spy> {
  const obj: Record<string, Spy> = {};
  for (const m of methods) {
    obj[m] = vi.fn(() => obj);
  }
  obj.destroy = vi.fn();
  return obj;
}

const GRAPHICS_METHODS = [
  'fillStyle',
  'fillCircle',
  'fillRoundedRect',
  'fillTriangle',
  'lineStyle',
  'lineBetween',
  'strokeCircle',
  'clear',
  'setDepth',
  'setPosition',
  'setAlpha',
] as const;
const TEXT_METHODS = ['setOrigin', 'setDepth', 'setColor', 'setText', 'setPosition'] as const;

interface FakeCtx extends CodeTheaterSceneContext {
  time: { now: number };
  _spies: {
    graphics: Spy;
    text: Spy;
    tweenAdd: Spy;
    removeAll: Spy;
    eventsOn: Spy;
    scaleOn: Spy;
  };
}

function makeCtx(width = 960, height = 640): FakeCtx {
  const graphics = vi.fn(() => chain(GRAPHICS_METHODS));
  const text = vi.fn(() => chain(TEXT_METHODS));
  const tweenAdd = vi.fn();
  const removeAll = vi.fn();
  const eventsOn = vi.fn();
  const scaleOn = vi.fn();
  const ctx = {
    add: {
      graphics,
      text: (x: number, y: number, str: string, style?: Record<string, unknown>) =>
        text(x, y, str, style),
    },
    tweens: { add: tweenAdd, killTweensOf: vi.fn() },
    scale: { width, height, on: scaleOn },
    time: { now: 0 },
    events: { on: eventsOn },
    children: { removeAll },
    _spies: { graphics, text, tweenAdd, removeAll, eventsOn, scaleOn },
  };
  return ctx as unknown as FakeCtx;
}

afterEach(() => {
  __resetCodeTheaterForTests();
});

describe('CodeTheaterScene · build', () => {
  it('builds the stage on create() without touching phaser directly', () => {
    loadCodeTheater(sumLoopCommands(), { trace: makeTrace(SUM_LOOP_SOURCE) });
    const ctx = makeCtx();

    expect(() => CodeTheaterScene.create.call(ctx)).not.toThrow();

    expect(ctx._spies.graphics).toHaveBeenCalled(); // 脚本梯/傀儡/牌/跑道/岔路/公告板
    expect(ctx._spies.text).toHaveBeenCalled();
    expect(ctx._spies.removeAll).toHaveBeenCalledWith(true);
    // 生命周期钩子已挂载。
    expect(ctx._spies.eventsOn).toHaveBeenCalledWith('shutdown', expect.any(Function));
    expect(ctx._spies.eventsOn).toHaveBeenCalledWith('destroy', expect.any(Function));
    expect(ctx._spies.scaleOn).toHaveBeenCalledWith('resize', expect.any(Function));
    expect(getCodeTheaterPlayer().getTotal()).toBe(9);
  });

  it('rebuilds the stage when new commands are loaded while mounted', () => {
    loadCodeTheater(sumLoopCommands(), { trace: makeTrace(SUM_LOOP_SOURCE) });
    const ctx = makeCtx();
    CodeTheaterScene.create.call(ctx);
    ctx._spies.removeAll.mockClear();

    loadCodeTheater(sumLoopCommands());
    expect(ctx._spies.removeAll).toHaveBeenCalledWith(true);
  });
});

describe('CodeTheaterScene · update loop drives the player', () => {
  it('advances playback frame by frame (respecting the per-frame delta clamp) and animates the puppet', () => {
    loadCodeTheater(sumLoopCommands(), { trace: makeTrace(SUM_LOOP_SOURCE) });
    const ctx = makeCtx();
    CodeTheaterScene.create.call(ctx);

    const player = getCodeTheaterPlayer();
    player.play();

    let now = 0;
    for (let i = 0; i < 60 && player.getStatus() !== 'ended'; i += 1) {
      now += 200;
      ctx.time.now = now;
      expect(() => CodeTheaterScene.update.call(ctx)).not.toThrow();
    }

    expect(player.getStatus()).toBe('ended');
    expect(player.getAppliedCount()).toBe(9);
    expect(player.getState().variables.total?.value).toBe('3');
    // 傀儡随发光行移动 → 用到 tween。
    expect(ctx._spies.tweenAdd).toHaveBeenCalled();
  });

  it('is a no-op-safe update before any stage is built', () => {
    __resetCodeTheaterForTests();
    const ctx = makeCtx();
    // 未 create，liveScene 为空：update 只推进播放器，不应抛错。
    expect(() => CodeTheaterScene.update.call(ctx)).not.toThrow();
  });
});

describe('CodeTheaterScene · singleton mounting', () => {
  it('registers + starts the scene via the existing game scene manager (no new Phaser.Game)', () => {
    const add = vi.fn();
    const bringToTop = vi.fn();
    ensureCodeTheaterSceneStarted({ scene: { add, bringToTop, keys: {} } });
    expect(add).toHaveBeenCalledWith(CODE_THEATER_SCENE_KEY, CodeTheaterScene, true);
    expect(bringToTop).toHaveBeenCalledWith(CODE_THEATER_SCENE_KEY);
  });

  it('starts (not re-adds) an already-registered scene', () => {
    const add = vi.fn();
    const start = vi.fn();
    ensureCodeTheaterSceneStarted({
      scene: { add, start, keys: { [CODE_THEATER_SCENE_KEY]: {} } },
    });
    expect(add).not.toHaveBeenCalled();
    expect(start).toHaveBeenCalledWith(CODE_THEATER_SCENE_KEY);
  });

  it('guards against stub/empty game handles', () => {
    expect(() => ensureCodeTheaterSceneStarted(null)).not.toThrow();
    expect(() => ensureCodeTheaterSceneStarted({})).not.toThrow();
    expect(() => stopCodeTheaterScene(null)).not.toThrow();

    const stop = vi.fn();
    stopCodeTheaterScene({ scene: { stop } });
    expect(stop).toHaveBeenCalledWith(CODE_THEATER_SCENE_KEY);
  });
});
