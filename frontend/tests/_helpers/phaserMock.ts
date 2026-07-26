// 隔离原因：vitest 在 jsdom 环境下直接 import phaser 会触发 CanvasFeatures 初始化
//          调用 getContext('2d')，jsdom 默认未实现该 API 会抛 fillStyle null 错。
//          通过 vi.mock('phaser') 在所有 spec 加载前替换 phaser 模块为最小桩，
//          只暴露生产代码静态读取的常量 + 单测断言可能用到的最小 API。
import { vi } from 'vitest';

vi.mock('phaser', () => {
  class MockBaseSound {
    play = vi.fn();
    stop = vi.fn();
    pause = vi.fn();
    resume = vi.fn();
    isPlaying = false;
    volume = 1;
  }

  class MockSoundManager {
    mute = false;
    add = vi.fn(() => new MockBaseSound());
    play = vi.fn();
    stop = vi.fn();
  }

  class MockTweens {
    add = vi.fn();
    killTweensOf = vi.fn();
  }

  class MockScene {
    tweens = new MockTweens();
  }

  class MockGame {
    sound = new MockSoundManager();
    scene = { scenes: [new MockScene()] };
    destroy = vi.fn();
  }

  const PhaserStub = {
    AUTO: 0,
    WEBGL: 1,
    CANVAS: 2,
    HEADLESS: 3,
    Game: MockGame,
    Scene: class {},
    Scenes: { Events: { SHUTDOWN: 'shutdown' } },
    // sprite.ts 的 playAction 用 ANIMATION_COMPLETE 挂/摘一次性动作的收尾回调，
    // 常量值与真实 Phaser 一致（'animationcomplete'）。
    Animations: { Events: { ANIMATION_COMPLETE: 'animationcomplete' } },
    Scale: { FIT: 'FIT', CENTER_BOTH: 'CENTER_BOTH', NONE: 'NONE' },
    Sound: { BaseSound: MockBaseSound },
    Tilemaps: { TilemapLayer: class {} },
    Types: {},
    Math: { Between: (a: number, b: number) => Math.floor(a + Math.random() * (b - a + 1)) },
  };

  return {
    default: PhaserStub,
    ...PhaserStub,
  };
});
