import { http } from './http';
import type {
  WorkshopCreateRequest,
  WorkshopCreateResponse,
  WorkshopCheckResponse,
  WorkshopCheckSubmitRequest,
  WorkshopConsumptionResponse,
  WorkshopFeedbackRequest,
  WorkshopResourceDto,
  WorkshopResourceListItemDto,
  WorkshopResourceType,
} from './dto/Workshop';

export async function createWorkshopResource(
  request: WorkshopCreateRequest,
): Promise<WorkshopCreateResponse> {
  const response = await http.post<WorkshopCreateResponse>(
    '/api/workshop/resources',
    request,
  );
  return response.data;
}

export interface WorkshopListQuery {
  type?: WorkshopResourceType;
  kcCode?: string;
  limit?: number;
  offset?: number;
}

export async function listWorkshopResources(
  query: WorkshopListQuery = {},
): Promise<WorkshopResourceListItemDto[]> {
  const params: Record<string, string | number> = {};
  if (query.type) params.type = query.type;
  if (query.kcCode) params.kcCode = query.kcCode;
  if (query.limit !== undefined) params.limit = query.limit;
  if (query.offset !== undefined) params.offset = query.offset;
  const response = await http.get<WorkshopResourceListItemDto[]>(
    '/api/workshop/resources',
    { params },
  );
  return response.data;
}

export async function getWorkshopResource(
  id: number,
): Promise<WorkshopResourceDto> {
  const response = await http.get<WorkshopResourceDto>(
    `/api/workshop/resources/${id}`,
  );
  return response.data;
}

export async function consumeWorkshopResource(
  id: number,
): Promise<WorkshopConsumptionResponse> {
  const response = await http.post<WorkshopConsumptionResponse>(
    `/api/workshop/resources/${id}/consumptions`,
  );
  return response.data;
}

export async function submitWorkshopResourceCheck(
  id: number,
  request: WorkshopCheckSubmitRequest,
): Promise<WorkshopCheckResponse> {
  const response = await http.post<WorkshopCheckResponse>(
    `/api/workshop/resources/${id}/checks`,
    request,
  );
  return response.data;
}

export async function submitWorkshopFeedback(
  id: number,
  request: WorkshopFeedbackRequest,
): Promise<void> {
  await http.post(`/api/workshop/resources/${id}/feedback`, request);
}
