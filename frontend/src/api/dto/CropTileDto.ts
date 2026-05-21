export type CropStatus = 'SEED' | 'SPROUT' | 'MATURE' | 'WITHERED' | 'LOCKED';

export interface CropTileDto {
  tileX: number;
  tileY: number;
  kcCode: string;
  status: CropStatus;
  unlocked: boolean;
  masteryScore: number | null;
  growthStage: number;
}
