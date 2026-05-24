/**
 * R-13 §-1 续：useArenaStore 单元测试。
 *
 * 覆盖：
 *   1. 初始空 state（无 localStorage）
 *   2. markAttempt 累加 attempts 不改 cleared
 *   3. markCleared 写 cleared + lastClearedSec + ISO 时间戳
 *   4. isUnlocked：第 1 关常解锁；第 N 关需上一关 cleared
 *   5. chapterCleared / chapterTotal：单章 10 关计数
 *   6. totalCleared / totalLevels：跨章 70 关汇总
 *   7. localStorage 持久化：第二次 useArenaStore() 读回
 *   8. reset 清空
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useArenaStore } from '@/stores/arena';
import { ARENA_LEVELS } from '@/data/arenaLevels';

describe('useArenaStore', () => {
  beforeEach(() => {
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem('pyt_arena_v1');
    }
    setActivePinia(createPinia());
  });

  afterEach(() => {
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem('pyt_arena_v1');
    }
  });

  it('starts with empty state when localStorage is empty', () => {
    const arena = useArenaStore();
    expect(arena.totalCleared).toBe(0);
    expect(arena.totalLevels).toBe(70);
    expect(arena.getProgress('1-1')).toEqual({
      cleared: false,
      attempts: 0,
      lastClearedSec: null,
      lastClearedAt: null,
    });
  });

  it('markAttempt increments attempts without clearing', () => {
    const arena = useArenaStore();
    arena.markAttempt('1-1');
    arena.markAttempt('1-1');
    const p = arena.getProgress('1-1');
    expect(p.attempts).toBe(2);
    expect(p.cleared).toBe(false);
  });

  it('markCleared sets cleared, elapsed sec, ISO timestamp', () => {
    const arena = useArenaStore();
    arena.markCleared('1-1', 42);
    const p = arena.getProgress('1-1');
    expect(p.cleared).toBe(true);
    expect(p.attempts).toBe(1);
    expect(p.lastClearedSec).toBe(42);
    expect(p.lastClearedAt).toBeTruthy();
    expect(new Date(p.lastClearedAt!).toString()).not.toBe('Invalid Date');
  });

  it('isUnlocked: level 1 always unlocked, level N requires N-1 cleared', () => {
    const arena = useArenaStore();
    expect(arena.isUnlocked('1-1')).toBe(true);
    expect(arena.isUnlocked('1-2')).toBe(false);
    expect(arena.isUnlocked('1-10')).toBe(false);
    arena.markCleared('1-1', 30);
    expect(arena.isUnlocked('1-2')).toBe(true);
    expect(arena.isUnlocked('1-3')).toBe(false);
  });

  it('chapterCleared / chapterTotal: single chapter has 10 levels', () => {
    const arena = useArenaStore();
    expect(arena.chapterTotal(1)).toBe(10);
    expect(arena.chapterCleared(1)).toBe(0);
    arena.markCleared('1-1', 10);
    arena.markCleared('1-2', 20);
    expect(arena.chapterCleared(1)).toBe(2);
  });

  it('totalCleared / totalLevels: 7 chapters × 10 = 70 levels', () => {
    const arena = useArenaStore();
    expect(arena.totalLevels).toBe(70);
    expect(ARENA_LEVELS.length).toBe(70);
    arena.markCleared('1-1', 10);
    arena.markCleared('3-5', 50);
    arena.markCleared('7-10', 200);
    expect(arena.totalCleared).toBe(3);
  });

  it('persists to localStorage and re-reads on new pinia instance', () => {
    const arena = useArenaStore();
    arena.markCleared('1-1', 33);
    expect(window.localStorage.getItem('pyt_arena_v1')).toContain('"1-1"');
    setActivePinia(createPinia());
    const arena2 = useArenaStore();
    expect(arena2.getProgress('1-1').cleared).toBe(true);
    expect(arena2.getProgress('1-1').lastClearedSec).toBe(33);
  });

  it('reset clears all progress', () => {
    const arena = useArenaStore();
    arena.markCleared('1-1', 10);
    arena.markCleared('2-3', 20);
    expect(arena.totalCleared).toBe(2);
    arena.reset();
    expect(arena.totalCleared).toBe(0);
    expect(window.localStorage.getItem('pyt_arena_v1')).toBe('{}');
  });

  it('boss level (1-10) is locked until 1-9 cleared', () => {
    const arena = useArenaStore();
    for (let i = 1; i < 9; i += 1) {
      arena.markCleared(`1-${i}`, 10);
    }
    expect(arena.isUnlocked('1-10')).toBe(false);
    arena.markCleared('1-9', 10);
    expect(arena.isUnlocked('1-10')).toBe(true);
  });
});
