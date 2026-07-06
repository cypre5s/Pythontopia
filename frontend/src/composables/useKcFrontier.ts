// 知识前沿选择器：从掌握度快照 + 画像 + 可选聚焦章节，产出按优先级排序的候选 KC。
//
// 优先级（高→低）：画像弱项 KC 且未掌握 > ZPD 中带（0.2–0.8）> 未掌握 > 越不熟越靠前。
// MVP 在前端用此规则；后续阶段下沉后端（带间隔重复持久化 + 防刷），见重构计划 §3/§6。

import { useLearningStore } from '@stores/learning';
import { useProfileSignals } from '@/composables/useProfileSignals';
import { allKcCodes, chapterKcs } from '@/data/rogueConfig';

export interface UseKcFrontierReturn {
  masteryOf: (code: string) => number;
  frontier: (focusChapter: number | null) => string[];
  pickKc: (focusChapter: number | null, exclude?: Set<string>, topN?: number) => string;
  difficultyForKc: (code: string) => number;
}

export function useKcFrontier(): UseKcFrontierReturn {
  const learning = useLearningStore();
  const { weakKcs } = useProfileSignals();

  function masteryOf(code: string): number {
    return learning.field.masterySnapshot.find((s) => s.kcCode === code)?.masteryScore ?? 0;
  }

  function priority(code: string, weak: Set<string>): number {
    const m = masteryOf(code);
    let p = 0;
    if (weak.has(code) && m < 0.8) p += 100;
    if (m >= 0.2 && m <= 0.8) p += 50;
    if (m < 0.2) p += 20;
    p += (1 - m) * 10;
    return p;
  }

  function frontier(focusChapter: number | null): string[] {
    const pool = focusChapter ? chapterKcs(focusChapter) : allKcCodes();
    const weak = new Set(weakKcs.value);
    return [...pool].sort((a, b) => priority(b, weak) - priority(a, weak));
  }

  function pickKc(focusChapter: number | null, exclude: Set<string> = new Set(), topN = 8): string {
    const ranked = frontier(focusChapter).filter((c) => !exclude.has(c));
    const list = ranked.length > 0 ? ranked : frontier(focusChapter);
    const span = Math.max(1, Math.min(topN, list.length));
    const idx = Math.floor(Math.random() * span);
    return list[idx] ?? list[0] ?? allKcCodes()[0] ?? '';
  }

  function difficultyForKc(code: string): number {
    const m = masteryOf(code);
    return Math.max(1, Math.min(5, Math.round(1 + m * 4)));
  }

  return { masteryOf, frontier, pickKc, difficultyForKc };
}
