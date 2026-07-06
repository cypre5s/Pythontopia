// 隔离原因：gamepadAdapter 仅依赖浏览器原生 Gamepad API（navigator.getGamepads），
// 不需要 scene 监听，因此 attach 默认是空操作。仅 import type Phaser 用作签名。
// 设计原因：采用 standard mapping —— 左摇杆轴 0/1 → move，A(0)/B(1) → action/cancel，
// 右摇杆按下(11) → run 修饰。死区采用「磁场半径」语义：dx²+dy² 的平方根低于
// 0.15 即归零，避免静态漂移。action/cancel 走边沿触发，与键盘 adapter 行为一致。
import type Phaser from 'phaser';
import { computeDirection8, emptyIntent, type InputAdapter, type InputIntent } from './input';

export interface GamepadAdapterOptions {
  /** 死区半径；左摇杆位移模长低于该值即视为静止。默认 0.15。 */
  deadzone?: number;
  /** 注入手柄列表来源，测试用；默认调用 navigator.getGamepads。 */
  getGamepads?: () => readonly (Gamepad | null)[];
}

const DEFAULT_DEADZONE = 0.15;
// Web Gamepad API standard mapping。
const BUTTON_A = 0;
const BUTTON_B = 1;
const BUTTON_RIGHT_STICK = 11;
const AXIS_LEFT_X = 0;
const AXIS_LEFT_Y = 1;

export function createGamepadAdapter(opts: GamepadAdapterOptions = {}): InputAdapter {
  const deadzone = opts.deadzone ?? DEFAULT_DEADZONE;
  const fetchGamepads =
    opts.getGamepads ??
    ((): readonly (Gamepad | null)[] => {
      if (typeof navigator === 'undefined') {
        return [];
      }
      const nav = navigator as Navigator & {
        getGamepads?: () => readonly (Gamepad | null)[];
      };
      if (typeof nav.getGamepads !== 'function') {
        return [];
      }
      return nav.getGamepads();
    });

  let prevAction = false;
  let prevCancel = false;

  function pickActiveGamepad(): Gamepad | null {
    const list = fetchGamepads();
    for (const gp of list) {
      if (gp && gp.connected) {
        return gp;
      }
    }
    return null;
  }

  function applyDeadzone(rawX: number, rawY: number): { dx: number; dy: number } {
    const mag = Math.sqrt(rawX * rawX + rawY * rawY);
    if (mag < deadzone) {
      return { dx: 0, dy: 0 };
    }
    return { dx: rawX, dy: rawY };
  }

  return {
    poll(): InputIntent {
      const gp = pickActiveGamepad();
      if (!gp) {
        prevAction = false;
        prevCancel = false;
        return emptyIntent();
      }
      const rawX = gp.axes[AXIS_LEFT_X] ?? 0;
      const rawY = gp.axes[AXIS_LEFT_Y] ?? 0;
      const { dx, dy } = applyDeadzone(rawX, rawY);

      const aDown = gp.buttons[BUTTON_A]?.pressed === true;
      const bDown = gp.buttons[BUTTON_B]?.pressed === true;
      const rsDown = gp.buttons[BUTTON_RIGHT_STICK]?.pressed === true;

      const actionPressed = aDown && !prevAction;
      const cancelPressed = bDown && !prevCancel;
      prevAction = aDown;
      prevCancel = bDown;

      return {
        move: { dx, dy },
        direction: computeDirection8(dx, dy),
        run: rsDown,
        actionPressed,
        cancelPressed,
      };
    },
    attach(_scene: Phaser.Scene): void {
      // Gamepad API 是全局的，无需注册场景 listener。保留接口对齐统一抽象。
    },
    detach(): void {
      prevAction = false;
      prevCancel = false;
    },
  };
}
