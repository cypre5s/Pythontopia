// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
import type { Direction } from '@api/dto/Direction';
import type { ImageLike, PlayerSpriteHandle, SceneLike } from './sceneTypes';

export interface PlayerSpriteSpec {
  tileX: number;
  tileY: number;
  direction: Direction;
  tileSize: number;
}

export function createPlayerSprite(scene: SceneLike, spec: PlayerSpriteSpec): PlayerSpriteHandle {
  const body = scene.add
    .image(tileCenter(spec.tileX, spec.tileSize), tileCenter(spec.tileY, spec.tileSize), 'student_default')
    .setScale(1.35)
    .setDepth(20);
  const handle: PlayerSpriteHandle = {
    x: body.x,
    y: body.y,
    tileX: spec.tileX,
    tileY: spec.tileY,
    direction: spec.direction,
    moveToTile(tileX: number, tileY: number, direction: Direction): void {
      this.tileX = tileX;
      this.tileY = tileY;
      this.direction = direction;
      const nextX = tileCenter(tileX, spec.tileSize);
      const nextY = tileCenter(tileY, spec.tileSize);
      this.x = nextX;
      this.y = nextY;
      moveRectangle(body, nextX, nextY);
    },
  };
  return handle;
}

function tileCenter(tile: number, tileSize: number): number {
  return tile * tileSize + tileSize / 2;
}

function moveRectangle(image: ImageLike, x: number, y: number): void {
  image.x = x;
  image.y = y;
}
