export interface WorldEventDto {
  id: number;
  dateIso: string;
  eventType: string;
  payload: Record<string, unknown>;
  isProcessed: boolean;
  createdAt: string;
}
