import { http } from './http';

export interface AiRuntimeProtocolSummary {
  mcpToolCount: number;
  toolsWithEvidenceFieldCount: number;
  mcpResourceCount: number;
  aguiEventCount: number;
  skillPackageCount: number;
  evidenceSkillPackageCount: number;
  toolsWithEvidenceFields: string[];
  evidenceSkillPackages: string[];
}

export interface AiRuntimeEvidenceSummary {
  nodeCount: number;
  edgeCount: number;
  touchedKcCount: number;
  groundedPageCount: number;
  checkCoverageRate: number;
  graphCompletenessScore: number;
  graphMemoryScore: number;
  directSignalCount: number;
  propagatedSignalCount: number;
  retainedEvidenceCount: number;
}

export interface AiRuntimeLearnerTwinSummary {
  memoryEventCount: number;
  failedPracticeCount: number;
  succeededPracticeCount: number;
  insightCount: number;
  masteryKcCount: number;
  resourceRunCount: number;
}

export interface AiRuntimeExternalResearchSummary {
  allowedDomains: string[];
  defaultMaxSteps: number;
  citationPolicy: string;
}

export type AiRuntimeGateStatus = 'PASS' | 'WARN' | 'INSUFFICIENT_EVIDENCE';

export interface AiRuntimeGate {
  key: string;
  label: string;
  status: AiRuntimeGateStatus;
  value: number;
  threshold: number;
  evidenceRefs: string[];
}

export interface AiRuntimeSummary {
  protocol: AiRuntimeProtocolSummary;
  evidence: AiRuntimeEvidenceSummary;
  learnerTwin: AiRuntimeLearnerTwinSummary;
  externalResearch: AiRuntimeExternalResearchSummary;
  gates: AiRuntimeGate[];
}

export type RuntimeOperationStatus = AiRuntimeGateStatus;

export interface RuntimeLoopOperation {
  loopType: 'LEARNER' | 'DEBUG' | 'RESOURCE' | 'WORLD' | 'IMPROVE' | string;
  label: string;
  status: RuntimeOperationStatus;
  latestTraceId: string;
  nextAction: string;
  evidenceRefs: string[];
}

export interface RuntimeGovernanceItem {
  key: string;
  label: string;
  status: RuntimeOperationStatus;
  action: string;
  reviewRoute: string;
  evidenceRefs: string[];
}

export interface RuntimeOperationsSubject {
  scope: 'SELF' | 'PLATFORM';
  label: string;
  learnerCount: number;
  evidenceLearnerCount: number;
}

export interface AiRuntimeOperations {
  subject: RuntimeOperationsSubject;
  loops: RuntimeLoopOperation[];
  governance: RuntimeGovernanceItem[];
}

/** GET /api/ai/runtime/summary */
export async function fetchAiRuntimeSummary(): Promise<AiRuntimeSummary> {
  const response = await http.get<AiRuntimeSummary>('/api/ai/runtime/summary');
  return response.data;
}

/** GET /api/ai/runtime/operations */
export async function fetchAiRuntimeOperations(): Promise<AiRuntimeOperations> {
  const response = await http.get<AiRuntimeOperations>('/api/ai/runtime/operations');
  return response.data;
}
