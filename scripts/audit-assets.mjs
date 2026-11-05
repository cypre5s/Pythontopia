import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const publicDir=path.join(root,'frontend/public');
const world=JSON.parse(fs.readFileSync(path.join(root,'data/world/world.json'),'utf8'));
// Existence only: attribution and optional click/glow contract references are included.
// The resulting count is not a count of actual runtime downloads or rendered files.
const paths=new Set(['assets/pixel/tilesets/super-retro-world/town_tiles.png','assets/pixel/LICENSE.md']);
const groups={barrel:'barrels',crate:'crates',lamp:'lamps',pot:'pots',potted_plant:'potted_plants',rock:'rocks',torch:'torches',tree:'trees'};
let mapCount=0;
for(const area of Object.values(world.maps)){
 if(!area.asset)continue;
 mapCount++;
 const relative=area.asset.replace(/^\//,'');
 paths.add(relative);
 const map=JSON.parse(fs.readFileSync(path.join(publicDir,relative),'utf8'));
 for(const tileset of map.tilesets??[]){
  if(tileset.image)paths.add(path.posix.normalize(path.posix.join(path.posix.dirname(relative),tileset.image)));
  if(tileset.source)paths.add(path.posix.normalize(path.posix.join(path.posix.dirname(relative),tileset.source)));
 }
 for(const layer of map.layers)for(const obj of layer.objects??[]){
 const texture=obj.properties?.find(p=>p.name==='texture')?.value;
 if(typeof texture==='string')paths.add(texture.replace(/^\//,''));
 const key=obj.properties?.find(p=>p.name==='sprite')?.value;
 if(typeof key!=='string')continue;
 const stem=key.replace(/^srw_/,'');
 if(stem.startsWith('house_'))paths.add(`assets/pixel/buildings/super-retro-world/${stem}.png`);
 else {const group=groups[stem.replace(/_\d+$/,'')];if(group)paths.add(`assets/pixel/props/super-retro-world/${group}/${stem}.png`);}
 }
}
if(world.portal_marker?.asset)paths.add(world.portal_marker.asset.replace(/^\//,''));
for(const i of [0,2,4,6,10])paths.add(`assets/pixel/sprites/super-retro-world/characters/chara_${i}.png`);
for(const npc of ['mayor_py','professor','arena_guard','librarian_doc'])paths.add(`assets/pixel/portraits/galgame/${npc}.png`);
for(const rel of ['assets/pixel/bgm/track_01.mp3','assets/pixel/sfx/click/click.ogg','assets/pixel/effects/light/glow_soft.png'])paths.add(rel);
const missing=[...paths].filter(p=>!fs.existsSync(path.join(publicDir,p)));
if(missing.length){console.error(missing.join('\n'));process.exitCode=1;}
else console.log(`Asset existence audit OK: ${paths.size} unique required files across ${mapCount} configured maps and retained contract references (not a runtime usage count).`);
