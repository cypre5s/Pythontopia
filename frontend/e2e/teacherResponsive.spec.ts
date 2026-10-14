import { expect, test } from '@playwright/test';

test('教师班级卡片在 320 CSS px 视口内不产生页面横向溢出', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 900 });
  await page.addInitScript(() => {
    localStorage.setItem('pythontopia.token', 'responsive-test-token');
    localStorage.setItem('pythontopia.expiresAt', new Date(Date.now() + 3_600_000).toISOString());
    localStorage.setItem('pythontopia.user', JSON.stringify({
      id: 42,
      username: 'responsive-teacher',
      displayName: '教师窄屏测试',
      role: 'TEACHER',
    }));
  });
  await page.route('**/api/auth/refresh', (route) => route.fulfill({
    json: {
      token: 'responsive-test-refreshed-token',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
      user: {
        id: 42,
        username: 'responsive-teacher',
        displayName: '教师窄屏测试',
        role: 'TEACHER',
      },
    },
  }));
  await page.route('**/api/classrooms/mine', (route) => route.fulfill({
    json: [{
      classroomId: 9,
      name: 'Python 初学者班级',
      description: '用于检查教师班级卡片在窄视口的布局',
      teacherId: 42,
      packId: 43,
      status: 'ACTIVE',
      joinCode: 'ABC123',
    }],
  }));

  await page.goto('/teacher', { waitUntil: 'networkidle' });
  await expect(page.getByTestId('classroom-list')).toBeVisible();
  await expect(page.locator('.classroom-card')).toContainText('Python 初学者班级');

  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    document: document.documentElement.scrollWidth,
  }));
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport);
});
