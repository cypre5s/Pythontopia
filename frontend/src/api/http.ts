import axios, { AxiosError, type AxiosInstance } from 'axios';
import type { ApiError } from './dto/ApiError';

export class ApiClientError extends Error {
  readonly code: string;
  readonly details: string[];
  readonly status: number | undefined;

  constructor(error: ApiError, status?: number) {
    super(error.message);
    this.name = 'ApiClientError';
    this.code = error.code;
    this.details = error.details;
    this.status = status;
  }
}

let tokenProvider: (() => string | null) | null = null;
let unauthorizedHandler: (() => void) | null = null;

export function setTokenProvider(provider: () => string | null): void {
  tokenProvider = provider;
}

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
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
    throw new ApiClientError(
      {
        code: 'NETWORK_ERROR',
        message: error.message || 'Network request failed',
        details: [],
        traceId: '',
        timestamp: new Date().toISOString(),
      },
      error.response?.status,
    );
  },
);
