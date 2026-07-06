export type SubmissionStatus = 'PENDING' | 'RUNNING' | 'PASSED' | 'FAILED' | 'ERROR';

export type Verdict = 'AC' | 'WA' | 'RE' | 'TLE' | 'CE' | 'MLE' | 'SYSTEM_ERROR';

export type SubmissionSource =
  | 'TRAINING'
  | 'PATH_QUEST'
  | 'BUG_MINE_RETRY'
  | 'BUG_MINE_ECHO'
  | 'BUG_MINE_VARIANT'
  | 'CLASSROOM';

export interface ProblemSample {
  input?: string;
  output?: string;
  expected_stdout?: string;
  explanation?: string;
}

export interface PublicProblem {
  id: number;
  packId: number;
  kcCode: string;
  title: string;
  statementMarkdown: string;
  inputDescription: string | null;
  outputDescription: string | null;
  difficulty: string | null;
  samples: ProblemSample[] | null;
}

export interface SubmissionCreateRequest {
  problemId: number;
  code: string;
  questId?: number;
  source: SubmissionSource;
}

export interface Submission {
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

export interface JudgeResult {
  id: number;
  submissionId: number;
  verdict: Verdict;
  passedCases: number;
  totalCases: number;
  failureReason: string | null;
  trace: unknown;
  createdAt: string;
}

export interface SubmissionDetail {
  submission: Submission;
  judgeResult: JudgeResult | null;
}
