import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// celebrate.ts 内部从 @game/adapters/phaserGame 拿 Phaser 实例，单测里要么没有
// 实例（DOM 降级路径），要么注入一个伪 game。借助 vi.mock 让两条路径都可以被
// 显式 stub 出来。
const phaserGameMock = vi.hoisted(() => ({
  getPhaserGame: vi.fn(),
}));

vi.mock('@game/adapters/phaserGame', () => phaserGameMock);

const audioMock = vi.hoisted(() => ({
  playSfx: vi.fn(),
}));

vi.mock('@/composables/useAudio', () => ({
  playSfx: audioMock.playSfx,
  playBgm: vi.fn(),
  stopBgm: vi.fn(),
  setMuted: vi.fn(),
  useAudio: () => ({
    playBgm: vi.fn(),
    stopBgm: vi.fn(),
    playSfx: audioMock.playSfx,
    setMuted: vi.fn(),
    isMuted: () => false,
    getCurrentBgmKey: () => null,
  }),
}));

import { celebrateKcUnlock, celebrateLevelUp, celebrateMature } from './celebrate';

class MockAudio {
  src: string;
  volume = 1;
  currentTime = 0;
  preload = '';
  constructor(src: string) {
    this.src = src;
  }
  play(): Promise<void> {
    return Promise.resolve();
  }
}

describe('celebrateMature', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    vi.stubGlobal('Audio', MockAudio as unknown);
    vi.useFakeTimers();
    phaserGameMock.getPhaserGame.mockReset();
    audioMock.playSfx.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
  });

  it('调用不抛错（target 为 null 时挂载到 body）', () => {
    expect(() => celebrateMature(null)).not.toThrow();
  });

  it('调用后 DOM 出现 .pyt-celebrate 覆盖层', () => {
    celebrateMature(null);
    const overlays = document.querySelectorAll('.pyt-celebrate');
    expect(overlays.length).toBe(1);
    const overlay = overlays[0]!;
    expect(overlay.querySelector('.pyt-celebrate__burst')).not.toBeNull();
    expect(overlay.querySelectorAll('.pyt-celebrate__piece').length).toBeGreaterThan(0);
  });

  it('1.2 秒后覆盖层自动移除', () => {
    celebrateMature(null);
    expect(document.querySelectorAll('.pyt-celebrate').length).toBe(1);
    vi.advanceTimersByTime(1199);
    expect(document.querySelectorAll('.pyt-celebrate').length).toBe(1);
    vi.advanceTimersByTime(2);
    expect(document.querySelectorAll('.pyt-celebrate').length).toBe(0);
  });

  it('指定 target 时覆盖层挂载到该元素下', () => {
    const host = document.createElement('section');
    document.body.appendChild(host);
    celebrateMature(host);
    expect(host.querySelectorAll('.pyt-celebrate').length).toBe(1);
    expect(document.body.children).toContain(host);
  });
});

interface FakeEmitter {
  destroy: ReturnType<typeof vi.fn>;
  setDepth: ReturnType<typeof vi.fn>;
}

interface FakeSceneCalls {
  particles: Array<{ x: number; y: number; key: string; config: Record<string, unknown> }>;
  emitters: FakeEmitter[];
}

function makeFakeGame(options?: { missingTexture?: boolean }): {
  game: { scene: { getScenes(active: boolean): unknown[] } };
  calls: FakeSceneCalls;
} {
  const calls: FakeSceneCalls = { particles: [], emitters: [] };
  const scene = {
    add: {
      particles: vi.fn((x: number, y: number, key: string, config: Record<string, unknown>) => {
        const emitter: FakeEmitter = { destroy: vi.fn(), setDepth: vi.fn() };
        calls.particles.push({ x, y, key, config });
        calls.emitters.push(emitter);
        return emitter;
      }),
    },
    cameras: { main: { midPoint: { x: 320, y: 200 } } },
    textures: { exists: vi.fn(() => !options?.missingTexture) },
  };
  const game = { scene: { getScenes: vi.fn(() => [scene]) } };
  return { game, calls };
}

describe('celebrateKcUnlock', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    vi.useFakeTimers();
    phaserGameMock.getPhaserGame.mockReset();
    audioMock.playSfx.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    document.body.innerHTML = '';
  });

  it('始终触发 DOM 浮层（即便 Phaser 不在线）', () => {
    phaserGameMock.getPhaserGame.mockReturnValue(null);

    celebrateKcUnlock({ kcCode: 'KC.PY.LIST', kcName: '列表' });

    expect(document.querySelectorAll('.pyt-celebrate').length).toBe(1);
  });

  it('Phaser 在线时在屏幕中心创建 confetti 粒子并在 2s 后销毁', () => {
    const { game, calls } = makeFakeGame();
    phaserGameMock.getPhaserGame.mockReturnValue(game);

    celebrateKcUnlock({ kcCode: 'KC.PY.LIST', kcName: '列表' });

    expect(calls.particles).toHaveLength(1);
    expect(calls.particles[0]?.key).toBe('fx_confetti_burst');
    expect(calls.particles[0]?.x).toBe(320);
    expect(calls.particles[0]?.y).toBe(200);
    expect(calls.emitters[0]?.setDepth).toHaveBeenCalledWith(100);

    vi.advanceTimersByTime(2_001);
    expect(calls.emitters[0]?.destroy).toHaveBeenCalledTimes(1);
  });

  it('withPhaserBurst=false 关闭 Phaser 路径，只走 DOM', () => {
    const { game, calls } = makeFakeGame();
    phaserGameMock.getPhaserGame.mockReturnValue(game);

    celebrateKcUnlock({ kcCode: 'KC.X', kcName: 'X', withPhaserBurst: false });

    expect(calls.particles).toHaveLength(0);
    expect(document.querySelectorAll('.pyt-celebrate').length).toBe(1);
  });

  it('贴图未加载时静默降级，不抛错', () => {
    const { game, calls } = makeFakeGame({ missingTexture: true });
    phaserGameMock.getPhaserGame.mockReturnValue(game);

    expect(() => celebrateKcUnlock({ kcCode: 'KC.Y', kcName: 'Y' })).not.toThrow();
    expect(calls.particles).toHaveLength(0);
  });
});

describe('celebrateLevelUp', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    vi.useFakeTimers();
    phaserGameMock.getPhaserGame.mockReset();
    audioMock.playSfx.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    document.body.innerHTML = '';
  });

  it('调用 sfx_jingle_complete 并尝试播 confetti', () => {
    const { game, calls } = makeFakeGame();
    phaserGameMock.getPhaserGame.mockReturnValue(game);

    celebrateLevelUp();

    expect(audioMock.playSfx).toHaveBeenCalledWith('sfx_jingle_complete', { volume: 0.5 });
    expect(calls.particles).toHaveLength(1);
    expect(calls.particles[0]?.key).toBe('fx_confetti_burst');
  });

  it('Phaser 缺席时不抛错', () => {
    phaserGameMock.getPhaserGame.mockReturnValue(null);

    expect(() => celebrateLevelUp()).not.toThrow();
    expect(audioMock.playSfx).toHaveBeenCalledWith('sfx_jingle_complete', { volume: 0.5 });
  });
});
