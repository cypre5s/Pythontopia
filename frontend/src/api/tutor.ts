import { http } from './http';

export type CognitiveStyle = 'visual' | 'logical' | 'trial';

export interface TutorAskRequest {
  question: string;
  /** R-12 主线 Phase 3：关联 KC（复数，对齐后端 kc_codes / Python kc_codes），用于 RAG 过滤检索。 */
  kcCodes?: string[];
  /** 当前学习上下文（视频标题 / PPT 页 / 题面摘要），透传给导师增强答疑。 */
  context?: string;
  /** R-14 看图答疑：可选多图 base64（可带 data URL 前缀，最多 3 张）；后端逐张经讯飞图片理解转描述后交 deepseek 作答。 */
  images?: string[];
  /**
   * R-14 即时换讲法（①）：学生本次显式选择的认知风格。携带时后端无视画像置信度门槛，
   * 让同一问题立刻按该风格改写当前答案；缺省则由画像（门槛内）决定，行为不变。
   */
  style?: CognitiveStyle;
}

export interface TutorSessionDto {
  id: number;
  question: string;
  answerMd: string | null;
  evidencePageIds: number[];
  groundingStatus: string;
  createdAt: string;
}

export async function listTutorSessions(): Promise<TutorSessionDto[]> {
  const response = await http.get<TutorSessionDto[]>('/api/tutor/sessions');
  return response.data;
}

export async function getTutorSession(id: number): Promise<TutorSessionDto> {
  const response = await http.get<TutorSessionDto>(`/api/tutor/sessions/${id}`);
  return response.data;
}

/** 学生采纳某讲法（因材施教闭环）：回流画像 cognitive_style。 */
export async function styleEngaged(style: CognitiveStyle, kcCode?: string): Promise<void> {
  await http.post('/api/tutor/style-engaged', { style, kcCode });
}
