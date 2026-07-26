/**
 * 无尽远征「像素远征塔」Phaser 场景（R-14「看→学→练→测」之「练」可视化）。
 *
 * 把 rogueRun 的分层分支 DAG（nodesByLayer）渲染成一座可见的像素秘境塔：底层在下、BOSS 在顶，
 * avatar 随通关向上攀爬；节点 = 房间（按 kind 上不同像素色/图标），按 state 点亮/高亮/暗/灰，
 * 过 BOSS 触发庆祝。DOM 地图（EndlessExpeditionPage 的 .exp-map）保留为交互真源 + 移动端/降级，
 * 本场景为渐进增强（仅在已有 Phaser 单例时驱动，参考 CodeTheaterPage 的写法）。
 *
 * 架构隔离（AGENTS.md / 契约红线「Phaser 动态对象隔离、单 Game 实例」）：
 *   - 全部用 Phaser graphics + text 程序化绘制，不加载任何第三方像素二进制素材（同 CodeTheaterScene），
 *     因此不引入新的 CC-BY/CC0 资产，无 LICENSE-ART 变更。
 *   - 沿用项目「plain-object scene + *-Like 接口」约定（同 BootScene/WorldScene/CodeTheaterScene），
 *     不 `new Phaser.Game`、不直接 import phaser 运行时；经现有单例（phaserGame.ts）的 scene manager 挂载。
 *   - 模块级桥（同 CodeTheaterScene）：Vue 组件通过 loadExpedition() 喂入地图 + run 视图并触发重建；
 *     getExpeditionBridge().selectNode(id) 把像素节点点击回流到 run.enterNode（与 DOM 地图同一动作）。
 */
import type { RogueMap, RogueNode, RogueNodeKind, RogueRunPhase } from '@/types/rogue';
import { NODE_KIND_ICON, NODE_KIND_LABEL } from '@/data/rogueConfig';

export const EXPEDITION_SCENE_KEY = 'ExpeditionScene';

const DEFAULT_WIDTH = 960;
const DEFAULT_HEIGHT = 640;

const COLOR_BACKDROP = 0xf7f1dc;
const COLOR_BACKDROP_TOP = 0xe4f3ff;
const COLOR_COLUMN = 0xe8edd9;
const COLOR_FLOOR = 0xc8d7b8;
const COLOR_EDGE_OPEN = 0xd99f3a;
const COLOR_EDGE_DIM = 0xb7c3ac;
const COLOR_AVATAR = 0x2f8f6f;

/** 每种节点房间的主色（浅色小镇调色板）。 */
const KIND_COLOR: Record<RogueNodeKind, number> = {
  battle: 0x73a7e8,
  elite: 0x9b8bd9,
  event: 0xf2c85a,
  bug: 0xe9786b,
  chest: 0xe6a15b,
  rest: 0x7acb8a,
  boss: 0xe0b847,
};

const PIXEL_FONT = '"Zpix", "Ark Pixel 12", monospace';

// 塔身内边距（layout 与房间尺寸共用，保证两者一致；标题/BOSS 顶幅留在 TOP_PAD 内）。
const TOP_PAD = 70;
const BOTTOM_PAD = 58;
const LEFT_PAD = 92;
const RIGHT_PAD = 36;

/** 本场景所需的最小 Phaser 上下文（隔离不外泄；与 CodeTheaterSceneContext 同风格）。 */
export interface ExpeditionSceneContext {
  add: {
    graphics(): GraphicsLike;
    text(x: number, y: number, text: string, style?: Record<string, unknown>): TextLike;
  };
  tweens?: {
    add(config: Record<string, unknown>): unknown;
    killTweensOf(target: unknown): unknown;
  };
  scale?: {
    width: number;
    height: number;
    on(event: string, cb: (...args: unknown[]) => void): void;
    off?(event: string, cb: (...args: unknown[]) => void): void;
  };
  time?: { now: number };
  events?: { on(event: string, cb: () => void): void };
  children?: { removeAll(destroyChild?: boolean): void };
}

interface GraphicsLike {
  clear(): GraphicsLike;
  fillStyle(color: number, alpha?: number): GraphicsLike;
  fillRect(x: number, y: number, w: number, h: number): GraphicsLike;
  fillRoundedRect(x: number, y: number, w: number, h: number, radius?: number): GraphicsLike;
  lineStyle(width: number, color: number, alpha?: number): GraphicsLike;
  lineBetween(x1: number, y1: number, x2: number, y2: number): GraphicsLike;
  strokeRoundedRect(x: number, y: number, w: number, h: number, radius?: number): GraphicsLike;
  setDepth(depth: number): GraphicsLike;
  setAlpha(alpha: number): GraphicsLike;
  destroy(): void;
}

interface TextLike {
  setOrigin(x: number, y?: number): TextLike;
  setDepth(depth: number): TextLike;
  setColor(color: string): TextLike;
  setAlpha(alpha: number): TextLike;
  setInteractive(config?: unknown): TextLike;
  on(event: string, cb: (...args: unknown[]) => void): TextLike;
  destroy(): void;
}

/** run 视图（仅渲染需要的字段；不含题目/判题，保持场景纯展示）。 */
export interface ExpeditionRunView {
  currentNodeId: string | null;
  phase: RogueRunPhase;
  hp: number;
  maxHp: number;
  score: number;
}

export interface ExpeditionLoadOptions {
  /** 像素节点点击回调（available 节点才触发）；通常接到 run.enterNode。 */
  onNodeClick?: (id: string) => void;
}

const DEFAULT_VIEW: ExpeditionRunView = {
  currentNodeId: null,
  phase: 'map',
  hp: 0,
  maxHp: 0,
  score: 0,
};

// ── 模块级桥（场景实例无法被 Vue 直接持有，沿用 CodeTheaterScene 的中转模式） ──
let currentMap: RogueMap | null = null;
let runView: ExpeditionRunView = { ...DEFAULT_VIEW };
let onNodeClick: ((id: string) => void) | null = null;
let liveScene: ExpeditionSceneContext | null = null;

interface NodePos {
  node: RogueNode;
  x: number;
  y: number;
}

const bridge = {
  /** 像素节点选择：仅 available 节点可进入，回流到 onNodeClick（与 DOM 地图同一动作）。 */
  selectNode(id: string): void {
    const node = currentMap?.nodes.find((n) => n.id === id);
    if (!node || node.state !== 'available') {
      return;
    }
    onNodeClick?.(id);
  },
};

/** 对外控制接口：像素节点点击回流桥。 */
export function getExpeditionBridge(): typeof bridge {
  return bridge;
}

/** 喂入一张地图 + run 视图（+ 可选点击回调），若场景已就绪立即重建塔。 */
export function loadExpedition(
  map: RogueMap,
  view: Partial<ExpeditionRunView>,
  options?: ExpeditionLoadOptions,
): void {
  currentMap = map;
  runView = { ...DEFAULT_VIEW, ...view };
  if (options && options.onNodeClick !== undefined) {
    onNodeClick = options.onNodeClick;
  }
  if (liveScene) {
    rebuildTower(liveScene);
  }
}

/** 仅供测试/调试：重置模块级桥（避免跨用例泄漏）。 */
export function __resetExpeditionForTests(): void {
  currentMap = null;
  runView = { ...DEFAULT_VIEW };
  onNodeClick = null;
  liveScene = null;
}

function readDimensions(ctx: ExpeditionSceneContext): { width: number; height: number } {
  return {
    width: ctx.scale?.width ?? DEFAULT_WIDTH,
    height: ctx.scale?.height ?? DEFAULT_HEIGHT,
  };
}

/** 按层分组（layer 0 在最下，最高层 = BOSS 在顶），层内按 col 排序。 */
function groupByLayer(map: RogueMap): RogueNode[][] {
  const rows: RogueNode[][] = Array.from({ length: map.layers }, () => []);
  for (const node of map.nodes) {
    (rows[node.layer] ??= []).push(node);
  }
  return rows.map((row) => [...row].sort((a, b) => a.col - b.col));
}

/** 计算每个节点在塔内的像素坐标（底层在下、顶层在上）。 */
function layoutNodes(map: RogueMap, width: number, height: number): Map<string, NodePos> {
  const rows = groupByLayer(map);
  const positions = new Map<string, NodePos>();
  const usableH = Math.max(1, height - TOP_PAD - BOTTOM_PAD);
  const usableW = Math.max(1, width - LEFT_PAD - RIGHT_PAD);
  const denom = Math.max(1, map.layers - 1);
  for (let layer = 0; layer < rows.length; layer += 1) {
    const row = rows[layer];
    if (!row || row.length === 0) continue;
    const y = height - BOTTOM_PAD - (layer / denom) * usableH;
    for (let i = 0; i < row.length; i += 1) {
      const node = row[i];
      if (!node) continue;
      const x = LEFT_PAD + ((i + 0.5) / row.length) * usableW;
      positions.set(node.id, { node, x, y });
    }
  }
  return positions;
}

/** 房间尺寸随层数 / 层宽自适应，保证整座塔（含 BOSS + 起点层）一屏内不重叠。 */
function computeRoomSize(map: RogueMap, width: number, height: number): { w: number; h: number } {
  const rows = groupByLayer(map);
  const maxRow = Math.max(1, ...rows.map((r) => r.length));
  const usableH = Math.max(1, height - TOP_PAD - BOTTOM_PAD);
  const usableW = Math.max(1, width - LEFT_PAD - RIGHT_PAD);
  const rowGap = usableH / Math.max(1, map.layers - 1);
  const colGap = usableW / maxRow;
  const h = Math.max(28, Math.min(52, Math.round(rowGap - 10)));
  const w = Math.max(78, Math.min(124, Math.round(colGap - 14)));
  return { w, h };
}

function roomColors(node: RogueNode): { fill: number; fillAlpha: number; border: number; textAlpha: number } {
  const base = KIND_COLOR[node.kind];
  switch (node.state) {
    case 'cleared':
      return { fill: base, fillAlpha: 0.92, border: 0x8df0b0, textAlpha: 1 };
    case 'available':
    case 'current':
      return { fill: base, fillAlpha: 0.85, border: 0xffe08a, textAlpha: 1 };
    case 'bypassed':
      return { fill: 0xd4cec0, fillAlpha: 0.58, border: 0xb6ad98, textAlpha: 0.42 };
    case 'locked':
    default:
      return { fill: 0xe3dfd2, fillAlpha: 0.86, border: 0xb6ad98, textAlpha: 0.55 };
  }
}

/** （重）构建整座塔：清空旧对象 → 背景/楼层 → 连线 → 房间 → avatar → HUD → 庆祝。 */
function rebuildTower(ctx: ExpeditionSceneContext): void {
  ctx.children?.removeAll(true);
  ctx.tweens?.killTweensOf?.({});

  const { width, height } = readDimensions(ctx);

  // 背景：浅天空 + 纸面底色，和 town_hub 的温暖学习面板统一。
  const bg = ctx.add.graphics();
  bg.fillStyle(COLOR_BACKDROP, 1).fillRect(0, 0, width, height);
  bg.fillStyle(COLOR_BACKDROP_TOP, 0.72).fillRect(0, 0, width, height * 0.42);
  bg.setDepth(0);

  // 标题（测试断言含「无尽远征」）。
  ctx.add
    .text(width / 2, 26, '无尽远征塔', {
      fontFamily: PIXEL_FONT,
      fontSize: '18px',
      color: '#5d4724',
    })
    .setOrigin(0.5, 0.5)
    .setDepth(6);

  const map = currentMap;
  if (!map || map.nodes.length === 0) {
    ctx.add
      .text(width / 2, height / 2, '点「开始远征」生成你的专属知识塔', {
        fontFamily: PIXEL_FONT,
        fontSize: '14px',
        color: '#6d6a5c',
      })
      .setOrigin(0.5, 0.5)
      .setDepth(6);
    return;
  }

  const positions = layoutNodes(map, width, height);
  const room = computeRoomSize(map, width, height);

  // 中央塔身柱（垫底装饰）。
  const column = ctx.add.graphics();
  column.fillStyle(COLOR_COLUMN, 0.72).fillRoundedRect(72, 52, width - 112, height - 104, 18);
  column.setDepth(0);

  // 楼层地板线 + 层标签（顶层标 BOSS）。
  const floors = ctx.add.graphics();
  floors.setDepth(1);
  const rows = groupByLayer(map);
  for (let layer = 0; layer < rows.length; layer += 1) {
    const row = rows[layer];
    const sample = row?.[0];
    if (!sample) continue;
    const pos = positions.get(sample.id);
    if (!pos) continue;
    floors.fillStyle(COLOR_FLOOR, 0.58).fillRect(80, pos.y + room.h / 2 + 7, width - 128, 2);
    const isTop = layer === map.layers - 1;
    ctx.add
      .text(20, pos.y, isTop ? '顶层' : `${layer + 1}F`, {
        fontFamily: PIXEL_FONT,
        fontSize: '11px',
        color: isTop ? '#7b5d1e' : '#7c6b4e',
      })
      .setOrigin(0, 0.5)
      .setDepth(2);
  }

  // 连线（出边）：起点已通关且终点可达 → 点亮金线，否则暗虚线。
  const edges = ctx.add.graphics();
  edges.setDepth(1);
  for (const node of map.nodes) {
    const from = positions.get(node.id);
    if (!from) continue;
    for (const nid of node.next) {
      const to = positions.get(nid);
      if (!to) continue;
      const target = map.nodes.find((n) => n.id === nid);
      const open =
        node.state === 'cleared' &&
        (target?.state === 'cleared' || target?.state === 'available' || target?.state === 'current');
      edges.lineStyle(open ? 3 : 2, open ? COLOR_EDGE_OPEN : COLOR_EDGE_DIM, open ? 0.9 : 0.5);
      edges.lineBetween(from.x, from.y, to.x, to.y);
    }
  }

  // 房间 + 标签（尺寸自适应，整座塔一屏内不重叠）。
  for (const pos of positions.values()) {
    drawRoom(ctx, pos, room.w, room.h);
  }

  // avatar：定位到当前节点（随通关向上攀爬），带轻微脉冲。
  drawAvatar(ctx, positions, room.w);

  // HUD：体力 / 得分。
  drawHud(ctx, width);

  // 过 BOSS 庆祝（测试断言含「通关」）。
  if (runView.phase === 'cleared') {
    drawVictory(ctx, width, height);
  } else if (runView.phase === 'over') {
    drawGameOver(ctx, width, height);
  }
}

function drawRoom(ctx: ExpeditionSceneContext, pos: NodePos, roomW: number, roomH: number): void {
  const { node, x, y } = pos;
  const { fill, fillAlpha, border, textAlpha } = roomColors(node);
  const left = x - roomW / 2;
  const top = y - roomH / 2;
  const isBoss = node.kind === 'boss';

  const g = ctx.add.graphics();
  g.fillStyle(fill, fillAlpha).fillRoundedRect(left, top, roomW, roomH, 8);
  g.lineStyle(isBoss ? 4 : 2, border, 1).strokeRoundedRect(left, top, roomW, roomH, 8);
  g.setDepth(2);

  const labelFont = roomH >= 44 ? '12px' : '11px';
  const icon = NODE_KIND_ICON[node.kind];
  const label = ctx.add
    .text(x, top + roomH * 0.34, `${icon} ${shortLabel(node)}`, {
      fontFamily: PIXEL_FONT,
      fontSize: isBoss ? '13px' : labelFont,
      color: '#1a1326',
    })
    .setOrigin(0.5, 0.5)
    .setDepth(3)
    .setColor(textAlpha >= 1 ? '#1a1326' : '#cfc6e6')
    .setAlpha(textAlpha);

  // available 节点可点击：像素点击回流到 run.enterNode（与 DOM 地图同一动作）。
  if (node.state === 'available') {
    label.setInteractive({ useHandCursor: true }).on('pointerdown', () => bridge.selectNode(node.id));
  }

  // 难度/KC 副标。
  const sub = node.kcCode
    ? `${'★'.repeat(Math.max(0, node.difficulty))}`
    : node.kind === 'rest'
      ? '回血'
      : '资源';
  ctx.add
    .text(x, top + roomH * 0.72, sub, {
      fontFamily: PIXEL_FONT,
      fontSize: '10px',
      color: '#2a2142',
    })
    .setOrigin(0.5, 0.5)
    .setDepth(3)
    .setColor(textAlpha >= 1 ? '#4f583d' : '#8b8676')
    .setAlpha(textAlpha);

  // 已通关打勾。
  if (node.state === 'cleared') {
    ctx.add
      .text(left + roomW - 11, top + 9, '✓', {
        fontFamily: PIXEL_FONT,
        fontSize: '13px',
        color: '#1f7a44',
      })
      .setOrigin(0.5, 0.5)
      .setDepth(4);
  }
}

function shortLabel(node: RogueNode): string {
  const label = NODE_KIND_LABEL[node.kind];
  // 取「·」后的精简名（如「知识闸 · BOSS」→「BOSS」），无则用全名。
  const parts = label.split('·').map((p) => p.trim());
  return parts[parts.length - 1] || label;
}

function drawAvatar(
  ctx: ExpeditionSceneContext,
  positions: Map<string, NodePos>,
  roomW: number,
): void {
  const id = runView.currentNodeId;
  if (!id) return;
  const pos = positions.get(id);
  if (!pos) return;
  const ax = pos.x - roomW / 2 - 15;
  const ay = pos.y;
  const marker = ctx.add.graphics();
  marker.fillStyle(COLOR_AVATAR, 1).fillRoundedRect(ax - 9, ay - 9, 18, 18, 6);
  marker.lineStyle(2, 0x1f5d49, 1).strokeRoundedRect(ax - 9, ay - 9, 18, 18, 6);
  marker.setDepth(5);
  ctx.add
    .text(ax, ay - 22, '你', {
      fontFamily: PIXEL_FONT,
      fontSize: '11px',
      color: '#2a4d3a',
    })
    .setOrigin(0.5, 0.5)
    .setDepth(5);
  ctx.tweens?.add({
    targets: marker,
    alpha: { from: 1, to: 0.55 },
    duration: 720,
    yoyo: true,
    repeat: -1,
  });
}

function drawHud(ctx: ExpeditionSceneContext, width: number): void {
  if (runView.maxHp <= 0) return;
  const hearts = '♥'.repeat(Math.max(0, runView.hp)) + '♡'.repeat(Math.max(0, runView.maxHp - runView.hp));
  ctx.add
    .text(width - 16, 20, `体力 ${hearts}`, {
      fontFamily: PIXEL_FONT,
      fontSize: '13px',
      color: '#b53b42',
    })
    .setOrigin(1, 0.5)
    .setDepth(6);
  ctx.add
    .text(width - 16, 40, `得分 ${runView.score}`, {
      fontFamily: PIXEL_FONT,
      fontSize: '12px',
      color: '#7b5d1e',
    })
    .setOrigin(1, 0.5)
    .setDepth(6);
}

function drawVictory(ctx: ExpeditionSceneContext, width: number, height: number): void {
  const veil = ctx.add.graphics();
  veil.fillStyle(0xfaf3dd, 0.86).fillRect(0, 0, width, height);
  veil.setDepth(9);
  ctx.add
    .text(width / 2, height / 2, '🎉 通关！知识闸已击穿', {
      fontFamily: PIXEL_FONT,
      fontSize: '22px',
      color: '#2f8f6f',
    })
    .setOrigin(0.5, 0.5)
    .setDepth(10);
}

function drawGameOver(ctx: ExpeditionSceneContext, width: number, height: number): void {
  const veil = ctx.add.graphics();
  veil.fillStyle(0xfaf3dd, 0.86).fillRect(0, 0, width, height);
  veil.setDepth(9);
  ctx.add
    .text(width / 2, height / 2, '本局远征结束 · 掌握度已入库', {
      fontFamily: PIXEL_FONT,
      fontSize: '18px',
      color: '#b05348',
    })
    .setOrigin(0.5, 0.5)
    .setDepth(10);
}

/** Phaser 场景配置（plain object，沿用项目对象式声明）。 */
export const ExpeditionScene = {
  key: EXPEDITION_SCENE_KEY,
  create(this: ExpeditionSceneContext): void {
    // eslint-disable-next-line @typescript-eslint/no-this-alias -- 缓存场景上下文供模块级桥重建；shutdown 清除。
    liveScene = this;
    rebuildTower(this);

    // resize 监听按「场景实例」绑定/解绑：模块级 resizeBound 布尔跨 Phaser.Game
    // 实例残留（destroy 不复位），第 2 次进页时新实例上不再绑 resize，重排失效。
    const scale = this.scale;
    const onResize = (): void => {
      if (liveScene) rebuildTower(liveScene);
    };
    scale?.on('resize', onResize);

    this.events?.on('shutdown', () => {
      scale?.off?.('resize', onResize);
      if (liveScene === this) {
        liveScene = null;
      }
    });
  },
};

// ── 经现有 Phaser 单例挂载本场景（隔离 scene manager 调用，全程 guard，桩环境安全 no-op） ──

/** 最小 game-like，仅覆盖本模块用到的 scene manager API（隔离 phaser 类型）。 */
export interface ExpeditionGameLike {
  scene?: {
    keys?: Record<string, unknown>;
    getScene?(key: string): unknown;
    add?(key: string, sceneConfig: unknown, autoStart?: boolean, data?: unknown): unknown;
    start?(key: string, data?: unknown): unknown;
    stop?(key: string): unknown;
    bringToTop?(key: string): unknown;
  };
}

function sceneRegistered(mgr: NonNullable<ExpeditionGameLike['scene']>): boolean {
  if (mgr.keys && EXPEDITION_SCENE_KEY in mgr.keys) return true;
  if (typeof mgr.getScene === 'function') {
    try {
      return Boolean(mgr.getScene(EXPEDITION_SCENE_KEY));
    } catch {
      return false;
    }
  }
  return false;
}

/**
 * 确保 ExpeditionScene 已在给定 Phaser game 上注册并启动（复用现有单例，不 new Game）。
 * 全部 guard：桩 / MockGame（无 add/start）下安全 no-op，便于单测与降级。
 */
export function ensureExpeditionSceneStarted(game: ExpeditionGameLike | null): void {
  const mgr = game?.scene;
  if (!mgr) return;
  if (!sceneRegistered(mgr)) {
    if (typeof mgr.add === 'function') {
      mgr.add(EXPEDITION_SCENE_KEY, ExpeditionScene, true);
    }
  } else if (typeof mgr.start === 'function') {
    mgr.start(EXPEDITION_SCENE_KEY);
  }
  if (typeof mgr.bringToTop === 'function') {
    mgr.bringToTop(EXPEDITION_SCENE_KEY);
  }
}

/** 离开远征页时停掉本场景（guard）。 */
export function stopExpeditionScene(game: ExpeditionGameLike | null): void {
  const mgr = game?.scene;
  if (typeof mgr?.stop === 'function') {
    mgr.stop(EXPEDITION_SCENE_KEY);
  }
}
