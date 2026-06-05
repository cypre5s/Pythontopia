// HUD 几何（逻辑 CSS 像素，与触摸 clientX/clientY 同坐标系），供 Input 与 Hud 共用。

export type Direction = 'up' | 'down' | 'left' | 'right';

export interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface DpadLayout {
  button: number;
  gap: number;
  up: Rect;
  down: Rect;
  left: Rect;
  right: Rect;
}

export function rectContains(rect: Rect, px: number, py: number): boolean {
  return px >= rect.x && px <= rect.x + rect.w && py >= rect.y && py <= rect.y + rect.h;
}

/** 左下角十字方向键布局，按视口尺寸自适应。 */
export function computeDpad(viewWidth: number, viewHeight: number): DpadLayout {
  const button = clamp(Math.round(Math.min(viewWidth, viewHeight) * 0.13), 40, 76);
  const gap = Math.round(button * 0.12);
  const margin = Math.round(button * 0.7);
  const originX = margin;
  const originY = viewHeight - margin - (button * 3 + gap * 2);
  const colMid = originX + button + gap;
  const rowMid = originY + button + gap;
  return {
    button,
    gap,
    up: { x: colMid, y: originY, w: button, h: button },
    left: { x: originX, y: rowMid, w: button, h: button },
    right: { x: colMid + button + gap, y: rowMid, w: button, h: button },
    down: { x: colMid, y: rowMid + button + gap, w: button, h: button },
  };
}

export function directionAtPoint(dpad: DpadLayout, px: number, py: number): Direction | null {
  if (rectContains(dpad.up, px, py)) {
    return 'up';
  }
  if (rectContains(dpad.down, px, py)) {
    return 'down';
  }
  if (rectContains(dpad.left, px, py)) {
    return 'left';
  }
  if (rectContains(dpad.right, px, py)) {
    return 'right';
  }
  return null;
}

function clamp(value: number, min: number, max: number): number {
  if (value < min) {
    return min;
  }
  if (value > max) {
    return max;
  }
  return value;
}
