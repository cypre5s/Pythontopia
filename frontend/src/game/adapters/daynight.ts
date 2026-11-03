// 昼夜色调通过结构化入口隔离在 adapter。
//
// 实现要点：
// - 4 段制（morning / noon / evening / night），与课表节奏对齐。
// - 由 worldStore.session 的 inGameMinutes 推导，不引入新 DTO 字段。
// - **用屏幕固定的半透明矩形，而不是 camera.setTint**：Phaser 3.87 的 Camera
//   根本没有 setTint（只有 postFX / setBackgroundColor），旧实现 `setTint?.()`
//   一直是静默空操作——实测同一块草地在 21:40 与 09:00 的截图像素完全相同。
//   现在照 mood.ts 的成熟做法叠一层 scrollFactor=0 的矩形，效果确定可见。
// - noon 不创建任何对象（alpha=0 时直接 no-op），避免白留一个全屏图层。
import type { SceneLike } from './sceneTypes';

export type TimeOfDay = 'morning' | 'noon' | 'evening' | 'night';

interface TimeOverlay {
  color: number;
  alpha: number;
}

/**
 * 各时段叠色。alpha 不要太大：这是「加一层色玻璃」而不是压暗整屏，
 * 0.4 以上会把像素美术的对比度洗掉。
 */
const TIME_OVERLAY: Record<TimeOfDay, TimeOverlay> = {
  morning: { color: 0xffe9b8, alpha: 0.06 },
  noon: { color: 0xffffff, alpha: 0 },
  evening: { color: 0xff8a3c, alpha: 0.18 },
  night: { color: 0x0b1430, alpha: 0.5 },
};

/**
 * 昼夜层深度：压在世界之上（瓦片 0~3 / 道具 4 / NPC 9~12 / 角色 15 / 扬尘 13.5），
 * 但低于 mood 氛围层（150），这样「雨天叠夜晚」时雨的氛围仍然在最外层。
 */
export const TIME_OVERLAY_DEPTH = 140;

/** 屏幕固定层的横向溢出倍数：相机 resize 后仍能盖满视口。 */
const OVERSCAN = 3;

interface PhaserRect {
  setOrigin(x: number, y?: number): PhaserRect;
  setScrollFactor(x: number, y?: number): PhaserRect;
  setDepth(depth: number): PhaserRect;
  setAlpha(alpha: number): PhaserRect;
  setSize?(w: number, h: number): PhaserRect;
  setPosition?(x: number, y: number): PhaserRect;
  destroy(): void;
}

interface PhaserScene {
  cameras?: { main?: { width?: number; height?: number } };
  add?: { rectangle?: (x: number, y: number, w: number, h: number, fill: number, alpha: number) => PhaserRect };
  scale?: { on?: (event: string, cb: (size: { width: number; height: number }) => void) => void; off?: (event: string, cb: (size: { width: number; height: number }) => void) => void };
}

export interface DayNightHandle {
  destroy(): void;
}

export function applyTimeOfDay(scene: SceneLike, time: TimeOfDay): DayNightHandle {
  const overlay = TIME_OVERLAY[time];
  if (!overlay || overlay.alpha <= 0) {
    return { destroy(): void {} };
  }

  const phaserScene = scene as unknown as PhaserScene;
  if (typeof phaserScene.add?.rectangle !== 'function') {
    return { destroy(): void {} };
  }
  const cam = phaserScene.cameras?.main;
  const viewW = cam?.width ?? 960;
  const viewH = cam?.height ?? 640;
  const span = Math.max(viewW, viewH) * OVERSCAN;

  // 第 6 个参数是 **fillAlpha**：传 0 再 setAlpha 的话，形状填充透明度仍是 0，
  // 整层永远画不出来（踩过一次：调试成红色 0.9 依然完全不可见）。
  const rect = phaserScene.add.rectangle(viewW / 2, viewH / 2, span, span, overlay.color, overlay.alpha);
  rect.setScrollFactor(0).setOrigin(0.5).setDepth(TIME_OVERLAY_DEPTH);

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
        rect.destroy();
      } catch {
        // 场景已销毁时忽略。
      }
    },
  };
}

// inGameMinutes（0~24*60）→ TimeOfDay。
// 边界分段：05:00-10:59 morning, 11:00-15:59 noon, 16:00-19:59 evening, 其它 night。
// 这样夜晚 8 小时、黄昏 4 小时、白天 5 小时、清晨 6 小时，演示节奏清晰。
export function deriveTimeOfDay(input: { inGameMinutes?: number | null; timeOfDay?: string | null }): TimeOfDay {
  const explicit = normalizeTimeOfDay(input.timeOfDay);
  if (explicit) return explicit;
  const minutes = typeof input.inGameMinutes === 'number' ? input.inGameMinutes : 12 * 60;
  const wrapped = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60);
  const hour = Math.floor(wrapped / 60);
  if (hour >= 5 && hour < 11) return 'morning';
  if (hour >= 11 && hour < 16) return 'noon';
  if (hour >= 16 && hour < 20) return 'evening';
  return 'night';
}

function normalizeTimeOfDay(input: string | null | undefined): TimeOfDay | null {
  const value = String(input ?? '').toLowerCase();
  if (value === 'morning') return 'morning';
  if (value === 'noon' || value === 'day') return 'noon';
  if (value === 'evening' || value === 'dusk') return 'evening';
  if (value === 'night') return 'night';
  return null;
}

export const __testing = { TIME_OVERLAY };
