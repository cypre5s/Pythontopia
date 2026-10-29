#!/usr/bin/env node
/**
 * generate-maps.mjs — Pythontopia R-14 地图生成脚本
 *
 * @file 程序化生成 Phaser/Tiled 兼容的 JSON tilemap，覆盖：
 *   - town_hub   (50×40) 中央小镇
 *
 * @summary
 *   - 生成 1 个 .json + index.json + README.md（外围图与 Bug 矿洞风景图均已移除）。
 *   - 全部基于 16×16 源 tile；Phaser 渲染时统一 2× 放大（与 M1-B tilemap.ts 一致）。
 *   - tilemap_packed.png 132 frame 中我们仅使用确认存在的视觉安全帧；frame → gid = frame + 1。
 *   - 决策性数据全部在 MAPS 数组里维护；要新增/调整地图，编辑 MAPS 即可。
 *
 * @runtime  Node 20+（仅 node:fs / node:path / node:url）
 * @usage    node scripts/generate-maps.mjs [--dry-run]
 *
 * @author   W2-MAPS（M2 阶段地图子代理）
 * @license  MIT（脚本本体），地图引用的 Kenney tilemap 走 CC0-1.0。
 */

import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// ---------- 路径锚点 ----------
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const REPO_ROOT = path.resolve(__dirname, '..');
const MAPS_DIR = path.join(REPO_ROOT, 'frontend', 'public', 'assets', 'maps');

// ---------- 像素 / Tile 常量 ----------
const TILE = 16;
const TILED_VERSION = '1.10';

// ---------- 帧号清单（gid = frame + 1，因为 firstgid=1） ----------
// ⚠️ 2026-10-26 修复：旧表的帧号是凭印象猜的，建筑用的 14..25 实际是树丛/土丘/叶子，
// 导致整座小镇一栋房子都没有（铺的全是“散落门板 + 草丛”），水井是蘑菇、路灯是墙砖。
// 现在每个数字都对照 tilemap_packed.png（12×11 = 132 帧，无 margin/spacing）逐格核对：
//   帧 0/1/2      纯草 / 杂点草 / 带花草地
//   帧 12..14     沙地上边沿（左/中/右）  24..26 中段  36..38 下边沿  39..42 实心沙地
//   帧 4          松树   16 大树冠   5 圆灌木   28 大灌木   15 小树   19 果树
//   帧 44..47     木栅栏横段+立柱        56..59 竖段+立柱
//   帧 48..51     灰石屋顶              52..55 红瓦屋顶
//   帧 60..63     灰屋檐 / 山墙（63）     64..67 红屋檐 / 山墙（67）
//   帧 72..75     木墙 + 门（74）        76..79 灰石墙 + 门（78）
//   帧 83/95      路牌                  92 水井   93/94 金币/花环   104..107 木桶/木箱/壶
const TOWN_GIDS = {
  // 草地
  grass: 1,
  grassSpeckle: 2,
  grassFlower: 3,
  grassAlt: 2,
  grassTall: 3,
  // 沙地
  sandPath: 40,
  sandPlain: 40,
  // 植被
  treePine: 5,
  treeRound: 17,
  treeFruit: 23,
  bushSmall: 6,
  bushBig: 31,
  // 小物件
  mushroom: 30,
  well: 93,
  signPost: 84,
  signRed: 96,
  coin: 94,
  barrel: 105,
  crate: 106,
  rockSmall: 10,
  rockMedium: 11,
  logPile: 106,
  treeStump: 48,
  lamp: 48,
  flowerPink: 3,
  flowerYellow: 3,
  flowerBlue: 3,
  // 栅栏（横 / 竖 / 立柱）
  fenceWoodH: 46,
  fenceWoodV: 58,
  fencePost: 48,
  // 建筑风格：屋顶 → 屋檐/山墙 → 墙体 → 门（墙体最后一排中间换门）
  // 不同建筑用不同风格，避免“图书馆和竞技场长得一模一样”。
  house: {
    // 红瓦民居：红屋顶 + 碎砖屋檐 + 土坯墙
    red: { roofL: 53, roofM: 54, roofR: 55, eaveL: 65, gable: 68, eaveR: 67, wallL: 73, wallM: 74, wallR: 76, door: 75, window: 56 },
    // 灰石民居／公共建筑：石屋顶 + 浅色抹灰墙
    grey: { roofL: 49, roofM: 50, roofR: 51, eaveL: 61, gable: 64, eaveR: 63, wallL: 77, wallM: 78, wallR: 80, door: 79, window: 52 },
    // 城堡式（镇长大厅 / 竞技场）：齿形城埵 + 石墙 + 城门
    stone: { roofL: 99, roofM: 100, roofR: 101, eaveL: 97, gable: 98, eaveR: 98, wallL: 97, wallM: 97, wallR: 98, door: 104, window: 97 },
    // 木匠／工坊：深木色墙 + 大木门
    wood: { roofL: 53, roofM: 54, roofR: 55, eaveL: 73, gable: 66, eaveR: 76, wallL: 73, wallM: 74, wallR: 76, door: 86, window: 55 },
  },
};

// ---------- 通用工具 ----------

/** 生成 w*h 的零填充数组（gid 0 = 空 tile） */
function emptyData(w, h) {
  return new Array(w * h).fill(0);
}

/** 全填一个 gid（用于 ground 全铺草地 / 矿洞铺地板） */
function fillAll(data, gid) {
  for (let i = 0; i < data.length; i += 1) data[i] = gid;
}

/** 矩形填充 */
function fillRect(data, w, x0, y0, rw, rh, gid) {
  for (let dy = 0; dy < rh; dy += 1) {
    for (let dx = 0; dx < rw; dx += 1) {
      const xi = x0 + dx;
      const yi = y0 + dy;
      if (xi < 0 || yi < 0) continue;
      data[yi * w + xi] = gid;
    }
  }
}

/** 单点设置 */
function setTile(data, w, x, y, gid) {
  if (x < 0 || y < 0) return;
  data[y * w + x] = gid;
}

/**
 * 按房屋四件套（屋顶 / 屋檐+山墙 / 墙体 / 门）拼一栋房子。
 * 帧号全部经过图集核对，见 TOWN_GIDS.house 注释。
 * variant 不传时按坐标奇偶选style，保证相邻房子屋顶颜色有变化。
 */
function paintBuilding(buildingsData, w, x0, y0, bw, bh, variant) {
  const styles = TOWN_GIDS.house;
  const style = styles[variant] ?? (((x0 + y0) % 2 === 0) ? styles.red : styles.grey);
  const width = Math.max(2, bw);
  const height = Math.max(2, bh);
  const mid = Math.floor((width - 1) / 2);
  const doorCol = Math.floor((width - 1) / 2);

  for (let dx = 0; dx < width; dx += 1) {
    const isLeft = dx === 0;
    const isRight = dx === width - 1;
    for (let dy = 0; dy < height; dy += 1) {
      let gid;
      if (dy === 0) {
        // 屋顶斜坡
        gid = isLeft ? style.roofL : isRight ? style.roofR : style.roofM;
      } else if (dy === 1) {
        // 屋檐；正中一列放山墙三角，形成人字屋顶轮廓
        if (width >= 3 && dx === mid) gid = style.gable;
        else gid = isLeft ? style.eaveL : isRight ? style.eaveR : style.roofM;
      } else if (dy === height - 1) {
        // 最底排：正中开门
        gid = dx === doorCol ? style.door : isLeft ? style.wallL : isRight ? style.wallR : style.wallM;
      } else {
        // 中间墙体：宽房在正中开一扇窗，避免整面砖墙没细节
        gid = width >= 4 && dx === mid ? style.window : isLeft ? style.wallL : isRight ? style.wallR : style.wallM;
      }
      setTile(buildingsData, w, x0 + dx, y0 + dy, gid);
    }
  }
}

/** 用纯沙地铺一块矩形，四角/四边用圆角沙地帧，避免“硬方块路”。 */
function paintSandRect(data, w, x0, y0, rw, rh) {
  for (let dy = 0; dy < rh; dy += 1) {
    for (let dx = 0; dx < rw; dx += 1) {
      let gid = TOWN_GIDS.sandPlain;
      const top = dy === 0;
      const bottom = dy === rh - 1;
      const left = dx === 0;
      const right = dx === rw - 1;
      if (top) gid = left ? 13 : right ? 14 : 13;
      else if (bottom) gid = left ? 37 : right ? 38 : 37;
      else if (left) gid = 25;
      else if (right) gid = 26;
      setTile(data, w, x0 + dx, y0 + dy, gid);
    }
  }
}

/** 画一个矩形围栏（仅边框，4 条边），用于知识田/班级广场围合 */
function paintFenceRect(data, w, x0, y0, rw, rh) {
  for (let dx = 0; dx < rw; dx += 1) {
    setTile(data, w, x0 + dx, y0, TOWN_GIDS.fenceWoodH);
    setTile(data, w, x0 + dx, y0 + rh - 1, TOWN_GIDS.fenceWoodH);
  }
  for (let dy = 0; dy < rh; dy += 1) {
    setTile(data, w, x0, y0 + dy, TOWN_GIDS.fenceWoodV);
    setTile(data, w, x0 + rw - 1, y0 + dy, TOWN_GIDS.fenceWoodV);
  }
}

/** 简单确定性 PRNG（mulberry32），用于在不同地图间得到可复现的 decoration 散布 */
function mulberry32(seed) {
  let a = seed >>> 0;
  return function rng() {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** 撒一些装饰 tile，给定区域内随机点，避开 buildings 层占用的位置 */
function scatterDecoration(decoData, buildingsData, w, h, count, gids, rng, excludeRect = null) {  let placed = 0;
  let attempts = 0;
  while (placed < count && attempts < count * 12) {
    attempts += 1;
    const x = Math.floor(rng() * w);
    const y = Math.floor(rng() * h);
    const idx = y * w + x;
    if (buildingsData[idx] !== 0) continue;
    if (decoData[idx] !== 0) continue;
    if (excludeRect && x >= excludeRect.x && x < excludeRect.x + excludeRect.w &&
        y >= excludeRect.y && y < excludeRect.y + excludeRect.h) continue;
    const gid = gids[Math.floor(rng() * gids.length)];
    decoData[idx] = gid;
    placed += 1;
  }
}

// ---------- 通用 Tilemap JSON 构造 ----------

/**
 * 把 layer + objects spec 包成完整 Phaser tilemap JSON
 * @param {object} spec
 * @param {string} spec.key - 地图 key (town_hub / farm_north / ...)
 * @param {number} spec.width - 列数
 * @param {number} spec.height - 行数
 * @param {string} spec.tileset - tileset name (kenney_tiny_town | kenney_tiny_dungeon)
 * @param {number[]} spec.ground - 长度 w*h 的 gid 数组
 * @param {number[]} spec.buildings - 长度 w*h
 * @param {number[]} spec.decoration - 长度 w*h
 * @param {object[]} spec.objects - object 数组
 */
function buildMapJson(spec) {
  const tilesetMeta = TILESET_META[spec.tileset];
  if (!tilesetMeta) {
    throw new Error(`Unknown tileset: ${spec.tileset}`);
  }

  return {
    version: 1,
    tiledversion: TILED_VERSION,
    type: 'map',
    orientation: 'orthogonal',
    renderorder: 'right-down',
    width: spec.width,
    height: spec.height,
    tilewidth: TILE,
    tileheight: TILE,
    infinite: false,
    nextlayerid: 5,
    nextobjectid: spec.objects.length + 1,
    properties: [
      { name: 'mapKey', type: 'string', value: spec.key },
      { name: 'label', type: 'string', value: spec.label ?? spec.key },
    ],
    tilesets: [
      {
        firstgid: 1,
        name: tilesetMeta.name,
        tilewidth: TILE,
        tileheight: TILE,
        tilecount: 132,
        columns: 12,
        image: tilesetMeta.image,
        imagewidth: 192,
        imageheight: 176,
        margin: 0,
        spacing: 0,
      },
    ],
    layers: [
      {
        id: 1,
        name: 'ground',
        type: 'tilelayer',
        x: 0,
        y: 0,
        width: spec.width,
        height: spec.height,
        opacity: 1,
        visible: true,
        data: spec.ground,
      },
      {
        id: 2,
        name: 'buildings',
        type: 'tilelayer',
        x: 0,
        y: 0,
        width: spec.width,
        height: spec.height,
        opacity: 1,
        visible: true,
        data: spec.buildings,
      },
      {
        id: 3,
        name: 'decoration',
        type: 'tilelayer',
        x: 0,
        y: 0,
        width: spec.width,
        height: spec.height,
        opacity: 1,
        visible: true,
        data: spec.decoration,
      },
      {
        id: 4,
        name: 'objects',
        type: 'objectgroup',
        x: 0,
        y: 0,
        opacity: 1,
        visible: true,
        draworder: 'topdown',
        objects: spec.objects,
      },
    ],
  };
}

const TILESET_META = {
  kenney_tiny_town: {
    name: 'kenney_tiny_town',
    image: '../pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png',
  },
  kenney_tiny_dungeon: {
    name: 'kenney_tiny_dungeon',
    image: '../pixel/tilesets/kenney-tiny-dungeon/Tilemap/tilemap_packed.png',
  },
};

// ---------- objects 构造助手 ----------

let _objectIdSeq = 0;
function nextObjectId() {
  _objectIdSeq += 1;
  return _objectIdSeq;
}

/** 重置 object id 序列（每张地图独立编号） */
function resetObjectIds() {
  _objectIdSeq = 0;
}

/** 出口对象（target = 目标地图 key），单位：源像素（16） */
function makeExit(name, side, mapW, mapH, target) {
  const id = nextObjectId();
  if (side === 'north') {
    return {
      id,
      name,
      type: 'exit',
      x: (mapW / 2 - 2) * TILE,
      y: 0,
      width: 4 * TILE,
      height: TILE,
      visible: true,
      rotation: 0,
      properties: [
        { name: 'target', type: 'string', value: target },
        { name: 'side', type: 'string', value: 'north' },
      ],
    };
  }
  if (side === 'south') {
    return {
      id,
      name,
      type: 'exit',
      x: (mapW / 2 - 2) * TILE,
      y: (mapH - 1) * TILE,
      width: 4 * TILE,
      height: TILE,
      visible: true,
      rotation: 0,
      properties: [
        { name: 'target', type: 'string', value: target },
        { name: 'side', type: 'string', value: 'south' },
      ],
    };
  }
  if (side === 'east') {
    return {
      id,
      name,
      type: 'exit',
      x: (mapW - 1) * TILE,
      y: (mapH / 2 - 2) * TILE,
      width: TILE,
      height: 4 * TILE,
      visible: true,
      rotation: 0,
      properties: [
        { name: 'target', type: 'string', value: target },
        { name: 'side', type: 'string', value: 'east' },
      ],
    };
  }
  // west
  return {
    id,
    name,
    type: 'exit',
    x: 0,
    y: (mapH / 2 - 2) * TILE,
    width: TILE,
    height: 4 * TILE,
    visible: true,
    rotation: 0,
    properties: [
      { name: 'target', type: 'string', value: target },
      { name: 'side', type: 'string', value: 'west' },
    ],
  };
}

/** 建筑标记对象（点击区） */
function makeBuilding(name, label, route, tileX, tileY, bw, bh) {
  return {
    id: nextObjectId(),
    name,
    type: 'building',
    x: tileX * TILE,
    y: tileY * TILE,
    width: bw * TILE,
    height: bh * TILE,
    visible: true,
    rotation: 0,
    properties: [
      { name: 'route', type: 'string', value: route },
      { name: 'label', type: 'string', value: label },
    ],
  };
}

/** NPC 出生点对象（点对象，width=height=0） */
function makeNpc(name, key, label, tileX, tileY, sprite = 'player_sara') {
  return {
    id: nextObjectId(),
    name,
    type: 'npc',
    x: tileX * TILE,
    y: tileY * TILE,
    width: 0,
    height: 0,
    visible: true,
    rotation: 0,
    properties: [
      { name: 'key', type: 'string', value: key },
      { name: 'label', type: 'string', value: label },
      { name: 'sprite', type: 'string', value: sprite },
    ],
  };
}

/** 玩家 spawn 对象 */
function makeSpawn(tileX, tileY) {
  return {
    id: nextObjectId(),
    name: 'spawn',
    type: 'spawn',
    x: tileX * TILE,
    y: tileY * TILE,
    width: 0,
    height: 0,
    visible: true,
    rotation: 0,
    properties: [],
  };
}

/** decoration 用：地标对象（喷泉 / 水井 / 灯塔）—— 不参与碰撞，仅用作可视化锚点 */
function makeLandmark(name, label, tileX, tileY, bw, bh, kind = 'landmark') {
  return {
    id: nextObjectId(),
    name,
    type: kind,
    x: tileX * TILE,
    y: tileY * TILE,
    width: bw * TILE,
    height: bh * TILE,
    visible: true,
    rotation: 0,
    properties: [{ name: 'label', type: 'string', value: label }],
  };
}

const LOW_FENCE_FRAMES = {
  post: 0,
  horizontal: 1,
  vertical: 2,
  corner: 3,
  stump: 4,
  cap: 5,
};

/** 低障碍对象：玩家不可直接走过，但可用 Space 按当前朝向跳过 1 格。 */
function makeLowObstacle(name, label, tileX, tileY, bw = 1, bh = 1, kind = 'low', frame = LOW_FENCE_FRAMES.post) {
  return {
    id: nextObjectId(),
    name,
    type: 'low_obstacle',
    x: tileX * TILE,
    y: tileY * TILE,
    width: bw * TILE,
    height: bh * TILE,
    visible: true,
    rotation: 0,
    properties: [
      { name: 'label', type: 'string', value: label },
      { name: 'kind', type: 'string', value: kind },
      { name: 'sprite', type: 'string', value: 'low_fence_16' },
      { name: 'frame', type: 'int', value: frame },
    ],
  };
}

// ---------- 各张地图配置 + 生成器 ----------

// ============================================================
// town_hub (50×40)
// ============================================================
function generateTownHub() {
  const W = 50;
  const H = 40;
  const ground = emptyData(W, H);
  const buildings = emptyData(W, H);
  const decoration = emptyData(W, H);
  resetObjectIds();

  fillAll(ground, TOWN_GIDS.grass);
  // 草地纹理：确定性噪点，避免 50×40 一整块死平绿（只做轻微点缀，不制造“草坪长满杂物”的观感）
  const groundRng = mulberry32(0x9e3779b9);
  for (let i = 0; i < ground.length; i += 1) {
    const r = groundRng();
    if (r < 0.045) ground[i] = TOWN_GIDS.grassSpeckle;
    else if (r < 0.06) ground[i] = TOWN_GIDS.grassFlower;
  }

  // 中央广场：5×5 沙地，四边/四角圆化（喷泉就落在广场中央）
  paintSandRect(ground, W, 23, 18, 5, 5);

  // 主轴：喷泉往南的 2 格宽主街；副轴：贯穿东西的 2 格宽横街
  for (let y = 21; y < H - 1; y += 1) {
    setTile(ground, W, 24, y, TOWN_GIDS.sandPlain);
    setTile(ground, W, 25, y, TOWN_GIDS.sandPlain);
  }
  for (let x = 1; x < W - 1; x += 1) {
    if (x === 24 || x === 25) continue;
    setTile(ground, W, x, 19, TOWN_GIDS.sandPlain);
    setTile(ground, W, x, 20, TOWN_GIDS.sandPlain);
  }
  // 北向支路：广场 → 镇长大厅门前
  for (let y = 13; y < 18; y += 1) {
    setTile(ground, W, 24, y, TOWN_GIDS.sandPlain);
    setTile(ground, W, 25, y, TOWN_GIDS.sandPlain);
  }

  // 镇长大厅：北中央，4×5（叙事上对应「东校园老镇厅」，承担画像 / 主线推进）
  const mayorHall = { x: 22, y: 4, w: 5, h: 5, label: '镇长大厅', route: '/profile-room', name: 'building_mayor_hall' };
  paintBuilding(buildings, W, mayorHall.x, mayorHall.y, mayorHall.w, mayorHall.h, 'red');

  // R-13 §-1 后续：在镇长大厅东西两侧加 2 栋纺织主题民居（不命名校名），
  // 叙事上呼应「松江纺织文化」+「东华纺织学院」一脉相承的小镇背景；
  // 实现：复用 paintBuilding 普通房屋瓦面，标记 building_textile_pavilion_*。
  const textileWest = { x: 16, y: 5, w: 4, h: 4, label: '纺织展馆·西', route: '/house-west', name: 'building_textile_pavilion_w' };
  const textileEast = { x: 30, y: 5, w: 4, h: 4, label: '纺织展馆·东', route: '/house-east', name: 'building_textile_pavilion_e' };
  paintBuilding(buildings, W, textileWest.x, textileWest.y, textileWest.w, textileWest.h, 'red');
  paintBuilding(buildings, W, textileEast.x, textileEast.y, textileEast.w, textileEast.h, 'grey');

  // 南面 3 栋民居（peasant 房子）
  const housesSouth = [
    { x: 8, y: 28, w: 4, h: 4, label: '民居·东', route: '/house-east', name: 'building_house_a' },
    { x: 22, y: 28, w: 4, h: 4, label: '民居·中', route: '/house-mid', name: 'building_house_b' },
    { x: 36, y: 28, w: 4, h: 4, label: '民居·西', route: '/house-west', name: 'building_house_c' },
  ];
  housesSouth.forEach((h, i) => paintBuilding(buildings, W, h.x, h.y, h.w, h.h, i % 2 === 0 ? 'red' : 'grey'));

  // R-13 优化：货币/商店系统移除 —— 删除 东西商店（杂货铺/工具铺）。

  const lowPathFences = [];
  for (let y = 22; y <= 34; y += 2) {
    lowPathFences.push(
      makeLowObstacle(`low_path_fence_w_${y}`, '主街西侧矮栅栏', 23, y, 1, 1, 'fence', LOW_FENCE_FRAMES.vertical),
      makeLowObstacle(`low_path_fence_e_${y}`, '主街东侧矮栅栏', 26, y, 1, 1, 'fence', LOW_FENCE_FRAMES.vertical),
    );
  }
  lowPathFences.push(
    makeLowObstacle('low_path_fence_gate_w', '主街低木桩', 23, 20, 1, 1, 'stump', LOW_FENCE_FRAMES.post),
    makeLowObstacle('low_path_fence_gate_e', '主街低木桩', 26, 20, 1, 1, 'stump', LOW_FENCE_FRAMES.post),
  );

  // 原外围图建筑收进中央小镇（R-14 不再保留农场/作物玩法）
  // campus_east 建筑
  const library = { x: 10, y: 8, w: 5, h: 4, label: '图书馆', route: '/library', name: 'building_library' };
  const reviewHall = { x: 36, y: 8, w: 4, h: 4, label: '复盘大厅', route: '/review-hall', name: 'building_review_hall' };
  const workshop = { x: 40, y: 24, w: 4, h: 4, label: '百晓工坊', route: '/workshop', name: 'building_workshop' };
  paintBuilding(buildings, W, library.x, library.y, library.w, library.h, 'grey');
  paintBuilding(buildings, W, reviewHall.x, reviewHall.y, reviewHall.w, reviewHall.h, 'grey');
  paintBuilding(buildings, W, workshop.x, workshop.y, workshop.w, workshop.h, 'wood');
  // Bug 矿洞功能入口（点击 → /bug-mine 页面；R-14 已删除 mine_south 风景地图，此处仅作功能入口、不做地图传送）
  const mine = { x: 18, y: 33, w: 4, h: 3, label: 'Bug 矿洞', route: '/bug-mine', name: 'building_mine' };
  // 矿洞单独拼：外框素石墙 + 中间 2×2 石拱洞口（帧 111..114 是拱的左右两半）。
  const MINE_ARCH = [[112, 113], [111, 114]];
  for (let dy = 0; dy < mine.h; dy += 1) {
    for (let dx = 0; dx < mine.w; dx += 1) {
      const archRow = dy - (mine.h - 2);
      const archCol = dx - 1;
      const inArch = archRow >= 0 && archRow <= 1 && archCol >= 0 && archCol <= 1;
      const edge = dx === 0 || dx === mine.w - 1;
      const gid = inArch ? MINE_ARCH[archRow][archCol] : edge ? 97 : 98;
      setTile(buildings, W, mine.x + dx, mine.y + dy, gid);
    }
  }
  // 竞技场
  const arena = { x: 36, y: 34, w: 4, h: 3, label: '闯关竞技场', route: '/arena/1', name: 'building_arena' };
  paintBuilding(buildings, W, arena.x, arena.y, arena.w, arena.h, 'grey');
  // 训练场（点击 → 题库列表 /problems，先选题再去训练场做题界面）
  const training = { x: 8, y: 34, w: 4, h: 3, label: '训练场', route: '/problems', name: 'building_training' };
  paintBuilding(buildings, W, training.x, training.y, training.w, training.h, 'wood');

  // ---------- 装饰层（构图化，不再全图随机撒）----------
  const rng = mulberry32(0x10101);
  const reserved = new Set();
  const reserve = (tx, ty, pad = 0) => {
    for (let dy = -pad; dy <= pad; dy += 1) {
      for (let dx = -pad; dx <= pad; dx += 1) {
        const x = tx + dx;
        const y = ty + dy;
        if (x >= 0 && y >= 0 && x < W && y < H) reserved.add(y * W + x);
      }
    }
  };
  [mayorHall, textileWest, textileEast, ...housesSouth, library, reviewHall, workshop, mine, arena, training]
    .forEach((b) => {
      for (let dy = b.y - 1; dy <= b.y + b.h; dy += 1) {
        for (let dx = b.x - 1; dx <= b.x + b.w; dx += 1) {
          if (dx >= 0 && dy >= 0 && dx < W && dy < H) reserved.add(dy * W + dx);
        }
      }
    });
  [[25, 10], [11, 12], [42, 28], [10, 33], [25, 33], [37, 12], [18, 21], [25, 20]]
    .forEach(([tx, ty]) => reserve(tx, ty, 1));

  const canDecorate = (tx, ty) => {
    if (tx < 1 || ty < 1 || tx >= W - 1 || ty >= H - 1) return false;
    const idx = ty * W + tx;
    if (reserved.has(idx)) return false;
    if (buildings[idx] !== 0) return false;
    return ground[idx] === TOWN_GIDS.grass || ground[idx] === TOWN_GIDS.grassSpeckle;
  };

  const put = (tx, ty, gid) => {
    if (!canDecorate(tx, ty)) return;
    setTile(decoration, W, tx, ty, gid);
    reserved.add(ty * W + tx);
  };

  // 1) 地图四周的林带：隔格种，松树/树冠交替 + 留缺口，避免变成一排“∧∧∧”
  const borderTree = () => (rng() < 0.5 ? TOWN_GIDS.treeRound : TOWN_GIDS.treePine);
  for (let x = 1; x < W - 1; x += 2) {
    if (rng() < 0.9) put(x, 1, borderTree());
    if (rng() < 0.9) put(x, H - 2, borderTree());
  }
  for (let y = 2; y < H - 2; y += 2) {
    if (rng() < 0.88) put(1, y, borderTree());
    if (rng() < 0.88) put(W - 2, y, borderTree());
  }

  // 2) 林丛：交错格（棋盘）布点而不是满铺，林地边缘用灌木过渡，视觉上才像树林而不是草坪长满草
  const FOREST_TREES = [TOWN_GIDS.treeRound, TOWN_GIDS.treeRound, TOWN_GIDS.treeRound, TOWN_GIDS.treePine];
  const FOREST_EDGE = [TOWN_GIDS.bushBig, TOWN_GIDS.treePine];
  const clusterRng = mulberry32(0x5eed);
  // 成片林：内部实心铺（满才像树林），外圈用灌木/松树过渡
  const clusters = [[9, 15, 3], [42, 15, 3], [12, 32, 3], [38, 31, 3]];
  clusters.forEach(([cx, cy, radius]) => {
    for (let dy = -radius; dy <= radius; dy += 1) {
      for (let dx = -radius; dx <= radius; dx += 1) {
        const dist = Math.max(Math.abs(dx), Math.abs(dy));
        if (dist > radius) continue;
        const outer = dist === radius;
        if (clusterRng() > (outer ? 0.55 : 0.92)) continue;
        const gid = outer
          ? FOREST_EDGE[Math.floor(clusterRng() * FOREST_EDGE.length)]
          : FOREST_TREES[Math.floor(clusterRng() * FOREST_TREES.length)];
        put(cx + dx, cy + dy, gid);
      }
    }
  });

  // 3) 建筑门前：路牌 / 木桶 / 木箱，给每个入口一个“门口”语感
  const doorFront = (b, gid, offset = 0) => {
    const tx = b.x + Math.floor(b.w / 2) + offset;
    const ty = b.y + b.h;
    setTile(decoration, W, tx, ty, gid);
    reserved.add(ty * W + tx);
  };
  doorFront(library, TOWN_GIDS.signPost, -2);
  doorFront(reviewHall, TOWN_GIDS.signPost, 2);
  doorFront(workshop, TOWN_GIDS.barrel, -2);
  doorFront(arena, TOWN_GIDS.signRed, 2);
  doorFront(training, TOWN_GIDS.crate, 2);
  doorFront(mayorHall, TOWN_GIDS.signRed, 2);

  // 4) 广场：水井 + 四角花带（原本用粉色块冒充喷泉，现改为实心沙地上摆水井 + 花）
  setTile(decoration, W, 24, 19, TOWN_GIDS.well);
  [[22, 17], [27, 17], [22, 22], [27, 22]].forEach(([fx, fy]) => {
    setTile(decoration, W, fx, fy, TOWN_GIDS.grassFlower);
  });

  // 5) 主街两侧的低矮栅栏由 objects 层（low_fence_16 sprite）负责，这里不再重复铺瓦片，
  //    避免同一格同时出现 sprite 与瓦片两层木桩。

  // 专用低栅栏沿主街围出小路：中间 2 格保持通行，两侧是可跳过的低障碍。
  // objects ---------- (no exits, single map)
  // R-14 Phase 0: 纺织展馆与南面民居仅保留视觉瓦片（paintBuilding），移除无路由的点击对象；
  // 避免玩家点击后进入死路 placeholder 页面。视觉房屋仍参与碰撞（buildings layer）。
  const objects = [
    makeBuilding(mayorHall.name, mayorHall.label, mayorHall.route, mayorHall.x, mayorHall.y, mayorHall.w, mayorHall.h),
    makeBuilding(library.name, library.label, library.route, library.x, library.y, library.w, library.h),
    makeBuilding(reviewHall.name, reviewHall.label, reviewHall.route, reviewHall.x, reviewHall.y, reviewHall.w, reviewHall.h),
    makeBuilding(workshop.name, workshop.label, workshop.route, workshop.x, workshop.y, workshop.w, workshop.h),
    makeBuilding(mine.name, mine.label, mine.route, mine.x, mine.y, mine.w, mine.h),
    makeBuilding(arena.name, arena.label, arena.route, arena.x, arena.y, arena.w, arena.h),
    makeBuilding(training.name, training.label, training.route, training.x, training.y, training.w, training.h),
    makeLandmark('fountain', '镜月喷泉', 24, 19, 2, 2, 'landmark'),
    ...lowPathFences,
    makeLowObstacle('low_tutorial_log', '跳跃练习木桩', 26, 20, 1, 1, 'stump', LOW_FENCE_FRAMES.post),
    // R-14 精简 NPC：移除与主线/合并后系统重复或失效的居民——
    //   tutor_ai（导师已并入百晓工坊，与 workshop_master 重复）、trainer（与 coach_q 重复）、
    //   miner（与守矿人 / 矿头 Bug 重复）、farmer + gardener（农场 / 作物系统已删）、
    //   chef + sailor（纯装饰 + 已删的商店 / 商船）。保留的每个 NPC 各担一项学习闭环职责。
    // 站位：贴近对应建筑入口，彼此错开 ≥2 tile，避免重叠。
    // R-14 核心阵容收敛：只保留 7 个主线 NPC（镇长引导 / 馆长看 / 工坊师傅学 /
    // 教务 Q 练·测 / 矿头 Bug 调试 / 复盘师复盘 / 班长班级）。移除 professor、student_a、
    // arena_guard、mine_keeper 的「地图站位」——它们的数据文件保留（professor 头像仍被
    // 工坊智能体工作室复用），仅不再在小镇里出现，降低首页 NPC 认知负荷。
    makeNpc('npc_mayor_py', 'mayor_py', '镇长 Py', 25, 10, 'npc_mayor'),
    makeNpc('npc_librarian_doc', 'librarian_doc', '馆长 Doc', 11, 12, 'npc_librarian'),
    makeNpc('npc_workshop_master', 'workshop_master', '工坊师傅', 42, 28, 'npc_workshop'),
    makeNpc('npc_coach_q', 'coach_q', '教务 Q', 10, 33, 'npc_coach'),
    makeNpc('npc_foreman_bug', 'foreman_bug', '矿头 Bug', 25, 33, 'npc_guard'),
    makeNpc('npc_sage_review', 'sage_review', '复盘师 Sage', 37, 12, 'npc_sage'),
    makeNpc('npc_class_president', 'class_president', '班长', 18, 21, 'npc_student'),
    makeSpawn(25, 20),
  ];

  return buildMapJson({
    key: 'town_hub',
    label: 'Pythontopia 小镇',
    width: W,
    height: H,
    tileset: 'kenney_tiny_town',
    ground,
    buildings,
    decoration,
    objects,
  });
}

// ---------- index.json ----------

function buildIndex() {
  return {
    version: 1,
    generatedAt: new Date().toISOString(),
    maps: [
      {
        key: 'town_hub',
        file: 'town_hub.json',
        label: 'Pythontopia 小镇',
        width: 50,
        height: 40,
        bgm: 'bgm_town_day',
        tileset: 'kenney_tiny_town',
        // R-14 单中央 hub：town_hub.json 不含任何 exit 对象，index 出口声明须与地图一致（空）。
        // 进入 Bug 矿洞经 building → /bug-mine 路由，不走地图边缘 walk-exit。
        exits: [],
        spawn: { tileX: 25, tileY: 20 },
      },
    ],
  };
}

// ---------- README.md ----------

function buildReadme(indexObj) {
  const rows = indexObj.maps
    .map(
      (m) =>
        `| ${m.key} | ${m.label} | ${m.width}×${m.height} | ${m.tileset} | ${m.bgm} | ${m.exits.join(', ')} | (${m.spawn.tileX}, ${m.spawn.tileY}) |`,
    )
    .join('\n');

  return `# Pythontopia 地图（R-14 世界收缩）

R-14 方案 A：仅生成 **town_hub**（中央小镇）。外围图与 Bug 矿洞风景图（mine_south）均已移除；Bug 矿洞功能仍在，经 town_hub building → /bug-mine 进入。

## 概览

| key | 中文名 | 尺寸（列×行） | tileset | BGM | 出口 | spawn |
| --- | --- | --- | --- | --- | --- | --- |
${rows}

## town_hub.json（50×40）

- 出口：无（R-14 单中央 hub，外围图已移除；Bug 矿洞经 building → /bug-mine 路由进入）
- 学习建筑：图书馆 / 工坊 / 训练场 / 竞技场 / 复盘大厅 / Bug 矿洞入口等
- 7 核心 NPC 贴近建筑入口排布：镇长 Py / 馆长 Doc / 工坊师傅 / 教务 Q / 矿头 Bug / 复盘师 Sage / 班长（见 \`scripts/generate-maps.mjs\` 内 \`makeNpc\` 列表）

## 文件结构

\`\`\`
frontend/public/assets/maps/
├── index.json
├── README.md
└── town_hub.json
\`\`\`

## 字段约定

### tileset 引用

- 每张地图只用 1 个 tileset，\`firstgid = 1\`
- \`tilesets[].image\` 路径相对 maps/ 目录：
  - kenney_tiny_town → \`../pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png\`
  - kenney_tiny_dungeon → \`../pixel/tilesets/kenney-tiny-dungeon/Tilemap/tilemap_packed.png\`

### layer 顺序

| id | name | type | 说明 |
| --- | --- | --- | --- |
| 1 | ground | tilelayer | 草地 / 沙地 / 水面（不参与碰撞） |
| 2 | buildings | tilelayer | 房屋外墙 + 矿洞入口（runtime 视为碰撞，玩家不可穿过） |
| 3 | decoration | tilelayer | 树木、花坛、围栏、矿石（一般不参与碰撞，纯视觉） |
| 4 | objects | objectgroup | exit / building / npc / spawn / landmark 等元数据 |

### objects 对象类型

- \`exit\`：边缘门洞，\`properties.target\` 指向下一张地图 key
- \`building\`：建筑点击区，\`properties.route\` 指向 Vue 路由
- \`npc\`：NPC 出生点，\`properties.key\` / \`label\` / \`sprite\`
- \`spawn\`：玩家默认出生点（每张地图唯一）
- \`low_obstacle\`：低障碍物，正常行走视为阻挡，玩家可面朝它按 Space 跳过 1 格
- \`landmark\` / \`field\` / \`plaza\`：纯视觉锚点

## 编辑流程

### 方式 1：在 Tiled 编辑器里改

1. 安装 [Tiled Map Editor](https://www.mapeditor.org/)（1.10+）
2. 用 Tiled 打开任意 \`*.json\`
3. 编辑后导出回原 JSON 即可

### 方式 2：改脚本重新生成（推荐）

地图布局都写在 \`scripts/generate-maps.mjs\` 顶部的常量 / 函数里：

\`\`\`bash
cd /home/cypress/Pythontopia
node scripts/generate-maps.mjs
\`\`\`

执行后会重新覆盖：

- \`frontend/public/assets/maps/*.json\`
- \`frontend/public/assets/maps/index.json\`
- 本 README.md 不会自动重写（只在脚本本体改动时更新一次）

## 当前已知 TODO

- decoration 层目前只散布 bush / flower / fence 三类瓷砖；后续 W2-MAPS 二期会引入更精细的 Tiled object（NPC 走路路径、商铺货架、矿洞机关等）。
- 各地图碰撞规则现在由 buildings 层 + 边界 implicit 阻挡推断；M2-ROUTER 子代理可能需要额外的 \`collision\` 数据层来标记不可穿越的装饰（例如树木）。
- 字段 \`spawn\` 在 index.json 与 map.objects 内重复出现：runtime 默认读 index.json，地图内的 \`spawn\` 对象作为权威覆盖。
`;
}

// ---------- 主入口 ----------

async function main() {
  await fs.mkdir(MAPS_DIR, { recursive: true });

  // R-14 方案A：只生成中央小镇 town_hub；farm_north / campus_east / port_west / mine_south 风景图均已废弃。
  const generators = [
    { name: 'town_hub.json', build: generateTownHub },
  ];

  const dryRun = process.argv.includes('--dry-run');

  const results = [];
  for (const g of generators) {
    const json = g.build();
    const out = JSON.stringify(json, null, 2);
    const filePath = path.join(MAPS_DIR, g.name);
    if (!dryRun) await fs.writeFile(filePath, out, 'utf-8');
    results.push({
      name: g.name,
      bytes: Buffer.byteLength(out, 'utf-8'),
      layers: json.layers.length,
      groundCells: json.layers[0].data.length,
      buildingCells: json.layers[1].data.length,
      decorationCells: json.layers[2].data.length,
      objects: json.layers[3].objects.length,
    });
  }

  const indexObj = buildIndex();
  const indexStr = JSON.stringify(indexObj, null, 2);
  if (!dryRun) await fs.writeFile(path.join(MAPS_DIR, 'index.json'), indexStr, 'utf-8');

  const readmeStr = buildReadme(indexObj);
  if (!dryRun) await fs.writeFile(path.join(MAPS_DIR, 'README.md'), readmeStr, 'utf-8');

  console.log('[generate-maps] 写入完成：');
  for (const r of results) {
    console.log(
      `  - ${r.name.padEnd(20)} ${String(r.bytes).padStart(8)} bytes | layers=${r.layers} | ground=${r.groundCells} | building=${r.buildingCells} | deco=${r.decorationCells} | objects=${r.objects}`,
    );
  }
  console.log(`  - index.json         ${String(Buffer.byteLength(indexStr, 'utf-8')).padStart(8)} bytes`);
  console.log(`  - README.md          ${String(Buffer.byteLength(readmeStr, 'utf-8')).padStart(8)} bytes`);
  if (dryRun) console.log('[generate-maps] --dry-run：未写入文件。');
}

main().catch((err) => {
  console.error('[generate-maps] 失败：', err);
  process.exit(1);
});
