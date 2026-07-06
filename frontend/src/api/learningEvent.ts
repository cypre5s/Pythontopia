import { http } from './http';

export interface LearningEventLogRequest {
  eventType: string;
  payload: Record<string, unknown>;
}

export async function postLearningEvent(event: LearningEventLogRequest): Promise<void> {
  await http.post('/api/learning/events', event);
}

export async function postLearningEventBatch(events: LearningEventLogRequest[]): Promise<void> {
  await http.post('/api/learning/events/batch', events);
}
