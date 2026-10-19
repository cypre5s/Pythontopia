import { http } from './http';
import type {
  WorkshopCreateRequest,
  WorkshopCreateResponse,
  WorkshopCheckResponse,
  WorkshopCheckSubmitRequest,
  WorkshopConsumptionResponse,
  WorkshopFeedbackRequest,
  WorkshopResourceEffectivenessDto,
  WorkshopResourceDto,
  WorkshopResourceListItemDto,
  WorkshopResourceType,
} from './dto/Workshop';

export type { WorkshopResourceEffectivenessDto } from './dto/Workshop';

// 覆盖后端工坊专用 180s read 上限，给双层公网代理和 JSON 映射留出余量。
const WORKSHOP_GENERATION_TIMEOUT_MS = 190_000;

interface ResourceCheckAttempt {
  idempotencyKey: string;
}

const resourceCheckAttempts = new Map<number, Map<string, ResourceCheckAttempt>>();

export async function createWorkshopResource(
  request: WorkshopCreateRequest,
): Promise<WorkshopCreateResponse> {
  const response = await http.post<WorkshopCreateResponse>(
    '/api/workshop/resources',
    request,
    { timeout: WORKSHOP_GENERATION_TIMEOUT_MS },
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

export async function getWorkshopResourceEffectiveness(
  id: number,
): Promise<WorkshopResourceEffectivenessDto> {
  const response = await http.get<WorkshopResourceEffectivenessDto>(
    `/api/workshop/resources/${id}/effectiveness`,
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
  const payload = JSON.stringify(request);
  let attempts = resourceCheckAttempts.get(id);
  if (!attempts) {
    attempts = new Map<string, ResourceCheckAttempt>();
    resourceCheckAttempts.set(id, attempts);
  }
  let attempt = attempts.get(payload);
  if (!attempt) {
    attempt = { idempotencyKey: crypto.randomUUID() };
    attempts.set(payload, attempt);
  }
  const response = await http.post<WorkshopCheckResponse>(
    `/api/workshop/resources/${id}/checks`,
    request,
    { headers: { 'Idempotency-Key': attempt.idempotencyKey } },
  );
  if (attempts.get(payload) === attempt) {
    attempts.delete(payload);
    if (attempts.size === 0 && resourceCheckAttempts.get(id) === attempts) {
      resourceCheckAttempts.delete(id);
    }
  }
  return response.data;
}

export async function submitWorkshopFeedback(
  id: number,
  request: WorkshopFeedbackRequest,
): Promise<void> {
  await http.post(`/api/workshop/resources/${id}/feedback`, request);
}
