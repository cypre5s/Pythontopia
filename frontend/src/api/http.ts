import axios, { AxiosError, type AxiosInstance } from 'axios';
import type { ApiError } from './dto/ApiError';

// W4-LOGIN-UX：把后端返回的英文 code 映射为中文友好提示，
// 便于在登录/注册等用户直面流程中直接展示给学习者。
const ERROR_CODE_ZH: Record<string, string> = {
  BAD_CREDENTIALS: '用户名或密码错误',
  INVALID_CREDENTIALS: '用户名或密码错误',
  UNAUTHORIZED: '请重新登录',
  FORBIDDEN: '没有权限执行该操作',
  NOT_FOUND: '请求的资源不存在',
  VALIDATION_ERROR: '输入内容不符合要求',
  CONFLICT: '操作与当前数据状态冲突',
  RATE_LIMITED: '请求过于频繁，请稍后再试',
  NETWORK_ERROR: '无法连接服务器，请稍后重试',
  TIMEOUT: '请求超时，请检查网络后重试',
  INTERNAL_ERROR: '服务器开小差了，请稍后再试',
};

function localizeError(code: string, fallback: string): string {
  return ERROR_CODE_ZH[code] ?? fallback;
}

export class ApiClientError extends Error {
  readonly code: string;
  readonly details: string[];
  readonly status: number | undefined;

  constructor(error: ApiError, status?: number) {
    super(localizeError(error.code, error.message));
    this.name = 'ApiClientError';
    this.code = error.code;
    this.details = error.details;
    this.status = status;
  }
}

let tokenProvider: (() => string | null) | null = null;
let unauthorizedHandler: (() => void) | null = null;
let networkErrorHandler: ((code: 'NETWORK_ERROR' | 'TIMEOUT') => void) | null = null;

// W4-FINAL-POLISH：避免短时间内大量 NETWORK_ERROR（dev server 重启、wifi 抖动）
// 反复刷屏 toast。一次提示后冷却 5 秒，期间相同错误静默，到点再放行。
let lastNetworkToastAt = 0;
const NETWORK_TOAST_COOLDOWN_MS = 5_000;

export function setTokenProvider(provider: () => string | null): void {
  tokenProvider = provider;
}

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
}

export function setNetworkErrorHandler(
  handler: ((code: 'NETWORK_ERROR' | 'TIMEOUT') => void) | null,
): void {
  networkErrorHandler = handler;
}

// 仅供测试使用：清空冷却时间，便于反复触发 toast 路径。
export function __resetNetworkErrorCooldownForTest(): void {
  lastNetworkToastAt = 0;
}

export const http: AxiosInstance = axios.create({
  baseURL: '/',
  timeout: 10_000,
});

http.interceptors.request.use((config) => {
  const token = tokenProvider?.();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
      unauthorizedHandler?.();
    }
    const data = error.response?.data;
    if (data) {
      throw new ApiClientError(data, error.response?.status);
    }
    const isTimeout = error.code === 'ECONNABORTED';
    const code: 'NETWORK_ERROR' | 'TIMEOUT' = isTimeout ? 'TIMEOUT' : 'NETWORK_ERROR';
    const now = Date.now();
    if (networkErrorHandler && now - lastNetworkToastAt > NETWORK_TOAST_COOLDOWN_MS) {
      lastNetworkToastAt = now;
      try {
        networkErrorHandler(code);
      } catch {
        // 防止 toast/store 在极端情况下抛错把请求拦截器搞死
      }
    }
    throw new ApiClientError(
      {
        code,
        message: error.message || 'Network request failed',
        details: [],
        traceId: '',
        timestamp: new Date().toISOString(),
      },
      error.response?.status,
    );
  },
);
