/** 工坊可生成的 6 类资源（与意图路由的 6 个资源 intent 一一对应）。 */
export type WorkshopResourceType =
  | 'HANDOUT'
  | 'MINDMAP'
  | 'PRACTICE'
  | 'CODE_LAB'
  | 'READING'
  | 'STORYBOARD';

export type ContentFormat = 'MARKDOWN' | 'MERMAID' | 'STRUCT_JSON';

export type ResourceStatusFlag = 'PASSED' | 'FAILED' | 'NO_EVIDENCE';

export type ResourceFeedbackStatus = 'NONE' | 'LIKED' | 'DISLIKED';

export interface ResourceCreateRequest {
  kcCodes: string[];
  type: WorkshopResourceType;
  focusHint?: string;
}

export interface ResourceCreateResponse {
  runId: number;
  resourceId: number;
  status: string;
}

export interface LearningResource {
  id: number;
  userId: number;
  packId: number | null;
  kcCodes: string[];
  chapterId: number | null;
  type: WorkshopResourceType;
  title: string;
  contentFormat: ContentFormat;
  /** 结构化内容，形态随 contentFormat（MARKDOWN 文本 / MERMAID 文本 / STRUCT_JSON 对象）。 */
  content: unknown;
  evidencePageIds: number[];
  summary: string | null;
  safetyStatus: ResourceStatusFlag;
  groundingStatus: ResourceStatusFlag;
  feedbackStatus: ResourceFeedbackStatus;
  feedbackNote: string | null;
  createdAt: string;
}
