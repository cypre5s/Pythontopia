import type { CourseInteraction, Player } from "../shared/types";

/** Entry triggers use the saved tile; remaining inside never repeats a flow. */
export function enteredInteractions(player: Player, interactions: readonly CourseInteraction[], previous: ReadonlySet<string>) {
  const inside = new Set(interactions.filter(item => {
    if (item.trigger !== "enter" || item.map_key !== player.map_key) return false;
    if ("region" in item.target) {
      const region = item.target.region;
      return player.tile_x >= region.x && player.tile_y >= region.y
        && player.tile_x < region.x + region.width && player.tile_y < region.y + region.height;
    }
    return item.points.some(point => point.map_key === player.map_key
      && Math.abs(point.tile_x - player.tile_x) + Math.abs(point.tile_y - player.tile_y) <= item.radius);
  }).map(item => item.id));
  return { inside, entered: [...inside].filter(id => !previous.has(id)) };
}
