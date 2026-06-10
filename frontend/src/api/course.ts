import { http } from './http';
import type { ChapterDto } from './dto/ChapterDto';
import type { CoursePackDto } from './dto/CoursePackDto';
import type { KcDto } from './dto/KcDto';
import type { PageDto } from './dto/PageDto';
import type { ProblemDto } from './dto/ProblemDto';

// 后端只暴露当前激活课程包 (CoursePackService.ACTIVE_PACK_ID = 43)，
// 故这些端点都不接收 packId。

export async function getCoursePack(): Promise<CoursePackDto> {
  const response = await http.get<CoursePackDto>(`/api/course/pack`);
  return response.data;
}

export async function listChapters(): Promise<ChapterDto[]> {
  const response = await http.get<ChapterDto[]>(`/api/course/chapters`);
  return response.data;
}

export async function listKnowledgeComponents(chapterId?: number): Promise<KcDto[]> {
  const params: Record<string, number> = {};
  if (chapterId !== undefined) {
    params.chapterId = chapterId;
  }
  const response = await http.get<KcDto[]>(`/api/course/kcs`, { params });
  return response.data;
}

export async function listKcPages(kcCode: string): Promise<PageDto[]> {
  const response = await http.get<PageDto[]>(`/api/course/pages`, {
    params: { kcCode },
  });
  return response.data;
}

export async function getProblem(problemId: number): Promise<ProblemDto> {
  const response = await http.get<ProblemDto>(`/api/course/problems/${problemId}`);
  return response.data;
}
