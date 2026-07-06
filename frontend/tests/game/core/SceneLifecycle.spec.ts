import type Phaser from 'phaser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { attachLifecycle } from '@/game/core/SceneLifecycle';

type SceneEventName = 'shutdown' | 'destroy' | string;
type SceneEventListener = () => void;

interface MockSceneSpies {
  on: ReturnType<typeof vi.fn>;
  off: ReturnType<typeof vi.fn>;
  killAll: ReturnType<typeof vi.fn>;
  removeAllEvents: ReturnType<typeof vi.fn>;
  removeAllListeners: ReturnType<typeof vi.fn>;
}

interface MockScene {
  scene: Phaser.Scene;
  spies: MockSceneSpies;
  emit(event: SceneEventName): void;
  listenerCount(event: SceneEventName): number;
}

function createMockScene(): MockScene {
  const handlers = new Map<string, Set<SceneEventListener>>();

  const on = vi.fn((event: string, fn: SceneEventListener) => {
    let set = handlers.get(event);
    if (!set) {
      set = new Set();
      handlers.set(event, set);
    }
    set.add(fn);
  });

  const off = vi.fn((event: string, fn: SceneEventListener) => {
    handlers.get(event)?.delete(fn);
  });

  const killAll = vi.fn();
  const removeAllEvents = vi.fn();
  const removeAllListeners = vi.fn();

  const raw = {
    events: { on, off },
    tweens: { killAll },
    time: { removeAllEvents },
    input: { removeAllListeners },
  };

  return {
    scene: raw as unknown as Phaser.Scene,
    spies: { on, off, killAll, removeAllEvents, removeAllListeners },
    emit(event: string): void {
      const set = handlers.get(event);
      if (!set) {
        return;
      }
      for (const fn of [...set]) {
        fn();
      }
    },
    listenerCount(event: string): number {
      return handlers.get(event)?.size ?? 0;
    },
  };
}

describe('attachLifecycle', () => {
  let mock: MockScene;

  beforeEach(() => {
    mock = createMockScene();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('registers shutdown and destroy listeners on the scene events emitter', () => {
    attachLifecycle(mock.scene);

    expect(mock.spies.on).toHaveBeenCalledTimes(2);
    expect(mock.spies.on).toHaveBeenCalledWith('shutdown', expect.any(Function));
    expect(mock.spies.on).toHaveBeenCalledWith('destroy', expect.any(Function));
    expect(mock.listenerCount('shutdown')).toBe(1);
    expect(mock.listenerCount('destroy')).toBe(1);
  });

  it('runs tween / time / input cleanup when shutdown fires', () => {
    attachLifecycle(mock.scene);

    mock.emit('shutdown');

    expect(mock.spies.killAll).toHaveBeenCalledTimes(1);
    expect(mock.spies.removeAllEvents).toHaveBeenCalledTimes(1);
    expect(mock.spies.removeAllListeners).toHaveBeenCalledTimes(1);
  });

  it('runs cleanup again when destroy fires (idempotent safety net)', () => {
    attachLifecycle(mock.scene);

    mock.emit('shutdown');
    mock.emit('destroy');

    expect(mock.spies.killAll).toHaveBeenCalledTimes(2);
    expect(mock.spies.removeAllEvents).toHaveBeenCalledTimes(2);
    expect(mock.spies.removeAllListeners).toHaveBeenCalledTimes(2);
  });

  it('invokes opts.onShutdown after the runtime cleanup', () => {
    const order: string[] = [];
    mock.spies.killAll.mockImplementation(() => order.push('killAll'));
    mock.spies.removeAllEvents.mockImplementation(() => order.push('removeAllEvents'));
    mock.spies.removeAllListeners.mockImplementation(() => order.push('removeAllListeners'));
    const onShutdown = vi.fn(() => {
      order.push('onShutdown');
    });

    attachLifecycle(mock.scene, { onShutdown });
    mock.emit('shutdown');

    expect(onShutdown).toHaveBeenCalledTimes(1);
    expect(order).toEqual(['killAll', 'removeAllEvents', 'removeAllListeners', 'onShutdown']);
  });

  it('swallows errors thrown by cleanup primitives and forwards them to the logger', () => {
    mock.spies.killAll.mockImplementation(() => {
      throw new Error('boom-tween');
    });
    const logger = vi.fn();

    const detach = attachLifecycle(mock.scene, { logger });
    expect(() => mock.emit('shutdown')).not.toThrow();

    expect(mock.spies.removeAllEvents).toHaveBeenCalled();
    expect(mock.spies.removeAllListeners).toHaveBeenCalled();
    expect(logger).toHaveBeenCalledWith(expect.stringContaining('boom-tween'));

    detach();
  });

  it('detaches both listeners when the returned cleanup is called', () => {
    const detach = attachLifecycle(mock.scene);

    expect(mock.listenerCount('shutdown')).toBe(1);
    expect(mock.listenerCount('destroy')).toBe(1);

    detach();

    expect(mock.spies.off).toHaveBeenCalledWith('shutdown', expect.any(Function));
    expect(mock.spies.off).toHaveBeenCalledWith('destroy', expect.any(Function));
    expect(mock.listenerCount('shutdown')).toBe(0);
    expect(mock.listenerCount('destroy')).toBe(0);

    mock.emit('shutdown');
    mock.emit('destroy');
    expect(mock.spies.killAll).not.toHaveBeenCalled();
    expect(mock.spies.removeAllEvents).not.toHaveBeenCalled();
  });

  it('detach is idempotent — calling it twice does not double-remove listeners', () => {
    const detach = attachLifecycle(mock.scene);
    detach();
    detach();

    expect(mock.spies.off).toHaveBeenCalledTimes(2);
  });

  it('reattaching after detach keeps exactly one pair of listeners (no duplicate accumulation)', () => {
    const detach1 = attachLifecycle(mock.scene);
    detach1();

    attachLifecycle(mock.scene);

    expect(mock.listenerCount('shutdown')).toBe(1);
    expect(mock.listenerCount('destroy')).toBe(1);

    mock.emit('shutdown');
    expect(mock.spies.killAll).toHaveBeenCalledTimes(1);
  });
});
