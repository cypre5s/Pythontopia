// 无尽远征「概念事件」节点的剧情文案池 + 三类应对。
//
// 事件节点不直接开打，而是给一段与本节点 KC 相关的小情景 + 3 个应对选择（钻研 / 稳练 / 复盘），
// 不同选择有不同代价与收益，增加策略与叙事——roguelike 的「事件房」。

export type RogueEventChoice = 'study' | 'steady' | 'review';

export interface RogueEventChoiceMeta {
  key: RogueEventChoice;
  label: string;
  desc: string;
  icon: string;
}

export const ROGUE_EVENT_CHOICES: readonly RogueEventChoiceMeta[] = [
  { key: 'study', label: '迎难钻研', icon: '🔬', desc: '挑战一道更难的题（难度 +1），通关得分 ×1.5。' },
  { key: 'steady', label: '稳扎稳打', icon: '🎯', desc: '按当前难度挑战一题，常规得分。' },
  { key: 'review', label: '就地复盘', icon: '📖', desc: '不开打：回 1 点体力，拿少量分直接通过。' },
];

// {kc} 会被替换成本节点知识点中文名。
export const ROGUE_EVENT_SCENARIOS: readonly string[] = [
  '岔路口立着一块旧木牌，刻着一道关于「{kc}」的谜题。',
  '一位赶路的学徒卡在「{kc}」上，停下来向你请教。',
  '废墟石台上摊着半张「{kc}」的手稿，字迹斑驳待补。',
  '机关门上的符文与「{kc}」同源，似乎在等一个答案。',
  '篝火旁的老学者聊起「{kc}」，邀你切磋两手。',
];

export function pickEventScenario(kcName: string, rng: () => number = Math.random): string {
  const tpl = ROGUE_EVENT_SCENARIOS[Math.floor(rng() * ROGUE_EVENT_SCENARIOS.length)]
    ?? ROGUE_EVENT_SCENARIOS[0]
    ?? '{kc}';
  return tpl.replace('{kc}', kcName);
}
