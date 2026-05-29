import type { Direction } from '@api/dto/Direction';

export interface TilePoint {
  tileX: number;
  tileY: number;
}

export interface LowObstacleJumpInput {
  tileX: number;
  tileY: number;
  direction: Direction;
  width: number;
  height: number;
  collisions: boolean[][];
  lowObstacles: boolean[][];
}

export interface LowObstacleJumpSuccess {
  kind: 'jump';
  from: TilePoint;
  obstacle: TilePoint;
  landing: TilePoint;
  direction: Direction;
}

export interface LowObstacleJumpBlocked {
  kind: 'blocked';
  reason: 'no_low_obstacle' | 'landing_blocked' | 'out_of_bounds' | 'non_cardinal_direction';
}

export type LowObstacleJumpResult = LowObstacleJumpSuccess | LowObstacleJumpBlocked;

export function resolveLowObstacleJump(input: LowObstacleJumpInput): LowObstacleJumpResult {
  const delta = directionDelta(input.direction);
  if (delta.dx === 0 && delta.dy === 0) {
    return { kind: 'blocked', reason: 'non_cardinal_direction' };
  }

  const obstacle = {
    tileX: input.tileX + delta.dx,
    tileY: input.tileY + delta.dy,
  };
  const landing = {
    tileX: input.tileX + delta.dx * 2,
    tileY: input.tileY + delta.dy * 2,
  };

  if (!inBounds(obstacle, input.width, input.height) || !inBounds(landing, input.width, input.height)) {
    return { kind: 'blocked', reason: 'out_of_bounds' };
  }
  if (!cellAt(input.lowObstacles, obstacle.tileX, obstacle.tileY)) {
    return { kind: 'blocked', reason: 'no_low_obstacle' };
  }
  if (
    cellAt(input.collisions, landing.tileX, landing.tileY) ||
    cellAt(input.lowObstacles, landing.tileX, landing.tileY)
  ) {
    return { kind: 'blocked', reason: 'landing_blocked' };
  }

  return {
    kind: 'jump',
    from: { tileX: input.tileX, tileY: input.tileY },
    obstacle,
    landing,
    direction: input.direction,
  };
}

function directionDelta(direction: Direction): { dx: number; dy: number } {
  switch (direction) {
    case 'UP':
      return { dx: 0, dy: -1 };
    case 'DOWN':
      return { dx: 0, dy: 1 };
    case 'LEFT':
      return { dx: -1, dy: 0 };
    case 'RIGHT':
      return { dx: 1, dy: 0 };
    default:
      return { dx: 0, dy: 0 };
  }
}

function inBounds(point: TilePoint, width: number, height: number): boolean {
  return point.tileX >= 0 && point.tileY >= 0 && point.tileX < width && point.tileY < height;
}

function cellAt(grid: boolean[][], tileX: number, tileY: number): boolean {
  return grid[tileY]?.[tileX] === true;
}
