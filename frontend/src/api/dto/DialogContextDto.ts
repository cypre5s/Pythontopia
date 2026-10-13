/**
 * 对话模板的数据上下文，与后端 `com.pythontopia.dialog.DialogContextDto` 一一对应。
 *   - playerName：玩家显示名（后端取 users.display_name / username）；
 *   - profileType：legacy template slot fixed to a conservative fallback;
 *   - cognitiveStyle：legacy template slot mapped from explicit media preference, never inferred as learner ability;
 *   - stats：所有 {{stats.xxx}} 占位符来源，统一为 string。
 *
 * 枚举字段额外允许 string，是为了后端新增取值时不触发前端 typecheck 失败（渲染器拿不到 key
 * 时回退到 '—' / 兜底文案）；cognitiveStyle 可选以兼容旧上下文（缺省按 'unknown' 走视觉型首分支）。
 */
export type DialogProfileType = 'practical' | 'cautious';

export type DialogCognitiveStyle = 'visual' | 'logical' | 'trial' | 'unknown';

export interface DialogContextDto {
  playerName: string;
  profileType: DialogProfileType | string;
  cognitiveStyle?: DialogCognitiveStyle | string;
  stats: Record<string, string>;
}
