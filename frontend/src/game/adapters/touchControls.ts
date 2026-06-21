// 隔离原因：移动端虚拟摇杆（DOM 层 VirtualJoystick.vue）与游戏输入（Phaser
// 场景内的 createDirectionController）之间的最小共享状态层，避免 DOM 组件直接
// 依赖 Phaser、也避免 input.ts 反向依赖 Vue。
//
// 设计原因：WorldScene 每帧读取 DirectionController（方向为「按住态」level，
// 动作/工具/跳跃由场景自行做边沿检测）。虚拟摇杆只需把当前方向与按钮按住态
// 写入这里，createDirectionController 在键盘无输入时回退读取本状态即可。
import type { ExtendedDirection } from './input';

export type TouchButton = 'action' | 'jump' | 'run' | 'tool';

interface TouchControlState {
  /** 当前摇杆方向（8 向）；无输入为 null。 */
  direction: ExtendedDirection | null;
  /** 互动键（对应键盘 F/E）按住态。 */
  action: boolean;
  /** 跳跃键（对应键盘 Space）按住态。 */
  jump: boolean;
  /** 奔跑修饰（对应键盘 Shift）按住/切换态。 */
  run: boolean;
  /** 工具键（对应键盘 C）按住态。 */
  tool: boolean;
}

const state: TouchControlState = {
  direction: null,
  action: false,
  jump: false,
  run: false,
  tool: false,
};

export function setTouchDirection(direction: ExtendedDirection | null): void {
  state.direction = direction;
}

export function setTouchButton(button: TouchButton, pressed: boolean): void {
  state[button] = pressed;
}

export function getTouchControlState(): Readonly<TouchControlState> {
  return state;
}

/** 摇杆隐藏 / 组件卸载时清零，避免「按住态」遗留导致角色一直走。 */
export function resetTouchControls(): void {
  state.direction = null;
  state.action = false;
  state.jump = false;
  state.run = false;
  state.tool = false;
}
