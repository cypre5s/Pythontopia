import { randomBytes } from 'node:crypto';
import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { resolveBaseUrl } from './support/demoSession';

interface TestAccount {
  username: string;
  password: string;
  displayName: string;
}

interface LoginResponse {
  token: string;
}

function createAccount(label: string): TestAccount {
  const suffix = `${Date.now().toString(36)}.${randomBytes(4).toString('hex')}`;
  return {
    username: `pyt.e2e.isolation.${label}.${suffix}`,
    password: `E2e!${randomBytes(12).toString('base64url')}9Aa`,
    displayName: `隔离回归-${label}`,
  };
}

async function registerAndLogin(
  request: APIRequestContext,
  account: TestAccount,
): Promise<LoginResponse> {
  const baseUrl = resolveBaseUrl();
  const registered = await request.post(`${baseUrl}/api/auth/register`, {
    data: {
      username: account.username,
      password: account.password,
      displayName: account.displayName,
      role: 'STUDENT',
    },
  });
  expect(registered.ok(), `注册隔离测试账号失败：HTTP ${registered.status()}`).toBe(true);

  const loggedIn = await request.post(`${baseUrl}/api/auth/login`, {
    data: { username: account.username, password: account.password },
  });
  expect(loggedIn.ok(), `登录隔离测试账号失败：HTTP ${loggedIn.status()}`).toBe(true);
  const body = (await loggedIn.json()) as Partial<LoginResponse>;
  expect(body.token, '登录响应缺少 JWT').toBeTruthy();
  return body as LoginResponse;
}

async function loginThroughUi(page: Page, account: TestAccount): Promise<void> {
  await page.locator('input[autocomplete="username"]').fill(account.username);
  await page.locator('input[autocomplete="current-password"]').fill(account.password);
  await page.getByRole('button', { name: '进入小镇' }).click();
  await expect(page).toHaveURL(/\/workshop(?:\?.*)?$/, { timeout: 30_000 });
  await expect(page.getByTestId('workshop-page')).toBeVisible();
}

test('同一标签页切换账号后不会复用前一账号的工坊历史', async ({ page, request }) => {
  const ownerA = createAccount('a');
  const ownerB = createAccount('b');
  const [sessionA] = await Promise.all([
    registerAndLogin(request, ownerA),
    registerAndLogin(request, ownerB),
  ]);
  const marker = `ACCOUNT-A-ONLY-${randomBytes(8).toString('hex')}`;

  const seeded = await request.post(`${resolveBaseUrl()}/api/workshop/chat-history/turns`, {
    headers: { Authorization: `Bearer ${sessionA.token}` },
    data: {
      conversationId: `isolation-${randomBytes(8).toString('hex')}`,
      userText: marker,
      intent: 'ANSWER',
      responseKind: 'answer',
      toolLabel: '账号隔离回归',
      responseText: '仅账号 A 可见的真实历史记录。',
      responsePayload: {},
    },
  });
  expect(seeded.ok(), `预置账号 A 工坊历史失败：HTTP ${seeded.status()}`).toBe(true);

  await page.goto('/login?redirect=%2Fworkshop');
  const ownerAHistory = page.waitForResponse((response) =>
    response.request().method() === 'GET'
      && response.url().includes('/api/workshop/chat-history/turns'),
  );
  await loginThroughUi(page, ownerA);
  expect((await ownerAHistory).ok(), '账号 A 历史接口应成功').toBe(true);
  await expect(page.getByTestId('workshop-history-panel')).toContainText(marker);

  await page.getByRole('button', { name: '退出登录' }).click();
  await expect(page).toHaveURL(/\/login(?:\?.*)?$/);
  await page.goto('/login?redirect=%2Fworkshop');

  const ownerBHistory = page.waitForResponse((response) =>
    response.request().method() === 'GET'
      && response.url().includes('/api/workshop/chat-history/turns'),
  );
  await loginThroughUi(page, ownerB);
  expect((await ownerBHistory).ok(), '账号 B 必须重新读取自己的历史').toBe(true);
  const historyPanel = page.getByTestId('workshop-history-panel');
  await expect(historyPanel).toContainText('还没有问答记录');
  await expect(historyPanel).not.toContainText(marker);
});
