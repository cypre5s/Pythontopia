import { request } from '../utils/request';
import { IntentRouteRequest, IntentRouteResponse } from './dto/intent';

/** 单句意图分类 + KC 锚定（非流式，单次调用）。 */
export function routeIntent(body: IntentRouteRequest): Promise<IntentRouteResponse> {
  return request<IntentRouteResponse, IntentRouteRequest>('/intent/route', {
    method: 'POST',
    data: body,
  });
}
