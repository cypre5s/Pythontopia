import { http } from './http';
import type { ChapterDto } from './dto/ChapterDto';
import type { CoursePackDto } from './dto/CoursePackDto';
import type { KcDto } from './dto/KcDto';
import type { PageDto } from './dto/PageDto';
import type { ProblemDto } from './dto/ProblemDto';

// 后端只暴露当前激活课程包 (CoursePackService.ACTIVE_PACK_ID = 43)。
// 保留 packId 参数仅作历史兼容；实际请求路径不再使用它。

export async function getCoursePack(_packId: number = 43): Promise<CoursePackDto> {
  void _packId;
  const response = await http.get<CoursePackDto>(`/api/course/pack`);
  return response.data;
}

export async function listChapters(_packId: number = 43): Promise<ChapterDto[]> {
  void _packId;
  const response = await http.get<ChapterDto[]>(`/api/course/chapters`);
  return response.data;
}

export async function listKnowledgeComponents(
  _packId: number = 43,
  chapterId?: number,
): Promise<KcDto[]> {
  void _packId;
  const params: Record<string, number> = {};
  if (chapterId !== undefined) {
    params.chapterId = chapterId;
  }
  const response = await http.get<KcDto[]>(`/api/course/kcs`, { params });
  return response.data;
}

export async function listKcPages(kcCode: string, _packId: number = 43): Promise<PageDto[]> {
  void _packId;
  const response = await http.get<PageDto[]>(`/api/course/pages`, {
    params: { kcCode },
  });
  return response.data;
}

export async function getProblem(problemId: number): Promise<ProblemDto> {
  const response = await http.get<ProblemDto>(`/api/course/problems/${problemId}`);
  return response.data;
}
