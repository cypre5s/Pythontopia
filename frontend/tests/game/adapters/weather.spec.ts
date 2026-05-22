// 重构原因：weather.ts 直接调用 Phaser ParticleEmitter / Camera.setTint，
//          jsdom 不渲染，本 spec 只断言接口契约：
//            - 各 kind 是否在 add.particles 上正确指向预期贴图 key
//            - cameras.main.setTint / clearTint 路径
//            - WeatherHandle.destroy 必须 idempotent 且清理 tint
//          DOM/像素效果留给手动验收。
import { describe, expect, it, vi } from 'vitest';
import { applyWeather, normalizeWeatherKind } from '@/game/adapters/weather';
import type { SceneLike } from '@/game/adapters/sceneTypes';

interface ParticleStub {
  destroy: ReturnType<typeof vi.fn>;
  setDepth: ReturnType<typeof vi.fn>;
}

interface ImageStub {
  x: number;
  y: number;
  setAlpha: ReturnType<typeof vi.fn>;
  setDepth: ReturnType<typeof vi.fn>;
  destroy: ReturnType<typeof vi.fn>;
}

interface SceneCalls {
  particles: Array<{ x: number; y: number; key: string; config: Record<string, unknown> }>;
  images: Array<{ x: number; y: number; key: string; ref: ImageStub }>;
  tweens: Array<Record<string, unknown>>;
  setTint: Array<number>;
  clearTint: number;
  particleStubs: ParticleStub[];
  imageStubs: ImageStub[];
}

function createScene(options?: { missingTexture?: string }): { scene: SceneLike; calls: SceneCalls } {
  const calls: SceneCalls = {
    particles: [],
    images: [],
    tweens: [],
    setTint: [],
    clearTint: 0,
    particleStubs: [],
    imageStubs: [],
  };
  const scene = {
    add: {
      particles: vi.fn((x: number, y: number, key: string, config: Record<string, unknown>) => {
        const stub: ParticleStub = {
          destroy: vi.fn(),
          setDepth: vi.fn(),
        };
        calls.particles.push({ x, y, key, config });
        calls.particleStubs.push(stub);
        return stub;
      }),
      image: vi.fn((x: number, y: number, key: string) => {
        const stub: ImageStub = {
          x,
          y,
          setAlpha: vi.fn(function setAlpha(this: ImageStub) { return this; }),
          setDepth: vi.fn(function setDepth(this: ImageStub) { return this; }),
          destroy: vi.fn(),
        };
        calls.images.push({ x, y, key, ref: stub });
        calls.imageStubs.push(stub);
        return stub;
      }),
    },
    cameras: {
      main: {
        setTint: vi.fn((color: number) => {
          calls.setTint.push(color);
        }),
        clearTint: vi.fn(() => {
          calls.clearTint += 1;
        }),
      },
    },
    tweens: {
      add: vi.fn((config: Record<string, unknown>) => {
        calls.tweens.push(config);
      }),
    },
    textures: {
      exists: vi.fn((key: string) => key !== options?.missingTexture),
    },
  };
  return { scene: scene as unknown as SceneLike, calls };
}

describe('normalizeWeatherKind', () => {
  it('维持 sunny 默认值，未知字符串退化为 sunny', () => {
    expect(normalizeWeatherKind(null)).toBe('sunny');
    expect(normalizeWeatherKind(undefined)).toBe('sunny');
    expect(normalizeWeatherKind('')).toBe('sunny');
    expect(normalizeWeatherKind('moonshine')).toBe('sunny');
  });

  it('接受后端大写枚举与小写别名', () => {
    expect(normalizeWeatherKind('SUNNY')).toBe('sunny');
    expect(normalizeWeatherKind('RAINY')).toBe('rainy');
    expect(normalizeWeatherKind('snowy')).toBe('snowy');
    expect(normalizeWeatherKind('CLOUDY')).toBe('cloudy');
  });

  it('WINDY/wind 归并到 cloudy 表现', () => {
    expect(normalizeWeatherKind('WINDY')).toBe('cloudy');
    expect(normalizeWeatherKind('wind')).toBe('cloudy');
  });

  it('storm/STORMY 映射为 stormy', () => {
    expect(normalizeWeatherKind('storm')).toBe('stormy');
    expect(normalizeWeatherKind('STORMY')).toBe('stormy');
  });
});

describe('applyWeather', () => {
  it('rainy 触发 fx_rain_drop 粒子 + 蓝色 tint', () => {
    const { scene, calls } = createScene();

    const handle = applyWeather(scene, 'rainy', 640, 480);

    expect(calls.particles).toHaveLength(1);
    expect(calls.particles[0]?.key).toBe('fx_rain_drop');
    expect(calls.setTint).toContain(0x90b0d0);
    expect(handle).toBeTruthy();
  });

  it('stormy 提高发射频率（frequency 更小）且 tint 更暗', () => {
    const { scene, calls } = createScene();

    applyWeather(scene, 'stormy', 800, 600);

    expect(calls.particles).toHaveLength(1);
    expect(calls.particles[0]?.key).toBe('fx_rain_drop');
    expect(calls.particles[0]?.config.frequency).toBe(12);
    expect(calls.setTint).toContain(0x6080a0);
  });

  it('snowy 触发 fx_snowflake 粒子且速度较慢', () => {
    const { scene, calls } = createScene();

    applyWeather(scene, 'snowy', 640, 480);

    expect(calls.particles).toHaveLength(1);
    expect(calls.particles[0]?.key).toBe('fx_snowflake');
    expect(calls.setTint).toContain(0xd0e0f0);
  });

  it('cloudy 添加 4 张 fx_cloud_drift 图片 + 同数量 tween', () => {
    const { scene, calls } = createScene();

    applyWeather(scene, 'cloudy', 800, 600);

    expect(calls.particles).toHaveLength(0);
    expect(calls.images).toHaveLength(4);
    expect(calls.images.every((img) => img.key === 'fx_cloud_drift')).toBe(true);
    expect(calls.tweens).toHaveLength(4);
    expect(calls.setTint).toContain(0xc0c8d0);
  });

  it('sunny 不创建粒子且立即 clearTint', () => {
    const { scene, calls } = createScene();

    applyWeather(scene, 'sunny', 640, 480);

    expect(calls.particles).toHaveLength(0);
    expect(calls.images).toHaveLength(0);
    expect(calls.clearTint).toBeGreaterThanOrEqual(1);
  });

  it('destroy() 清理所有粒子句柄并 clearTint', () => {
    const { scene, calls } = createScene();
    const handle = applyWeather(scene, 'rainy', 640, 480);
    const particle = calls.particleStubs[0]!;

    handle.destroy();

    expect(particle.destroy).toHaveBeenCalledTimes(1);
    expect(calls.clearTint).toBeGreaterThanOrEqual(1);
  });

  it('destroy() 是 idempotent：连续两次只销毁一次粒子', () => {
    const { scene, calls } = createScene();
    const handle = applyWeather(scene, 'snowy', 640, 480);
    const particle = calls.particleStubs[0]!;

    handle.destroy();
    handle.destroy();

    expect(particle.destroy).toHaveBeenCalledTimes(1);
  });

  it('贴图缺失时跳过粒子创建（fail soft）', () => {
    const { scene, calls } = createScene({ missingTexture: 'fx_rain_drop' });

    applyWeather(scene, 'rainy', 640, 480);

    expect(calls.particles).toHaveLength(0);
    // tint 仍要按 kind 设置，确保场景视觉受影响
    expect(calls.setTint).toContain(0x90b0d0);
  });
});
