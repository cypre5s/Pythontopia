// 触摸输入：左下角方向键 + 任意处「滑动摇杆」。统一产出一个当前移动方向。
import {
  onTouchCancel,
  onTouchEnd,
  onTouchMove,
  onTouchStart,
  type GameTouch,
  type GameTouchEvent,
} from '../runtime/wx';
import {
  computeDpad,
  directionAtPoint,
  type Direction,
  type DpadLayout,
} from './layout';

interface TouchState {
  startX: number;
  startY: number;
  curX: number;
  curY: number;
  fromDpad: boolean;
}

const JOYSTICK_DEADZONE = 18;

export class Input {
  private dpad: DpadLayout;
  private readonly touches = new Map<number, TouchState>();

  constructor(viewWidth: number, viewHeight: number) {
    this.dpad = computeDpad(viewWidth, viewHeight);
    onTouchStart((e) => this.onStart(e));
    onTouchMove((e) => this.onMove(e));
    onTouchEnd((e) => this.onEnd(e));
    onTouchCancel((e) => this.onEnd(e));
  }

  resize(viewWidth: number, viewHeight: number): void {
    this.dpad = computeDpad(viewWidth, viewHeight);
  }

  getDpad(): DpadLayout {
    return this.dpad;
  }

  /** 当前请求移动方向（无输入返回 null）。最近活跃的触摸优先。 */
  getDirection(): Direction | null {
    let result: Direction | null = null;
    for (const touch of this.touches.values()) {
      const dir = this.directionOf(touch);
      if (dir) {
        result = dir;
      }
    }
    return result;
  }

  /** 供 HUD 高亮：当前被按下的方向键。 */
  getHeldButton(): Direction | null {
    let held: Direction | null = null;
    for (const touch of this.touches.values()) {
      const dir = directionAtPoint(this.dpad, touch.curX, touch.curY);
      if (dir) {
        held = dir;
      }
    }
    return held;
  }

  private directionOf(touch: TouchState): Direction | null {
    const onDpad = directionAtPoint(this.dpad, touch.curX, touch.curY);
    if (onDpad) {
      return onDpad;
    }
    if (touch.fromDpad) {
      return null;
    }
    const dx = touch.curX - touch.startX;
    const dy = touch.curY - touch.startY;
    if (Math.abs(dx) < JOYSTICK_DEADZONE && Math.abs(dy) < JOYSTICK_DEADZONE) {
      return null;
    }
    if (Math.abs(dx) >= Math.abs(dy)) {
      return dx >= 0 ? 'right' : 'left';
    }
    return dy >= 0 ? 'down' : 'up';
  }

  private onStart(event: GameTouchEvent): void {
    for (const touch of event.changedTouches) {
      const fromDpad = directionAtPoint(this.dpad, touch.clientX, touch.clientY) !== null;
      this.touches.set(touch.identifier, {
        startX: touch.clientX,
        startY: touch.clientY,
        curX: touch.clientX,
        curY: touch.clientY,
        fromDpad,
      });
    }
  }

  private onMove(event: GameTouchEvent): void {
    for (const touch of event.changedTouches) {
      this.updateTouch(touch);
    }
  }

  private onEnd(event: GameTouchEvent): void {
    for (const touch of event.changedTouches) {
      this.touches.delete(touch.identifier);
    }
  }

  private updateTouch(touch: GameTouch): void {
    const state = this.touches.get(touch.identifier);
    if (state) {
      state.curX = touch.clientX;
      state.curY = touch.clientY;
    }
  }
}
