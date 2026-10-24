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

export interface AdminStudentImportCredentialDto {
  username: string;
  displayName: string;
  password: string;
}

export interface AdminStudentImportResultDto {
  createdCount: number;
  skippedCount: number;
  credentials: AdminStudentImportCredentialDto[];
}

export interface AdminStudentDetailDto {
  account: AdminUserDto & { createdAt: string };
  profileDimensions: Record<string, {
    score?: number | null;
    evidence_count?: number;
    tags?: string[];
    source?: string;
  }>;
  mastery: Array<{
    kcName: string | null;
    chapterOrder: number | null;
    masteryScore: number | null;
    confidence: number | null;
    updatedAt: string | null;
  }>;
  activity: Array<{ id: number; eventType: string; source: string; occurredAt: string }>;
  submissions: Array<{
    id: number;
    problemTitle: string | null;
    status: string;
    judgeVerdict: string | null;
    passedCases: number;
    totalCases: number;
    submittedAt: string;
  }>;
  classrooms: Array<{
    classroomId: number;
    classroomName: string;
    teacherName: string;
    status: string;
    joinedAt: string;
  }>;
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

export async function importAdminStudents(file: File): Promise<AdminStudentImportResultDto> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await http.post<AdminStudentImportResultDto>('/api/admin/student-imports', formData);
  return response.data;
}

export async function setAdminUserStatus(
  userId: number,
  status: AdminUserStatus,
): Promise<AdminUserDto> {
  const response = await http.patch<AdminUserDto>(`/api/admin/users/${userId}`, { status });
  return response.data;
}

export async function fetchAdminStudentDetail(userId: number): Promise<AdminStudentDetailDto> {
  const response = await http.get<AdminStudentDetailDto>(`/api/admin/users/${userId}/detail`);
  return response.data;
}
