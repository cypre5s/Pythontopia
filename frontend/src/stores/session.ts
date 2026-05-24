import { defineStore } from 'pinia';
import { login as loginApi, register as registerApi } from '@api/auth';
import { setTokenProvider, setUnauthorizedHandler } from '@api/http';
import type { LoginRequest } from '@api/dto/LoginRequest';
import type { RegisterRequest } from '@api/dto/RegisterRequest';
import type { UserDto, UserRole } from '@api/dto/UserDto';

const TOKEN_KEY = 'pythontopia.token';
const EXPIRES_KEY = 'pythontopia.expiresAt';

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
    /** R-13 §2.3-6：教师审核页 / 班级面板按角色显示不同视图。 */
    role: (state): UserRole | null => state.user?.role ?? null,
  },
  actions: {
    hydrateFromStorage(): void {
      this.token = localStorage.getItem(TOKEN_KEY);
      this.expiresAt = localStorage.getItem(EXPIRES_KEY);
      this.configureHttp();
      this.refreshIfNeeded();
    },
    async login(req: LoginRequest): Promise<void> {
      const response = await loginApi(req);
      this.token = response.token;
      this.user = response.user;
      this.expiresAt = response.expiresAt;
      localStorage.setItem(TOKEN_KEY, response.token);
      localStorage.setItem(EXPIRES_KEY, response.expiresAt);
      this.configureHttp();
    },
    async register(req: RegisterRequest): Promise<UserDto> {
      return registerApi(req);
    },
    logout(): void {
      this.token = null;
      this.user = null;
      this.expiresAt = null;
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(EXPIRES_KEY);
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
