import { http } from './http';
import type { ClassroomDto } from './dto/ClassroomDto';
import type { ClassroomMemberDto } from './dto/ClassroomMemberDto';

export interface CreateClassroomRequest {
  name: string;
  description?: string;
}

export interface JoinClassroomRequest {
  joinCode: string;
}

export async function createClassroom(req: CreateClassroomRequest): Promise<ClassroomDto> {
  const response = await http.post<ClassroomDto>('/api/classrooms', req);
  return response.data;
}

export async function listMyClassrooms(): Promise<ClassroomDto[]> {
  const response = await http.get<ClassroomDto[]>('/api/classrooms/mine');
  return response.data;
}

export async function updateClassroom(
  classroomId: number,
  request: CreateClassroomRequest,
): Promise<ClassroomDto> {
  const response = await http.put<ClassroomDto>(`/api/teacher/classrooms/${classroomId}`, request);
  return response.data;
}

export async function joinClassroom(req: JoinClassroomRequest): Promise<ClassroomDto> {
  const response = await http.post<ClassroomDto>('/api/classrooms/join', req);
  return response.data;
}

export async function listClassroomMembers(classroomId: number): Promise<ClassroomMemberDto[]> {
  const response = await http.get<ClassroomMemberDto[]>(`/api/classrooms/${classroomId}/members`);
  return response.data;
}

export async function removeClassroomMember(classroomId: number, studentId: number): Promise<void> {
  await http.delete(`/api/classrooms/${classroomId}/members/${studentId}`);
}
