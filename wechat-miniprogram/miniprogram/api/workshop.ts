import { request } from '../utils/request';
import { SseHandlers, streamSse } from '../utils/sse';
import { LearningResource, ResourceCreateRequest, ResourceCreateResponse } from './dto/workshop';

/**
 * 非流式资源生成：阻塞直到 graph 生成完成并落库，返回 runId / resourceId / status。
 * 随后用 getResource(resourceId) 拉取完整内容渲染。
 */
export function createResource(body: ResourceCreateRequest): Promise<ResourceCreateResponse> {
  return request<ResourceCreateResponse, ResourceCreateRequest>('/workshop/resources', {
    method: 'POST',
    data: body,
  });
}

/**
 * 流式资源生成（SSE，仅 STUDENT）。事件序列：plan → trace* → result →（error）。
 * `result` 帧载荷与非流式 create 同形（runId / resourceId / status）；收尾后用
 * getResource(resourceId) 拉完整内容。无原生 EventSource，走 wx.request({enableChunked:true})。
 */
export function streamResources(
  body: ResourceCreateRequest,
  handlers: SseHandlers,
): WechatMiniprogram.RequestTask {
  return streamSse<ResourceCreateRequest>('/workshop/resources/stream', body, handlers);
}

export function getResource(id: number): Promise<LearningResource> {
  return request<LearningResource>(`/workshop/resources/${id}`);
}
