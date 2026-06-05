import { describe, expect, it } from 'vitest';
import { MAIN_QUESTS, findMainQuestById } from '@/data/mainQuests';
import { resolveQuestGuidance } from '@/utils/questGuidance';

const VALID_MAPS = new Set([
  'town_hub',
  'farm_north',
  'mine_south',
  'campus_east',
  'port_west',
]);

describe('resolveQuestGuidance（学习目标面板「下一步」指路）', () => {
  it('dialog_complete → 指路到任务指定 NPC，需传送 + 开对话', () => {
    const g = resolveQuestGuidance(findMainQuestById('1-1')!);
    expect(g.kind).toBe('dialog');
    expect(g.npcKey).toBe('mayor_py');
    expect(g.npcName).toBe('镇长 Py');
    expect(g.teleport).toBe(true);
    expect(g.openDialog).toBe(true);
    expect(g.tile).toBeNull();
    expect(g.stepText).toContain('对话');
    expect(g.stepText).not.toContain('KC-001');
  });

  it('resource_consumed → 指路到工坊师傅（工坊），文案含资源类型', () => {
    // 2-1（KC-008 装好 Python）是 STORYBOARD 资源子任务
    const g = resolveQuestGuidance(findMainQuestById('2-1')!);
    expect(g.kind).toBe('workshop');
    expect(g.npcKey).toBe('workshop_master');
    expect(g.teleport).toBe(false);
    expect(g.openDialog).toBe(true);
    expect(g.stepText).not.toContain('KC-008');
    expect(g.stepText).toContain('STORYBOARD');
    expect(g.stepText).toContain('历史故事板');
  });

  it('submission → 指路到教务 Q（训练场）', () => {
    // 7-3（KC-061 递归，problem #511）是 submission 子任务；1-4 已回归为「学」里程碑。
    const g = resolveQuestGuidance(findMainQuestById('7-3')!);
    expect(g.kind).toBe('training');
    expect(g.npcKey).toBe('coach_q');
    expect(g.mapKey).toBe('town_hub');
    expect(g.stepText).toContain('训练场');
    expect(g.stepText).not.toContain('KC-061');
  });

  it('kc_mastery → 指路到训练场（教务 Q），文案含目标掌握度', () => {
    const g = resolveQuestGuidance(findMainQuestById('2-5')!);
    expect(g.kind).toBe('practice');
    expect(g.npcKey).toBe('coach_q');
    expect(g.mapKey).toBe('town_hub');
    expect(g.teleport).toBe(false);
    expect(g.stepText).toMatch(/\d+/);
    expect(g.stepText).toContain('掌握进度');
  });

  it('首进 1-2 → 指路到教授对话（不再是抽象资源生成）', () => {
    const g = resolveQuestGuidance(findMainQuestById('1-2')!);
    expect(g.kind).toBe('dialog');
    expect(g.npcKey).toBe('professor');
    expect(g.openDialog).toBe(true);
  });

  it('全部 35 个子任务都能产出有效指路（非空文案 + 合法地图）', () => {
    for (const quest of MAIN_QUESTS) {
      const g = resolveQuestGuidance(quest);
      expect(g.npcKey.length).toBeGreaterThan(0);
      expect(g.npcName.length).toBeGreaterThan(0);
      expect(g.stepText.length).toBeGreaterThan(0);
      expect(g.ctaText.length).toBeGreaterThan(0);
      // 文案一律展示 KC 中文名，绝不暴露原始编码（KC-0xx）。
      expect(g.stepText).not.toMatch(/KC-\d{3}/);
      expect(VALID_MAPS.has(g.mapKey)).toBe(true);
      // building 任务必带 tile，其余不带。
      if (g.kind === 'building') {
        expect(g.tile).not.toBeNull();
      } else {
        expect(g.tile).toBeNull();
      }
    }
  });
});
