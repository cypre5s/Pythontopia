import { request } from '../utils/request';
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

export function getResource(id: number): Promise<LearningResource> {
  return request<LearningResource>(`/workshop/resources/${id}`);
}
