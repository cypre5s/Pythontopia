/**
 * 代码剧场「机关广场」Phaser 场景（R-14 收敛后的「看→学→练→测」之「学/练」可视化）。
 *
 * 把学生真实 Python 执行轨迹（Trace → RenderCommand[]）演成傀儡走代码：脚本梯逐行发光、
 * 代码傀儡当程序计数器、变量牌翻牌显值、环形循环跑道计圈、分支岔路口选路、公告板打印输出。
 *
 * 架构隔离（AGENTS.md / 契约 §0 红线「Phaser 动态对象隔离、单 Game 实例」）：
 *   - 时序/状态全在纯模块（codeTheater/timelinePlayer + theaterState + stageLayout），无 Phaser。
 *   - 本场景沿用项目「plain-object scene + *-Like 接口」约定（同 BootScene/WorldScene/ChapterMapScene），
 *     不 `new Phaser.Game`、不直接 import phaser；经现有单例（phaserGame.ts）的 scene manager 挂载。
 *   - 模块级桥（同 ChapterMapScene）：Vue 组件 / debug-theater 通过 getCodeTheaterPlayer() 拿到
 *     共享时间轴播放器做 play/pause/step/seek/快进；loadCodeTheater() 喂入指令并触发重建。
 */
import { attachLifecycle } from '@game/core/SceneLifecycle';
import type Phaser from 'phaser';
import type { RenderCommand } from '@/services/codeTheater/types';
import {
  TimelinePlayer,
  type LoadOptions,
} from '@game/codeTheater/timelinePlayer';
import type { TheaterState } from '@game/codeTheater/theaterState';
import {
  computeForkLayout,
  computeLadderLayout,
  computeLoopTrackLayout,
  computeNoticeBoardLayout,
  computeWorldPanelLayout,
  puppetPositionForLine,
  variableBoardSlot,
  type LadderLayout,
} from '@game/codeTheater/stageLayout';
import {
  createBranchFork,
  createLoopTrack,
  createNoticeBoard,
  createPuppet,
  createScriptLadder,
  createVariableBoards,
  createWorldPanel,
  formatStumble,
  type BranchForkHandle,
  type LoopTrackHandle,
  type NoticeBoardHandle,
  type PuppetHandle,
  type ScriptLadderHandle,
  type StageAddLike,
  type StageContext,
  type StageTweensLike,
  type VariableBoardsHandle,
  type WorldPanelHandle,
  type WorldPanelMeta,
} from '@game/codeTheater/stagePrimitives';

export const CODE_THEATER_SCENE_KEY = 'CodeTheaterScene';

const DEFAULT_WIDTH = 960;
const DEFAULT_HEIGHT = 640;
const MAX_DELTA_MS = 200; // 切后台/断点时 delta 可能巨大，钳制避免一帧跳完。
const COLOR_BACKDROP = 0x141821;

/** 本场景所需的最小 Phaser 上下文（隔离不外泄）。 */
export interface CodeTheaterSceneContext extends StageContext {
  add: StageAddLike;
  tweens?: StageTweensLike;
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

interface StageHandles {
  ladder: ScriptLadderHandle;
  puppet: PuppetHandle;
  boards: VariableBoardsHandle;
  loopTrack: LoopTrackHandle;
  fork: BranchForkHandle;
  notice: NoticeBoardHandle;
  world: WorldPanelHandle;
}

// ── 模块级桥（场景实例无法被 Vue 直接持有，沿用 ChapterMapScene 的中转模式） ──
const player = new TimelinePlayer();
let liveScene: CodeTheaterSceneContext | null = null;
let handles: StageHandles | null = null;
let ladderLayout: LadderLayout | null = null;
let stageDim = { width: DEFAULT_WIDTH, height: DEFAULT_HEIGHT };
let prevTimeMs = 0;
let lastRenderedCount = -1;

/** 对外控制接口：共享时间轴播放器（play/pause/step/seek/setRate/subscribe...）。 */
export function getCodeTheaterPlayer(): TimelinePlayer {
  return player;
}

/** 加载一份 RenderCommand[]（+ 可选 Trace），重置播放头；若场景已就绪立即重建舞台。 */
export function loadCodeTheater(
  commands: readonly RenderCommand[],
  options: LoadOptions = {},
): void {
  player.load(commands, options);
  if (liveScene) {
    rebuildStage(liveScene);
  }
}

/** 仅供测试/调试：重置模块级桥（避免跨用例泄漏）。 */
export function __resetCodeTheaterForTests(): void {
  destroyHandles();
  liveScene = null;
  ladderLayout = null;
  stageDim = { width: DEFAULT_WIDTH, height: DEFAULT_HEIGHT };
  prevTimeMs = 0;
  lastRenderedCount = -1;
  player.load([]);
}

function readDimensions(ctx: CodeTheaterSceneContext): { width: number; height: number } {
  return {
    width: ctx.scale?.width ?? DEFAULT_WIDTH,
    height: ctx.scale?.height ?? DEFAULT_HEIGHT,
  };
}

/** 当无 Trace.source 时，用指令里出现过的最大行号推断脚本梯行数。 */
function inferLineCount(): number {
  let maxLine = player.getSourceLines().length;
  for (const cmd of player.getCommands()) {
    const line = cmd.payload['line'];
    if (typeof line === 'number' && line > maxLine) maxLine = line;
    const lineTaken = cmd.payload['lineTaken'];
    if (typeof lineTaken === 'number' && lineTaken > maxLine) maxLine = lineTaken;
  }
  return Math.max(1, maxLine);
}

/** 读取系统「减少动作」偏好；SSR / 无 matchMedia 环境 fail-safe 返回 false。 */
function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false;
  }
  try {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch {
    return false;
  }
}

interface WorldScan {
  /** 全部世界目标编号（升序、去重）。 */
  targets: number[];
  meta: WorldPanelMeta;
}

/**
 * 预扫指令里的 world_effect，得到全部目标编号与展示元信息，供舞台开场即把整排灯「全暗」铺好，
 * 随播放逐一点亮（避免目标逐条出现时反复重排）。与 reducer 一致按 target 升序。
 */
function scanWorldTargets(): WorldScan {
  const set = new Set<number>();
  let noun = '目标';
  let actionVerb: string | undefined;
  let unit: string | undefined;
  for (const cmd of player.getCommands()) {
    if (cmd.action !== 'world_effect') continue;
    const target = cmd.payload['target'];
    if (typeof target === 'number' && Number.isFinite(target)) set.add(target);
    const nounValue = cmd.payload['targetNoun'];
    if (typeof nounValue === 'string' && nounValue) noun = nounValue;
    const verbValue = cmd.payload['actionVerb'];
    if (typeof verbValue === 'string' && verbValue) actionVerb = verbValue;
    const unitValue = cmd.payload['unit'];
    if (typeof unitValue === 'string' && unitValue) unit = unitValue;
  }
  return { targets: [...set].sort((a, b) => a - b), meta: { noun, actionVerb, unit } };
}

function destroyHandles(): void {
  if (!handles) return;
  handles.ladder.destroy();
  handles.puppet.destroy();
  handles.boards.destroy();
  handles.loopTrack.destroy();
  handles.fork.destroy();
  handles.notice.destroy();
  handles.world.destroy();
  handles = null;
}

/** （重）构建整座舞台：清空旧对象 → 按当前 dim + 脚本行数布局 → 创建原语 → 渲染初始帧。 */
function rebuildStage(ctx: CodeTheaterSceneContext): void {
  ctx.children?.removeAll(true);
  handles = null; // 对象已随 removeAll 销毁，丢弃旧句柄引用即可。

  stageDim = readDimensions(ctx);
  const { width, height } = stageDim;

  // 背景幕。
  const backdrop = ctx.add.graphics();
  backdrop.fillStyle(COLOR_BACKDROP, 1).fillRoundedRect(0, 0, width, height, 0).setDepth(0);
  ctx.add
    .text(width / 2, 28, '机关广场 · 代码剧场', {
      fontFamily: '"Zpix", "Ark Pixel 12", monospace',
      fontSize: '16px',
      color: '#ffd166',
    })
    .setOrigin(0.5, 0.5)
    .setDepth(1);

  const lineCount = inferLineCount();
  ladderLayout = computeLadderLayout(lineCount, stageDim);
  const loopTrack = computeLoopTrackLayout(stageDim);
  const fork = computeForkLayout(stageDim);
  const notice = computeNoticeBoardLayout(stageDim);
  const worldScan = scanWorldTargets();
  const worldLayout = computeWorldPanelLayout(worldScan.targets.length, stageDim);
  const start = puppetPositionForLine(ladderLayout, 1);

  handles = {
    ladder: createScriptLadder(ctx, ladderLayout, player.getSourceLines()),
    puppet: createPuppet(ctx, start),
    boards: createVariableBoards(ctx),
    loopTrack: createLoopTrack(ctx, loopTrack),
    fork: createBranchFork(ctx, fork),
    notice: createNoticeBoard(ctx, notice),
    world: createWorldPanel(ctx, worldLayout, worldScan.targets, worldScan.meta, stageDim),
  };

  lastRenderedCount = -1;
  renderState(player.getState());
}

/** 把一帧 TheaterState「画出来」（只在播放头变化时调用）。 */
function renderState(state: TheaterState): void {
  if (!handles || !ladderLayout) return;

  if (state.currentLine >= 1) {
    handles.ladder.highlight(state.currentLine);
    const pos = puppetPositionForLine(ladderLayout, state.currentLine);
    handles.puppet.moveTo(pos.x, pos.y);
  }
  handles.puppet.setStumbled(state.stumbled);
  handles.boards.sync(state, (index) => variableBoardSlot(index, stageDim));
  handles.loopTrack.sync(state.loops);
  handles.world.sync(state.world, { reducedMotion: prefersReducedMotion() });

  if (state.lastBranch) {
    handles.fork.choose(state.lastBranch.result, state.lastBranch.cond);
  } else {
    handles.fork.reset();
  }

  const lines = [...state.noticeLines];
  if (state.stumbled && state.stumble) {
    lines.push(`⚠ ${formatStumble(state.stumble)}`);
  }
  // 死循环：轨迹被步数预算截断且已播到结尾 → 提示「原地无限踱步」。
  if (player.isTruncated() && player.getAppliedCount() >= player.getTotal() && player.getTotal() > 0) {
    lines.push('… 疑似死循环，傀儡在原地无限踱步（轨迹已按步数预算截断）');
  }
  handles.notice.post(lines);
}

/** Phaser 场景配置（plain object，沿用项目对象式声明）。 */
export const CodeTheaterScene = {
  key: CODE_THEATER_SCENE_KEY,
  create(this: CodeTheaterSceneContext): void {
    // eslint-disable-next-line @typescript-eslint/no-this-alias -- 缓存当前场景上下文供模块级桥重建；shutdown 时清除。
    liveScene = this;
    prevTimeMs = this.time?.now ?? 0;
    rebuildStage(this);

    // resize 监听按「场景实例」绑定/解绑：模块级 resizeBound 布尔跨 Phaser.Game
    // 实例残留（destroy 不复位），第 2 次进页时新实例上不再绑 resize，重排失效。
    const scale = this.scale;
    const onResize = (): void => {
      if (liveScene) rebuildStage(liveScene);
    };
    scale?.on('resize', onResize);

    attachLifecycle(this as unknown as Phaser.Scene, {
      onShutdown: () => {
        scale?.off?.('resize', onResize);
        if (liveScene === this) {
          liveScene = null;
          handles = null;
        }
      },
    });
  },
  update(this: CodeTheaterSceneContext): void {
    const now = this.time?.now ?? prevTimeMs;
    const delta = Math.max(0, Math.min(MAX_DELTA_MS, now - prevTimeMs));
    prevTimeMs = now;

    player.tick(delta);

    const count = player.getAppliedCount();
    if (count !== lastRenderedCount) {
      lastRenderedCount = count;
      renderState(player.getState());
    }
  },
};

// ── 经现有 Phaser 单例挂载本场景（隔离 scene manager 调用，全程 guard，桩环境安全 no-op） ──

/** 最小 game-like，仅覆盖本模块用到的 scene manager API（隔离 phaser 类型）。 */
export interface CodeTheaterGameLike {
  scene?: {
    keys?: Record<string, unknown>;
    getScene?(key: string): unknown;
    add?(key: string, sceneConfig: unknown, autoStart?: boolean, data?: unknown): unknown;
    start?(key: string, data?: unknown): unknown;
    stop?(key: string): unknown;
    bringToTop?(key: string): unknown;
  };
}

function sceneRegistered(mgr: NonNullable<CodeTheaterGameLike['scene']>): boolean {
  if (mgr.keys && CODE_THEATER_SCENE_KEY in mgr.keys) return true;
  if (typeof mgr.getScene === 'function') {
    try {
      return Boolean(mgr.getScene(CODE_THEATER_SCENE_KEY));
    } catch {
      return false;
    }
  }
  return false;
}

/**
 * 确保 CodeTheaterScene 已在给定 Phaser game 上注册并启动（复用现有单例，不 new Game）。
 * 全部 guard：桩 / MockGame（无 add/start）下安全 no-op，便于单测与降级。
 */
export function ensureCodeTheaterSceneStarted(game: CodeTheaterGameLike | null): void {
  const mgr = game?.scene;
  if (!mgr) return;
  if (!sceneRegistered(mgr)) {
    if (typeof mgr.add === 'function') {
      mgr.add(CODE_THEATER_SCENE_KEY, CodeTheaterScene, true);
    }
  } else if (typeof mgr.start === 'function') {
    mgr.start(CODE_THEATER_SCENE_KEY);
  }
  if (typeof mgr.bringToTop === 'function') {
    mgr.bringToTop(CODE_THEATER_SCENE_KEY);
  }
}

/** 离开剧场页时停掉本场景（guard）。 */
export function stopCodeTheaterScene(game: CodeTheaterGameLike | null): void {
  const mgr = game?.scene;
  if (typeof mgr?.stop === 'function') {
    mgr.stop(CODE_THEATER_SCENE_KEY);
  }
}
