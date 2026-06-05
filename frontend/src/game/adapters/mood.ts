// 隔离原因：氛围叠加层用 Phaser 3 的 GameObjects.Rectangle 直接渲染，
// 业务层（WorldScene、mood store）仍只通过 applyMood + MoodHandle.destroy 两个结构化
// 入口交互。
//
// 设计取舍：
// - 不使用第三次 camera.setTint —— weather.ts 与 daynight.ts 已各调一次，
//   再加会互相覆盖打架。改用全视口 scrollFactor=0 矩形叠加，depth=150 落在
//   weather(80) 与 hint 气泡(200) 之间，低 alpha、背景级、不打扰操作。
// - 矩形以视口中心为锚（origin 0.5 + scrollFactor 0），尺寸按视口最大边 ×3 过扫，
//   保证任意 zoom（1.5~3）/ 滚动下都铺满；scale resize 时重新居中。
// - sunny == 无氛围：返回 no-op 句柄，不创建对象、不留 tween。
// - 销毁路径 idempotent：场景切换 / 关闭氛围会触发多次 destroy。
import type { SceneLike } from './sceneTypes';
import { useGameSettingsStore } from '@stores/gameSettings';

export type MoodKind = 'sunny' | 'cloudy' | 'rainy';

export interface MoodHandle {
  destroy(): void;
}

interface MoodStyle {
  color: number;
  alpha: number;
}

// 连胜→晴；中性/轻挫→冷灰薄云；连挫→暗蓝紫细雨感。alpha 全部克制在「背景级」。
const MOOD_STYLES: Record<MoodKind, MoodStyle> = {
  sunny: { color: 0xffffff, alpha: 0 },
  cloudy: { color: 0x8a93a8, alpha: 0.16 },
  rainy: { color: 0x3a3470, alpha: 0.26 },
};

const MOOD_DEPTH = 150;
const MOOD_FADE_MS = 1400;
const OVERSCAN = 3;

interface PhaserRect {
  setOrigin(x: number, y?: number): PhaserRect;
  setScrollFactor(x: number, y?: number): PhaserRect;
  setDepth(depth: number): PhaserRect;
  setAlpha(alpha: number): PhaserRect;
  setSize?(width: number, height: number): PhaserRect;
  setPosition?(x: number, y: number): PhaserRect;
  destroy(): void;
}

interface PhaserScaleLike {
  on?(event: string, cb: (size: { width: number; height: number }) => void): void;
  off?(event: string, cb: (size: { width: number; height: number }) => void): void;
}

interface PhaserMoodScene {
  add: {
    rectangle: (
      x: number,
      y: number,
      width: number,
      height: number,
      fillColor: number,
      fillAlpha?: number,
    ) => PhaserRect;
  };
  cameras: { main: { width?: number; height?: number } };
  tweens: { add: (config: Record<string, unknown>) => unknown };
  scale?: PhaserScaleLike;
}

function prefersReducedMotion(): boolean {
  try {
    return (
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    );
  } catch {
    return false;
  }
}

function particlesAllowed(): boolean {
  // adapter 内直接读设置（前例：WorldScene 直接读 useMainQuestStore）。
  // store 不可用（极端测试）时默认放行动效，不阻断渲染。
  try {
    return useGameSettingsStore().display.showParticles;
  } catch {
    return true;
  }
}

export function applyMood(
  scene: SceneLike,
  mood: MoodKind,
  _mapWidth: number,
  _mapHeight: number,
): MoodHandle {
  const style = MOOD_STYLES[mood];

  // sunny / 任何 alpha<=0：无氛围叠加，返回 no-op 句柄（不创建对象、不留 tween）。
  if (style.alpha <= 0) {
    return { destroy(): void {} };
  }

  const phaserScene = scene as unknown as PhaserMoodScene;
  const cam = phaserScene.cameras.main;
  const viewW = cam.width ?? 960;
  const viewH = cam.height ?? 640;
  const span = Math.max(viewW, viewH) * OVERSCAN;

  const rect = phaserScene.add.rectangle(viewW / 2, viewH / 2, span, span, style.color, 0);
  rect.setScrollFactor(0).setOrigin(0.5).setDepth(MOOD_DEPTH);

  // 动效降级：reduced-motion 或玩家关闭粒子 → 直接落终值，不做渐显 tween。
  const animate = !prefersReducedMotion() && particlesAllowed();
  if (animate) {
    rect.setAlpha(0);
    phaserScene.tweens.add({
      targets: rect,
      alpha: style.alpha,
      duration: MOOD_FADE_MS,
      ease: 'Sine.easeInOut',
    });
  } else {
    rect.setAlpha(style.alpha);
  }

  const onResize = (size: { width: number; height: number }): void => {
    const nextSpan = Math.max(size.width, size.height) * OVERSCAN;
    rect.setSize?.(nextSpan, nextSpan);
    rect.setPosition?.(size.width / 2, size.height / 2);
  };
  phaserScene.scale?.on?.('resize', onResize);

  let destroyed = false;
  return {
    destroy(): void {
      if (destroyed) return;
      destroyed = true;
      try {
        phaserScene.scale?.off?.('resize', onResize);
      } catch {
        // scale 已随场景销毁时忽略。
      }
      try {
        rect.destroy();
      } catch {
        // 对象已被场景统一销毁时再次 destroy 会抛错，忽略即可。
      }
    },
  };
}
