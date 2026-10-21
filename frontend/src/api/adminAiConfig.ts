import { http } from './http';

export interface AdminAiConfigDto {
  provider: 'DeepSeek';
  model: 'deepseek-flash' | 'deepseek-v4-pro';
  apiKeyConfigured: boolean;
  keyStored: boolean;
  updatedAt: string | null;
}

export interface AdminAiConfigTestDto {
  success: boolean;
  model: string;
  message: string;
}

export async function fetchAdminAiConfig(): Promise<AdminAiConfigDto> {
  const response = await http.get<AdminAiConfigDto>('/api/admin/ai-config');
  return response.data;
}

export async function testAdminAiConfig(request: { model: string; apiKey?: string }): Promise<AdminAiConfigTestDto> {
  const response = await http.post<AdminAiConfigTestDto>('/api/admin/ai-config/test', request);
  return response.data;
}

export async function updateAdminAiConfig(request: { model: string; apiKey?: string }): Promise<AdminAiConfigDto> {
  const response = await http.put<AdminAiConfigDto>('/api/admin/ai-config', request);
  return response.data;
}
