// 无尽远征「学习增益」（roguelike relics，教育化命名）。
//
// 一局内可累积的被动增益：从「资源宝箱」与「精英挑战」通关获得，丰富 run 的策略与成长感。
// 效果都正向且教育友好（更耐打 / 减挫 / 奖励连题 / 复盘回气），不引入与学习无关的纯数值赌博。

export interface RogueRelic {
  id: string;
  name: string;
  icon: string;
  desc: string;
}

export const ROGUE_RELICS: readonly RogueRelic[] = [
  { id: 'sturdy_notes', name: '厚实笔记', icon: '🛡', desc: '体力上限 +1，并立即回 1 点（更耐打）。' },
  { id: 'calm_mind', name: '沉静心法', icon: '🧘', desc: '答错少扣 1 点体力（至少扣 1）。' },
  { id: 'combo_scholar', name: '连题学者', icon: '🔥', desc: '通关得分 +20%。' },
  { id: 'second_wind', name: '复盘回气', icon: '💧', desc: '答错时气势减半，而非清零。' },
  { id: 'focus_lens', name: '专注透镜', icon: '🔍', desc: '营地复盘额外回 1 点体力。' },
  { id: 'quick_thinker', name: '急智', icon: '⚡', desc: '30 秒内通关，额外 +50% 得分。' },
  { id: 'first_aid', name: '急救包', icon: '🩹', desc: '本局首次答错不扣体力。' },
];

export function relicById(id: string): RogueRelic | undefined {
  return ROGUE_RELICS.find((r) => r.id === id);
}
