#!/usr/bin/env node
/**
 * generate-maps.mjs — Pythontopia R-14 地图生成脚本
 *
 * @file 程序化生成 Phaser/Tiled 兼容的 JSON tilemap，覆盖：
 *   - town_hub   (50×40) 中央小镇
 *   - mine_south (30×30) Bug 矿洞支线（kenney_tiny_dungeon tileset）
 *
 * @summary
 *   - 生成 2 个 .json + index.json + README.md（外围图生成器已移除）。
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
  sandPath: 13, // frame 12 = 砂石路面左上块，用于小镇主路 / 分岔路
  // 4×N 建筑（沿用 M1-B BUILDING_FRAMES 约定，3 列对应房屋宽度归一化）
  bld: [
    [14, 15, 16], // 顶/屋顶：frame 13 / 14 / 15
    [17, 18, 19], // 上层墙：frame 16 / 17 / 18
    [20, 21, 22], // 中层墙：frame 19 / 20 / 21
    [23, 24, 25], // 底层墙（中间是门）：frame 22 / 23 / 24
  ],
  grassAlt: 2, // frame 1 = 草地变种
  grassTall: 4, // frame 3 = 高草
  bushSmall: 5, // frame 4 = 小绿色灌木
  bushBig: 17, // frame 16 = 较大绿色灌木（屋边树）
  rockSmall: 6, // frame 5 = 小石头
  rockMedium: 7, // frame 6 = 中石头
  logPile: 8, // frame 7 = 木柴堆
  mushroom: 9, // frame 8 = 蘑菇
  flowerPink: 26, // frame 25 = 纯粉色（用作花坛 / 喷泉花池）
  flowerYellow: 22, // frame 21 = 黄+棕（黄色花坛）
  flowerBlue: 27, // frame 26 = 蓝色花
  barrel: 28, // frame 27 = 木桶
  crate: 29, // frame 28 = 木箱
  well: 30, // frame 29 = 水井
  fenceWoodH: 69, // frame 68 = 木质栅栏水平段
  fenceWoodV: 81, // frame 80 = 木质栅栏竖向段
  fencePost: 70, // frame 69 = 栅栏柱
  water: 50, // frame 49 = 纯蓝水面
  waterEdgeTop: 49, // frame 48 = 水边沿（上沿）
  waterEdgeBottom: 51, // frame 50 = 水边沿（下沿）
  waterDeep: 62, // frame 61 = 深蓝水（中央）
  treeStump: 85, // frame 84 = 棕色 + 暗色（树桩 / 木箱外观）
  treePine: 86, // frame 85 = 松树
  signPost: 87, // frame 86 = 路标
  lamp: 88, // frame 87 = 路灯
  benchH: 89, // frame 88 = 横向长椅
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
  // 中央广场铺一层略带花纹的草地（24,18 ~ 26,22，5×5）
  fillRect(ground, W, 23, 18, 5, 5, TOWN_GIDS.grassFlower);

  // 镇长大厅：北中央，4×5（叙事上对应「东校园老镇厅」，承担画像 / 主线推进）
  const mayorHall = { x: 23, y: 4, w: 4, h: 5, label: '镇长大厅', route: '/profile-room', name: 'building_mayor_hall' };
  paintBuilding(buildings, W, mayorHall.x, mayorHall.y, mayorHall.w, mayorHall.h);

  // R-13 §-1 后续：在镇长大厅东西两侧加 2 栋纺织主题民居（不命名校名），
  // 叙事上呼应「松江纺织文化」+「东华纺织学院」一脉相承的小镇背景；
  // 实现：复用 paintBuilding 普通房屋瓦面，标记 building_textile_pavilion_*。
  const textileWest = { x: 16, y: 5, w: 4, h: 4, label: '纺织展馆·西', route: '/house-west', name: 'building_textile_pavilion_w' };
  const textileEast = { x: 30, y: 5, w: 4, h: 4, label: '纺织展馆·东', route: '/house-east', name: 'building_textile_pavilion_e' };
  paintBuilding(buildings, W, textileWest.x, textileWest.y, textileWest.w, textileWest.h);
  paintBuilding(buildings, W, textileEast.x, textileEast.y, textileEast.w, textileEast.h);

  // 南面 3 栋民居（peasant 房子）
  const housesSouth = [
    { x: 8, y: 28, w: 4, h: 4, label: '民居·东', route: '/house-east', name: 'building_house_a' },
    { x: 22, y: 28, w: 4, h: 4, label: '民居·中', route: '/house-mid', name: 'building_house_b' },
    { x: 36, y: 28, w: 4, h: 4, label: '民居·西', route: '/house-west', name: 'building_house_c' },
  ];
  housesSouth.forEach((h) => paintBuilding(buildings, W, h.x, h.y, h.w, h.h));

  // R-13 优化：货币/商店系统移除 —— 删除 东西商店（杂货铺/工具铺）。

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

  // 高密度装饰：多种素材轮换，town_hub 更精致
  const rng = mulberry32(0x10101);

  // 地面变种：随机替换部分草地为花草/高草，增加地面层次感
  for (let i = 0; i < ground.length; i += 1) {
    if (ground[i] === TOWN_GIDS.grass && rng() < 0.12) {
      ground[i] = rng() < 0.5 ? TOWN_GIDS.grassFlower : TOWN_GIDS.grassAlt;
    }
  }

  scatterDecoration(
    decoration,
    buildings,
    W,
    H,
    380,
    [
      TOWN_GIDS.bushSmall,
      TOWN_GIDS.bushSmall,
      TOWN_GIDS.bushBig,
      TOWN_GIDS.flowerYellow,
      TOWN_GIDS.flowerPink,
      TOWN_GIDS.flowerBlue,
      TOWN_GIDS.treeStump,
      TOWN_GIDS.grassFlower,
      TOWN_GIDS.grassTall,
      TOWN_GIDS.rockSmall,
      TOWN_GIDS.rockMedium,
      TOWN_GIDS.mushroom,
      TOWN_GIDS.barrel,
      TOWN_GIDS.treePine,
      TOWN_GIDS.lamp,
    ],
    rng,
    { x: 22, y: 17, w: 7, h: 7 },
  );

  // R-13 §-1 后续：在喷泉广场四角撒 4 个对称粉花柱（2×1），让喷泉中心更醒目。
  // 喷泉位置 (24,19) 2×2，四角偏 4 格放粉花，形成菱形花阵。
  [
    [22, 17], [27, 17], [22, 22], [27, 22],
  ].forEach(([fx, fy]) => {
    setTile(decoration, W, fx, fy, TOWN_GIDS.flowerPink);
    setTile(decoration, W, fx, fy + 1, TOWN_GIDS.flowerYellow);
  });

  // R-13 §-1 后续：南北主轴贯穿砂石路（喷泉到南门），让玩家有"主街"叙事。
  for (let y = 21; y < H - 1; y += 1) {
    setTile(ground, W, 24, y, TOWN_GIDS.sandPath);
    setTile(ground, W, 25, y, TOWN_GIDS.sandPath);
  }
  // 东西副轴砂石路（喷泉到东、西门）
  for (let x = 1; x < W - 1; x += 1) {
    if (x === 24 || x === 25) continue; // 跳过主轴交叉点
    setTile(ground, W, x, 19, TOWN_GIDS.sandPath);
    setTile(ground, W, x, 20, TOWN_GIDS.sandPath);
  }
  // 在主轴两侧每 6 格摆一个 treeStump（充当路灯桩占位，等爬到正式 streetlamp 替换）
  for (let y = 22; y < H - 2; y += 6) {
    setTile(decoration, W, 23, y, TOWN_GIDS.treeStump);
    setTile(decoration, W, 26, y, TOWN_GIDS.treeStump);
  }
  // Space 跳跃教学：出生点右侧放一根低木桩，玩家可面朝右跨过。
  setTile(decoration, W, 26, 20, TOWN_GIDS.treeStump);

  // 专用低栅栏沿主街围出小路：中间 2 格保持通行，两侧是可跳过的低障碍。
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
  paintBuilding(buildings, W, library.x, library.y, library.w, library.h);
  paintBuilding(buildings, W, reviewHall.x, reviewHall.y, reviewHall.w, reviewHall.h);
  paintBuilding(buildings, W, workshop.x, workshop.y, workshop.w, workshop.h);
  // mine_south 入口
  const mine = { x: 22, y: 34, w: 3, h: 2, label: 'Bug 矿洞', route: '/bug-mine', name: 'building_mine' };
  paintBuilding(buildings, W, mine.x, mine.y, mine.w, mine.h);
  // 竞技场
  const arena = { x: 36, y: 34, w: 4, h: 3, label: '闯关竞技场', route: '/arena/1', name: 'building_arena' };
  paintBuilding(buildings, W, arena.x, arena.y, arena.w, arena.h);
  // 训练场
  const training = { x: 8, y: 34, w: 4, h: 3, label: '训练场', route: '/training', name: 'building_training' };
  paintBuilding(buildings, W, training.x, training.y, training.w, training.h);

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
    makeNpc('npc_mayor_py', 'mayor_py', '镇长 Py', 25, 10, 'npc_mayor'),
    makeNpc('npc_librarian_doc', 'librarian_doc', '馆长 Doc', 11, 12, 'npc_librarian'),
    makeNpc('npc_professor', 'professor', '教授', 14, 12, 'npc_professor'),
    makeNpc('npc_coach_q', 'coach_q', '教务 Q', 10, 33, 'npc_coach'),
    makeNpc('npc_sage_review', 'sage_review', '复盘师 Sage', 37, 12, 'npc_sage'),
    makeNpc('npc_student_a', 'student_a', '学生 A', 22, 22, 'npc_student'),
    makeNpc('npc_workshop_master', 'workshop_master', '工坊师傅', 42, 28, 'npc_workshop'),
    makeNpc('npc_arena_guard', 'arena_guard', '竞技场守卫', 37, 37, 'npc_guard'),
    makeNpc('npc_mine_keeper', 'mine_keeper', '守矿人', 21, 33, 'npc_miner'),
    makeNpc('npc_foreman_bug', 'foreman_bug', '矿头 Bug', 25, 33, 'npc_guard'),
    makeNpc('npc_captain_def', 'captain_def', '船长 Def', 5, 24, 'npc_sailor'),
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
  // R-13 §-1 后续：矿洞密度 60→160，让"二进制矿洞"看起来真有矿
  scatterDecoration(decoration, buildings, W, H, 160, [
    DUNG_GIDS.oreBlue,
    DUNG_GIDS.chest,
    DUNG_GIDS.pillar,
    DUNG_GIDS.wallStone,
    DUNG_GIDS.wallStone2,
    DUNG_GIDS.floorAlt,
    DUNG_GIDS.floorAlt2,
  ], rng);
  // 矿洞碎石：只允许跳过，不允许直接穿越。
  setTile(decoration, W, 15, 4, DUNG_GIDS.wallStone);

  // objects ----------
  // R-13 §-1 续：矿洞叙事改为「二进制矿洞 · 织丝走廊」：呼应纺织文化里
  // "丝线在织机上像 0/1 一样穿梭"的视觉双关；不出现校名。
  // 蓝色 oreBlue 同时象征 "蓝染" 工艺；pillar 充当织机立柱。
  const objects = [
    makeExit('exit_north', 'north', W, H, 'town_hub'),
    makeBuilding(mine1.name, mine1.label, mine1.route, mine1.x, mine1.y, mine1.w, mine1.h),
    makeBuilding(mine2.name, mine2.label, mine2.route, mine2.x, mine2.y, mine2.w, mine2.h),
    makeLandmark('loom_pillar', '织机立柱', 15, 8, 1, 4, 'landmark'),
    makeLowObstacle('low_mine_rubble', '可跨越碎石', 15, 4, 1, 1, 'rubble'),
    makeNpc('npc_foreman_bug', 'foreman_bug', '矿洞工头 Bug', 14, 5, 'player_sara'),
    makeSpawn(15, 2),
  ];

  return buildMapJson({
    key: 'mine_south',
    label: 'Bug 矿洞 · 织丝走廊',
    width: W,
    height: H,
    tileset: 'kenney_tiny_dungeon',
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
      {
        key: 'mine_south',
        file: 'mine_south.json',
        label: 'Bug 矿洞 · 织丝走廊',
        width: 30,
        height: 30,
        bgm: 'bgm_bug_mine',
        tileset: 'kenney_tiny_dungeon',
        exits: ['town_hub'],
        spawn: { tileX: 15, tileY: 2 },
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

R-14 方案 A：仅生成 **town_hub**（中央小镇）与 **mine_south**（Bug 矿洞支线）。外围图已从主线移除。

## 概览

| key | 中文名 | 尺寸（列×行） | tileset | BGM | 出口 | spawn |
| --- | --- | --- | --- | --- | --- | --- |
${rows}

## town_hub.json（50×40）

- 出口：无（R-14 单中央 hub，外围图已移除；Bug 矿洞经 building → /bug-mine 路由进入）
- 学习建筑：图书馆 / 工坊 / 训练场 / 竞技场 / 复盘大厅 / Bug 矿洞入口等
- 12 主线 NPC 贴近建筑入口排布（见 \`scripts/generate-maps.mjs\` 内 \`makeNpc\` 列表）

## mine_south.json（30×30）

- 出口：north→town_hub
- tileset：**kenney_tiny_dungeon**
- 矿洞入口 + \`foreman_bug\` NPC

## 文件结构

\`\`\`
frontend/public/assets/maps/
├── index.json
├── README.md
├── town_hub.json
└── mine_south.json
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
- mine_south 暂未铺设矿洞内部副本——副本场景由独立 Phaser scene 处理，本 JSON 仅负责矿洞外景与入口判定。
- 字段 \`spawn\` 在 index.json 与 map.objects 内重复出现：runtime 默认读 index.json，地图内的 \`spawn\` 对象作为权威覆盖。
`;
}

// ---------- 主入口 ----------

async function main() {
  await fs.mkdir(MAPS_DIR, { recursive: true });

  // R-14 方案A：只生成中央小镇 + Bug 矿洞支线；farm_north / campus_east / port_west 已废弃。
  const generators = [
    { name: 'town_hub.json', build: generateTownHub },
    { name: 'mine_south.json', build: generateMineSouth },
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
