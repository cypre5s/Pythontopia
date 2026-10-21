export interface WorldTile {
  x: number;
  y: number;
}

/** Return the shortest four-way path excluding start, or null when the destination cannot be reached. */
export function findTilePath(input: {
  start: WorldTile;
  target: WorldTile;
  width: number;
  height: number;
  isBlocked: (x: number, y: number) => boolean;
}): WorldTile[] | null {
  const { start, target, width, height, isBlocked } = input;
  const inBounds = (tile: WorldTile) =>
    Number.isInteger(tile.x) && Number.isInteger(tile.y)
    && tile.x >= 0 && tile.y >= 0 && tile.x < width && tile.y < height;
  if (!Number.isInteger(width) || !Number.isInteger(height) || width <= 0 || height <= 0
    || !inBounds(start) || !inBounds(target) || isBlocked(start.x, start.y) || isBlocked(target.x, target.y)) {
    return null;
  }
  if (start.x === target.x && start.y === target.y) return [];

  const key = (tile: WorldTile) => `${tile.x},${tile.y}`;
  const startKey = key(start);
  const targetKey = key(target);
  const queue: WorldTile[] = [start];
  const previous = new Map<string, string | null>([[startKey, null]]);
  const tiles = new Map<string, WorldTile>([[startKey, start]]);
  const steps = [{ x: 0, y: -1 }, { x: 1, y: 0 }, { x: 0, y: 1 }, { x: -1, y: 0 }];

  for (let cursor = 0; cursor < queue.length && !previous.has(targetKey); cursor += 1) {
    const current = queue[cursor];
    if (!current) continue;
    for (const step of steps) {
      const next = { x: current.x + step.x, y: current.y + step.y };
      const nextKey = key(next);
      if (!inBounds(next) || isBlocked(next.x, next.y) || previous.has(nextKey)) continue;
      previous.set(nextKey, key(current));
      tiles.set(nextKey, next);
      queue.push(next);
    }
  }

  if (!previous.has(targetKey)) return null;
  const path: WorldTile[] = [];
  let cursor: string | null = targetKey;
  while (cursor && cursor !== startKey) {
    const tile = tiles.get(cursor);
    if (!tile) return null;
    path.push(tile);
    cursor = previous.get(cursor) ?? null;
  }
  return path.reverse();
}
