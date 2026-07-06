import { describe, expect, it } from 'vitest';
import {
  allKcCodes,
  chapterKcs,
  computeRunDifficulty,
  kcChapter,
  KC_TOTAL,
} from '@/data/rogueConfig';

describe('rogueConfig KC↔chapter', () => {
  it('allKcCodes lists 65 KC in PY43-KC-### form', () => {
    const codes = allKcCodes();
    expect(codes).toHaveLength(KC_TOTAL);
    expect(codes[0]).toBe('PY43-KC-001');
    expect(codes[64]).toBe('PY43-KC-065');
  });

  it('kcChapter maps codes to chapters by range', () => {
    expect(kcChapter('PY43-KC-001')).toBe(1);
    expect(kcChapter('PY43-KC-007')).toBe(1);
    expect(kcChapter('PY43-KC-008')).toBe(2);
    expect(kcChapter('PY43-KC-016')).toBe(2);
    expect(kcChapter('PY43-KC-031')).toBe(4);
    expect(kcChapter('PY43-KC-065')).toBe(7);
  });

  it('kcChapter falls back to chapter 1 on garbage input', () => {
    expect(kcChapter('nonsense')).toBe(1);
  });

  it('chapterKcs returns the chapter slice and partitions all KC', () => {
    expect(chapterKcs(1)).toEqual([
      'PY43-KC-001', 'PY43-KC-002', 'PY43-KC-003', 'PY43-KC-004',
      'PY43-KC-005', 'PY43-KC-006', 'PY43-KC-007',
    ]);
    const union = [1, 2, 3, 4, 5, 6, 7].flatMap((c) => chapterKcs(c));
    expect(union.sort()).toEqual(allKcCodes().sort());
  });
});

describe('computeRunDifficulty (局内自适应)', () => {
  it('keeps base difficulty without momentum or failures', () => {
    expect(computeRunDifficulty(3, 0, 0)).toBe(3);
    expect(computeRunDifficulty(2, 2, 1)).toBe(2);
  });

  it('raises difficulty on a winning streak (+1 at 3, +2 at 6)', () => {
    expect(computeRunDifficulty(2, 3, 0)).toBe(3);
    expect(computeRunDifficulty(2, 6, 0)).toBe(4);
  });

  it('eases difficulty after two recent failures', () => {
    expect(computeRunDifficulty(3, 0, 2)).toBe(2);
    expect(computeRunDifficulty(3, 0, 1)).toBe(3);
  });

  it('clamps the result into 1..5', () => {
    expect(computeRunDifficulty(5, 6, 0)).toBe(5);
    expect(computeRunDifficulty(1, 0, 2)).toBe(1);
  });
});
