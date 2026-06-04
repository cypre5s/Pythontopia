// 多模态媒资前端访问层（Phase 0/1）。
//
// 后端端点（MediaAssetController）：
//   POST /api/media/image            → MediaAssetDto（按 prompt 文生图；无 apikey 返回占位 degraded=true）
//   POST /api/media/storyboard/video → MediaAssetDto（图序列+TTS 经 ffmpeg 合成 mp4；无 ffmpeg 抛降级错误）
//   GET  /api/media/assets/{id}      → 媒资字节流（匿名，支持 Range，供 <img>/<video> src 直连）
//
// 设计：生成走 http(axios) 带鉴权；展示用返回的 url 给 <img>/<video> src 直连（该 GET 端点匿名放行）。

import { http } from './http';

export type MediaAssetKind = 'IMAGE' | 'AUDIO' | 'VIDEO';

export interface MediaAssetDto {
  id: number;
  kind: MediaAssetKind;
  /** 可直接用于 <img>/<video> src 的相对地址（/api/media/assets/{id}）。 */
  url: string;
  mimeType: string;
  /** true 表示本地占位/降级产物（未接入真实讯飞生成），前端可显示角标。 */
  degraded: boolean;
  source: string;
  prompt: string | null;
  kcCodes: string[];
}

export interface StoryboardSceneSpec {
  imagePrompt?: string;
  narration?: string;
  voice?: string;
  durationSeconds?: number;
  kcCodes?: string[];
}

/** 文生图：按 prompt 生成插画/知识点图解。失败由调用方决定回退。 */
export async function generateImage(
  prompt: string,
  label?: string,
  kcCodes?: string[],
): Promise<MediaAssetDto> {
  const res = await http.post<MediaAssetDto>('/api/media/image', { prompt, label, kcCodes });
  return res.data;
}

/** 合成故事板短视频（图序列 + TTS）。无 ffmpeg 时后端抛 VIDEO_COMPOSE_UNAVAILABLE，调用方回退实时剧场。 */
export async function composeStoryboardVideo(
  scenes: StoryboardSceneSpec[],
): Promise<MediaAssetDto> {
  const res = await http.post<MediaAssetDto>('/api/media/storyboard/video', { scenes });
  return res.data;
}
