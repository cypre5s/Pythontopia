import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

test.use({ actionTimeout: 20_000 });

// These are self-authored reader/player fixtures, never represented as course media.
function twoPagePdf(): Buffer {
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 7 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  for (let page = 1; page <= 2; page++) {
    const content = `BT /F1 18 Tf 36 740 Td (Self-authored test media - not course content) Tj 0 -36 Td (Page ${page} of 2) Tj ET`;
    objects.push(`<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}\nendstream`);
  }
  let result = '%PDF-1.4\n';
  const offsets = [0];
  for (const [index, object] of objects.entries()) {
    offsets.push(Buffer.byteLength(result));
    result += `${index + 1} 0 obj\n${object}\nendobj\n`;
  }
  const xref = Buffer.byteLength(result);
  result += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  result += offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('');
  result += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(result);
}

async function recordTestVideo(page: Page): Promise<Buffer> {
  const encoded = await page.evaluate(async () => {
    const canvas = document.createElement('canvas');
    canvas.width = 320;
    canvas.height = 180;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('Canvas unavailable');
    const stream = canvas.captureStream(12);
    const recorder = new MediaRecorder(stream, { mimeType: 'video/webm' });
    const chunks: Blob[] = [];
    recorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data); };
    const stopped = new Promise<void>(resolve => { recorder.onstop = () => resolve(); });
    let frame = 0;
    const draw = () => {
      context.fillStyle = frame % 2 ? '#2d6b59' : '#2a5b7a';
      context.fillRect(0, 0, 320, 180);
      context.fillStyle = '#ffffff';
      context.font = '18px sans-serif';
      context.fillText('Self-authored TEST MEDIA', 20, 70);
      context.fillText('Not course content', 20, 105);
      context.fillText(`Frame ${frame++}`, 20, 140);
    };
    draw();
    recorder.start();
    const timer = setInterval(draw, 80);
    await new Promise(resolve => setTimeout(resolve, 1200));
    recorder.stop();
    await stopped;
    clearInterval(timer);
    stream.getTracks().forEach(track => track.stop());
    const data = new Uint8Array(await new Blob(chunks, { type: 'video/webm' }).arrayBuffer());
    let binary = '';
    for (const byte of data) binary += String.fromCharCode(byte);
    return btoa(binary);
  });
  return Buffer.from(encoded, 'base64');
}

async function closeWorkspace(page: Page) {
  await page.getByRole('button', { name: '关闭当前工作区' }).click();
}

async function openLibrary(page: Page) {
  await page.getByRole('button', { name: /学习资料/ }).click();
  await expect(page.locator('.library-workspace')).toBeVisible();
}

async function playVideo(page: Page) {
  const video = page.locator('video').first();
  await expect(video).toBeVisible();
  await expect.poll(() => video.evaluate(element => (element as HTMLVideoElement).readyState), { timeout: 20_000 }).toBeGreaterThanOrEqual(2);
  await video.evaluate(async element => {
    const video = element as HTMLVideoElement;
    video.muted = true;
    video.currentTime = 0;
    await video.play();
  });
  await expect.poll(() => video.evaluate(element => (element as HTMLVideoElement).currentTime)).toBeGreaterThan(0.2);
  expect(await video.evaluate(element => (element as HTMLVideoElement).videoWidth)).toBe(320);
}

test('teacher authors real private media; student reads PDF, plays video and completes configured activities', async ({ page, browser, request }, testInfo) => {
  test.setTimeout(240_000);
  const email = process.env.E2E_TEACHER_EMAIL;
  const password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(!email || !password, 'Requires explicit local Reborn teacher credentials; no default accounts.');
  const tag = randomUUID().slice(0, 8);
  const classroomName = `媒体验收 ${tag}`;
  const textTitle = `TEST 自制文本 ${tag}`;
  const pdfTitle = `TEST 两页 PDF ${tag}`;
  const videoTitle = `TEST 自制视频 ${tag}`;
  const chapterId = 185; // Isolated release scope: does not affect the chapter-1/2 journey test.
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  await page.getByLabel('邮箱', { exact: true }).fill(email!);
  await page.getByLabel('密码', { exact: true }).fill(password!);
  await page.getByRole('button', { name: '进入小镇', exact: true }).click();
  await page.waitForURL('**/teacher', { timeout: 30_000 });
  await page.locator('.teacher-heading').getByRole('button', { name: '创建班级', exact: true }).click();
  const classDialog = page.getByRole('dialog', { name: '创建班级', exact: true });
  await expect(classDialog.getByLabel('班级名称', { exact: true })).toBeVisible({ timeout: 30_000 });
  await classDialog.getByLabel('班级名称', { exact: true }).fill(classroomName);
  const created = page.waitForResponse(response => response.url().endsWith('/api/classrooms') && response.request().method() === 'POST');
  await classDialog.getByRole('button', { name: '创建班级', exact: true }).click();
  const classResponse = await created;
  expect(classResponse.status()).toBe(201);
  const classroom = await classResponse.json() as { id: number; join_code: string };
  await expect(page.locator('.class-card').filter({ has: page.getByRole('heading', { name: classroomName, exact: true }) })).toBeVisible();
  await page.getByRole('button', { name: '材料与版本', exact: true }).click();
  const videoBytes = await recordTestVideo(page);
  expect(videoBytes.length).toBeGreaterThan(200);
  const fixtures = [
    { title: textTitle, name: 'self-authored-test.txt', mimeType: 'text/plain', buffer: Buffer.from('Self-authored TEST MEDIA, not course content.\nThis text verifies private upload, processing and reading.') },
    { title: pdfTitle, name: 'self-authored-two-pages.pdf', mimeType: 'application/pdf', buffer: twoPagePdf() },
    { title: videoTitle, name: 'self-authored-test.webm', mimeType: 'video/webm', buffer: videoBytes },
  ];
  const materials: { id: number; version: number; title: string; activityId: string }[] = [];
  const uploadDialog = page.getByRole('dialog', { name: '上传课程材料', exact: true });
  for (const [index, fixture] of fixtures.entries()) {
    await page.getByRole('button', { name: '上传课程材料', exact: true }).click();
    await uploadDialog.getByLabel('资料名称', { exact: true }).fill(fixture.title);
    await uploadDialog.getByLabel(/^所属章节/).selectOption(String(chapterId));
    await uploadDialog.getByLabel(/^文件/).setInputFiles({ name: fixture.name, mimeType: fixture.mimeType, buffer: fixture.buffer });
    const uploaded = page.waitForResponse(response => response.url().endsWith('/api/teacher/materials/upload') && response.request().method() === 'POST');
    await uploadDialog.getByRole('button', { name: '上传并处理', exact: true }).click();
    const response = await uploaded;
    expect(response.status()).toBe(201);
    const material = await response.json() as { id: number; version: number };
    materials.push({ ...material, title: fixture.title, activityId: `media-${tag}-${index}` });
    await expect(uploadDialog).toBeHidden();
    await page.getByRole('searchbox', { name: '搜索材料名称或 ID', exact: true }).fill(fixture.title);
    const row = page.locator('tbody tr').filter({ hasText: fixture.title }).first();
    await expect(row).toContainText('可使用', { timeout: 45_000 });
    await row.getByRole('button', { name: '预览', exact: true }).click();
    if (index === 0) await expect(page.locator('.material-preview pre')).toContainText('Self-authored TEST MEDIA');
    if (index === 1) {
      await expect(page.getByTitle('教师 PDF 预览')).toBeVisible();
      expect(await page.getByTitle('教师 PDF 预览').getAttribute('src')).toMatch(/^blob:/);
    }
    if (index === 2) await playVideo(page);
    await page.getByRole('button', { name: '收起预览', exact: true }).click();
  }
  await page.screenshot({ path: testInfo.outputPath('teacher-media-processing.png'), fullPage: true });
  await page.getByRole('button', { name: '活动与 NPC', exact: true }).click();
  await page.getByRole('button', { name: '新建活动', exact: true }).click();
  const activityForm = page.locator('.admin-editor form');
  for (const material of materials) {
    await activityForm.getByLabel('活动 ID', { exact: true }).fill(material.activityId);
    await activityForm.getByLabel('标题', { exact: true }).fill(material.title);
    await activityForm.getByLabel(/^章节/).selectOption(String(chapterId));
    await activityForm.getByLabel(/^材料/).selectOption(String(material.id));
    const saved = page.waitForResponse(response => response.url().endsWith('/api/teacher/activities') && response.request().method() === 'POST');
    await page.getByRole('button', { name: '创建活动', exact: true }).click();
    expect((await saved).status()).toBe(201);
  }
  await page.getByRole('button', { name: '任务配置', exact: true }).click();
  const definition = {
    id: `media-lab-${tag}`, title: `自制测试材料实验 ${tag}`, chapter_id: chapterId, version: 1, kind: 'side',
    nodes: materials.map((material, index) => ({ id: `read-${index}`, type: 'STUDY_LESSON', title: material.title, activity_id: material.activityId, activity_version: 1, dependencies: [] })),
    effects: [{ type: 'set_world_flag', key: `media_lab_${tag}` }],
  };
  await page.getByLabel('任务 JSON', { exact: true }).fill(JSON.stringify(definition));
  await page.getByRole('button', { name: '校验任务', exact: true }).click();
  await expect(page.locator('pre.notice')).toContainText('"valid": true');
  const published = page.waitForResponse(response => response.url().endsWith('/api/teacher/quests') && response.request().method() === 'POST');
  await page.getByRole('button', { name: '创建任务版本', exact: true }).click();
  expect((await published).status()).toBe(201);
  await page.getByRole('button', { name: '章节管理', exact: true }).click();
  const teacherToken = await page.evaluate(() => sessionStorage.getItem('pythontopia-reborn-session'));
  const courses = await (await request.get('/api/teacher/courses', { headers: { Authorization: `Bearer ${teacherToken}` } })).json();
  const releasedChapter = courses.chapters.find((chapter: { id: number }) => chapter.id === chapterId);
  const chapterRow = page.locator('.chapter-management tbody tr').filter({ hasText: releasedChapter.title });
  const released = page.waitForResponse(response => response.url().endsWith(`/releases/${chapterId}`) && response.request().method() === 'PUT');
  await chapterRow.getByRole('button', { name: '立即解锁', exact: true }).click();
  expect((await released).status()).toBe(200);
  await expect(chapterRow.locator('.status-badge')).toHaveText('已解锁');
  // Uploading another version does not publish it or change version-1 activity bindings.
  await page.getByRole('button', { name: '材料与版本', exact: true }).click();
  await page.getByRole('button', { name: '上传课程材料', exact: true }).click();
  await uploadDialog.getByLabel('资料名称', { exact: true }).fill(textTitle);
  await uploadDialog.getByLabel(/^所属章节/).selectOption(String(chapterId));
  await uploadDialog.getByLabel(/^更新已有材料（选填）/).fill(String(materials[0]!.id));
  await uploadDialog.getByLabel(/^文件/).setInputFiles({ name: 'self-authored-test-v2.txt', mimeType: 'text/plain', buffer: Buffer.from('UNPUBLISHED VERSION TWO - test fixture only.') });
  const versioned = page.waitForResponse(response => response.url().endsWith('/api/teacher/materials/upload') && response.request().method() === 'POST');
  await uploadDialog.getByRole('button', { name: '上传并处理', exact: true }).click();
  const versionResponse = await versioned;
  expect(versionResponse.status()).toBe(201);
  expect((await versionResponse.json()).version).toBe(2);
  await expect(uploadDialog).toBeHidden();
  await page.getByRole('searchbox', { name: '搜索材料名称或 ID', exact: true }).fill(textTitle);
  const versionRow = page.locator('tbody tr').filter({ hasText: textTitle }).first();
  await expect(versionRow.locator('td').nth(1)).toHaveText('v2');
  await expect(versionRow).toContainText('可使用', { timeout: 45_000 });

  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  try {
    const student = await context.newPage();
    student.on('pageerror', error => errors.push(error.message));
    await student.goto('/');
    await student.getByRole('button', { name: '初次到访？注册居民证', exact: true }).click();
    await student.getByLabel('名字', { exact: true }).fill('媒体验收学生');
    await student.getByLabel('邮箱', { exact: true }).fill(`media-${randomUUID()}@example.test`);
    await student.getByLabel('密码', { exact: true }).fill(`Media-${randomUUID()}`);
    await student.getByRole('button', { name: '注册并进入', exact: true }).click();
    await student.getByLabel('班级加入码', { exact: true }).fill(classroom.join_code);
    await student.getByRole('button', { name: '加入班级', exact: true }).click();
    await expect(student.locator('canvas')).toBeVisible();
    await student.getByRole('button', { name: '按任意键继续', exact: true }).click();
    await expect(student.getByRole('button', { name: /学习资料/ })).toBeVisible();
    const token = await student.evaluate(() => sessionStorage.getItem('pythontopia-reborn-session'));
    const headers = { Authorization: `Bearer ${token}` };
    const unbound = await request.get(`/api/materials/${materials[0]!.id}/versions/2?classroom_id=${classroom.id}`, { headers });
    expect(unbound.status()).toBe(403);
    expect((await request.get(`/api/materials/${materials[0]!.id}/versions/2/file?classroom_id=${classroom.id}`, { headers })).status()).toBe(403);
    await openLibrary(student);
    await student.getByRole('button', { name: new RegExp(pdfTitle) }).click();
    await expect(student.locator('.pdf-reader .reader-toolbar')).toContainText('第 1 / 2 页', { timeout: 30_000 });
    const pdfCanvas = student.getByLabel('PDF课件当前页', { exact: true });
    await expect.poll(() => pdfCanvas.evaluate(element => {
      const canvas = element as HTMLCanvasElement;
      const pixels = canvas.getContext('2d')?.getImageData(0, 0, canvas.width, canvas.height).data;
      if (!pixels) return 0;
      let dark = 0;
      for (let index = 0; index < pixels.length; index += 4) if (pixels[index]! < 180 && pixels[index + 3]! > 0) dark++;
      return dark;
    })).toBeGreaterThan(100);
    await student.locator('.pdf-reader').getByRole('button', { name: '下一页 PDF', exact: true }).click();
    await expect(student.locator('.pdf-reader .reader-toolbar')).toContainText('第 2 / 2 页');
    await student.screenshot({ path: testInfo.outputPath('student-pdf-page-two.png') });
    expect(await student.getByRole('dialog', { name: '游戏工作区' }).count()).toBe(1);
    await closeWorkspace(student);
    await openLibrary(student);
    await student.getByRole('button', { name: new RegExp(videoTitle) }).click();
    await playVideo(student);
    await student.screenshot({ path: testInfo.outputPath('student-video-playing.png') });
    await closeWorkspace(student);
    for (const [index, material] of materials.entries()) {
      await student.getByRole('button', { name: /任务日志/ }).click();
      await student.locator('.quest-directory .quest-card').filter({ hasText: definition.title }).click();
      const quest = student.locator('.journal-entry').filter({ has: student.getByRole('heading', { name: definition.title, exact: true }) });
      const node = quest.locator('li').filter({ has: student.getByText(material.title, { exact: true }) });
      await node.getByRole('button', { name: '打开课程', exact: true }).click();
      await expect(student.getByRole('heading', { name: material.title, exact: true })).toBeVisible();
      if (index === 0) await expect(student.locator('.lesson-text')).toContainText('Self-authored TEST MEDIA');
      await student.getByRole('button', { name: '记录本次学习', exact: true }).click();
      await expect(student.getByText('本次学习已记录，任务目标已更新。', { exact: true }).last()).toBeVisible();
      await expect.poll(async () => {
        const response = await request.get(`/api/world?classroom_id=${classroom.id}`, { headers });
        const state = await response.json();
        return state.quests.find((quest: { id: string }) => quest.id === definition.id).nodes.find((node: { id: string }) => node.id === `read-${index}`).status;
      }, { timeout: 15_000, message: `${material.title}: authoritative learning node completion` }).toBe('complete');
      await closeWorkspace(student);
    }
    const world = await request.get(`/api/world?classroom_id=${classroom.id}`, { headers });
    expect((await world.json()).quests.find((quest: { id: string }) => quest.id === definition.id).status).toBe('complete');
    const library = await request.get(`/api/library?classroom_id=${classroom.id}`, { headers });
    expect((await library.json()).find((material: { id: number }) => material.id === materials[0]!.id).version).toBe(1);
    await page.getByRole('button', { name: '学习记录', exact: true }).click();
    await page.getByRole('button', { name: '刷新记录', exact: true }).click();
    const member = page.locator('tbody tr').filter({ hasText: '媒体验收学生' });
    await expect(member).toBeVisible();
    await expect(member.locator('td').nth(1)).toHaveText('3');
    await expect(member.locator('td').nth(2)).toHaveText('0'); // Side activities do not complete a main chapter.
    await expect(member.locator('td').nth(3)).toHaveText('3');
    await page.screenshot({ path: testInfo.outputPath('teacher-real-media-progress.png') });
    expect(errors).toEqual([]);
  } finally {
    await context.close();
  }
});
