// 世界内探针目录（中性、可跳过、低风险的世界内选择卡）。
//
// 每个探针 = 一张世界内选择卡：只记录玩家明确选择的学习媒介，不推断能力或情绪。
// choiceId 必须与后端 ProbeCatalog（server-authoritative）一一对齐。
//
// 触发方式（trigger）：
//   - 'watch-phase'：进入主线「看」阶段时弹（cog_intro，首次进镇）。
//
// 设计铁律：选项美术等价、措辞中性、无优劣暗示；可随时跳过；提交 fire-and-forget。

export type ProbeTrigger = 'watch-phase';

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
});
