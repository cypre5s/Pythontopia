import { describe, expect, it } from 'vitest';
import {
  computeForkLayout,
  computeLadderLayout,
  computeLoopTrackLayout,
  computeNoticeBoardLayout,
  forkBranchPosition,
  ladderLineY,
  loopRunnerPosition,
  puppetPositionForLine,
  variableBoardSlot,
} from '@/game/codeTheater/stageLayout';

const DIM = { width: 960, height: 640 };

describe('stageLayout · script ladder', () => {
  it('produces one monotonically descending rung per line', () => {
    const layout = computeLadderLayout(5, DIM);
    expect(layout.rungs).toHaveLength(5);
    expect(layout.rungs[0]?.line).toBe(1);
    for (let i = 1; i < layout.rungs.length; i += 1) {
      expect(layout.rungs[i]!.y).toBeGreaterThan(layout.rungs[i - 1]!.y);
    }
  });

  it('clamps rung height: caps for few lines, compresses (but not below min) for many', () => {
    const few = computeLadderLayout(2, DIM);
    expect(few.rungHeight).toBeLessThanOrEqual(30);

    const many = computeLadderLayout(400, DIM);
    expect(many.rungHeight).toBeGreaterThanOrEqual(12);
    expect(many.rungHeight).toBeLessThanOrEqual(30);
    expect(many.rungs).toHaveLength(400);
  });

  it('ladderLineY clamps out-of-range lines to first/last rung', () => {
    const layout = computeLadderLayout(4, DIM);
    expect(ladderLineY(layout, 1)).toBe(layout.rungs[0]?.y);
    expect(ladderLineY(layout, 4)).toBe(layout.rungs[3]?.y);
    expect(ladderLineY(layout, 0)).toBe(layout.rungs[0]?.y); // 下界
    expect(ladderLineY(layout, 99)).toBe(layout.rungs[3]?.y); // 上界
    expect(ladderLineY(computeLadderLayout(0, DIM), 1)).toBe(layout.top);
  });

  it('puppet stands to the right of its line rung', () => {
    const layout = computeLadderLayout(3, DIM);
    const pos = puppetPositionForLine(layout, 2);
    expect(pos.x).toBeGreaterThan(layout.x);
    expect(pos.y).toBe(layout.rungs[1]?.y);
  });
});

describe('stageLayout · loop track', () => {
  it('keeps the ring on-screen', () => {
    const track = computeLoopTrackLayout(DIM);
    expect(track.cx + track.radius).toBeLessThanOrEqual(DIM.width);
    expect(track.cy - track.radius).toBeGreaterThanOrEqual(0);
    expect(track.radius).toBeGreaterThan(0);
  });

  it('runner sits on the circle; fraction 0 is at the top; laps wrap around', () => {
    const track = { cx: 100, cy: 100, radius: 40 };
    const top = loopRunnerPosition(track, 0);
    expect(top.x).toBeCloseTo(100, 5);
    expect(top.y).toBeCloseTo(60, 5); // 正上方

    const quarter = loopRunnerPosition(track, 0.25); // 顺时针 90° → 右侧
    expect(quarter.x).toBeCloseTo(140, 5);
    expect(quarter.y).toBeCloseTo(100, 5);

    // 任意小数圈数都落在半径上。
    for (const frac of [0.1, 1.3, 2.75, -0.4]) {
      const pt = loopRunnerPosition(track, frac);
      const r = Math.hypot(pt.x - track.cx, pt.y - track.cy);
      expect(r).toBeCloseTo(40, 5);
    }
    // 整数圈回到原点（每圈走满一周）。
    const oneLap = loopRunnerPosition(track, 1);
    expect(oneLap.x).toBeCloseTo(top.x, 5);
    expect(oneLap.y).toBeCloseTo(top.y, 5);
  });
});

describe('stageLayout · branch fork', () => {
  it('splits true to the right and false to the left, dropping down', () => {
    const fork = computeForkLayout(DIM);
    expect(fork.trueX).toBeGreaterThan(fork.originX);
    expect(fork.falseX).toBeLessThan(fork.originX);
    expect(fork.trueY).toBeGreaterThan(fork.originY);
    expect(fork.falseY).toBeGreaterThan(fork.originY);

    expect(forkBranchPosition(fork, true)).toEqual({ x: fork.trueX, y: fork.trueY });
    expect(forkBranchPosition(fork, false)).toEqual({ x: fork.falseX, y: fork.falseY });
  });
});

describe('stageLayout · variable boards & notice', () => {
  it('lays boards out in a column then wraps to the next column', () => {
    const perColumn = 3;
    const opts = { startX: 800, startY: 100, boardWidth: 100, boardHeight: 40, gap: 10, perColumn };
    const first = variableBoardSlot(0, DIM, opts);
    const second = variableBoardSlot(1, DIM, opts);
    expect(second.x).toBe(first.x); // 同列
    expect(second.y).toBeGreaterThan(first.y); // 下移

    const wrapped = variableBoardSlot(perColumn, DIM, opts);
    expect(wrapped.x).toBeLessThan(first.x); // 进入左侧新列
    expect(wrapped.y).toBe(first.y); // 回到列首
  });

  it('centers the notice board within the stage width', () => {
    const notice = computeNoticeBoardLayout(DIM);
    expect(notice.x).toBeGreaterThanOrEqual(0);
    expect(notice.x + notice.width).toBeLessThanOrEqual(DIM.width);
    expect(notice.x).toBeCloseTo((DIM.width - notice.width) / 2, 0);
    expect(notice.y + notice.height).toBeLessThanOrEqual(DIM.height);
  });
});
