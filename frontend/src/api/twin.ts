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
  currentScore: number;
  confidence: number;
  recentEventReasons: string[];
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

/** GET /api/learner/twin/weekly */
export async function fetchWeeklyReport(): Promise<WeeklyReport> {
  const response = await http.get<WeeklyReport>('/api/learner/twin/weekly');
  return response.data;
}
