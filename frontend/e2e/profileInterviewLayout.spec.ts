import { expect, test } from '@playwright/test';

test('keeps the profile interview submit button clickable when no profile exists', async ({ page }) => {
  await page.setViewportSize({ width: 1365, height: 900 });
  await page.addInitScript(() => {
    const user = {
      id: 990017,
      username: 'e2e-profile-interview-layout',
      displayName: 'E2E 学生',
      role: 'STUDENT',
    };
    localStorage.setItem('pythontopia.token', 'profile-layout-e2e-token');
    localStorage.setItem('pythontopia.expiresAt', '2035-01-01T00:00:00.000Z');
    localStorage.setItem('pythontopia.user', JSON.stringify(user));
  });

  const turns: unknown[] = [];
  await page.route((url) => new URL(url).pathname.startsWith('/api/'), async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const respond = (data: unknown, status = 200) => route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(data),
    });

    if (path === '/api/auth/refresh' && request.method() === 'POST') {
      return respond({
        token: 'profile-layout-e2e-token',
        expiresAt: '2035-01-01T00:00:00.000Z',
        user: {
          id: 990017,
          username: 'e2e-profile-interview-layout',
          displayName: 'E2E 学生',
          role: 'STUDENT',
        },
      });
    }
    if (path === '/api/learner/profile' && request.method() === 'GET') {
      return route.fulfill({ status: 204, body: '' });
    }
    if (path === '/api/learner/profile/dimensions' && request.method() === 'GET') {
      return respond([]);
    }
    if (path === '/api/learner/profile/interview' && request.method() === 'POST') {
      return respond({ interviewSessionId: 'layout-session', status: 'OPEN', history: [], draft: null });
    }
    if (path === '/api/learner/profile/interview/layout-session/turn' && request.method() === 'POST') {
      turns.push(request.postDataJSON());
      return respond({ nextQuestion: '你更喜欢什么学习形式？', dimensionsFilled: [], done: false });
    }
    return respond({ code: 'UNEXPECTED_TEST_REQUEST', message: `${request.method()} ${path}` }, 500);
  });

  await page.goto('/profile');
  await expect(page.getByTestId('profile-page')).toBeVisible();
  await page.getByTestId('profile-interview-toggle').click();
  await expect(page.locator('.profile-form textarea')).toBeVisible();
  const submit = page.locator('.profile-form button[type="submit"]');
  await page.locator('.profile-form textarea').fill('我是计算机专业学生，正在准备期末考试。');
  await submit.click({ timeout: 5_000 });

  await expect.poll(() => turns.length).toBe(1);
  await expect(page.locator('.profile-form textarea')).toHaveValue('');
});
