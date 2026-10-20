import { expect, test } from '@playwright/test';

test('loads Phaser only after opening the performance HUD', async ({ page }) => {
  const phaserRequests: string[] = [];
  page.on('request', (request) => {
    if (/\/phaser-[^/]+\.js$/.test(new URL(request.url()).pathname)) {
      phaserRequests.push(request.url());
    }
  });

  await page.goto('/login', { waitUntil: 'load' });
  await expect(page.locator('.auth-page')).toBeVisible();
  await page.waitForTimeout(500);
  expect(phaserRequests).toHaveLength(0);

  await page.keyboard.press('F3');
  await expect(page.getByTestId('perf-hud')).toBeVisible();
  await expect.poll(() => phaserRequests.length).toBeGreaterThan(0);
});
