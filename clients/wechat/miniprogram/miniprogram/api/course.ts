import { mediaUrl, request } from '../utils/request';
import { Chapter, CoursePack, CoursePage, CourseProblem, CourseVideo, Kc } from './dto/course';

export function getCoursePack(): Promise<CoursePack> {
  return request<CoursePack>('/course/pack');
}

export function listChapters(): Promise<Chapter[]> {
  return request<Chapter[]>('/course/chapters');
}

export function listKcs(chapterId?: number): Promise<Kc[]> {
  return request<Kc[]>('/course/kcs', { query: { chapterId } });
}

export function listPages(kcCode: string): Promise<CoursePage[]> {
  return request<CoursePage[]>('/course/pages', { query: { kcCode } });
}

export function listProblems(kcCode?: string): Promise<CourseProblem[]> {
  return request<CourseProblem[]>('/course/problems', { query: { kcCode } });
}

export function listVideos(chapterId?: number): Promise<CourseVideo[]> {
  return request<CourseVideo[]>('/course/videos', { query: { chapterId } });
}

/** 课件页渲染图（公开，无需鉴权头）。 */
export function pageImageUrl(pageId: number): string {
  return mediaUrl(`/course/pages/${pageId}/image`);
}

/** 任务点视频流（公开，支持 Range）。 */
export function videoStreamUrl(videoId: number): string {
  return mediaUrl(`/course/videos/${videoId}`);
}
