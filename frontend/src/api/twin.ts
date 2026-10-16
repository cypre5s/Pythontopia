import { http } from './http';

export interface MemoryBrief {
  totalEvents: number;
  failedPractices: number;
  succeededPractices: number;
  topKcCodes: string[];
  highlights: string[];
}

export interface InsightBrief {
  id: number;
  text: string;
  dimensionKey: string;
  tags: string[];
  createdAt: string;
}

export interface MasteryDeltaBrief {
  kcCode: string;
  currentScore: number | null;
  confidence: number | null;
  weeklyDelta: number | null;
  evidenceCount: number;
  evidenceSources: string[];
}

export interface ResourceRunBrief {
  agentName: string;
  scenario: string;
  status: string;
  evidencePageCount: number;
  createdAt: string;
}

export interface WeeklyReport {
  periodStart: string;
  periodEnd: string;
  memoryBrief: MemoryBrief;
  insights: InsightBrief[];
  masteryDeltas: MasteryDeltaBrief[];
  resourceRuns: ResourceRunBrief[];
}

export interface LearnerTwinKcProgress {
  kcCode: string;
  kcName: string;
  chapter: number;
  chapterName: string;
  assessed: boolean;
  masteryScore: number | null;
  confidence: number | null;
  reviewDue: boolean;
  dueAt: string | null;
  lastPracticedAt: string | null;
  consecutiveFailures: number;
  evidenceSources: string[];
}

export interface LearnerTwinNextStep {
  kcCode: string;
  kcName: string;
  chapter: number;
  chapterName: string;
  difficulty: number;
  action: 'REVIEW' | 'REMEDIATION' | 'PRACTICE';
  reason: string;
  remediationRequired: boolean;
}

export interface LearnerTwinState {
  assessedKcCount: number;
  totalKcCount: number;
  masteredKcCount: number;
  growthStage: string;
  kcs: LearnerTwinKcProgress[];
  nextSteps: LearnerTwinNextStep[];
}

/** GET /api/learner/twin/state */
export async function fetchLearnerTwinState(): Promise<LearnerTwinState> {
  const response = await http.get<LearnerTwinState>('/api/learner/twin/state');
  return response.data;
}

/** GET /api/learner/twin/weekly */
export async function fetchWeeklyReport(): Promise<WeeklyReport> {
  const response = await http.get<WeeklyReport>('/api/learner/twin/weekly');
  return response.data;
}
