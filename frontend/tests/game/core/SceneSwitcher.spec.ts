// W1 SceneSwitcher 单测：覆盖 fade → cleanup → start 主链 + 错误/超时兜底。
// 测试不依赖真 Phaser 渲染：手工构造结构上等价的 fake scene，通过 emit() 模拟
// camerafadeoutcomplete 事件触发。phaserMock.ts setup 会在加载阶段把 phaser
// 模块替换成最小桩，但本测试仅使用 import type，运行期不会触发到 phaser 真模块。
import { describe, expect, it, vi } from 'vitest';
import type Phaser from 'phaser';
import { SceneSwitcher } from '@/game/core/SceneSwitcher';

type Listener = () => void;

interface FakeCamera {
  fadeOut: ReturnType<typeof vi.fn>;
  once: ReturnType<typeof vi.fn>;
  emit(event: string): void;
}

interface FakeScene {
  cameras: { main: FakeCamera };
  time: { removeAllEvents: ReturnType<typeof vi.fn> };
  tweens: { killAll: ReturnType<typeof vi.fn> };
  load: { reset: ReturnType<typeof vi.fn> };
  scene: {
    start: ReturnType<typeof vi.fn>;
    get: ReturnType<typeof vi.fn>;
    manager: { keys: Record<string, object> };
  };
}

function makeFakeCamera(): FakeCamera {
  const listeners: Record<string, Listener[]> = {};
  return {
    fadeOut: vi.fn(),
    once: vi.fn((event: string, cb: Listener) => {
      let list = listeners[event];
      if (!list) {
        list = [];
        listeners[event] = list;
      }
      list.push(cb);
    }),
    emit(event: string) {
      const list = listeners[event] ?? [];
      delete listeners[event];
      for (const cb of list) {
        cb();
      }
    },
  };
}

function makeFakeScene(
  registeredKeys: ReadonlyArray<string> = ['BootScene', 'TownScene'],
): FakeScene {
  const keys: Record<string, object> = {};
  for (const k of registeredKeys) {
    keys[k] = {};
  }
  return {
    cameras: { main: makeFakeCamera() },
    time: { removeAllEvents: vi.fn() },
    tweens: { killAll: vi.fn() },
    load: { reset: vi.fn() },
    scene: {
      start: vi.fn(),
      get: vi.fn((key: string) => keys[key] ?? null),
      manager: { keys },
    },
  };
}

function asPhaserScene(fake: FakeScene): Phaser.Scene {
  return fake as unknown as Phaser.Scene;
}

describe('SceneSwitcher', () => {
  it('switchTo 按 fade-out → cleanup → start 顺序执行', async () => {
    const fake = makeFakeScene();
    const order: string[] = [];
    fake.cameras.main.fadeOut.mockImplementation(() => {
      order.push('fadeOut');
    });
    fake.time.removeAllEvents.mockImplementation(() => {
      order.push('cleanup');
    });
    fake.scene.start.mockImplementation(() => {
      order.push('start');
    });

    const switcher = new SceneSwitcher();
    const promise = switcher.switchTo(asPhaserScene(fake), 'TownScene');
    fake.cameras.main.emit('camerafadeoutcomplete');
    await promise;

    expect(order).toEqual(['fadeOut', 'cleanup', 'start']);
    expect(fake.cameras.main.fadeOut).toHaveBeenCalledTimes(1);
    expect(fake.scene.start).toHaveBeenCalledWith('TownScene', undefined);
  });

  it('fade-out 完成后清理 timers / tweens / loader', async () => {
    const fake = makeFakeScene();
    const switcher = new SceneSwitcher();
    const promise = switcher.switchTo(asPhaserScene(fake), 'TownScene');

    expect(fake.time.removeAllEvents).not.toHaveBeenCalled();
    expect(fake.tweens.killAll).not.toHaveBeenCalled();
    expect(fake.load.reset).not.toHaveBeenCalled();

    fake.cameras.main.emit('camerafadeoutcomplete');
    await promise;

    expect(fake.time.removeAllEvents).toHaveBeenCalledTimes(1);
    expect(fake.tweens.killAll).toHaveBeenCalledTimes(1);
    expect(fake.load.reset).toHaveBeenCalledTimes(1);
  });

  it('自定义 fadeMs 透传给 cameras.main.fadeOut', async () => {
    const fake = makeFakeScene();
    const switcher = new SceneSwitcher();
    const promise = switcher.switchTo(asPhaserScene(fake), 'TownScene', { fadeMs: 750 });
    fake.cameras.main.emit('camerafadeoutcomplete');
    await promise;

    expect(fake.cameras.main.fadeOut).toHaveBeenCalledWith(750, 0, 0, 0);
  });

  it('目标 scene 不存在时 fallback 到 BootScene 且调 onError', async () => {
    const fake = makeFakeScene(['BootScene', 'TownScene']);
    const onError = vi.fn();
    const switcher = new SceneSwitcher();
    const promise = switcher.switchTo(asPhaserScene(fake), 'GhostScene', { onError });
    fake.cameras.main.emit('camerafadeoutcomplete');
    await promise;

    expect(onError).toHaveBeenCalledTimes(1);
    const call = onError.mock.calls[0];
    expect(call?.[0]).toBeInstanceOf(Error);
    expect(call?.[1]).toBe('GhostScene');
    expect(fake.scene.start).toHaveBeenCalledTimes(1);
    expect(fake.scene.start).toHaveBeenCalledWith('BootScene', undefined);
  });

  it('data 透传到 scene.start', async () => {
    const fake = makeFakeScene();
    const switcher = new SceneSwitcher();
    const data: Record<string, unknown> = { mapKey: 'farm_north', spawnTileX: 5 };
    const promise = switcher.switchTo(asPhaserScene(fake), 'TownScene', { data });
    fake.cameras.main.emit('camerafadeoutcomplete');
    await promise;

    expect(fake.scene.start).toHaveBeenCalledWith('TownScene', data);
  });

  it('cleanupHooks 在 cleanupScene 之后按注册顺序调用', async () => {
    const fake = makeFakeScene();
    const order: string[] = [];
    fake.time.removeAllEvents.mockImplementation(() => {
      order.push('cleanup');
    });
    const hook1 = vi.fn(() => {
      order.push('hook1');
    });
    const hook2 = vi.fn(() => {
      order.push('hook2');
    });
    const switcher = new SceneSwitcher({ cleanupHooks: [hook1, hook2] });
    const promise = switcher.switchTo(asPhaserScene(fake), 'TownScene');
    fake.cameras.main.emit('camerafadeoutcomplete');
    await promise;

    expect(hook1).toHaveBeenCalledTimes(1);
    expect(hook2).toHaveBeenCalledTimes(1);
    expect(order).toEqual(['cleanup', 'hook1', 'hook2']);
  });

  it('fade-out 事件未触发时 200ms 超时兜底继续推进管线', async () => {
    vi.useFakeTimers();
    try {
      const fake = makeFakeScene();
      const switcher = new SceneSwitcher();
      const promise = switcher.switchTo(asPhaserScene(fake), 'TownScene', { fadeMs: 100 });

      expect(fake.cameras.main.fadeOut).toHaveBeenCalledTimes(1);
      expect(fake.scene.start).not.toHaveBeenCalled();
      expect(fake.time.removeAllEvents).not.toHaveBeenCalled();

      await vi.advanceTimersByTimeAsync(99);
      expect(fake.scene.start).not.toHaveBeenCalled();

      await vi.advanceTimersByTimeAsync(202);
      await promise;

      expect(fake.time.removeAllEvents).toHaveBeenCalledTimes(1);
      expect(fake.tweens.killAll).toHaveBeenCalledTimes(1);
      expect(fake.load.reset).toHaveBeenCalledTimes(1);
      expect(fake.scene.start).toHaveBeenCalledWith('TownScene', undefined);
    } finally {
      vi.useRealTimers();
    }
  });

  it('SceneSwitcher.cleanupScene 静态方法独立可用', () => {
    const fake = makeFakeScene();
    SceneSwitcher.cleanupScene(asPhaserScene(fake));
    expect(fake.time.removeAllEvents).toHaveBeenCalledTimes(1);
    expect(fake.tweens.killAll).toHaveBeenCalledTimes(1);
    expect(fake.load.reset).toHaveBeenCalledTimes(1);
    expect(fake.cameras.main.fadeOut).not.toHaveBeenCalled();
    expect(fake.scene.start).not.toHaveBeenCalled();
  });

  it('自定义 fallbackSceneKey 在目标 scene 不存在时生效', async () => {
    const fake = makeFakeScene(['BootScene', 'TownScene', 'TitleScene']);
    const onError = vi.fn();
    const switcher = new SceneSwitcher({ fallbackSceneKey: 'TitleScene' });
    const promise = switcher.switchTo(asPhaserScene(fake), 'GhostScene', { onError });
    fake.cameras.main.emit('camerafadeoutcomplete');
    await promise;

    expect(onError).toHaveBeenCalledTimes(1);
    expect(fake.scene.start).toHaveBeenCalledWith('TitleScene', undefined);
  });
});
