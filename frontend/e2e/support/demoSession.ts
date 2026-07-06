// 演示账号会话：通过真实后端 API 注册/登录，把会话注入浏览器 localStorage。
//
// 契约（凭证绝不写死在源码）：
//   - BASE_URL                       目标站点，默认 http://127.0.0.1:5173（vite dev，/api 代理到 :18080）
//   - PYT_E2E_USERNAME / PYT_E2E_PASSWORD   学生演示账号凭证
//   - PYT_E2E_REGISTER=1             允许走 POST /api/auth/register 注册临时演示账号
//                                    （提供了 PYT_E2E_USERNAME/PASSWORD 则按该凭证注册，
//                                    已存在时直接登录；否则生成一次性随机账号）
//   - PYT_E2E_TEACHER_USERNAME / PYT_E2E_TEACHER_PASSWORD   教师演示账号（12-teacher-classroom 用）
//   - PYT_E2E_TEACHER_INVITE_CODE    配合 PYT_E2E_REGISTER=1 注册临时教师号
//                                    （必须与后端 PYT_TEACHER_INVITE_CODE 一致）
//
// 注入方式与真实登录完全一致：写 session store 持久化所用的三个 localStorage key
// （pythontopia.token / pythontopia.expiresAt / pythontopia.user，见 src/stores/session.ts），
// 应用启动时 hydrateFromStorage 会读取并向后端 /api/auth/refresh 换发校验，绝无静态伪造面板。

import { randomBytes } from 'node:crypto';
import type { APIRequestContext, BrowserContext } from '@playwright/test';

export interface DemoUser {
  id: number;
  username: string;
  displayName: string;
  role: 'STUDENT' | 'TEACHER' | 'ADMIN';
}

export interface DemoSession {
  token: string;
  expiresAt: string;
  user: DemoUser;
}

interface RegisterPayload {
  username: string;
  password: string;
  displayName: string;
  role: 'STUDENT' | 'TEACHER';
  teacherInviteCode?: string;
}

export function resolveBaseUrl(): string {
  return (process.env.BASE_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
}

/** 随机账号后缀：时间戳 + 随机字节，满足后端 username 约束 ^[A-Za-z0-9_.-]+$。 */
function randomSuffix(): string {
  return `${Date.now().toString(36)}.${randomBytes(4).toString('hex')}`;
}

/** 一次性随机密码（24 字符，处于后端 8..72 限制内），只存在于进程内存，不落盘不打日志。 */
function randomPassword(): string {
  return randomBytes(18).toString('base64url');
}

async function loginViaApi(
  request: APIRequestContext,
  username: string,
  password: string,
): Promise<DemoSession> {
  const response = await request.post(`${resolveBaseUrl()}/api/auth/login`, {
    data: { username, password },
  });
  if (!response.ok()) {
    // 错误信息刻意不回显用户名/密码，避免凭证进入测试报告。
    throw new Error(
      `演示账号登录失败（HTTP ${response.status()}）：请确认本地栈已启动、PYT_E2E_* 凭证正确`,
    );
  }
  const body = (await response.json()) as Partial<DemoSession>;
  if (!body.token || !body.expiresAt || !body.user) {
    throw new Error('登录响应缺少 token / expiresAt / user 字段，后端契约可能已变化');
  }
  return body as DemoSession;
}

/** 注册演示账号；username 已存在（409）视为可继续（随后直接登录）。 */
async function registerViaApi(request: APIRequestContext, payload: RegisterPayload): Promise<void> {
  const response = await request.post(`${resolveBaseUrl()}/api/auth/register`, { data: payload });
  if (response.ok() || response.status() === 409) {
    return;
  }
  const detail = (await response.text()).slice(0, 300);
  throw new Error(`注册演示账号失败（HTTP ${response.status()}）：${detail}`);
}

/**
 * 获取学生演示会话：
 *   - PYT_E2E_REGISTER=1：先注册（凭证取 env，缺省生成一次性随机账号），再登录拿 JWT；
 *   - 否则要求 PYT_E2E_USERNAME / PYT_E2E_PASSWORD 已提供，直接登录。
 */
export async function acquireStudentSession(request: APIRequestContext): Promise<DemoSession> {
  const envUser = process.env.PYT_E2E_USERNAME;
  const envPass = process.env.PYT_E2E_PASSWORD;
  if (process.env.PYT_E2E_REGISTER === '1') {
    const username = envUser ?? `pyt.e2e.s.${randomSuffix()}`;
    const password = envPass ?? randomPassword();
    await registerViaApi(request, {
      username,
      password,
      displayName: 'E2E 演示学生',
      role: 'STUDENT',
    });
    return loginViaApi(request, username, password);
  }
  if (!envUser || !envPass) {
    throw new Error(
      '缺少演示账号：请设置 PYT_E2E_USERNAME / PYT_E2E_PASSWORD，'
        + '或设 PYT_E2E_REGISTER=1 由测试自动注册临时演示账号',
    );
  }
  return loginViaApi(request, envUser, envPass);
}

/**
 * 获取教师演示会话（12-teacher-classroom 专用）：
 *   - 优先 PYT_E2E_TEACHER_USERNAME / PYT_E2E_TEACHER_PASSWORD 直接登录；
 *   - 否则 PYT_E2E_REGISTER=1 且提供 PYT_E2E_TEACHER_INVITE_CODE 时注册临时教师号；
 *   - 两者皆无 → 抛错（该张截图必须失败，不允许用学生号凑数）。
 */
export async function acquireTeacherSession(request: APIRequestContext): Promise<DemoSession> {
  const envUser = process.env.PYT_E2E_TEACHER_USERNAME;
  const envPass = process.env.PYT_E2E_TEACHER_PASSWORD;
  if (envUser && envPass) {
    return loginViaApi(request, envUser, envPass);
  }
  const inviteCode = process.env.PYT_E2E_TEACHER_INVITE_CODE;
  if (process.env.PYT_E2E_REGISTER === '1' && inviteCode) {
    const username = `pyt.e2e.t.${randomSuffix()}`;
    const password = randomPassword();
    await registerViaApi(request, {
      username,
      password,
      displayName: 'E2E 演示教师',
      role: 'TEACHER',
      teacherInviteCode: inviteCode,
    });
    return loginViaApi(request, username, password);
  }
  throw new Error(
    '12-teacher-classroom 需要教师会话：请设置 PYT_E2E_TEACHER_USERNAME / PYT_E2E_TEACHER_PASSWORD；'
      + '或设 PYT_E2E_REGISTER=1 并提供 PYT_E2E_TEACHER_INVITE_CODE'
      + '（须与后端 PYT_TEACHER_INVITE_CODE 一致）以注册临时教师号',
  );
}

/** 把真实会话注入浏览器：在任何页面脚本执行前写入 session store 的 localStorage key。 */
export async function injectSession(context: BrowserContext, session: DemoSession): Promise<void> {
  await context.addInitScript(
    ({ token, expiresAt, user }) => {
      window.localStorage.setItem('pythontopia.token', token);
      window.localStorage.setItem('pythontopia.expiresAt', expiresAt);
      window.localStorage.setItem('pythontopia.user', JSON.stringify(user));
    },
    { token: session.token, expiresAt: session.expiresAt, user: session.user },
  );
}

/**
 * 预置「新手引导已看过」标记，抑制 /world 首次进入的帮助弹窗与镇长自动对话，
 * 保证 01-town-hub 拍到的是小镇本身而不是叠层引导（标记与玩家真实点掉弹窗后的状态一致）。
 */
export async function suppressFirstVisitOverlays(context: BrowserContext): Promise<void> {
  await context.addInitScript(() => {
    window.localStorage.setItem('pyt_first_world_visit', '1');
    window.localStorage.setItem('pyt_helped_v1', '1');
  });
}

interface ClassroomSummary {
  classroomId: number;
  name: string;
}

/** 教师号至少要有 1 个班级，列表才不空：没有则通过真实 API 创建演示班级。 */
export async function ensureDemoClassroom(
  request: APIRequestContext,
  session: DemoSession,
): Promise<void> {
  const base = resolveBaseUrl();
  const headers = { Authorization: `Bearer ${session.token}` };
  const listResponse = await request.get(`${base}/api/classrooms/mine`, { headers });
  if (!listResponse.ok()) {
    throw new Error(`读取教师班级列表失败（HTTP ${listResponse.status()}）`);
  }
  const classrooms = (await listResponse.json()) as ClassroomSummary[];
  if (classrooms.length > 0) {
    return;
  }
  const createResponse = await request.post(`${base}/api/classrooms`, {
    headers,
    data: { name: 'E2E 演示班级', description: '引导手册截图用演示班级（可随时删除）' },
  });
  if (!createResponse.ok()) {
    throw new Error(`创建演示班级失败（HTTP ${createResponse.status()}）`);
  }
}
