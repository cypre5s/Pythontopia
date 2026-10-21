import { http } from './http';

export type AdminCourseResourceType = 'ALL' | 'DOCUMENT' | 'CHAPTER' | 'KNOWLEDGE_POINT' | 'EXAMPLE' | 'PROBLEM';

export interface AdminCourseResourceDto {
  resourceType: Exclude<AdminCourseResourceType, 'ALL'>;
  resourceId: number;
  title: string;
  category: string;
  status: string;
  enabled: boolean;
  preview: string;
  pageCount: number | null;
}

export async function setAdminCourseResourceEnabled(
  type: Exclude<AdminCourseResourceType, 'ALL'>,
  resourceId: number,
  enabled: boolean,
): Promise<void> {
  await http.put(`/api/admin/course-resources/${type}/${resourceId}`, { enabled });
}

export interface AdminCourseResourcePageDto {
  content: AdminCourseResourceDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export async function searchAdminCourseResources(request: {
  page: number;
  size: number;
  q?: string;
  type: AdminCourseResourceType;
}): Promise<AdminCourseResourcePageDto> {
  const response = await http.get<AdminCourseResourcePageDto>('/api/admin/course-resources', {
    params: {
      page: request.page,
      size: request.size,
      type: request.type,
      ...(request.q?.trim() ? { q: request.q.trim() } : {}),
    },
  });
  return response.data;
}
