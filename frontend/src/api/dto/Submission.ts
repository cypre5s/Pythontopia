export type SubmissionSource =
  | 'TRAINING'
  | 'PATH_QUEST'
  | 'BUG_MINE_RETRY'
  | 'BUG_MINE_VARIANT'
  | 'CLASSROOM';

export type SubmissionStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'PASSED'
  | 'FAILED'
  | 'ERROR';

export type Verdict = 'AC' | 'WA' | 'RE' | 'TLE' | 'CE' | 'MLE' | 'SYSTEM_ERROR';

export interface SubmissionDto {
  id: number;
  userId: number;
  problemId: number;
  questId: number | null;
  source: SubmissionSource;
  language: string;
  code: string;
  submittedAt: string;
  status: SubmissionStatus;
  runtimeMs: number | null;
  memoryKb: number | null;
}

export interface JudgeResultDto {
  id: number;
  submissionId: number;
  verdict: Verdict;
  passedCases: number;
  totalCases: number;
  failureReason: string | null;
  trace: Record<string, unknown>;
  createdAt: string;
}

export interface SubmissionDetailDto {
  submission: SubmissionDto;
  judgeResult: JudgeResultDto | null;
}

export interface SubmissionCreateRequest {
  problemId: number;
  code: string;
  questId?: number;
  source?: SubmissionSource;
}

export interface PublicProblemDto {
  id: number;
  packId: number;
  kcCode: string;
  title: string;
  statementMarkdown: string;
  inputDescription: string;
  outputDescription: string;
  difficulty: string;
  samples: { input: string; output: string }[];
}
