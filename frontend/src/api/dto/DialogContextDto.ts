/**
 * R-12 W2-NPC：对话模板的真实数据上下文 DTO。
 *
 * 与后端 `com.pythontopia.dialog.DialogContextDto` 字段一一对应：
 *   - playerName：玩家显示名（后端从 users.display_name / username 拿）；
 *   - profileType：'practical' | 'cautious'（基于 learner_profile.cognitive_style 推断）；
 *   - cognitiveStyle：'visual' | 'logical' | 'trial' | 'unknown'（基于 learner_profile.dimensions
 *     的 cognitive_style.tags 最新/主导标签推断），驱动 {{styleAdaptive:..|..|..}} 三分支因材施教；
 *   - giftJustGiven：玩家近 24h 内给该 NPC 送过 like/love 礼物；
 *   - stats：所有 {{stats.xxx}} 占位符的来源，统一为 string。
 *
 * 字段不在编译期固定枚举，是为了后端新增字段不强制前端 typecheck 红色：
 * 模板渲染器拿不到 key 时会回退到 '—' / 兜底文案。`cognitiveStyle` 设为可选，向后兼容尚未带该
 * 字段的旧上下文（缺省时渲染器按 'unknown' → 视觉型首分支兜底）。
 */
export type DialogProfileType = 'practical' | 'cautious';

export type DialogCognitiveStyle = 'visual' | 'logical' | 'trial' | 'unknown';

export interface DialogContextDto {
  playerName: string;
  profileType: DialogProfileType | string;
  cognitiveStyle?: DialogCognitiveStyle | string;
  giftJustGiven: boolean;
  stats: Record<string, string>;
}
