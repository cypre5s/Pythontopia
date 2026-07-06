/** 百晓工坊意图分类：ASK=答疑，VIDEO=B站视频，REVIEW=去矿洞复盘，其余 6 个=工坊资源类型。 */
export type IntentType =
  | 'ASK'
  | 'VIDEO'
  | 'REVIEW'
  | 'HANDOUT'
  | 'MINDMAP'
  | 'PRACTICE'
  | 'CODE_LAB'
  | 'READING'
  | 'STORYBOARD';

/** 课程 KC 目录条目：code 用于锚定，name 用于让分类器把语句主题映射到正确知识点。 */
export interface IntentCourseKc {
  code: string;
  name?: string;
}

export interface IntentRouteRequest {
  message: string;
  manualKcCodes?: string[];
  courseKcs?: IntentCourseKc[];
  currentChapter?: number;
  recentKcCodes?: string[];
}

export interface IntentRouteResponse {
  intent: IntentType;
  kcCodes: string[];
  focusHint: string;
  confidence: number;
  reason: string;
}
