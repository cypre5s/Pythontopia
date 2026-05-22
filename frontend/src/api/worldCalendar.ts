import { http } from './http';
import type { WorldCalendarDto } from './dto/WorldCalendarDto';
import type { WorldEventDto } from './dto/WorldEventDto';

export async function fetchCalendarToday(): Promise<WorldCalendarDto> {
  const response = await http.get<WorldCalendarDto>('/api/world/calendar/today');
  return response.data;
}

export async function fetchCalendarEvents(): Promise<WorldEventDto[]> {
  const response = await http.get<WorldEventDto[]>('/api/world/calendar/events');
  return response.data;
}

export async function markCalendarEventProcessed(eventId: number): Promise<WorldEventDto> {
  const response = await http.post<WorldEventDto>(
    `/api/world/calendar/events/${eventId}/process`,
  );
  return response.data;
}
