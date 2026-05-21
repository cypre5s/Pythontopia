import { http } from './http';
import type { ChapterDto } from './dto/ChapterDto';
import type { CoursePackDto } from './dto/CoursePackDto';
import type { KcDto } from './dto/KcDto';
import type { PageDto } from './dto/PageDto';
import type { ProblemDto } from './dto/ProblemDto';

export async function getCoursePack(packId = 43): Promise<CoursePackDto> {
  const response = await http.get<CoursePackDto>(`/api/course/packs/${packId}`);
  return response.data;
}

export async function listChapters(packId = 43): Promise<ChapterDto[]> {
  const response = await http.get<ChapterDto[]>(`/api/course/packs/${packId}/chapters`);
  return response.data;
}

export async function listKnowledgeComponents(packId = 43): Promise<KcDto[]> {
  const response = await http.get<KcDto[]>(`/api/course/packs/${packId}/kcs`);
  return response.data;
}

export async function listKcPages(kcCode: string, packId = 43): Promise<PageDto[]> {
  const response = await http.get<PageDto[]>(`/api/course/packs/${packId}/kcs/${kcCode}/pages`);
  return response.data;
}

export async function listProblems(packId = 43): Promise<ProblemDto[]> {
  const response = await http.get<ProblemDto[]>(`/api/course/packs/${packId}/problems`);
  return response.data;
}
