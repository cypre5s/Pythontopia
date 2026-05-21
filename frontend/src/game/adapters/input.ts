// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
import type { Direction } from '@api/dto/Direction';
import type { KeyboardKeyLike, SceneLike } from './sceneTypes';

export interface DirectionController {
  getDirection(): Direction | null;
  isActionPressed(): boolean;
}

export function createDirectionController(scene: SceneLike): DirectionController {
  const cursors = scene.input.keyboard?.createCursorKeys();
  const actionKey: KeyboardKeyLike | null = scene.input.keyboard?.addKey('E') ?? null;
  return {
    getDirection() {
      if (!cursors) {
        return null;
      }
      if (cursors.left.isDown) {
        return 'LEFT';
      }
      if (cursors.right.isDown) {
        return 'RIGHT';
      }
      if (cursors.up.isDown) {
        return 'UP';
      }
      if (cursors.down.isDown) {
        return 'DOWN';
      }
      return null;
    },
    isActionPressed() {
      return actionKey?.isDown ?? false;
    },
  };
}
