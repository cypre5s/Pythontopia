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

/** 右下角圆形交互键，按视口尺寸自适应（与左下方向键镜像，便于右手拇指够到）。 */
export function computeInteractButton(viewWidth: number, viewHeight: number): Rect {
  const size = clamp(Math.round(Math.min(viewWidth, viewHeight) * 0.17), 56, 96);
  const margin = Math.round(size * 0.7);
  return {
    x: viewWidth - margin - size,
    y: viewHeight - margin - size - Math.round(size * 0.6),
    w: size,
    h: size,
  };
}

export interface DialogLayout {
  /** 对话框外框。 */
  box: Rect;
  /** 深链动作按钮（仅学习站点 + 已配置配套小程序时绘制/命中）。 */
  action: Rect;
}

/** 对话框几何：上中部一块面板，避开底部方向键/交互键；动作按钮贴近面板底边。 */
export function computeDialogLayout(viewWidth: number, viewHeight: number): DialogLayout {
  const w = Math.min(viewWidth - 24, 540);
  const h = clamp(Math.round(viewHeight * 0.27), 140, 210);
  const x = Math.round((viewWidth - w) / 2);
  const y = Math.round(Math.min(84, viewHeight * 0.12));
  const pad = 16;
  const actionH = 40;
  return {
    box: { x, y, w, h },
    action: { x: x + pad, y: y + h - 14 - actionH, w: w - pad * 2, h: actionH },
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
