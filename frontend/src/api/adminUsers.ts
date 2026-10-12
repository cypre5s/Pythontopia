import { http } from './http';
import type { UserRole } from './dto/UserDto';

export type AdminUserStatus = 'ACTIVE' | 'DISABLED';
export type AdminUserStatusFilter = 'ALL' | AdminUserStatus;
export type AdminUserRoleFilter = 'ALL' | UserRole;

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
  role?: UserRole;
}

export async function searchAdminUsers(search: AdminUserSearch): Promise<AdminUserPageDto> {
  const query = search.q?.trim();
  const params = {
    page: search.page,
    size: search.size,
    ...(query ? { q: query } : {}),
    ...(search.status ? { status: search.status } : {}),
    ...(search.role ? { role: search.role } : {}),
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
