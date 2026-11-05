#!/usr/bin/env node
/**
 * generate-maps.mjs — Pythontopia R-14 地图生成脚本
 *
 * @file 程序化生成 Phaser/Tiled 兼容的 JSON tilemap，覆盖：
 *   - town_hub   (50×40) 中央小镇
 *
 * @summary
 *   - 生成 1 个 .json + index.json + README.md（外围图与 矿洞景观风景图均已移除）。
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

// ---------- Super Retro World 切片图集（取代 kenney tiny-town 帧号表）----------
// 帧号不再凭印象猜：整张表由 scripts/build_srw_assets.py 从 Godot TileSet 的
// terrains_peering_bits 导出，与运行时 town_tiles.png 一一对应。
// 内联而非运行时读 JSON 的原因见 build_srw_assets.sync_generator_atlas。
// BEGIN SRW_ATLAS（由 scripts/build_srw_assets.py 从 town_tiles.json 回写，勿手改）
const SRW_ATLAS = {
  image: '../pixel/tilesets/super-retro-world/town_tiles.png',
  columns: 32,
  tileCount: 220,
  tileSize: 16,
  single: { grass: 0, grass_tall_a: 1, grass_tall_b: 2, tuft_a: 3, tuft_b: 4, tuft_c: 5, tuft_d: 6, tuft_e: 7, tuft_f: 8, flower_a: 9, flower_b: 10, flower_c: 11, flower_d: 12, flower_e: 13, bush_a: 14, bush_b: 15, bush_c: 16, bush_d: 17, bush_e: 18, blade_a: 19, blade_b: 20, blade_c: 21, mound_tl: 22, mound_tc: 23, mound_tr: 24, mound_ml: 25, mound_mc: 26, mound_mr: 27, mound_bl: 28, mound_bc: 29, mound_br: 30, cave_mouth: 31 },
  blob: {
    road: { 0: 32, 1: 33, 4: 34, 5: 35, 7: 36, 16: 37, 17: 38, 20: 39, 21: 40, 23: 41, 28: 42, 29: 43, 31: 44, 64: 45, 65: 46, 68: 47, 69: 48, 71: 49, 80: 50, 81: 51, 84: 52, 85: 53, 87: 54, 92: 55, 93: 56, 95: 57, 112: 58, 113: 59, 116: 60, 117: 61, 119: 62, 124: 63, 125: 64, 127: 65, 193: 66, 197: 67, 199: 68, 209: 69, 213: 70, 215: 71, 221: 72, 223: 73, 241: 74, 245: 75, 247: 76, 253: 77, 255: 78 },
    dirt: { 0: 79, 1: 80, 4: 81, 5: 82, 7: 83, 16: 84, 17: 85, 20: 86, 21: 87, 23: 88, 28: 89, 29: 90, 31: 91, 64: 92, 65: 93, 68: 94, 69: 95, 71: 96, 80: 97, 81: 98, 84: 99, 85: 100, 87: 101, 92: 102, 93: 103, 95: 104, 112: 105, 113: 106, 116: 107, 117: 108, 119: 109, 124: 110, 125: 111, 127: 112, 193: 113, 197: 114, 199: 115, 209: 116, 213: 117, 215: 118, 221: 119, 223: 120, 241: 121, 245: 122, 247: 123, 253: 124, 255: 125 },
    fence: { 0: 126, 1: 127, 4: 128, 5: 129, 7: 130, 16: 131, 17: 132, 20: 133, 21: 134, 23: 135, 28: 136, 29: 137, 31: 138, 64: 139, 65: 140, 68: 141, 69: 142, 71: 143, 80: 144, 81: 145, 84: 146, 85: 147, 87: 148, 92: 149, 93: 150, 95: 151, 112: 152, 113: 153, 116: 154, 117: 155, 119: 156, 124: 157, 125: 158, 127: 159, 193: 160, 197: 161, 199: 162, 209: 163, 213: 164, 215: 165, 221: 166, 223: 167, 241: 168, 245: 169, 247: 170, 253: 171, 255: 172 },
    water: { 0: 173, 1: 174, 4: 175, 5: 176, 7: 177, 16: 178, 17: 179, 20: 180, 21: 181, 23: 182, 28: 183, 29: 184, 31: 185, 64: 186, 65: 187, 68: 188, 69: 189, 71: 190, 80: 191, 81: 192, 84: 193, 85: 194, 87: 195, 92: 196, 93: 197, 95: 198, 112: 199, 113: 200, 116: 201, 117: 202, 119: 203, 124: 204, 125: 205, 127: 206, 193: 207, 197: 208, 199: 209, 209: 210, 213: 211, 215: 212, 221: 213, 223: 214, 241: 215, 245: 216, 247: 217, 253: 218, 255: 219 },
  },
  props: { barrel: 5, chara: 32, crate: 8, house: 12, lamp: 5, pot: 27, potted_plant: 15, rock: 46, torch: 18, tree: 15 },
  charSheet: {'size': [48, 80], 'frame': [16, 20], 'framesPerDir': 3, 'dirRowOrder': ['DOWN', 'LEFT', 'RIGHT', 'UP'], 'idleFrame': 0},
};
// END SRW_ATLAS

/** 图集索引 -> Tiled gid（firstgid = 1，0 = 空 tile）。 */
const gid = (index) => index + 1;
const SRW_SINGLE = SRW_ATLAS.single;
const SRW_BLOB = SRW_ATLAS.blob;

// ---------- 通用工具 ----------
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

// ---------- 布局引擎：材质网格 + 圆盘笔刷 + 47-blob autotile ----------
//
// 为什么不用矩形 fillRect 铺路：直上直下的方块路会让小镇读起来像"空地上一个加号"。
// 这里先用圆盘笔刷沿折线拓出材质网格，交给 47-blob autotile 生成边缘，最后做一次
// 形态学清理 + 连通校验。三步都各自解决一个真实踩过的坑：
//   canonicalMask —— 笔刷抖动会造出 47 表里没有的掩码，查不到就会回退到内部块，
//                    在路/水与草地之间留下硬切边（线上看到"半圈有岸线半圈硬切"）。
//   cleanGrid     —— 笔刷留的 1 格草舌会让两侧都长毛边，看起来像路上长草。
//   connectAll    —— 门前坪/支路容易成为孤立路面块，玩家走不到 NPC。

const MAT = { GRASS: 0, ROAD: 1, WATER: 2, DIRT: 3 };
const SIDES = ['top_side', 'top_right_corner', 'right_side', 'bottom_right_corner',
               'bottom_side', 'bottom_left_corner', 'left_side', 'top_left_corner'];
const NEIGHBOR_DELTAS = [[0, -1], [1, -1], [1, 0], [1, 1], [0, 1], [-1, 1], [-1, 0], [-1, -1]];

function makeGrid(w, h) {
  return Array.from({ length: h }, () => new Array(w).fill(MAT.GRASS));
}

function gridSet(grid, x, y, mat) {
  if (x < 0 || y < 0 || y >= grid.length || x >= grid[0].length) return;
  grid[y][x] = mat;
}

function gridFill(grid, x0, y0, x1, y1, mat) {
  for (let y = Math.max(0, y0); y <= Math.min(grid.length - 1, y1); y += 1) {
    for (let x = Math.max(0, x0); x <= Math.min(grid[0].length - 1, x1); x += 1) grid[y][x] = mat;
  }
}

/** 圆盘笔刷：坐标可为小数，jitter 让边缘不规整。 */
function gridStamp(grid, cx, cy, r, mat, jitter = 0, seed = 0) {
  for (let y = Math.max(0, Math.floor(cy - r - 1)); y <= Math.min(grid.length - 1, Math.ceil(cy + r + 1)); y += 1) {
    for (let x = Math.max(0, Math.floor(cx - r - 1)); x <= Math.min(grid[0].length - 1, Math.ceil(cx + r + 1)); x += 1) {
      let rr = r;
      if (jitter) rr += Math.sin(x * 0.9 + y * 1.7 + seed) * jitter;
      if ((x - cx) ** 2 + (y - cy) ** 2 <= rr * rr) grid[y][x] = mat;
    }
  }
}

/** 沿折线拓一条路：宽度起伏、转角自动圆化、交叉口自然加宽。 */
function gridStampPath(grid, pts, r, mat = MAT.ROAD, seed = 0, jitter = 0.22) {
  for (let i = 0; i < pts.length - 1; i += 1) {
    const [x0, y0] = pts[i];
    const [x1, y1] = pts[i + 1];
    const dist = Math.hypot(x1 - x0, y1 - y0);
    const steps = Math.max(1, Math.round(dist * 4));
    for (let s = 0; s <= steps; s += 1) {
      const t = s / steps;
      const rr = r + Math.sin((t * dist * 1.3) + i + seed) * jitter;
      gridStamp(grid, x0 + ((x1 - x0) * t), y0 + ((y1 - y0) * t), rr, mat, 0, seed + i);
    }
  }
}

/** 有机团块（广场 / 湖）：椭圆 + 低频扰动，避免规则台阶感。 */
function gridStampBlob(grid, cx, cy, rx, ry, mat = MAT.ROAD, seed = 0, wob = 0.35) {
  for (let y = Math.max(0, Math.floor(cy - ry - 2)); y <= Math.min(grid.length - 1, Math.ceil(cy + ry + 2)); y += 1) {
    for (let x = Math.max(0, Math.floor(cx - rx - 2)); x <= Math.min(grid[0].length - 1, Math.ceil(cx + rx + 2)); x += 1) {
      const ang = Math.atan2(y - cy, x - cx);
      const k = 1 + (Math.sin((ang * 3) + seed) * wob * 0.5)
              + (Math.sin((ang * 5) - seed * 1.7) * wob * 0.35);
      if (((x - cx) / (rx * k)) ** 2 + ((y - cy) / (ry * k)) ** 2 <= 1) grid[y][x] = mat;
    }
  }
}

function gridMaskOf(grid, mat, x, y) {
  let m = 0;
  NEIGHBOR_DELTAS.forEach(([dx, dy], bit) => {
    const nx = x + dx;
    const ny = y + dy;
    if (nx >= 0 && ny >= 0 && ny < grid.length && nx < grid[0].length && grid[ny][nx] === mat) {
      m |= 1 << bit;
    }
  });
  return m;
}

/**
 * 掩码规范化：角落位只在其两侧侧边位都亮时才有意义（Godot blob 约定）。
 * 不规范化就会查不到块 -> 回退内部块 -> 路/水边出现硬切。
 */
function canonicalMask(m) {
  const TOP = 0, TR = 1, RIGHT = 2, BR = 3, BOTTOM = 4, BL = 5, LEFT = 6, TL = 7;
  [[TR, TOP, RIGHT], [BR, RIGHT, BOTTOM], [BL, BOTTOM, LEFT], [TL, LEFT, TOP]].forEach(
    ([corner, a, b]) => {
      if (!((m & (1 << a)) && (m & (1 << b)))) m &= ~(1 << corner);
    },
  );
  return m;
}

/** 查 47-blob 表；仍不命中就按 Hamming 距离取最近的合法掩码，绝不回退内部块。 */
function lookupBlob(table, m) {
  const key = canonicalMask(m);
  if (table[key] !== undefined) return table[key];
  let best = null;
  Object.keys(table).forEach((k) => {
    const d = (Number(k) ^ key).toString(2).split('1').length - 1;
    if (!best || d < best[0]) best = [d, Number(k)];
  });
  return best ? table[best[1]] : table[0];
}

/** 形态学开/闭：吞掉 1 格草洞与毛刺。顺序必须早于 connectAll。 */
function cleanGrid(grid, mats = [MAT.ROAD, MAT.WATER, MAT.DIRT]) {
  const w = grid[0].length;
  const h = grid.length;
  for (let iter = 0; iter < 3; iter += 1) {
    let changed = false;
    mats.forEach((mat) => {
      const nb = grid.map((row, y) => row.map((_, x) => {
        let n = 0;
        NEIGHBOR_DELTAS.forEach(([dx, dy]) => {
          if (!dx && !dy) return;
          const nx = x + dx;
          const ny = y + dy;
          if (nx >= 0 && ny >= 0 && ny < h && nx < w && grid[ny][nx] === mat) n += 1;
        });
        return n;
      }));
      for (let y = 0; y < h; y += 1) {
        for (let x = 0; x < w; x += 1) {
          const orth = [[1, 0], [-1, 0], [0, 1], [0, -1]].filter(
            ([dx, dy]) => grid[y + dy]?.[x + dx] === mat,
          ).length;
          const c = grid[y][x];
          if (c !== mat && (nb[y][x] >= 6 || orth >= 3)) {
            grid[y][x] = mat;
            changed = true;
          } else if (c === mat && nb[y][x] <= 1) {
            grid[y][x] = MAT.GRASS;
            changed = true;
          }
        }
      }
    });
    if (!changed) break;
  }
}

/** 洪泛填充去掉湖里的"孤岛"（从图边可达的草地之外全部并入水面）。 */
function fillLakes(grid) {
  const w = grid[0].length;
  const h = grid.length;
  const seen = grid.map((row) => row.map(() => false));
  const stack = [];
  for (let x = 0; x < w; x += 1) {
    stack.push([x, 0], [x, h - 1]);
  }
  for (let y = 0; y < h; y += 1) {
    stack.push([0, y], [w - 1, y]);
  }
  while (stack.length) {
    const [x, y] = stack.pop();
    if (x < 0 || y < 0 || y >= h || x >= w || seen[y][x]) continue;
    seen[y][x] = true;
    if (grid[y][x] === MAT.WATER) continue;
    [[1, 0], [-1, 0], [0, 1], [0, -1]].forEach(([dx, dy]) => stack.push([x + dx, y + dy]));
  }
  for (let y = 0; y < h; y += 1) {
    for (let x = 0; x < w; x += 1) {
      if (grid[y][x] !== MAT.WATER && !seen[y][x]) grid[y][x] = MAT.WATER;
    }
  }
}

function componentsOf(grid, mat) {
  const w = grid[0].length;
  const h = grid.length;
  const seen = new Set();
  const out = [];
  for (let y = 0; y < h; y += 1) {
    for (let x = 0; x < w; x += 1) {
      if (grid[y][x] !== mat || seen.has(y * w + x)) continue;
      const stack = [[x, y]];
      const comp = [];
      seen.add(y * w + x);
      while (stack.length) {
        const [cx, cy] = stack.pop();
        comp.push([cx, cy]);
        NEIGHBOR_DELTAS.forEach(([dx, dy]) => {
          const nx = cx + dx;
          const ny = cy + dy;
          const k = ny * w + nx;
          if (nx < 0 || ny < 0 || ny >= h || nx >= w || seen.has(k)) return;
          if (grid[ny][nx] === mat) {
            seen.add(k);
            stack.push([nx, ny]);
          }
        });
      }
      out.push(comp);
    }
  }
  return out;
}

/** 把所有孤立块接回最大块（多源 BFS 最短桥接）。返回补接格数。 */
function connectAll(grid, mat = MAT.ROAD, avoid = [MAT.WATER]) {
  const comps = componentsOf(grid, mat).sort((a, b) => b.length - a.length);
  if (comps.length <= 1) return 0;
  const w = grid[0].length;
  const h = grid.length;
  const main = new Set(comps[0].map(([x, y]) => (y * w) + x));
  let bridges = 0;
  comps.slice(1).forEach((comp) => {
    const target = new Set(comp.map(([x, y]) => (y * w) + x));
    const prev = new Map();
    const queue = [...comps[0]];
    queue.forEach(([x, y]) => prev.set((y * w) + x, null));
    let hit = null;
    while (queue.length && !hit) {
      const [x, y] = queue.shift();
      for (let dy = -1; dy <= 1 && !hit; dy += 1) {
        for (let dx = -1; dx <= 1 && !hit; dx += 1) {
          const nx = x + dx;
          const ny = y + dy;
          if (nx < 0 || ny < 0 || ny >= h || nx >= w) continue;
          const k = ny * w + nx;
          if (prev.has(k)) continue;
          prev.set(k, [x, y]);
          if (target.has(k)) {
            hit = k;
            break;
          }
          const cell = grid[ny][nx];
          if (avoid.includes(cell) || cell === mat) continue;
          queue.push([nx, ny]);
        }
      }
    }
    if (hit === null) return;
    let cur = hit;
    while (cur !== null && !main.has(cur)) {
      const [x, y] = [cur % w, Math.floor(cur / w)];
      grid[y][x] = mat;
      main.add(cur);
      bridges += 1;
      const p = prev.get(cur);
      cur = p ? ((p[1] * w) + p[0]) : null;
    }
  });
  return bridges;
}

/** 门前坪：实心矩形（不能用圆盘笔刷——cleanGrid 会把小圆块当毛刺删掉）。 */
function gridDoorApron(grid, cx, by, wTiles = 3, dTiles = 2, mat = MAT.ROAD) {
  gridFill(grid, cx - Math.floor(wTiles / 2), by + 1,
    cx - Math.floor(wTiles / 2) + wTiles - 1, by + dTiles, mat);
}

/** 校验：建筑占地互不重叠且至少隔 minGap 格。返回违规对。 */
function footprintsGapOk(boxes, minGap = 2) {
  const bad = [];
  for (let i = 0; i < boxes.length; i += 1) {
    for (let j = i + 1; j < boxes.length; j += 1) {
      const [ax0, ay0, ax1, ay1] = boxes[i];
      const [bx0, by0, bx1, by1] = boxes[j];
      const dx = Math.max(bx0 - ax1 - 1, ax0 - bx1 - 1, 0);
      const dy = Math.max(by0 - ay1 - 1, ay0 - by1 - 1, 0);
      const gap = Math.max(dx, dy);
      if (gap < minGap) bad.push([i, j, gap]);
    }
  }
  return bad;
}

/**
 * 把材质网格烘焙成 gid 数组：单格用 SRW_ATLAS.single，路/水/土/栅栏走 47-blob 查表。
 * blobTables 可追加额外表（如 fence），按材质 -> 表名映射取。
 */
function bakeGrid(grid, blobFor) {
  const w = grid[0].length;
  const h = grid.length;
  const data = emptyData(w, h);
  for (let y = 0; y < h; y += 1) {
    for (let x = 0; x < w; x += 1) {
      const mat = grid[y][x];
      const table = blobFor(mat);
      data[(y * w) + x] = table === undefined
        ? gid(SRW_SINGLE.grass)
        : gid(lookupBlob(table, gridMaskOf(grid, mat, x, y)));
    }
  }
  return data;
}


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


// ---------- 通用 Tilemap JSON 构造 ----------

/**
 * 把 layer + objects spec 包成完整 Phaser tilemap JSON
 * @param {object} spec
 * @param {string} spec.key - 地图 key (town_hub)
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
    nextlayerid: 6,
    nextobjectid: spec.objects.length + 1,
    properties: [
      { name: 'mapKey', type: 'string', value: spec.key },
      { name: 'label', type: 'string', value: spec.label ?? spec.key },
    ],
    tilesets: [
      {
        firstgid: 1,
        name: tilesetMeta.name,
        tilewidth: tilesetMeta.tileWidth,
        tileheight: tilesetMeta.tileHeight,
        tilecount: tilesetMeta.tilecount,
        columns: tilesetMeta.columns,
        image: tilesetMeta.image,
        imagewidth: tilesetMeta.imagewidth,
        imageheight: tilesetMeta.imageheight,
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
        // 专用碰撞层：只被 runtime 的 buildCollisions() 读取，本身不渲染。
        // 建筑改整栋精灵、树/石改 prop 对象之后，碰撞不再能从 buildings 瓦片层推断。
        id: 5,
        name: 'collision',
        type: 'tilelayer',
        x: 0,
        y: 0,
        width: spec.width,
        height: spec.height,
        opacity: 1,
        visible: false,
        data: spec.collision ?? new Array(spec.width * spec.height).fill(0),
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

const KENNEY_META = {
    tilecount: 132,
    columns: 12,
    tileWidth: TILE,
    tileHeight: TILE,
    imagewidth: 192,
    imageheight: 176,
  };

const TILESET_META = {
  srw_town: {
    name: 'srw_town',
    image: SRW_ATLAS.image,
    tilecount: SRW_ATLAS.tileCount,
    columns: SRW_ATLAS.columns,
    tileWidth: SRW_ATLAS.tileSize,
    tileHeight: SRW_ATLAS.tileSize,
    imagewidth: SRW_ATLAS.columns * SRW_ATLAS.tileSize,
    imageheight: Math.ceil(SRW_ATLAS.tileCount / SRW_ATLAS.columns) * SRW_ATLAS.tileSize,
  },
  kenney_tiny_town: {
    name: 'kenney_tiny_town',
    image: '../pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png',
    ...KENNEY_META,
  },
  kenney_tiny_dungeon: {
    name: 'kenney_tiny_dungeon',
    image: '../pixel/tilesets/kenney-tiny-dungeon/Tilemap/tilemap_packed.png',
    ...KENNEY_META,
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
function makeBuilding(name, label, route, tileX, tileY, bw, bh, sprite = null) {
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
      { name: 'interaction', type: 'string', value: route },
      { name: 'label', type: 'string', value: label },
      // sprite 非空 = 用整栋精灵绘制（Super Retro World）；null = 只保留点击区。
      ...(sprite ? [{ name: 'sprite', type: 'string', value: sprite }] : []),
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

/* 旧 kenney 矮栅栏帧号表随 town_hub 重做移除：栅栏改由 SRW_ATLAS.blob.fence 逐格查表 */

/** 低障碍对象：玩家不可直接走过，但可用 Space 按当前朝向跳过 1 格。 */
function makeLowObstacle(name, label, tileX, tileY, bw = 1, bh = 1, kind = 'low', frame = 0) {
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
      { name: 'sprite', type: 'string', value: 'srw_town' },
      { name: 'frame', type: 'int', value: frame },
    ],
  };
}

// ---------- 各张地图配置 + 生成器 ----------

// ---------- town_hub (50×40) —— Super Retro World 版 ----------
//
// 布局方法论与旧版相反：旧版"先画对称广场 → 建筑围着摆 → 剩下的洞用树填"，产出的是
// "空地上一个加号 + 一圈绿墙"。这里改成地形先行：
//   1. 先立骨架：S 形主街 + 广场（主街的加宽段）+ 支巷 + 东南湖 + 顶边树线
//   2. 房子贴路摆：占地按精灵真实尺寸（HOUSE_TILES），门前自动铺门前坪
//   3. 管线顺序固定：cleanGrid → 门前坪 → connectAll → 烘焙。
//      先连通再清理会把刚接上的细桥当毛刺删掉；用圆盘笔刷铺门前坪会被 cleanGrid
//      当孤点削掉 —— 两个坑都实测踩过，所以门前坪是实心矩形、且排在清理之后。
//   4. 树不围成绿墙：顶边一条 1~2 排深的连续树线收边界，内部按衰减散布
//   5. 收尾跑校验：建筑间距、道路单连通、NPC 必定落在路面上；不满足直接抛错

/** 静态美术排布表。交互名称/用途/门前点/portal权威数据见data/world/world.json。
 * 建筑：[key, 房屋图号, 中心 tileX, 底边 tileY, 历史中文名, 历史交互键]。
 * 调整排布后同步检查配置中的object_name与门前点，运行validate-world.mjs。
 */
const TOWN_BUILDINGS = [
  ['mayor_hall', 10, 24, 12, '镇长大厅', 'town_hall'],
  ['cottage_a', 6, 31, 11, null, null],
  ['review_hall', 4, 38, 14, '居民会馆', 'community_hall'],
  ['workshop', 2, 43, 21, '教学工坊', 'classroom'],
  ['cottage_e', 3, 44, 26, null, null],
  ['library', 8, 8, 20, '图书馆', 'library'],
  ['training', 11, 8, 30, '训练场', 'practice'],
  ['cottage_b', 9, 15, 27, null, null],
  ['cottage_c', 7, 22, 28, null, null],
  ['cottage_d', 5, 17, 35, null, null],
  ['arena', 12, 33, 34, '练习广场', 'practice'],
];

/** 院墙：[x0, y0, x1, y1]（闭区间，圈内的地算院子） */
const TOWN_YARDS = [[27, 13, 30, 15], [11, 24, 13, 26], [7, 33, 12, 36]];

/** 矿洞：土丘 3×3 左上角；洞口在底边中心，主街向西直插到洞口面前 */
const MINE = { x: 9, y: 8, label: '矿洞景观', route: 'scenery' };

/** NPC：[对象名, 数据 key, 名牌, 角色图号, 归属建筑（null = 矿洞）] */
const TOWN_NPCS = [
  ['npc_mayor_py', 'mayor_py', '镇长 Py', 2, 'mayor_hall'],
  ['npc_librarian_doc', 'librarian_doc', '馆长 Doc', 6, 'library'],
  ['npc_workshop_master', 'workshop_master', '工坊师傅', 12, 'workshop'],
  ['npc_coach_q', 'coach_q', '教务 Q', 16, 'training'],
  ['npc_foreman_bug', 'foreman_bug', '矿头 Bug', 22, null],
  ['npc_sage_review', 'sage_review', '复盘师 Sage', 26, 'review_hall'],
  ['npc_class_president', 'class_president', '班长', 30, null],
];

/** 玩家出生：南入口主街上 */
const TOWN_SPAWN = { tileX: 22, tileY: 36 };

/**
 * 房屋精灵真实占地（tile 宽×高）。
 * 旧版照抄 town_hub.json 里的 80×80 元数据占位，而新素材整栋图只有 4.7×3 ~ 5×4 tile，
 * 于是每栋房子周围凭空一圈空地、只能拿树去填 —— "中间一堆、四周绿墙"就是这么来的。
 */
const HOUSE_TILES = {
  1: [4, 3], 2: [5, 3], 3: [5, 3], 4: [5, 4], 5: [4, 3], 6: [5, 3],
  7: [5, 3], 8: [5, 4], 9: [4, 3], 10: [5, 4], 11: [5, 3], 12: [5, 3],
};

function houseFootprint(houseIdx, cx, by) {
  const [wt, ht] = HOUSE_TILES[houseIdx];
  const x0 = Math.round(cx - (wt - 1) / 2);
  return [x0, by - ht + 1, x0 + wt - 1, by];
}

const pad2 = (n) => String(n).padStart(2, '0');

function addTownLandscape(map) {
  const bounds={x:-18,y:-14,width:86,height:68};
  const data=Array(bounds.width*bounds.height).fill(0);
  for(let y=0;y<bounds.height;y++)for(let x=0;x<bounds.width;x++){
    const tx=x+bounds.x,ty=y+bounds.y;
    if(tx<0||ty<0||tx>=map.width||ty>=map.height)data[y*bounds.width+x]=gid(SRW_ATLAS.single.grass);
  }
  const forest=[];
  const variants=[1,2,3,4,5,8,9,10,11,12,13,14,15];
  let id=map.nextobjectid;
  for(let y=-12;y<53;y+=4)for(let x=-16;x<66;x+=5){
    if(x>-4&&x<53&&y>-4&&y<43)continue;
    const code=Math.abs(x*127+y*311),tileX=x+(code%3),tileY=y+(code%2);
    const sprite=`srw_tree_${String(variants[code%variants.length]).padStart(2,'0')}`;
    forest.push({id:id++,name:`landscape_tree_${forest.length+1}`,type:'prop',x:tileX*TILE,y:(tileY-1)*TILE,width:TILE,height:TILE,rotation:0,visible:true,properties:[{name:'sprite',type:'string',value:sprite},{name:'collidable',type:'bool',value:false}]});
  }
  map.layers.unshift({id:6,name:'landscape',type:'tilelayer',...bounds,opacity:1,visible:true,data});
  map.layers.push({id:7,name:'landscape_trees',type:'objectgroup',x:0,y:0,opacity:1,visible:true,draworder:'topdown',objects:forest});
  map.nextlayerid=8;map.nextobjectid=id;
  return map;
}

function generateTownHub() {
  const W = 50;
  const H = 40;
  const grid = makeGrid(W, H);
  const rand = mulberry32(0x51ee7);
  const inB = (x, y) => x >= 0 && y >= 0 && y < H && x < W;
  const isGrass = (x, y) => inB(x, y) && grid[y][x] === MAT.GRASS;
  resetObjectIds();

  // ---------- 1. 路网 ----------
  const road = (pts, r, seed) => gridStampPath(grid, pts, r, MAT.ROAD, seed);
  road([[23, 40], [22, 36], [21, 32], [22, 28], [24, 24], [25, 22],
    [24.5, 19], [24, 15.5], [24, 13.6]], 1.35, 1);               // 主街：南门 → 广场 → 镇长府
  gridStampBlob(grid, 25.5, 21, 3.2, 2.1, MAT.ROAD, 2, 0.28);     // 广场（就是主街的加宽段）
  road([[24, 13.6], [20, 13], [16, 12], [12, 11.4], [MINE.x + 1.4, MINE.y + 3.2]], 1, 3);
  road([[21, 22], [15, 22], [10, 22], [8, 22]], 1, 5);            // 西巷（图书馆）
  road([[8, 22], [7, 26], [8, 30], [8, 31.5]], 0.95, 15);         // 西巷南下（训练场）
  road([[13, 22], [16, 25], [19, 27], [22, 29]], 0.9, 14);        // 民居 b / c
  road([[8, 31.5], [11, 33], [15, 34], [19, 33], [21, 31]], 0.95, 12);   // 南环路
  road([[16, 34], [17, 36]], 0.9, 16);                            // 民居 d 门前
  road([[28, 20], [32, 18], [36, 16], [38, 16]], 1, 6);           // 东环道（复盘厅）
  road([[34, 17], [32, 14], [31, 12.6]], 0.9, 17);                // 民居 a 门前
  road([[39, 17], [40, 20], [42, 22.5]], 0.95, 18);               // 工坊支巷
  road([[43, 23], [44, 25], [44, 27.5]], 0.95, 19);               // cottage_e 支巷
  road([[24, 26], [29, 28], [33, 30]], 1, 7);                     // 东南路
  road([[33, 30], [32, 33], [33, 35.5]], 0.9, 13);                // 竞技场
  road([[34, 31], [36, 32.5]], 0.85, 20);                         // 湖西岸观景台

  // ---------- 2. 湖与训练沙坪 ----------
  gridStampBlob(grid, 41.5, 31.5, 4.8, 3.6, MAT.WATER, 8, 0.26);
  fillLakes(grid);                                                // 抖动会漏格，洪泛去掉"湖中孤岛"
  gridStampBlob(grid, 9.5, 34.5, 2.4, 1.3, MAT.DIRT, 31, 0.1);     // 训练沙坪（沙子色在这里才成立）

  // ---------- 3. 建筑：清出占地 + 登记禁区 ----------
  const reserved = new Set();
  /** 只挡“建筑本体+1 格 + 院子内部”的紧凑禁区，给边界林用。
   *  3 格的宽禁区是为林块/道具留的；边界林离房子远，用宽禁区会把整条林带呑掉。 */
  const reservedCore = new Set();
  const reserve = (x0, y0, x1, y1, pad = 0) => {
    for (let y = y0 - pad; y <= y1 + pad; y += 1) {
      for (let x = x0 - pad; x <= x1 + pad; x += 1) reserved.add((y * W) + x);
    }
  };
  const reserveCore = (x0, y0, x1, y1, pad = 0) => {
    for (let y = y0 - pad; y <= y1 + pad; y += 1) {
      for (let x = x0 - pad; x <= x1 + pad; x += 1) reservedCore.add((y * W) + x);
    }
  };
  TOWN_BUILDINGS.forEach(([_k, house, cx, by]) => {
    const [fx0, fy0, fx1, fy1] = houseFootprint(house, cx, by);
    gridFill(grid, fx0, fy0 - 1, fx1, fy1 + 1, MAT.GRASS);
    reserve(fx0, fy0, fx1, fy1, 3);
    reserveCore(fx0, fy0, fx1, fy1, 1);
  });
  reserve(MINE.x - 3, MINE.y - 2, MINE.x + 4, MINE.y + 3);
  reserveCore(MINE.x - 1, MINE.y - 1, MINE.x + 3, MINE.y + 3, 1);
  TOWN_YARDS.forEach(([ax, ay, bx, by]) => {
    reserve(ax - 1, ay - 1, bx + 1, by + 1);
    reserveCore(ax, ay, bx, by);
  });

  // ---------- 4. 管线 + 校验 ----------
  cleanGrid(grid);
  TOWN_BUILDINGS.forEach(([_k, _h, cx, by]) => gridDoorApron(grid, cx, by));
  gridFill(grid, MINE.x, MINE.y + 3, MINE.x + 2, MINE.y + 4, MAT.ROAD);   // 矿洞门前台阶
  const bridges = connectAll(grid, MAT.ROAD);
  const comps = componentsOf(grid, MAT.ROAD).length;
  if (comps !== 1) throw new Error(`town_hub 道路未连通：${comps} 块（桥接 ${bridges} 格后仍不闭合）`);
  const gaps = footprintsGapOk(TOWN_BUILDINGS.map(([, h, cx, by]) => houseFootprint(h, cx, by)), 2);
  if (gaps.length) {
    throw new Error(`town_hub 建筑间距违规：${gaps.map(([i, j, g]) => `${TOWN_BUILDINGS[i][0]}<->${TOWN_BUILDINGS[j][0]}=${g}`).join(' ')}`);
  }

  // ---------- 5. 烘焙地面 + 装饰层 ----------
  const blobFor = (mat) => (mat === MAT.ROAD ? SRW_BLOB.road
    : mat === MAT.WATER ? SRW_BLOB.water
      : mat === MAT.DIRT ? SRW_BLOB.dirt : undefined);
  const ground = bakeGrid(grid, blobFor);
  const decoration = emptyData(W, H);
  const collision = new Array(W * H).fill(0);
  // 水面全部不可走。之前只给建筑/矿洞/树锚点写过 1，水是纯装饰色块，
  // 结果角色能直接在水面上走（用户实机截图）。
  for (let y = 0; y < H; y += 1) {
    for (let x = 0; x < W; x += 1) {
      if (grid[y][x] === MAT.WATER) collision[(y * W) + x] = 1;
    }
  }

  const S = SRW_SINGLE;
  const pick = (names) => names.map((k) => S[k]);
  // ⚠️ 只允许「能独立成株」的格子进装饰池。图集前 22 格逐格合成到草地上实测：
  //   grass_tall_a/b + tuft_a..f（1..8）图案压在格子**上边缘**、自带暗绿渐变，是
  //     草地↔道路交界的过渡覆盖片，散铺在开阔草地中间会连成悬空的深绿斑；
  //   bush_c（16）是暗绿三角，看着像地上的洞；blade_a（19）是灰黑枯枝，像垃圾；
  // 这几格的视觉故障在 R-14 换图后由用户实机截图为证（小镇中部一大坨深绿斑）。
  // 保留的四组：花/蘑菇（9..13）、双蘑菇与蕨（14..15）、浅色草簇（17/18/20/21）。
  const FLOWERS = pick(['flower_a', 'flower_b', 'flower_c', 'flower_d', 'flower_e']);
  const BUSHES = pick(['bush_a', 'bush_b']);
  const TUFTS = pick(['bush_d', 'bush_e', 'blade_b', 'blade_c']);

  const freeDeco = (x, y) => inB(x, y) && grid[y][x] === MAT.GRASS
    && !reserved.has((y * W) + x) && decoration[(y * W) + x] === 0;
  const putDeco = (x, y, index) => {
    if (!freeDeco(x, y)) return false;
    decoration[(y * W) + x] = gid(index);
    return true;
  };
  /** 随机游走连成一团：单格散铺会出条纹/竖线，成团才读作草丛。 */
  const patch = (cx0, cy0, steps, choose) => {
    let cell = null;
    for (let t = 0; t < 24 && !cell; t += 1) {
      const x = cx0 + Math.floor(rand() * 7) - 3;
      const y = cy0 + Math.floor(rand() * 7) - 3;
      if (freeDeco(x, y)) cell = [x, y];
    }
    if (!cell) return 0;
    let done = 0;
    for (let s = 0; s < steps; s += 1) {
      const [x, y] = cell;
      if (putDeco(x, y, choose(x, y))) done += 1;
      let moved = null;
      for (let t = 0; t < 8 && !moved; t += 1) {
        const nx = x + Math.floor(rand() * 3) - 1;
        const ny = y + Math.floor(rand() * 3) - 1;
        if (freeDeco(nx, ny)) moved = [nx, ny];
      }
      if (!moved) break;
      cell = moved;
    }
    return done;
  };

  [[6, 13], [15, 15], [33, 24], [38, 30], [45, 20], [24, 34], [5, 22], [43, 13],
    [19, 36], [29, 33], [47, 34], [36, 8], [11, 17], [26, 31]]
    .forEach(([x, y]) => patch(x, y, 10, (px, py) => TUFTS[(px + py) % TUFTS.length]));

  // 野花草甸：草簇55 / 小花25 / 蘑菇与蕨20 的权重混铺
  const MEADOW = [[4, 12], [8, 16], [14, 12], [19, 9], [26, 8], [33, 10], [39, 10], [45, 14],
    [12, 19], [24, 15], [30, 14], [36, 24], [43, 22], [48, 28], [3, 20], [6, 34],
    [14, 33], [22, 36], [28, 33], [38, 36], [46, 37], [41, 14], [33, 21], [20, 21],
    [16, 30], [48, 16], [15, 9], [20, 10], [25, 9], [30, 10], [35, 9], [40, 10],
    [45, 9], [48, 11], [18, 12], [28, 12], [38, 12], [44, 13], [13, 11], [26, 13]];
  const MIX = []
    .concat(Array(55).fill(TUFTS), Array(25).fill(FLOWERS), Array(20).fill(BUSHES))
    .map((group) => group[Math.floor(rand() * group.length)]);
  MEADOW.forEach(([x, y], i) => patch(x, y, 7 + Math.floor(rand() * 7),
    () => MIX[(i * 5 + 1) % MIX.length]));

  // 贴边草簇：紧挨路/水的草地边缘聚簇，消掉"铺装硬切到纯绿"
  for (let t = 0, placed = 0; t < 2400 && placed < 320; t += 1) {
    const x = 1 + Math.floor(rand() * (W - 2));
    const y = 1 + Math.floor(rand() * (H - 2));
    if (!freeDeco(x, y)) continue;
    if (!NEIGHBOR_DELTAS.some(([dx, dy]) => inB(x + dx, y + dy)
      && grid[y + dy][x + dx] !== MAT.GRASS)) continue;
    putDeco(x, y, TUFTS[placed % TUFTS.length]);
    placed += 1;
  }

  // ---------- 6. objects ----------
  const objects = [];
  /** 精灵物体：锚点 = 该 tile 底边中心；渲染层按 y 排序做前后遮挡。 */
  const addProp = (name, sprite, tx, ty, blocks) => {
    objects.push({
      id: nextObjectId(),
      name,
      type: 'prop',
      x: tx * TILE,
      y: ty * TILE,
      width: TILE,
      height: TILE,
      visible: true,
      rotation: 0,
      properties: [
        { name: 'sprite', type: 'string', value: sprite },
        { name: 'anchor', type: 'string', value: 'bottom' },
      ],
    });
    if (blocks && inB(tx, ty)) collision[(ty * W) + tx] = 1;
  };

  // 6a. 房屋：整栋精灵 + 点击区。民居没有功能路由，就不发 building 对象（避免点进死路页）。
  TOWN_BUILDINGS.forEach(([key, house, cx, by, label, route]) => {
    const [fx0, fy0, fx1, fy1] = houseFootprint(house, cx, by);
    const wt = fx1 - fx0 + 1;
    const ht = fy1 - fy0 + 1;
    for (let y = fy0; y <= fy1; y += 1) {
      for (let x = fx0; x <= fx1; x += 1) collision[(y * W) + x] = 1;
    }
    if (route) {
      objects.push(makeBuilding(`building_${key}`, label, route, fx0, fy0, wt, ht,
        `srw_house_${pad2(house)}`));
    } else {
      addProp(`cottage_${key}`, `srw_house_${pad2(house)}`, cx, fy1, false);
    }
  });

  // 6b. 矿洞：洞口土丘（逐格进 decoration，只留洞口可站）+ 身后巨石群 + 火把
  [['mound_tl', 0, 0], ['mound_tc', 1, 0], ['mound_tr', 2, 0],
    ['mound_ml', 0, 1], ['mound_mc', 1, 1], ['mound_mr', 2, 1],
    ['mound_bl', 0, 2], ['cave_mouth', 1, 2], ['mound_br', 2, 2]].forEach(([name, dx, dy]) => {
    const index = S[name];
    if (index === undefined) {
      throw new Error(`图集清单缺少矿洞块 ${name}：请重跑 python scripts/build_srw_assets.py`);
    }
    const x = MINE.x + dx;
    const y = MINE.y + dy;
    decoration[(y * W) + x] = gid(index);
    if (name !== 'cave_mouth') collision[(y * W) + x] = 1;
  });
  objects.push(makeBuilding('building_mine', MINE.label, MINE.route, MINE.x, MINE.y, 3, 3, null));
  [[-2, -3, 18], [2, -3, 25], [-4, -1, 36], [5, -1, 41], [1, -5, 20], [-3, -4, 27],
    [4, -4, 46], [-5, 1, 19], [6, 1, 26]].forEach(([dx, dy, rock], i) => {
    addProp(`mine_rock_${i}`, `srw_rock_${pad2(rock)}`, MINE.x + dx, MINE.y + dy, true);
  });
  addProp('mine_torch_w', 'srw_torch_01', MINE.x - 1, MINE.y + 2, false);
  addProp('mine_torch_e', 'srw_torch_03', MINE.x + 3, MINE.y + 2, false);

  // 6c. 树：顶边连续树线（收边界）→ 疏林溢出 → 矿洞背后林子 → 两条次排林 → 村周小丛
  const used = new Set(reserved);
  const treeFree = (x, y) => {
    // 树冠宽 2~3 格、底边对齐锚点，所以锚点要离地图边界留余量，
    // 否则树冠被边界硬切（旧版顶边 y=1~2 也种树，冠被切掉一半）。
    if (x < 2 || x > W - 3 || y < 3 || y > H - 4) return false;
    // 锚点本身必须是草地（不种在路上/水上）；邻域只要求“没有别的树”。
    // 不要求整个 3×3 都是草地：树冠垂下路边是好看的，代价是候选点被砍掉一大半（实测只种得进 33 棵）。
    if (!isGrass(x, y)) return false;
    for (let dy = -1; dy <= 1; dy += 1) {
      for (let dx = -1; dx <= 1; dx += 1) {
        if (used.has(((y + dy) * W) + (x + dx))) return false;
      }
    }
    return true;
  };
  let treeN = 0;
  /** 已种下的树锤点。间距只拿树和树比：稠密禁区（reserved）是给林块用的，
   *  边界林用 departmental 的 reservedCore，不该被整片吞掉。 */
  const treeCells = new Set();
  const plantTree = (x, y) => {
    if (!treeFree(x, y)) return false;
    addProp(`tree_${treeN}`, `srw_tree_${pad2(((treeN * 7) % SRW_ATLAS.props.tree) + 1)}`, x, y, true);
    used.add((y * W) + x);
    treeCells.add((y * W) + x);
    treeN += 1;
    return true;
  };
  /** 边界林专用：挡建筑本体+院子，只与其它树比 3×3 间距。 */
  const plantBandTree = (x, y) => {
    if (x < 2 || x > W - 3 || y < 3 || y > H - 4) return false;
    if (!isGrass(x, y) || reservedCore.has((y * W) + x)) return false;
    for (let dy = -1; dy <= 1; dy += 1) {
      for (let dx = -1; dx <= 1; dx += 1) {
        if (treeCells.has(((y + dy) * W) + (x + dx))) return false;
      }
    }
    return plantTreeForced(x, y);
  };
  const plantTreeForced = (x, y) => {
    addProp(`tree_${treeN}`, `srw_tree_${pad2(((treeN * 7) % SRW_ATLAS.props.tree) + 1)}`, x, y, true);
    used.add((y * W) + x);
    treeCells.add((y * W) + x);
    treeN += 1;
    return true;
  };
  // 边界林：四周 6 格内用交错栅格种树（步伐 2 + 隔行错位 1，天然满足≥⃣2 格最小间距），
  // 越靠边概率越高。顶线原来是一排“滤镜树”，看着像树篱而不是树林。
  const BAND = 6;
  for (let y = 3; y < H - 3; y += 2) {
    const shift = y % 4 === 0 ? 0 : 1;
    for (let x = 2 + shift; x < W - 2; x += 2) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d >= BAND) continue;
      if (rand() > 0.95 - 0.14 * d) continue;
      if (reservedCore.has((y * W) + x)) continue;
      plantBandTree(x, y);
    }
  }
  for (let x = MINE.x - 7; x <= MINE.x + 8; x += 1) {    // 矿洞背后林子：把洞口嵌进林缘
    [MINE.y - 4, MINE.y - 5, MINE.y - 6].forEach((y) => {
      if (rand() < 0.52) plantTree(x, y);
    });
  }
  [[5, 0.78], [7.4, 0.46]].forEach(([rowY, keep]) => {   // 树线与房屋之间的两条次排林
    for (let x = MINE.x + 4; x < W; x += 1) {
      const y = Math.round(rowY + Math.sin(x * 0.37 + rowY * 2.2) * 1.15);
      if (rand() < keep) plantTree(x, y);
    }
  });
  [[[3, 15, 4.2, 3.2], [6, 18, 3, 2.6]],
    [[45, 12, 4.2, 3.2], [47, 16, 3, 3]],
    [[3, 34, 4.2, 3], [6, 37, 3.2, 2.2]],
    [[20, 37, 5, 2.2]], [[46, 30, 3.4, 2.6]], [[31, 8, 4.6, 2.4]],
    [[12, 9, 3.6, 2.2]], [[26, 14, 2.6, 2]], [[14, 34, 3, 2]]].forEach((lobes, ci) => {
    // 用交错栅格（步伐 2 + 隔行错位 1）扫描林块，而不是随机撒点：
    // 引入“任意两树锤点至少隔 2 格”后，随机撒点会被 3×3 封区反复撞回，
    // 一块林子只能种进一两棵（实测 320 次甚至 2000 次都填不满）。
    // 栅格步 2 天然满足最小间距，(2,1) 的交错锤点切比雪夫距离也是 2，不会被拒。
    const minX = Math.floor(Math.min(...lobes.map((a) => a[0] - a[2])));
    const maxX = Math.ceil(Math.max(...lobes.map((a) => a[0] + a[2])));
    const minY = Math.floor(Math.min(...lobes.map((a) => a[1] - a[3])));
    const maxY = Math.ceil(Math.max(...lobes.map((a) => a[1] + a[3])));
    for (let y = minY; y <= maxY; y += 2) {
      const shift = Math.abs(y) % 4 === 0 ? 0 : 1;
      for (let x = minX + shift; x <= maxX; x += 2) {
        const d = Math.min(...lobes.map((a) => Math.hypot((x - a[0]) / a[2], (y - a[1]) / a[3])));
        if (d > 1) continue;
        // 林心密、林缘稀：d=0 约 0.85 保留，d=1 约 0.3。
        if (rand() > 0.85 - 0.55 * d * d) continue;
        if (!plantTree(x, y)) continue;
        if (d > 0.7) putDeco(x, y - 1, BUSHES[(ci + treeN) % BUSHES.length]);
      }
    }
  });

  // 6d. 院墙：逐格 low_obstacle（可跳），每格按 47-blob 掩码取该画的栅栏块。
  //     落在路/水上的格子直接省略 —— 于是院墙与路相交处自然形成院门。
  const cellInBuilding = (x, y) => TOWN_BUILDINGS.some(([, house, cx, by]) => {
    const [fx0, fy0, fx1, fy1] = houseFootprint(house, cx, by);
    return x >= fx0 && x <= fx1 && y >= fy0 && y <= fy1;
  });
  TOWN_YARDS.forEach(([ax, ay, bx, by], yi) => {
    const ring = new Set();
    for (let x = ax - 1; x <= bx + 1; x += 1) {
      ring.add(`${x},${ay - 1}`);
      ring.add(`${x},${by + 1}`);
    }
    for (let y = ay - 1; y <= by + 1; y += 1) {
      ring.add(`${ax - 1},${y}`);
      ring.add(`${bx + 1},${y}`);
    }
    ring.forEach((k) => {
      const [x, y] = k.split(',').map(Number);
      if (!isGrass(x, y) || cellInBuilding(x, y)) return;
      let m = 0;
      NEIGHBOR_DELTAS.forEach(([dx, dy], bit) => {
        if (ring.has(`${x + dx},${y + dy}`) && isGrass(x + dx, y + dy)) m |= 1 << bit;
      });
      objects.push(makeLowObstacle(`low_path_fence_${yi}_${x}_${y}`, '院墙栅栏', x, y, 1, 1,
        'fence', lookupBlob(SRW_BLOB.fence, m)));
      used.add((y * W) + x);
    });
    [[ax, ay], [bx, ay], [ax, by], [bx, by]].forEach(([x, y], j) => {
      addProp(`yard_${yi}_${j}_plant`, `srw_potted_plant_${pad2(((yi * 2 + j) % 12) + 1)}`, x, y, false);
    });
  });

  // 6e. 路灯 / 木桶：站在紧邻路面的草地上（不挡路、不参与碰撞）
  const roadside = (x, y) => {
    for (let r = 0; r < 4; r += 1) {
      for (let dy = -r; dy <= r; dy += 1) {
        for (let dx = -r; dx <= r; dx += 1) {
          const nx = x + dx;
          const ny = y + dy;
          if (!inB(nx, ny) || reserved.has((ny * W) + nx) || grid[ny][nx] !== MAT.GRASS) continue;
          if (NEIGHBOR_DELTAS.some(([ux, uy]) => grid[ny + uy]?.[nx + ux] === MAT.ROAD)) return [nx, ny];
        }
      }
    }
    return null;
  };
  [[21, 27], [27, 25], [14, 22], [31, 19], [24, 16], [9, 32], [36, 31], [17, 24], [39, 20], [28, 30]]
    .forEach(([x, y], i) => {
      const spot = roadside(x, y);
      if (spot) addProp(`lamp_${i}`, `srw_lamp_${pad2((i % 5) + 1)}`, spot[0], spot[1], false);
    });
  [[47, 22], [48, 22], [47, 23]].forEach(([x, y], i) => {
    if (isGrass(x, y) && !reserved.has((y * W) + x)) {
      addProp(`workshop_barrel_${i}`, `srw_barrel_${pad2(i + 1)}`, x, y, false);
    }
  });

  // 精灵键越界校验：道具是切片脚本按 01..NN 重命名出来的，源包本身编号不连续
  // （例 trees 没有 06/07）。少这道检查，运行时会静默画不出树。
  objects.forEach((o) => {
    const sprite = (o.properties ?? []).find((p) => p.name === 'sprite')?.value;
    if (typeof sprite !== 'string' || sprite === 'srw_town') return;
    const m = /^srw_([a-z_]+)_(\d+)$/.exec(sprite);
    if (!m) throw new Error(`精灵键格式不认识：${sprite}`);
    const [, kind, num] = m;
    const total = SRW_ATLAS.props[kind];
    if (total === undefined) throw new Error(`图集清单里没有道具种类 ${kind}（键 ${sprite}）`);
    if (Number(num) > total) throw new Error(`精灵键 ${sprite} 越界：${kind} 只有 ${total} 张`);
  });

  // ---------- 7. spawn 与 NPC（一律吸附到路面，落不到就抛错）----------
  const nearestRoad = (x, y) => {
    for (let r = 0; r < 6; r += 1) {
      for (let dy = -r; dy <= r; dy += 1) {
        for (let dx = -r; dx <= r; dx += 1) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue;
          if (inB(x + dx, y + dy) && grid[y + dy][x + dx] === MAT.ROAD) return [x + dx, y + dy];
        }
      }
    }
    return null;
  };
  const spawnTile = nearestRoad(TOWN_SPAWN.tileX, TOWN_SPAWN.tileY);
  if (!spawnTile) throw new Error('玩家出生点附近找不到路面');
  objects.push(makeSpawn(spawnTile[0], spawnTile[1]));
  TOWN_NPCS.forEach(([objName, npcKey, label, chara, buildingKey]) => {
    const found = buildingKey ? TOWN_BUILDINGS.find((b) => b[0] === buildingKey) : null;
    if (buildingKey && !found) throw new Error(`NPC ${objName} 的归属建筑 ${buildingKey} 不存在`);
    const raw = found ? [found[2], found[3] + 1] : [MINE.x + 1, MINE.y + 3];
    const at = nearestRoad(raw[0], raw[1]);
    if (!at) throw new Error(`NPC ${objName} 落不到路面上（起点 ${raw}）`);
    objects.push(makeNpc(objName, npcKey, label, at[0], at[1], `srw_chara_${chara}`));
  });
  // 广场中心树：这套素材没有户外长椅 / 喷泉 / 水井，广场靠一棵大树收中心
  addProp('plaza_tree', 'srw_tree_09', 25, 20, true);
  objects.push(makeLandmark('plaza_tree', '中央广场古樟', 25, 20, 1, 1, 'landmark'));

  // ---------- 8. 组装 ----------
  // 对象按锚点 y 升序输出：渲染时同深度的对象按创建顺序叠，于是“下面的树压在上面的树
  // 之上”，林带有了前后关系；之前顺序是种植先后，树冠互相穿插、看不出层。
  objects.sort((a, b) => (a.y - b.y) || (a.x - b.x));
  console.log(`  town_hub: 树 ${treeN}，路面 ${componentsOf(grid, MAT.ROAD)[0].length} 格，`
    + `桥接 ${bridges} 格，objects ${objects.length} 个`);
  return addTownLandscape(buildMapJson({
    key: 'town_hub',
    label: 'Pythontopia 小镇',
    width: W,
    height: H,
    tileset: 'srw_town',
    ground,
    buildings: emptyData(W, H),      // 建筑改整栋精灵后此层留空（保留层以兼容旧碰撞读取）
    decoration,
    collision,
    objects,
  }));
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
        tileset: 'srw_town',
        // R-14 单中央 hub：town_hub.json 不含任何 exit 对象，index 出口声明须与地图一致（空）。
        // 进入 矿洞景观经 building → scenery 路由，不走地图边缘 walk-exit。
        exits: [],
        spawn: { tileX: TOWN_SPAWN.tileX, tileY: TOWN_SPAWN.tileY },
      },
    ],
  };
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
    const layerByName = (n) => json.layers.find((l) => l.name === n);
    results.push({
      name: g.name,
      bytes: Buffer.byteLength(out, 'utf-8'),
      layers: json.layers.length,
      groundCells: layerByName('ground').data.length,
      buildingCells: layerByName('buildings').data.length,
      decorationCells: layerByName('decoration').data.length,
      collisionCells: layerByName('collision').data.filter((v) => v > 0).length,
      objects: layerByName('objects').objects.length,
    });
  }

  const indexObj = buildIndex();
  const indexStr = JSON.stringify(indexObj, null, 2);
  if (!dryRun && !process.argv.includes('--town-only')) await fs.writeFile(path.join(MAPS_DIR, 'index.json'), indexStr, 'utf-8');


  console.log('[generate-maps] 写入完成：');
  for (const r of results) {
    console.log(
      `  - ${r.name.padEnd(20)} ${String(r.bytes).padStart(8)} bytes | layers=${r.layers} | ground=${r.groundCells} | building=${r.buildingCells} | deco=${r.decorationCells} | collide=${r.collisionCells} | objects=${r.objects}`,
    );
  }
  console.log(`  - index.json         ${String(Buffer.byteLength(indexStr, 'utf-8')).padStart(8)} bytes`);
  if (dryRun) console.log('[generate-maps] --dry-run：未写入文件。');
}

main().catch((err) => {
  console.error('[generate-maps] 失败：', err);
  process.exit(1);
});
