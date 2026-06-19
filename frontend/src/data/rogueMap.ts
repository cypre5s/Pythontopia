// 无尽远征地图生成（纯函数，可注入 RNG + KC 解析器，便于单测）。
//
// 产出一张分层 DAG：layer 0 单起点（battle），最后一层单 boss，中间层 2..4 个节点，
// 每 3 层保证一个营地（rest）。连边保证「每个下层节点都有入边、每个上层节点都有出边」→ 必可达 boss。

import type { ArenaLevelKind } from '@/types/arena';
import type { RogueMap, RogueNode, RogueNodeKind } from '@/types/rogue';
import { EXPEDITION_LAYERS, LAYER_MAX_WIDTH, LAYER_MIN_WIDTH } from './rogueConfig';

export interface ResolvedChallenge {
  kcCode: string | null;
  challengeKind: ArenaLevelKind | null;
  difficulty: number;
}

export interface GenerateRogueMapOptions {
  /** 中间区段层数（含起点层；boss 层会再追加一层）。 */
  layers?: number;
  /** 随机源（默认 Math.random），测试可注入确定性序列。 */
  rng?: () => number;
  /** 给定节点类型 + 层号 → KC / 题型 / 难度（由 store 注入：知识前沿选择器）。 */
  resolveChallenge: (kind: RogueNodeKind, layer: number) => ResolvedChallenge;
}

function pickKindForLayer(layer: number, midLayers: number, rng: () => number): RogueNodeKind {
  const t = midLayers > 0 ? layer / midLayers : 0;
  const pool: RogueNodeKind[] = ['battle', 'battle', 'event'];
  if (t > 0.25) pool.push('bug');
  if (t > 0.3) pool.push('chest');
  if (t > 0.55) pool.push('elite');
  return pool[Math.floor(rng() * pool.length)] ?? 'battle';
}

export function generateRogueMap(opts: GenerateRogueMapOptions): RogueMap {
  const rng = opts.rng ?? Math.random;
  const midLayers = Math.max(3, opts.layers ?? EXPEDITION_LAYERS);
  const totalLayers = midLayers + 1; // + boss 层
  const randInt = (lo: number, hi: number) => lo + Math.floor(rng() * (hi - lo + 1));

  // 1) 每层节点类型
  const layerKinds: RogueNodeKind[][] = [];
  for (let layer = 0; layer < totalLayers; layer++) {
    if (layer === 0) {
      layerKinds.push(['battle']);
      continue;
    }
    if (layer === totalLayers - 1) {
      layerKinds.push(['boss']);
      continue;
    }
    const width = randInt(LAYER_MIN_WIDTH, LAYER_MAX_WIDTH);
    const forceRest = layer % 3 === 0; // 每 3 层一个营地
    const kinds: RogueNodeKind[] = [];
    for (let col = 0; col < width; col++) {
      kinds.push(forceRest && col === 0 ? 'rest' : pickKindForLayer(layer, midLayers, rng));
    }
    layerKinds.push(kinds);
  }

  // 2) 建节点
  const byLayer: RogueNode[][] = [];
  const nodes: RogueNode[] = [];
  layerKinds.forEach((kinds, layer) => {
    const row: RogueNode[] = kinds.map((kind, col) => {
      const resolved = opts.resolveChallenge(kind, layer);
      return {
        id: `L${layer}-${col}`,
        layer,
        col,
        kind,
        kcCode: resolved.kcCode,
        challengeKind: resolved.challengeKind,
        difficulty: resolved.difficulty,
        next: [],
        state: layer === 0 ? 'available' : 'locked',
      } satisfies RogueNode;
    });
    byLayer.push(row);
    nodes.push(...row);
  });

  // 3) 连边：按归一化列位置取最近 1..2 个；再补齐让下层每个节点都有入边
  const posOf = (n: RogueNode, len: number): number => (len <= 1 ? 0.5 : n.col / (len - 1));
  for (let layer = 0; layer < totalLayers - 1; layer++) {
    const cur = byLayer[layer] ?? [];
    const nxt = byLayer[layer + 1] ?? [];
    for (const node of cur) {
      const p = posOf(node, cur.length);
      const sorted = [...nxt].sort(
        (a, b) => Math.abs(posOf(a, nxt.length) - p) - Math.abs(posOf(b, nxt.length) - p),
      );
      const links = 1 + (rng() < 0.45 && sorted.length > 1 ? 1 : 0);
      node.next = sorted.slice(0, links).map((n) => n.id);
    }
    const covered = new Set(cur.flatMap((n) => n.next));
    for (const target of nxt) {
      if (covered.has(target.id)) continue;
      const p = posOf(target, nxt.length);
      const src = [...cur].sort(
        (a, b) => Math.abs(posOf(a, cur.length) - p) - Math.abs(posOf(b, cur.length) - p),
      )[0];
      if (src && !src.next.includes(target.id)) src.next.push(target.id);
    }
  }

  return { nodes, layers: totalLayers };
}
