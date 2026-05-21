export interface MasterySnapshotDto {
  kcCode: string;
  masteryScore: number;
  confidence: number;
  lastEventAt: string | null;
  latestSource: string | null;
}
