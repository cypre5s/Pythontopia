import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const publicDir=path.join(root,'frontend/public');
const map=JSON.parse(fs.readFileSync(path.join(publicDir,'assets/maps/town_hub.json'),'utf8'));
const paths=new Set(['assets/pixel/tilesets/super-retro-world/town_tiles.png','assets/pixel/LICENSE.md']);
const groups={barrel:'barrels',crate:'crates',lamp:'lamps',pot:'pots',potted_plant:'potted_plants',rock:'rocks',torch:'torches',tree:'trees'};
for(const layer of map.layers)for(const obj of layer.objects??[]){
 const key=obj.properties?.find(p=>p.name==='sprite')?.value;
 if(typeof key!=='string')continue;
 const stem=key.replace(/^srw_/,'');
 if(stem.startsWith('house_'))paths.add(`assets/pixel/buildings/super-retro-world/${stem}.png`);
 else {const group=groups[stem.replace(/_\d+$/,'')];if(group)paths.add(`assets/pixel/props/super-retro-world/${group}/${stem}.png`);}
}
for(const i of [0,2,4,6,10])paths.add(`assets/pixel/sprites/super-retro-world/characters/chara_${i}.png`);
for(const npc of ['mayor_py','professor','arena_guard','librarian_doc'])paths.add(`assets/pixel/portraits/galgame/${npc}.png`);
for(const rel of ['assets/pixel/bgm/track_01.mp3','assets/pixel/sfx/click/click.ogg','assets/pixel/effects/light/glow_soft.png'])paths.add(rel);
const missing=[...paths].filter(p=>!fs.existsSync(path.join(publicDir,p)));
if(missing.length){console.error(missing.join('\n'));process.exitCode=1;}
else console.log(`Asset references OK: ${paths.size} required files; map objects, dynamic sprites, portraits, audio and legal entry exist.`);
