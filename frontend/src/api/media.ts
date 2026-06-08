// 多模态媒资前端访问层。
//
// 后端端点（MediaAssetController / MediaAsrController）：
//   POST /api/media/image            → MediaAssetDto（按 prompt 文生图；无 apikey 返回占位 degraded=true）
//   POST /api/media/storyboard/video → MediaAssetDto（图序列+TTS 经 ffmpeg 合成 mp4；无 ffmpeg 抛降级错误）
//   POST /api/media/asr              → AsrResponse（multipart 上传录音 → 讯飞语音听写转文本；不可用返回 degraded=true）
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
  const res = await http.post<MediaAssetDto>(
    '/api/media/image',
    { prompt, label, kcCodes },
    { suppressNetworkToast: true },
  );
  return res.data;
}

/** 合成故事板短视频（图序列 + TTS）。无 ffmpeg 时后端抛 VIDEO_COMPOSE_UNAVAILABLE，调用方回退实时剧场。 */
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

/** 语音听写（ASR）结果。degraded=true 表示服务端 ASR 不可用（无讯飞凭据 / 无 ffmpeg / 转码失败）。 */
export interface AsrResponse {
  text: string;
  degraded: boolean;
}

/**
 * 把一段录音（MediaRecorder 的 Blob）上传到 /api/media/asr 转写为文本。随身助教在浏览器
 * Web Speech API 不可用时回退到此。失败 / 不可用由调用方处理（提示改用键盘输入），不抛全局网络 toast。
 */
export async function transcribeAudio(audio: Blob): Promise<AsrResponse> {
  const form = new FormData();
  form.append('audio', audio, 'speech.webm');
  const res = await http.post<AsrResponse>('/api/media/asr', form, {
    suppressNetworkToast: true,
  });
  return res.data;
}
