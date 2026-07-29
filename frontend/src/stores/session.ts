import { defineStore, getActivePinia } from 'pinia';
import { login as loginApi, refresh as refreshApi, register as registerApi } from '@api/auth';
import { setTokenProvider, setUnauthorizedHandler } from '@api/http';
import { clearWorkshopChatHistoryCache } from '@api/workshopHistory';
import type { LoginRequest } from '@api/dto/LoginRequest';
import type { LoginResponse } from '@api/dto/LoginResponse';
import type { RegisterRequest } from '@api/dto/RegisterRequest';
import type { UserDto, UserRole } from '@api/dto/UserDto';

const TOKEN_KEY = 'pythontopia.token';
const EXPIRES_KEY = 'pythontopia.expiresAt';
const USER_KEY = 'pythontopia.user';

/** 未登录（或用户快照损坏）时的持久化命名空间。 */
const ANONYMOUS_SCOPE = 'anon';
/** 用户级 localStorage 键的分隔标记：`<base>@u:<scope>`，登出时按该前缀批量清理。 */
const USER_SCOPE_MARK = '@u:';

/** store id 白名单：这些 store 不参与「换账号全局重置」。 */
const RESET_EXCLUDED_STORE_IDS: readonly string[] = [
  'session', // 自己：重置会把刚写入的 token 冲掉
  'gameSettings', // 设备级偏好（音量/画质），独立持久化，不属于某个学生的学情数据
];

/** setup 语法的 store（`defineStore(id, () => {...})`，pinia 不给它们实现 `$reset`）。 */
const SETUP_STORE_IDS: readonly string[] = ['learning', 'rogueRun', 'companion'];

interface ResettableStore {
  $reset?: () => void;
  $dispose?: () => void;
  reset?: () => void;
}

interface PiniaRegistry {
  _s?: Map<string, ResettableStore>;
  state?: { value: Record<string, unknown> };
}

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

/**
 * 当前登录用户在本机持久化里的命名空间（用户名优先，缺失回退 `anon`）。
 *
 * 直接读 localStorage 而非 store，方便非 pinia 上下文（如 useCodeDraft 这类纯模块）
 * 与单测复用；未登录 / 快照损坏一律落到匿名空间。
 */
export function currentUserScope(): string {
  if (typeof localStorage === 'undefined') return ANONYMOUS_SCOPE;
  const user = readStoredUser();
  const raw = user?.username ?? '';
  return raw.trim().length > 0 ? raw.trim() : ANONYMOUS_SCOPE;
}

/**
 * 把一个本地持久化键绑定到当前登录用户：`<base>@u:<username>`。
 *
 * 公用机房同一浏览器换账号时，不加用户标识的键会串档（B 读到 A 的探针记录 / 代码草稿）。
 * 所有「属于某个学生」的 localStorage 键都必须经此函数生成。
 */
export function userScopedKey(base: string): string {
  return `${base}${USER_SCOPE_MARK}${currentUserScope()}`;
}

/** 删除某个用户命名空间下的全部本地键（登出时清当前用户，避免公用机器上留痕）。 */
function clearUserScopedStorage(scope: string): void {
  if (typeof localStorage === 'undefined') return;
  const suffix = `${USER_SCOPE_MARK}${scope}`;
  try {
    const doomed: string[] = [];
    for (let i = 0; i < localStorage.length; i += 1) {
      const key = localStorage.key(i);
      if (key && key.endsWith(suffix)) {
        doomed.push(key);
      }
    }
    for (const key of doomed) {
      localStorage.removeItem(key);
    }
  } catch {
    // 隐私模式 / 配额异常：忽略，内存状态已由 store 重置负责
  }
}

/**
 * 换账号时把除白名单外的所有 pinia store 复位。
 *
 * 登出只清 token 而不复位 store 时，同一标签页登录另一个账号会读到上一个学生的数据
 * （mainline overview 有 `if (!force && this.overview) return` 缓存，HUD 星星条 /「下一步」/
 * 章节进度全是上一个人的；tutor/teacher/feynman/dialog/rogueRun 同样残留）。
 *
 * 复位策略按 store 形态分两档：
 *   1. option store → `$reset()`（重跑 state 工厂；保持 store 实例标识，已挂载组件不受影响）；
 *   2. setup store → pinia 不为其实现 `$reset`（dev 抛错、**prod 是静默 noop**），
 *      只能 `$dispose()` 注销 + 删掉状态袋，下次 `useXxxStore()` 重新构造。
 *
 * 因为 prod 下 setup store 的 `$reset` 不报错，无法靠 try/catch 分流，这里维护显式清单；
 * 新增 setup store（`defineStore(id, () => {...})`）时**必须**同步登记，否则换账号会残留。
 */
export function resetStoresForAccountSwitch(): void {
  const pinia = getActivePinia() as unknown as PiniaRegistry | undefined;
  const registry = pinia?._s;
  if (!registry) return;
  for (const [id, store] of Array.from(registry.entries())) {
    if (RESET_EXCLUDED_STORE_IDS.includes(id)) continue;
    try {
      if (SETUP_STORE_IDS.includes(id)) {
        disposeStore(pinia, id, store);
      } else {
        store.$reset?.();
      }
    } catch {
      // 单个 store 复位失败不阻断其余 store（也兜住「漏登记的 setup store 在 dev 抛错」）
      try {
        disposeStore(pinia, id, store);
      } catch {
        /* 兜底失败同样忽略 */
      }
    }
  }
}

/** 注销 store 并清掉其状态袋，使下一次 `useXxxStore()` 重新构造一个干净实例。 */
function disposeStore(pinia: PiniaRegistry | undefined, id: string, store: ResettableStore): void {
  store.$dispose?.();
  if (pinia?.state?.value) {
    delete pinia.state.value[id];
  }
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
    /**
     * 登录：写入会话后**立即全局复位其余 store**。
     *
     * 同一标签页先后登录两个账号时，第一个账号残留的 store 数据（mainline overview 缓存、
     * 画像、任务…）会被第二个账号读到；且 probe 这类按用户命名空间读 localStorage 的 store
     * 也需要在 user 落地后重建，才能读到新账号自己的持久化数据。
     */
    async login(req: LoginRequest): Promise<void> {
      const response = await loginApi(req);
      clearWorkshopChatHistoryCache();
      this.applySession(response);
      this.configureHttp();
      resetStoresForAccountSwitch();
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
    /**
     * 登出：清会话 + 清当前用户的本地命名空间 + 全局复位其余 store。
     *
     * 只清 token 是不够的——各 store 的内存缓存与用户级 localStorage（探针记录、代码草稿）
     * 都会跨账号残留，公用机房换账号会直接串档。
     */
    logout(): void {
      const scope = currentUserScope();
      clearWorkshopChatHistoryCache();
      this.token = null;
      this.user = null;
      this.expiresAt = null;
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(EXPIRES_KEY);
      localStorage.removeItem(USER_KEY);
      clearUserScopedStorage(scope);
      resetStoresForAccountSwitch();
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
