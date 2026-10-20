/**
 * 机关广场渲染原语（Phaser 隔离层）。
 *
 * 隔离约定（AGENTS.md「Phaser 动态对象隔离在 adapter/场景层、不外泄」）：本文件只通过一组
 * 最小 `*-Like` 接口操作 Phaser 的 graphics/text/tween，不 import phaser、不向业务层泄露
 * Phaser 类型。每个原语返回一个「句柄」（带 update/destroy 的纯数据接口），由 Scene 编排：
 * Scene 每帧从纯播放器拿 TheaterState，调句柄方法把状态「画出来」。
 *
 * 所有原语对桩 ctx 友好（缺方法即安全降级），便于无头单测与弱环境兜底。
 */
import type {
  LoopRuntimeState,
  StumbleState,
  TheaterState,
  VariableCell,
  WorldStageState,
} from './theaterState';
import type {
  LadderLayout,
  LoopTrackLayout,
  ForkLayout,
  NoticeBoardLayout,
  StageDimensions,
  WorldPanelLayout,
} from './stageLayout';
import { forkBranchPosition, ladderLineY, loopRunnerPosition, worldCellSlot } from './stageLayout';

// ── 最小 Phaser 接口（仅本层绘制所需，链式方法返回自身以匹配 Phaser GameObject） ──
export interface GraphicsLike {
  fillStyle(color: number, alpha?: number): GraphicsLike;
  fillCircle(x: number, y: number, r: number): GraphicsLike;
  fillRoundedRect(x: number, y: number, w: number, h: number, radius: number): GraphicsLike;
  fillTriangle(
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    x3: number,
    y3: number,
  ): GraphicsLike;
  lineStyle(width: number, color: number, alpha?: number): GraphicsLike;
  lineBetween(x1: number, y1: number, x2: number, y2: number): GraphicsLike;
  strokeCircle(x: number, y: number, r: number): GraphicsLike;
  clear(): GraphicsLike;
  setDepth(depth: number): GraphicsLike;
  setPosition(x: number, y: number): GraphicsLike;
  setAlpha?(alpha: number): GraphicsLike;
  destroy(): void;
}
export interface TextLike {
  setOrigin(x: number, y?: number): TextLike;
  setDepth(depth: number): TextLike;
  setColor(color: string): TextLike;
  setText(text: string | string[]): TextLike;
  setPosition(x: number, y: number): TextLike;
  destroy(): void;
}
export interface StageAddLike {
  graphics(): GraphicsLike;
  text(x: number, y: number, text: string, style?: Record<string, unknown>): TextLike;
}
export interface StageTweensLike {
  add(config: Record<string, unknown>): unknown;
  killTweensOf(target: unknown): void;
}
export interface StageContext {
  add: StageAddLike;
  tweens?: StageTweensLike;
}

// ── 调色板 / 深度 ──
const PIXEL_FONT = '"Ark Pixel 12", monospace';
const COLOR_RUNG = 0x243042;
const COLOR_RUNG_ACTIVE = 0x3a567f;
const COLOR_GLOW = 0xffd166;
const COLOR_LINE_NUM = '#7f8da3';
const COLOR_SRC_TEXT = '#cdd6e4';
const COLOR_SRC_ACTIVE = '#fff6da';
const COLOR_PUPPET_BODY = 0x4ea1ff;
const COLOR_PUPPET_HEAD = 0xffe0b3;
const COLOR_PUPPET_STUMBLE = 0xff5a5a;
const COLOR_BOARD_FILL = 0x2c2540;
const COLOR_BOARD_FLIP = 0x5a4a86;
const COLOR_BOARD_EDGE = 0x8b7bd0;
const COLOR_TRACK = 0x6e6450;
const COLOR_TRACK_RUNNER = 0xffcf5c;
const COLOR_FORK_IDLE = 0x55617a;
const COLOR_FORK_TRUE = 0x4caf50;
const COLOR_FORK_FALSE = 0xe06c75;
const COLOR_NOTICE_FILL = 0x14110a;
const COLOR_NOTICE_EDGE = 0xb9923f;
const COLOR_WORLD_CARD_FILL = 0x1b2230;
const COLOR_WORLD_CARD_EDGE = 0x3a4a66;
const COLOR_LAMP_PENDING_FILL = 0x2a2f3a;
const COLOR_LAMP_PENDING_EDGE = 0x49566f;
const COLOR_LAMP_DONE_FILL = 0x47b061;
const COLOR_LAMP_DONE_EDGE = 0x9ff0a6;
const COLOR_BANNER_FILL = 0x123a18;
const COLOR_BANNER_EDGE = 0xffd166;

const DEPTH_LADDER = 2;
const DEPTH_GLOW = 3;
const DEPTH_LADDER_TEXT = 4;
const DEPTH_PUPPET = 6;
const DEPTH_BOARD = 5;
const DEPTH_TRACK = 4;
const DEPTH_FORK = 4;
const DEPTH_NOTICE = 7;
const DEPTH_WORLD_CARD = 5;
const DEPTH_WORLD_LAMP = 6;
const DEPTH_WORLD_TEXT = 7;
const DEPTH_WORLD_BANNER = 9;

function applyAlpha(g: GraphicsLike, alpha: number): void {
  // 桩对象可能不实现 setAlpha，可选链兜底。
  g.setAlpha?.(alpha);
}

// ── 脚本梯 ──
export interface ScriptLadderHandle {
  /** 高亮当前发光行（1 基）。 */
  highlight(line: number): void;
  destroy(): void;
}

export function createScriptLadder(
  ctx: StageContext,
  layout: LadderLayout,
  lines: readonly string[],
  rungWidth = 300,
): ScriptLadderHandle {
  const add = ctx.add;
  const bars: GraphicsLike[] = [];
  const srcTexts: TextLike[] = [];
  const created: Array<{ destroy(): void }> = [];
  const rh = Math.max(10, layout.rungHeight - 4);

  for (const rung of layout.rungs) {
    const bar = add.graphics();
    bar.fillStyle(COLOR_RUNG, 0.85).fillRoundedRect(layout.x, rung.y - rh / 2, rungWidth, rh, 4);
    bar.setDepth(DEPTH_LADDER);
    bars.push(bar);
    created.push(bar);

    const num = add.text(layout.x + 8, rung.y, String(rung.line).padStart(2, ' '), {
      fontFamily: PIXEL_FONT,
      fontSize: '11px',
      color: COLOR_LINE_NUM,
    });
    num.setOrigin(0, 0.5).setDepth(DEPTH_LADDER_TEXT);
    created.push(num);

    const src = (lines[rung.line - 1] ?? '').replace(/\t/g, '  ');
    const srcText = add.text(layout.x + 34, rung.y, src, {
      fontFamily: PIXEL_FONT,
      fontSize: '12px',
      color: COLOR_SRC_TEXT,
    });
    srcText.setOrigin(0, 0.5).setDepth(DEPTH_LADDER_TEXT);
    srcTexts.push(srcText);
    created.push(srcText);
  }

  // 发光条：浮在当前行台阶下方，随 highlight 移动。
  const glow = add.graphics();
  glow.fillStyle(COLOR_GLOW, 0.16).fillRoundedRect(layout.x - 4, -rh / 2 - 2, rungWidth + 8, rh + 4, 5);
  glow.setDepth(DEPTH_GLOW);
  applyAlpha(glow, 0);
  created.push(glow);

  let activeIndex = -1;

  return {
    highlight(line: number): void {
      const idx = Math.max(1, Math.min(layout.lineCount, Math.floor(line))) - 1;
      if (layout.lineCount <= 0) return;
      if (activeIndex >= 0) {
        bars[activeIndex]?.clear().fillStyle(COLOR_RUNG, 0.85).fillRoundedRect(
          layout.x,
          (layout.rungs[activeIndex]?.y ?? 0) - rh / 2,
          rungWidth,
          rh,
          4,
        );
        srcTexts[activeIndex]?.setColor(COLOR_SRC_TEXT);
      }
      const y = ladderLineY(layout, idx + 1);
      bars[idx]?.clear().fillStyle(COLOR_RUNG_ACTIVE, 0.95).fillRoundedRect(
        layout.x,
        y - rh / 2,
        rungWidth,
        rh,
        4,
      );
      srcTexts[idx]?.setColor(COLOR_SRC_ACTIVE);
      glow.setPosition(0, y);
      applyAlpha(glow, 1);
      activeIndex = idx;
    },
    destroy(): void {
      for (const obj of created) {
        try {
          obj.destroy();
        } catch {
          // 桩对象 destroy 可能缺失，忽略。
        }
      }
      bars.length = 0;
      srcTexts.length = 0;
      created.length = 0;
    },
  };
}

// ── 傀儡（程序计数器） ──
export interface PuppetHandle {
  moveTo(x: number, y: number, durationMs?: number): void;
  setStumbled(stumbled: boolean): void;
  destroy(): void;
}

export function createPuppet(ctx: StageContext, start: { x: number; y: number }): PuppetHandle {
  const add = ctx.add;
  // 整个小人画在自身 (0,0) 附近，靠 setPosition 整体移动。
  const body = add.graphics();
  drawPuppet(body, COLOR_PUPPET_BODY);
  body.setDepth(DEPTH_PUPPET).setPosition(start.x, start.y);

  return {
    moveTo(x: number, y: number, durationMs = 240): void {
      if (ctx.tweens) {
        ctx.tweens.killTweensOf(body);
        ctx.tweens.add({
          targets: body,
          x,
          y,
          duration: Math.max(60, durationMs),
          ease: 'Quad.easeInOut',
        });
      } else {
        body.setPosition(x, y);
      }
    },
    setStumbled(stumbled: boolean): void {
      body.clear();
      drawPuppet(body, stumbled ? COLOR_PUPPET_STUMBLE : COLOR_PUPPET_BODY);
      if (stumbled) {
        // 头顶冒火花。
        body.fillStyle(0xfff04d, 1);
        body.fillTriangle(2, -22, 7, -30, 11, -22);
        body.fillTriangle(-9, -20, -5, -28, -1, -20);
      }
    },
    destroy(): void {
      try {
        ctx.tweens?.killTweensOf(body);
        body.destroy();
      } catch {
        // 忽略桩对象差异。
      }
    },
  };
}

function drawPuppet(g: GraphicsLike, bodyColor: number): void {
  g.fillStyle(0x000000, 0.18).fillCircle(0, 10, 9); // 投影
  g.fillStyle(bodyColor, 1).fillRoundedRect(-7, -8, 14, 18, 4); // 身体
  g.fillStyle(COLOR_PUPPET_HEAD, 1).fillCircle(0, -14, 7); // 头
}

// ── 变量牌（举牌/翻牌） ──
export interface VariableBoardsHandle {
  sync(state: TheaterState, slotFor: (index: number) => { x: number; y: number }): void;
  destroy(): void;
}

interface BoardObjects {
  panel: GraphicsLike;
  nameText: TextLike;
  valueText: TextLike;
}

export function createVariableBoards(
  ctx: StageContext,
  boardWidth = 130,
  boardHeight = 44,
): VariableBoardsHandle {
  const add = ctx.add;
  const boards = new Map<string, BoardObjects>();

  function drawPanel(panel: GraphicsLike, x: number, y: number, flipped: boolean): void {
    panel
      .clear()
      .fillStyle(COLOR_BOARD_EDGE, 1)
      .fillRoundedRect(x, y, boardWidth, boardHeight, 6)
      .fillStyle(flipped ? COLOR_BOARD_FLIP : COLOR_BOARD_FILL, 1)
      .fillRoundedRect(x + 2, y + 2, boardWidth - 4, boardHeight - 4, 5);
    panel.setDepth(DEPTH_BOARD);
  }

  return {
    sync(state: TheaterState, slotFor): void {
      const order = state.variableOrder;
      const present = new Set(order);
      // 移除已不存在的牌（理论上变量不消失，但防御）。
      for (const [name, objs] of boards) {
        if (!present.has(name)) {
          safeDestroy(objs);
          boards.delete(name);
        }
      }
      order.forEach((name, index) => {
        const cell = state.variables[name];
        if (!cell) return;
        const slot = slotFor(index);
        const flipped = state.changedVar === name;
        let objs = boards.get(name);
        if (!objs) {
          const panel = add.graphics();
          const nameText = add
            .text(slot.x + 8, slot.y + 7, `${cell.name}`, {
              fontFamily: PIXEL_FONT,
              fontSize: '11px',
              color: '#c7bdf0',
            })
            .setOrigin(0, 0)
            .setDepth(DEPTH_BOARD + 1);
          const valueText = add
            .text(slot.x + 8, slot.y + 23, fitValue(cell.value), {
              fontFamily: PIXEL_FONT,
              fontSize: '13px',
              color: '#ffffff',
            })
            .setOrigin(0, 0)
            .setDepth(DEPTH_BOARD + 1);
          objs = { panel, nameText, valueText };
          boards.set(name, objs);
        }
        drawPanel(objs.panel, slot.x, slot.y, flipped);
        objs.nameText.setPosition(slot.x + 8, slot.y + 7).setText(scopeLabel(cell));
        objs.valueText.setPosition(slot.x + 8, slot.y + 23).setText(fitValue(cell.value));
        if (flipped && ctx.tweens) {
          ctx.tweens.killTweensOf(objs.valueText);
          ctx.tweens.add({
            targets: objs.valueText,
            scaleX: { from: 0, to: 1 },
            duration: 200,
            ease: 'Back.easeOut',
          });
        }
      });
    },
    destroy(): void {
      for (const objs of boards.values()) {
        safeDestroy(objs);
      }
      boards.clear();
    },
  };
}

function scopeLabel(cell: VariableCell): string {
  return cell.scope === 'local' ? `${cell.name} (局部)` : cell.name;
}
function fitValue(value: string, max = 16): string {
  return value.length > max ? `${value.slice(0, max - 1)}…` : value;
}
function safeDestroy(objs: BoardObjects): void {
  for (const obj of [objs.panel, objs.nameText, objs.valueText]) {
    try {
      obj.destroy();
    } catch {
      // 忽略。
    }
  }
}

// ── 环形循环跑道 ──
export interface LoopTrackHandle {
  sync(loops: Record<string, LoopRuntimeState>): void;
  destroy(): void;
}

export function createLoopTrack(ctx: StageContext, track: LoopTrackLayout): LoopTrackHandle {
  const add = ctx.add;
  const ring = add.graphics();
  ring.lineStyle(6, COLOR_TRACK, 1).strokeCircle(track.cx, track.cy, track.radius);
  ring.setDepth(DEPTH_TRACK);

  const runner = add.graphics();
  runner.fillStyle(COLOR_TRACK_RUNNER, 1).fillCircle(0, 0, 7);
  runner.setDepth(DEPTH_TRACK + 1);
  const startPt = loopRunnerPosition(track, 0);
  runner.setPosition(startPt.x, startPt.y);
  applyAlpha(runner, 0);

  const label = add.text(track.cx, track.cy, '', {
    fontFamily: PIXEL_FONT,
    fontSize: '12px',
    color: '#ffe7a8',
    align: 'center',
  });
  label.setOrigin(0.5, 0.5).setDepth(DEPTH_TRACK + 2);

  return {
    sync(loops: Record<string, LoopRuntimeState>): void {
      const entries = Object.values(loops);
      if (entries.length === 0) {
        applyAlpha(runner, 0);
        label.setText('');
        return;
      }
      // 展示「圈数最多」的活跃循环（嵌套时聚焦最内层进度感最强者）。
      const active = entries.reduce((a, b) => (b.laps >= a.laps ? b : a));
      applyAlpha(runner, 1);
      const pt = loopRunnerPosition(track, active.laps * 0.999); // 每圈走满一周
      runner.setPosition(pt.x, pt.y);
      const varPart = active.varName ? `\n${active.varName}=${active.value ?? ''}` : '';
      label.setText(`${active.loopType} ×${active.laps}${varPart}`);
    },
    destroy(): void {
      for (const obj of [ring, runner, label]) {
        try {
          obj.destroy();
        } catch {
          // 忽略。
        }
      }
    },
  };
}

// ── 分支岔路口 ──
export interface BranchForkHandle {
  choose(result: boolean, cond: string): void;
  reset(): void;
  destroy(): void;
}

export function createBranchFork(ctx: StageContext, fork: ForkLayout): BranchForkHandle {
  const add = ctx.add;
  const paths = add.graphics();
  const marker = add.graphics();
  marker.fillStyle(0xffffff, 1).fillCircle(0, 0, 6).setDepth(DEPTH_FORK + 2);
  applyAlpha(marker, 0);

  const trueLabel = add
    .text(fork.trueX, fork.trueY + 12, 'True', {
      fontFamily: PIXEL_FONT,
      fontSize: '11px',
      color: '#9ad6a0',
    })
    .setOrigin(0.5, 0)
    .setDepth(DEPTH_FORK + 1);
  const falseLabel = add
    .text(fork.falseX, fork.falseY + 12, 'False', {
      fontFamily: PIXEL_FONT,
      fontSize: '11px',
      color: '#e6a3aa',
    })
    .setOrigin(0.5, 0)
    .setDepth(DEPTH_FORK + 1);

  function drawPaths(result: boolean | null): void {
    paths.clear().setDepth(DEPTH_FORK);
    paths.lineStyle(5, result === false ? COLOR_FORK_FALSE : COLOR_FORK_IDLE, result === false ? 1 : 0.5);
    paths.lineBetween(fork.originX, fork.originY, fork.falseX, fork.falseY);
    paths.lineStyle(5, result === true ? COLOR_FORK_TRUE : COLOR_FORK_IDLE, result === true ? 1 : 0.5);
    paths.lineBetween(fork.originX, fork.originY, fork.trueX, fork.trueY);
  }
  drawPaths(null);

  return {
    choose(result: boolean, cond: string): void {
      drawPaths(result);
      const pos = forkBranchPosition(fork, result);
      marker.setPosition(pos.x, pos.y);
      applyAlpha(marker, 1);
      const label = result ? trueLabel : falseLabel;
      label.setText(`${result ? 'True' : 'False'}${cond ? `: ${fitValue(cond, 20)}` : ''}`);
    },
    reset(): void {
      drawPaths(null);
      applyAlpha(marker, 0);
      trueLabel.setText('True');
      falseLabel.setText('False');
    },
    destroy(): void {
      for (const obj of [paths, marker, trueLabel, falseLabel]) {
        try {
          obj.destroy();
        } catch {
          // 忽略。
        }
      }
    },
  };
}

// ── 公告板（print / output） ──
export interface NoticeBoardHandle {
  post(lines: readonly string[]): void;
  destroy(): void;
}

export function createNoticeBoard(
  ctx: StageContext,
  layout: NoticeBoardLayout,
  maxLines = 4,
): NoticeBoardHandle {
  const add = ctx.add;
  const panel = add.graphics();
  panel
    .fillStyle(COLOR_NOTICE_EDGE, 1)
    .fillRoundedRect(layout.x, layout.y, layout.width, layout.height, 8)
    .fillStyle(COLOR_NOTICE_FILL, 0.96)
    .fillRoundedRect(layout.x + 3, layout.y + 3, layout.width - 6, layout.height - 6, 6)
    .setDepth(DEPTH_NOTICE);

  const title = add
    .text(layout.x + 12, layout.y + 8, '公告板', {
      fontFamily: PIXEL_FONT,
      fontSize: '11px',
      color: '#d9b061',
    })
    .setOrigin(0, 0)
    .setDepth(DEPTH_NOTICE + 1);

  const body = add
    .text(layout.x + 12, layout.y + 28, '', {
      fontFamily: PIXEL_FONT,
      fontSize: '12px',
      color: '#f3f0e2',
      lineSpacing: 3,
    })
    .setOrigin(0, 0)
    .setDepth(DEPTH_NOTICE + 1);

  return {
    post(lines: readonly string[]): void {
      const tail = lines.slice(-maxLines);
      body.setText(tail.join('\n'));
    },
    destroy(): void {
      for (const obj of [panel, title, body]) {
        try {
          obj.destroy();
        } catch {
          // 忽略。
        }
      }
    },
  };
}

/** 把 stumble 信息格式化成一行（公告板/提示复用）。 */
export function formatStumble(stumble: StumbleState | null): string {
  if (!stumble) return '';
  return `${stumble.excType}: ${stumble.message} (第 ${stumble.line} 行)`;
}

// ── 世界目标面板（world_effect：一排灯/格 + 全完成横幅） ──
/** 世界面板展示元信息（拼标题/成功横幅）。 */
export interface WorldPanelMeta {
  /** 目标人类可读名（如「路灯」）。 */
  noun: string;
  /** 可选：动作动词（如「点亮」）。 */
  actionVerb?: string;
  /** 可选：量词（如「盏」）。 */
  unit?: string;
}

export interface WorldPanelHandle {
  /** 按世界目标快照刷新每盏灯；world=null 时全部维持 pending（暗）。 */
  sync(world: WorldStageState | null, options?: { reducedMotion?: boolean }): void;
  destroy(): void;
}

interface LampObjects {
  tile: GraphicsLike;
  glyph: TextLike;
  num: TextLike;
  /** 上次渲染状态，用于「新点亮」判定（仅新 done 才脉冲）。 */
  lastState: 'done' | 'pending' | null;
}

/**
 * 世界目标面板：把每个目标画成一格灯——`done` 亮绿打勾、`pending` 灰暗显问号；
 * 全部 done 时在舞台中央亮成功横幅。失败/未达成 = 对应灯仍黑着，天然把「世界现状」
 * 摊给学生看（替代隐藏断言）。`targets` 为预扫得到的全部目标（升序），开场即全暗呈现，
 * 随播放逐一点亮。`reducedMotion` 时跳过脉冲/弹入补间，瞬时切换。
 */
export function createWorldPanel(
  ctx: StageContext,
  layout: WorldPanelLayout,
  targets: readonly number[],
  meta: WorldPanelMeta,
  dim: StageDimensions,
): WorldPanelHandle {
  const add = ctx.add;
  const created: Array<{ destroy(): void }> = [];
  const lamps = new Map<number, LampObjects>();
  const slotOf = new Map<number, number>();
  const hasTargets = targets.length > 0 && layout.count > 0;

  let titleText: TextLike | null = null;
  let banner: GraphicsLike | null = null;
  let bannerText: TextLike | null = null;
  let bannerShown = false;

  function hideBanner(): void {
    if (!banner || !bannerText) return;
    banner.clear();
    applyAlpha(banner, 0);
    bannerText.setText('');
    bannerShown = false;
  }

  function showBanner(doneCount: number, total: number, reduced: boolean): void {
    if (!banner || !bannerText) return;
    const verb = meta.actionVerb ?? '完成';
    const unit = meta.unit ?? '个';
    const label = `✓ 全部${verb}！${total} ${unit}${meta.noun}`;
    const w = Math.max(220, Math.min(dim.width - 40, label.length * 17 + 56));
    const h = 56;
    const bx = Math.round(dim.width / 2 - w / 2);
    const by = Math.round(dim.height / 2 - h / 2);
    banner
      .clear()
      .fillStyle(COLOR_BANNER_EDGE, 1)
      .fillRoundedRect(bx, by, w, h, 12)
      .fillStyle(COLOR_BANNER_FILL, 0.96)
      .fillRoundedRect(bx + 3, by + 3, w - 6, h - 6, 10)
      .setDepth(DEPTH_WORLD_BANNER);
    applyAlpha(banner, 1);
    bannerText.setText(label).setPosition(dim.width / 2, dim.height / 2);
    // 仅首次出现时弹入，避免每帧重复触发；reduce 时直接呈现。
    if (!reduced && ctx.tweens && !bannerShown) {
      ctx.tweens.killTweensOf(bannerText);
      ctx.tweens.add({
        targets: bannerText,
        scaleX: { from: 0.6, to: 1 },
        scaleY: { from: 0.6, to: 1 },
        duration: 260,
        ease: 'Back.easeOut',
      });
    }
    bannerShown = true;
  }

  if (hasTargets) {
    const pad = 10;
    const card = add.graphics();
    card
      .fillStyle(COLOR_WORLD_CARD_EDGE, 1)
      .fillRoundedRect(
        layout.x - pad,
        layout.y - pad - 18,
        layout.width + pad * 2,
        layout.height + pad * 2 + 18,
        8,
      )
      .fillStyle(COLOR_WORLD_CARD_FILL, 0.96)
      .fillRoundedRect(
        layout.x - pad + 2,
        layout.y - pad - 16,
        layout.width + pad * 2 - 4,
        layout.height + pad * 2 + 14,
        7,
      )
      .setDepth(DEPTH_WORLD_CARD);
    created.push(card);

    titleText = add
      .text(layout.x - pad + 6, layout.y - pad - 14, worldTitle(meta, 0, targets.length), {
        fontFamily: PIXEL_FONT,
        fontSize: '11px',
        color: '#cdd6e4',
      })
      .setOrigin(0, 0)
      .setDepth(DEPTH_WORLD_TEXT);
    created.push(titleText);

    targets.forEach((target, index) => {
      slotOf.set(target, index);
      const slot = worldCellSlot(layout, index);
      const tile = add.graphics();
      const glyph = add
        .text(slot.x + layout.cell / 2, slot.y + layout.cell / 2 - 3, '?', {
          fontFamily: PIXEL_FONT,
          fontSize: '15px',
          color: '#8794ad',
        })
        .setOrigin(0.5, 0.5)
        .setDepth(DEPTH_WORLD_TEXT);
      const num = add
        .text(slot.x + layout.cell / 2, slot.y + layout.cell - 1, `#${target}`, {
          fontFamily: PIXEL_FONT,
          fontSize: '9px',
          color: '#7f8da3',
        })
        .setOrigin(0.5, 1)
        .setDepth(DEPTH_WORLD_TEXT);
      const obj: LampObjects = { tile, glyph, num, lastState: null };
      drawLamp(obj, slot, layout.cell, 'pending');
      obj.lastState = 'pending';
      lamps.set(target, obj);
      created.push(tile, glyph, num);
    });

    banner = add.graphics();
    bannerText = add
      .text(dim.width / 2, dim.height / 2, '', {
        fontFamily: PIXEL_FONT,
        fontSize: '18px',
        color: '#eafff0',
        align: 'center',
      })
      .setOrigin(0.5, 0.5)
      .setDepth(DEPTH_WORLD_BANNER + 1);
    created.push(banner, bannerText);
    hideBanner();
  }

  return {
    sync(world: WorldStageState | null, options): void {
      if (!hasTargets) return;
      const reduced = options?.reducedMotion ?? false;
      const stateByTarget = new Map<number, 'done' | 'pending'>();
      let doneCount = 0;
      if (world) {
        for (const c of world.cells) {
          stateByTarget.set(c.target, c.state);
          if (c.state === 'done') doneCount += 1;
        }
      }
      const total = targets.length;
      for (const target of targets) {
        const obj = lamps.get(target);
        if (!obj) continue;
        const slot = worldCellSlot(layout, slotOf.get(target) ?? 0);
        const stateValue = stateByTarget.get(target) ?? 'pending';
        const was = obj.lastState;
        drawLamp(obj, slot, layout.cell, stateValue);
        if (stateValue === 'done' && was !== 'done' && !reduced && ctx.tweens) {
          ctx.tweens.killTweensOf(obj.glyph);
          ctx.tweens.add({
            targets: obj.glyph,
            scaleX: { from: 0.4, to: 1 },
            scaleY: { from: 0.4, to: 1 },
            duration: 220,
            ease: 'Back.easeOut',
          });
        }
        obj.lastState = stateValue;
      }
      titleText?.setText(worldTitle(meta, doneCount, total));
      if (total > 0 && doneCount === total) {
        showBanner(doneCount, total, reduced);
      } else {
        hideBanner();
      }
    },
    destroy(): void {
      for (const obj of created) {
        try {
          obj.destroy();
        } catch {
          // 忽略桩对象差异。
        }
      }
      created.length = 0;
      lamps.clear();
      slotOf.clear();
    },
  };
}

function drawLamp(
  obj: LampObjects,
  slot: { x: number; y: number },
  cell: number,
  state: 'done' | 'pending',
): void {
  const fill = state === 'done' ? COLOR_LAMP_DONE_FILL : COLOR_LAMP_PENDING_FILL;
  const edge = state === 'done' ? COLOR_LAMP_DONE_EDGE : COLOR_LAMP_PENDING_EDGE;
  obj.tile
    .clear()
    .fillStyle(edge, 1)
    .fillRoundedRect(slot.x, slot.y, cell, cell, 6)
    .fillStyle(fill, 1)
    .fillRoundedRect(slot.x + 2, slot.y + 2, cell - 4, cell - 4, 5)
    .setDepth(DEPTH_WORLD_LAMP);
  if (state === 'done') {
    // 灯芯高光，强化「亮起来」的可见变化。
    obj.tile.fillStyle(0xffffff, 0.4).fillCircle(slot.x + cell / 2, slot.y + cell / 2 - 3, Math.max(3, cell * 0.16));
  }
  obj.glyph.setText(state === 'done' ? '✓' : '?').setColor(state === 'done' ? '#08240f' : '#8794ad');
}

function worldTitle(meta: WorldPanelMeta, doneCount: number, total: number): string {
  return `世界目标 · ${meta.noun} ${doneCount}/${total}`;
}
