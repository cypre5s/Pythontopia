import { describe, expect, it } from 'vitest';
import { buildConstellation } from '@/data/kcConstellation';
import { KC_TOTAL } from '@/data/rogueConfig';

describe('buildConstellation', () => {
  it('places all 65 KC as nodes within the canvas bounds', () => {
    const c = buildConstellation(1000, 680);
    expect(c.nodes).toHaveLength(KC_TOTAL);
    const codes = new Set(c.nodes.map((n) => n.code));
    expect(codes.size).toBe(KC_TOTAL);
    for (const n of c.nodes) {
      expect(n.x).toBeGreaterThanOrEqual(0);
      expect(n.x).toBeLessThanOrEqual(1000);
      expect(n.y).toBeGreaterThanOrEqual(0);
      expect(n.y).toBeLessThanOrEqual(680);
    }
  });

  it('every edge connects two existing nodes', () => {
    const c = buildConstellation();
    const codes = new Set(c.nodes.map((n) => n.code));
    for (const e of c.edges) {
      expect(codes.has(e.a)).toBe(true);
      expect(codes.has(e.b)).toBe(true);
    }
    expect(c.edges.length).toBeGreaterThan(0);
  });

  it('is deterministic across builds', () => {
    expect(buildConstellation()).toEqual(buildConstellation());
  });
});
