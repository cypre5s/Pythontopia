// 隔离原因：touchAdapter 通过 scene.input 的 pointer 事件驱动虚拟摇杆，
// 不直接 import phaser 模块，仅 import type 用作类型。
// 设计原因：触屏布局采用「左半屏虚拟摇杆 + 右半屏 action 按钮」的拇指可
// 触达模型。按下时以触点为摇杆原点（动态摇杆），移动距离归一化到
// [-1, 1]；释放后归零。Run / cancel 在触屏上无对应输入，恒为 false。
// pollEnabled 通过 navigator.maxTouchPoints > 0 检测，桌面环境直接返回
// 空 intent，避免与鼠标/键盘冲突。
import type Phaser from 'phaser';
import { computeDirection8, emptyIntent, type InputAdapter, type InputIntent } from './input';

/** 摇杆事件需要的最小指针字段，与 Phaser.Input.Pointer 结构兼容。 */
interface PointerLike {
  id: number;
  x: number;
  y: number;
}

export interface TouchAdapterOptions {
  /** 自定义触屏检测；默认使用 navigator.maxTouchPoints > 0。 */
  isTouchDevice?: () => boolean;
  /** 屏幕宽度。未提供则取 window.innerWidth；用于划分左右半屏。 */
  screenWidth?: number;
  /** 屏幕高度。未提供则取 window.innerHeight，目前仅用于潜在的按钮布局。 */
  screenHeight?: number;
  /** 摇杆最大半径（像素）。触点到原点的距离按该半径归一化。 */
  joystickRadius?: number;
}

const DEFAULT_JOYSTICK_RADIUS = 60;

export function createTouchAdapter(opts: TouchAdapterOptions = {}): InputAdapter {
  const isTouchDevice =
    opts.isTouchDevice ??
    ((): boolean => {
      if (typeof navigator === 'undefined') {
        return false;
      }
      const maxTouchPoints = navigator.maxTouchPoints ?? 0;
      return maxTouchPoints > 0;
    });
  const joystickRadius = opts.joystickRadius ?? DEFAULT_JOYSTICK_RADIUS;

  let attachedScene: Phaser.Scene | null = null;
  let joystickActive = false;
  let joystickPointerId: number | null = null;
  let joystickOriginX = 0;
  let joystickOriginY = 0;
  let moveDx = 0;
  let moveDy = 0;
  let actionDown = false;
  let actionPointerId: number | null = null;
  let prevAction = false;

  function getScreenWidth(): number {
    if (opts.screenWidth !== undefined) {
      return opts.screenWidth;
    }
    if (typeof window !== 'undefined' && Number.isFinite(window.innerWidth)) {
      return window.innerWidth;
    }
    return 800;
  }

  function resetState(): void {
    joystickActive = false;
    joystickPointerId = null;
    joystickOriginX = 0;
    joystickOriginY = 0;
    moveDx = 0;
    moveDy = 0;
    actionDown = false;
    actionPointerId = null;
    prevAction = false;
  }

  function handlePointerDown(pointer: PointerLike): void {
    const width = getScreenWidth();
    if (pointer.x < width / 2) {
      // 左半屏：作为虚拟摇杆原点。若已有手指占用，忽略后续按下。
      if (joystickPointerId !== null) {
        return;
      }
      joystickActive = true;
      joystickPointerId = pointer.id;
      joystickOriginX = pointer.x;
      joystickOriginY = pointer.y;
      moveDx = 0;
      moveDy = 0;
    } else {
      // 右半屏：action 按钮区。
      if (actionPointerId !== null) {
        return;
      }
      actionDown = true;
      actionPointerId = pointer.id;
    }
  }

  function handlePointerMove(pointer: PointerLike): void {
    if (!joystickActive || pointer.id !== joystickPointerId) {
      return;
    }
    const rawDx = pointer.x - joystickOriginX;
    const rawDy = pointer.y - joystickOriginY;
    const mag = Math.sqrt(rawDx * rawDx + rawDy * rawDy);
    if (mag <= 0 || joystickRadius <= 0) {
      moveDx = 0;
      moveDy = 0;
      return;
    }
    const clampedMag = Math.min(mag, joystickRadius);
    // normalizedMag ∈ [0, 1]；除以 mag 再乘原始分量即可得到 [-1,1] 范围分量。
    const scale = clampedMag / joystickRadius / mag;
    moveDx = rawDx * scale;
    moveDy = rawDy * scale;
  }

  function handlePointerUp(pointer: PointerLike): void {
    if (pointer.id === joystickPointerId) {
      joystickActive = false;
      joystickPointerId = null;
      moveDx = 0;
      moveDy = 0;
    }
    if (pointer.id === actionPointerId) {
      actionDown = false;
      actionPointerId = null;
    }
  }

  return {
    poll(): InputIntent {
      if (!isTouchDevice()) {
        prevAction = false;
        return emptyIntent();
      }
      const actionPressed = actionDown && !prevAction;
      prevAction = actionDown;
      const direction = computeDirection8(moveDx, moveDy);
      return {
        move: { dx: moveDx, dy: moveDy },
        direction,
        run: false,
        actionPressed,
        cancelPressed: false,
      };
    },
    attach(scene: Phaser.Scene): void {
      if (attachedScene) {
        // 已绑定时跳过，避免重复 listener。
        return;
      }
      attachedScene = scene;
      const input = scene.input;
      input.on('pointerdown', handlePointerDown);
      input.on('pointermove', handlePointerMove);
      input.on('pointerup', handlePointerUp);
    },
    detach(): void {
      if (!attachedScene) {
        return;
      }
      const input = attachedScene.input;
      input.off('pointerdown', handlePointerDown);
      input.off('pointermove', handlePointerMove);
      input.off('pointerup', handlePointerUp);
      attachedScene = null;
      resetState();
    },
  };
}
