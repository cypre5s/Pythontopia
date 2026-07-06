// 知识星座布局（纯函数）：把 packId=43 的 65 个 KC 排成 7 个章节星团，
// 章节内 KC 成环、相邻相连。掌握度由调用方上色（点亮）。无尽远征的跨局 meta 进度可视化。

import { chapterKcs } from './rogueConfig';

export interface ConstellationNode {
  code: string;
  chapter: number;
  x: number;
  y: number;
}
export interface ConstellationEdge {
  a: string;
  b: string;
}
export interface Constellation {
  nodes: ConstellationNode[];
  edges: ConstellationEdge[];
  width: number;
  height: number;
}

/** 程序化布局：7 个章节星团围一圈，团内 KC 成环。坐标确定、可单测。 */
export function buildConstellation(width = 1000, height = 680): Constellation {
  const nodes: ConstellationNode[] = [];
  const edges: ConstellationEdge[] = [];
  const cx = width / 2;
  const cy = height / 2;
  const clusterR = Math.min(width, height) * 0.36;
  for (let c = 1; c <= 7; c++) {
    const clusterAngle = ((c - 1) / 7) * Math.PI * 2 - Math.PI / 2;
    const ccx = cx + Math.cos(clusterAngle) * clusterR;
    const ccy = cy + Math.sin(clusterAngle) * clusterR;
    const kcs = chapterKcs(c);
    const innerR = 26 + Math.min(kcs.length, 12) * 6;
    kcs.forEach((code, i) => {
      const a = kcs.length <= 1 ? 0 : (i / kcs.length) * Math.PI * 2 - Math.PI / 2;
      nodes.push({
        code,
        chapter: c,
        x: Math.round(ccx + Math.cos(a) * innerR),
        y: Math.round(ccy + Math.sin(a) * innerR),
      });
      const prev = kcs[i - 1];
      if (i > 0 && prev) edges.push({ a: prev, b: code });
    });
    const first = kcs[0];
    const last = kcs[kcs.length - 1];
    if (kcs.length > 2 && first && last) edges.push({ a: last, b: first });
  }
  return { nodes, edges, width, height };
}

/** 掌握度阈值：≥0.6 视为「点亮（已掌握）」，与毕业/复盘口径同量级。 */
export const CONSTELLATION_LIT_THRESHOLD = 0.6;
