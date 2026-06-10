import { describe, expect, it } from 'vitest';
import {
  MAIN_QUESTS,
  MAIN_QUEST_BOSSES,
  findBossByChapter,
  findMainQuestById,
  listSubQuestsByChapter,
} from '@/data/mainQuests';

describe('R-13 §5 主线 + Boss 静态数据', () => {
  it('总共 35 个子任务（7 章 × 5）', () => {
    expect(MAIN_QUESTS.length).toBe(35);
  });

  it('每章恰好 5 个子任务，index 1..5 无缺漏', () => {
    for (let chapter = 1; chapter <= 7; chapter += 1) {
      const subs = listSubQuestsByChapter(chapter);
      expect(subs.length).toBe(5);
      expect(subs.map((s) => s.index)).toEqual([1, 2, 3, 4, 5]);
    }
  });

  it('所有子任务 id 唯一且符合 "{chapter}-{index}" 规范', () => {
    const ids = new Set<string>();
    for (const quest of MAIN_QUESTS) {
      expect(quest.id).toBe(`${quest.chapter}-${quest.index}`);
      expect(ids.has(quest.id)).toBe(false);
      ids.add(quest.id);
    }
  });

  it('mapKey 只能是 town_hub（R-14 单中央 hub，mine_south 风景图已删）', () => {
    const validMaps = new Set([
      'town_hub',
    ]);
    for (const quest of MAIN_QUESTS) {
      expect(validMaps.has(quest.mapKey)).toBe(true);
    }
  });

  it('kcCodes 全部符合 PY43-KC-NNN 格式', () => {
    const re = /^PY43-KC-\d{3}$/;
    for (const quest of MAIN_QUESTS) {
      expect(quest.kcCodes.length).toBeGreaterThan(0);
      for (const code of quest.kcCodes) {
        expect(re.test(code)).toBe(true);
      }
    }
  });

  it('evidenceRequired 各 type 字段齐全', () => {
    for (const quest of MAIN_QUESTS) {
      const e = quest.evidenceRequired;
      switch (e.type) {
        case 'dialog_complete':
          expect(typeof e.npcKey).toBe('string');
          expect(e.npcKey.length).toBeGreaterThan(0);
          break;
        case 'submission':
          expect(typeof e.problemId).toBe('number');
          break;
        case 'resource_consumed':
          expect(typeof e.resourceType).toBe('string');
          break;
        case 'building_visit':
          expect(typeof e.tileX).toBe('number');
          expect(typeof e.tileY).toBe('number');
          break;
        case 'kc_mastery':
          expect(typeof e.kcCode).toBe('string');
          expect(typeof e.thresholdScore).toBe('number');
          break;
        default: {
          const _exhaustive: never = e;
          void _exhaustive;
        }
      }
    }
  });

  it('每章都有 boss 元数据（共 7 个）', () => {
    expect(MAIN_QUEST_BOSSES.length).toBe(7);
    for (let chapter = 1; chapter <= 7; chapter += 1) {
      const boss = findBossByChapter(chapter);
      expect(boss, `boss for chapter ${chapter}`).not.toBeNull();
      expect(boss?.name.length).toBeGreaterThan(0);
    }
  });

  it('每个 boss 的 KC 范围与本章子任务 kcCodes 有交集', () => {
    for (let chapter = 1; chapter <= 7; chapter += 1) {
      const boss = findBossByChapter(chapter)!;
      const chapterKcCodes = new Set(
        listSubQuestsByChapter(chapter).flatMap((s) => s.kcCodes),
      );
      const start = Number(boss.kcRange.startCode.split('-').pop());
      const end = Number(boss.kcRange.endCode.split('-').pop());
      const inRange = Array.from(chapterKcCodes).some((code) => {
        const n = Number(code.split('-').pop());
        return n >= start && n <= end;
      });
      expect(inRange, `chapter ${chapter} kc range mismatch`).toBe(true);
    }
  });

  it('findMainQuestById 命中已知任务，未知返回 null', () => {
    // R-13 mainQuests v2：1-1 标题改为"回望中国计算"（基于课件第一章 KC-001）
    expect(findMainQuestById('1-1')?.title).toContain('中国计算');
    expect(findMainQuestById('7-5')?.chapter).toBe(7);
    expect(findMainQuestById('99-99')).toBeNull();
  });

  it('rewards 可选：非讲义任务有奖励、HANDOUT 讲义任务无奖励（讲义解耦），有则 amount>0', () => {
    for (const quest of MAIN_QUESTS) {
      const isHandout =
        quest.evidenceRequired.type === 'resource_consumed' &&
        quest.evidenceRequired.resourceType === 'HANDOUT';
      if (isHandout) {
        expect(quest.rewards ?? []).toHaveLength(0);
      } else {
        expect((quest.rewards ?? []).length).toBeGreaterThan(0);
      }
      for (const reward of quest.rewards ?? []) {
        expect(reward.amount).toBeGreaterThan(0);
      }
    }
  });
});
