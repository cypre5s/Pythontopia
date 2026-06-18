import { describe, expect, it, vi } from 'vitest';
import { createWorldPanel, type StageContext } from '@/game/codeTheater/stagePrimitives';
import { computeWorldPanelLayout } from '@/game/codeTheater/stageLayout';
import type { WorldStageState, WorldTargetCell } from '@/game/codeTheater/theaterState';

const DIM = { width: 960, height: 640 };

interface GraphicsRec {
  fills: number[];
  alpha: number | null;
}
interface TextRec {
  /** setText 历史（含构造时的初值）。 */
  texts: string[];
}

/** 记录式桩 ctx：graphics 记录填充色 / alpha，text 记录文本历史，便于行为断言。 */
function makeRecordingCtx() {
  const graphicsList: GraphicsRec[] = [];
  const textList: TextRec[] = [];
  const tweenAdd = vi.fn();
  const killTweensOf = vi.fn();

  function graphics() {
    const rec: GraphicsRec = { fills: [], alpha: null };
    const g: Record<string, unknown> = {
      fillStyle: vi.fn((c: number) => {
        rec.fills.push(c);
        return g;
      }),
      fillCircle: vi.fn(() => g),
      fillRoundedRect: vi.fn(() => g),
      fillTriangle: vi.fn(() => g),
      lineStyle: vi.fn(() => g),
      lineBetween: vi.fn(() => g),
      strokeCircle: vi.fn(() => g),
      clear: vi.fn(() => {
        rec.fills.length = 0;
        return g;
      }),
      setDepth: vi.fn(() => g),
      setPosition: vi.fn(() => g),
      setAlpha: vi.fn((a: number) => {
        rec.alpha = a;
        return g;
      }),
      destroy: vi.fn(),
    };
    graphicsList.push(rec);
    return g;
  }

  function text(_x: number, _y: number, str: string) {
    const rec: TextRec = { texts: [str] };
    const t: Record<string, unknown> = {
      setOrigin: vi.fn(() => t),
      setDepth: vi.fn(() => t),
      setColor: vi.fn(() => t),
      setText: vi.fn((s: string) => {
        rec.texts.push(s);
        return t;
      }),
      setPosition: vi.fn(() => t),
      destroy: vi.fn(),
    };
    textList.push(rec);
    return t;
  }

  const ctx = {
    add: { graphics, text },
    tweens: { add: tweenAdd, killTweensOf },
  } as unknown as StageContext;

  return { ctx, graphicsList, textList, tweenAdd };
}

function makeWorld(cells: Array<[number, 'done' | 'pending']>, noun = '路灯'): WorldStageState {
  const list: WorldTargetCell[] = cells.map(([target, state]) => ({ target, noun, state }));
  const doneCount = list.filter((c) => c.state === 'done').length;
  return {
    goal: 'coverage',
    noun,
    actionVerb: '点亮',
    unit: '盏',
    cells: list,
    doneCount,
    totalCount: list.length,
    allDone: list.length > 0 && doneCount === list.length,
  };
}

/** 取出「灯格字形」文本（构造初值为 '?' 的那些）。 */
function lampGlyphs(textList: TextRec[]): TextRec[] {
  return textList.filter((r) => r.texts[0] === '?');
}
function lastText(rec: TextRec): string {
  return rec.texts[rec.texts.length - 1] ?? '';
}
function findText(textList: TextRec[], pred: (t: string) => boolean): string | undefined {
  for (const rec of textList) {
    for (const t of rec.texts) {
      if (pred(t)) return t;
    }
  }
  return undefined;
}

describe('createWorldPanel · lamps', () => {
  it('pre-draws every target as pending (dark, "?") before any sync', () => {
    const { ctx, textList } = makeRecordingCtx();
    const layout = computeWorldPanelLayout(4, DIM);
    createWorldPanel(ctx, layout, [1, 2, 3, 4], { noun: '路灯' }, DIM);

    const glyphs = lampGlyphs(textList);
    expect(glyphs).toHaveLength(4);
    expect(glyphs.every((g) => lastText(g) === '?')).toBe(true);
    // 暂无成功横幅文案。
    expect(findText(textList, (t) => t.includes('全部'))).toBeUndefined();
  });

  it('lights the done lamps (✓) and keeps pending ones dark (?); pulse tween fires', () => {
    const { ctx, textList, tweenAdd } = makeRecordingCtx();
    const layout = computeWorldPanelLayout(4, DIM);
    const handle = createWorldPanel(ctx, layout, [1, 2, 3, 4], { noun: '路灯', actionVerb: '点亮', unit: '盏' }, DIM);

    handle.sync(makeWorld([[1, 'done'], [2, 'done'], [3, 'done'], [4, 'pending']]), {
      reducedMotion: false,
    });

    const lastTexts = lampGlyphs(textList).map(lastText);
    expect(lastTexts).toEqual(['✓', '✓', '✓', '?']);
    expect(tweenAdd).toHaveBeenCalled(); // 新点亮 → 脉冲
    expect(findText(textList, (t) => t.includes('全部'))).toBeUndefined(); // 未全亮，无横幅
  });

  it('shows the success banner with verb/unit/noun once all targets are done', () => {
    const { ctx, textList } = makeRecordingCtx();
    const layout = computeWorldPanelLayout(3, DIM);
    const handle = createWorldPanel(ctx, layout, [1, 2, 3], { noun: '路灯', actionVerb: '点亮', unit: '盏' }, DIM);

    handle.sync(makeWorld([[1, 'done'], [2, 'done'], [3, 'done']]), { reducedMotion: false });

    const banner = findText(textList, (t) => t.includes('全部'));
    expect(banner).toBeDefined();
    expect(banner).toContain('点亮');
    expect(banner).toContain('盏');
    expect(banner).toContain('路灯');
  });

  it('respects reduced motion: instant switch, no pulse / pop tween', () => {
    const { ctx, textList, tweenAdd } = makeRecordingCtx();
    const layout = computeWorldPanelLayout(2, DIM);
    const handle = createWorldPanel(ctx, layout, [1, 2], { noun: '城门' }, DIM);

    handle.sync(makeWorld([[1, 'done'], [2, 'done']], '城门'), { reducedMotion: true });

    expect(tweenAdd).not.toHaveBeenCalled();
    // 状态仍可见：两盏都点亮，且横幅出现（只是没有补间）。
    expect(lampGlyphs(textList).map(lastText)).toEqual(['✓', '✓']);
    expect(findText(textList, (t) => t.includes('全部'))).toBeDefined();
  });

  it('hides the banner again if a later snapshot is no longer all-done', () => {
    const { ctx, textList } = makeRecordingCtx();
    const layout = computeWorldPanelLayout(2, DIM);
    const handle = createWorldPanel(ctx, layout, [1, 2], { noun: '路灯' }, DIM);

    handle.sync(makeWorld([[1, 'done'], [2, 'done']]), { reducedMotion: true });
    const bannerRec = textList.find((r) => r.texts.some((t) => t.includes('全部')));
    expect(bannerRec).toBeDefined();

    handle.sync(makeWorld([[1, 'done'], [2, 'pending']]), { reducedMotion: true });
    expect(lastText(bannerRec!)).toBe(''); // 横幅文案被清空
  });

  it('is inert for zero targets and safe to destroy', () => {
    const { ctx, graphicsList, textList } = makeRecordingCtx();
    const layout = computeWorldPanelLayout(0, DIM);
    const handle = createWorldPanel(ctx, layout, [], { noun: '目标' }, DIM);

    expect(graphicsList).toHaveLength(0);
    expect(textList).toHaveLength(0);
    expect(() => handle.sync(null, {})).not.toThrow();
    expect(() => handle.sync(makeWorld([]), {})).not.toThrow();
    expect(() => handle.destroy()).not.toThrow();
  });
});
