import { API_BASE, API_PREFIX, STORAGE_TOKEN } from '../config';
import { ApiError, handleUnauthorized } from './request';

const CR_RE = /\r$/;
const LEADING_SPACE_RE = /^ /;

/** 解析后的单个 SSE 帧。 */
export interface SseMessage {
  event: string;
  data: string;
}

export interface SseHandlers {
  /** 每收到一个完整 SSE 帧回调一次（event 默认 'message'）。 */
  onMessage: (msg: SseMessage) => void;
  /** 网络错误 / 非 2xx 状态 / 鉴权失败时回调（仅一次）。 */
  onError?: (err: ApiError) => void;
  /** 流正常结束（连接关闭）后回调一次。 */
  onComplete?: () => void;
}

function byteAt(bytes: Uint8Array, index: number): number {
  return index < bytes.length ? bytes[index] : 0;
}

/**
 * 手动 UTF-8 解码（小程序运行时不保证全局 TextDecoder）。仅对「完整帧」字节解码——
 * SSE 帧以 ASCII 的 `\n\n` 分隔，多字节字符不会跨帧切断，故按帧解码是安全的。
 */
function utf8Decode(bytes: Uint8Array): string {
  let out = '';
  let i = 0;
  while (i < bytes.length) {
    const b0 = bytes[i];
    if (b0 < 0x80) {
      out += String.fromCharCode(b0);
      i += 1;
    } else if (b0 >= 0xc0 && b0 < 0xe0) {
      out += String.fromCharCode(((b0 & 0x1f) << 6) | (byteAt(bytes, i + 1) & 0x3f));
      i += 2;
    } else if (b0 >= 0xe0 && b0 < 0xf0) {
      out += String.fromCharCode(
        ((b0 & 0x0f) << 12) | ((byteAt(bytes, i + 1) & 0x3f) << 6) | (byteAt(bytes, i + 2) & 0x3f),
      );
      i += 3;
    } else {
      let cp =
        ((b0 & 0x07) << 18) |
        ((byteAt(bytes, i + 1) & 0x3f) << 12) |
        ((byteAt(bytes, i + 2) & 0x3f) << 6) |
        (byteAt(bytes, i + 3) & 0x3f);
      cp -= 0x10000;
      out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
      i += 4;
    }
  }
  return out;
}

function concat(a: Uint8Array, b: Uint8Array): Uint8Array {
  const out = new Uint8Array(a.length + b.length);
  out.set(a, 0);
  out.set(b, a.length);
  return out;
}

function parseFrame(frame: string): SseMessage | null {
  const lines = frame.split('\n');
  let event = 'message';
  const dataLines: string[] = [];
  let sawField = false;
  lines.forEach((raw) => {
    const line = raw.replace(CR_RE, '');
    if (line === '' || line.charAt(0) === ':') {
      return;
    }
    if (line.indexOf('event:') === 0) {
      event = line.slice(6).replace(LEADING_SPACE_RE, '');
      sawField = true;
    } else if (line.indexOf('data:') === 0) {
      dataLines.push(line.slice(5).replace(LEADING_SPACE_RE, ''));
      sawField = true;
    }
  });
  if (!sawField) {
    return null;
  }
  return { event, data: dataLines.join('\n') };
}

/**
 * 以 `wx.request({ enableChunked: true })` 消费 POST SSE 流（导师答疑 / 工坊生成）。
 * 自动注入 JWT、按 `\n\n` 切分帧、UTF-8 解码并逐帧回调。返回 RequestTask，页面卸载时可 abort。
 */
export function streamSse<B>(
  path: string,
  body: B,
  handlers: SseHandlers,
): WechatMiniprogram.RequestTask {
  const url = `${API_BASE}${API_PREFIX}${path}`;
  const header: Record<string, string> = {
    'content-type': 'application/json',
    Accept: 'text/event-stream',
  };
  const token = wx.getStorageSync<string>(STORAGE_TOKEN);
  if (token) {
    header.Authorization = `Bearer ${token}`;
  }

  let buffer: Uint8Array = new Uint8Array(0);
  let finished = false;

  const finishError = (err: ApiError): void => {
    if (finished) {
      return;
    }
    finished = true;
    if (handlers.onError) {
      handlers.onError(err);
    }
  };

  const pump = (): void => {
    for (;;) {
      let boundary = -1;
      for (let i = 0; i + 1 < buffer.length; i += 1) {
        if (buffer[i] === 0x0a && buffer[i + 1] === 0x0a) {
          boundary = i;
          break;
        }
      }
      if (boundary < 0) {
        break;
      }
      const frameBytes = buffer.subarray(0, boundary);
      buffer = buffer.slice(boundary + 2);
      const msg = parseFrame(utf8Decode(frameBytes));
      if (msg && !finished) {
        handlers.onMessage(msg);
      }
    }
  };

  const task = wx.request({
    url,
    method: 'POST',
    header,
    data: body as unknown as WechatMiniprogram.IAnyObject,
    enableChunked: true,
    responseType: 'text',
    success: (res) => {
      const status = res.statusCode;
      if (status === 401) {
        handleUnauthorized();
        finishError(new ApiError(401, 'UNAUTHORIZED', '登录已过期，请重新登录', [], ''));
        return;
      }
      if (status < 200 || status >= 300) {
        finishError(new ApiError(status, `HTTP_${status}`, `请求失败（${status}）`, [], ''));
      }
    },
    fail: (err) => {
      finishError(new ApiError(0, 'NETWORK_ERROR', err.errMsg || '网络请求失败', [], ''));
    },
    complete: () => {
      if (finished) {
        return;
      }
      // 兜底：收尾时若残留未以空行结束的最后一帧，尝试解析后再结束。
      if (buffer.length > 0) {
        const tail = parseFrame(utf8Decode(buffer));
        if (tail) {
          handlers.onMessage(tail);
        }
        buffer = new Uint8Array(0);
      }
      finished = true;
      if (handlers.onComplete) {
        handlers.onComplete();
      }
    },
  });

  task.onChunkReceived((res) => {
    if (finished) {
      return;
    }
    buffer = concat(buffer, new Uint8Array(res.data));
    pump();
  });

  return task;
}
