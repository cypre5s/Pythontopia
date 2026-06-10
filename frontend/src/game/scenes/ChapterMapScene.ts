// R-15 关卡地图 Phaser 场景（羊皮卷像素地图，星露谷风）。
//
// 隔离约定（AGENTS.md「Phaser 动态对象隔离在 adapter/场景层」）：本文件不向业务层暴露任何
// Phaser 类型——对外只输出「纯数据 + 纯函数 + 一组最小 *-Like 接口」。Vue 页面通过
// `setChapterMapData(nodes)` 喂入纯数据、用 `onChapterMapSelect(cb)` 接收节点点击，
// 用 `ensureChapterMapSceneStarted(game)` 经现有 Phaser 单例（phaserGame.ts）挂载本场景，
// 全程不 new Phaser.Game、不直接 import phaser。
//
// 视觉：羊皮卷底 + 像素小路串联 7 个章节节点；节点三态用不同颜色 + 星牌匾呈现。
// 颜色用数值 hex（Phaser canvas 只能吃数值，无法读 CSS 变量），数值刻意对齐 tokens.css
// 的 --pyt-color-* 取值，保持与 Vue 层视觉一致（见各常量注释）。

export const CHAPTER_MAP_SCENE_KEY = 'ChapterMapScene';

/** 关卡地图节点三态。 */
export type ChapterMapNodeState = 'locked' | 'in-progress' | 'cleared';

/** 喂给场景的纯节点数据（由页面从 overview 派生，场景不感知 store / DTO）。 */
export interface ChapterMapNode {
  chapter: number;
  title: string;
  state: ChapterMapNodeState;
  stars: number;
}

// ── 调色板（数值 hex，对齐 tokens.css；canvas 专用） ──
const COLOR_PARCHMENT = 0xfdf3d0; // --pyt-color-bg-soft
const COLOR_PARCHMENT_EDGE = 0xc9a86b; // --pyt-color-border
const COLOR_PATH = 0x8a6c2c; // --pyt-color-text-muted
const COLOR_TEXT = 0x4d3613; // --pyt-color-text-primary
const COLOR_NODE_LOCKED = 0xc9a86b; // 锁定灰金 --pyt-color-border
const COLOR_NODE_IN_PROGRESS = 0xf1c453; // 进行中金 --pyt-color-gold
const COLOR_NODE_CLEARED = 0x2f8f3c; // 已通关绿 --pyt-color-success
const COLOR_NODE_STROKE = 0x4d3613; // --pyt-color-border-strong

const DEPTH_BG = 0;
const DEPTH_PATH = 1;
const DEPTH_NODE = 2;
const DEPTH_LABEL = 3;

/** 节点选中事件名（场景 → 页面，沿用项目 window CustomEvent 习惯）。 */
export const CHAPTER_MAP_SELECT_EVENT = 'pythontopia:mainline-node-select';

// ── 最小 Phaser 接口（仅本场景渲染所需，隔离不外泄） ──
interface MapGraphicsLike {
  fillStyle(color: number, alpha?: number): MapGraphicsLike;
  fillRect(x: number, y: number, w: number, h: number): MapGraphicsLike;
  lineStyle(width: number, color: number, alpha?: number): MapGraphicsLike;
  lineBetween(x1: number, y1: number, x2: number, y2: number): MapGraphicsLike;
  strokeRect(x: number, y: number, w: number, h: number): MapGraphicsLike;
  fillCircle(x: number, y: number, radius: number): MapGraphicsLike;
  clear(): MapGraphicsLike;
  setDepth?(depth: number): MapGraphicsLike;
}
interface MapTextLike {
  setOrigin(x: number, y?: number): MapTextLike;
  setDepth?(depth: number): MapTextLike;
}
interface MapRectLike {
  setStrokeStyle(width: number, color: number, alpha?: number): MapRectLike;
  setInteractive(config?: unknown): MapRectLike;
  on(event: string, callback: () => void): MapRectLike;
  setDepth?(depth: number): MapRectLike;
}
interface MapAddLike {
  graphics(): MapGraphicsLike;
  text(x: number, y: number, text: string, style?: Record<string, unknown>): MapTextLike;
  rectangle(
    x: number,
    y: number,
    width: number,
    height: number,
    fillColor?: number,
    fillAlpha?: number,
  ): MapRectLike;
}
interface ChapterMapSceneContext {
  add: MapAddLike;
  scale?: { width: number; height: number };
  children?: { removeAll(destroyChild?: boolean): void };
  events?: { on(event: string, callback: () => void): void };
}

// ── 模块级数据桥（场景实例无法被 Vue 直接持有，故用模块级中转） ──
let currentNodes: ChapterMapNode[] = [];
let liveScene: ChapterMapSceneContext | null = null;
let selectHandler: ((chapter: number) => void) | null = null;

/** 喂入/更新地图节点数据；若场景已就绪则立刻重绘。 */
export function setChapterMapData(nodes: ChapterMapNode[]): void {
  currentNodes = nodes;
  if (liveScene) {
    renderChapterMap(liveScene, currentNodes);
  }
}

/** 注册节点点击回调（页面用它同步选中章节）。传 null 解绑。 */
export function onChapterMapSelect(callback: ((chapter: number) => void) | null): void {
  selectHandler = callback;
}

/** 仅供测试 / 调试：读取当前缓存节点。 */
export function getChapterMapData(): ChapterMapNode[] {
  return currentNodes;
}

/** 仅供测试：重置模块级桥接状态，避免跨用例泄漏。 */
export function __resetChapterMapBridgeForTests(): void {
  currentNodes = [];
  liveScene = null;
  selectHandler = null;
}

// ── 纯函数（可单测） ──

/** 章节节点底色（数值 hex，canvas 用）。 */
export function nodeFillColor(state: ChapterMapNodeState): number {
  switch (state) {
    case 'cleared':
      return COLOR_NODE_CLEARED;
    case 'in-progress':
      return COLOR_NODE_IN_PROGRESS;
    default:
      return COLOR_NODE_LOCKED;
  }
}

/** 星牌匾文本（实心 ★ + 空心 ☆，上限 3）。 */
export function starString(stars: number, max = 3): string {
  const filled = Math.max(0, Math.min(max, Math.floor(Number.isFinite(stars) ? stars : 0)));
  return '★'.repeat(filled) + '☆'.repeat(max - filled);
}

/**
 * 计算 N 个章节节点在画布上的「蜿蜒小路」坐标（像素）。
 * 横向均匀铺开 + 正弦起伏，营造星露谷地图蜿蜒感；全部落在 padding 安全框内。
 */
export function computeNodePositions(
  count: number,
  width: number,
  height: number,
  padding = 64,
): Array<{ x: number; y: number }> {
  if (count <= 0) return [];
  const usableW = Math.max(0, width - padding * 2);
  const usableH = Math.max(0, height - padding * 2);
  const midY = padding + usableH / 2;
  const amplitude = usableH * 0.32;
  const positions: Array<{ x: number; y: number }> = [];
  for (let i = 0; i < count; i += 1) {
    const t = count === 1 ? 0.5 : i / (count - 1);
    const x = padding + usableW * t;
    const y = midY + amplitude * Math.sin(i * 0.9);
    positions.push({
      x: Math.round(x),
      y: Math.round(Math.max(padding, Math.min(height - padding, y))),
    });
  }
  return positions;
}

function emitSelect(chapter: number): void {
  selectHandler?.(chapter);
  if (typeof window !== 'undefined') {
    try {
      window.dispatchEvent(
        new CustomEvent(CHAPTER_MAP_SELECT_EVENT, { detail: { chapter } }),
      );
    } catch {
      // jsdom / 非浏览器环境无 CustomEvent：忽略，selectHandler 已兜底。
    }
  }
}

/** 用给定 add 工厂把整张地图画出来（场景 create 与重绘共用，可单测）。 */
export function renderChapterMap(ctx: ChapterMapSceneContext, nodes: ChapterMapNode[]): void {
  ctx.children?.removeAll(true);
  const width = ctx.scale?.width ?? 960;
  const height = ctx.scale?.height ?? 640;

  // 羊皮卷底 + 描边。
  const bg = ctx.add.graphics();
  bg.fillStyle(COLOR_PARCHMENT, 1).fillRect(0, 0, width, height);
  bg.lineStyle(6, COLOR_PARCHMENT_EDGE, 1).strokeRect(8, 8, width - 16, height - 16);
  bg.setDepth?.(DEPTH_BG);

  const positions = computeNodePositions(nodes.length, width, height);

  // 像素小路：依次连接相邻节点。
  if (positions.length >= 2) {
    const path = ctx.add.graphics();
    path.lineStyle(6, COLOR_PATH, 0.9);
    for (let i = 1; i < positions.length; i += 1) {
      const a = positions[i - 1]!;
      const b = positions[i]!;
      path.lineBetween(a.x, a.y, b.x, b.y);
    }
    path.setDepth?.(DEPTH_PATH);
  }

  // 节点圆点 + 命中矩形 + 文本（章号 / 标题 / 星牌匾）。
  const radius = 26;
  nodes.forEach((node, i) => {
    const pos = positions[i];
    if (!pos) return;

    const dot = ctx.add.graphics();
    dot.fillStyle(nodeFillColor(node.state), 1).fillCircle(pos.x, pos.y, radius);
    dot.lineStyle(3, COLOR_NODE_STROKE, 1);
    dot.setDepth?.(DEPTH_NODE);

    const hit = ctx.add.rectangle(pos.x, pos.y, radius * 2 + 12, radius * 2 + 12, 0xffffff, 0.001);
    hit.setStrokeStyle(0, COLOR_NODE_STROKE, 0);
    hit.setInteractive({ useHandCursor: true });
    hit.on('pointerup', () => emitSelect(node.chapter));
    hit.setDepth?.(DEPTH_NODE);

    const numText = ctx.add.text(pos.x, pos.y, String(node.chapter), {
      fontFamily: 'var(--pyt-font-pixel)',
      fontSize: '20px',
      color: '#4d3613',
    });
    numText.setOrigin(0.5, 0.5);
    numText.setDepth?.(DEPTH_LABEL);

    const stars = ctx.add.text(pos.x, pos.y + radius + 6, starString(node.stars), {
      fontFamily: 'var(--pyt-font-pixel)',
      fontSize: '14px',
      color: '#8a6c2c',
    });
    stars.setOrigin(0.5, 0);
    stars.setDepth?.(DEPTH_LABEL);

    const title = ctx.add.text(pos.x, pos.y - radius - 18, node.title, {
      fontFamily: 'var(--pyt-font-pixel)',
      fontSize: '12px',
      color: '#4d3613',
    });
    title.setOrigin(0.5, 0.5);
    title.setDepth?.(DEPTH_LABEL);
  });

  void COLOR_TEXT; // 颜色常量保留，供后续标题/图例扩展。
}

/** Phaser 场景配置（plain object，沿用 BootScene/WorldScene 的对象式声明）。 */
export const ChapterMapScene = {
  key: CHAPTER_MAP_SCENE_KEY,
  create(this: ChapterMapSceneContext): void {
    liveScene = this;
    renderChapterMap(this, currentNodes);
    this.events?.on('shutdown', () => {
      if (liveScene === this) {
        liveScene = null;
      }
    });
  },
};

// ── 经现有 Phaser 单例挂载本场景（隔离 scene manager 调用，全程 guard，桩环境安全 no-op） ──

/** 最小 game-like，只覆盖本模块用到的 scene manager API（隔离 phaser 类型）。 */
export interface MainlineGameLike {
  scene?: {
    keys?: Record<string, unknown>;
    getScene?(key: string): unknown;
    add?(key: string, sceneConfig: unknown, autoStart?: boolean, data?: unknown): unknown;
    start?(key: string, data?: unknown): unknown;
    stop?(key: string): unknown;
    sleep?(key: string): unknown;
    bringToTop?(key: string): unknown;
  };
  canvas?: HTMLCanvasElement;
}

function sceneRegistered(mgr: NonNullable<MainlineGameLike['scene']>): boolean {
  if (mgr.keys && CHAPTER_MAP_SCENE_KEY in mgr.keys) return true;
  if (typeof mgr.getScene === 'function') {
    try {
      return Boolean(mgr.getScene(CHAPTER_MAP_SCENE_KEY));
    } catch {
      return false;
    }
  }
  return false;
}

/**
 * 确保 ChapterMapScene 已在给定 Phaser game 上注册并启动（复用现有单例，不 new Game）。
 * 全部 guard：桩 / MockGame（无 add/start）下安全 no-op，便于单测与降级。
 */
export function ensureChapterMapSceneStarted(game: MainlineGameLike | null): void {
  const mgr = game?.scene;
  if (!mgr) return;
  if (!sceneRegistered(mgr)) {
    if (typeof mgr.add === 'function') {
      mgr.add(CHAPTER_MAP_SCENE_KEY, ChapterMapScene, true);
    }
  } else if (typeof mgr.start === 'function') {
    mgr.start(CHAPTER_MAP_SCENE_KEY);
  }
  if (typeof mgr.bringToTop === 'function') {
    mgr.bringToTop(CHAPTER_MAP_SCENE_KEY);
  }
}

/** 离开地图页时停掉本场景（复用场景的复用路径用；guard）。 */
export function stopChapterMapScene(game: MainlineGameLike | null): void {
  const mgr = game?.scene;
  if (typeof mgr?.stop === 'function') {
    mgr.stop(CHAPTER_MAP_SCENE_KEY);
  }
}
