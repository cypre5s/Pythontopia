// W3 Polish 隔离原因：天气粒子直接使用 Phaser 3.87 的 ParticleEmitter / Camera tint
// API，无法走 SceneLike 抽象；把粒子相关代码集中在本适配器，业务层 (WorldScene、
// stores) 仍只通过 applyWeather + WeatherHandle.destroy 两个结构化入口交互。
//
// 设计取舍：
// - 天气提供「粒子 + 一次性 tint」，timeOfDay 之后会覆盖 tint，因此天气的 tint
//   只在 daynight 缺失（极端测试场景）时短暂生效；正式渲染顺序为
//   先 applyWeather 再 applyTimeOfDay。
// - 销毁路径必须 idempotent：场景切换 + 用户登出都会触发 SHUTDOWN 多次。
import type Phaser from 'phaser';
import type { SceneLike } from './sceneTypes';

export type WeatherKind = 'sunny' | 'rainy' | 'snowy' | 'cloudy' | 'stormy';

export interface WeatherHandle {
  destroy(): void;
}

const WEATHER_TINT: Partial<Record<WeatherKind, number>> = {
  rainy: 0x90b0d0,
  stormy: 0x6080a0,
  snowy: 0xd0e0f0,
  cloudy: 0xc0c8d0,
};

const WEATHER_DEPTH = 80;
const CLOUD_COUNT = 4;
const CLOUD_DEPTH = 78;
const CLOUD_BASE_DURATION_MS = 60_000;
const CLOUD_STEP_DURATION_MS = 5_000;

interface PhaserCamera {
  setTint?: (color: number) => void;
  clearTint?: () => void;
}

interface PhaserTextures {
  exists(key: string): boolean;
}

interface PhaserScene {
  add: {
    particles: (
      x: number,
      y: number,
      key: string,
      config: Record<string, unknown>,
    ) => Phaser.GameObjects.Particles.ParticleEmitter;
    image: (x: number, y: number, key: string) => Phaser.GameObjects.Image;
  };
  cameras: { main: PhaserCamera };
  tweens: { add: (config: Record<string, unknown>) => unknown };
  textures: PhaserTextures;
}

// 把 DTO 大写枚举 + 兜底字符串统一映射成内部小写 kind。
// WINDY 没专门粒子，归为 cloudy 表现（飘云），保证视觉一致。
export function normalizeWeatherKind(input: string | null | undefined): WeatherKind {
  const value = String(input ?? '').toLowerCase();
  if (value === 'rainy' || value === 'rain') return 'rainy';
  if (value === 'snowy' || value === 'snow') return 'snowy';
  if (value === 'cloudy' || value === 'cloud' || value === 'windy' || value === 'wind') return 'cloudy';
  if (value === 'stormy' || value === 'storm') return 'stormy';
  return 'sunny';
}

export function applyWeather(
  scene: SceneLike,
  kind: WeatherKind,
  mapWidth: number,
  mapHeight: number,
): WeatherHandle {
  const phaserScene = scene as unknown as PhaserScene;
  const destroyables: Array<{ destroy(): void }> = [];
  const fallbackHeight = Math.max(mapHeight, 240);

  if (kind === 'rainy' || kind === 'stormy') {
    if (textureReady(phaserScene, 'fx_rain_drop')) {
      const rain = phaserScene.add.particles(0, 0, 'fx_rain_drop', {
        x: { min: 0, max: Math.max(mapWidth, 16) },
        y: -10,
        lifespan: 1200,
        speedY: { min: 400, max: 600 },
        speedX: { min: -20, max: 0 },
        scale: { start: 0.5, end: 0.3 },
        alpha: { start: 0.8, end: 0.4 },
        frequency: kind === 'stormy' ? 12 : 28,
      });
      rain.setDepth?.(WEATHER_DEPTH);
      destroyables.push(rain);
    }
  } else if (kind === 'snowy') {
    if (textureReady(phaserScene, 'fx_snowflake')) {
      const snow = phaserScene.add.particles(0, 0, 'fx_snowflake', {
        x: { min: 0, max: Math.max(mapWidth, 16) },
        y: -10,
        lifespan: 4000,
        speedY: { min: 60, max: 100 },
        speedX: { min: -30, max: 30 },
        scale: { start: 0.6, end: 0.6 },
        alpha: { start: 0.9, end: 0.7 },
        frequency: 50,
      });
      snow.setDepth?.(WEATHER_DEPTH);
      destroyables.push(snow);
    }
  } else if (kind === 'cloudy') {
    if (textureReady(phaserScene, 'fx_cloud_drift')) {
      for (let i = 0; i < CLOUD_COUNT; i += 1) {
        const cloud = phaserScene.add.image(Math.random() * mapWidth, 40 + i * 50, 'fx_cloud_drift');
        cloud.setAlpha?.(0.5);
        cloud.setDepth?.(CLOUD_DEPTH);
        phaserScene.tweens.add({
          targets: cloud,
          x: cloud.x + mapWidth,
          duration: CLOUD_BASE_DURATION_MS + i * CLOUD_STEP_DURATION_MS,
          repeat: -1,
        });
        destroyables.push(cloud);
      }
    }
  }
  // 不强行 silence fallbackHeight 警告：保留参数语义，未来 stormy 闪电特效会复用。
  void fallbackHeight;

  const weatherTint = WEATHER_TINT[kind];
  if (weatherTint !== undefined) {
    phaserScene.cameras.main.setTint?.(weatherTint);
  } else {
    phaserScene.cameras.main.clearTint?.();
  }

  let destroyed = false;
  return {
    destroy(): void {
      if (destroyed) return;
      destroyed = true;
      destroyables.forEach((h) => {
        try {
          h.destroy();
        } catch {
          // particle 已被场景统一销毁时再次 destroy 会抛错，忽略即可。
        }
      });
      try {
        phaserScene.cameras.main.clearTint?.();
      } catch {
        // 场景已销毁时 cameras.main 可能为 null。
      }
    },
  };
}

function textureReady(phaserScene: PhaserScene, key: string): boolean {
  try {
    return phaserScene.textures?.exists?.(key) ?? false;
  } catch {
    return false;
  }
}
