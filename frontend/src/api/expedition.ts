// 无尽远征「知识前沿选择器」前端 client。
//
// 调后端 POST /api/world/boss/select-frontier（后端读权威掌握度 + graph 排序 + 间隔重复）。
// 失败/超时（后端或 graph 不可达）由调用方回退本地 useKcFrontier——绝不阻塞玩家，
// 与 boss 出题「graph 不可达回退 golden」同一降级哲学。

import { http } from './http';
import type { ProfileSignalsDto } from '@/api/dto/Boss';

export interface FrontierPick {
  kcCode: string;
  chapter: number;
  difficulty: number;
  reason: string;
}

interface FrontierSelectResult {
  picks: FrontierPick[];
}

export interface FrontierSelectInput {
  focusChapter?: number | null;
  /** 近期练过的 KC（最近在前），间隔重复用。 */
  recentKcs?: string[];
  /** 本局已用 KC，避免重复。 */
  exclude?: string[];
  /** 需要的候选数量。 */
  count: number;
  profileSignals?: ProfileSignalsDto;
}

/** 取一批知识前沿候选；失败抛错（调用方 catch 后回退本地选择器）。 */
export async function requestFrontierPicks(input: FrontierSelectInput): Promise<FrontierPick[]> {
  const body: Record<string, unknown> = {
    recentKcs: input.recentKcs ?? [],
    exclude: input.exclude ?? [],
    count: input.count,
  };
  if (input.focusChapter != null) body.focusChapter = input.focusChapter;
  if (input.profileSignals) body.profileSignals = input.profileSignals;
  const res = await http.post<FrontierSelectResult>('/api/world/boss/select-frontier', body, {
    suppressNetworkToast: true,
    timeout: 3000,
  });
  return Array.isArray(res.data?.picks) ? res.data.picks : [];
}
