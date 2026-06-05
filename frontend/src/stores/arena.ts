// 闯关塔关卡进度持久化（localStorage）。
//
// 仅记 70 关的「是否通关 + 尝试次数 + 最近用时」简表，不存题目本身（题目由 boss/endless
// 服务即时生成）。
//
// 数据形状（localStorage `pyt_arena_v1`）：
//   { [levelId]: { cleared, attempts, lastClearedSec, lastClearedAt } }

import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { ArenaLevelProgress } from '@/types/arena';
import { ARENA_LEVELS, arenaLevelsByChapter, findArenaLevel } from '@/data/arenaLevels';

const STORAGE_KEY = 'pyt_arena_v1';

type ProgressMap = Record<string, ArenaLevelProgress>;

function loadProgress(): ProgressMap {
  if (typeof window === 'undefined') return {};
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    if (typeof parsed !== 'object' || parsed === null) return {};
    return parsed as ProgressMap;
  } catch {
    return {};
  }
}

function persistProgress(map: ProgressMap): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(map));
  } catch {
    // 配额超限静默
  }
}

export const useArenaStore = defineStore('arena', () => {
  const progressMap = ref<ProgressMap>(loadProgress());

  function getProgress(levelId: string): ArenaLevelProgress {
    return (
      progressMap.value[levelId] ?? {
        cleared: false,
        attempts: 0,
        lastClearedSec: null,
        lastClearedAt: null,
      }
    );
  }

  function markAttempt(levelId: string): void {
    const cur = getProgress(levelId);
    progressMap.value = {
      ...progressMap.value,
      [levelId]: {
        ...cur,
        attempts: cur.attempts + 1,
      },
    };
    persistProgress(progressMap.value);
  }

  function markCleared(levelId: string, elapsedSec: number): void {
    const cur = getProgress(levelId);
    progressMap.value = {
      ...progressMap.value,
      [levelId]: {
        ...cur,
        cleared: true,
        attempts: cur.attempts + 1,
        lastClearedSec: elapsedSec,
        lastClearedAt: new Date().toISOString(),
      },
    };
    persistProgress(progressMap.value);
  }

  function reset(): void {
    progressMap.value = {};
    persistProgress(progressMap.value);
  }

  /** 通关数 / 总关数 */
  const totalCleared = computed<number>(() =>
    Object.values(progressMap.value).filter((p) => p.cleared).length,
  );
  const totalLevels = computed<number>(() => ARENA_LEVELS.length);

  function chapterCleared(chapter: number): number {
    const levels = arenaLevelsByChapter(chapter);
    return levels.filter((l) => progressMap.value[l.id]?.cleared).length;
  }
  function chapterTotal(chapter: number): number {
    return arenaLevelsByChapter(chapter).length;
  }

  /** 是否可解锁该关：前 1 关已通或该章首关 */
  function isUnlocked(levelId: string): boolean {
    const level = findArenaLevel(levelId);
    if (!level) return false;
    if (level.index === 1) return true;
    const prev = `${level.chapter}-${level.index - 1}`;
    return progressMap.value[prev]?.cleared === true;
  }

  return {
    progressMap,
    getProgress,
    markAttempt,
    markCleared,
    reset,
    totalCleared,
    totalLevels,
    chapterCleared,
    chapterTotal,
    isUnlocked,
  };
});
