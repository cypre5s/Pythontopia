// 玩家：按格移动（逻辑坐标始终对齐网格），格间用缓动插值出平滑视觉位置。
import type { Direction } from './layout';
import type { TileMap } from './TileMap';

const STEP_MS = 140;

const DELTA: Readonly<Record<Direction, { dx: number; dy: number }>> = {
  up: { dx: 0, dy: -1 },
  down: { dx: 0, dy: 1 },
  left: { dx: -1, dy: 0 },
  right: { dx: 1, dy: 0 },
};

export class Player {
  tileX: number;
  tileY: number;
  facing: Direction = 'down';

  private originX: number;
  private originY: number;
  private progress = 1;
  private animTimeMs = 0;

  constructor(tileX: number, tileY: number) {
    this.tileX = tileX;
    this.tileY = tileY;
    this.originX = tileX;
    this.originY = tileY;
  }

  get moving(): boolean {
    return this.progress < 1;
  }

  /** 重新放置（如后端 bootstrap 回包），仅在站定时生效，避免打断当前位移。 */
  placeAt(tileX: number, tileY: number): void {
    if (this.moving) {
      return;
    }
    this.tileX = tileX;
    this.tileY = tileY;
    this.originX = tileX;
    this.originY = tileY;
    this.progress = 1;
  }

  update(deltaMs: number, requested: Direction | null, map: TileMap): void {
    if (this.moving) {
      this.progress = Math.min(1, this.progress + deltaMs / STEP_MS);
      this.animTimeMs += deltaMs;
      if (this.progress >= 1) {
        this.originX = this.tileX;
        this.originY = this.tileY;
      }
      return;
    }

    if (!requested) {
      this.animTimeMs = 0;
      return;
    }

    this.facing = requested;
    const step = DELTA[requested];
    const targetX = this.tileX + step.dx;
    const targetY = this.tileY + step.dy;
    if (map.isBlocked(targetX, targetY)) {
      this.animTimeMs = 0;
      return;
    }
    this.originX = this.tileX;
    this.originY = this.tileY;
    this.tileX = targetX;
    this.tileY = targetY;
    this.progress = 0;
    this.animTimeMs += deltaMs;
  }

  /** 当前小数格坐标（用于渲染插值）。 */
  fractionalTile(): { fx: number; fy: number } {
    const eased = easeInOut(this.progress);
    return {
      fx: this.originX + (this.tileX - this.originX) * eased,
      fy: this.originY + (this.tileY - this.originY) * eased,
    };
  }

  /** 走路上下小幅 bob（像素抖动），站定为 0。 */
  bobOffset(): number {
    if (!this.moving) {
      return 0;
    }
    return Math.sin(this.animTimeMs / 60) * 1.2;
  }
}

function easeInOut(t: number): number {
  if (t <= 0) {
    return 0;
  }
  if (t >= 1) {
    return 1;
  }
  return t * t * (3 - 2 * t);
}
