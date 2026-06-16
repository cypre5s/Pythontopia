import { ref } from 'vue';
import {
  consumeWorkshopResource,
  createWorkshopResource,
  getWorkshopResource,
  listWorkshopResources,
  submitWorkshopFeedback,
} from '@/api/workshop';
import type {
  WorkshopCreateRequest,
  WorkshopCreateResponse,
  WorkshopConsumptionResponse,
  WorkshopFeedbackRequest,
  WorkshopResourceDto,
  WorkshopResourceListItemDto,
  WorkshopResourceType,
} from '@/api/dto/Workshop';

export type WorkshopRunPhase = 'idle' | 'creating' | 'success' | 'error';

export interface WorkshopFilters {
  type: WorkshopResourceType | null;
  kcCode: string | null;
}

export interface ResourcesState {
  list: WorkshopResourceListItemDto[];
  current: WorkshopResourceDto | null;
  currentConsumption: WorkshopConsumptionResponse | null;
  filters: WorkshopFilters;
  phase: WorkshopRunPhase;
  lastError: string | null;
  loading: boolean;
}

/** 工坊学习资源（「学」环：生成 / 消费 / 反馈）切片。 */
export function createWorkshopSlice() {
  const resources = ref<ResourcesState>({
    list: [],
    current: null,
    currentConsumption: null,
    filters: { type: null, kcCode: null },
    phase: 'idle',
    lastError: null,
    loading: false,
  });

  async function loadResources(): Promise<void> {
    resources.value.loading = true;
    try {
      const filters = resources.value.filters;
      const query: {
        type?: WorkshopResourceType;
        kcCode?: string;
        limit: number;
      } = { limit: 100 }; // 放宽上限：避免新生成的讲义因超过 30 条而看不到（书房应收齐所有产物）。
      if (filters.type) {
        query.type = filters.type;
      }
      if (filters.kcCode) {
        query.kcCode = filters.kcCode;
      }
      resources.value.list = await listWorkshopResources(query);
    } finally {
      resources.value.loading = false;
    }
  }

  async function createResource(request: WorkshopCreateRequest): Promise<WorkshopCreateResponse> {
    resources.value.phase = 'creating';
    resources.value.lastError = null;
    try {
      const response = await createWorkshopResource(request);
      resources.value.phase = 'success';
      await loadResources();
      return response;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      resources.value.lastError = message;
      resources.value.phase = 'error';
      throw error;
    }
  }

  async function openResource(id: number): Promise<WorkshopResourceDto> {
    resources.value.loading = true;
    try {
      const detail = await getWorkshopResource(id);
      resources.value.current = detail;
      resources.value.currentConsumption = null;
      return detail;
    } finally {
      resources.value.loading = false;
    }
  }

  async function consumeResource(id: number): Promise<WorkshopConsumptionResponse> {
    const response = await consumeWorkshopResource(id);
    if (resources.value.current && resources.value.current.id === id) {
      resources.value.currentConsumption = response;
    }
    return response;
  }

  async function giveResourceFeedback(id: number, request: WorkshopFeedbackRequest): Promise<void> {
    await submitWorkshopFeedback(id, request);
    if (resources.value.current && resources.value.current.id === id) {
      resources.value.current = {
        ...resources.value.current,
        feedbackStatus: request.status,
        feedbackNote: request.note ?? null,
      };
    }
    await loadResources();
  }

  function setResourceFilter(filters: WorkshopFilters): void {
    resources.value.filters = filters;
  }

  function resetResourcePhase(): void {
    resources.value.phase = 'idle';
    resources.value.lastError = null;
  }

  function streamCreateResource(_request: WorkshopCreateRequest): void {
    resources.value.phase = 'creating';
    resources.value.lastError = null;
  }

  return {
    resources,
    loadResources,
    createResource,
    openResource,
    consumeResource,
    giveResourceFeedback,
    setResourceFilter,
    resetResourcePhase,
    streamCreateResource,
  };
}
