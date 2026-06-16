// AI 导学工作流（6 阶段卡片式导学）API client。
//
// 端点：
//   GET  /api/tutor/workflow/sessions/active?problemId=  → 活跃会话快照 + 卡片时间线
//   POST /api/tutor/workflow/runs（SSE，经 useSseStream 消费，DTO 形状见 WorkflowRunResultDto）
//
// 卡片 payload 字段与 graph 侧 pydantic 模型对齐（schemas/tutor_workflow.py）。

import { http } from './http';

export type WorkflowPhase =
  | 'READING'
  | 'IDEATING'
  | 'CODING'
  | 'ERROR_FEEDBACK'
  | 'AC_REVIEW'
  | 'TRANSFER';

export type WorkflowEvent =
  | 'READING'
  | 'IDEATING'
  | 'SKELETON'
  | 'CODING'
  | 'ERROR_FEEDBACK'
  | 'AC_REVIEW'
  | 'TRANSFER'
  | 'CHAT'
  // 完整对标 Alethicode 的 4 个辅助动作（不改阶段；后端 graph 已实现，C5）。
  | 'KNOWLEDGE_REVIEW'
  | 'EXECUTION_TRACE'
  | 'VISUALIZE'
  | 'PARSONS';

export type WorkflowCardKey =
  | 'problem_guide'
  | 'ideate_analysis'
  | 'skeleton_code'
  | 'error_diagnosis'
  | 'post_ac'
  | 'transfer_problem'
  | 'ai_reply'
  | 'knowledge_review'
  | 'execution_trace'
  | 'visualize'
  | 'parsons';

export interface ProblemGuideCardDto {
  card_id?: string;
  problem_restatement: string;
  input_output_focus: string;
  key_observation: string;
  starter_questions: string[];
  related_kcs: string[];
  teaching_goal: string;
  checkpoint_prompt: string;
  reflection_prompt: string;
}

export interface IdeateAnalysisCardDto {
  card_id?: string;
  analysis: string;
  steps: string[];
  guiding_questions: string[];
  misconception_alert: string;
  teaching_goal: string;
  checkpoint_prompt: string;
  reflection_prompt: string;
}

export interface SkeletonCodeCardDto {
  card_id?: string;
  description: string;
  skeleton: string;
  teaching_goal: string;
  checkpoint_prompt: string;
  reflection_prompt: string;
}

export interface FailedTestCaseDto {
  input: string;
  expected_output: string;
  actual_output: string;
}

export interface ErrorDiagnosisCardDto {
  card_id?: string;
  root_cause: string;
  what_program_is_doing: string;
  expected_behavior: string;
  fix_direction: string;
  related_kcs: string[];
  error_pattern: string;
  is_recurring: boolean;
  encouragement: string;
  frustration_level: string;
  first_failed_test_case: FailedTestCaseDto | null;
  teaching_goal: string;
  checkpoint_prompt: string;
  reflection_prompt: string;
}

export interface PostAcCardDto {
  card_id?: string;
  success_summary: string;
  key_action: string;
  code_quality_notes: string[];
  knowledge_points: string[];
  next_practice_direction: string;
  teaching_goal: string;
  checkpoint_prompt: string;
  reflection_prompt: string;
}

export interface TransferSampleDto {
  input: string;
  output: string;
}

export interface TransferProblemCardDto {
  card_id?: string;
  title: string;
  description: string;
  input_description: string;
  output_description: string;
  samples: TransferSampleDto[];
  hint: string;
  target_kcs: string[];
}

export interface AiReplyCardDto {
  card_id?: string;
  content: string;
  referenced_card_ids: string[];
  /** Java 投影附带：本轮学生消息（时间线恢复用户气泡用）。 */
  user_message?: string;
}

export interface CoursewareRefDto {
  page_no: number;
  page_id?: number | null;
  chapter_id?: number | null;
  snippet?: string;
}

export interface KnowledgeReviewCardDto {
  card_id?: string;
  review_content: string;
  related_kcs: string[];
  courseware_refs: CoursewareRefDto[];
  practice_suggestions: string[];
  mastery_snapshot?: Record<string, unknown>;
  kc_selection_basis?: string;
  teaching_goal: string;
  checkpoint_prompt: string;
  mentor_role?: string;
  reflection_prompt: string;
}

export interface ExecutionTraceStepDto {
  step: number;
  line_ref: string;
  action: string;
  variables?: Record<string, unknown>;
  note?: string;
}

export interface ExecutionTraceCardDto {
  card_id?: string;
  summary: string;
  trace_target: string;
  input_used: string;
  steps: ExecutionTraceStepDto[];
  key_insight: string;
  teaching_goal: string;
  checkpoint_prompt: string;
  mentor_role?: string;
  reflection_prompt: string;
}

export interface VisualizeCardDto {
  card_id?: string;
  intent: string;
  title: string;
  /** mermaid | ascii | svg */
  format: string;
  diagram: string;
  alt_text: string;
  caption: string;
  teaching_goal: string;
  checkpoint_prompt: string;
  mentor_role?: string;
  reflection_prompt: string;
}

export interface ParsonsBlockDto {
  id: string;
  code: string;
  indent: number;
}

export interface ParsonsCardDto {
  card_id?: string;
  drill_goal: string;
  prompt: string;
  blocks: ParsonsBlockDto[];
  distractors: ParsonsBlockDto[];
  correct_order: string[];
  fading_level: number;
  success_check: string;
  teaching_goal: string;
  checkpoint_prompt: string;
  mentor_role?: string;
  reflection_prompt: string;
}

export type WorkflowCardPayload =
  | ProblemGuideCardDto
  | IdeateAnalysisCardDto
  | SkeletonCodeCardDto
  | ErrorDiagnosisCardDto
  | PostAcCardDto
  | TransferProblemCardDto
  | AiReplyCardDto
  | KnowledgeReviewCardDto
  | ExecutionTraceCardDto
  | VisualizeCardDto
  | ParsonsCardDto;

export interface WorkflowActionDto {
  key: string;
  label: string;
  event: WorkflowEvent;
  recommended?: boolean;
  reason?: string;
}

/** SSE RUN_FINISHED.result 形状（graph WorkflowRunResult）。 */
export interface WorkflowRunResultDto {
  session_id: string;
  event: WorkflowEvent;
  phase: WorkflowPhase;
  card_key: WorkflowCardKey | null;
  card: (WorkflowCardPayload & { card_id?: string }) | null;
  available_actions: WorkflowActionDto[];
  seq: number;
}

export interface WorkflowTimelineCardDto {
  seq: number;
  event: WorkflowEvent;
  cardKey: WorkflowCardKey;
  payload: WorkflowCardPayload;
  createdAt: string;
}

export interface WorkflowSessionSnapshotDto {
  sessionKey: string;
  problemId: number;
  phase: WorkflowPhase;
  seq: number;
  cards: WorkflowTimelineCardDto[];
}

/** 取（或创建）该题活跃工作流会话快照 + 卡片时间线。 */
export async function getActiveWorkflowSession(
  problemId: number,
): Promise<WorkflowSessionSnapshotDto> {
  const response = await http.get<WorkflowSessionSnapshotDto>(
    '/api/tutor/workflow/sessions/active',
    { params: { problemId } },
  );
  return response.data;
}
