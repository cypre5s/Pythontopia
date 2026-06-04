import { http } from './http';
import type { WorkshopResourceType } from './dto/Workshop';

// 百晓工坊「意图路由」前端契约。后端 POST /api/intent/route（Java 代理 → Python IntentRouterAgent），
// 单次分类（非流式）：把一句话判定成该调用的工具并锚定 KC，前端据此分派到既有导师 / 工坊 SSE 管线。

/**
 * ASK = 答疑（导师管线）；VIDEO = B站精选讲解视频（外部检索，非生成）；
 * 其余 6 个与 WorkshopResourceType 一一对应（工坊生成管线）。
 */
export type IntentKind = 'ASK' | 'VIDEO' | WorkshopResourceType;

/** 课程 KC 目录条目：前端为 @ 提及菜单本就全量加载，路由时透传给后端做语义锚定。 */
export interface CourseKcRef {
  code: string;
  name: string;
}

export interface IntentRouteRequest {
  message: string;
  /** 学生手动 @ 指定的 KC（最高优先级）。 */
  manualKcCodes?: string[];
  courseKcs?: CourseKcRef[];
  currentChapter?: number;
  recentKcCodes?: string[];
}

export interface IntentRouteResponse {
  intent: IntentKind;
  kcCodes: string[];
  focusHint: string;
  confidence: number;
  reason: string;
}

/** ASK / VIDEO 之外的 6 个意图即资源类型，可直接喂给工坊生成接口。 */
export function isResourceIntent(intent: IntentKind): intent is WorkshopResourceType {
  return intent !== 'ASK' && intent !== 'VIDEO';
}

export async function routeIntent(
  request: IntentRouteRequest,
): Promise<IntentRouteResponse> {
  const response = await http.post<IntentRouteResponse>('/api/intent/route', request);
  return response.data;
}
