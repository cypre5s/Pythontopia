import { describe, expect, it } from 'vitest';
import { resolveLowObstacleJump } from '@/game/core/JumpSystem';

function grid(width: number, height: number, cells: ReadonlyArray<readonly [number, number]>): boolean[][] {
  const result = Array.from({ length: height }, () => Array.from({ length: width }, () => false));
  cells.forEach(([x, y]) => {
    const row = result[y];
    if (row) row[x] = true;
  });
  return result;
}

describe('resolveLowObstacleJump', () => {
  it('jumps over a one-tile low obstacle when the landing tile is walkable', () => {
    const result = resolveLowObstacleJump({
      tileX: 5,
      tileY: 5,
      direction: 'RIGHT',
      width: 10,
      height: 10,
      collisions: grid(10, 10, [[6, 5]]),
      lowObstacles: grid(10, 10, [[6, 5]]),
    });

    expect(result).toEqual({
      kind: 'jump',
      from: { tileX: 5, tileY: 5 },
      obstacle: { tileX: 6, tileY: 5 },
      landing: { tileX: 7, tileY: 5 },
      direction: 'RIGHT',
    });
  });

  it('blocks the jump when the landing tile is not walkable', () => {
    const result = resolveLowObstacleJump({
      tileX: 5,
      tileY: 5,
      direction: 'RIGHT',
      width: 10,
      height: 10,
      collisions: grid(10, 10, [[6, 5], [7, 5]]),
      lowObstacles: grid(10, 10, [[6, 5]]),
    });

    expect(result.kind).toBe('blocked');
  });

  it('does not jump over ordinary walls', () => {
    const result = resolveLowObstacleJump({
      tileX: 5,
      tileY: 5,
      direction: 'UP',
      width: 10,
      height: 10,
      collisions: grid(10, 10, [[5, 4]]),
      lowObstacles: grid(10, 10, []),
    });

    expect(result.kind).toBe('blocked');
  });
});
