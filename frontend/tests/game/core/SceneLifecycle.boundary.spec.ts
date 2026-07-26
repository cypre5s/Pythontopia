// SceneLifecycle 边界测试：与原 spec 互补。
// 原 spec 覆盖完整 host（events/tweens/time/input 全在 + off 可用）的快路径，
// 本 spec 聚焦"宿主成员部分缺失"、"events.off 缺失"、"onShutdown 抛错"、
// "logger 不提供时不抛"、"destroy 阶段 onShutdown 不会被再触发"。
import type Phaser from 'phaser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { attachLifecycle } from '@/game/core/SceneLifecycle';

type Listener = () => void;

interface PartialHostSpec {
  withTweens?: boolean;
  withTime?: boolean;
  withInput?: boolean;
  withOff?: boolean;
}

interface FlexibleScene {
  scene: Phaser.Scene;
  emit(event: string): void;
  listenerCount(event: string): number;
  killAll?: ReturnType<typeof vi.fn>;
  removeAllEvents?: ReturnType<typeof vi.fn>;
  removeAllListeners?: ReturnType<typeof vi.fn>;
  on: ReturnType<typeof vi.fn>;
  off: ReturnType<typeof vi.fn> | undefined;
}

function createFlexibleScene(spec: PartialHostSpec = {}): FlexibleScene {
  const handlers = new Map<string, Set<Listener>>();
  const on = vi.fn((event: string, fn: Listener) => {
    const set = handlers.get(event) ?? new Set<Listener>();
    set.add(fn);
    handlers.set(event, set);
  });
  const off = vi.fn((event: string, fn: Listener) => {
    handlers.get(event)?.delete(fn);
  });

  const killAll = spec.withTweens ? vi.fn() : undefined;
  const removeAllEvents = spec.withTime ? vi.fn() : undefined;
  const removeAllListeners = spec.withInput ? vi.fn() : undefined;

  const raw: Record<string, unknown> = {
    events: spec.withOff === false ? { on } : { on, off },
  };
  if (killAll) raw.tweens = { killAll };
  if (removeAllEvents) raw.time = { removeAllEvents };
  if (removeAllListeners) raw.input = { removeAllListeners };

  return {
    scene: raw as unknown as Phaser.Scene,
    emit(event: string): void {
      const set = handlers.get(event);
      if (!set) return;
      for (const fn of [...set]) fn();
    },
    listenerCount(event: string): number {
      return handlers.get(event)?.size ?? 0;
    },
    killAll,
    removeAllEvents,
    removeAllListeners,
    on,
    off: spec.withOff === false ? undefined : off,
  };
}

describe('SceneLifecycle 边界 - 宿主部分缺失', () => {
  let mock: FlexibleScene;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('完全没有 tweens/time/input 时 shutdown 不抛错', () => {
    mock = createFlexibleScene({});
    attachLifecycle(mock.scene);
    expect(() => mock.emit('shutdown')).not.toThrow();
  });

  it('只有 tweens 时仅调用 killAll，其它 cleanup 静默跳过', () => {
    mock = createFlexibleScene({ withTweens: true });
    attachLifecycle(mock.scene);
    mock.emit('shutdown');
    expect(mock.killAll).toHaveBeenCalledTimes(1);
  });

  it('只有 time 时仅调用 removeAllEvents', () => {
    mock = createFlexibleScene({ withTime: true });
    attachLifecycle(mock.scene);
    mock.emit('shutdown');
    expect(mock.removeAllEvents).toHaveBeenCalledTimes(1);
  });

  it('只有 input 时仅调用 removeAllListeners', () => {
    mock = createFlexibleScene({ withInput: true });
    attachLifecycle(mock.scene);
    mock.emit('shutdown');
    expect(mock.removeAllListeners).toHaveBeenCalledTimes(1);
  });

  it('events.off 缺失（老版 Phaser API）时 detach 不抛错，但 listener 不会被解除', () => {
    mock = createFlexibleScene({
      withTweens: true,
      withTime: true,
      withInput: true,
      withOff: false,
    });
    const detach = attachLifecycle(mock.scene);
    expect(mock.listenerCount('shutdown')).toBe(1);
    expect(() => detach()).not.toThrow();
    // off 不存在 → 监听器不会真的被解除（仍能再次触发）。
    expect(mock.listenerCount('shutdown')).toBe(1);
    mock.emit('shutdown');
    expect(mock.killAll).toHaveBeenCalledTimes(1);
  });
});

describe('SceneLifecycle 边界 - 错误传播与 logger', () => {
  let mock: FlexibleScene;

  beforeEach(() => {
    mock = createFlexibleScene({
      withTweens: true,
      withTime: true,
      withInput: true,
    });
  });

  it('logger 缺失时即便 cleanup 抛错也不挂掉 shutdown', () => {
    mock.killAll?.mockImplementation(() => {
      throw new Error('tween-boom');
    });
    mock.removeAllEvents?.mockImplementation(() => {
      throw new Error('time-boom');
    });
    mock.removeAllListeners?.mockImplementation(() => {
      throw new Error('input-boom');
    });

    attachLifecycle(mock.scene);
    expect(() => mock.emit('shutdown')).not.toThrow();
    expect(mock.killAll).toHaveBeenCalled();
    expect(mock.removeAllEvents).toHaveBeenCalled();
    expect(mock.removeAllListeners).toHaveBeenCalled();
  });

  it('三个 cleanup 全部抛错时 logger 收到三条独立消息且包含 phase=shutdown', () => {
    mock.killAll?.mockImplementation(() => {
      throw new Error('tween-boom');
    });
    mock.removeAllEvents?.mockImplementation(() => {
      throw new Error('time-boom');
    });
    mock.removeAllListeners?.mockImplementation(() => {
      throw new Error('input-boom');
    });
    const logger = vi.fn();
    attachLifecycle(mock.scene, { logger });
    mock.emit('shutdown');

    expect(logger).toHaveBeenCalledTimes(4); // 3 cleanup errors + 1 final "shutdown cleanup done"
    expect(logger.mock.calls.some((c) => String(c[0]).includes('tween-boom'))).toBe(true);
    expect(logger.mock.calls.some((c) => String(c[0]).includes('time-boom'))).toBe(true);
    expect(logger.mock.calls.some((c) => String(c[0]).includes('input-boom'))).toBe(true);
    expect(logger.mock.calls.every((c) => String(c[0]).includes('SceneLifecycle'))).toBe(true);
  });

  it('cleanup 抛非 Error（字符串）时 logger 仍可正常 describeError', () => {
    mock.killAll?.mockImplementation(() => {
      throw 'plain-string';
    });
    const logger = vi.fn();
    attachLifecycle(mock.scene, { logger });
    mock.emit('shutdown');
    expect(logger.mock.calls.some((c) => String(c[0]).includes('plain-string'))).toBe(true);
  });

  it('onShutdown 抛错时不冒泡，logger 收到 "onShutdown threw" 消息', () => {
    const logger = vi.fn();
    const onShutdown = vi.fn(() => {
      throw new Error('user-callback-boom');
    });
    attachLifecycle(mock.scene, { onShutdown, logger });
    expect(() => mock.emit('shutdown')).not.toThrow();
    expect(onShutdown).toHaveBeenCalledTimes(1);
    expect(
      logger.mock.calls.some((c) => String(c[0]).includes('onShutdown threw')),
    ).toBe(true);
    expect(
      logger.mock.calls.some((c) => String(c[0]).includes('user-callback-boom')),
    ).toBe(true);
  });

  // 契约变更（修复「destroy 不跑业务清理」）：Phaser 的 Systems.destroy() 只 emit
  // DESTROY 不 emit SHUTDOWN，销毁整个 Game 时若只认 shutdown，业务清理永不执行
  // （WorldScene 的 window 监听器会随每次 /world 往返累积）。新契约是「shutdown 与
  // destroy 谁先来谁触发，且只触发一次」。
  it('onShutdown 在 destroy 阶段也会触发（整 Game 销毁路径没有 shutdown）', () => {
    const onShutdown = vi.fn();
    attachLifecycle(mock.scene, { onShutdown });
    mock.emit('destroy');
    expect(onShutdown).toHaveBeenCalledTimes(1);
    // 之后再来的 shutdown / destroy 不重复触发。
    mock.emit('shutdown');
    mock.emit('destroy');
    expect(onShutdown).toHaveBeenCalledTimes(1);
  });

  it('onShutdown 先由 shutdown 触发后，destroy 不会再触发一次', () => {
    const onShutdown = vi.fn();
    attachLifecycle(mock.scene, { onShutdown });
    mock.emit('shutdown');
    expect(onShutdown).toHaveBeenCalledTimes(1);
    mock.emit('destroy');
    expect(onShutdown).toHaveBeenCalledTimes(1);
  });

  it('多次 attachLifecycle 注册的 onShutdown 互不影响（各自独立计数）', () => {
    const onA = vi.fn();
    const onB = vi.fn();
    attachLifecycle(mock.scene, { onShutdown: onA });
    attachLifecycle(mock.scene, { onShutdown: onB });
    expect(mock.listenerCount('shutdown')).toBe(2);
    mock.emit('shutdown');
    expect(onA).toHaveBeenCalledTimes(1);
    expect(onB).toHaveBeenCalledTimes(1);
    // cleanup 也被调用了两次（一次 attach 一次回调）。
    expect(mock.killAll).toHaveBeenCalledTimes(2);
  });
});

describe('SceneLifecycle 边界 - detach 行为', () => {
  it('detach 后 destroy 也不会再触发 cleanup', () => {
    const mock = createFlexibleScene({
      withTweens: true,
      withTime: true,
      withInput: true,
    });
    const detach = attachLifecycle(mock.scene);
    detach();
    mock.emit('shutdown');
    mock.emit('destroy');
    expect(mock.killAll).not.toHaveBeenCalled();
    expect(mock.removeAllEvents).not.toHaveBeenCalled();
    expect(mock.removeAllListeners).not.toHaveBeenCalled();
  });

  it('attach -> emit shutdown -> detach 后再次 emit shutdown 不会重复 cleanup', () => {
    const mock = createFlexibleScene({
      withTweens: true,
      withTime: true,
      withInput: true,
    });
    const detach = attachLifecycle(mock.scene);
    mock.emit('shutdown');
    expect(mock.killAll).toHaveBeenCalledTimes(1);
    detach();
    mock.emit('shutdown');
    expect(mock.killAll).toHaveBeenCalledTimes(1);
  });
});
