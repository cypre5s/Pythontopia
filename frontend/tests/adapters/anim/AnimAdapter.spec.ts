// 测试取舍：本 spec 是 F1-anim-adapter 的完整测试矩阵（§4 F 测试矩阵 ①②③④⑤ + §8.4 无障碍）。
// - 三方动画库（motion / @rive-app/canvas）全部用 vi.mock 隔离，断言「不实际加载」只走我方
//   adapter 暴露的稳定接口（③）。
// - Phaser tween 用结构化假 scene（不引真实 phaser，沿用 weather.spec 的做法）。
// - reduceMotion 下三个后端一律退化为「瞬时」（①）；playStepIn/Done/celebrate 路由正确（②）；
//   接口契约稳定（⑤）；无 any 由 typecheck 兜底（④）。
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { createAnimAdapter } from '@game/adapters/anim/AnimAdapter';
import { createDomMotionBackend } from '@game/adapters/anim/domMotion';
import { createRiveMascot } from '@game/adapters/anim/riveMascot';
import { createPhaserCanvasFx } from '@game/adapters/anim/phaserTween';
import type {
  CanvasFxBackend,
  DomMotionBackend,
  MascotBackend,
} from '@game/adapters/anim/types';

// ── motion 隔离 mock：只暴露生产代码用到的 animate（值导入），证明三方库不真正加载 ──
const motionMock = vi.hoisted(() => ({ animate: vi.fn() }));
vi.mock('motion', () => ({ animate: motionMock.animate }));

interface MotionControlsStub {
  finished: Promise<unknown>;
  stop: Mock;
  complete: Mock;
  cancel: Mock;
  play: Mock;
  pause: Mock;
}

function makeControls(finished: Promise<unknown> = Promise.resolve()): MotionControlsStub {
  return { finished, stop: vi.fn(), complete: vi.fn(), cancel: vi.fn(), play: vi.fn(), pause: vi.fn() };
}

// ── @rive-app/canvas 隔离 mock：MockRive 记录构造参数 + 暴露最小播放控制 ──
interface RiveInputStub {
  name: string;
  value: number | boolean;
  fire: Mock;
}
interface RiveInstanceStub {
  params: Record<string, unknown>;
  play: Mock;
  pause: Mock;
  stop: Mock;
  cleanup: Mock;
  stateMachineInputs: Mock;
}
const riveMock = vi.hoisted(() => ({
  instances: [] as Array<{
    params: Record<string, unknown>;
    play: Mock;
    pause: Mock;
    stop: Mock;
    cleanup: Mock;
    stateMachineInputs: Mock;
  }>,
  inputs: [] as Array<{ name: string; value: number | boolean; fire: Mock }>,
  throwOnInputs: false,
}));

vi.mock('@rive-app/canvas', () => {
  class MockRive {
    play = vi.fn();
    pause = vi.fn();
    stop = vi.fn();
    cleanup = vi.fn();
    stateMachineInputs = vi.fn((_name: string) => {
      if (riveMock.throwOnInputs) {
        throw new Error('rive not ready');
      }
      return riveMock.inputs;
    });

    constructor(params: Record<string, unknown>) {
      riveMock.instances.push({
        params,
        play: this.play,
        pause: this.pause,
        stop: this.stop,
        cleanup: this.cleanup,
        stateMachineInputs: this.stateMachineInputs,
      });
      const onLoad = params.onLoad;
      if (typeof onLoad === 'function') {
        (onLoad as () => void)();
      }
    }
  }
  return { Rive: MockRive };
});

function lastRive(): RiveInstanceStub {
  const inst = riveMock.instances[riveMock.instances.length - 1];
  if (!inst) {
    throw new Error('no Rive instance constructed');
  }
  return inst;
}

function makeInput(name: string, value: number | boolean = 0): RiveInputStub {
  return { name, value, fire: vi.fn() };
}

beforeEach(() => {
  motionMock.animate.mockReset();
  motionMock.animate.mockReturnValue(makeControls());
  riveMock.instances.length = 0;
  riveMock.inputs.length = 0;
  riveMock.throwOnInputs = false;
});

afterEach(() => {
  vi.restoreAllMocks();
});

// ── 假后端（adapter 契约级测试用注入，纯接口、零三方依赖） ──
interface FakeDom extends DomMotionBackend {
  stepIn: Mock;
  stepDone: Mock;
}
function fakeDom(): FakeDom {
  return {
    stepIn: vi.fn(() => Promise.resolve()),
    stepDone: vi.fn(() => Promise.resolve()),
  };
}
interface FakeMascot extends MascotBackend {
  setExpression: Mock;
  celebrate: Mock;
  dispose: Mock;
}
function fakeMascot(): FakeMascot {
  return {
    setExpression: vi.fn(),
    celebrate: vi.fn(() => Promise.resolve()),
    dispose: vi.fn(),
  };
}
interface FakeCanvasFx extends CanvasFxBackend {
  celebrate: Mock;
  dispose: Mock;
}
function fakeCanvasFx(): FakeCanvasFx {
  return {
    celebrate: vi.fn(() => Promise.resolve()),
    dispose: vi.fn(),
  };
}

describe('createAnimAdapter · 契约与 reduce-motion 路由', () => {
  it('暴露稳定接口的全部成员（⑤ 接口契约稳定）', () => {
    const adapter = createAnimAdapter({ dom: fakeDom(), prefersReducedMotion: () => false });
    expect(typeof adapter.playStepIn).toBe('function');
    expect(typeof adapter.playStepDone).toBe('function');
    expect(typeof adapter.celebrate).toBe('function');
    expect(typeof adapter.dispose).toBe('function');
    expect(typeof adapter.reduceMotion).toBe('boolean');
    expect(adapter.playStepIn(document.createElement('div'))).toBeInstanceOf(Promise);
  });

  it('reduceMotion=false：playStepIn 以 instant:false 调 dom.stepIn 并透传 opts（②）', async () => {
    const dom = fakeDom();
    const adapter = createAnimAdapter({ dom, prefersReducedMotion: () => false });
    const el = document.createElement('div');
    const signal = new AbortController().signal;

    await adapter.playStepIn(el, { durationMs: 200, signal });

    expect(dom.stepIn).toHaveBeenCalledTimes(1);
    expect(dom.stepIn).toHaveBeenCalledWith(el, { durationMs: 200, signal, instant: false });
  });

  it('reduceMotion=true：playStepIn 以 instant:true 调 dom.stepIn（① 退化为瞬时）', async () => {
    const dom = fakeDom();
    const adapter = createAnimAdapter({ dom, prefersReducedMotion: () => true });
    const el = document.createElement('div');

    await adapter.playStepIn(el);

    expect(dom.stepIn).toHaveBeenCalledWith(el, { instant: true });
  });

  it('playStepDone 透传 outcome + instant（②/①）', async () => {
    const dom = fakeDom();
    const adapter = createAnimAdapter({ dom, prefersReducedMotion: () => true });
    const el = document.createElement('div');

    await adapter.playStepDone(el, 'failed', { durationMs: 120 });

    expect(dom.stepDone).toHaveBeenCalledWith(el, 'failed', { durationMs: 120, instant: true });
  });

  it('celebrate(both)：默认 expression=celebrate 调 mascot.celebrate + canvasFx.celebrate（②）', async () => {
    const mascot = fakeMascot();
    const canvasFx = fakeCanvasFx();
    const adapter = createAnimAdapter({ dom: fakeDom(), mascot, canvasFx, prefersReducedMotion: () => false });

    await adapter.celebrate();

    expect(mascot.celebrate).toHaveBeenCalledTimes(1);
    expect(mascot.celebrate).toHaveBeenCalledWith(expect.objectContaining({ instant: false }));
    expect(canvasFx.celebrate).toHaveBeenCalledTimes(1);
    expect(mascot.setExpression).not.toHaveBeenCalled();
  });

  it('celebrate({expression:approve})：非 celebrate 表情走 setExpression，不放 confetti 庆祝', async () => {
    const mascot = fakeMascot();
    const canvasFx = fakeCanvasFx();
    const adapter = createAnimAdapter({ dom: fakeDom(), mascot, canvasFx, prefersReducedMotion: () => false });

    await adapter.celebrate({ expression: 'approve' });

    expect(mascot.setExpression).toHaveBeenCalledWith('approve', expect.objectContaining({ instant: false }));
    expect(mascot.celebrate).not.toHaveBeenCalled();
  });

  it('celebrate({scope:mascot})：只触达吉祥物，不触达画布（②）', async () => {
    const mascot = fakeMascot();
    const canvasFx = fakeCanvasFx();
    const adapter = createAnimAdapter({ dom: fakeDom(), mascot, canvasFx, prefersReducedMotion: () => false });

    await adapter.celebrate({ scope: 'mascot' });

    expect(mascot.celebrate).toHaveBeenCalledTimes(1);
    expect(canvasFx.celebrate).not.toHaveBeenCalled();
  });

  it('celebrate({scope:canvas})：只触达画布，不触达吉祥物（②）', async () => {
    const mascot = fakeMascot();
    const canvasFx = fakeCanvasFx();
    const adapter = createAnimAdapter({ dom: fakeDom(), mascot, canvasFx, prefersReducedMotion: () => false });

    await adapter.celebrate({ scope: 'canvas' });

    expect(canvasFx.celebrate).toHaveBeenCalledTimes(1);
    expect(mascot.celebrate).not.toHaveBeenCalled();
    expect(mascot.setExpression).not.toHaveBeenCalled();
  });

  it('reduceMotion=true：celebrate 向后端透传 instant:true（①）', async () => {
    const mascot = fakeMascot();
    const canvasFx = fakeCanvasFx();
    const adapter = createAnimAdapter({ dom: fakeDom(), mascot, canvasFx, prefersReducedMotion: () => true });

    await adapter.celebrate();

    expect(mascot.celebrate).toHaveBeenCalledWith(expect.objectContaining({ instant: true }));
    expect(canvasFx.celebrate).toHaveBeenCalledWith(expect.objectContaining({ instant: true }));
  });

  it('未注入 mascot/canvasFx：celebrate 不抛错且 resolve（降级路径）', async () => {
    const adapter = createAnimAdapter({ dom: fakeDom(), prefersReducedMotion: () => false });
    await expect(adapter.celebrate()).resolves.toBeUndefined();
  });

  it('reduceMotion getter 实时反映注入函数（系统运行时切换）', () => {
    let reduce = false;
    const adapter = createAnimAdapter({ dom: fakeDom(), prefersReducedMotion: () => reduce });
    expect(adapter.reduceMotion).toBe(false);
    reduce = true;
    expect(adapter.reduceMotion).toBe(true);
  });

  it('默认 prefersReducedMotion 读取 window.matchMedia(reduce)', () => {
    const matchMedia = vi.fn(() => ({ matches: true })) as unknown as typeof window.matchMedia;
    Object.defineProperty(window, 'matchMedia', { configurable: true, writable: true, value: matchMedia });

    const adapter = createAnimAdapter({ dom: fakeDom() });
    expect(adapter.reduceMotion).toBe(true);
    expect(matchMedia).toHaveBeenCalledWith('(prefers-reduced-motion: reduce)');

    Object.defineProperty(window, 'matchMedia', { configurable: true, writable: true, value: undefined });
  });

  it('matchMedia 缺失（SSR/老环境）：reduceMotion fail-safe 返回 false', () => {
    Object.defineProperty(window, 'matchMedia', { configurable: true, writable: true, value: undefined });
    const adapter = createAnimAdapter({ dom: fakeDom() });
    expect(adapter.reduceMotion).toBe(false);
  });

  it('dispose 释放 mascot + canvasFx 后端', () => {
    const mascot = fakeMascot();
    const canvasFx = fakeCanvasFx();
    const adapter = createAnimAdapter({ dom: fakeDom(), mascot, canvasFx });
    adapter.dispose();
    expect(mascot.dispose).toHaveBeenCalledTimes(1);
    expect(canvasFx.dispose).toHaveBeenCalledTimes(1);
  });
});

describe('domMotion 后端（Motion 隔离 · ③ 不实际加载）', () => {
  it('stepIn 非瞬时：调 motion.animate(target, {opacity,y}) 并在 finished 后 resolve', async () => {
    const backend = createDomMotionBackend();
    const el = document.createElement('div');

    await backend.stepIn(el, { instant: false });

    expect(motionMock.animate).toHaveBeenCalledTimes(1);
    const [target, keyframes] = motionMock.animate.mock.calls[0]!;
    expect(target).toBe(el);
    expect(keyframes).toHaveProperty('opacity');
    expect(keyframes).toHaveProperty('y');
  });

  it('stepIn 瞬时：不调 animate，直接落终值 opacity=1（① 退化为瞬时）', async () => {
    const backend = createDomMotionBackend();
    const el = document.createElement('div');
    el.style.opacity = '0';

    await backend.stepIn(el, { instant: true });

    expect(motionMock.animate).not.toHaveBeenCalled();
    expect(el.style.opacity).toBe('1');
  });

  it('stepDone(done)：animate 关键帧含 scale（成功收束 pop）', async () => {
    const backend = createDomMotionBackend();
    const el = document.createElement('div');

    await backend.stepDone(el, 'done', { instant: false });

    const [, keyframes] = motionMock.animate.mock.calls[0]!;
    expect(keyframes).toHaveProperty('scale');
  });

  it('stepDone(failed)：animate 关键帧含 x（失败抖动）', async () => {
    const backend = createDomMotionBackend();
    const el = document.createElement('div');

    await backend.stepDone(el, 'failed', { instant: false });

    const [, keyframes] = motionMock.animate.mock.calls[0]!;
    expect(keyframes).toHaveProperty('x');
  });

  it('stepDone 瞬时：不调 animate（① 退化为瞬时）', async () => {
    const backend = createDomMotionBackend();
    const el = document.createElement('div');

    await backend.stepDone(el, 'done', { instant: true });

    expect(motionMock.animate).not.toHaveBeenCalled();
  });

  it('signal 已 abort：调 controls.complete() 跳到终值并 resolve', async () => {
    const controls = makeControls();
    motionMock.animate.mockReturnValue(controls);
    const backend = createDomMotionBackend();
    const ctrl = new AbortController();
    ctrl.abort();

    await backend.stepIn(document.createElement('div'), { instant: false, signal: ctrl.signal });

    expect(controls.complete).toHaveBeenCalledTimes(1);
  });

  it('animate 抛错：fail-soft 落终值并 resolve（不外抛）', async () => {
    motionMock.animate.mockImplementation(() => {
      throw new Error('motion boom');
    });
    const backend = createDomMotionBackend();
    const el = document.createElement('div');
    el.style.opacity = '0';

    await expect(backend.stepIn(el, { instant: false })).resolves.toBeUndefined();
    expect(el.style.opacity).toBe('1');
  });
});

describe('riveMascot 后端（Rive 隔离 · ③ 不实际加载）', () => {
  const canvas = (): HTMLCanvasElement => document.createElement('canvas');

  it('createRiveMascot 用 canvas/src/stateMachines/autoplay 构造 Rive', () => {
    createRiveMascot(canvas(), { src: '/assets/mascot.riv', stateMachine: 'Tutor SM' });
    const inst = lastRive();
    expect(inst.params.src).toBe('/assets/mascot.riv');
    expect(inst.params.stateMachines).toBe('Tutor SM');
    expect(inst.params.autoplay).toBe(true);
    expect(inst.params.canvas).toBeInstanceOf(HTMLCanvasElement);
  });

  it('setExpression 触发映射的 trigger 输入并播放', () => {
    const fire = makeInput('celebrate');
    riveMock.inputs.push(fire);
    const mascot = createRiveMascot(canvas(), { src: '/m.riv' });

    mascot.setExpression('celebrate');

    expect(fire.fire).toHaveBeenCalledTimes(1);
    expect(lastRive().play).toHaveBeenCalled();
  });

  it('setExpression instant：fire 后 pause（① 不持续循环）', () => {
    const approve = makeInput('approve');
    riveMock.inputs.push(approve);
    const mascot = createRiveMascot(canvas(), { src: '/m.riv' });

    mascot.setExpression('approve', { instant: true });

    expect(approve.fire).toHaveBeenCalledTimes(1);
    expect(lastRive().pause).toHaveBeenCalled();
    expect(lastRive().play).not.toHaveBeenCalled();
  });

  it('setExpression 支持自定义 trigger 名映射', () => {
    const happy = makeInput('Happy');
    riveMock.inputs.push(happy);
    const mascot = createRiveMascot(canvas(), { src: '/m.riv', triggers: { celebrate: 'Happy' } });

    mascot.setExpression('celebrate');

    expect(happy.fire).toHaveBeenCalledTimes(1);
  });

  it('boolean 输入：setExpression 置 value=true', () => {
    const flag = makeInput('thinking', false);
    riveMock.inputs.push(flag);
    const mascot = createRiveMascot(canvas(), { src: '/m.riv' });

    mascot.setExpression('thinking');

    expect(flag.value).toBe(true);
  });

  it('未找到输入：fail-soft no-op 不抛错', () => {
    const mascot = createRiveMascot(canvas(), { src: '/m.riv' });
    expect(() => mascot.setExpression('reject')).not.toThrow();
  });

  it('stateMachineInputs 抛错（资源未就绪）：setExpression 不抛错', () => {
    riveMock.throwOnInputs = true;
    const mascot = createRiveMascot(canvas(), { src: '/m.riv' });
    expect(() => mascot.setExpression('idle')).not.toThrow();
  });

  it('celebrate(instant) 立即 resolve 并触发 celebrate 表情', async () => {
    const fire = makeInput('celebrate');
    riveMock.inputs.push(fire);
    const mascot = createRiveMascot(canvas(), { src: '/m.riv' });

    await expect(mascot.celebrate({ instant: true })).resolves.toBeUndefined();
    expect(fire.fire).toHaveBeenCalledTimes(1);
  });

  it('celebrate 非瞬时 durationMs=0：resolve 且触发表情', async () => {
    const fire = makeInput('celebrate');
    riveMock.inputs.push(fire);
    const mascot = createRiveMascot(canvas(), { src: '/m.riv' });

    await expect(mascot.celebrate({ durationMs: 0 })).resolves.toBeUndefined();
    expect(fire.fire).toHaveBeenCalledTimes(1);
  });

  it('dispose 调 rive.cleanup 且幂等', () => {
    const mascot = createRiveMascot(canvas(), { src: '/m.riv' });
    const inst = lastRive();
    mascot.dispose();
    mascot.dispose();
    expect(inst.cleanup).toHaveBeenCalledTimes(1);
  });
});

describe('phaserTween 后端（结构化假 scene · 不引真实 phaser）', () => {
  interface TweenConfigCapture {
    targets: unknown;
    scaleX?: number;
    scaleY?: number;
    duration?: number;
    yoyo?: boolean;
    onComplete?: () => void;
  }
  interface SceneCalls {
    tweens: TweenConfigCapture[];
    stops: number;
    throwOnAdd: boolean;
  }
  interface FxTarget {
    setScale: Mock;
    scaleX: number;
    scaleY: number;
  }

  function createScene(): { scene: Parameters<typeof createPhaserCanvasFx>[0]; calls: SceneCalls } {
    const calls: SceneCalls = { tweens: [], stops: 0, throwOnAdd: false };
    const scene = {
      tweens: {
        add: vi.fn((config: TweenConfigCapture) => {
          if (calls.throwOnAdd) {
            throw new Error('tween boom');
          }
          calls.tweens.push(config);
          // 假 scene 同步回调 onComplete，驱动 promise resolve
          config.onComplete?.();
          return {
            stop: vi.fn(() => {
              calls.stops += 1;
            }),
          };
        }),
      },
    };
    return { scene: scene as unknown as Parameters<typeof createPhaserCanvasFx>[0], calls };
  }

  function makeTarget(): FxTarget {
    return { setScale: vi.fn(), scaleX: 1, scaleY: 1 };
  }

  it('celebrate 非瞬时：在 target 上加缩放 pop tween 并在 onComplete 后 resolve', async () => {
    const { scene, calls } = createScene();
    const target = makeTarget();
    const fx = createPhaserCanvasFx(scene, { getTarget: () => target });

    await expect(fx.celebrate({ instant: false })).resolves.toBeUndefined();

    expect(calls.tweens).toHaveLength(1);
    expect(calls.tweens[0]?.targets).toBe(target);
    expect(calls.tweens[0]?.scaleX).toBeGreaterThan(1);
    expect(calls.tweens[0]?.yoyo).toBe(true);
  });

  it('celebrate 瞬时：不加 tween，直接 setScale(1)（① 退化为瞬时）', async () => {
    const { scene, calls } = createScene();
    const target = makeTarget();
    const fx = createPhaserCanvasFx(scene, { getTarget: () => target });

    await fx.celebrate({ instant: true });

    expect(calls.tweens).toHaveLength(0);
    expect(target.setScale).toHaveBeenCalledWith(1);
  });

  it('getTarget 返回 null：no-op 且 resolve（画布未就绪降级）', async () => {
    const { scene, calls } = createScene();
    const fx = createPhaserCanvasFx(scene, { getTarget: () => null });

    await expect(fx.celebrate({ instant: false })).resolves.toBeUndefined();
    expect(calls.tweens).toHaveLength(0);
  });

  it('tweens.add 抛错：fail-soft resolve 不外抛', async () => {
    const { scene, calls } = createScene();
    calls.throwOnAdd = true;
    const fx = createPhaserCanvasFx(scene, { getTarget: () => makeTarget() });

    await expect(fx.celebrate({ instant: false })).resolves.toBeUndefined();
  });

  it('dispose 停止残留 tween 且幂等', async () => {
    const { scene, calls } = createScene();
    const fx = createPhaserCanvasFx(scene, { getTarget: () => makeTarget() });
    await fx.celebrate({ instant: false });

    fx.dispose();
    fx.dispose();

    expect(calls.stops).toBe(1);
  });
});

describe('AnimAdapter 与真实后端集成（默认装配冒烟）', () => {
  it('默认 dom 后端经 motion mock 完成 playStepIn（无 mascot/canvasFx 时 celebrate 仍 resolve）', async () => {
    const adapter = createAnimAdapter({ prefersReducedMotion: () => false });
    const el = document.createElement('div');

    await adapter.playStepIn(el);
    expect(motionMock.animate).toHaveBeenCalledTimes(1);

    await expect(adapter.celebrate()).resolves.toBeUndefined();
  });
});
