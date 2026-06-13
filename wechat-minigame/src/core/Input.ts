// 触摸输入：左下角方向键 + 任意处「滑动摇杆」移动；右下角交互键 + 轻点产出离散交互动作。
// 移动是「轮询」（getDirection），交互是「事件」（consumeActions，边沿触发，每次轻点/按键一次）。
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
  computeInteractButton,
  directionAtPoint,
  rectContains,
  type Direction,
  type DpadLayout,
  type Rect,
} from './layout';

interface TouchState {
  startX: number;
  startY: number;
  curX: number;
  curY: number;
  fromDpad: boolean;
  fromInteract: boolean;
  moved: boolean;
}

/** 离散交互动作：交互键按下，或屏幕上的一次轻点（带坐标，供对话框命中测试）。 */
export type InputAction = { type: 'interact' } | { type: 'tap'; x: number; y: number };

const JOYSTICK_DEADZONE = 18;
// 位移超过此阈值即视为「滑动」（摇杆），不再算作轻点。
const TAP_SLOP = 12;
// 无动作时复用同一个空数组，避免每帧 consumeActions 分配新数组（主循环逐帧调用）。
const EMPTY_ACTIONS: readonly InputAction[] = [];

export class Input {
  private dpad: DpadLayout;
  private interact: Rect;
  private readonly touches = new Map<number, TouchState>();
  private actions: InputAction[] = [];

  constructor(viewWidth: number, viewHeight: number) {
    this.dpad = computeDpad(viewWidth, viewHeight);
    this.interact = computeInteractButton(viewWidth, viewHeight);
    onTouchStart((e) => this.onStart(e));
    onTouchMove((e) => this.onMove(e));
    onTouchEnd((e) => this.onEnd(e));
    onTouchCancel((e) => this.onEnd(e));
  }

  resize(viewWidth: number, viewHeight: number): void {
    this.dpad = computeDpad(viewWidth, viewHeight);
    this.interact = computeInteractButton(viewWidth, viewHeight);
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

  /** 供 HUD 高亮：交互键是否正被按住。 */
  getInteractHeld(): boolean {
    for (const touch of this.touches.values()) {
      if (touch.fromInteract && rectContains(this.interact, touch.curX, touch.curY)) {
        return true;
      }
    }
    return false;
  }

  /** 取走自上次调用以来累计的交互动作（边沿触发，取走即清空）。 */
  consumeActions(): readonly InputAction[] {
    if (this.actions.length === 0) {
      return EMPTY_ACTIONS;
    }
    const taken = this.actions;
    this.actions = [];
    return taken;
  }

  private directionOf(touch: TouchState): Direction | null {
    if (touch.fromInteract) {
      return null;
    }
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
      const fromInteract = rectContains(this.interact, touch.clientX, touch.clientY);
      this.touches.set(touch.identifier, {
        startX: touch.clientX,
        startY: touch.clientY,
        curX: touch.clientX,
        curY: touch.clientY,
        fromDpad,
        fromInteract,
        moved: false,
      });
      if (fromInteract) {
        this.actions.push({ type: 'interact' });
      }
    }
  }

  private onMove(event: GameTouchEvent): void {
    for (const touch of event.changedTouches) {
      this.updateTouch(touch);
    }
  }

  private onEnd(event: GameTouchEvent): void {
    for (const touch of event.changedTouches) {
      const state = this.touches.get(touch.identifier);
      if (state && !state.fromDpad && !state.fromInteract && !state.moved) {
        this.actions.push({ type: 'tap', x: state.curX, y: state.curY });
      }
      this.touches.delete(touch.identifier);
    }
  }

  private updateTouch(touch: GameTouch): void {
    const state = this.touches.get(touch.identifier);
    if (!state) {
      return;
    }
    state.curX = touch.clientX;
    state.curY = touch.clientY;
    if (
      Math.abs(state.curX - state.startX) > TAP_SLOP ||
      Math.abs(state.curY - state.startY) > TAP_SLOP
    ) {
      state.moved = true;
    }
  }
}
