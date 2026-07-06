import { login as apiLogin, register as apiRegister } from '../api/auth';
import { AuthUser, LoginRequest, RegisterRequest } from '../api/dto/auth';
import { HOME_ROUTE, LOGIN_ROUTE, STORAGE_TOKEN, STORAGE_USER } from '../config';

// 内存态会话（启动时由 hydrateSession 从本地存储恢复）。
let token: string | null = null;
let user: AuthUser | null = null;

export function hydrateSession(): void {
  try {
    const storedToken = wx.getStorageSync<string>(STORAGE_TOKEN);
    const storedUser = wx.getStorageSync<AuthUser | ''>(STORAGE_USER);
    token = storedToken ? storedToken : null;
    user = storedUser ? (storedUser as AuthUser) : null;
  } catch {
    token = null;
    user = null;
  }
}

export function getToken(): string | null {
  return token;
}

export function getUser(): AuthUser | null {
  return user;
}

export function isLoggedIn(): boolean {
  return token !== null && token.length > 0;
}

function persist(nextToken: string, nextUser: AuthUser): void {
  token = nextToken;
  user = nextUser;
  wx.setStorageSync(STORAGE_TOKEN, nextToken);
  wx.setStorageSync(STORAGE_USER, nextUser);
}

export async function login(credentials: LoginRequest): Promise<AuthUser> {
  const res = await apiLogin(credentials);
  persist(res.token, res.user);
  return res.user;
}

/** 注册接口只返回用户信息（不含 token），随后用同一组凭据登录换取 token。 */
export async function register(req: RegisterRequest): Promise<AuthUser> {
  await apiRegister(req);
  return login({ username: req.username, password: req.password });
}

export function logout(): void {
  token = null;
  user = null;
  try {
    wx.removeStorageSync(STORAGE_TOKEN);
    wx.removeStorageSync(STORAGE_USER);
  } catch {
    // 忽略存储异常。
  }
  wx.reLaunch({ url: LOGIN_ROUTE });
}

/** 登录成功后进入学习主页（tab 页用 switchTab）。 */
export function redirectHome(): void {
  wx.switchTab({ url: HOME_ROUTE });
}
