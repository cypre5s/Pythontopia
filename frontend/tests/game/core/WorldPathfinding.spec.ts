import { describe, expect, it } from 'vitest';
import { findTilePath } from '@/game/core/WorldPathfinding';

describe('findTilePath', () => {
  it('finds a shortest cardinal route around blocked tiles', () => {
    const path = findTilePath({
      start: { x: 0, y: 1 }, target: { x: 3, y: 1 }, width: 4, height: 3,
      isBlocked: (x, y) => x === 1 && y === 1,
    });

    expect(path).toEqual([
      { x: 0, y: 0 }, { x: 1, y: 0 }, { x: 2, y: 0 }, { x: 3, y: 0 }, { x: 3, y: 1 },
    ]);
  });

  it('returns null for unreachable or blocked endpoints and an empty path at the destination', () => {
    const walls = (x: number) => x === 1;
    expect(findTilePath({ start: { x: 0, y: 0 }, target: { x: 2, y: 0 }, width: 3, height: 1, isBlocked: walls })).toBeNull();
    expect(findTilePath({ start: { x: 0, y: 0 }, target: { x: 0, y: 0 }, width: 3, height: 1, isBlocked: () => false })).toEqual([]);
    expect(findTilePath({ start: { x: 0, y: 0 }, target: { x: 1, y: 0 }, width: 3, height: 1, isBlocked: walls })).toBeNull();
    expect(findTilePath({ start: { x: 0, y: 0 }, target: { x: 3, y: 0 }, width: 3, height: 1, isBlocked: () => false })).toBeNull();
  });
});
