import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useSkillStore } from '@stores/skill';
import type { LearnerSkillDto } from '@/api/dto/LearnerSkillDto';
import type { SkillNodeDto } from '@/api/dto/SkillNodeDto';

const mocks = vi.hoisted(() => ({
  fetchSkillTree: vi.fn(),
  unlockSkill: vi.fn(),
}));

vi.mock('@/api/worldSkill', () => mocks);

function node(skillKey: string, tier: number): SkillNodeDto {
  return {
    skillKey,
    displayName: skillKey,
    kcCodes: [],
    tier,
  };
}

function learner(skillKey: string, proficiency = 1): LearnerSkillDto {
  return {
    skillKey,
    displayName: skillKey,
    tier: 1,
    unlockedAt: '2026-05-22T08:00:00Z',
    proficiency,
  };
}

describe('skill store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.fetchSkillTree.mockReset();
    mocks.unlockSkill.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('load() GET /api/world/skills fills catalog and learnerSkills', async () => {
    mocks.fetchSkillTree.mockResolvedValue({
      catalog: [node('SK-LOOP', 1), node('SK-FUNC', 2)],
      unlocked: [learner('SK-LOOP', 3)],
    });
    const store = useSkillStore();
    await store.load();
    expect(mocks.fetchSkillTree).toHaveBeenCalledTimes(1);
    expect(store.catalog).toHaveLength(2);
    expect(store.learnerSkills).toHaveLength(1);
    expect(store.isUnlocked('SK-LOOP')).toBe(true);
    expect(store.isUnlocked('SK-FUNC')).toBe(false);
    expect(store.proficiencyOf('SK-LOOP')).toBe(3);
    expect(store.proficiencyOf('SK-FUNC')).toBe(0);
    expect(store.catalogByTier(1)).toHaveLength(1);
  });

  it('unlock() POST returns learnerSkill, pushes if new', async () => {
    const store = useSkillStore();
    store.setBootstrap([node('SK-LOOP', 1), node('SK-FUNC', 2)], []);
    mocks.unlockSkill.mockResolvedValue(learner('SK-LOOP', 1));
    const result = await store.unlock('SK-LOOP');
    expect(mocks.unlockSkill).toHaveBeenCalledWith('SK-LOOP');
    expect(result.skillKey).toBe('SK-LOOP');
    expect(store.isUnlocked('SK-LOOP')).toBe(true);
  });

  it('unlock() replaces existing entry on upgrade', async () => {
    const store = useSkillStore();
    store.setBootstrap([node('SK-LOOP', 1)], [learner('SK-LOOP', 1)]);
    mocks.unlockSkill.mockResolvedValue(learner('SK-LOOP', 5));
    await store.unlock('SK-LOOP');
    expect(store.learnerSkills).toHaveLength(1);
    expect(store.proficiencyOf('SK-LOOP')).toBe(5);
  });

  it('setBootstrap clears prior state', () => {
    const store = useSkillStore();
    store.setBootstrap([node('A', 1)], [learner('A')]);
    store.setBootstrap([node('B', 2)], []);
    expect(store.catalog.map((n) => n.skillKey)).toEqual(['B']);
    expect(store.learnerSkills).toHaveLength(0);
  });
});
