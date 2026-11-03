export interface Tile {
  x: number;
  y: number;
}
export function findPath(
  start: Tile,
  end: Tile,
  width: number,
  height: number,
  blocked: (x: number, y: number) => boolean,
): Tile[] {
  const key = (x: number, y: number) => `${x},${y}`,
    queue = [start],
    parents = new Map<string, Tile | null>([[key(start.x, start.y), null]]);
  let found: Tile | undefined;
  for (let i = 0; i < queue.length; i++) {
    const item = queue[i]!;
    if (item.x === end.x && item.y === end.y) {
      found = item;
      break;
    }
    for (const [dx, dy] of [
      [0, 1],
      [1, 0],
      [-1, 0],
      [0, -1],
    ]) {
      const next = { x: item.x + dx!, y: item.y + dy! },
        k = key(next.x, next.y);
      if (
        next.x < 0 ||
        next.y < 0 ||
        next.x >= width ||
        next.y >= height ||
        blocked(next.x, next.y) ||
        parents.has(k)
      )
        continue;
      parents.set(k, item);
      queue.push(next);
    }
  }
  if (!found) return [];
  const result: Tile[] = [];
  let cursor: Tile | null = found;
  while (cursor) {
    result.unshift(cursor);
    cursor = parents.get(key(cursor.x, cursor.y)) ?? null;
  }
  return result.slice(1);
}
export function isTextFocus(element: Element | null) {
  return Boolean(
    element?.closest(
      'input,textarea,select,[contenteditable="true"],.cm-editor',
    ),
  );
}
