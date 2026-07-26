// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
// 设计原因：
//   - 旧 API（createDirectionController / DirectionController /
//     ExtendedDirection / toServerDirection）保持向后兼容，TownScene 与
//     WorldScene 仍在使用，签名与行为不变。
//   - 新 API（InputAdapter / InputIntent / Direction8）面向多设备输入抽象：
//     键盘 / 触屏虚拟摇杆 / 手柄三套实现可单独测试，由组合 adapter 在单例层
//     按 gamepad > keyboard > touch 的优先级合成 intent，避免多源冲突。
//   - Phaser 仅以 `import type` 方式引入，运行时不依赖 phaser 模块，便于
//     jsdom 单测加载。
import type Phaser from 'phaser';
import type { CardinalDirection } from '@api/dto/Direction';
import type { KeyboardKeyLike, SceneLike } from './sceneTypes';
import { createKeyboardAdapter } from './keyboardAdapter';
import { createTouchAdapter } from './touchAdapter';
import { createGamepadAdapter } from './gamepadAdapter';
import { getTouchControlState } from './touchControls';

export type ExtendedDirection =
  | 'UP'
  | 'DOWN'
  | 'LEFT'
  | 'RIGHT'
  | 'UP_LEFT'
  | 'UP_RIGHT'
  | 'DOWN_LEFT'
  | 'DOWN_RIGHT';

export interface DirectionController {
  getDirection(): ExtendedDirection | null;
  isActionPressed(): boolean;
  isJumpPressed(): boolean;
  /** 奔跑修饰键（Shift）按住时为 true：星露谷式「按住跑、松开走」。 */
  isRunPressed(): boolean;
  /** 工具/动作键（C）：触发与当前地图相称的工具动作（浇水/挖矿/钓鱼/施法/庆祝）。 */
  isToolPressed(): boolean;
}

/**
 * 写后端前的方向降级：后端 `PlayerStatePatch.direction` 只接受 4 向。
 * 返回类型收窄为 CardinalDirection（而非 8 向的 Direction），让调用点拿到的就是
 * 编译期可证的 4 向值 —— 见 WorldScene.persistPlayerTile（唯一写回口）。
 */
export function toServerDirection(dir: ExtendedDirection): CardinalDirection {
  switch (dir) {
    case 'UP':
    case 'UP_LEFT':
    case 'UP_RIGHT':
      return 'UP';
    case 'DOWN':
    case 'DOWN_LEFT':
    case 'DOWN_RIGHT':
      return 'DOWN';
    case 'LEFT':
      return 'LEFT';
    case 'RIGHT':
      return 'RIGHT';
  }
}

export function createDirectionController(scene: SceneLike): DirectionController {
  const cursors = scene.input.keyboard?.createCursorKeys() ?? null;
  const wKey: KeyboardKeyLike | null = scene.input.keyboard?.addKey('W') ?? null;
  const aKey: KeyboardKeyLike | null = scene.input.keyboard?.addKey('A') ?? null;
  const sKey: KeyboardKeyLike | null = scene.input.keyboard?.addKey('S') ?? null;
  const dKey: KeyboardKeyLike | null = scene.input.keyboard?.addKey('D') ?? null;
  const fKey: KeyboardKeyLike | null = scene.input.keyboard?.addKey('F') ?? null;
  // F/E 交互，Space 留给跳跃。
  const spaceKey: KeyboardKeyLike | null = scene.input.keyboard?.addKey('SPACE') ?? null;
  const eKey: KeyboardKeyLike | null = scene.input.keyboard?.addKey('E') ?? null;
  // Shift 奔跑，C 使用工具（与交互/跳跃键互不冲突）。
  const shiftKey: KeyboardKeyLike | null = scene.input.keyboard?.addKey('SHIFT') ?? null;
  const cKey: KeyboardKeyLike | null = scene.input.keyboard?.addKey('C') ?? null;

  function isPressed(key: KeyboardKeyLike | null): boolean {
    return key?.isDown === true;
  }

  return {
    getDirection() {
      const up = isPressed(wKey) || isPressed(cursors?.up ?? null);
      const down = isPressed(sKey) || isPressed(cursors?.down ?? null);
      const left = isPressed(aKey) || isPressed(cursors?.left ?? null);
      const right = isPressed(dKey) || isPressed(cursors?.right ?? null);

      let dir: ExtendedDirection | null = null;
      if (up && right) {
        dir = 'UP_RIGHT';
      } else if (up && left) {
        dir = 'UP_LEFT';
      } else if (down && right) {
        dir = 'DOWN_RIGHT';
      } else if (down && left) {
        dir = 'DOWN_LEFT';
      } else if (up) {
        dir = 'UP';
      } else if (down) {
        dir = 'DOWN';
      } else if (left) {
        dir = 'LEFT';
      } else if (right) {
        dir = 'RIGHT';
      }

      // 键盘无方向时回退到移动端虚拟摇杆。
      return dir ?? getTouchControlState().direction;
    },
    isActionPressed() {
      return isPressed(fKey) || isPressed(eKey) || getTouchControlState().action;
    },
    isJumpPressed() {
      return isPressed(spaceKey) || getTouchControlState().jump;
    },
    isRunPressed() {
      return isPressed(shiftKey) || getTouchControlState().run;
    },
    isToolPressed() {
      return isPressed(cKey) || getTouchControlState().tool;
    },
  };
}

// ===================================================================
// 新 API：8 方向 InputIntent + 多设备 InputAdapter
// ===================================================================

/**
 * 罗盘 8 方向。约定：屏幕坐标系下 +y 朝下，因此 N=(-y) / S=(+y) /
 * E=(+x) / W=(-x)。NE/SE/SW/NW 类推。
 */
export type Direction8 = 'N' | 'NE' | 'E' | 'SE' | 'S' | 'SW' | 'W' | 'NW';

/**
 * 每帧输入快照。不同设备 adapter 的输出统一成该结构，供业务层消费。
 */
export interface InputIntent {
  /** 归一化方向向量；键盘为 -1/0/1，触屏/手柄为 [-1,1]。0 表示无输入。 */
  move: { dx: number; dy: number };
  /** 由 move 离散化得到的 8 方向；move 全 0 时为 null。 */
  direction: Direction8 | null;
  /** 奔跑修饰键（Shift / 右摇杆按下）。 */
  run: boolean;
  /** 当前帧是否首次按下交互键（E/F / A 键），边沿触发。 */
  actionPressed: boolean;
  /** 当前帧是否首次按下取消/菜单键（Esc / B 键），边沿触发。 */
  cancelPressed: boolean;
}

/**
 * 单一设备的输入抽象。
 *
 * - poll：每帧调用，返回 InputIntent 快照（包括边沿触发标志）。
 * - attach：绑定到具体 Phaser scene，注册键盘 / 指针监听等。
 * - detach：scene shutdown 前清理 listener，避免泄漏。
 */
export interface InputAdapter {
  poll(): InputIntent;
  attach(scene: Phaser.Scene): void;
  detach(): void;
}

// 扇区顺序：sector 0 起点对应 E（+x 轴），每 45° 顺时针递进。
// 0=E, 1=SE, 2=S, 3=SW, 4=W, 5=NW, 6=N, 7=NE。
const DIRECTION8_ORDER: readonly Direction8[] = [
  'E',
  'SE',
  'S',
  'SW',
  'W',
  'NW',
  'N',
  'NE',
];

/**
 * 由 dx/dy 计算 8 方向。dx=0 且 dy=0 时返回 null。
 *
 * 算法：用 atan2(dy, dx) 取得弧度后，整体偏移 π/8（半个扇区）使 E 扇区
 * 横跨 [-22.5°, 22.5°]，再以 π/4 划分 8 个扇区。
 */
export function computeDirection8(dx: number, dy: number): Direction8 | null {
  if (dx === 0 && dy === 0) {
    return null;
  }
  const angle = Math.atan2(dy, dx);
  let normalized = angle + Math.PI / 8;
  if (normalized < 0) {
    normalized += Math.PI * 2;
  }
  const sector = Math.floor(normalized / (Math.PI / 4)) % 8;
  return DIRECTION8_ORDER[sector] ?? null;
}

/** 返回一个全空 intent，供 adapter 在无输入时复用。 */
export function emptyIntent(): InputIntent {
  return {
    move: { dx: 0, dy: 0 },
    direction: null,
    run: false,
    actionPressed: false,
    cancelPressed: false,
  };
}

/** 判断一个 intent 是否包含任何输入信号，用于组合 adapter 选 winner。 */
export function hasAnyInput(intent: InputIntent): boolean {
  return (
    intent.move.dx !== 0 ||
    intent.move.dy !== 0 ||
    intent.run ||
    intent.actionPressed ||
    intent.cancelPressed
  );
}

/**
 * 组合 adapter：每帧同时 poll 三个子 adapter，按 gamepad > keyboard > touch
 * 的优先级返回首个含输入的 intent；都没有则返回空 intent。
 *
 * 三个子 adapter 各自维护边沿状态，poll 调用顺序保证每帧都推进，避免
 * “没赢的 adapter 丢失下一帧边沿” 的问题。
 */
export function createCompositeAdapter(
  keyboard: InputAdapter,
  touch: InputAdapter,
  gamepad: InputAdapter,
): InputAdapter {
  return {
    poll(): InputIntent {
      const gp = gamepad.poll();
      const kb = keyboard.poll();
      const tc = touch.poll();
      if (hasAnyInput(gp)) {
        return gp;
      }
      if (hasAnyInput(kb)) {
        return kb;
      }
      if (hasAnyInput(tc)) {
        return tc;
      }
      return emptyIntent();
    },
    attach(scene: Phaser.Scene): void {
      keyboard.attach(scene);
      touch.attach(scene);
      gamepad.attach(scene);
    },
    detach(): void {
      keyboard.detach();
      touch.detach();
      gamepad.detach();
    },
  };
}

let inputAdapterSingleton: InputAdapter | null = null;

/**
 * 取得跨场景共享的输入 adapter 单例（默认三设备组合）。
 *
 * scene 切换时不应重新创建 adapter，避免边沿状态被重置；只有 attach 的
 * scene 句柄会随之切换。
 */
export function getInputAdapter(): InputAdapter {
  if (!inputAdapterSingleton) {
    inputAdapterSingleton = createCompositeAdapter(
      createKeyboardAdapter(),
      createTouchAdapter(),
      createGamepadAdapter(),
    );
  }
  return inputAdapterSingleton;
}

/**
 * 仅测试用：注入 mock adapter，或传 null 让下一次 getInputAdapter 重新
 * 构造默认组合 adapter。
 */
export function setInputAdapterForTest(adapter: InputAdapter | null): void {
  inputAdapterSingleton = adapter;
}
