import { http } from './http';

export type CognitiveStyle = 'visual' | 'logical' | 'trial';

/**
 * 一条「系统命中的课件证据」（对应 graph `TutorResponse.evidence` 的 snake_case 投影）。
 * 供「课件证据」面板把答案依据的课本原文片段展示给学生（仿 Alethicode 引用卡）。
 */
export interface EvidenceCitation {
  /** 课件页主键，用于回链 `/library?pageId=`。 */
  pageId: number;
  /** 课本页码（学生可读，渲染为「第 X 页」）。 */
  pageNo: number;
  /** RAG 命中的课件原文片段（已折叠空白 + 截断）。 */
  snippet: string;
  /** 检索相关度（0~1），可空。 */
  score?: number;
}

export interface TutorAskRequest {
  question: string;
  /** 关联 KC（复数，对齐后端 kc_codes / Python kc_codes），用于 RAG 过滤检索。 */
  kcCodes?: string[];
  /** 当前学习上下文（视频标题 / PPT 页 / 题面摘要），透传给导师增强答疑。 */
  context?: string;
  /** 看图答疑：可选 3 张以内 JPEG/PNG/GIF/WebP base64 图片（可带 data URL 前缀），由 DeepSeek 直接理解并文字作答。 */
  images?: string[];
  /**
   * 即时换讲法：学生本次显式选择的认知风格。携带时后端无视画像置信度门槛，
   * 让同一问题立刻按该风格改写当前答案；缺省则由画像（门槛内）决定，行为不变。
   */
  style?: CognitiveStyle;
  /**
   * 会话窗口 id：同一个聊天窗口（一次打开 / 一次「新对话」）内的多次提问共享同一值，
   * 用于「聊天记录」按会话分组而非每问一条。前端生成、后端落库，不下传 graph。
   */
  conversationId?: string;
  /** 看课页当前视频 id；后端据此读取真实 MP4 的机器转写时间窗。 */
  videoId?: number;
  /** 当前播放时刻（秒）；后端读取前后时间窗字幕并继续以课件 RAG 证据约束回答。 */
  videoTimeSec?: number;
}

export interface TutorSessionDto {
  id: number;
  question: string;
  answerMd: string | null;
  evidencePageIds: number[];
  groundingStatus: string;
  /** 所属会话窗口 id；旧数据为 null（前端按「每条独立」回退）。 */
  conversationId: string | null;
  createdAt: string;
}

export async function listTutorSessions(limit = 50): Promise<TutorSessionDto[]> {
  const response = await http.get<TutorSessionDto[]>('/api/tutor/sessions', {
    params: { limit },
  });
  return response.data;
}

/** 取某会话窗口内的全部问答（按时间升序），用于多轮 thread 展示与历史回拉。 */
export async function listConversation(conversationId: string): Promise<TutorSessionDto[]> {
  const response = await http.get<TutorSessionDto[]>(
    `/api/tutor/conversations/${encodeURIComponent(conversationId)}`,
  );
  return response.data;
}

/** 学生采纳某讲法（因材施教闭环）：回流画像 cognitive_style。 */
export async function styleEngaged(style: CognitiveStyle, kcCode?: string): Promise<void> {
  await http.post('/api/tutor/style-engaged', { style, kcCode });
}
