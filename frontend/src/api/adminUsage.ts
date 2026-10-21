import { http } from './http';

export interface AdminUsageEventDto {
  id: number;
  eventType: 'LOOP_RUN' | 'RESOURCE_RUN' | 'MEDIA_JOB' | 'FORMAL_JUDGE';
  userId: number;
  username: string;
  label: string;
  occurredAt: string;
  status: string;
  inputTokens: number | null;
  outputTokens: number | null;
}

export interface AdminUsageDto {
  content: AdminUsageEventDto[];
  days: number;
  totalEvents: number;
  inputTokens: number;
  outputTokens: number;
  unknownTokenEvents: number;
}

export async function fetchAdminUsage(request: { days: number; username?: string }): Promise<AdminUsageDto> {
  const response = await http.get<AdminUsageDto>('/api/admin/usage', {
    params: { days: request.days, ...(request.username?.trim() ? { username: request.username.trim() } : {}) },
  });
  return response.data;
}
