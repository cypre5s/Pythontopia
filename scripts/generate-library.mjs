import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const W=18,H=14,T=16,COLUMNS=112;
const texture='/assets/pixel/tilesets/super-retro-world/farming_source.png';
const gid=(x,y)=>y*COLUMNS+x+1;
const props=(values)=>Object.entries(values).map(([name,value])=>({name,type:typeof value==='boolean'?'bool':typeof value==='number'?'int':'string',value}));
const floor=Array(W*H).fill(gid(1,43)),wall=Array(W*H).fill(0),rug=Array(W*H).fill(0),collision=Array(W*H).fill(0);
for(let y=0;y<H;y++)for(let x=0;x<W;x++){
  if(y<2)wall[y*W+x]=gid(x===0?65:x===W-1?67:66,y===0?25:27);
  if(y>=2&&(x===0||x===W-1))wall[y*W+x]=gid(x===0?65:67,26);
  if(y===H-1)wall[y*W+x]=gid(x===0?65:x===W-1?67:66,27);
  if(x===0||x===W-1||y===0||y===H-1)collision[y*W+x]=1;
  // One wall-foot row matches the two-row artwork; every historical y=1 point can exit to y=2.
  if(y===1)collision[y*W+x]=1;
}
const objects=[];
function frame(name,x,y,width,height,sx,sy,sw=width,sh=height,extra={}){
  objects.push({id:objects.length+1,name,type:'prop',x,y,width,height,rotation:0,visible:true,properties:props({texture,frame_x:sx,frame_y:sy,frame_width:sw,frame_height:sh,collidable:false,...extra})});
}
function block(x,y,width=1){for(let dx=0;dx<width;dx++)collision[y*W+x+dx]=1;}
// Six wall bookcases and two aligned pairs form quiet, readable book zones.
const shelfGroups=[
  ...[16,48,80,176,208,240].map((x,index)=>({name:index===1?'library_shelf':`wall_bookshelf_${index+1}`,x,y:32})),
  ...[32,64,192,224].map((x,index)=>({name:`book_zone_${index+1}`,x,y:96})),
];
const shelfPoints=[];
for(const shelf of shelfGroups){
  const {name,x,y}=shelf,booksDepth=(y+32)/T+2;
  frame(name,x,y,32,32,1696,576,32,32,{interaction_id:'library_shelf'});
  frame(`${name}_books_upper`,x,y,32,16,1696,560,32,16,{depth:booksDepth});
  frame(`${name}_books_lower`,x,y+16,32,16,1696,560,32,16,{depth:booksDepth});
  block(x/T,y/T+1,2);
  shelfPoints.push({map_key:'library',tile_x:x/T+1,tile_y:y/T+2});
}
// A centered service counter sits directly in front of the unchanged librarian at (9,5).
frame('librarian_desk',136,96,32,32,1600,416);
block(8,7,3);
// Full-width reading tables and separate short stools leave a visible seating gap.
// The source table starts above the atlas tile boundary: y=528 clips its top edge.
for(const [name,x]of [['west',32],['east',208]]){
  frame(`reading_table_${name}`,x,144,48,36,1616,520);
  block(x/T,9,3);block(x/T,10,3);
  for(const [seat,dx]of [['left',0],['right',32]]){
    frame(`reading_stool_${name}_${seat}`,x+dx,184,16,16,1552,512);
    block((x+dx)/T,12);
  }
}
frame('library_exit_door',144,208,16,16,0,0,16,16,{texture:'/assets/pixel/props/super-retro-world/library/door-source.png'});
const layer=(id,name,data,visible=true)=>({id,name,type:'tilelayer',x:0,y:0,width:W,height:H,opacity:1,visible,data});
const map={version:1.10,tiledversion:'1.10',type:'map',orientation:'orthogonal',renderorder:'right-down',width:W,height:H,tilewidth:T,tileheight:T,infinite:false,nextlayerid:6,nextobjectid:objects.length+1,
  properties:props({mapKey:'library',label:'小镇图书馆',source:'Super Retro World Farming / user-confirmed purchased assets'}),
  tilesets:[{firstgid:1,name:'srw_farming_original',tilewidth:T,tileheight:T,tilecount:8960,columns:COLUMNS,image:'../pixel/tilesets/super-retro-world/farming_source.png',imagewidth:1792,imageheight:1280,margin:0,spacing:0}],
  layers:[layer(1,'ground',floor),layer(2,'walls',wall),layer(3,'reading_rug',rug),layer(4,'collision',collision,false),{id:5,name:'furniture',type:'objectgroup',x:0,y:0,opacity:1,visible:true,draworder:'topdown',objects}]};

const required=[[9,11],[9,12],[9,5],[4,4],...shelfPoints.map(point=>[point.tile_x,point.tile_y])];
const visited=new Set(),queue=[required[0]];
for(const[x,y]of queue){const key=`${x},${y}`;if(visited.has(key)||x<0||y<0||x>=W||y>=H||collision[y*W+x])continue;visited.add(key);queue.push([x+1,y],[x-1,y],[x,y+1],[x,y-1]);}
for(const[x,y]of required)if(!visited.has(`${x},${y}`))throw new Error(`Required library point unreachable: ${x},${y}`);
for(let y=1;y<H-1;y++)for(let x=1;x<W-1;x++){
  if(!collision[y*W+x]&&!visited.has(`${x},${y}`))throw new Error(`Historical walkable position disconnected: ${x},${y}`);
  if(collision[y*W+x]&&![[x+1,y],[x-1,y],[x,y+1],[x,y-1]].some(([a,b])=>visited.has(`${a},${b}`)))throw new Error(`Historical position has no connected escape neighbor: ${x},${y}`);
}
const world=JSON.parse(await fs.readFile(path.join(root,'shared/world/world.json'),'utf8'));
if(JSON.stringify(world.interactions.library_shelf.points)!==JSON.stringify(shelfPoints))throw new Error('Canonical library shelf points do not match the ten real bookcases');
const out=path.join(root,'frontend/public/assets/maps/library.json');
const encoded=JSON.stringify(map,null,2)+'\n';
if(process.argv.includes('--check')){
  if(await fs.readFile(out,'utf8')!==encoded)throw new Error('library.json differs from its editable generator');
}else await fs.writeFile(out,encoded);
console.log(`library: ${W}x${H}, ${shelfGroups.length} full bookcases / ${objects.length} original-frame objects / ${visited.size} reachable cells; all real shelf anchors and legacy positions passed`);
