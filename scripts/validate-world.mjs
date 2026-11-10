import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const config = JSON.parse(fs.readFileSync(path.join(root, 'shared/world/world.json'), 'utf8'));
const errors = [];
let totalReachable = 0, totalBuildings = 0, totalObjects = 0;
const reachable = new Map();
const semanticAnchors = new Set();
for (const [key, area] of Object.entries(config.maps)) {
  let collision = [], objects = [];
  const walk = area.walk_bounds ?? { x: 0, y: 0, width: area.width, height: area.height };
  const render = area.render_bounds ?? { x: 0, y: 0, width: area.width, height: area.height };
  if (walk.x < 0 || walk.y < 0 || walk.x + walk.width > area.width || walk.y + walk.height > area.height) errors.push(`Walk bounds exceed logical map: ${key}`);
  if (render.x > 0 || render.y > 0 || render.x + render.width < area.width || render.y + render.height < area.height) errors.push(`Render bounds clip logical map: ${key}`);
  if (area.renderer === 'tiled') {
    const file = path.resolve(root, 'frontend/public', area.asset.replace(/^\//, ''));
    const map = JSON.parse(fs.readFileSync(file, 'utf8'));
    if (map.width !== area.width || map.height !== area.height) errors.push(`Map dimensions disagree: ${key}`);
    if (map.tilewidth !== 16 || map.tileheight !== 16) errors.push(`Only 16-pixel maps are supported: ${key}`);
    collision = map.layers.find(layer => layer.name === 'collision')?.data ?? [];
    if (collision.length !== area.width * area.height) errors.push(`Collision dimensions disagree: ${key}`);
    for (const layer of map.layers.filter(layer => layer.type === 'tilelayer')) {
      if (layer.data?.length !== (layer.width ?? map.width) * (layer.height ?? map.height)) errors.push(`Tile layer dimensions disagree: ${key}/${layer.name}`);
    }
    objects = map.layers.flatMap(layer => layer.objects ?? []);
    totalObjects += objects.length;
    for (const tileset of map.tilesets) {
      if (tileset.image && !fs.existsSync(path.resolve(path.dirname(file), tileset.image))) errors.push(`Missing tileset ${tileset.image}`);
    }
    const configured = new Set(area.buildings.map(item => item.object_name));
    for (const object of objects) {
      if (object.properties?.some(p => p.name === 'route')) errors.push(`Retired route on ${object.name}`);
      const sprite = object.properties?.find(p => p.name === 'sprite')?.value ?? '';
      if (String(sprite).startsWith('srw_house_') && !configured.has(object.name)) errors.push(`Unnamed building: ${object.name}`);
      const props = Object.fromEntries((object.properties ?? []).map(item => [item.name, item.value]));
      if (props.interaction_id) {
        if (!config.interactions[props.interaction_id]) errors.push(`Unknown prop interaction: ${props.interaction_id}`);
        semanticAnchors.add(`${props.interaction_id}:${key}:${Math.floor((object.x + object.width / 2) / 16)},${Math.floor((object.y + object.height) / 16)}`);
      }
      if (props.texture) {
        const texture = path.resolve(root, 'frontend/public', String(props.texture).replace(/^\//, ''));
        if (!fs.existsSync(texture)) errors.push(`Missing prop texture: ${props.texture}`);
        else if (['frame_x', 'frame_y', 'frame_width', 'frame_height'].every(field => Number.isInteger(props[field]))) {
          const png = fs.readFileSync(texture);
          const width = png.readUInt32BE(16), height = png.readUInt32BE(20);
          if (props.frame_x < 0 || props.frame_y < 0 || props.frame_width <= 0 || props.frame_height <= 0
            || props.frame_x + props.frame_width > width || props.frame_y + props.frame_height > height) errors.push(`Prop frame exceeds source texture: ${object.name}`);
        }
      }
    }
    for (const building of area.buildings) {
      if (!objects.some(object => object.name === building.object_name)) errors.push(`Missing building object: ${building.object_name}`);
    }
  } else errors.push(`Map requires an authored Tiled asset: ${key}`);
  totalBuildings += area.buildings.length;
  const open = (x, y) => x >= walk.x && y >= walk.y && x < walk.x + walk.width && y < walk.y + walk.height && !collision[y * area.width + x];
  const spawn = config.default_spawn.map_key === key ? config.default_spawn : config.portals.find(item => item.to.map_key === key)?.to;
  if (!spawn) { errors.push(`No entry spawn: ${key}`); continue; }
  const visited = new Set(), queue = [[spawn.tile_x, spawn.tile_y]];
  for (let i = 0; i < queue.length; i++) {
    const [x, y] = queue[i];
    if (!open(x, y) || visited.has(`${x},${y}`)) continue;
    visited.add(`${x},${y}`);
    queue.push([x + 1, y], [x - 1, y], [x, y + 1], [x, y - 1]);
  }
  totalReachable += visited.size;
  reachable.set(key, visited);
  for (const building of area.buildings) {
    if (!building.label?.trim()) errors.push(`Missing building label: ${building.id}`);
    if (!visited.has(`${building.anchor.x},${building.anchor.y}`)) errors.push(`Unreachable building entrance: ${building.id}`);
  }
}
const checkPoint = (label, point) => {
  if (!reachable.get(point.map_key)?.has(`${point.tile_x},${point.tile_y}`)) errors.push(`Unreachable ${label}: ${JSON.stringify(point)}`);
};
checkPoint('spawn', config.default_spawn);
for (const [key, interaction] of Object.entries(config.interactions)) {
  for (const point of interaction.points ?? [interaction]) {
    checkPoint(key, point);
    if (interaction.points && !semanticAnchors.has(`${key}:${point.map_key}:${point.tile_x},${point.tile_y}`)) errors.push(`Multi-point interaction has no matching authored prop: ${key}/${point.tile_x},${point.tile_y}`);
  }
}
for (const portal of config.portals) {
  checkPoint(`portal ${portal.id} entrance`, portal.from);
  checkPoint(`portal ${portal.id} arrival`, portal.to);
  if (!config.portals.some(other => other.from.map_key === portal.to.map_key && other.to.map_key === portal.from.map_key)) {
    errors.push(`No return route for ${portal.id}`);
  }
}
if (config.portal_marker && !fs.existsSync(path.resolve(root, 'frontend/public', config.portal_marker.asset.replace(/^\//, '')))) errors.push('Missing portal marker artwork');
if (errors.length) { console.error(errors.join('\n')); process.exitCode = 1; }
else console.log(`World OK: ${Object.keys(config.maps).length} maps, ${totalBuildings} named buildings, ${config.portals.length} portals, ${totalReachable} reachable cells, ${totalObjects} art objects.`);
