import {
  expect,
  test,
  type APIRequestContext,
  type Page,
} from '@playwright/test';

const BASE_URL = (process.env.BASE_URL ?? 'http://127.0.0.1:5173').replace(
  /\/+$/,
  '',
);

function url(path: string): string {
  return new URL(path, `${BASE_URL}/`).toString();
}

interface Account {
  username: string;
  password: string;
  displayName: string;
}

interface LoginResponse {
  token: string;
}

async function registerAndLogin(
  request: APIRequestContext,
  suffix: string,
): Promise<{ account: Account; session: LoginResponse }> {
  const account: Account = {
    username: `history-${suffix}-${Date.now().toString(36)}`.slice(0, 40),
    password: `Hist!${suffix}9aZ-${Date.now().toString(36)}`,
    displayName: `历史隔离${suffix}`,
  };
  const registration = await request.post(url('/api/auth/register'), {
    data: { ...account, role: 'STUDENT' },
  });
  expect(registration.ok()).toBe(true);

  const login = await request.post(url('/api/auth/login'), {
    data: { username: account.username, password: account.password },
  });
  expect(login.ok()).toBe(true);
  return {
    account,
    session: (await login.json()) as LoginResponse,
  };
}

async function loginInBrowser(page: Page, account: Account): Promise<void> {
  await page.locator('input[autocomplete="username"]').fill(account.username);
  await page
    .locator('input[autocomplete="current-password"]')
    .fill(account.password);
  await page.locator('form.auth-form button[type="submit"]').click();
}

test('同一浏览器从 A 切换到 B 后不会显示 A 的工坊历史', async ({
  page,
  request,
}) => {
  const first = await registerAndLogin(request, 'a');
  const second = await registerAndLogin(request, 'b');
  const privateMarker = `A-ONLY-HISTORY-${Date.now().toString(36)}`;

  const recorded = await request.post(url('/api/workshop/chat-history/turns'), {
    headers: { Authorization: `Bearer ${first.session.token}` },
    data: {
      conversationId: `conv-${privateMarker}`,
      userText: privateMarker,
      intent: 'ASK',
      responseKind: 'answer',
      toolLabel: '问答',
      responseText: '仅属于 A 的历史回答',
      responsePayload: {},
    },
  });
  expect(recorded.ok()).toBe(true);

  await page.goto(url('/login?redirect=/workshop'), {
    waitUntil: 'domcontentloaded',
  });
  await loginInBrowser(page, first.account);
  await expect(page).toHaveURL(/\/workshop(?:\?|$)/, { timeout: 90_000 });
  await expect(page.getByTestId('workshop-history-panel')).toBeVisible();
  await expect(page.getByText(privateMarker, { exact: true })).toBeVisible();

  await page.getByRole('button', { name: '退出登录' }).click();
  await expect(page).toHaveURL(/\/login(?:\?|$)/);

  await page.evaluate(() => {
    window.history.pushState({}, '', '/login?redirect=/workshop');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page).toHaveURL(/\/login\?redirect=\/workshop$/);
  await loginInBrowser(page, second.account);
  await expect(page).toHaveURL(/\/workshop(?:\?|$)/, { timeout: 90_000 });
  await expect(page.getByTestId('workshop-history-panel')).toBeVisible();

  await expect(page.getByText(privateMarker, { exact: true })).toHaveCount(0);
});
