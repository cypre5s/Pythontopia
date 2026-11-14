import type { SubmissionStatus } from './types';

export interface AssistantLocalRun {
  question_id: string;
  run_id: string;
  code: string;
  completed_at: string;
  duration_ms: number;
  cases: { stdout: string; error: string; status: SubmissionStatus }[];
  error: string;
  stopped: boolean;
}
export interface AssistantFeedback {
  local_run?: AssistantLocalRun;
  displayed?: 'local_run' | 'submission';
  selected_case?: number;
}
export interface AssistantState extends AssistantFeedback {
  page_no?: number;
  video_time?: number;
  question_id?: string;
  answer?: string | boolean;
  code?: string;
}
