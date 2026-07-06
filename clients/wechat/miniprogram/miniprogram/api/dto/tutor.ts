/** 学生显式选择的认知风格（为空时由画像决定讲法）。 */
export type TutorStyle = 'visual' | 'logical' | 'trial';

/**
 * 导师问答请求（POST /api/tutor/ask，SSE）。小程序端不上送 images（截图理解），
 * 仅传文本问题与可选 KC / 上下文 / 风格 / 会话窗口 id。
 */
export interface TutorAskRequest {
  question: string;
  kcCodes?: string[];
  context?: string;
  style?: TutorStyle;
  conversationId?: string;
}
