// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
// 重构原因：原实现瞬移无 tween，新版用 Phaser tween + Sprite Animations 做 4 向行走 + 阴影。
import Phaser from 'phaser';
import type { CardinalDirection, Direction } from '@api/dto/Direction';
import type { PlayerSpriteHandle, RectangleLike, SceneLike } from './sceneTypes';

// LPC SaraFullSheet 832×1344, 64×64 帧 / 13 列 × 21 行。
// row 10 = walk down, row 11 = walk right, row 8 = walk up, row 9 = walk left（标准 LPC 排布）。
// 每行第 0 帧是 idle，1~8 是 walk cycle。
// R-13：Direction 已扩到 8 方向；sprite 动画仍按 4 方向 cardinal 渲染，
// 对角线方向通过 toCardinalFrame 收敛为最近的 4 向。
const LPC_SHEET_KEY = 'player_sara';
const LPC_FRAME_HEIGHT = 64;
const LPC_COLS = 13;
const LPC_WALK_ROW: Record<CardinalDirection, number> = {
  UP: 8,
  LEFT: 9,
  DOWN: 10,
  RIGHT: 11,
};

function toCardinalFrame(dir: Direction): CardinalDirection {
  if (dir === 'UP' || dir === 'DOWN' || dir === 'LEFT' || dir === 'RIGHT') return dir;
  // 对角线：横向优先（与 WorldScene.toCardinal 保持一致）
  if (dir === 'UP_LEFT' || dir === 'DOWN_LEFT') return 'LEFT';
  if (dir === 'UP_RIGHT' || dir === 'DOWN_RIGHT') return 'RIGHT';
  return 'DOWN';
}

function idleFrame(dir: Direction): number {
  return LPC_WALK_ROW[toCardinalFrame(dir)] * LPC_COLS;
}

function walkFrames(dir: Direction): number[] {
  const base = LPC_WALK_ROW[toCardinalFrame(dir)] * LPC_COLS;
  return [1, 2, 3, 4, 5, 6, 7, 8].map((offset) => base + offset);
}

function walkAnimKey(dir: Direction): string {
  return `sara_walk_${toCardinalFrame(dir).toLowerCase()}`;
}

export interface CreatePlayerOptions {
  tileX: number;
  tileY: number;
  direction: Direction;
  tileSize: number;
}

const TWEEN_DURATION_MS = 140;

let animsRegistered = false;

function ensureAnims(phaserScene: Phaser.Scene): void {
  if (animsRegistered) {
    return;
  }
  const directions: Direction[] = ['UP', 'LEFT', 'DOWN', 'RIGHT'];
  directions.forEach((dir) => {
    const key = walkAnimKey(dir);
    if (!phaserScene.anims.exists(key)) {
      phaserScene.anims.create({
        key,
        frames: walkFrames(dir).map((frame) => ({ key: LPC_SHEET_KEY, frame })),
        frameRate: 10,
        repeat: -1,
      });
    }
  });
  animsRegistered = true;
}

export function createPlayerSprite(scene: SceneLike, opts: CreatePlayerOptions): PlayerSpriteHandle {
  const phaserScene = scene as unknown as Phaser.Scene;
  ensureAnims(phaserScene);

  const startX = opts.tileX * opts.tileSize + opts.tileSize / 2;
  const startY = opts.tileY * opts.tileSize + opts.tileSize / 2;

  const shadow = phaserScene.add
    .ellipse(startX, startY + opts.tileSize / 2 - 4, opts.tileSize * 0.7, opts.tileSize * 0.3, 0x000000, 0.35)
    .setDepth(14);
  const sprite = phaserScene.add
    .sprite(startX, startY, LPC_SHEET_KEY, idleFrame(opts.direction))
    .setOrigin(0.5, 0.75)
    .setDepth(15);
  sprite.setScale((opts.tileSize / LPC_FRAME_HEIGHT) * 1.4);

  const handle: PlayerSpriteHandle = {
    x: startX,
    y: startY,
    tileX: opts.tileX,
    tileY: opts.tileY,
    direction: opts.direction,
    moveToTile(tileX: number, tileY: number, direction: Direction): void {
      this.tileX = tileX;
      this.tileY = tileY;
      this.direction = direction;
      const targetX = tileX * opts.tileSize + opts.tileSize / 2;
      const targetY = tileY * opts.tileSize + opts.tileSize / 2;
      sprite.play(walkAnimKey(direction), true);
      phaserScene.tweens.killTweensOf(sprite);
      phaserScene.tweens.killTweensOf(shadow);
      phaserScene.tweens.add({
        targets: sprite,
        x: targetX,
        y: targetY,
        duration: TWEEN_DURATION_MS,
        ease: 'Linear',
        onUpdate: () => {
          handle.x = sprite.x;
          handle.y = sprite.y;
        },
        onComplete: () => {
          sprite.stop();
          sprite.setFrame(idleFrame(direction));
          handle.x = sprite.x;
          handle.y = sprite.y;
        },
      });
      phaserScene.tweens.add({
        targets: shadow,
        x: targetX,
        y: targetY + opts.tileSize / 2 - 4,
        duration: TWEEN_DURATION_MS,
        ease: 'Linear',
      });
    },
  };
  return handle;
}

export function setupClickable(target: RectangleLike, callback: () => void): void {
  target.setInteractive().on('pointerdown', callback);
}
