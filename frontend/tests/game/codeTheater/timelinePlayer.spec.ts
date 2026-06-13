import { describe, expect, it, vi } from 'vitest';
import {
  FAST_FORWARD_RATE,
  MAX_RATE,
  MIN_RATE,
  TimelinePlayer,
  type TimelineFrame,
} from '@/game/codeTheater/timelinePlayer';
import { cmd, makeTrace, SUM_LOOP_SOURCE, sumLoopCommands } from './_fixtures';

describe('TimelinePlayer · load', () => {
  it('is idle with totals after loading; ended when empty', () => {
    const p = new TimelinePlayer();
    p.load(sumLoopCommands());
    expect(p.getStatus()).toBe('idle');
    expect(p.getTotal()).toBe(9);
    expect(p.getAppliedCount()).toBe(0);
    expect(p.getProgress()).toBe(0);

    const empty = new TimelinePlayer();
    empty.load([]);
    expect(empty.getStatus()).toBe('ended');
    expect(empty.getTotal()).toBe(0);
  });

  it('sorts commands by step but keeps insertion order within the same step (stable)', () => {
    const p = new TimelinePlayer();
    p.load([
      cmd(2, 'speak', { text: 'last' }),
      cmd(0, 'move_to_line', { line: 1 }),
      cmd(1, 'set_var', { varName: 'x', value: '1' }),
      cmd(1, 'move_to_line', { line: 2 }), // 同 step，应排在 set_var 之后
    ]);
    const steps = p.getCommands().map((c) => c.step);
    expect(steps).toEqual([0, 1, 1, 2]);
    const sameStep = p.getCommands().filter((c) => c.step === 1).map((c) => c.action);
    expect(sameStep).toEqual(['set_var', 'move_to_line']);
  });

  it('exposes source lines and truncation flag from the trace', () => {
    const p = new TimelinePlayer();
    p.load(sumLoopCommands(), { trace: makeTrace(SUM_LOOP_SOURCE, { truncated: true }) });
    expect(p.getSourceLines()).toHaveLength(4);
    expect(p.getSourceLines()[1]).toBe('for i in range(3):');
    expect(p.isTruncated()).toBe(true);
  });
});

describe('TimelinePlayer · time-driven playback', () => {
  it('applies a command only after its durationMs of playing time elapses', () => {
    const p = new TimelinePlayer();
    p.load([cmd(0, 'move_to_line', { line: 1 }, 100), cmd(1, 'move_to_line', { line: 2 }, 100)]);
    p.play();
    expect(p.getStatus()).toBe('playing');

    p.tick(60);
    expect(p.getAppliedCount()).toBe(0); // 还没到 100ms

    p.tick(60); // 累计 120ms ≥ 100 → 应用第 0 条，余 20ms
    expect(p.getAppliedCount()).toBe(1);
    expect(p.getState().currentLine).toBe(1);

    p.tick(60); // 20 + 60 = 80 < 100
    expect(p.getAppliedCount()).toBe(1);
    p.tick(40); // 120 ≥ 100 → 应用第 1 条
    expect(p.getAppliedCount()).toBe(2);
    expect(p.getStatus()).toBe('ended');
  });

  it('can apply several short commands within one large delta frame', () => {
    const p = new TimelinePlayer();
    p.load(sumLoopCommands());
    p.play();
    p.tick(100_000); // 一帧吃掉全部
    expect(p.getAppliedCount()).toBe(9);
    expect(p.getStatus()).toBe('ended');
    expect(p.getState().variables.total?.value).toBe('3');
  });

  it('does nothing on tick while paused/idle', () => {
    const p = new TimelinePlayer();
    p.load(sumLoopCommands());
    p.tick(10_000); // idle
    expect(p.getAppliedCount()).toBe(0);
    p.play();
    p.pause();
    p.tick(10_000); // paused
    expect(p.getAppliedCount()).toBe(0);
  });

  it('rate multiplies elapsed time (fast-forward); rate is clamped', () => {
    const p = new TimelinePlayer();
    p.load([cmd(0, 'move_to_line', { line: 1 }, 100)]);
    p.setRate(2);
    p.play();
    p.tick(50); // 50 * 2 = 100 ≥ 100
    expect(p.getAppliedCount()).toBe(1);

    p.fastForward();
    expect(p.getRate()).toBe(FAST_FORWARD_RATE);
    p.setRate(999);
    expect(p.getRate()).toBe(MAX_RATE);
    p.setRate(0);
    expect(p.getRate()).toBe(MIN_RATE);
  });

  it('replays from the start when play() is pressed after ending', () => {
    const p = new TimelinePlayer();
    p.load(sumLoopCommands());
    p.play();
    p.tick(100_000);
    expect(p.getStatus()).toBe('ended');
    p.play();
    expect(p.getStatus()).toBe('playing');
    expect(p.getAppliedCount()).toBe(0);
    expect(p.getState().lastStep).toBe(-1);
  });
});

describe('TimelinePlayer · manual controls', () => {
  it('step / stepBack walk one command and stay paused', () => {
    const p = new TimelinePlayer();
    p.load(sumLoopCommands());
    expect(p.step()).toBe(true);
    expect(p.getAppliedCount()).toBe(1);
    expect(p.getStatus()).toBe('paused');
    expect(p.getState().variables.total?.value).toBe('0');

    expect(p.stepBack()).toBe(true);
    expect(p.getAppliedCount()).toBe(0);
    expect(p.getStatus()).toBe('idle');
    expect(p.getState().variables.total).toBeUndefined();

    expect(p.stepBack()).toBe(false); // 已到开头
  });

  it('seek jumps to an arbitrary command index and recomputes state', () => {
    const p = new TimelinePlayer();
    p.load(sumLoopCommands());
    p.seek(6); // 应用 step 0..5
    expect(p.getAppliedCount()).toBe(6);
    expect(p.getState().loops['for@2']?.laps).toBe(3);
    expect(p.getState().variables.total?.value).toBe('1');

    p.seekToEnd();
    expect(p.getAppliedCount()).toBe(9);
    expect(p.getStatus()).toBe('ended');

    p.seek(2); // 从结尾往回 seek → paused
    expect(p.getStatus()).toBe('paused');
    expect(p.getAppliedCount()).toBe(2);

    p.seek(99); // 越界钳制
    expect(p.getAppliedCount()).toBe(9);
  });

  it('reset returns to the very beginning', () => {
    const p = new TimelinePlayer();
    p.load(sumLoopCommands());
    p.seekToEnd();
    p.reset();
    expect(p.getAppliedCount()).toBe(0);
    expect(p.getStatus()).toBe('idle');
    expect(p.getState().lastStep).toBe(-1);
  });

  it('step at the end returns false and marks ended', () => {
    const p = new TimelinePlayer();
    p.load([cmd(0, 'speak', { text: 'x' })]);
    expect(p.step()).toBe(true);
    expect(p.getStatus()).toBe('ended');
    expect(p.step()).toBe(false);
  });
});

describe('TimelinePlayer · subscription', () => {
  it('notifies subscribers immediately and on every change, and unsubscribes', () => {
    const p = new TimelinePlayer();
    p.load(sumLoopCommands());
    const frames: TimelineFrame[] = [];
    const unsub = p.subscribe((f) => frames.push(f));

    expect(frames).toHaveLength(1); // 订阅即收到当前帧
    expect(frames[0]?.appliedCount).toBe(0);

    p.step();
    expect(frames.at(-1)?.appliedCount).toBe(1);
    expect(frames.at(-1)?.progress).toBeCloseTo(1 / 9, 5);

    unsub();
    p.step();
    expect(frames.at(-1)?.appliedCount).toBe(1); // 取消订阅后不再收到
  });

  it('frame carries status, rate, step and a live state snapshot', () => {
    const p = new TimelinePlayer();
    p.load(sumLoopCommands(), { rate: 2 });
    const listener = vi.fn();
    p.subscribe(listener);
    p.seekToEnd();
    const frame = p.getFrame();
    expect(frame.status).toBe('ended');
    expect(frame.rate).toBe(2);
    expect(frame.step).toBe(8);
    expect(frame.total).toBe(9);
    expect(frame.state.variables.total?.value).toBe('3');
    expect(listener).toHaveBeenCalled();
  });
});

describe('TimelinePlayer · autoplay', () => {
  it('starts playing immediately when autoplay is set', () => {
    const p = new TimelinePlayer();
    p.load(sumLoopCommands(), { autoplay: true });
    expect(p.getStatus()).toBe('playing');
    p.tick(100_000);
    expect(p.getStatus()).toBe('ended');
  });
});
