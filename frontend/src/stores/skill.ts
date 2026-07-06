import { defineStore } from 'pinia';
import { fetchSkillTree, unlockSkill } from '@api/worldSkill';
import type { LearnerSkillDto } from '@api/dto/LearnerSkillDto';
import type { SkillNodeDto } from '@api/dto/SkillNodeDto';

/**
 * 玩家技能树 store。
 *
 * 数据来源：
 *   - bootstrap：world store 的 `bootstrap()` 下发 skillsCatalog（全量节点） + skills（已解锁），
 *     调用 `setBootstrap(catalog, unlocked)`；
 *   - lazy reload：`load()` 单独 GET /api/world/skills（返回 SkillTreeResponse = { catalog, unlocked }）；
 *   - mutation：`unlock(skillKey)` POST /api/world/skills/{key}/unlock 返回新 LearnerSkill 行，本地 push。
 *
 * 用户视角：catalog 永远是全集（包括未解锁），learnerSkills 仅包含已解锁项；
 * 组件渲染时 join 两表即可拿到 "节点 + 是否解锁 + 熟练度" 视图。
 */
interface SkillState {
  learnerSkills: LearnerSkillDto[];
  catalog: SkillNodeDto[];
  loading: boolean;
  error: string | null;
}

export const useSkillStore = defineStore('skill', {
  state: (): SkillState => ({
    learnerSkills: [],
    catalog: [],
    loading: false,
    error: null,
  }),
  getters: {
    isUnlocked(state) {
      return (skillKey: string): boolean =>
        state.learnerSkills.some((entry) => entry.skillKey === skillKey);
    },
    proficiencyOf(state) {
      return (skillKey: string): number =>
        state.learnerSkills.find((entry) => entry.skillKey === skillKey)?.proficiency ?? 0;
    },
    catalogByTier(state) {
      return (tier: number): SkillNodeDto[] =>
        state.catalog.filter((node) => node.tier === tier);
    },
  },
  actions: {
    async load(): Promise<void> {
      this.loading = true;
      this.error = null;
      try {
        const response = await fetchSkillTree();
        this.catalog = response.catalog;
        this.learnerSkills = response.unlocked;
      } catch (error) {
        this.error = error instanceof Error ? error.message : String(error);
      } finally {
        this.loading = false;
      }
    },
    async unlock(skillKey: string): Promise<LearnerSkillDto> {
      const learnerSkill = await unlockSkill(skillKey);
      const idx = this.learnerSkills.findIndex((entry) => entry.skillKey === skillKey);
      if (idx >= 0) {
        const next = [...this.learnerSkills];
        next[idx] = learnerSkill;
        this.learnerSkills = next;
      } else {
        this.learnerSkills = [...this.learnerSkills, learnerSkill];
      }
      return learnerSkill;
    },
    setBootstrap(catalog: SkillNodeDto[], unlocked: LearnerSkillDto[]): void {
      this.catalog = catalog;
      this.learnerSkills = unlocked;
    },
  },
});
