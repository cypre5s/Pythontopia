import { http } from './http';
import type { UserRole } from './dto/UserDto';

export type AdminUserStatus = 'ACTIVE' | 'DISABLED';
export type AdminUserStatusFilter = 'ALL' | AdminUserStatus;

export interface AdminUserDto {
  id: number;
  username: string;
  displayName: string;
  role: UserRole;
  status: AdminUserStatus;
}

export interface AdminUserPageDto {
  content: AdminUserDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminUserSearch {
  page: number;
  size: number;
  q?: string;
  status?: AdminUserStatus;
}

export async function searchAdminUsers(search: AdminUserSearch): Promise<AdminUserPageDto> {
  const query = search.q?.trim();
  const params = {
    page: search.page,
    size: search.size,
    ...(query ? { q: query } : {}),
    ...(search.status ? { status: search.status } : {}),
  };
  const response = await http.get<AdminUserPageDto>('/api/admin/users', { params });
  return response.data;
}

export async function setAdminUserStatus(
  userId: number,
  status: AdminUserStatus,
): Promise<AdminUserDto> {
  const response = await http.patch<AdminUserDto>(`/api/admin/users/${userId}`, { status });
  return response.data;
}
