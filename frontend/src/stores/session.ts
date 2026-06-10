import { defineStore } from 'pinia';
import { login as loginApi, refresh as refreshApi, register as registerApi } from '@api/auth';
import { setTokenProvider, setUnauthorizedHandler } from '@api/http';
import type { LoginRequest } from '@api/dto/LoginRequest';
import type { LoginResponse } from '@api/dto/LoginResponse';
import type { RegisterRequest } from '@api/dto/RegisterRequest';
import type { UserDto, UserRole } from '@api/dto/UserDto';

const TOKEN_KEY = 'pythontopia.token';
const EXPIRES_KEY = 'pythontopia.expiresAt';
const USER_KEY = 'pythontopia.user';

/** localStorage 里的 user 快照可能被篡改/损坏，解析失败一律按未登录用户处理。 */
function readStoredUser(): UserDto | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (
      typeof parsed === 'object'
      && parsed !== null
      && 'username' in parsed
      && 'role' in parsed
    ) {
      return parsed as UserDto;
    }
  } catch {
    // 损坏快照按 null 处理
  }
  return null;
}

export const useSessionStore = defineStore('session', {
  state: () => ({
    token: null as string | null,
    user: null as UserDto | null,
    expiresAt: null as string | null,
  }),
  getters: {
    isAuthenticated: (state): boolean => {
      if (!state.token || !state.expiresAt) {
        return false;
      }
      return Date.parse(state.expiresAt) > Date.now();
    },
    displayName: (state): string => state.user?.displayName ?? state.user?.username ?? '学习者',
    /** 教师审核页 / 班级面板按角色显示不同视图。 */
    role: (state): UserRole | null => state.user?.role ?? null,
  },
  actions: {
    /**
     * 页面刷新后从 localStorage 恢复会话：
     *   1. 同步回灌 token / expiresAt / user（user 含 role，教师端路由守卫立即可用）；
     *   2. token 仍有效时后台调 /api/auth/refresh 换发新 token，并以服务端返回的
     *      user 覆盖本地快照（防篡改 + 顺带续期，token 到期不再直接掉线）。
     */
    hydrateFromStorage(): void {
      this.token = localStorage.getItem(TOKEN_KEY);
      this.expiresAt = localStorage.getItem(EXPIRES_KEY);
      this.user = readStoredUser();
      this.configureHttp();
      if (this.refreshIfNeeded()) {
        void this.refreshSession();
      }
    },
    async login(req: LoginRequest): Promise<void> {
      this.applySession(await loginApi(req));
      this.configureHttp();
    },
    async register(req: RegisterRequest): Promise<UserDto> {
      return registerApi(req);
    },
    /** 把登录/刷新响应统一写入 state 与 localStorage。 */
    applySession(response: LoginResponse): void {
      this.token = response.token;
      this.user = response.user;
      this.expiresAt = response.expiresAt;
      localStorage.setItem(TOKEN_KEY, response.token);
      localStorage.setItem(EXPIRES_KEY, response.expiresAt);
      localStorage.setItem(USER_KEY, JSON.stringify(response.user));
    },
    /**
     * 用现有 token 向后端换发新 token 并回灌 user/role。
     * 401（token 失效）由 http 拦截器统一 logout；网络故障保留本地会话不打断使用。
     */
    async refreshSession(): Promise<void> {
      if (!this.token) return;
      try {
        this.applySession(await refreshApi(this.token));
      } catch {
        // 失效场景已由 unauthorized handler 处理；离线场景静默保留本地态
      }
    },
    logout(): void {
      this.token = null;
      this.user = null;
      this.expiresAt = null;
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(EXPIRES_KEY);
      localStorage.removeItem(USER_KEY);
    },
    refreshIfNeeded(): boolean {
      if (!this.expiresAt || Date.parse(this.expiresAt) <= Date.now()) {
        this.logout();
        return false;
      }
      return true;
    },
    configureHttp(onUnauthorized?: () => void): void {
      setTokenProvider(() => this.token);
      setUnauthorizedHandler(() => {
        this.logout();
        onUnauthorized?.();
      });
    },
  },
});
