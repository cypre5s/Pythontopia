// 章节视频讲解前端访问层。
//
// 后端端点（CourseVideoController，源无关）：
//   GET /api/course/videos?chapterId=N   → CourseVideoDto[]（读 video_manifest.json）
//   GET /api/course/videos/{id}          → 视频字节流（支持 HTTP Range，<video> 直连）
//
// 设计：列表走 http(axios) 拿 manifest JSON；播放 URL 给 <video src> 直连
// （后端该端点放行匿名，<video> 不会带 Authorization 头）。
// 课程当前可能没有任何视频（线下课），此时列表为空，页面回退到 PPT/问答。

import { http } from './http';

export interface CourseVideoDto {
  /** 视频在 manifest 中的稳定 id。 */
  id: number;
  /** 章节序号 1..7。 */
  chapter: number;
  /** 视频标题（超星任务点标题 / 章节小节名）。 */
  title: string;
  /** 时长（秒），未知为 null。 */
  durationSec: number | null;
  /** 关联 KC code（尽力匹配，可空）。 */
  kcCodes: string[];
  /** 视频文件是否已就绪可播放（manifest 有记录但文件未下载时为 false）。 */
  available: boolean;
}

/** 拉某章视频清单；任何失败（404/网络/后端未起）都降级成空数组，调用方据此回退。 */
export async function listChapterVideos(chapter: number): Promise<CourseVideoDto[]> {
  try {
    const res = await http.get<CourseVideoDto[]>('/api/course/videos', {
      params: { chapterId: chapter },
      suppressNetworkToast: true,
    });
    return Array.isArray(res.data) ? res.data : [];
  } catch {
    return [];
  }
}

/** <video> 可直连的流式播放 URL（dev 由 Vite 代理 /api，prod 同源）。 */
export function videoStreamUrl(id: number): string {
  return `/api/course/videos/${id}`;
}
