import { describe, expect, it } from 'vitest';
import { allKcCodes, chapterKcs, kcChapter, KC_TOTAL } from '@/data/rogueConfig';

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
