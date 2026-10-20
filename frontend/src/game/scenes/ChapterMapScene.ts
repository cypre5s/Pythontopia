// R-15 / R-14 关卡地图 Phaser 场景（星露谷风像素小镇地图）。
//
// 隔离约定（AGENTS.md「Phaser 动态对象隔离在 adapter/场景层」）：本文件不向业务层暴露任何
// Phaser 类型——对外只输出「纯数据 + 纯函数 + 一组最小 *-Like 接口」。Vue 页面通过
// `setChapterMapData(nodes)` 喂入纯数据、`setChapterMapSelection(ch)` 同步选中、
// `onChapterMapSelect(cb)` 接收节点点击，`ensureChapterMapSceneStarted(game)` 经现有 Phaser
// 单例（phaserGame.ts）挂载本场景，全程不 new Phaser.Game、不直接 import phaser。
//
// 视觉（星露谷 / 二次元像素风）：
//   - 草坪底（双色格子草地）+ 蜿蜒泥土小路串联 7 个章节路牌；
//   - 边缘散布 CC0 Kenney Tiny Town 树木 / 灌木 / 蘑菇 / 花丛点缀，路口站一个像素小人；
//   - 每个节点 = 木牌(signpost) + 圆形章号徽章 + 星牌匾，三态(锁定/进行中/已通关)用徽章色 + 描边环区分；
//   - 章节「目标」默认隐藏，仅在该节点被点选后，于节点上方弹出木质说明牌显示标题 + 目标。
//
// 素材：Kenney Tiny Town（CC0），见 frontend/public/assets/pixel/tilesets/kenney-tiny-town/License.txt。

export const CHAPTER_MAP_SCENE_KEY = 'ChapterMapScene';

/** 关卡地图节点三态。 */
export type ChapterMapNodeState = 'locked' | 'in-progress' | 'cleared';

/** 喂给场景的纯节点数据（由页面从 overview 派生，场景不感知 store / DTO）。 */
export interface ChapterMapNode {
  chapter: number;
  title: string;
  state: ChapterMapNodeState;
  stars: number;
  /** 章节学习目标（点击节点后才显示；缺省则只显示标题）。 */
  objective?: string;
}

/** 节点选中事件名（场景 → 页面，沿用项目 window CustomEvent 习惯）。 */
export const CHAPTER_MAP_SELECT_EVENT = 'pythontopia:mainline-node-select';

// ── Kenney Tiny Town 贴图（16px、12×11 packed 无间隔；frame = row*12+col） ──
const TILESET_KEY = 'mainline-tinytown';
const TILESET_URL = '/assets/pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png';
const TILE = 16;

// 选用的 frame 序号（见对照表，已人工核对）。
const FRAME_FLOWERS = 2; // 带花草地
const FRAME_TREE_GREEN = [4, 5, 6];
const FRAME_TREE_AUTUMN = [8, 9, 10];
const FRAME_BUSH = 17;
const FRAME_MUSHROOM = 29;
const FRAME_NPC = 104; // 像素小人
// 小房子：3 列 ×（屋顶 + 屋檐 + 墙面）3 行。屋顶按章交替红/蓝；墙面 = 窗·门·窗。
const HOUSE_FACADE = [84, 85, 84];
const HOUSE_ROOF_VARIANTS: ReadonlyArray<{ top: number[]; eave: number[] }> = [
  { top: [52, 53, 54], eave: [64, 65, 66] }, // 红顶
  { top: [48, 49, 50], eave: [60, 61, 62] }, // 蓝顶
];

// ── 调色板（数值 hex；草坪 / 泥路 / 徽章三态） ──
const COLOR_GRASS_A = 0x8ac765;
const COLOR_GRASS_B = 0x7fbd57;
const COLOR_GRASS_EDGE = 0x5f9a3e;
const COLOR_PATH_EDGE = 0x8a6334;
const COLOR_PATH_FILL = 0xcaa46a;
const COLOR_PATH_CENTER = 0xdcc08a;
const COLOR_NODE_LOCKED = 0x9a9387; // 锁定·冷灰
const COLOR_NODE_IN_PROGRESS = 0xf1c453; // 进行中·金
const COLOR_NODE_CLEARED = 0x4caf50; // 已通关·绿
const COLOR_MEDAL_STROKE = 0x4d3613;
const COLOR_PANEL_FILL = 0xfff4d6; // 木牌说明纸
const COLOR_PANEL_EDGE = 0x6e4f1d;
const COLOR_TEXT = '#4d3613';
const COLOR_TEXT_MUTED = '#7a5a28';

const PIXEL_FONT = '"Ark Pixel 12", monospace';
// 文本按设备像素比渲染，高分屏标签更锐利（封顶 2，避免超大纹理）。
const TEXT_RES =
  typeof window !== 'undefined' ? Math.min(2, Math.max(1, window.devicePixelRatio || 1)) : 1;

// 渲染层级。
const DEPTH_GROUND = 0;
const DEPTH_PATH = 1;
const DEPTH_DECO_BACK = 2;
const DEPTH_NODE = 5;
const DEPTH_NPC = 6;
const DEPTH_POPUP = 20;

// ── 最小 Phaser 接口（仅本场景渲染所需，隔离不外泄） ──
interface DisplayLike {
  setOrigin(x: number, y?: number): DisplayLike;
  setDepth(d: number): DisplayLike;
  setScale(x: number, y?: number): DisplayLike;
  setAlpha(a: number): DisplayLike;
  setTint(color: number): DisplayLike;
  clearTint(): DisplayLike;
  setFlipX(flip: boolean): DisplayLike;
  setInteractive(config?: unknown): DisplayLike;
  setSize(w: number, h: number): DisplayLike;
  setPosition(x: number, y: number): DisplayLike;
  setTileScale(x: number, y?: number): DisplayLike;
  on(event: string, cb: (...args: unknown[]) => void): DisplayLike;
  destroy(): void;
}
interface GraphicsLike {
  fillStyle(color: number, alpha?: number): GraphicsLike;
  fillRect(x: number, y: number, w: number, h: number): GraphicsLike;
  fillCircle(x: number, y: number, r: number): GraphicsLike;
  fillRoundedRect(x: number, y: number, w: number, h: number, radius: number): GraphicsLike;
  fillTriangle(x1: number, y1: number, x2: number, y2: number, x3: number, y3: number): GraphicsLike;
  lineStyle(width: number, color: number, alpha?: number): GraphicsLike;
  lineBetween(x1: number, y1: number, x2: number, y2: number): GraphicsLike;
  strokeCircle(x: number, y: number, r: number): GraphicsLike;
  strokeRoundedRect(x: number, y: number, w: number, h: number, radius: number): GraphicsLike;
  clear(): GraphicsLike;
  setDepth(depth: number): GraphicsLike;
  setAlpha(alpha: number): GraphicsLike;
  setPosition(x: number, y: number): GraphicsLike;
  setScale(x: number, y?: number): GraphicsLike;
  destroy(): void;
}
interface TextLike extends DisplayLike {
  setColor(color: string): TextLike;
  setText(text: string): TextLike;
}
interface AddLike {
  graphics(): GraphicsLike;
  text(x: number, y: number, text: string, style?: Record<string, unknown>): TextLike;
  image(x: number, y: number, key: string, frame?: number): DisplayLike;
  tileSprite(x: number, y: number, w: number, h: number, key: string, frame?: number): DisplayLike;
  rectangle(
    x: number,
    y: number,
    width: number,
    height: number,
    fillColor?: number,
    fillAlpha?: number,
  ): DisplayLike;
}
interface TweensLike {
  add(config: Record<string, unknown>): unknown;
  killTweensOf(target: unknown): void;
}
interface ScaleLike {
  width: number;
  height: number;
  on(event: string, cb: (...args: unknown[]) => void): void;
  off?(event: string, cb: (...args: unknown[]) => void): void;
}
interface LoadLike {
  spritesheet(key: string, url: string, config: Record<string, unknown>): void;
}
interface TexturesLike {
  exists(key: string): boolean;
}
interface ChapterMapSceneContext {
  add: AddLike;
  scale?: ScaleLike;
  load?: LoadLike;
  textures?: TexturesLike;
  tweens?: TweensLike;
  children?: { removeAll(destroyChild?: boolean): void };
  events?: { on(event: string, callback: () => void): void };
}

// ── 模块级数据桥（场景实例无法被 Vue 直接持有，故用模块级中转） ──
let currentNodes: ChapterMapNode[] = [];
let selectedChapter: number | null = null;
let liveScene: ChapterMapSceneContext | null = null;
let selectHandler: ((chapter: number) => void) | null = null;

/** 喂入/更新地图节点数据；若场景已就绪则立刻重绘。 */
export function setChapterMapData(nodes: ChapterMapNode[]): void {
  currentNodes = nodes;
  scheduleRender();
}

/** 同步当前选中章节（决定哪个节点高亮 + 弹出目标说明牌）。 */
export function setChapterMapSelection(chapter: number | null): void {
  if (selectedChapter === chapter) return;
  selectedChapter = chapter;
  scheduleRender();
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
  selectedChapter = null;
  liveScene = null;
  selectHandler = null;
}

function scheduleRender(): void {
  if (liveScene) {
    renderChapterMap(liveScene, currentNodes);
  }
}

// ── 纯函数（可单测） ──

/** 章节徽章底色（数值 hex）。 */
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

/** 确定性伪随机（mulberry32），让点缀布局稳定不抖动。 */
function makeRng(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), 1 | t);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function pick<T>(arr: readonly T[], rng: () => number): T {
  return arr[Math.floor(rng() * arr.length)] ?? arr[0]!;
}

/**
 * 计算 N 个章节节点在画布上的「蜿蜒小路」坐标（像素）。
 * 横向均匀铺开 + 正弦起伏，营造星露谷地图蜿蜒感；节点收敛在画面中段，上下留出点缀带。
 */
export function computeNodePositions(
  count: number,
  width: number,
  height: number,
  padding = 96,
): Array<{ x: number; y: number }> {
  if (count <= 0) return [];
  const topPad = padding + 70; // 顶部多留：房子向上长 + 章号徽章/说明牌。
  const usableW = Math.max(0, width - padding * 2);
  const usableH = Math.max(0, height - topPad - padding);
  const midY = topPad + usableH / 2;
  const amplitude = usableH * 0.26;
  const positions: Array<{ x: number; y: number }> = [];
  for (let i = 0; i < count; i += 1) {
    const t = count === 1 ? 0.5 : i / (count - 1);
    const x = padding + usableW * t;
    const y = midY + amplitude * Math.sin(i * 0.95 + 0.4);
    positions.push({
      x: Math.round(x),
      y: Math.round(Math.max(topPad, Math.min(height - padding, y))),
    });
  }
  return positions;
}

function emitSelect(chapter: number): void {
  selectHandler?.(chapter);
  if (typeof window !== 'undefined') {
    try {
      window.dispatchEvent(new CustomEvent(CHAPTER_MAP_SELECT_EVENT, { detail: { chapter } }));
    } catch {
      // jsdom / 非浏览器环境无 CustomEvent：忽略，selectHandler 已兜底。
    }
  }
}

// ── 渲染 ──

const GROUND_TILE_SCALE = 2; // 16→32px 草地块，比纯色格更细腻。

function drawGround(ctx: ChapterMapSceneContext, width: number, height: number): void {
  // 底色兜底（瓷砖有透明缝时不露背景）。
  const base = ctx.add.graphics();
  base.fillStyle(COLOR_GRASS_A, 1).fillRect(0, 0, width, height);
  base.setDepth(DEPTH_GROUND);

  // 真·像素草地：用 tileSprite 平铺草 frame（单 draw call，缩放后依旧锐利）。
  if (ctx.textures?.exists(TILESET_KEY)) {
    const grass = ctx.add.tileSprite(0, 0, width, height, TILESET_KEY, 0);
    grass.setOrigin(0, 0).setTileScale(GROUND_TILE_SCALE).setDepth(DEPTH_GROUND);
  } else {
    const cell = 32;
    for (let y = 0; y < height; y += cell) {
      for (let x = 0; x < width; x += cell) {
        const checker = (Math.floor(x / cell) + Math.floor(y / cell)) % 2 === 0;
        base.fillStyle(checker ? COLOR_GRASS_A : COLOR_GRASS_B, 1).fillRect(x, y, cell, cell);
      }
    }
  }

  // 四周一圈深草，做出「地图边缘」收口。
  const edge = ctx.add.graphics();
  edge.lineStyle(12, COLOR_GRASS_EDGE, 0.45);
  edge.strokeRoundedRect(6, 6, width - 12, height - 12, 16);
  edge.setDepth(DEPTH_GROUND);
}

function drawPath(
  ctx: ChapterMapSceneContext,
  positions: Array<{ x: number; y: number }>,
): void {
  if (positions.length < 2) return;
  const g = ctx.add.graphics();
  g.setDepth(DEPTH_PATH);
  const layers: Array<{ w: number; c: number; a: number }> = [
    { w: 30, c: COLOR_PATH_EDGE, a: 1 },
    { w: 22, c: COLOR_PATH_FILL, a: 1 },
    { w: 8, c: COLOR_PATH_CENTER, a: 0.85 },
  ];
  for (const layer of layers) {
    g.lineStyle(layer.w, layer.c, layer.a);
    for (let i = 1; i < positions.length; i += 1) {
      const a = positions[i - 1]!;
      const b = positions[i]!;
      g.lineBetween(a.x, a.y, b.x, b.y);
    }
    // 圆化拐点，避免折线尖角。
    g.fillStyle(layer.c, layer.a);
    for (const p of positions) {
      g.fillCircle(p.x, p.y, layer.w / 2);
    }
  }
}

function drawDecorations(
  ctx: ChapterMapSceneContext,
  width: number,
  height: number,
  positions: Array<{ x: number; y: number }>,
): void {
  if (!ctx.textures?.exists(TILESET_KEY)) return;
  const rng = makeRng(0x5eed01 ^ (Math.round(width) * 73856093) ^ (Math.round(height) * 19349663));
  const step = 58; // 更密的网格 → 更精细的植被。
  const minNodeDist = 100;
  const centerY = height / 2;
  for (let gy = 30; gy < height - 16; gy += step) {
    for (let gx = 30; gx < width - 16; gx += step) {
      const x = gx + (rng() - 0.5) * step * 0.75;
      const y = gy + (rng() - 0.5) * step * 0.75;
      let near = false;
      for (const p of positions) {
        if (Math.hypot(p.x - x, p.y - y) < minNodeDist) {
          near = true;
          break;
        }
      }
      if (near) continue;
      // 中段（节点带）少放，边缘多放，留出主路视野。
      const edgeBias = Math.min(1, Math.abs(y - centerY) / (height * 0.42));
      if (rng() > 0.34 + edgeBias * 0.5) continue;

      const roll = rng();
      let frame: number;
      let scale: number;
      if (roll < 0.42) {
        frame = pick(rng() < 0.66 ? FRAME_TREE_GREEN : FRAME_TREE_AUTUMN, rng);
        scale = 2.1 + rng() * 0.7;
      } else if (roll < 0.6) {
        frame = FRAME_BUSH;
        scale = 1.6 + rng() * 0.3;
      } else if (roll < 0.76) {
        frame = FRAME_MUSHROOM;
        scale = 1.4 + rng() * 0.3;
      } else {
        frame = FRAME_FLOWERS;
        scale = 1.5 + rng() * 0.4;
      }
      const deco = ctx.add.image(Math.round(x), Math.round(y), TILESET_KEY, frame);
      deco.setOrigin(0.5, 0.9).setScale(scale).setDepth(DEPTH_DECO_BACK + (y > centerY ? 2 : 0));
    }
  }
}

function drawNode(
  ctx: ChapterMapSceneContext,
  node: ChapterMapNode,
  pos: { x: number; y: number },
): void {
  const { x, y } = pos;
  const color = nodeFillColor(node.state);
  const selected = selectedChapter === node.chapter;
  const hasTexture = ctx.textures?.exists(TILESET_KEY) ?? false;

  const S = 2; // 房子缩放：单 tile 16→32px。
  const tw = TILE * S; // 32
  const houseH = tw * 2; // 屋顶 + 墙面两层
  const houseTopY = y - houseH; // 屋顶顶端
  const medalY = houseTopY - 18; // 章号徽章浮于屋顶上方
  const houseW = tw * 3;

  // 选中：脉动光环（置于房子后）。
  if (selected) {
    const glow = ctx.add.graphics();
    glow.fillStyle(0xffe27a, 0.4).fillCircle(0, 0, 60);
    glow.setPosition(x, y - tw).setDepth(DEPTH_NODE - 2);
    ctx.tweens?.add({
      targets: glow,
      scale: { from: 0.82, to: 1.12 },
      alpha: { from: 0.55, to: 0.12 },
      duration: 900,
      yoyo: true,
      repeat: -1,
      ease: 'Sine.inOut',
    });
  }

  // 脚下泥土小台 + 投影。
  const base = ctx.add.graphics();
  base.setDepth(DEPTH_NODE - 1);
  base.fillStyle(0x000000, 0.16).fillCircle(x, y + 2, houseW / 2 - 2);
  base.fillStyle(COLOR_PATH_EDGE, 0.6).fillCircle(x, y, houseW / 2 - 4);
  base.fillStyle(COLOR_PATH_FILL, 0.95).fillCircle(x, y - 2, houseW / 2 - 10);

  // 小房子（屋顶按章交替红/蓝；锁定态压暗）。
  if (hasTexture) {
    const roof = HOUSE_ROOF_VARIANTS[(node.chapter - 1) % HOUSE_ROOF_VARIANTS.length]!;
    for (let c = 0; c < 3; c += 1) {
      const cx = x + (c - 1) * tw;
      const facade = ctx.add.image(cx, y, TILESET_KEY, HOUSE_FACADE[c]);
      facade.setOrigin(0.5, 1).setScale(S).setDepth(DEPTH_NODE);
      const eave = ctx.add.image(cx, y - tw, TILESET_KEY, roof.eave[c]);
      eave.setOrigin(0.5, 1).setScale(S).setDepth(DEPTH_NODE);
      const top = ctx.add.image(cx, y - tw - tw, TILESET_KEY, roof.top[c]);
      top.setOrigin(0.5, 1).setScale(S).setDepth(DEPTH_NODE);
      if (node.state === 'locked') {
        const lockTint = 0x5b5750;
        facade.setTint(lockTint).setAlpha(0.86);
        eave.setTint(lockTint).setAlpha(0.86);
        top.setTint(lockTint).setAlpha(0.86);
      }
    }
  }

  // 已通关：屋顶插小旗。
  if (node.state === 'cleared') {
    const flag = ctx.add.graphics();
    flag.setDepth(DEPTH_NODE + 1);
    const fx = x;
    const fy = houseTopY + 2;
    flag.lineStyle(3, 0x4d3613, 1).lineBetween(fx, fy, fx, fy - 24);
    flag.fillStyle(0x4caf50, 1).fillTriangle(fx, fy - 24, fx, fy - 12, fx + 18, fy - 18);
  }

  // 章号徽章（焦点）。
  const medal = ctx.add.graphics();
  medal.setDepth(DEPTH_NODE + 2);
  if (selected) {
    medal.lineStyle(5, 0xffffff, 0.95).strokeCircle(0, 0, 23);
  }
  medal.fillStyle(COLOR_MEDAL_STROKE, 1).fillCircle(0, 0, 19);
  medal.fillStyle(color, 1).fillCircle(0, 0, 15.5);
  medal.fillStyle(0xffffff, 0.25).fillCircle(-5, -5, 6);
  if (node.state === 'cleared') {
    medal.lineStyle(2.5, 0x2f7d33, 1).strokeCircle(0, 0, 19);
  } else if (node.state === 'in-progress') {
    medal.lineStyle(2.5, 0xe0a020, 1).strokeCircle(0, 0, 19);
  }
  medal.setPosition(x, medalY);

  const label = node.state === 'locked' ? '🔒' : String(node.chapter);
  const numText = ctx.add.text(x, medalY, label, {
    fontFamily: PIXEL_FONT,
    fontSize: node.state === 'locked' ? '15px' : '18px',
    color: node.state === 'locked' ? '#3a2a10' : '#fff8e6',
    fontStyle: 'bold',
    resolution: TEXT_RES,
  });
  numText.setOrigin(0.5, 0.5).setDepth(DEPTH_NODE + 3);

  // 星牌匾（房子下方）。
  const stars = ctx.add.text(x, y + houseW / 2 - 6, starString(node.stars), {
    fontFamily: PIXEL_FONT,
    fontSize: '15px',
    color: node.stars > 0 ? '#f2b705' : '#b89a63',
    resolution: TEXT_RES,
  });
  stars.setOrigin(0.5, 0).setDepth(DEPTH_NODE + 3);

  // 命中区（覆盖房子 + 徽章 + 星），点击选中。
  const hitH = y + 24 - (medalY - 24);
  const hit = ctx.add.rectangle(x, (medalY - 24 + y + 24) / 2, houseW + 16, hitH, 0xffffff, 0.001);
  hit.setDepth(DEPTH_NODE + 4);
  hit.setInteractive({ useHandCursor: true });
  hit.on('pointerup', () => emitSelect(node.chapter));
  hit.on('pointerover', () => {
    ctx.tweens?.add({ targets: [medal, numText], scale: 1.14, duration: 110, ease: 'Quad.out' });
  });
  hit.on('pointerout', () => {
    ctx.tweens?.add({ targets: [medal, numText], scale: 1, duration: 110, ease: 'Quad.out' });
  });

  // 进行中：徽章轻微上下浮动，吸引视线。
  if (node.state === 'in-progress') {
    ctx.tweens?.add({
      targets: [medal, numText],
      y: '-=5',
      duration: 780,
      yoyo: true,
      repeat: -1,
      ease: 'Sine.inOut',
    });
  }

  // 仅选中节点弹出「目标」木质说明牌。
  if (selected) {
    drawObjectivePanel(ctx, node, x, medalY - 26);
  }
}

function drawObjectivePanel(
  ctx: ChapterMapSceneContext,
  node: ChapterMapNode,
  cx: number,
  bottomY: number,
): void {
  const width = ctx.scale?.width ?? 960;
  const panelW = 248;
  const padX = 14;
  const titleSize = 14;
  const objSize = 12;

  const title = ctx.add.text(0, 0, node.title, {
    fontFamily: PIXEL_FONT,
    fontSize: `${titleSize}px`,
    color: COLOR_TEXT,
    fontStyle: 'bold',
    resolution: TEXT_RES,
    wordWrap: { width: panelW - padX * 2, useAdvancedWrap: true },
  });
  const objText = (node.objective ?? '点击「进入学习」开始本章的看 · 学 · 练。').trim();
  const obj = ctx.add.text(0, 0, objText, {
    fontFamily: PIXEL_FONT,
    fontSize: `${objSize}px`,
    color: COLOR_TEXT_MUTED,
    lineSpacing: 4,
    resolution: TEXT_RES,
    wordWrap: { width: panelW - padX * 2, useAdvancedWrap: true },
  });

  // 估高：用 DOM text 的实际行高较难取，这里按字数粗估，宁可略宽松。
  const titleLines = Math.max(1, Math.ceil(node.title.length / 14));
  const objLines = Math.max(1, Math.ceil(objText.length / 18));
  const panelH = 18 + titleLines * (titleSize + 6) + objLines * (objSize + 6) + 14;

  // 钳制在画面内。
  let px = cx - panelW / 2;
  px = Math.max(10, Math.min(width - panelW - 10, px));
  const py = Math.max(8, bottomY - panelH);

  const g = ctx.add.graphics();
  g.setDepth(DEPTH_POPUP);
  g.fillStyle(0x000000, 0.18).fillRoundedRect(px + 3, py + 4, panelW, panelH, 10);
  g.fillStyle(COLOR_PANEL_EDGE, 1).fillRoundedRect(px, py, panelW, panelH, 10);
  g.fillStyle(COLOR_PANEL_FILL, 1).fillRoundedRect(px + 3, py + 3, panelW - 6, panelH - 6, 8);
  // 指向节点的小三角。
  const tipX = Math.max(px + 18, Math.min(px + panelW - 18, cx));
  g.fillStyle(COLOR_PANEL_EDGE, 1).fillTriangle(
    tipX - 9,
    py + panelH - 1,
    tipX + 9,
    py + panelH - 1,
    tipX,
    py + panelH + 12,
  );

  title.setPosition(px + padX, py + 12).setDepth(DEPTH_POPUP + 1);
  obj
    .setPosition(px + padX, py + 12 + titleLines * (titleSize + 6) + 4)
    .setDepth(DEPTH_POPUP + 1);
}

function drawNpc(
  ctx: ChapterMapSceneContext,
  positions: Array<{ x: number; y: number }>,
  nodes: ChapterMapNode[],
): void {
  if (!ctx.textures?.exists(TILESET_KEY) || positions.length === 0) return;
  // 站在「当前可操作」节点旁：优先进行中，其次第一个已解锁，再不行第一个。
  let idx = nodes.findIndex((n) => n.state === 'in-progress');
  if (idx < 0) idx = nodes.findIndex((n) => n.state !== 'locked');
  if (idx < 0) idx = 0;
  const target = positions[idx];
  if (!target) return;
  const npc = ctx.add.image(target.x + 60, target.y + 4, TILESET_KEY, FRAME_NPC);
  npc.setOrigin(0.5, 1).setScale(2.6).setDepth(DEPTH_NPC).setFlipX(true);
  if (ctx.tweens) {
    ctx.tweens.add({
      targets: npc,
      y: '-=3',
      duration: 620,
      yoyo: true,
      repeat: -1,
      ease: 'Sine.inOut',
    });
  }
}

/** 用给定场景上下文把整张地图画出来（场景 create 与重绘共用）。 */
export function renderChapterMap(ctx: ChapterMapSceneContext, nodes: ChapterMapNode[]): void {
  ctx.children?.removeAll(true);
  const width = ctx.scale?.width ?? 960;
  const height = ctx.scale?.height ?? 640;

  const positions = computeNodePositions(nodes.length, width, height);

  drawGround(ctx, width, height);
  drawPath(ctx, positions);
  drawDecorations(ctx, width, height, positions);
  nodes.forEach((node, i) => {
    const pos = positions[i];
    if (pos) drawNode(ctx, node, pos);
  });
  drawNpc(ctx, positions, nodes);
}

/** Phaser 场景配置（plain object，沿用 BootScene/WorldScene 的对象式声明）。 */
export const ChapterMapScene = {
  key: CHAPTER_MAP_SCENE_KEY,
  preload(this: ChapterMapSceneContext): void {
    if (this.load && !this.textures?.exists(TILESET_KEY)) {
      this.load.spritesheet(TILESET_KEY, TILESET_URL, { frameWidth: TILE, frameHeight: TILE });
    }
  },
  create(this: ChapterMapSceneContext): void {
    // eslint-disable-next-line @typescript-eslint/no-this-alias -- 缓存当前 Phaser 场景上下文，供外部 setChapterMapData/Selection 重绘；shutdown 时清除。
    liveScene = this;
    renderChapterMap(this, currentNodes);
    // resize 监听按「场景实例」绑定/解绑，不用模块级 resizeBound 布尔守卫：
    // 那个守卫跨 Phaser.Game 实例残留（destroy 不复位），第 2 次进本页时
    // `!resizeBound` 为 false，新实例上压根不再绑 resize，窗口缩放不再重排。
    const scale = this.scale;
    const onResize = (): void => {
      if (liveScene) renderChapterMap(liveScene, currentNodes);
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
