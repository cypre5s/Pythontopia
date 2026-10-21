import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import {
  acquireStudentSession,
  injectSession,
  suppressFirstVisitOverlays,
  type DemoSession,
} from './support/demoSession';

let sessionPromise: Promise<DemoSession> | null = null;

function studentSession(request: APIRequestContext): Promise<DemoSession> {
  sessionPromise ??= acquireStudentSession(request);
  return sessionPromise;
}

async function waitForWorldScene(page: Page): Promise<void> {
  await page.goto('/world');
  const canvas = page.locator('canvas').first();
  await expect(canvas).toBeVisible();
  await expect.poll(() => canvas.evaluate((element) => element.width * element.height)).toBeGreaterThan(0);
  await expect(page.getByRole('heading', { name: 'Pythontopia 加载中…' }))
    .toBeHidden({ timeout: 90_000 });
}

test('WASD and ground clicks move the player in the world', async ({ page, request }) => {
  const session = await studentSession(request);
  const auth = { Authorization: `Bearer ${session.token}` };
  const mapResponse = await request.get('/assets/maps/town_hub.json');
  expect(mapResponse.ok()).toBeTruthy();
  const map = await mapResponse.json() as {
    tilewidth: number;
    layers: Array<{ type: string; objects?: Array<{ name: string; type: string; x: number; y: number }> }>;
  };
  const spawnObject = map.layers.find((layer) => layer.type === 'objectgroup')?.objects
    ?.find((object) => object.type === 'spawn');
  expect(spawnObject).toBeDefined();
  const spawn = {
    tileX: Math.floor(spawnObject!.x / map.tilewidth),
    tileY: Math.floor(spawnObject!.y / map.tilewidth),
  };
  const worldResponse = await request.get('/api/world/session', { headers: auth });
  expect(worldResponse.ok()).toBeTruthy();
  const initialPlayer = (await worldResponse.json()).player as { tileX: number; tileY: number };
  expect({ tileX: initialPlayer.tileX, tileY: initialPlayer.tileY }).toEqual(spawn);
  const legacyPositionResponse = await request.patch('/api/world/player', {
    headers: auth,
    data: { tileX: 12, tileY: 10, direction: 'DOWN' },
  });
  expect(legacyPositionResponse.ok()).toBeTruthy();
  const updates: Array<{ tileX: number; tileY: number }> = [];
  const pageErrors: string[] = [];
  const consoleErrors: string[] = [];
  const failedRequests: string[] = [];
  const apiErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  page.on('requestfailed', (requestEvent) => failedRequests.push(new URL(requestEvent.url()).pathname));
  page.on('response', (response) => {
    if (new URL(response.url()).pathname.startsWith('/api/') && response.status() >= 400) {
      apiErrors.push(`${response.status()} ${new URL(response.url()).pathname}`);
    }
  });
  page.on('request', (requestEvent) => {
    if (requestEvent.method() !== 'PATCH' || !requestEvent.url().includes('/api/world/player')) return;
    const body = requestEvent.postDataJSON() as { tileX: number; tileY: number };
    updates.push(body);
  });

  await injectSession(page.context(), session);
  await suppressFirstVisitOverlays(page.context());
  await waitForWorldScene(page);
  const onboardingSkip = page.getByRole('button', { name: '跳过' });
  if (await onboardingSkip.isVisible().catch(() => false)) {
    await onboardingSkip.click();
  }
  await expect.poll(async () => {
    const currentResponse = await request.get('/api/world/session', { headers: auth });
    const { player } = await currentResponse.json();
    return { tileX: player.tileX, tileY: player.tileY };
  }).toEqual(spawn);
  updates.length = 0;
  const start = spawn;

  for (const key of ['w', 'a', 's', 'd']) {
    await page.keyboard.down(key);
    await page.waitForTimeout(900);
    await page.keyboard.up(key);
    if (updates.some(({ tileX, tileY }) => tileX !== start.tileX || tileY !== start.tileY)) break;
  }
  console.log('world keyboard movement', JSON.stringify({ start, updates }));
  await expect.poll(() => updates.some(({ tileX, tileY }) => tileX !== start.tileX || tileY !== start.tileY))
    .toBe(true);

  const current = updates.at(-1)!;
  const canvas = page.locator('canvas').first();
  const canvasBox = await canvas.boundingBox();
  expect(canvasBox).not.toBeNull();
  const point = { x: canvasBox!.width / 2, y: canvasBox!.height / 2 + 100 };
  const clickUpdateStart = updates.length;
  await canvas.click({ position: point, force: true });
  await expect.poll(() => updates.slice(clickUpdateStart)
    .some(({ tileX, tileY }) => tileX !== current.tileX || tileY !== current.tileY))
    .toBe(true);
  console.log('world browser errors', JSON.stringify({ consoleErrors, pageErrors, failedRequests, apiErrors }));
  expect(consoleErrors).toEqual([]);
  expect(pageErrors).toEqual([]);
  expect(failedRequests).toEqual([]);
  expect(apiErrors).toEqual([]);
});
