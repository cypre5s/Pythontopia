#!/usr/bin/env node
/**
 * sync-assets.mjs — Pythontopia 资产同步脚本
 *
 * @file 从 assets/inbox/ 增量同步像素美术资产到 frontend/public/assets/pixel/。
 * @summary
 *   - 输入源（src）：`assets/inbox/`（已审计的离线镜像，约 13000+ 文件）
 *   - 输出目标（dest）：`frontend/public/assets/pixel/`（Vite 静态服务目录）
 *   - 资产分层授权：CC0 / CC-BY 3.0 / CC-BY 4.0 / CC-BY-SA 3.0+ / OFL-1.1
 *   - 同步完成后会在 dest 根目录生成/更新 `LICENSE.md` 的"按目录索引"段落。
 *
 * @example
 *   node scripts/sync-assets.mjs              # 增量复制（仅当目标不存在时复制）
 *   node scripts/sync-assets.mjs --check      # 只检查不复制
 *   node scripts/sync-assets.mjs --dry-run    # 打印将要执行的复制操作但不实际执行
 *   node scripts/sync-assets.mjs --force      # 覆盖已存在的目标文件
 *
 * @author Pythontopia 子代理 M1-A
 * @license MIT
 * @runtime Node 20+, 仅依赖 node:fs / node:path / node:url（无 npm 依赖）
 */

import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// ---------- 路径锚点 ----------
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
/** 仓库根目录（脚本父级） */
const REPO_ROOT = path.resolve(__dirname, '..');
/** 资产源根目录（已审计的离线镜像） */
const INBOX_ROOT = path.join(REPO_ROOT, 'assets', 'inbox');
/** 资产目标根目录（Vite 静态资源） */
const PUBLIC_PIXEL_ROOT = path.join(REPO_ROOT, 'frontend', 'public', 'assets', 'pixel');

// ---------- 默认可复制扩展名 ----------
/** 默认目录复制时只复制这些扩展名的文件，避免把 .url / .DS_Store 等带进 public */
const DEFAULT_EXTENSIONS = new Set([
  '.png', '.jpg', '.jpeg', '.webp',
  '.mp3', '.ogg', '.wav',
  '.woff2', '.ttf', '.otf',
  '.fnt', '.json', '.tmx', '.tsx', '.tmj',
  '.csv', '.txt', '.md',
]);

/**
 * @typedef {Object} ManifestEntry
 * @property {string} src           inbox 相对路径
 * @property {string} dest          public/assets/pixel 相对路径
 * @property {'dir'|'file'} [kind]  显式声明类型，缺省时自动判断
 * @property {string} license       SPDX 标识 (CC0-1.0 / CC-BY-3.0 / CC-BY-4.0 / CC-BY-SA-3.0 / OFL-1.1 ...)
 * @property {string} [author]      作者署名
 * @property {string} [source]      原始来源 URL
 * @property {string} [notes]       备注
 * @property {string[]} [extensions] dir 模式下额外限制扩展名（缺省用 DEFAULT_EXTENSIONS）
 * @property {boolean} [noSync]     true 表示仅参与 LICENSE.md 索引但不实际同步（用于已手工挑选/精简过的目录）
 */

/**
 * 资产清单：声明 inbox → public 的全部映射关系。
 *
 * 维护原则：
 *  1. 只列举 inbox 实际存在的项目；脚本运行时会用 statSafe 兜底，src 不存在时打印警告并跳过。
 *  2. dest 路径风格按 frontend/public/assets/pixel/ 现有命名约定（短横线分隔）。
 *  3. license 字段必须精确，便于自动生成 LICENSE.md 索引。
 *
 * @type {ManifestEntry[]}
 */
const MANIFEST = [
  // ============================================================
  // === Kenney CC0 Tilemap / Tileset（小镇 + 矿洞 + 城市 + 地牢）===
  // ============================================================
  {
    src: 'kenney_tilesets/kenney_tiny-town',
    dest: 'tilesets/kenney-tiny-town',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/tiny-town',
    notes: '小镇 16x16 像素 tileset，BootScene 主用',
  },
  {
    src: 'kenney_tilesets/kenney_tiny-dungeon',
    dest: 'tilesets/kenney-tiny-dungeon',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/tiny-dungeon',
    notes: 'Bug 矿洞地牢 16x16 像素 tileset',
  },
  {
    src: 'kenney_tilemaps/roguelike-modern-city',
    dest: 'tilesets/kenney-roguelike-modern-city',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/roguelike-modern-city',
    notes: '现代城市 tileset（可选场景）',
  },
  {
    src: 'kenney_tilemaps/roguelike-caves-dungeons',
    dest: 'tilesets/kenney-roguelike-caves',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/roguelike-caves-dungeons',
    notes: '洞穴/地牢 tileset',
  },
  {
    src: 'assets/pixel/LICENSE.md',
    dest: 'tilesets/town_tiles.svg',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'Pythontopia project',
    source: 'assets/pixel/LICENSE.md',
    notes: '自绘小镇 tileset 占位 SVG；源文件为项目早期占位清单',
    noSync: true,
  },
  {
    src: 'assets/pixel/LICENSE.md',
    dest: 'buildings/town_buildings.svg',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'Pythontopia project',
    source: 'assets/pixel/LICENSE.md',
    notes: '自绘建筑占位 SVG；源文件为项目早期占位清单',
    noSync: true,
  },
  {
    src: 'assets/pixel/LICENSE.md',
    dest: 'characters/student_default.svg',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'Pythontopia project',
    source: 'assets/pixel/LICENSE.md',
    notes: '自绘默认学生占位 SVG；LPC Sara 不可用时的 fallback',
    noSync: true,
  },

  // ============================================================
  // === LPC 角色（CC-BY-SA 3.0+，传染性，已接受）===
  // ============================================================
  {
    src: 'characters_extra/lpc_sara',
    dest: 'sprites/lpc-sara',
    kind: 'dir',
    license: 'CC-BY-SA-3.0',
    author: 'Sharm / LPC Project',
    source: 'https://opengameart.org/content/lpc-sara',
    notes: 'LPC Sara 全身像，BootScene player_sara 帧 64x64',
  },
  {
    src: 'characters_extra/lpc_medieval_fantasy',
    dest: 'sprites/lpc-medieval-fantasy',
    kind: 'dir',
    license: 'CC-BY-SA-3.0',
    author: 'bluecarrot16 / TheraHedwig / Reemax / Ivan Voirol 等',
    source: 'https://opengameart.org/content/lpc-medieval-fantasy-character-sprites',
    notes: 'LPC 中世纪风 NPC，校园老师/同学候选',
  },
  {
    src: 'characters_extra/peppercarrot_24x32',
    dest: 'sprites/peppercarrot-24x32',
    kind: 'dir',
    license: 'CC-BY-4.0',
    author: 'Svetlana Kushnariova ("Cabbit") / David Revoy',
    source: 'https://www.peppercarrot.com',
    notes: '24x32 矮人角色，可作"小作物"NPC 候选',
  },
  {
    // P3-a3：智能体事务所复用的 16 张 NPC 行走帧（由 LPC walkcycle 图层合成，9×4，64px）。
    // 已在 BootScene 预载，并被 AgentOfficeScene.vue 以 CSS background 引用；CC-BY-SA-3.0 传染已接受。
    src: 'characters_extra/npc_composites',
    dest: 'sprites/npc-composites',
    kind: 'dir',
    license: 'CC-BY-SA-3.0',
    author: 'Johannes Sjölund (wulax) / bluecarrot16 / LPC base contributors',
    source: 'https://opengameart.org/content/lpc-medieval-fantasy-character-sprites',
    notes: '16 个 Agent / 主管 NPC walkcycle 合成帧（npc_*.png），智能体事务所动画 + 世界 NPC 复用',
    noSync: true,
  },
  {
    // R-12 动作模组（方案A）：官方 ULPC「Expanded」全动作角色表（13×42，含 run/jump/idle/sit），
    // 由 scripts/build-ulpc-characters.py 用官方 Universal-LPC 素材库合成；主角 + 8 个变体 NPC。
    src: 'characters_extra/ulpc_generated',
    dest: 'sprites/ulpc',
    kind: 'dir',
    license: 'CC-BY-SA-3.0',
    author: 'LPC contributors (bluecarrot16, ElizaWy, JaidynReiman, wulax, Redshrike, …，见 CREDITS-ulpc-used.txt)',
    source: 'https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator',
    notes: '官方 ULPC 全动作角色表（player_sara2 + ulpc_*，13×42 expanded，run/jump/idle/sit/emote + 6 基础），scripts/build-ulpc-characters.py 合成',
    noSync: true,
  },
  {
    // R-12 动作模组 C 阶段：星露谷味手持工具叠加层（LPC Hand Tools 的 thrust 系工具，
    // universal 13×21 同帧布局，叠在主角/NPC 之上即「手持工具」）。联网爬取自 OpenGameArt。
    src: 'characters_extra/lpc_hand_tools',
    dest: 'sprites/lpc-tools',
    kind: 'dir',
    license: 'CC-BY-SA-3.0',
    author: 'bluecarrot16, Pierre Vigier, Johannes Sjölund (wulax), Tuomo Untinen, Inboxninja',
    source: 'https://opengameart.org/content/lpc-hand-tools',
    notes: '浇水壶/锄头手持工具叠加层（watering-can/hoe + bg，universal），主角/NPC 浇水=thrust 动作叠加；含 CREDITS-hand-tools.txt',
    noSync: true,
  },
  {
    // R-12：用 LPC 茅草屋 kit 合成的整屋 sprite（scripts/build-cottages.py），替换世界场景方块拼接建筑。
    src: 'buildings_npc/lpc_thatched-roof-cottage',
    dest: 'sprites/cottages',
    kind: 'dir',
    license: 'CC-BY-SA-3.0',
    author: 'bluecarrot16 / Daniel Eddeland (daneeklu) / Lanea Zimmerman (Sharm)',
    source: 'https://opengameart.org/content/lpc-thatched-roof-cottage',
    notes: '茅草屋整屋 sprite（cottage_tan/cream/stone），scripts/build-cottages.py 从 kit 合成，世界场景建筑',
    noSync: true,
  },
  {
    // R-12 动作模组：砍树/挖矿用的超尺寸斧头叠加层（ULPC 192px attack_slash，与标准 slash 同步）。
    src: 'characters_extra/ulpc_generated',
    dest: 'sprites/weapons',
    kind: 'dir',
    license: 'CC-BY-SA-3.0',
    author: 'LPC contributors（weapon_blunt_waraxe，见 ULPC CREDITS）',
    source: 'https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator',
    notes: '斧头超尺寸挥砍叠加（weapon_axe + bg，192px oversize attack_slash），主角砍树/挖矿动作；取自 ULPC weapon_blunt_waraxe',
    noSync: true,
  },
  {
    src: 'portraits/README.md',
    dest: 'portraits',
    kind: 'dir',
    license: 'CC0-1.0 / CC-BY-4.0',
    author: 'isaiah658 / Svetlana Kushnariova ("Cabbit") / David Revoy',
    source: 'portraits/README.md',
    notes: '20 NPC 对话立绘；来自 isaiah658 CC0 与 Pepper&Carrot 24x32 CC-BY 4.0',
    noSync: true,
  },

  // ============================================================
  // === OpenGameArt 农场素材（多协议）===
  // ============================================================
  {
    src: 'opengameart_farming/marionline/tileset_farm.png',
    dest: 'vendor/opengameart/tileset_farm.png',
    kind: 'file',
    license: 'CC-BY-3.0',
    author: 'Marionline',
    source: 'https://opengameart.org/content/farm-tileset',
    notes: '农场主 tileset，BootScene tileset_farm 引用',
  },
  {
    src: 'opengameart_farming/scratchio/Cereals.png',
    dest: 'vendor/opengameart/cereals.png',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'Scratchio',
    source: 'https://opengameart.org/content/cereals',
    notes: '谷物像素图，BootScene cereals 引用（注意目标小写）',
  },
  {
    src: 'opengameart_farming/scratchio/Vegetables.png',
    dest: 'vendor/opengameart/vegetables.png',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'Scratchio',
    source: 'https://opengameart.org/content/vegetables',
    notes: '蔬菜像素图，BootScene vegetables 引用（注意目标小写）',
  },
  {
    src: 'opengameart_farming/scratchio/Crates And Sacks.png',
    dest: 'vendor/opengameart/crates_and_sacks.png',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'ScratchIO',
    source: 'https://opengameart.org/content/crates-and-sacks',
    notes: '货箱与麻袋像素图；当前 public 文件为手工集成，源页 CC0 已核对',
    noSync: true,
  },

  // ============================================================
  // === Ark Pixel 字体（OFL-1.1，TakWolf）===
  // ============================================================
  {
    src: 'fonts/ark-pixel-font/woff2/ark-pixel-10px-proportional-latin.woff2',
    dest: 'fonts/ark-pixel-10px-proportional-latin.woff2',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/ark-pixel-font',
    notes: 'Ark Pixel 10px Latin（HUD 小数字）',
  },
  {
    src: 'fonts/ark-pixel-font/woff2/ark-pixel-10px-proportional-zh_cn.woff2',
    dest: 'fonts/ark-pixel-10px-proportional-zh_cn.woff2',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/ark-pixel-font',
    notes: 'Ark Pixel 10px 简中（备用字体）',
  },
  {
    src: 'fonts/ark-pixel-font/woff2/ark-pixel-12px-proportional-latin.woff2',
    dest: 'fonts/ark-pixel-12px-proportional-latin.woff2',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/ark-pixel-font',
    notes: 'Ark Pixel 12px Latin（备用，主推 prop 系列已手工压缩入库）',
  },
  {
    src: 'fonts/ark-pixel-font/woff2/ark-pixel-12px-proportional-zh_cn.woff2',
    dest: 'fonts/ark-pixel-12px-proportional-zh_cn.woff2',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/ark-pixel-font',
    notes: 'Ark Pixel 12px 简中（备用，主推 prop 系列已手工压缩入库）',
  },
  {
    src: 'fonts/ark-pixel-font/LICENSE-OFL.txt',
    dest: 'fonts/LICENSE-ark-pixel-OFL.txt',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/ark-pixel-font',
    notes: 'Ark Pixel OFL 协议原文（OFL-1.1 强制保留）',
  },
  {
    src: 'github_extra/ark_pixel_font/OFL.txt',
    dest: 'fonts/OFL.txt',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/ark-pixel-font',
    notes: 'Ark Pixel OFL 协议原文（旧运行时路径）',
  },
  {
    src: 'github_extra/ark_pixel_font/ark-pixel-12px-monospaced-latin.ttf.woff2',
    dest: 'fonts/ark-pixel-12px-mono-latin.woff2',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/ark-pixel-font',
    notes: 'Ark Pixel 12px monospaced Latin（运行时短文件名）',
  },
  {
    src: 'github_extra/ark_pixel_font/ark-pixel-12px-monospaced-zh_cn.ttf.woff2',
    dest: 'fonts/ark-pixel-12px-mono-zh_cn.woff2',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/ark-pixel-font',
    notes: 'Ark Pixel 12px monospaced 简中（运行时短文件名）',
  },
  {
    src: 'github_extra/ark_pixel_font/ark-pixel-12px-proportional-latin.ttf.woff2',
    dest: 'fonts/ark-pixel-12px-prop-latin.woff2',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/ark-pixel-font',
    notes: 'Ark Pixel 12px proportional Latin（运行时短文件名）',
  },
  {
    src: 'github_extra/ark_pixel_font/ark-pixel-12px-proportional-zh_cn.ttf.woff2',
    dest: 'fonts/ark-pixel-12px-prop-zh_cn.woff2',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/ark-pixel-font',
    notes: 'Ark Pixel 12px proportional 简中（运行时短文件名）',
  },

  // === Fusion Pixel 字体（OFL-1.1，主推 CJK 字体）===
  {
    src: 'fonts/fusion-pixel-font/woff2/fusion-pixel-12px-proportional-zh_hans.woff2',
    dest: 'fonts/fusion-pixel-12px-proportional-zh_hans.woff2',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/fusion-pixel-font',
    notes: 'Fusion Pixel 12px 简中（主推中文像素字体，GB2312 覆盖 93.92%）',
  },
  {
    src: 'fonts/fusion-pixel-font/woff2/fusion-pixel-10px-proportional-latin.woff2',
    dest: 'fonts/fusion-pixel-10px-proportional-latin.woff2',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/fusion-pixel-font',
    notes: 'Fusion Pixel 10px Latin（HUD 数字小字号）',
  },
  {
    src: 'fonts/fusion-pixel-font/LICENSE-OFL.txt',
    dest: 'fonts/LICENSE-fusion-pixel-OFL.txt',
    kind: 'file',
    license: 'OFL-1.1',
    author: 'TakWolf',
    source: 'https://github.com/TakWolf/fusion-pixel-font',
    notes: 'Fusion Pixel OFL 协议原文',
  },

  // ============================================================
  // === BGM（CC0 / CC-BY 3.0，OpenGameArt）===
  // ============================================================
  {
    src: 'bgm/track_01.mp3',
    dest: 'bgm/track_01.mp3',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'RandomMind',
    source: 'https://opengameart.org/content/medieval-the-old-tower-inn',
    notes: '小镇白天主题',
  },
  {
    src: 'bgm/track_02.ogg',
    dest: 'bgm/track_02.ogg',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'MouthlessGames',
    source: 'https://opengameart.org/content/snowy-cozyness-calm-pianoguitar',
    notes: '学习/RAG 阅读 BGM（CC0 + CC-BY 3.0 双授权，取 CC0 路径）',
  },
  {
    src: 'bgm/track_03.ogg',
    dest: 'bgm/track_03.ogg',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'MintoDog',
    source: 'https://opengameart.org/content/cozy-puzzle-in-game-1',
    notes: '练习/Bug 矿洞 BGM',
  },
  {
    src: 'bgm/track_04.mp3',
    dest: 'bgm/track_04.mp3',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'RandomMind',
    source: 'https://opengameart.org/content/chiptune-medieval-the-old-tower-inn',
    notes: '小镇夜晚主题（chiptune 改编）',
  },
  {
    src: 'bgm/LICENSE.md',
    dest: 'bgm/LICENSE.md',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'Pythontopia inbox 审计',
    source: 'assets/inbox/bgm/LICENSE.md',
    notes: 'BGM 完整 license 与归属（运行时引用）',
  },

  // ============================================================
  // === SFX（Kenney CC0 + OGA CC0）===
  // ============================================================
  {
    src: 'sfx/click',
    dest: 'sfx/click',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/ui-audio',
    notes: 'UI 按钮点击 10 个音效',
  },
  {
    src: 'sfx/hover',
    dest: 'sfx/hover',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/ui-audio',
    notes: 'UI 按钮悬停 6 个音效',
  },
  {
    src: 'sfx/coin',
    dest: 'sfx/coin',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels / rubberduck',
    source: 'https://kenney.nl/assets/rpg-audio',
    notes: '金币奖励 4 个音效',
  },
  {
    src: 'sfx/dig',
    dest: 'sfx/dig',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/impact-sounds',
    notes: '挖矿/敲击 5 个音效',
  },
  {
    src: 'sfx/plant',
    dest: 'sfx/plant',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/impact-sounds',
    notes: '种植/放置 4 个音效',
  },
  {
    src: 'sfx/water_can',
    dest: 'sfx/water',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'rubberduck',
    source: 'https://opengameart.org/content/40-cc0-water-splash-slime-sfx',
    notes: '浇水 7 个音效（inbox 用 water_can，dest 用 water 与 BootScene 命名一致）',
  },
  {
    src: 'sfx/footstep',
    dest: 'sfx/footstep',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels / rubberduck',
    source: 'https://kenney.nl/assets/impact-sounds',
    notes: '脚步声 10 个音效',
  },
  {
    src: 'sfx/harvest_pickup',
    dest: 'sfx/harvest',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/interface-sounds',
    notes: '收获/掉落 5 个音效（重命名 harvest_pickup → harvest）',
  },
  {
    src: 'sfx/bell_chime',
    dest: 'sfx/bell',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/impact-sounds',
    notes: '收获完成铃声 6 个音效（重命名 bell_chime → bell）',
  },

  // 任务完成/升级 jingle（从 Music Jingles 包挑选代表性短乐句重命名）
  {
    src: 'sfx_extra/music-jingles/Audio/8-Bit jingles/jingles_NES15.ogg',
    dest: 'sfx/jingle-complete.ogg',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/music-jingles',
    notes: '任务完成 jingle（NES 风），BootScene sfx_jingle_complete',
  },
  {
    src: 'sfx_extra/music-jingles/Audio/8-Bit jingles/jingles_NES01.ogg',
    dest: 'sfx/jingle-level-up.ogg',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/music-jingles',
    notes: '等级提升 jingle（NES 风），BootScene sfx_jingle_levelup',
  },
  {
    src: 'sfx_extra/digital-audio/Audio/highUp.ogg',
    dest: 'sfx/success.ogg',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/digital-audio',
    notes: '答题/资源生成成功提示音，BootScene sfx_success',
  },
  {
    src: 'sfx_extra/digital-audio/Audio/highDown.ogg',
    dest: 'sfx/error.ogg',
    kind: 'file',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/digital-audio',
    notes: '答题/资源生成失败提示音，BootScene sfx_error',
  },

  // ============================================================
  // === game-icons.net KC 图标（CC-BY 3.0，强制署名）===
  // ============================================================
  {
    src: 'icons_kc',
    dest: 'icons_kc',
    kind: 'dir',
    license: 'CC-BY-3.0',
    author: 'Lorc, Delapouite, Skoll, sbed, Carl Olsen',
    source: 'https://game-icons.net',
    notes: '60+ KC 主题图标，按 Pythontopia 知识点分组',
  },

  // ============================================================
  // === Effects 粒子特效（CC0 + CC-BY 4.0，OpenGameArt）===
  // ============================================================
  {
    src: 'effects_extra/oga_codemanu_pixel_fx',
    dest: 'effects/codemanu-pixel-fx',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'CodeManu',
    source: 'https://opengameart.org/content/pixel-fx-pack',
    notes: '通用粒子特效 10 帧',
  },
  {
    src: 'effects_extra/oga_jellyfizh_confetti',
    dest: 'effects/jellyfizh-confetti',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Jellyfizh',
    source: 'https://opengameart.org/content/confetti',
    notes: '完成奖励彩纸',
  },
  {
    src: 'effects_extra/oga_jellyfizh_poof',
    dest: 'effects/jellyfizh-poof',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Jellyfizh',
    source: 'https://opengameart.org/content/poof',
    notes: '消失/淡出动画',
  },
  {
    src: 'effects_extra/oga_john2k_heal_effect',
    dest: 'effects/john2k-heal',
    kind: 'dir',
    license: 'CC-BY-4.0',
    author: 'john2k',
    source: 'https://opengameart.org/content/heal-effect',
    notes: '治疗/复习成功特效',
  },
  {
    src: 'weather_fx/LICENSE.md',
    dest: 'effects/weather',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Yevhen Babiichuk (DustDFG) / donte / alxl / Pythontopia project',
    source: 'assets/inbox/weather_fx/LICENSE.md',
    notes: '天气粒子运行时精选：云、雨滴、雪花来自已审计 CC0；confetti 为项目运行时粒子占位',
    noSync: true,
  },

  // ============================================================
  // === Kenney UI（CC0）已手工挑选成 Default/Double 子集，noSync 仅参与 LICENSE 索引 ===
  // ============================================================
  {
    src: 'pixel_ui/kenney_ui-pack-pixel-adventure',
    dest: 'ui/kenney-adventure',
    kind: 'dir',
    license: 'CC0-1.0',
    author: 'Kenney Vleugels',
    source: 'https://kenney.nl/assets/ui-pack-adventure',
    notes: '冒险风 UI 9-slice 元素，public 已挑选 Default/Double 子集，本脚本不再同步全量',
    noSync: true,
  },
];

// ---------- 命令行参数解析 ----------
/**
 * 解析 process.argv 得到运行模式。
 * @returns {{ mode: 'sync'|'check'|'dryRun', force: boolean }}
 */
function parseArgs() {
  const args = process.argv.slice(2);
  const has = (flag) => args.includes(flag);
  if (has('--check')) return { mode: 'check', force: false };
  if (has('--dry-run')) return { mode: 'dryRun', force: has('--force') };
  return { mode: 'sync', force: has('--force') };
}

// ---------- 文件系统工具 ----------
/**
 * 安全 stat：文件不存在返回 null，不抛异常。
 * @param {string} p 绝对路径
 * @returns {Promise<import('node:fs').Stats|null>}
 */
async function statSafe(p) {
  try {
    return await fs.stat(p);
  } catch (err) {
    if (err && err.code === 'ENOENT') return null;
    throw err;
  }
}

/**
 * 递归收集目录下所有满足扩展名过滤的文件，返回相对路径列表（POSIX 风格分隔）。
 * @param {string} dir 起始绝对路径
 * @param {Set<string>} allowedExts 允许的扩展名集合（含点号，全部小写）
 * @returns {Promise<string[]>}
 */
async function walkDir(dir, allowedExts) {
  /** @type {string[]} */
  const out = [];
  /**
   * @param {string} current 当前遍历的绝对路径
   * @param {string} rel 已累积的相对路径前缀
   */
  async function recurse(current, rel) {
    const entries = await fs.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const childAbs = path.join(current, entry.name);
      const childRel = rel ? `${rel}/${entry.name}` : entry.name;
      if (entry.isDirectory()) {
        await recurse(childAbs, childRel);
      } else if (entry.isFile()) {
        const ext = path.extname(entry.name).toLowerCase();
        if (allowedExts.has(ext)) {
          out.push(childRel);
        }
      }
    }
  }
  await recurse(dir, '');
  return out.sort();
}

/**
 * 复制单文件（自动 mkdir -p）。
 * @param {string} srcAbs
 * @param {string} destAbs
 */
async function copyFile(srcAbs, destAbs) {
  await fs.mkdir(path.dirname(destAbs), { recursive: true });
  await fs.copyFile(srcAbs, destAbs);
}

// ---------- 主同步流程 ----------
/**
 * 处理单个 MANIFEST 项。
 * @param {ManifestEntry} entry
 * @param {{ mode: 'sync'|'check'|'dryRun', force: boolean }} opts
 * @returns {Promise<{ copied: number, skipped: number, overwritten: number, warnings: string[], items: Array<{rel: string, action: string}> }>}
 */
async function processEntry(entry, opts) {
  const result = { copied: 0, skipped: 0, overwritten: 0, warnings: [], items: [] };

  if (entry.noSync) {
    result.items.push({ rel: entry.dest, action: 'NO-SYNC (manifest-only)' });
    return result;
  }

  const srcAbs = path.join(INBOX_ROOT, entry.src);
  const destAbs = path.join(PUBLIC_PIXEL_ROOT, entry.dest);

  const srcStat = await statSafe(srcAbs);
  if (!srcStat) {
    result.warnings.push(`[MISSING] ${entry.src} (inbox 中不存在，跳过)`);
    return result;
  }

  const declaredKind = entry.kind ?? (srcStat.isDirectory() ? 'dir' : 'file');
  if (declaredKind === 'dir' && !srcStat.isDirectory()) {
    result.warnings.push(`[KIND] ${entry.src} 声明为 dir 但实际是文件，跳过`);
    return result;
  }
  if (declaredKind === 'file' && !srcStat.isFile()) {
    result.warnings.push(`[KIND] ${entry.src} 声明为 file 但实际是目录，跳过`);
    return result;
  }

  if (declaredKind === 'file') {
    const destStat = await statSafe(destAbs);
    const exists = destStat !== null;
    if (exists && !opts.force) {
      result.skipped += 1;
      result.items.push({ rel: entry.dest, action: 'SKIP (exists)' });
      return result;
    }
    if (opts.mode === 'check') {
      result.items.push({ rel: entry.dest, action: exists ? 'WOULD-OVERWRITE' : 'WOULD-CREATE' });
      if (exists) result.overwritten += 1;
      else result.copied += 1;
      return result;
    }
    if (opts.mode === 'dryRun') {
      result.items.push({ rel: entry.dest, action: exists ? 'DRY OVERWRITE' : 'DRY CREATE' });
      if (exists) result.overwritten += 1;
      else result.copied += 1;
      return result;
    }
    await copyFile(srcAbs, destAbs);
    if (exists) {
      result.overwritten += 1;
      result.items.push({ rel: entry.dest, action: 'OVERWRITE' });
    } else {
      result.copied += 1;
      result.items.push({ rel: entry.dest, action: 'CREATE' });
    }
    return result;
  }

  // === dir 模式 ===
  const allowed = new Set(
    (entry.extensions ?? Array.from(DEFAULT_EXTENSIONS)).map((e) => e.toLowerCase()),
  );
  const files = await walkDir(srcAbs, allowed);
  for (const rel of files) {
    const fileSrc = path.join(srcAbs, rel);
    const fileDest = path.join(destAbs, rel);
    const destStat = await statSafe(fileDest);
    const exists = destStat !== null;
    const displayRel = path.posix.join(entry.dest, rel);
    if (exists && !opts.force) {
      result.skipped += 1;
      result.items.push({ rel: displayRel, action: 'SKIP (exists)' });
      continue;
    }
    if (opts.mode === 'check') {
      result.items.push({ rel: displayRel, action: exists ? 'WOULD-OVERWRITE' : 'WOULD-CREATE' });
      if (exists) result.overwritten += 1;
      else result.copied += 1;
      continue;
    }
    if (opts.mode === 'dryRun') {
      result.items.push({ rel: displayRel, action: exists ? 'DRY OVERWRITE' : 'DRY CREATE' });
      if (exists) result.overwritten += 1;
      else result.copied += 1;
      continue;
    }
    await copyFile(fileSrc, fileDest);
    if (exists) {
      result.overwritten += 1;
      result.items.push({ rel: displayRel, action: 'OVERWRITE' });
    } else {
      result.copied += 1;
      result.items.push({ rel: displayRel, action: 'CREATE' });
    }
  }
  return result;
}

// ---------- LICENSE.md 生成 ----------
/**
 * LICENSE.md 顶部固定段（项目级声明 + 必读署名清单）。运行结束后保留。
 * 索引段（== 按目录的 license 索引 ==）由本脚本根据 MANIFEST 自动重写。
 */
const LICENSE_HEADER = `# Pythontopia Web 端集成像素资产 License

> 本目录 (\`frontend/public/assets/pixel/\`) 是 Pythontopia 网页端运行所需像素资产的集成视图。
> 源仓库 \`assets/inbox/\` 是已审计的离线镜像，由 \`scripts/sync-assets.mjs\` 增量复制到此处；
> 少量自绘占位与手工挑选运行时素材以 manifest-only 方式登记。
> 全仓库资产 license 总声明见 \`/LICENSE-ART.md\`。
> 代码层 license 见仓库根 \`LICENSE\` 文件（MIT）。

## 项目级 License 分层

- **代码层**：MIT
- **资产层**：CC-BY-SA 3.0+ / OFL-1.1 / CC-BY 4.0 / CC-BY 3.0 / CC0 1.0（分文件标识）
- **SA 传染**：LPC 系列素材以 CC-BY-SA 3.0+ 传染至本项目"包装作品"层；衍生发布须同样以 SA 兼容协议公开

## 必读署名清单（CC-BY / CC-BY-SA / OFL 强制）

### game-icons.net (CC-BY 3.0)
> Game icons by **Lorc**, **Delapouite**, **Skoll**, **sbed**, **Carl Olsen** — https://game-icons.net — licensed CC BY 3.0

### LPC: Liberated Pixel Cup (CC-BY-SA 3.0+)
> Tilesets & characters from LPC project, including contributions by **bluecarrot16, Sharm, Reemax, daneeklu (Daniel Eddeland), Jetrel, J. Taylor, Ivan Voirol, TheraHedwig**.
> Source: https://opengameart.org/ — licensed CC BY-SA 3.0+
> 完整 CREDITS 文件保留在各子目录的 \`CREDITS-*.txt\` 中。

### CC-BY 4.0 contributors
- **Jana Ochse (2DPIXX)** — farming-game-basics (https://opengameart.org/content/farming-game-basics)
- **Marco L.** — pixel-lightning (https://opengameart.org/content/pixel-lightning)
- **bluecarrot16 / TheraHedwig / Ivan Voirol** — lpc-farm (https://opengameart.org/content/lpc-farm)
- **Svetlana Kushnariova ("Cabbit") / David Revoy** — Pepper&Carrot 24x32 character tribute

### OFL-1.1 字体
> Ark Pixel Font & Fusion Pixel Font by **TakWolf** — https://github.com/TakWolf — SIL OFL-1.1

### Kenney CC0（推荐致谢，非强制）
> Many UI / tile / character / SFX / icon assets by **Kenney Vleugels** (https://kenney.nl) — released as CC0 1.0.

`;

/** 索引段落开始标记 */
const INDEX_BEGIN = '<!-- AUTO-GENERATED LICENSE INDEX BEGIN -->';
/** 索引段落结束标记 */
const INDEX_END = '<!-- AUTO-GENERATED LICENSE INDEX END -->';

/**
 * 根据 MANIFEST 生成按目录的 license 索引 Markdown 表格。
 * @returns {string}
 */
function renderLicenseIndex() {
  const rows = MANIFEST.map((entry) => {
    const target = `\`${entry.dest}\``;
    const author = entry.author ?? '—';
    const source = entry.source ? `[link](${entry.source})` : '—';
    const noteParts = [];
    if (entry.noSync) noteParts.push('🛈 manifest-only（已手工挑选，脚本不同步）');
    if (entry.notes) noteParts.push(entry.notes);
    const notes = noteParts.join('；').replace(/\|/g, '\\|');
    return `| ${target} | ${entry.license} | ${author} | ${source} | ${notes} |`;
  });
  return [
    '## 按目录的 License 索引（由 `scripts/sync-assets.mjs` 自动生成，请勿手工编辑）',
    '',
    '| 目标路径 (dest) | License | 作者 | 来源 | 备注 |',
    '| --- | --- | --- | --- | --- |',
    ...rows,
    '',
    '> 编辑方式：修改 `scripts/sync-assets.mjs` 的 MANIFEST 数组后重新运行 `node scripts/sync-assets.mjs`。',
  ].join('\n');
}

/**
 * 把项目级声明 + 自动索引拼接为完整 LICENSE.md 文本。
 * @returns {string}
 */
function renderFullLicenseMarkdown() {
  return `${LICENSE_HEADER}\n${INDEX_BEGIN}\n${renderLicenseIndex()}\n${INDEX_END}\n`;
}

/**
 * 写入或更新 frontend/public/assets/pixel/LICENSE.md。
 * 如果已有相同内容则不动；否则覆盖。
 * @param {{ mode: 'sync'|'check'|'dryRun', force: boolean }} opts
 * @returns {Promise<'created'|'updated'|'unchanged'|'skipped'>}
 */
async function writeLicenseMarkdown(opts) {
  const dest = path.join(PUBLIC_PIXEL_ROOT, 'LICENSE.md');
  const next = renderFullLicenseMarkdown();
  const prev = (await statSafe(dest)) ? await fs.readFile(dest, 'utf8') : null;
  if (prev === next) return 'unchanged';
  if (opts.mode === 'check' || opts.mode === 'dryRun') return 'skipped';
  await fs.mkdir(path.dirname(dest), { recursive: true });
  await fs.writeFile(dest, next, 'utf8');
  return prev === null ? 'created' : 'updated';
}

// ---------- 入口 ----------
/**
 * 主入口：迭代 MANIFEST，汇总统计并打印报告。
 */
async function main() {
  const opts = parseArgs();
  const modeLabel = {
    sync: '增量同步（仅复制目标不存在的文件）',
    check: '仅检查（不复制）',
    dryRun: '干运行（打印将要执行的操作但不真复制）',
  }[opts.mode];
  console.log(`[sync-assets] 模式: ${modeLabel}${opts.force ? '  + 强制覆盖' : ''}`);
  console.log(`[sync-assets] inbox 源: ${INBOX_ROOT}`);
  console.log(`[sync-assets] public 目标: ${PUBLIC_PIXEL_ROOT}`);
  console.log('');

  let totalCopied = 0;
  let totalSkipped = 0;
  let totalOverwritten = 0;
  /** @type {string[]} */
  const allWarnings = [];

  for (const entry of MANIFEST) {
    const r = await processEntry(entry, opts);
    totalCopied += r.copied;
    totalSkipped += r.skipped;
    totalOverwritten += r.overwritten;
    allWarnings.push(...r.warnings);
    const tag = `[${entry.dest}]`.padEnd(48, ' ');
    console.log(
      `${tag} +${r.copied}  ~${r.overwritten}  =${r.skipped}` +
        (r.warnings.length ? `  ⚠ ${r.warnings.length}` : ''),
    );
    for (const w of r.warnings) console.log(`  ${w}`);
  }

  const licenseStatus = await writeLicenseMarkdown(opts);
  console.log('');
  console.log(`[LICENSE.md] ${licenseStatus}`);

  console.log('');
  console.log('========== 汇总 ==========');
  console.log(`模式:           ${modeLabel}`);
  console.log(`MANIFEST 条目: ${MANIFEST.length}`);
  console.log(`新增文件:       ${totalCopied}`);
  console.log(`已覆盖:         ${totalOverwritten}`);
  console.log(`已跳过:         ${totalSkipped}`);
  console.log(`警告:           ${allWarnings.length}`);
  if (allWarnings.length > 0) {
    console.log('--- 警告明细 ---');
    for (const w of allWarnings) console.log(`  ${w}`);
  }
  console.log('=========================');
}

main().catch((err) => {
  console.error('[sync-assets] 运行失败:', err);
  process.exitCode = 1;
});
