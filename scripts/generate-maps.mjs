#!/usr/bin/env node
/**
 * generate-maps.mjs — Pythontopia 五张分块大地图生成脚本（M2-W2-MAPS 子代理产出）
 *
 * @file 程序化生成 Phaser/Tiled 兼容的 JSON tilemap，覆盖：
 *   - town_hub   (50×40) 中央小镇
 *   - farm_north (40×40) 北农场
 *   - mine_south (30×30) 南矿洞（kenney_tiny_dungeon tileset）
 *   - campus_east(40×30) 东校园
 *   - port_west  (35×25) 西港口
 *
 * @summary
 *   - 单一脚本一次性生成 5 个 .json + 1 个 index.json + 1 个 README.md。
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
// Kenney tiny-town 132 帧已 PIL 抽样分析，下面只列入实际使用到的帧：
const TOWN_GIDS = {
  grass: 1, // frame 0 = 主草地（纯绿）
  grassFlower: 3, // frame 2 = 带黄色小花的草地（农田路面/装饰）
  sandPath: 10, // frame 9 = 砂石路面（黄色细节），用于矿洞外景或入口铺地
  // 4×N 建筑（沿用 M1-B BUILDING_FRAMES 约定，3 列对应房屋宽度归一化）
  bld: [
    [14, 15, 16], // 顶/屋顶：frame 13 / 14 / 15
    [17, 18, 19], // 上层墙：frame 16 / 17 / 18
    [20, 21, 22], // 中层墙：frame 19 / 20 / 21
    [23, 24, 25], // 底层墙（中间是门）：frame 22 / 23 / 24
  ],
  bushSmall: 5, // frame 4 = 小绿色灌木
  bushBig: 17, // frame 16 = 较大绿色灌木（屋边树）
  flowerPink: 26, // frame 25 = 纯粉色（用作花坛 / 喷泉花池）
  flowerYellow: 22, // frame 21 = 黄+棕（黄色花坛）
  fenceWoodH: 69, // frame 68 = 木质栅栏水平段
  fenceWoodV: 81, // frame 80 = 木质栅栏竖向段
  water: 50, // frame 49 = 纯蓝水面
  waterEdgeTop: 49, // frame 48 = 水边沿（上沿）
  waterEdgeBottom: 51, // frame 50 = 水边沿（下沿）
  waterDeep: 62, // frame 61 = 深蓝水（中央）
  treeStump: 85, // frame 84 = 棕色 + 暗色（树桩 / 木箱外观）
};

// Kenney tiny-dungeon 132 帧采样（用于矿洞）
const DUNG_GIDS = {
  floor: 1, // frame 0 = 灰石地板
  floorAlt: 13, // frame 12 = 地板变种 1
  floorAlt2: 25, // frame 24 = 地板变种 2
  voidDark: 10, // frame 9 = 黑色虚空（用作矿洞入口）
  voidDark2: 11, // frame 10
  voidDark3: 12, // frame 11
  wallBrown: 22, // frame 21 = 棕黑色墙（矿洞墙体）
  wallBrown2: 23, // frame 22
  wallBrown3: 24, // frame 23
  wallStone: 34, // frame 33 = 灰墙
  wallStone2: 35, // frame 34
  oreBlue: 96, // frame 95 = 蓝色矿石（深蓝点缀）
  chest: 110, // frame 109 = 宝箱 / 矿车
  pillar: 46, // frame 45 = 立柱
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
 * 按 M1-B 约定绘制建筑（4 行格式，处理任意 width × height）。
 * 在 buildingsData 写入建筑帧，自动跳过空 frame。
 */
function paintBuilding(buildingsData, w, x0, y0, bw, bh) {
  const FRAMES = TOWN_GIDS.bld;
  for (let dy = 0; dy < bh; dy += 1) {
    for (let dx = 0; dx < bw; dx += 1) {
      const col = dx === 0 ? 0 : dx === bw - 1 ? 2 : 1;
      const rowIdx = dy === 0 ? 0 : dy === bh - 1 ? 3 : dy === 1 ? 1 : 2;
      const gid = FRAMES[rowIdx]?.[col];
      if (typeof gid !== 'number') continue;
      setTile(buildingsData, w, x0 + dx, y0 + dy, gid);
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
function scatterDecoration(decoData, buildingsData, w, h, count, gids, rng, excludeRect = null) {
  let placed = 0;
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
  // 中央广场铺一层略带花纹的草地（24,18 ~ 26,22，5×5）
  fillRect(ground, W, 23, 18, 5, 5, TOWN_GIDS.grassFlower);

  // 镇长大厅：北中央，4×5
  const mayorHall = { x: 23, y: 4, w: 4, h: 5, label: '镇长大厅', route: '/profile-room', name: 'building_mayor_hall' };
  paintBuilding(buildings, W, mayorHall.x, mayorHall.y, mayorHall.w, mayorHall.h);

  // 南面 3 栋民居（peasant 房子）
  const housesSouth = [
    { x: 8, y: 28, w: 4, h: 4, label: '民居·东', route: '/house-east', name: 'building_house_a' },
    { x: 22, y: 28, w: 4, h: 4, label: '民居·中', route: '/house-mid', name: 'building_house_b' },
    { x: 36, y: 28, w: 4, h: 4, label: '民居·西', route: '/house-west', name: 'building_house_c' },
  ];
  housesSouth.forEach((h) => paintBuilding(buildings, W, h.x, h.y, h.w, h.h));

  // 东西商店
  const shops = [
    { x: 4, y: 14, w: 4, h: 4, label: '杂货铺', route: '/shop-west', name: 'building_shop_west' },
    { x: 42, y: 14, w: 4, h: 4, label: '工具铺', route: '/shop-east', name: 'building_shop_east' },
  ];
  shops.forEach((s) => paintBuilding(buildings, W, s.x, s.y, s.w, s.h));

  // 中央喷泉（24-25, 19-20，2×2，用粉色块代替石雕）
  fillRect(decoration, W, 24, 19, 2, 2, TOWN_GIDS.flowerPink);

  // 大厅前的花圃（蓝水当装饰水池 + 黄色花坛）
  fillRect(decoration, W, 24, 10, 2, 1, TOWN_GIDS.flowerYellow);

  // 沿地图边界种灌木（北/南），避开出口口
  for (let x = 2; x < W - 2; x += 3) {
    if (x < W / 2 - 4 || x > W / 2 + 4) {
      setTile(decoration, W, x, 1, TOWN_GIDS.bushBig);
      setTile(decoration, W, x, H - 2, TOWN_GIDS.bushBig);
    }
  }
  for (let y = 2; y < H - 2; y += 3) {
    if (y < H / 2 - 4 || y > H / 2 + 4) {
      setTile(decoration, W, 1, y, TOWN_GIDS.bushBig);
      setTile(decoration, W, W - 2, y, TOWN_GIDS.bushBig);
    }
  }

  // 撒一些小灌木在内场
  const rng = mulberry32(0x10101);
  scatterDecoration(
    decoration,
    buildings,
    W,
    H,
    24,
    [TOWN_GIDS.bushSmall, TOWN_GIDS.flowerYellow],
    rng,
    { x: 22, y: 17, w: 7, h: 7 }, // 喷泉广场不撒
  );

  // objects ----------
  const objects = [
    makeExit('exit_north', 'north', W, H, 'farm_north'),
    makeExit('exit_south', 'south', W, H, 'mine_south'),
    makeExit('exit_east', 'east', W, H, 'campus_east'),
    makeExit('exit_west', 'west', W, H, 'port_west'),
    makeBuilding(mayorHall.name, mayorHall.label, mayorHall.route, mayorHall.x, mayorHall.y, mayorHall.w, mayorHall.h),
    ...housesSouth.map((h) => makeBuilding(h.name, h.label, h.route, h.x, h.y, h.w, h.h)),
    ...shops.map((s) => makeBuilding(s.name, s.label, s.route, s.x, s.y, s.w, s.h)),
    makeLandmark('fountain', '中央喷泉', 24, 19, 2, 2, 'landmark'),
    makeNpc('npc_mayor_py', 'mayor_py', '镇长 Py', 25, 10),
    makeNpc('npc_librarian_doc', 'librarian_doc', '图书管理员 Doc', 6, 18),
    makeNpc('npc_coach_q', 'coach_q', '练习教练 Q', 44, 18),
    makeNpc('npc_sage_review', 'sage_review', '复盘贤者', 25, 24),
    makeNpc('npc_peasant_a', 'peasant_a', '居民 A', 12, 32),
    makeNpc('npc_peasant_b', 'peasant_b', '居民 B', 38, 32),
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

// ============================================================
// farm_north (40×40)
// ============================================================
function generateFarmNorth() {
  const W = 40;
  const H = 40;
  const ground = emptyData(W, H);
  const buildings = emptyData(W, H);
  const decoration = emptyData(W, H);
  resetObjectIds();

  fillAll(ground, TOWN_GIDS.grass);

  // 7 块知识田，每块 5×5，4+3 布局：行 5-9 / 行 14-18 / 行 23-27
  const fieldOrigins = [
    { x: 3, y: 5, kc: 'chapter_basics' },
    { x: 10, y: 5, kc: 'variable' },
    { x: 17, y: 5, kc: 'data_types' },
    { x: 3, y: 14, kc: 'control_flow' },
    { x: 10, y: 14, kc: 'loop' },
    { x: 17, y: 14, kc: 'function' },
    { x: 10, y: 23, kc: 'class' },
  ];
  fieldOrigins.forEach((f) => {
    // 田块内填带花草地，标记 ground
    fillRect(ground, W, f.x, f.y, 5, 5, TOWN_GIDS.grassFlower);
    // 围栏在 decoration 层
    paintFenceRect(decoration, W, f.x, f.y, 5, 5);
  });

  // 谷仓：东侧 (26,8)，5×4
  const barn = { x: 26, y: 8, w: 5, h: 4, label: '谷仓', route: '/storage', name: 'building_barn' };
  paintBuilding(buildings, W, barn.x, barn.y, barn.w, barn.h);

  // 鸡舍：东侧 (28,18)，4×3
  const coop = { x: 28, y: 18, w: 4, h: 3, label: '鸡舍', route: '/coop', name: 'building_chicken_coop' };
  paintBuilding(buildings, W, coop.x, coop.y, coop.w, coop.h);

  // 水井：单 tile (32, 25)
  setTile(decoration, W, 32, 25, TOWN_GIDS.water);
  setTile(decoration, W, 32, 24, TOWN_GIDS.waterEdgeTop);

  // 边界树林（北/东两侧），南边留给出口
  for (let x = 1; x < W - 1; x += 2) {
    setTile(decoration, W, x, 1, TOWN_GIDS.bushBig);
  }
  for (let y = 2; y < H - 6; y += 2) {
    setTile(decoration, W, W - 2, y, TOWN_GIDS.bushBig);
    setTile(decoration, W, 1, y, TOWN_GIDS.bushBig);
  }

  // 田间小路：从南出口走到中心田
  for (let y = 30; y < H - 1; y += 1) {
    setTile(ground, W, 19, y, TOWN_GIDS.sandPath);
    setTile(ground, W, 20, y, TOWN_GIDS.sandPath);
  }

  const rng = mulberry32(0x20202);
  scatterDecoration(decoration, buildings, W, H, 18, [TOWN_GIDS.bushSmall, TOWN_GIDS.flowerYellow], rng, {
    x: 0,
    y: 5,
    w: W,
    h: 25, // 不要在田块密集区域撒（避免污染）
  });

  // objects ----------
  const objects = [
    makeExit('exit_south', 'south', W, H, 'town_hub'),
    makeBuilding(barn.name, barn.label, barn.route, barn.x, barn.y, barn.w, barn.h),
    makeBuilding(coop.name, coop.label, coop.route, coop.x, coop.y, coop.w, coop.h),
    makeLandmark('well', '水井', 32, 24, 1, 2, 'landmark'),
    ...fieldOrigins.map((f, i) =>
      makeLandmark(`field_${f.kc}`, `知识田·${f.kc}`, f.x, f.y, 5, 5, 'field'),
    ),
    makeNpc('npc_farmer_kc', 'farmer_kc', '田主 KC', 28, 13, 'player_sara'),
    makeSpawn(20, 37),
  ];

  return buildMapJson({
    key: 'farm_north',
    label: '知识农场',
    width: W,
    height: H,
    tileset: 'kenney_tiny_town',
    ground,
    buildings,
    decoration,
    objects,
  });
}

// ============================================================
// mine_south (30×30) —— 使用 dungeon tileset
// ============================================================
function generateMineSouth() {
  const W = 30;
  const H = 30;
  const ground = emptyData(W, H);
  const buildings = emptyData(W, H);
  const decoration = emptyData(W, H);
  resetObjectIds();

  // ground = 地牢石地板
  fillAll(ground, DUNG_GIDS.floor);

  // 在矿洞内随机点缀地板变种，让地表不要太死
  for (let y = 0; y < H; y += 1) {
    for (let x = 0; x < W; x += 1) {
      if ((x * 13 + y * 7) % 19 === 0) {
        setTile(ground, W, x, y, DUNG_GIDS.floorAlt);
      } else if ((x * 11 + y * 5) % 23 === 0) {
        setTile(ground, W, x, y, DUNG_GIDS.floorAlt2);
      }
    }
  }

  // 外围一圈墙体，让矿洞看起来像封闭洞穴；北边中段留出口（出口处用 floor 不放墙）
  for (let x = 0; x < W; x += 1) {
    if (x < W / 2 - 2 || x > W / 2 + 1) {
      setTile(buildings, W, x, 0, DUNG_GIDS.wallBrown);
    }
    setTile(buildings, W, x, H - 1, DUNG_GIDS.wallBrown);
  }
  for (let y = 1; y < H - 1; y += 1) {
    setTile(buildings, W, 0, y, DUNG_GIDS.wallBrown2);
    setTile(buildings, W, W - 1, y, DUNG_GIDS.wallBrown2);
  }

  // 两个矿洞入口（黑色虚空 2×2）—— 在 buildings 层标记为不可走
  const mine1 = { x: 8, y: 12, w: 2, h: 2, label: 'Bug 矿洞·东', route: '/bug-mine?side=east', name: 'building_mine_east' };
  const mine2 = { x: 20, y: 12, w: 2, h: 2, label: 'Bug 矿洞·西', route: '/bug-mine?side=west', name: 'building_mine_west' };
  fillRect(buildings, W, mine1.x, mine1.y, mine1.w, mine1.h, DUNG_GIDS.voidDark);
  fillRect(buildings, W, mine2.x, mine2.y, mine2.w, mine2.h, DUNG_GIDS.voidDark);

  // 矿洞入口前的灯柱（pillar）
  setTile(decoration, W, mine1.x - 1, mine1.y + 1, DUNG_GIDS.pillar);
  setTile(decoration, W, mine1.x + 2, mine1.y + 1, DUNG_GIDS.pillar);
  setTile(decoration, W, mine2.x - 1, mine2.y + 1, DUNG_GIDS.pillar);
  setTile(decoration, W, mine2.x + 2, mine2.y + 1, DUNG_GIDS.pillar);

  // 散落矿石（蓝色）+ 宝箱
  const rng = mulberry32(0x30303);
  scatterDecoration(decoration, buildings, W, H, 20, [DUNG_GIDS.oreBlue, DUNG_GIDS.chest, DUNG_GIDS.pillar], rng);

  // objects ----------
  const objects = [
    makeExit('exit_north', 'north', W, H, 'town_hub'),
    makeBuilding(mine1.name, mine1.label, mine1.route, mine1.x, mine1.y, mine1.w, mine1.h),
    makeBuilding(mine2.name, mine2.label, mine2.route, mine2.x, mine2.y, mine2.w, mine2.h),
    makeNpc('npc_foreman_bug', 'foreman_bug', '矿洞工头 Bug', 14, 5, 'player_sara'),
    makeSpawn(15, 2),
  ];

  return buildMapJson({
    key: 'mine_south',
    label: 'Bug 矿洞',
    width: W,
    height: H,
    tileset: 'kenney_tiny_dungeon',
    ground,
    buildings,
    decoration,
    objects,
  });
}

// ============================================================
// campus_east (40×30)
// ============================================================
function generateCampusEast() {
  const W = 40;
  const H = 30;
  const ground = emptyData(W, H);
  const buildings = emptyData(W, H);
  const decoration = emptyData(W, H);
  resetObjectIds();

  fillAll(ground, TOWN_GIDS.grass);

  // 一条贯穿东西的中心步道（砂石路）
  for (let x = 2; x < W - 1; x += 1) {
    setTile(ground, W, x, 14, TOWN_GIDS.sandPath);
    setTile(ground, W, x, 15, TOWN_GIDS.sandPath);
  }

  // 北行三栋教学楼 5×4
  const library = { x: 8, y: 4, w: 5, h: 4, label: '图书馆', route: '/library', name: 'building_library' };
  const classroom = { x: 18, y: 4, w: 5, h: 4, label: '教室（题库）', route: '/problems', name: 'building_classroom' };
  const reviewHall = { x: 28, y: 4, w: 5, h: 4, label: '复盘大厅', route: '/review-hall', name: 'building_review_hall' };
  paintBuilding(buildings, W, library.x, library.y, library.w, library.h);
  paintBuilding(buildings, W, classroom.x, classroom.y, classroom.w, classroom.h);
  paintBuilding(buildings, W, reviewHall.x, reviewHall.y, reviewHall.w, reviewHall.h);

  // 南行工坊 4×3
  const workshop = { x: 18, y: 22, w: 4, h: 3, label: '资源工坊', route: '/workshop', name: 'building_workshop' };
  paintBuilding(buildings, W, workshop.x, workshop.y, workshop.w, workshop.h);

  // 工坊周围花坛
  setTile(decoration, W, workshop.x - 1, workshop.y, TOWN_GIDS.flowerPink);
  setTile(decoration, W, workshop.x + workshop.w, workshop.y, TOWN_GIDS.flowerPink);

  // 北侧围栏（图书馆群屋前小道）
  for (let x = 7; x < 34; x += 1) {
    setTile(decoration, W, x, 9, TOWN_GIDS.fenceWoodH);
  }

  // 边界树
  for (let x = 2; x < W - 2; x += 3) {
    setTile(decoration, W, x, 1, TOWN_GIDS.bushBig);
    setTile(decoration, W, x, H - 2, TOWN_GIDS.bushBig);
  }

  const rng = mulberry32(0x40404);
  scatterDecoration(decoration, buildings, W, H, 16, [TOWN_GIDS.bushSmall, TOWN_GIDS.flowerYellow], rng, {
    x: 2,
    y: 14,
    w: W - 4,
    h: 2, // 步道不撒花
  });

  // objects ----------
  const objects = [
    makeExit('exit_west', 'west', W, H, 'town_hub'),
    makeBuilding(library.name, library.label, library.route, library.x, library.y, library.w, library.h),
    makeBuilding(classroom.name, classroom.label, classroom.route, classroom.x, classroom.y, classroom.w, classroom.h),
    makeBuilding(reviewHall.name, reviewHall.label, reviewHall.route, reviewHall.x, reviewHall.y, reviewHall.w, reviewHall.h),
    makeBuilding(workshop.name, workshop.label, workshop.route, workshop.x, workshop.y, workshop.w, workshop.h),
    makeNpc('npc_librarian_doc', 'librarian_doc', '图书管理员 Doc', 10, 9),
    makeNpc('npc_coach_q', 'coach_q', '练习教练 Q', 20, 9),
    makeNpc('npc_sage_review', 'sage_review', '复盘贤者', 30, 9),
    makeSpawn(2, 15),
  ];

  return buildMapJson({
    key: 'campus_east',
    label: '校园',
    width: W,
    height: H,
    tileset: 'kenney_tiny_town',
    ground,
    buildings,
    decoration,
    objects,
  });
}

// ============================================================
// port_west (35×25)
// ============================================================
function generatePortWest() {
  const W = 35;
  const H = 25;
  const ground = emptyData(W, H);
  const buildings = emptyData(W, H);
  const decoration = emptyData(W, H);
  resetObjectIds();

  fillAll(ground, TOWN_GIDS.grass);

  // 南侧 5 行海水（rows 20-24）
  for (let y = 20; y < H; y += 1) {
    for (let x = 0; x < W; x += 1) {
      if (y === 20) {
        setTile(ground, W, x, y, TOWN_GIDS.waterEdgeTop);
      } else if (y === H - 1) {
        setTile(ground, W, x, y, TOWN_GIDS.waterDeep);
      } else {
        setTile(ground, W, x, y, TOWN_GIDS.water);
      }
    }
  }

  // 码头沙地（rows 18-19）
  for (let x = 8; x < 22; x += 1) {
    setTile(ground, W, x, 18, TOWN_GIDS.sandPath);
    setTile(ground, W, x, 19, TOWN_GIDS.sandPath);
  }

  // 灯塔（2×4，西北角）
  const lighthouse = { x: 3, y: 4, w: 2, h: 4, label: '灯塔', route: '/lighthouse', name: 'building_lighthouse' };
  paintBuilding(buildings, W, lighthouse.x, lighthouse.y, lighthouse.w, lighthouse.h);

  // 商船：在水边（rows 21-22），4×2，使用建筑外观近似船体
  const ship = { x: 13, y: 21, w: 4, h: 2, label: '商船', route: '/merchant-ship', name: 'building_merchant_ship' };
  paintBuilding(buildings, W, ship.x, ship.y, ship.w, ship.h);

  // 班级广场：5×5 围栏空地（坐标 24, 8）
  paintFenceRect(decoration, W, 22, 6, 6, 6);

  // 北边树木
  for (let x = 2; x < W - 2; x += 2) {
    setTile(decoration, W, x, 1, TOWN_GIDS.bushBig);
  }

  const rng = mulberry32(0x50505);
  scatterDecoration(decoration, buildings, W, H, 12, [TOWN_GIDS.bushSmall, TOWN_GIDS.flowerYellow, TOWN_GIDS.treeStump], rng, {
    x: 0,
    y: 18,
    w: W,
    h: H - 18, // 水里不撒
  });

  // objects ----------
  const objects = [
    makeExit('exit_east', 'east', W, H, 'town_hub'),
    makeBuilding(lighthouse.name, lighthouse.label, lighthouse.route, lighthouse.x, lighthouse.y, lighthouse.w, lighthouse.h),
    makeBuilding(ship.name, ship.label, ship.route, ship.x, ship.y, ship.w, ship.h),
    makeLandmark('class_plaza', '班级广场', 22, 6, 6, 6, 'plaza'),
    makeNpc('npc_merchant', 'merchant', '商人', 16, 19),
    makeNpc('npc_class_president', 'class_president', '班长', 25, 9),
    makeSpawn(33, 12),
  ];

  return buildMapJson({
    key: 'port_west',
    label: '码头港口',
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
        exits: ['farm_north', 'mine_south', 'campus_east', 'port_west'],
        spawn: { tileX: 25, tileY: 20 },
      },
      {
        key: 'farm_north',
        file: 'farm_north.json',
        label: '知识农场',
        width: 40,
        height: 40,
        bgm: 'bgm_town_day',
        tileset: 'kenney_tiny_town',
        exits: ['town_hub'],
        spawn: { tileX: 20, tileY: 37 },
      },
      {
        key: 'mine_south',
        file: 'mine_south.json',
        label: 'Bug 矿洞',
        width: 30,
        height: 30,
        bgm: 'bgm_bug_mine',
        tileset: 'kenney_tiny_dungeon',
        exits: ['town_hub'],
        spawn: { tileX: 15, tileY: 2 },
      },
      {
        key: 'campus_east',
        file: 'campus_east.json',
        label: '校园',
        width: 40,
        height: 30,
        bgm: 'bgm_quest',
        tileset: 'kenney_tiny_town',
        exits: ['town_hub'],
        spawn: { tileX: 2, tileY: 15 },
      },
      {
        key: 'port_west',
        file: 'port_west.json',
        label: '码头港口',
        width: 35,
        height: 25,
        bgm: 'bgm_town_eve',
        tileset: 'kenney_tiny_town',
        exits: ['town_hub'],
        spawn: { tileX: 33, tileY: 12 },
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

  return `# Pythontopia 地图（W2-MAPS 子代理输出）

Pythontopia M2 阶段的 5 张分块大地图，全部以 16×16 源 tile 渲染，Phaser 端按 2× 放大到 32×32 实际像素。

## 概览

| key | 中文名 | 尺寸（列×行） | tileset | BGM | 出口 | spawn |
| --- | --- | --- | --- | --- | --- | --- |
${rows}

> 出口列说明：当前地图边缘门洞会切换到列出的目标地图；M2-ROUTER 子代理读取 \`exits\` 解析跳转。

## 每张地图的细节

### town_hub.json（50×40，中央小镇）

- 4 边都有出口：north→farm_north / south→mine_south / east→campus_east / west→port_west
- 建筑（6 栋）：
  - \`building_mayor_hall\`（4×5）→ \`/profile-room\`
  - \`building_shop_west\`（4×4）→ \`/shop-west\`
  - \`building_shop_east\`（4×4）→ \`/shop-east\`
  - \`building_house_a / b / c\`（4×4 三栋住宅）
- 中央喷泉：(24, 19) 起 2×2 粉色花坛
- NPC：\`mayor_py\`, \`librarian_doc\`, \`coach_q\`, \`sage_review\`, \`peasant_a\`, \`peasant_b\`

### farm_north.json（40×40，知识农场）

- 出口：south→town_hub
- 7 个知识田（5×5 一块）：basics / variable / data_types / control_flow / loop / function / class
- 建筑：\`building_barn\`（谷仓 5×4）、\`building_chicken_coop\`（鸡舍 4×3）
- 装饰：水井 (32, 25)、田间小路、围栏
- NPC：\`farmer_kc\`

### mine_south.json（30×30，Bug 矿洞）

- 出口：north→town_hub
- 使用 **kenney_tiny_dungeon** tileset（其它地图均使用 kenney_tiny_town）
- 两个矿洞入口（2×2 黑色 void）：东侧 (8, 12)、西侧 (20, 12)，分别打开 \`/bug-mine?side=east|west\`
- 外围一圈深色墙壁，矿石与宝箱散布在地板上
- NPC：\`foreman_bug\`

### campus_east.json（40×30，校园）

- 出口：west→town_hub
- 北行 3 栋教学楼：图书馆（→ \`/library\`）、教室（题库，→ \`/problems\`）、复盘大厅（→ \`/review-hall\`）
- 南行 1 栋工坊（→ \`/workshop\`）
- 中央东西向砂石步道
- NPC：\`librarian_doc\`, \`coach_q\`, \`sage_review\`

### port_west.json（35×25，码头港口）

- 出口：east→town_hub
- 南侧 5 行海水（rows 20–24），中间 12 格沙地码头
- 灯塔（2×4，西北角）+ 商船（4×2，水边）
- 班级广场（22, 6 起 6×6 围栏空地）
- NPC：\`merchant\`, \`class_president\`

## 文件结构

\`\`\`
frontend/public/assets/maps/
├── index.json        # 5 张地图的 metadata 索引（M2-ROUTER 入口）
├── README.md         # 本文件
├── town_hub.json     # 50×40
├── farm_north.json   # 40×40
├── mine_south.json   # 30×30（dungeon tileset）
├── campus_east.json  # 40×30
└── port_west.json    # 35×25
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
- mine_south 暂未铺设矿洞内部副本——副本场景由独立 Phaser scene 处理，本 JSON 仅负责矿洞外景与入口判定。
- 字段 \`spawn\` 在 index.json 与 map.objects 内重复出现：runtime 默认读 index.json，地图内的 \`spawn\` 对象作为权威覆盖。
`;
}

// ---------- 主入口 ----------

async function main() {
  await fs.mkdir(MAPS_DIR, { recursive: true });

  const generators = [
    { name: 'town_hub.json', build: generateTownHub },
    { name: 'farm_north.json', build: generateFarmNorth },
    { name: 'mine_south.json', build: generateMineSouth },
    { name: 'campus_east.json', build: generateCampusEast },
    { name: 'port_west.json', build: generatePortWest },
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
