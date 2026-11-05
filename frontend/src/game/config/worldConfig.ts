import definition from "../../../../data/world/world.json";
import type { Npc, Player } from "../../shared/types";
import { findPath, type Tile } from "../pathfinding";

export interface WorldBuilding {
  id: string;
  object_name: string;
  label: string;
  purpose: string;
  status: "open" | "landmark" | "closed";
  anchor: Tile;
  portal_id?: string;
}
export interface WorldMapConfig {
  label: string;
  width: number;
  height: number;
  renderer: "tiled";
  asset?: string;
  buildings: WorldBuilding[];
  walk_bounds?: WorldBounds;
  render_bounds?: WorldBounds;
  camera_mode?: "centered" | "bounded";
}
export interface WorldBounds { x: number; y: number; width: number; height: number }
export interface WorldPortal {
  id: string;
  label: string;
  action_label: string;
  from: { map_key: string; tile_x: number; tile_y: number; radius: number };
  to: Player;
  required_flags: string[];
  blocked_message: string;
  building_id?: string;
}
export interface WorldDefinition {
  schema_version: number;
  default_spawn: Player;
  maps: Record<string, WorldMapConfig>;
  interactions: Record<string, WorldInteraction>;
  portals: WorldPortal[];
  portal_marker?: { asset: string; width: number; height: number };
}
export interface InteractionPoint { map_key: string; tile_x: number; tile_y: number }
export interface WorldInteraction {
  label: string; kind: "object" | "building"; building_id?: string;
  map_key?: string; tile_x?: number; tile_y?: number; points?: InteractionPoint[];
}
export const worldConfig = definition as WorldDefinition;
export interface WorldTarget extends Tile {
  id: string;
  map_key: string;
  label: string;
  kind: "npc" | "object" | "building" | "portal";
  radius: number;
  portal?: WorldPortal;
  building?: WorldBuilding;
  points?: Tile[];
}
export function getWorldMap(key: string): WorldMapConfig {
  const map = worldConfig.maps[key];
  if (!map) throw new Error("地图配置不存在，请重新进入小镇。");
  return map;
}
export function mapWalkBounds(map: WorldMapConfig): WorldBounds {
  return map.walk_bounds ?? { x: 0, y: 0, width: map.width, height: map.height };
}
export function mapRenderBounds(map: WorldMapConfig): WorldBounds {
  return map.render_bounds ?? { x: 0, y: 0, width: map.width, height: map.height };
}
export function withinWalkBounds(map: WorldMapConfig, x: number, y: number): boolean {
  const bounds = mapWalkBounds(map);
  return x >= bounds.x && y >= bounds.y && x < bounds.x + bounds.width && y < bounds.y + bounds.height;
}
export function mapCameraBounds(map: WorldMapConfig): WorldBounds | null {
  return map.camera_mode === "centered" ? null : mapRenderBounds(map);
}
export function portalAvailable(portal: WorldPortal, flags: readonly string[]) {
  return portal.required_flags.every(flag => flags.includes(flag));
}
export function interactionPoints(interaction: WorldInteraction): InteractionPoint[] {
  return interaction.points ?? [{ map_key: interaction.map_key!, tile_x: interaction.tile_x!, tile_y: interaction.tile_y! }];
}
export function mapTargets(mapKey: string): WorldTarget[] {
  const targets = new Map<string, WorldTarget>();
  for (const portal of worldConfig.portals.filter(item => item.from.map_key === mapKey)) {
    targets.set(portal.id, { id: portal.id, map_key: mapKey, x: portal.from.tile_x, y: portal.from.tile_y, label: portal.label, kind: "portal", radius: portal.from.radius, portal });
  }
  const buildings = getWorldMap(mapKey).buildings;
  for (const [id, item] of Object.entries(worldConfig.interactions)) {
    const points = interactionPoints(item).filter(point => point.map_key === mapKey);
    if (!points.length || targets.has(id)) continue;
    const building = buildings.find(candidate => candidate.id === (item.building_id ?? id));
    if (building?.portal_id && targets.has(building.portal_id)) continue;
    targets.set(id, { id, map_key: mapKey, x: points[0]!.tile_x, y: points[0]!.tile_y, label: item.label, kind: item.kind, radius: 2, building,
      points: points.length > 1 ? points.map(point => ({ x: point.tile_x, y: point.tile_y })) : undefined });
  }
  for (const building of buildings) {
    if (building.portal_id || targets.has(building.id)) continue;
    targets.set(building.id, { id: building.id, map_key: mapKey, ...building.anchor, label: building.label, kind: "building", radius: 2, building });
  }
  return [...targets.values()];
}
export function resolveWorldTargets(id: string, npcs: readonly Npc[]): WorldTarget[] {
  const npc = npcs.find(item => item.key === id);
  if (npc) return [{ id, map_key: npc.map_key, x: npc.tile_x, y: npc.tile_y, label: npc.name, kind: "npc", radius: 2 }];
  const targets: WorldTarget[] = [];
  for (const [mapKey, map] of Object.entries(worldConfig.maps)) {
    const building = map.buildings.find(item => item.id === id || item.object_name === id);
    const target = mapTargets(mapKey).find(item => item.id === (building?.portal_id ?? building?.id ?? id));
    if (target) targets.push(...(target.points?.map(point => ({ ...target, ...point })) ?? [target]));
  }
  return targets;
}
export function resolveWorldTarget(id: string, npcs: readonly Npc[]): WorldTarget | undefined {
  return resolveWorldTargets(id, npcs)[0];
}
export function portalRoute(from: string, to: string, flags: readonly string[], config = worldConfig): WorldPortal[] | null {
  if (from === to) return [];
  const queue: { map: string; route: WorldPortal[] }[] = [{ map: from, route: [] }];
  const visited = new Set([from]);
  for (const current of queue) {
    for (const portal of config.portals) {
      if (portal.from.map_key !== current.map || !portalAvailable(portal, flags) || visited.has(portal.to.map_key)) continue;
      const route = [...current.route, portal];
      if (portal.to.map_key === to) return route;
      visited.add(portal.to.map_key);
      queue.push({ map: portal.to.map_key, route });
    }
  }
  return null;
}
export function buildingMessage(building: WorldBuilding) {
  const purpose = building.purpose.replace(/(?:室内)?暂未开放[。；，]?/g, "").trim().replace(/[，；、]+$/, "");
  return purpose ? `${building.label}：${purpose}` : building.label;
}
export function worldTargetHint(target: WorldTarget, flags: readonly string[]) {
  if (target.kind === "npc") return "交谈";
  if (target.portal) return portalAvailable(target.portal, flags) ? (/返回|离开/.test(target.portal.action_label) ? "返回" : "进入") : "";
  if (target.building) return "";
  return target.id === "library_shelf" ? "阅读" : "查看";
}
export function nearbyWorldTarget(position: Tile & { map_key: string }, targets: readonly WorldTarget[], flags: readonly string[], preferredId?: string): WorldTarget | undefined {
  return targets.filter(target => target.map_key === position.map_key && (!preferredId || target.id === preferredId)
    && worldTargetHint(target, flags) && Math.abs(target.x - position.x) + Math.abs(target.y - position.y) <= target.radius)
    .sort((a, b) => (Math.abs(a.x - position.x) + Math.abs(a.y - position.y)) - (Math.abs(b.x - position.x) + Math.abs(b.y - position.y)))[0];
}
export function displayWorldText(text: string): string {
  return text.replaceAll("西侧书架", worldConfig.interactions.library_shelf?.label ?? "图书馆书架");
}
export function displayQuestTitle(node: { target?: string; title: string }): string {
  return node.target === "library_shelf" ? displayWorldText(node.title) : node.title;
}
export function reachableTargetApproach(start: Tile, targets: readonly WorldTarget[], width: number, height: number,
  blocked: (x: number, y: number) => boolean, preferredPoint?: Tile) {
  let best: { target: WorldTarget; point: Tile; path: Tile[]; score: number } | null = null;
  for (const target of targets) {
    const approach = reachableApproach(start, target, target.radius, width, height, blocked);
    if (!approach) continue;
    const score = (preferredPoint ? Math.abs(target.x - preferredPoint.x) + Math.abs(target.y - preferredPoint.y) : 0) * width * height + approach.path.length;
    if (!best || score < best.score) best = { target, ...approach, score };
  }
  return best;
}
export function reachableApproach(start: Tile, target: Tile, radius: number, width: number, height: number, blocked: (x: number, y: number) => boolean) {
  let best: { point: Tile; path: Tile[] } | null = null;
  for (let dy = -radius; dy <= radius; dy++) {
    for (let dx = -radius; dx <= radius; dx++) {
      if (Math.abs(dx) + Math.abs(dy) > radius) continue;
      const point = { x: target.x + dx, y: target.y + dy };
      if (point.x < 0 || point.y < 0 || point.x >= width || point.y >= height || blocked(point.x, point.y)) continue;
      const path = findPath(start, point, width, height, blocked);
      if (!path.length && (point.x !== start.x || point.y !== start.y)) continue;
      if (!best || path.length < best.path.length) best = { point, path };
    }
  }
  return best;
}
