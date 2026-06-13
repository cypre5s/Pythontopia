/**
 * 指令时间轴播放器（纯 TS，无 Phaser / 无 DOM / 无 timer）。
 *
 * 这是代码剧场对外的**控制核心**：加载一个 `Trace` + 一份 `RenderCommand[]`，按 `step`
 * 顺序、各指令 `durationMs` 时长推进；对外暴露 play / pause / step / stepBack / seek /
 * 快进（setRate）/ tick。时间推进由外部驱动（Phaser 场景在 `update` 里调 `tick(delta)`，
 * Vue 组件可用 requestAnimationFrame 或测试里手动 tick），播放器本身不持有任何计时器，
 * 因此可被无头单测精确驱动。
 *
 * 时序模型：第 i 条指令在「累计时间达到自身 durationMs」后被应用（appliedCount += 1）。
 * 即每条指令的 durationMs = 把舞台动画「过渡进该指令状态」所需时间。
 *
 * 复用：Vue 组件 / debug-theater 可直接 `new TimelinePlayer()` 做无头逻辑回放；
 * Phaser 场景持有一个共享实例驱动绘制（见 CodeTheaterScene 的模块级桥）。
 */
import type { RenderCommand, Trace } from '@/services/codeTheater/types';
import { applyCommand, computeStateAt, initialTheaterState, type TheaterState } from './theaterState';

export type PlaybackStatus = 'idle' | 'playing' | 'paused' | 'ended';

/** 对外广播的一帧快照（控制层/渲染层只读）。 */
export interface TimelineFrame {
  status: PlaybackStatus;
  /** 已应用指令数（0..total），即当前播放头。 */
  appliedCount: number;
  total: number;
  /** 最近一条已应用指令的 step；-1 = 尚未开始。 */
  step: number;
  /** 播放进度 0..1（total=0 时为 0）。 */
  progress: number;
  /** 当前播放速率倍数。 */
  rate: number;
  /** 机关广场快照。 */
  state: TheaterState;
}

export type TimelineListener = (frame: TimelineFrame) => void;

export interface LoadOptions {
  /** 源轨迹（提供脚本梯文本、stdout、死循环截断标记）。 */
  trace?: Trace;
  /** 加载后立即播放。 */
  autoplay?: boolean;
  /** 初始速率（默认 1）。 */
  rate?: number;
}

export const MIN_RATE = 0.25;
export const MAX_RATE = 16;
/** payload 未给 durationMs（或 ≤0）时的兜底时长。 */
export const DEFAULT_DURATION_MS = 600;
/** 「快进」默认速率。 */
export const FAST_FORWARD_RATE = 4;

function clampRate(rate: number): number {
  if (!Number.isFinite(rate)) return 1;
  return Math.max(MIN_RATE, Math.min(MAX_RATE, rate));
}

export class TimelinePlayer {
  private commands: RenderCommand[] = [];
  private trace: Trace | null = null;
  private appliedCount = 0;
  private state: TheaterState = initialTheaterState();
  private status: PlaybackStatus = 'idle';
  private rate = 1;
  /** 朝向「下一条待应用指令」累计的时间（毫秒）。 */
  private elapsedMs = 0;
  private listeners = new Set<TimelineListener>();

  /** 加载指令序列（按 step 稳定排序）与可选 Trace。重置播放头。 */
  load(commands: readonly RenderCommand[], options: LoadOptions = {}): void {
    // 稳定排序：保证全局按 step 播放，同 step 内保留 mapper 的产出顺序（ES2019 稳定排序）。
    this.commands = [...commands].sort((a, b) => a.step - b.step);
    this.trace = options.trace ?? null;
    this.appliedCount = 0;
    this.state = initialTheaterState();
    this.elapsedMs = 0;
    this.rate = clampRate(options.rate ?? 1);
    if (this.commands.length === 0) {
      this.status = 'ended';
    } else {
      this.status = options.autoplay ? 'playing' : 'idle';
    }
    this.emit();
  }

  /** 开始/继续自动播放；若已在结尾则从头重放。 */
  play(): void {
    if (this.commands.length === 0) {
      this.status = 'ended';
      this.emit();
      return;
    }
    if (this.appliedCount >= this.commands.length) {
      this.appliedCount = 0;
      this.state = initialTheaterState();
      this.elapsedMs = 0;
    }
    this.status = 'playing';
    this.emit();
  }

  /** 暂停自动播放（保留播放头）。 */
  pause(): void {
    if (this.status === 'playing') {
      this.status = 'paused';
      this.emit();
    }
  }

  /** play/pause 切换。 */
  toggle(): void {
    if (this.status === 'playing') {
      this.pause();
    } else {
      this.play();
    }
  }

  /**
   * 由外部时钟驱动推进（毫秒）。仅在 playing 时生效。
   * 累计时间足够即逐条应用指令（一帧可跨过多条短指令）。
   */
  tick(deltaMs: number): void {
    if (this.status !== 'playing') return;
    if (this.appliedCount >= this.commands.length) {
      this.finishIfDone();
      return;
    }
    let budget = this.elapsedMs + Math.max(0, deltaMs) * this.rate;
    let advanced = false;
    while (this.appliedCount < this.commands.length) {
      const cmd = this.commands[this.appliedCount];
      if (!cmd) break;
      const duration = cmd.durationMs > 0 ? cmd.durationMs : DEFAULT_DURATION_MS;
      if (budget < duration) break;
      budget -= duration;
      this.state = applyCommand(this.state, cmd);
      this.appliedCount += 1;
      advanced = true;
    }
    this.elapsedMs = budget;
    if (this.appliedCount >= this.commands.length) {
      this.status = 'ended';
      this.elapsedMs = 0;
      advanced = true;
    }
    if (advanced) this.emit();
  }

  /** 单步前进一条指令（忽略时长）。返回是否真的前进。 */
  step(): boolean {
    if (this.appliedCount >= this.commands.length) return false;
    const cmd = this.commands[this.appliedCount];
    if (!cmd) return false;
    this.state = applyCommand(this.state, cmd);
    this.appliedCount += 1;
    this.elapsedMs = 0;
    this.status = this.appliedCount >= this.commands.length ? 'ended' : 'paused';
    this.emit();
    return true;
  }

  /** 单步后退一条指令（从头重放到目标）。返回是否真的后退。 */
  stepBack(): boolean {
    if (this.appliedCount <= 0) return false;
    this.appliedCount -= 1;
    this.state = computeStateAt(this.commands, this.appliedCount);
    this.elapsedMs = 0;
    this.status = this.appliedCount === 0 ? 'idle' : 'paused';
    this.emit();
    return true;
  }

  /** 跳转到「已应用 count 条」的位置（0..total）。播放中跳转保持播放。 */
  seek(appliedCount: number): void {
    const target = Math.max(0, Math.min(Math.floor(appliedCount), this.commands.length));
    this.appliedCount = target;
    this.state = computeStateAt(this.commands, target);
    this.elapsedMs = 0;
    if (this.commands.length > 0 && target >= this.commands.length) {
      this.status = 'ended';
    } else if (this.status === 'ended') {
      this.status = target === 0 ? 'idle' : 'paused';
    }
    this.emit();
  }

  /** 跳到结尾（直接呈现最终态）。 */
  seekToEnd(): void {
    this.seek(this.commands.length);
  }

  /** 回到开头（不自动播放）。 */
  reset(): void {
    this.appliedCount = 0;
    this.state = initialTheaterState();
    this.elapsedMs = 0;
    this.status = this.commands.length === 0 ? 'ended' : 'idle';
    this.emit();
  }

  /** 设置播放速率（快进/慢放），自动钳制到 [MIN_RATE, MAX_RATE]。 */
  setRate(rate: number): void {
    const clamped = clampRate(rate);
    if (clamped !== this.rate) {
      this.rate = clamped;
      this.emit();
    }
  }

  /** 快进：把速率设为 FAST_FORWARD_RATE（或自定义）。 */
  fastForward(rate: number = FAST_FORWARD_RATE): void {
    this.setRate(rate);
  }

  // ── 只读访问 ──
  getState(): TheaterState {
    return this.state;
  }
  getStatus(): PlaybackStatus {
    return this.status;
  }
  getRate(): number {
    return this.rate;
  }
  getAppliedCount(): number {
    return this.appliedCount;
  }
  getTotal(): number {
    return this.commands.length;
  }
  getProgress(): number {
    return this.commands.length === 0 ? 0 : this.appliedCount / this.commands.length;
  }
  getCommands(): readonly RenderCommand[] {
    return this.commands;
  }
  getTrace(): Trace | null {
    return this.trace;
  }
  /** 脚本梯文本行（来自 Trace.source；无 Trace 时为空）。 */
  getSourceLines(): string[] {
    if (!this.trace) return [];
    return this.trace.source.split('\n');
  }
  /** 是否因步数预算（死循环）被截断——stage 据此演「原地无限踱步」。 */
  isTruncated(): boolean {
    return this.trace?.truncated ?? false;
  }
  getFrame(): TimelineFrame {
    return this.snapshot();
  }

  /** 订阅帧更新；订阅即收到当前帧。返回取消订阅函数。 */
  subscribe(listener: TimelineListener): () => void {
    this.listeners.add(listener);
    listener(this.snapshot());
    return () => {
      this.listeners.delete(listener);
    };
  }

  private finishIfDone(): void {
    if (this.status !== 'ended') {
      this.status = 'ended';
      this.emit();
    }
  }

  private snapshot(): TimelineFrame {
    const total = this.commands.length;
    return {
      status: this.status,
      appliedCount: this.appliedCount,
      total,
      step: this.state.lastStep,
      progress: total === 0 ? 0 : this.appliedCount / total,
      rate: this.rate,
      state: this.state,
    };
  }

  private emit(): void {
    if (this.listeners.size === 0) return;
    const frame = this.snapshot();
    for (const listener of this.listeners) {
      listener(frame);
    }
  }
}
