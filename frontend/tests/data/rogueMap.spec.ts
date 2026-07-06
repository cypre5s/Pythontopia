import { describe, expect, it } from 'vitest';
import { generateRogueMap, type ResolvedChallenge } from '@/data/rogueMap';
import type { RogueNode, RogueNodeKind } from '@/types/rogue';

// 确定性 RNG（LCG），让地图生成可复现。
function lcg(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0xffffffff;
  };
}

function stubResolve(kind: RogueNodeKind): ResolvedChallenge {
  if (kind === 'rest' || kind === 'chest') {
    return { kcCode: null, challengeKind: null, difficulty: 0 };
  }
  return { kcCode: 'PY43-KC-001', challengeKind: 'dual_track', difficulty: 2 };
}

function reachableFromStart(nodes: RogueNode[]): Set<string> {
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const start = nodes.find((n) => n.layer === 0);
  const seen = new Set<string>();
  const queue: string[] = start ? [start.id] : [];
  while (queue.length > 0) {
    const id = queue.shift() as string;
    if (seen.has(id)) continue;
    seen.add(id);
    for (const nxt of byId.get(id)?.next ?? []) queue.push(nxt);
  }
  return seen;
}

describe('generateRogueMap', () => {
  it('starts with a single battle node and ends with a single boss layer', () => {
    const map = generateRogueMap({ layers: 9, rng: lcg(42), resolveChallenge: stubResolve });
    const layer0 = map.nodes.filter((n) => n.layer === 0);
    const bossLayer = map.nodes.filter((n) => n.layer === map.layers - 1);
    expect(layer0).toHaveLength(1);
    expect(layer0[0]?.kind).toBe('battle');
    expect(layer0[0]?.state).toBe('available');
    expect(bossLayer).toHaveLength(1);
    expect(bossLayer[0]?.kind).toBe('boss');
  });

  it('every node is reachable from the start (connectivity guaranteed)', () => {
    for (const seed of [1, 7, 42, 99, 2026]) {
      const map = generateRogueMap({ layers: 9, rng: lcg(seed), resolveChallenge: stubResolve });
      const reachable = reachableFromStart(map.nodes);
      for (const node of map.nodes) {
        expect(reachable.has(node.id), `seed ${seed} node ${node.id} unreachable`).toBe(true);
      }
    }
  });

  it('non-boss nodes have outgoing edges; non-start nodes have incoming edges', () => {
    const map = generateRogueMap({ layers: 8, rng: lcg(123), resolveChallenge: stubResolve });
    const incoming = new Set(map.nodes.flatMap((n) => n.next));
    for (const node of map.nodes) {
      if (node.layer < map.layers - 1) {
        expect(node.next.length, `${node.id} should have outgoing`).toBeGreaterThan(0);
      }
      if (node.layer > 0) {
        expect(incoming.has(node.id), `${node.id} should have incoming`).toBe(true);
      }
    }
  });

  it('rest/chest nodes carry no challenge; challenge nodes carry kc + kind', () => {
    const map = generateRogueMap({ layers: 9, rng: lcg(5), resolveChallenge: stubResolve });
    for (const node of map.nodes) {
      if (node.kind === 'rest' || node.kind === 'chest') {
        expect(node.kcCode).toBeNull();
        expect(node.challengeKind).toBeNull();
      } else {
        expect(node.kcCode).toBe('PY43-KC-001');
        expect(node.challengeKind).toBe('dual_track');
      }
    }
  });
});
