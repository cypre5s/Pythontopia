// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
import type { Direction } from '@api/dto/Direction';
import type { ImageLike, PlayerSpriteHandle, SceneLike } from './sceneTypes';

export interface PlayerSpriteSpec {
  tileX: number;
  tileY: number;
  direction: Direction;
  tileSize: number;
}

// LPC SaraFullSheet 832×1344, 64×64 帧 / 13 列 × 21 行
// row 8 = walk up, row 9 = walk left, row 10 = walk down, row 11 = walk right
// 每行 9 个 walk 帧（含 frame 0 idle，1~8 是 walk cycle）
const LPC_COLS = 13;
const LPC_WALK_ROW: Record<Direction, number> = {
  UP: 8,
  LEFT: 9,
  DOWN: 10,
  RIGHT: 11,
};
const LPC_WALK_FRAMES_PER_DIR = 9;
const LPC_PLAYER_KEY = 'player_sara';
const LPC_FALLBACK_KEY = 'student_default';

function lpcIdleFrame(dir: Direction): number {
  return LPC_WALK_ROW[dir] * LPC_COLS;
}

function lpcStepFrame(dir: Direction, step: number): number {
  const base = LPC_WALK_ROW[dir] * LPC_COLS;
  const offset = ((step - 1) % (LPC_WALK_FRAMES_PER_DIR - 1)) + 1;
  return base + offset;
}

export function createPlayerSprite(scene: SceneLike, spec: PlayerSpriteSpec): PlayerSpriteHandle {
  const initFrame = lpcIdleFrame(spec.direction);
  let stepCounter = 0;
  let body: ImageLike;
  try {
    body = scene.add
      .image(tileCenter(spec.tileX, spec.tileSize), tileCenter(spec.tileY, spec.tileSize), LPC_PLAYER_KEY, initFrame)
      .setScale(0.85)
      .setDepth(20);
    if (body.setFrame) {
      body.setFrame(initFrame);
    }
  } catch (e) {
    void e;
    body = scene.add
      .image(tileCenter(spec.tileX, spec.tileSize), tileCenter(spec.tileY, spec.tileSize), LPC_FALLBACK_KEY)
      .setScale(1.35)
      .setDepth(20);
  }
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
      moveImage(body, nextX, nextY);
      if (body.setFrame) {
        stepCounter += 1;
        body.setFrame(lpcStepFrame(direction, stepCounter));
      }
    },
  };
  return handle;
}

function tileCenter(tile: number, tileSize: number): number {
  return tile * tileSize + tileSize / 2;
}

function moveImage(image: ImageLike, x: number, y: number): void {
  image.x = x;
  image.y = y;
}
