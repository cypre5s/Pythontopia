// ③玩中建画像：世界内探针目录（中性、可跳过、低风险的世界内选择卡）。
//
// 每个探针 = 一张世界内选择卡：从玩家的选择推断某个画像维度，绝不阻塞主线。
// choiceId 必须与后端 ProbeCatalog（server-authoritative）一一对齐：
//   - cog_intro  → cognitive_style：visual / logical / trial
//   - fail_react → emotion_persistence：retry / hint / skip
//
// 触发方式（trigger）：
//   - 'watch-phase'：进入主线「看」阶段时弹（cog_intro，首次进镇）。
//   - 'requested'  ：由外部事件置 requested 标记后弹（fail_react，练习失败后）。
//
// 设计铁律（见 docs/plans/2026-06-04-profile-loop-trilogy-design.md §4.1）：
//   选项美术等价、措辞中性、无优劣暗示；可随时跳过；提交 fire-and-forget。

export type ProbeTrigger = 'watch-phase' | 'requested';

export interface ProbeOption {
  /** 提交给后端的 choiceId（与对应维度的取值对齐）。 */
  id: string;
  label: string;
}

export interface ProbeDef {
  key: string;
  trigger: ProbeTrigger;
  eyebrow: string;
  title: string;
  lede: string;
  /** 视觉等价的中性选项（刻意不分主次，避免暗示「哪种更好」）。 */
  options: readonly ProbeOption[];
  /** 「不作答」入口文案；与各 option 文案区分，避免出现两个同名按钮。 */
  dismissLabel: string;
}

export const PROBE_CATALOG: Readonly<Record<string, ProbeDef>> = Object.freeze({
  cog_intro: {
    key: 'cog_intro',
    trigger: 'watch-phase',
    eyebrow: '悄悄话 · 随时可跳过',
    title: '初次进镇 · 你更想先怎么入门？',
    lede: '没有标准答案，选此刻最顺手的那种就好，小镇会照你的节奏来。',
    options: [
      { id: 'visual', label: '先看图解' },
      { id: 'logical', label: '先读代码' },
      { id: 'trial', label: '先跑一个最小例子' },
    ],
    dismissLabel: '跳过',
  },
  fail_react: {
    key: 'fail_react',
    trigger: 'requested',
    eyebrow: '悄悄话 · 随时可跳过',
    title: '练习没过时 · 你更想怎么继续？',
    lede: '没过是学习的一部分，挑此刻最想用的方式就好，小镇会照你的节奏来。',
    options: [
      { id: 'retry', label: '再试一次' },
      { id: 'hint', label: '看提示' },
      { id: 'skip', label: '跳过' },
    ],
    dismissLabel: '先不选',
  },
});

/** 所有「由外部事件请求弹出」的探针（如练习失败 → fail_react）。 */
export const REQUESTED_PROBES: readonly ProbeDef[] = Object.freeze(
  Object.values(PROBE_CATALOG).filter((def) => def.trigger === 'requested'),
);
