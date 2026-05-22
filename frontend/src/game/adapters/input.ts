// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
// 设计原因：默认采用「原神风」键位 —— WASD 移动 + F 交互，同时保留方向键作为别名；
//          支持 8 方向（同时按 W+D 等价为 UP_RIGHT），后端只接收 4 方向，
//          调用 toServerDirection 将 8 方向降级为主轴 4 方向。
import type { Direction } from '@api/dto/Direction';
import type { KeyboardKeyLike, SceneLike } from './sceneTypes';

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
}

export function toServerDirection(dir: ExtendedDirection): Direction {
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
  // 兼容旧版 Space/E 触发交互；新版主控为 F。
  const spaceKey: KeyboardKeyLike | null = scene.input.keyboard?.addKey('SPACE') ?? null;
  const eKey: KeyboardKeyLike | null = scene.input.keyboard?.addKey('E') ?? null;

  function isPressed(key: KeyboardKeyLike | null): boolean {
    return key?.isDown === true;
  }

  return {
    getDirection() {
      const up = isPressed(wKey) || isPressed(cursors?.up ?? null);
      const down = isPressed(sKey) || isPressed(cursors?.down ?? null);
      const left = isPressed(aKey) || isPressed(cursors?.left ?? null);
      const right = isPressed(dKey) || isPressed(cursors?.right ?? null);

      if (up && right) {
        return 'UP_RIGHT';
      }
      if (up && left) {
        return 'UP_LEFT';
      }
      if (down && right) {
        return 'DOWN_RIGHT';
      }
      if (down && left) {
        return 'DOWN_LEFT';
      }
      if (up) {
        return 'UP';
      }
      if (down) {
        return 'DOWN';
      }
      if (left) {
        return 'LEFT';
      }
      if (right) {
        return 'RIGHT';
      }
      return null;
    },
    isActionPressed() {
      return isPressed(fKey) || isPressed(spaceKey) || isPressed(eKey);
    },
  };
}
