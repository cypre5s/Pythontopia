/**
 * 机关广场舞台几何（纯函数，无 Phaser）。
 *
 * 把「舞台尺寸 + 脚本行数」算成各部件的像素坐标：脚本梯（逐行台阶）、傀儡落点、
 * 环形循环跑道、分支岔路口、变量牌位、公告板。绘制层（stagePrimitives / Scene）只
 * 取坐标摆放对象，几何全部可单测（行距单调、跑道点落在圆周、坐标钳制等）。
 */

export interface StageDimensions {
  width: number;
  height: number;
}

export interface LadderOptions {
  /** 脚本梯左侧基准 x。 */
  x?: number;
  /** 第一行 y。 */
  top?: number;
  /** 行距上限（行多时自动压缩到可用高度内）。 */
  maxRungHeight?: number;
  /** 底部留白。 */
  bottom?: number;
}

export interface LadderRung {
  /** 1 基行号。 */
  line: number;
  y: number;
}

export interface LadderLayout {
  x: number;
  top: number;
  rungHeight: number;
  lineCount: number;
  rungs: LadderRung[];
}

const DEFAULT_LADDER_X = 64;
const DEFAULT_LADDER_TOP = 80;
const DEFAULT_MAX_RUNG = 30;
const MIN_RUNG = 12;

/** 计算脚本梯：每行一个台阶，行距随行数自适应压缩。 */
export function computeLadderLayout(
  lineCount: number,
  dim: StageDimensions,
  options: LadderOptions = {},
): LadderLayout {
  const count = Math.max(0, Math.floor(lineCount));
  const x = options.x ?? DEFAULT_LADDER_X;
  const top = options.top ?? DEFAULT_LADDER_TOP;
  const bottom = options.bottom ?? 64;
  const maxRung = options.maxRungHeight ?? DEFAULT_MAX_RUNG;
  const usableH = Math.max(0, dim.height - top - bottom);
  // 行多时压缩行距，但不低于 MIN_RUNG（宁可溢出也不重叠到糊成一团）。
  const fitRung = count > 1 ? usableH / (count - 1) : maxRung;
  const rungHeight = Math.max(MIN_RUNG, Math.min(maxRung, fitRung));
  const rungs: LadderRung[] = [];
  for (let i = 0; i < count; i += 1) {
    rungs.push({ line: i + 1, y: Math.round(top + i * rungHeight) });
  }
  return { x, top, rungHeight, lineCount: count, rungs };
}

/** 1 基行号 → 台阶 y；越界钳制到首/末行（无行时返回 top）。 */
export function ladderLineY(layout: LadderLayout, line: number): number {
  if (layout.lineCount <= 0) return layout.top;
  const clamped = Math.max(1, Math.min(layout.lineCount, Math.floor(line)));
  const rung = layout.rungs[clamped - 1];
  return rung ? rung.y : layout.top;
}

/** 傀儡（程序计数器）站在某行台阶右侧的落点。 */
export function puppetPositionForLine(
  layout: LadderLayout,
  line: number,
  offsetX = 28,
): { x: number; y: number } {
  return { x: layout.x + offsetX, y: ladderLineY(layout, line) };
}

export interface LoopTrackOptions {
  cx?: number;
  cy?: number;
  radius?: number;
}

export interface LoopTrackLayout {
  cx: number;
  cy: number;
  radius: number;
}

/** 环形循环跑道（默认落在舞台右上区域）。 */
export function computeLoopTrackLayout(
  dim: StageDimensions,
  options: LoopTrackOptions = {},
): LoopTrackLayout {
  const radius = options.radius ?? Math.max(40, Math.min(110, Math.min(dim.width, dim.height) * 0.16));
  const cx = options.cx ?? dim.width - radius - 56;
  const cy = options.cy ?? radius + 72;
  return { cx, cy, radius };
}

/**
 * 跑道上的点：fraction 0..1 绕一圈（0 在正上方，顺时针）。
 * 传入「圈数小数」（如 2.5）会自动取小数部分，便于按圈进度补间。
 */
export function loopRunnerPosition(
  track: LoopTrackLayout,
  fraction: number,
): { x: number; y: number } {
  const frac = ((fraction % 1) + 1) % 1; // 归一到 [0,1)
  const angle = -Math.PI / 2 + frac * Math.PI * 2; // 12 点起，顺时针
  return {
    x: track.cx + Math.cos(angle) * track.radius,
    y: track.cy + Math.sin(angle) * track.radius,
  };
}

export interface ForkOptions {
  originX?: number;
  originY?: number;
  spread?: number;
  drop?: number;
}

export interface ForkLayout {
  originX: number;
  originY: number;
  trueX: number;
  trueY: number;
  falseX: number;
  falseY: number;
}

/** 分支岔路口：从 origin 向左下（false）/右下（true）分叉。 */
export function computeForkLayout(dim: StageDimensions, options: ForkOptions = {}): ForkLayout {
  const originX = options.originX ?? dim.width * 0.5;
  const originY = options.originY ?? dim.height - 150;
  const spread = options.spread ?? Math.max(60, dim.width * 0.16);
  const drop = options.drop ?? 84;
  return {
    originX,
    originY,
    trueX: originX + spread,
    trueY: originY + drop,
    falseX: originX - spread,
    falseY: originY + drop,
  };
}

/** 选中分支的落点（true → 右路，false → 左路）。 */
export function forkBranchPosition(fork: ForkLayout, result: boolean): { x: number; y: number } {
  return result ? { x: fork.trueX, y: fork.trueY } : { x: fork.falseX, y: fork.falseY };
}

export interface BoardOptions {
  startX?: number;
  startY?: number;
  boardWidth?: number;
  boardHeight?: number;
  gap?: number;
  perColumn?: number;
}

/** 变量牌按列网格排布：index → 左上角坐标。 */
export function variableBoardSlot(
  index: number,
  dim: StageDimensions,
  options: BoardOptions = {},
): { x: number; y: number } {
  const boardWidth = options.boardWidth ?? 130;
  const boardHeight = options.boardHeight ?? 44;
  const gap = options.gap ?? 12;
  const startX = options.startX ?? dim.width - boardWidth - 36;
  const startY = options.startY ?? dim.height * 0.42;
  const bottom = 48;
  const perColumn =
    options.perColumn ?? Math.max(1, Math.floor((dim.height - startY - bottom) / (boardHeight + gap)));
  const safeIndex = Math.max(0, Math.floor(index));
  const column = Math.floor(safeIndex / perColumn);
  const row = safeIndex % perColumn;
  return {
    x: startX - column * (boardWidth + gap),
    y: Math.round(startY + row * (boardHeight + gap)),
  };
}

export interface NoticeBoardLayout {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** 公告板（print 输出）固定在舞台底部居中。 */
export function computeNoticeBoardLayout(
  dim: StageDimensions,
  maxWidth = 360,
  height = 96,
): NoticeBoardLayout {
  const width = Math.min(maxWidth, Math.max(160, dim.width - 96));
  return {
    x: Math.round((dim.width - width) / 2),
    y: dim.height - height - 16,
    width,
    height,
  };
}

export interface WorldPanelOptions {
  /** 第一行格子的 y（面板顶部基准）。 */
  top?: number;
  /** 单格边长。 */
  cell?: number;
  /** 格间距。 */
  gap?: number;
  /** 整排最大宽度（超出自动换行）。 */
  maxWidth?: number;
  /** 横向居中基准 x（默认舞台正中）。 */
  centerX?: number;
}

export interface WorldPanelLayout {
  /** 第一格左上角 x。 */
  x: number;
  /** 第一行 y。 */
  y: number;
  cell: number;
  gap: number;
  /** 每行格数。 */
  columns: number;
  /** 行数。 */
  rows: number;
  /** 整排像素宽。 */
  width: number;
  /** 整排像素高。 */
  height: number;
  /** 目标总数（count=0 时面板为空，不绘制）。 */
  count: number;
}

/**
 * 世界目标面板：把 `count` 个目标排成居中的一排灯/格，超出 `maxWidth` 自动换行。
 * 落在舞台上方居中区（脚本梯右、循环跑道左的空带），与既有部件错开。
 */
export function computeWorldPanelLayout(
  count: number,
  dim: StageDimensions,
  options: WorldPanelOptions = {},
): WorldPanelLayout {
  const cell = options.cell ?? 34;
  const gap = options.gap ?? 10;
  const top = options.top ?? 92;
  const n = Math.max(0, Math.floor(count));
  const maxWidth = options.maxWidth ?? Math.min(300, Math.max(120, dim.width * 0.34));
  const perRow = Math.max(1, Math.floor((maxWidth + gap) / (cell + gap)));
  const columns = n > 0 ? Math.min(perRow, n) : 0;
  const rows = columns > 0 ? Math.ceil(n / columns) : 0;
  const width = columns > 0 ? columns * cell + (columns - 1) * gap : 0;
  const height = rows > 0 ? rows * cell + (rows - 1) * gap : 0;
  const centerX = options.centerX ?? dim.width / 2;
  return {
    x: Math.round(centerX - width / 2),
    y: top,
    cell,
    gap,
    columns,
    rows,
    width,
    height,
    count: n,
  };
}

/** 第 index 个目标格（0 基）的左上角坐标：先填满一行再换行。 */
export function worldCellSlot(layout: WorldPanelLayout, index: number): { x: number; y: number } {
  const i = Math.max(0, Math.floor(index));
  const col = layout.columns > 0 ? i % layout.columns : 0;
  const row = layout.columns > 0 ? Math.floor(i / layout.columns) : 0;
  return {
    x: layout.x + col * (layout.cell + layout.gap),
    y: layout.y + row * (layout.cell + layout.gap),
  };
}
