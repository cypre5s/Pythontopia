import { API_BASE, API_PREFIX, LOGIN_ROUTE, STORAGE_TOKEN, STORAGE_USER } from '../config';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

/** 后端统一错误返回体（见 backend ApiError）。 */
export interface ApiErrorEnvelope {
  code: string;
  message: string;
  details: string[];
  traceId: string;
  timestamp: string;
}

/** 业务/网络错误统一抛出此类型，页面可读 code / message / details。 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: string[];
  readonly traceId: string;

  constructor(status: number, code: string, message: string, details: string[], traceId: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
    this.traceId = traceId;
  }
}

export type QueryValue = string | number | boolean | undefined | null;

export interface RequestOptions<B> {
  method?: HttpMethod;
  data?: B;
  query?: Record<string, QueryValue>;
  /** 是否注入 Authorization 头，默认 true；登录 / 注册显式设为 false。 */
  auth?: boolean;
}

function buildQuery(query?: Record<string, QueryValue>): string {
  if (!query) {
    return '';
  }
  const parts: string[] = [];
  Object.keys(query).forEach((key) => {
    const value = query[key];
    if (value === undefined || value === null) {
      return;
    }
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  });
  return parts.length > 0 ? `?${parts.join('&')}` : '';
}

// 避免 401 并发时重复 reLaunch。
let reloginInFlight = false;

function handleUnauthorized(): void {
  try {
    wx.removeStorageSync(STORAGE_TOKEN);
    wx.removeStorageSync(STORAGE_USER);
  } catch {
    // 忽略存储异常。
  }
  if (reloginInFlight) {
    return;
  }
  reloginInFlight = true;
  wx.reLaunch({
    url: LOGIN_ROUTE,
    complete: () => {
      reloginInFlight = false;
    },
  });
}

function toEnvelope(payload: unknown): Partial<ApiErrorEnvelope> {
  if (payload !== null && typeof payload === 'object') {
    return payload as Partial<ApiErrorEnvelope>;
  }
  return {};
}

/**
 * wx.request 的 Promise 包装：
 * - URL = API_BASE + /api + path (+ query)
 * - 默认注入 `Authorization: Bearer <pyt_token>`
 * - 2xx 解析为 T（204 解析为 undefined）
 * - 401 清 token 并 reLaunch 登录页
 * - 其余状态解析错误信封并 reject ApiError
 */
export function request<T, B = unknown>(path: string, options: RequestOptions<B> = {}): Promise<T> {
  const method: HttpMethod = options.method ?? 'GET';
  const useAuth = options.auth !== false;
  const url = `${API_BASE}${API_PREFIX}${path}${buildQuery(options.query)}`;

  const header: Record<string, string> = {
    'content-type': 'application/json',
  };
  if (useAuth) {
    const token = wx.getStorageSync<string>(STORAGE_TOKEN);
    if (token) {
      header.Authorization = `Bearer ${token}`;
    }
  }

  const body = options.data as unknown as
    | string
    | WechatMiniprogram.IAnyObject
    | ArrayBuffer
    | undefined;

  return new Promise<T>((resolve, reject) => {
    wx.request({
      url,
      method,
      header,
      data: body,
      success: (res) => {
        const status = res.statusCode;
        const payload = res.data as unknown;
        if (status >= 200 && status < 300) {
          if (status === 204) {
            resolve(undefined as unknown as T);
            return;
          }
          resolve(payload as T);
          return;
        }
        if (status === 401) {
          handleUnauthorized();
        }
        const env = toEnvelope(payload);
        reject(
          new ApiError(
            status,
            env.code ?? `HTTP_${status}`,
            env.message ?? `请求失败（${status}）`,
            Array.isArray(env.details) ? env.details : [],
            env.traceId ?? '',
          ),
        );
      },
      fail: (err) => {
        reject(new ApiError(0, 'NETWORK_ERROR', err.errMsg || '网络请求失败', [], ''));
      },
    });
  });
}

/** 拼接公开媒体资源完整 URL（课件页图片 / 视频流），用于 <image>/<video> 的 src。 */
export function mediaUrl(path: string): string {
  return `${API_BASE}${API_PREFIX}${path}`;
}
