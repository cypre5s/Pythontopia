import { SseHandlers, streamSse } from '../utils/sse';
import { TutorAskRequest } from './dto/tutor';

/**
 * 导师答疑（SSE 流，仅 STUDENT）。事件序列：plan → trace* → token* → result →（error）。
 * 小程序无原生 EventSource，走 wx.request({enableChunked:true})。返回 RequestTask，页面卸载可 abort。
 */
export function askTutorStream(
  body: TutorAskRequest,
  handlers: SseHandlers,
): WechatMiniprogram.RequestTask {
  return streamSse<TutorAskRequest>('/tutor/ask', body, handlers);
}
