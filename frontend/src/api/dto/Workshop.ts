export type WorkshopResourceType =
  | 'HANDOUT'
  | 'MINDMAP'
  | 'PRACTICE'
  | 'CODE_LAB'
  | 'READING'
  | 'STORYBOARD';

export type WorkshopContentFormat = 'MARKDOWN' | 'MERMAID' | 'STRUCT_JSON';

export type WorkshopStatusFlag = 'PASSED' | 'FAILED' | 'NO_EVIDENCE';

export type WorkshopFeedbackStatus = 'NONE' | 'LIKED' | 'DISLIKED';

export interface PageRef {
  page_id: number;
  page_no: number;
  chapter_id: number | null;
}

export interface HandoutSection {
  heading: string;
  paragraphs: string[];
  code_snippets: string[];
  page_refs: PageRef[];
}

export interface HandoutContent {
  sections: HandoutSection[];
}

export interface MindMapContent {
  mermaid: string;
  kc_map: Record<string, string[]>;
}

export interface SampleIo {
  input: string;
  output: string;
}

export interface ProblemSpec {
  title: string;
  statement_md: string;
  hints: string[];
  sample_io: SampleIo[];
  kc_codes: string[];
  difficulty: 'basic' | 'medium' | 'hard';
}

export interface PracticeContent {
  problems: ProblemSpec[];
}

export interface Walkthrough {
  title: string;
  narrative: string;
  code: string;
  expected_output: string;
  common_mistakes: string[];
}

export interface CodeLabContent {
  walkthroughs: Walkthrough[];
}

export interface ReadingItem {
  summary_md: string;
  page_refs: PageRef[];
  kc_codes: string[];
}

export interface ReadingContent {
  items: ReadingItem[];
}

export interface StoryboardScene {
  title: string;
  narration: string;
  visual_hint: string;
  kc_codes: string[];
  duration_seconds: number;
  evidence_page_id?: number;
  narration_tts_voice?: string;
  /** 文生图提示词（讯飞 TTI）：用于生成分镜插画与合成短视频。 */
  image_prompt?: string;
  /** 已生成插画的 media_asset id（后端填充，可空）。 */
  image_asset_id?: number;
}

export interface StoryboardContent {
  scenes: StoryboardScene[];
}

export type WorkshopContent =
  | HandoutContent
  | MindMapContent
  | PracticeContent
  | CodeLabContent
  | ReadingContent
  | StoryboardContent;

export interface WorkshopResourceListItemDto {
  id: number;
  type: WorkshopResourceType;
  title: string;
  summary: string | null;
  kcCodes: string[];
  groundingStatus: WorkshopStatusFlag;
  feedbackStatus: WorkshopFeedbackStatus;
  createdAt: string;
}

export interface WorkshopResourceDto {
  id: number;
  userId: number;
  packId: number;
  kcCodes: string[];
  chapterId: number | null;
  type: WorkshopResourceType;
  title: string;
  contentFormat: WorkshopContentFormat;
  content: WorkshopContent;
  evidencePageIds: number[];
  summary: string | null;
  safetyStatus: WorkshopStatusFlag;
  groundingStatus: WorkshopStatusFlag;
  feedbackStatus: WorkshopFeedbackStatus;
  feedbackNote: string | null;
  createdAt: string;
}

export interface WorkshopCreateRequest {
  kcCodes: string[];
  type: WorkshopResourceType;
  focusHint?: string;
}

export interface WorkshopCreateResponse {
  runId: number;
  resourceId: number;
  status: 'SUCCESS' | 'PARTIAL' | 'FAILED';
}

export interface WorkshopConsumptionResponse {
  resourceId: number;
  kcCodes: string[];
  nextAction: 'micro_check';
  prompt: string;
  readRecorded: boolean;
  loopState: 'MICRO_CHECK_REQUIRED' | string;
  evidenceRequired: 'micro_check_or_operation' | string;
}

export type WorkshopCheckEvidenceType = 'MICRO_QUIZ' | 'OPERATION';

export type WorkshopCheckStatus = 'PASSED' | 'FAILED';

export interface WorkshopCheckSubmitRequest {
  kcCode: string;
  evidenceType: WorkshopCheckEvidenceType;
  answerText?: string;
  operationEvidence?: Record<string, unknown>;
}

export interface WorkshopCheckResponse {
  checkId: number;
  resourceId: number;
  kcCode: string;
  status: WorkshopCheckStatus;
  masteryDelta: number;
  nextAction: 'pass' | 'remediate' | 'swap_resource' | 'replan';
  remediationType: WorkshopResourceType | null;
  feedback: string;
  loopState: 'PASSED' | 'NEED_REMEDIATION' | 'NEED_RESOURCE_SWAP' | 'NEED_REPLAN' | string;
  practiceSubmissionId: number | null;
  decisionReason: string;
}

export interface WorkshopFeedbackRequest {
  status: 'LIKED' | 'DISLIKED' | 'NONE';
  note?: string;
}

export type WorkshopEffectivenessSignal =
  | 'INSUFFICIENT_EVIDENCE'
  | 'WATCH'
  | 'EFFECTIVE'
  | 'NEEDS_REVISION';

export interface WorkshopResourceEffectivenessDto {
  resourceId: number;
  feedbackStatus: WorkshopFeedbackStatus;
  adoptedCount: number;
  rejectedCount: number;
  checkCount: number;
  passedCheckCount: number;
  failedCheckCount: number;
  passRate: number;
  masteryDeltaSum: number;
  evidencePageIds: number[];
  signal: WorkshopEffectivenessSignal;
  evaluationHook: 'resource_feedback_or_check';
}
