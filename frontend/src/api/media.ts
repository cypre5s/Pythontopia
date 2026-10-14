// Legacy media API shapes. Image generation, speech recognition, and dubbing are disabled;
// Tutor image input uses the DeepSeek vision route and receives text responses.

import { http } from './http';

export type MediaAssetKind = 'IMAGE' | 'AUDIO' | 'VIDEO';

export interface MediaAssetDto {
  id: number;
  kind: MediaAssetKind;
  /** 可直接用于 <img>/<video> src 的相对地址（/api/media/assets/{id}）。 */
  url: string;
  mimeType: string;
  /** true 表示历史或非图像链路的降级产物；新图像生成会在 Provider 不可用时直接报错。 */
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
/** Disabled compatibility route; Tutor images are input to DeepSeek and never generated here. */
export async function generateImage(
  prompt: string,
  label?: string,
  kcCodes?: string[],
): Promise<MediaAssetDto> {
  const res = await http.post<MediaAssetDto>(
    '/api/media/image',
    { prompt, label, kcCodes },
    { suppressNetworkToast: true },
  );
  return res.data;
}

/** Legacy storyboard route; speech synthesis is disabled. */
export async function composeStoryboardVideo(
  scenes: StoryboardSceneSpec[],
): Promise<MediaAssetDto> {
  const res = await http.post<MediaAssetDto>(
    '/api/media/storyboard/video',
    { scenes },
    { suppressNetworkToast: true },
  );
  return res.data;
}

// ── 异步视频合成作业（media job：排队 + 幂等去重 + 进度上报，支撑高并发提交）─────────────
//
// 契约见 docs/plans/2026-06-11-baixiao-video-studio.md §4.2（MediaJobController）：
//   POST /api/media/storyboard/video/jobs → 202 {jobId,status,cached,assetId?}
//        （命中幂等缓存时 cached=true,status=SUCCESS,assetId 直给，无需订阅进度）
//   GET  /api/media/jobs/{id}             → {jobId,status,progress,assetId?,url?,errorCode?}（轮询兜底）
//   GET  /api/media/jobs/{id}/stream      → SSE plan/trace/result/error（前端用 useSseStream 走 fetch+JWT）

export type MediaJobStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

/** 合成作业实时进度（worker 逐镜回写；与 media_job.progress_json 对齐）。 */
export interface MediaJobProgress {
  phase?: string;
  sceneIndex?: number;
  sceneTotal?: number;
  roleKey?: string;
  message?: string;
  percent?: number;
}

/** 创建合成作业的响应（202）。cached=true 表示命中幂等缓存，assetId 直给、无需订阅进度。 */
export interface MediaJobCreateResponse {
  jobId: number;
  status: MediaJobStatus;
  cached: boolean;
  assetId?: number;
}

/** 轮询作业状态的响应（SSE 断流/异常时兜底）。 */
export interface MediaJobDto {
  jobId: number;
  status: MediaJobStatus;
  progress: MediaJobProgress;
  assetId?: number;
  url?: string;
  errorCode?: string;
}

/** SSE `result` 事件载荷（合成成片就绪）。 */
export interface MediaJobResult {
  jobId: number;
  assetId: number;
  /** 可直接用于 <video> src 的相对地址（/api/media/assets/{assetId}）。 */
  url: string;
}

/**
 * 创建故事板短视频合成作业（异步、高并发、按 spec 指纹幂等）。
 * 命中已成功成片时后端直接返回 cached=true,status=SUCCESS,assetId；否则入队 PENDING，
 * 调用方用 useSseStream('/api/media/jobs/{jobId}/stream') 订阅进度。
 */
export async function createStoryboardVideoJob(
  scenes: StoryboardSceneSpec[],
): Promise<MediaJobCreateResponse> {
  const res = await http.post<MediaJobCreateResponse>(
    '/api/media/storyboard/video/jobs',
    { scenes },
    { suppressNetworkToast: true },
  );
  return res.data;
}

/** 轮询合成作业状态（SSE 断流/异常时兜底，不伪造结果）。 */
export async function getMediaJob(id: number): Promise<MediaJobDto> {
  const res = await http.get<MediaJobDto>(`/api/media/jobs/${id}`, {
    suppressNetworkToast: true,
  });
  return res.data;
}

/** Legacy ASR response; the server keeps this route disabled. */
export interface AsrResponse {
  text: string;
  degraded: boolean;
}

/** Uploads to the disabled compatibility route; callers should use text input. */
export async function transcribeAudio(audio: Blob): Promise<AsrResponse> {
  const form = new FormData();
  form.append('audio', audio, 'speech.webm');
  const res = await http.post<AsrResponse>('/api/media/asr', form, {
    suppressNetworkToast: true,
  });
  return res.data;
}
