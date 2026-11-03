import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const file = path.join(root, 'frontend/public/assets/maps/town_hub.json');
const map = JSON.parse(fs.readFileSync(file, 'utf8'));
const collision = map.layers.find(layer => layer.name === 'collision').data;
const points = { spawn: [22, 36], mayor: [22, 34], professor: [25, 14], library_door: [8, 22], practice: [38, 24] };
const visited = new Set();
const queue = [points.spawn];
const open = (x, y) => x >= 0 && y >= 0 && x < map.width && y < map.height && !collision[y * map.width + x];
for (let i = 0; i < queue.length; i++) {
  const [x, y] = queue[i];
  if (!open(x, y) || visited.has(`${x},${y}`)) continue;
  visited.add(`${x},${y}`);
  queue.push([x + 1, y], [x - 1, y], [x, y + 1], [x, y - 1]);
}
const errors = [];
for (const [name, [x, y]] of Object.entries(points)) {
  if (!visited.has(`${x},${y}`)) errors.push(`Unreachable ${name}: ${x},${y}`);
}
for (const tileset of map.tilesets) {
  if (tileset.image && !fs.existsSync(path.resolve(path.dirname(file), tileset.image))) errors.push(`Missing tileset ${tileset.image}`);
}
const objects = map.layers.flatMap(layer => layer.objects ?? []);
for (const object of objects) {
  if (object.properties?.some(p => p.name === 'route')) errors.push(`Retired route on ${object.name}`);
}
if (map.width > 50 || map.height > 40) errors.push('Town exceeds intended dimensions');
if (errors.length) { console.error(errors.join('\n')); process.exitCode = 1; }
else console.log(`World OK: ${visited.size} reachable cells, ${Object.keys(points).length} required locations, ${objects.length} objects.`);
