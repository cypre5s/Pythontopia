import { ApiClientError, http } from './http';
import type {
  LearningPathDto,
  PlanPathResponse,
} from './dto/Path';

/** 覆盖 graph 30s 读超时 + 1 次重试的窗口。 */
const PATH_PLAN_TIMEOUT_MS = 70_000;

export async function planLearningPath(): Promise<PlanPathResponse> {
  // 后端同步调 PathPlannerAgent（graph 读超时 30s，GraphClientRetryHelper 最多重试 1 次），
  // 全局 10s 默认会在规划还没返回时就断开 → 学生看到失败，后端却继续烧模型额度。
  const response = await http.post<PlanPathResponse>('/api/learner/path/plan', {}, {
    timeout: PATH_PLAN_TIMEOUT_MS,
  });
  return response.data;
}

export async function getActiveLearningPath(): Promise<LearningPathDto | null> {
  try {
    const response = await http.get<LearningPathDto>('/api/learner/path/active');
    return response.data;
  } catch (error) {
    if (isNotFound(error)) {
      return null;
    }
    throw error;
  }
}

export async function getArchivedLearningPaths(): Promise<LearningPathDto[]> {
  const response = await http.get<LearningPathDto[]>('/api/learner/path/archived');
  return response.data;
}

export async function getLearningPathById(id: number): Promise<LearningPathDto> {
  const response = await http.get<LearningPathDto>(`/api/learner/path/${id}`);
  return response.data;
}

function isNotFound(error: unknown): boolean {
  // http 拦截器把 AxiosError 统一包成 ApiClientError（status 在顶层），
  // 旧实现查 error.response 永远不命中 → 「尚未生成学习路径」404 被误当真错误，
  // 公告板显示报错而非「还没规划」空态。
  if (error instanceof ApiClientError) {
    return error.status === 404;
  }
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const { response } = error as { response?: { status?: number } };
    return response?.status === 404;
  }
  return false;
}
