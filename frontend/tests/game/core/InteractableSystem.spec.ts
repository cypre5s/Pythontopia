// W2 InteractableSystem 单测：覆盖 shapeContains 边界、enter/exit/activate 主链、
// 多 trigger 注册顺序、enabled=false 跳过、hintRenderer show/hide、detach 与 clear
// 副作用、同 id 重复 register 替换。jsdom 环境运行，不依赖真实 Phaser；scene 用
// 最小 stub 即可（系统只把 scene 透传给业务回调，自身不调任何 Phaser API）。
import type Phaser from 'phaser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  DEFAULT_HINT,
  InteractableSystem,
  shapeContains,
} from '@/game/core/InteractableSystem';
import type {
  HintRenderer,
  ITrigger,
  TriggerContext,
} from '@/game/core/InteractableSystem';

function makeScene(): Phaser.Scene {
  return {} as unknown as Phaser.Scene;
}

function makeHintRenderer(): {
  renderer: HintRenderer;
  show: ReturnType<typeof vi.fn>;
  hide: ReturnType<typeof vi.fn>;
} {
  const show = vi.fn();
  const hide = vi.fn();
  return {
    renderer: { show, hide },
    show,
    hide,
  };
}

describe('shapeContains', () => {
  it('rect: returns true on interior and boundary points', () => {
    const shape = { type: 'rect' as const, x: 0, y: 0, width: 10, height: 10 };
    expect(shapeContains(shape, 5, 5)).toBe(true);
    expect(shapeContains(shape, 0, 0)).toBe(true);
    expect(shapeContains(shape, 10, 10)).toBe(true);
    expect(shapeContains(shape, 0, 10)).toBe(true);
  });

  it('rect: returns false on points outside any edge', () => {
    const shape = { type: 'rect' as const, x: 0, y: 0, width: 10, height: 10 };
    expect(shapeContains(shape, 11, 5)).toBe(false);
    expect(shapeContains(shape, -1, 5)).toBe(false);
    expect(shapeContains(shape, 5, 11)).toBe(false);
    expect(shapeContains(shape, 5, -1)).toBe(false);
  });

  it('circle: distance <= radius is inside, > radius is outside', () => {
    const shape = { type: 'circle' as const, x: 0, y: 0, radius: 5 };
    expect(shapeContains(shape, 0, 0)).toBe(true);
    expect(shapeContains(shape, 3, 4)).toBe(true); // dist = 5 exactly
    expect(shapeContains(shape, 5, 0)).toBe(true);
    expect(shapeContains(shape, 3.001, 4)).toBe(false);
    expect(shapeContains(shape, 10, 0)).toBe(false);
  });

  it('circle: respects (cx, cy) offset', () => {
    const shape = { type: 'circle' as const, x: 100, y: 100, radius: 5 };
    expect(shapeContains(shape, 100, 100)).toBe(true);
    expect(shapeContains(shape, 103, 104)).toBe(true);
    expect(shapeContains(shape, 0, 0)).toBe(false);
  });
});

describe('InteractableSystem', () => {
  let scene: Phaser.Scene;

  beforeEach(() => {
    scene = makeScene();
  });

  it('register + update with player entering rect fires onEnter once (not on subsequent in-frame updates)', () => {
    const sys = new InteractableSystem(scene);
    const onEnter = vi.fn();
    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onEnter,
    });

    sys.update(-5, -5, false);
    expect(onEnter).not.toHaveBeenCalled();

    sys.update(5, 5, false);
    expect(onEnter).toHaveBeenCalledTimes(1);
    const ctx = onEnter.mock.calls[0]?.[0] as TriggerContext | undefined;
    expect(ctx?.trigger.id).toBe('t1');
    expect(ctx?.playerX).toBe(5);
    expect(ctx?.playerY).toBe(5);
    expect(ctx?.scene).toBe(scene);

    sys.update(6, 6, false);
    expect(onEnter).toHaveBeenCalledTimes(1);
  });

  it('leaving the rect fires onExit with current frame player coords', () => {
    const sys = new InteractableSystem(scene);
    const onExit = vi.fn();
    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onExit,
    });

    sys.update(5, 5, false);
    expect(onExit).not.toHaveBeenCalled();

    sys.update(20, 20, false);
    expect(onExit).toHaveBeenCalledTimes(1);
    const ctx = onExit.mock.calls[0]?.[0] as TriggerContext | undefined;
    expect(ctx?.trigger.id).toBe('t1');
    expect(ctx?.playerX).toBe(20);
    expect(ctx?.playerY).toBe(20);
  });

  it('in range + actionPressed=true triggers onActivate', () => {
    const sys = new InteractableSystem(scene);
    const onActivate = vi.fn();
    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onActivate,
    });
    sys.update(5, 5, true);
    expect(onActivate).toHaveBeenCalledTimes(1);
    const ctx = onActivate.mock.calls[0]?.[0] as TriggerContext | undefined;
    expect(ctx?.trigger.id).toBe('t1');
  });

  it('in range + actionPressed=false does NOT trigger onActivate', () => {
    const sys = new InteractableSystem(scene);
    const onActivate = vi.fn();
    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onActivate,
    });
    sys.update(5, 5, false);
    expect(onActivate).not.toHaveBeenCalled();
  });

  it('out-of-range + actionPressed=true does NOT trigger onActivate', () => {
    const sys = new InteractableSystem(scene);
    const onActivate = vi.fn();
    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onActivate,
    });
    sys.update(50, 50, true);
    expect(onActivate).not.toHaveBeenCalled();
  });

  it('circle shape uses distance <= radius for activation', () => {
    const sys = new InteractableSystem(scene);
    const onEnter = vi.fn();
    sys.register({
      id: 't1',
      shape: { type: 'circle', x: 0, y: 0, radius: 5 },
      onEnter,
    });

    sys.update(3, 4, false); // distance == 5
    expect(onEnter).toHaveBeenCalledTimes(1);
    expect(sys.getActive().map((t) => t.id)).toEqual(['t1']);

    sys.update(10, 10, false);
    expect(sys.getActive()).toEqual([]);
  });

  it('multiple simultaneously-active triggers fire onActivate in registration order', () => {
    const sys = new InteractableSystem(scene);
    const order: string[] = [];
    const mkTrigger = (id: string): ITrigger => ({
      id,
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onActivate: () => order.push(id),
    });
    sys.register(mkTrigger('alpha'));
    sys.register(mkTrigger('bravo'));
    sys.register(mkTrigger('charlie'));

    sys.update(5, 5, true);
    expect(order).toEqual(['alpha', 'bravo', 'charlie']);
  });

  it('trigger with enabled=false is skipped for enter / activate / getActive', () => {
    const sys = new InteractableSystem(scene);
    const onEnter = vi.fn();
    const onActivate = vi.fn();
    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onEnter,
      onActivate,
      enabled: false,
    });

    sys.update(5, 5, true);

    expect(onEnter).not.toHaveBeenCalled();
    expect(onActivate).not.toHaveBeenCalled();
    expect(sys.getActive()).toEqual([]);
  });

  it('hintRenderer.show fires with trigger.hint while active, hide fires after leaving', () => {
    const { renderer, show, hide } = makeHintRenderer();
    const sys = new InteractableSystem(scene, { hintRenderer: renderer });
    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      hint: '按 E 浇水',
    });

    sys.update(5, 5, false);
    expect(show).toHaveBeenCalledWith('按 E 浇水');

    sys.update(50, 50, false);
    expect(hide).toHaveBeenCalled();
  });

  it('hintRenderer falls back to DEFAULT_HINT when trigger.hint is omitted', () => {
    const { renderer, show } = makeHintRenderer();
    const sys = new InteractableSystem(scene, { hintRenderer: renderer });
    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
    });

    sys.update(5, 5, false);
    expect(show).toHaveBeenCalledWith(DEFAULT_HINT);
  });

  it('hintRenderer.show uses first registered active trigger when multiple are active', () => {
    const { renderer, show } = makeHintRenderer();
    const sys = new InteractableSystem(scene, { hintRenderer: renderer });
    sys.register({
      id: 'first',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      hint: '提示 A',
    });
    sys.register({
      id: 'second',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      hint: '提示 B',
    });

    sys.update(5, 5, false);
    expect(show).toHaveBeenLastCalledWith('提示 A');
  });

  it('detach returned from register removes the trigger; fires onExit if currently active', () => {
    const sys = new InteractableSystem(scene);
    const onExit = vi.fn();
    const detach = sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onExit,
    });

    sys.update(5, 5, false);
    expect(sys.getActive()).toHaveLength(1);

    detach();
    expect(onExit).toHaveBeenCalledTimes(1);
    expect(sys.getActive()).toEqual([]);

    sys.update(5, 5, true);
    expect(onExit).toHaveBeenCalledTimes(1);
  });

  it('detach when trigger not active does NOT fire onExit', () => {
    const sys = new InteractableSystem(scene);
    const onExit = vi.fn();
    const detach = sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onExit,
    });
    sys.update(50, 50, false); // not inside
    detach();
    expect(onExit).not.toHaveBeenCalled();
  });

  it('detach is idempotent — calling twice does not double-fire onExit or break state', () => {
    const sys = new InteractableSystem(scene);
    const onExit = vi.fn();
    const detach = sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onExit,
    });
    sys.update(5, 5, false);
    detach();
    detach();
    expect(onExit).toHaveBeenCalledTimes(1);
  });

  it('clear fires onExit on all active triggers and empties the registry', () => {
    const sys = new InteractableSystem(scene);
    const onExitA = vi.fn();
    const onExitB = vi.fn();
    sys.register({
      id: 'a',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onExit: onExitA,
    });
    sys.register({
      id: 'b',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onExit: onExitB,
    });

    sys.update(5, 5, false);
    expect(sys.getActive()).toHaveLength(2);

    sys.clear();
    expect(onExitA).toHaveBeenCalledTimes(1);
    expect(onExitB).toHaveBeenCalledTimes(1);
    expect(sys.getActive()).toEqual([]);

    sys.update(5, 5, true);
    expect(onExitA).toHaveBeenCalledTimes(1);
    expect(onExitB).toHaveBeenCalledTimes(1);
  });

  it('clear hides the hint if a hintRenderer is attached', () => {
    const { renderer, hide } = makeHintRenderer();
    const sys = new InteractableSystem(scene, { hintRenderer: renderer });
    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
    });
    sys.update(5, 5, false);
    sys.clear();
    expect(hide).toHaveBeenCalled();
  });

  it('re-registering same id replaces the trigger and logs a warning', () => {
    const logger = vi.fn();
    const sys = new InteractableSystem(scene, { logger });
    const onActivateOld = vi.fn();
    const onActivateNew = vi.fn();
    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onActivate: onActivateOld,
    });
    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 100, y: 100, width: 10, height: 10 },
      onActivate: onActivateNew,
    });

    expect(logger).toHaveBeenCalledWith(expect.stringContaining('already registered'));

    sys.update(105, 105, true);
    expect(onActivateOld).not.toHaveBeenCalled();
    expect(onActivateNew).toHaveBeenCalledTimes(1);

    sys.update(5, 5, true);
    expect(onActivateNew).toHaveBeenCalledTimes(1);
  });

  it('re-registering same id calls old.onExit first when the old trigger was active', () => {
    const sys = new InteractableSystem(scene);
    const onExitOld = vi.fn();
    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
      onExit: onExitOld,
    });
    sys.update(5, 5, false);
    expect(sys.getActive()).toHaveLength(1);

    sys.register({
      id: 't1',
      shape: { type: 'rect', x: 100, y: 100, width: 10, height: 10 },
    });

    expect(onExitOld).toHaveBeenCalledTimes(1);
    expect(sys.getActive()).toEqual([]);
  });

  it('getActive returns triggers in registration order, filtered by active set', () => {
    const sys = new InteractableSystem(scene);
    sys.register({
      id: 'b',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
    });
    sys.register({
      id: 'a',
      shape: { type: 'rect', x: 0, y: 0, width: 10, height: 10 },
    });
    sys.register({
      id: 'c-outside',
      shape: { type: 'rect', x: 100, y: 100, width: 10, height: 10 },
    });

    sys.update(5, 5, false);
    expect(sys.getActive().map((t) => t.id)).toEqual(['b', 'a']);
  });
});
