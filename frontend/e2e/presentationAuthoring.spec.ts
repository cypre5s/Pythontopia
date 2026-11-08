import { test, expect } from '@playwright/test';
import { access, writeFile } from 'node:fs/promises';
import { basename, extname } from 'node:path';
import { randomUUID } from 'node:crypto';

test.use({ actionTimeout: 20_000 });

test('teacher uploads self-authored PPTX; student reads the converted private PDF and records learning', async ({ page, browser, request }, testInfo) => {
  test.setTimeout(240_000);
  const pptxPath = process.env.E2E_PPTX_PATH;
  const student_id = process.env.E2E_TEACHER_STUDENT_ID;
  const password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(!pptxPath, 'Requires E2E_PPTX_PATH pointing to an explicitly supplied self-authored test presentation.');
  test.skip(!student_id || !password, 'Requires explicit Reborn teacher credentials; no default accounts.');
  await access(pptxPath!);
  expect(extname(pptxPath!).toLowerCase()).toBe('.pptx');

  const tag = randomUUID().slice(0, 8);
  const classroomName = `PPTX 自制测试 ${tag}`;
  const title = `TEST 自制 PPTX（非原课件）${tag}`;
  const studentName = `PPT验收学生-${tag}`;
  const chapterId = 185;
  const activityId = `pptx-lesson-${tag}`;
  const questId = `pptx-reading-${tag}`;
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));

  await page.goto('/');
  await page.getByLabel('学号', { exact: true }).fill(student_id!);
  await page.getByLabel('密码', { exact: true }).fill(password!);
  await page.getByRole('button', { name: '进入小镇', exact: true }).click();
  await page.waitForURL('**/teacher', { timeout: 30_000 });
  await page.locator('.teacher-heading').getByRole('button', { name: '创建班级', exact: true }).click();
  const classDialog = page.getByRole('dialog', { name: '创建班级', exact: true });
  await expect(classDialog.getByLabel('班级名称', { exact: true })).toBeVisible({ timeout: 30_000 });
  await classDialog.getByLabel('班级名称', { exact: true }).fill(classroomName);
  const classCreated = page.waitForResponse(response => response.url().endsWith('/api/classrooms') && response.request().method() === 'POST');
  await classDialog.getByRole('button', { name: '创建班级', exact: true }).click();
  const classResponse = await classCreated;
  expect(classResponse.status()).toBe(201);
  const classroom = await classResponse.json() as { id: number; join_code: string };
  await expect(page.locator('.class-card').filter({ has: page.getByRole('heading', { name: classroomName, exact: true }) })).toBeVisible();
  await page.getByRole('combobox', { name: '当前班级', exact: true }).selectOption(String(classroom.id));

  await page.getByRole('button', { name: '课程材料', exact: true }).click();
  await page.getByRole('button', { name: '上传课程材料', exact: true }).click();
  const uploadDialog = page.getByRole('dialog', { name: '上传课程材料', exact: true });
  await uploadDialog.getByLabel('资料名称', { exact: true }).fill(title);
  await uploadDialog.getByRole('group', { name: '关联章节', exact: true }).locator(`input[type="checkbox"][value="${chapterId}"]`).check();
  await uploadDialog.getByLabel(/^文件/).setInputFiles(pptxPath!);
  const uploaded = page.waitForResponse(response => response.url().endsWith('/api/teacher/materials/upload') && response.request().method() === 'POST');
  await uploadDialog.getByRole('button', { name: '上传资料', exact: true }).click();
  const uploadResponse = await uploaded;
  expect(uploadResponse.status()).toBe(201);
  const material = await uploadResponse.json() as { id: number; status: string };
  expect(material.id).toBeGreaterThan(0);
  await expect(uploadDialog).toBeHidden();
  await page.getByRole('searchbox', { name: '搜索材料名称或 ID', exact: true }).fill(title);
  const row = page.locator('tbody tr').filter({ hasText: title }).first();
  await expect(row).toContainText('演示文稿');
  await expect(row).toContainText('可使用', { timeout: 90_000 });
  await row.getByRole('button', { name: '预览', exact: true }).click();
  const previewDialog = page.getByRole('dialog', { name: title, exact: true });
  const previewFrame = previewDialog.getByTitle(title, { exact: true });
  await expect(previewFrame).toBeVisible();
  expect(await previewFrame.getAttribute('src')).toMatch(/^blob:/);
  await page.screenshot({ path: testInfo.outputPath('teacher-self-authored-pptx-ready.png'), fullPage: true });
  await page.keyboard.press('Escape');
  await expect(previewDialog).toBeHidden();

  // Prepare real test-only definitions via API while their authoring navigation is withdrawn.
  const teacherToken = await page.evaluate(() => sessionStorage.getItem('pythontopia-reborn-session'));
  const teacherHeaders = { Authorization: `Bearer ${teacherToken}` };
  const activityResponse = await request.post('/api/teacher/activities', { headers: teacherHeaders, data: {
    id: activityId, title, chapter_id: chapterId, kind: 'lesson', npc_key: 'professor', material_id: material.id, assistant_allowed: false,
  } });
  expect(activityResponse.status()).toBe(201);
  expect((await activityResponse.json()).id).toBe(activityId);
  const definition = {
    id: questId, title: `自制 PPTX 阅读验收 ${tag}`, chapter_id: chapterId, kind: 'side',
    nodes: [{ id: 'read-presentation', type: 'STUDY_LESSON', title, activity_id: activityId, dependencies: [] }],
    effects: [{ type: 'set_world_flag', key: `pptx_test_${tag}` }] };
  const validation = await request.post('/api/teacher/quests/validate', { headers: teacherHeaders, data: { definition } });
  expect(validation.status()).toBe(200);
  expect((await validation.json()).valid).toBe(true);
  expect((await request.post('/api/teacher/quests', { headers: teacherHeaders, data: { definition } })).status()).toBe(201);
  await page.locator('#teacher-navigation').getByRole('button', { name: '章节管理', exact: true }).click();
  const courses = await (await request.get('/api/teacher/courses', { headers: { Authorization: `Bearer ${teacherToken}` } })).json();
  const releasedChapter = courses.chapters.find((chapter: { id: number }) => chapter.id === chapterId);
  const chapterRow = page.locator('.chapter-management tbody tr').filter({ hasText: releasedChapter.title });
  const releaseSaved = page.waitForResponse(response => response.url().endsWith(`/releases/${chapterId}`) && response.request().method() === 'PUT');
  await chapterRow.getByRole('button', { name: '立即解锁', exact: true }).click();
  expect((await releaseSaved).status()).toBe(200);
  await expect(chapterRow.locator('.status-badge')).toHaveText('已解锁');
  await page.screenshot({ path: testInfo.outputPath('teacher-self-authored-pptx-published.png'), fullPage: true });

  const context = await browser.newContext({ viewport: page.viewportSize() ?? { width: 1920, height: 1080 } });
  try {
    const student = await context.newPage();
    student.on('pageerror', error => errors.push(error.message));
    await student.goto('/');
    await student.getByRole('button', { name: '初次到访？注册居民证', exact: true }).click();
    await student.getByLabel('名字', { exact: true }).fill(studentName);
    await student.getByLabel('学号', { exact: true }).fill(BigInt('0x' + randomUUID().replaceAll('-', '').slice(0, 24)).toString());
    await student.getByLabel('密码', { exact: true }).fill(`Pptx-${randomUUID()}`);
    await student.getByRole('button', { name: '注册并进入', exact: true }).click();
    await student.getByLabel('班级加入码', { exact: true }).fill(classroom.join_code);
    await student.getByRole('button', { name: '加入班级', exact: true }).click();
    await expect(student.locator('canvas')).toBeVisible({ timeout: 30_000 });
    await student.getByRole('button', { name: '按任意键继续', exact: true }).click();
    await expect(student.getByRole('button', { name: /任务日志/ })).toBeVisible();
    const token = await student.evaluate(() => sessionStorage.getItem('pythontopia-reborn-session'));
    const headers = { Authorization: `Bearer ${token}` };
    const fileUrl = `/api/materials/${material.id}/file?classroom_id=${classroom.id}`;
    expect((await request.get(fileUrl)).status()).toBe(401);
    const ranged = await request.get(fileUrl, { headers: { ...headers, Range: 'bytes=0-15' } });
    expect(ranged.status()).toBe(206);
    expect((await ranged.body()).subarray(0, 5).toString()).toBe('%PDF-');

    await student.getByRole('button', { name: /任务日志/ }).click();
    await student.locator('.quest-directory .quest-card').filter({ hasText: definition.title }).click();
    const quest = student.locator('.journal-entry').filter({ has: student.getByRole('heading', { name: definition.title, exact: true }) });
    const node = quest.locator('li').filter({ has: student.getByText(title, { exact: true }) });
    await node.getByRole('button', { name: '打开课程', exact: true }).click();
    await expect(student.getByRole('region', { name: '课程内容', exact: true })).toContainText(title);
    await expect(student.locator('.pdf-reader .reader-toolbar')).toContainText('第 1 / 1 页', { timeout: 30_000 });
    const pdfCanvas = student.getByLabel('PDF课件当前页', { exact: true });
    await expect(pdfCanvas).toBeVisible();
    await expect.poll(() => pdfCanvas.evaluate(element => {
      const canvas = element as HTMLCanvasElement;
      const pixels = canvas.getContext('2d')?.getImageData(0, 0, canvas.width, canvas.height).data;
      if (!pixels) return 0;
      let ink = 0;
      for (let i = 0; i < pixels.length; i += 4) if (pixels[i]! < 80 && pixels[i + 1]! < 80 && pixels[i + 2]! < 80 && pixels[i + 3]! > 0) ink++;
      return ink;
    }), { timeout: 20_000, message: 'Converted self-authored PPTX must render real PDF text on canvas' }).toBeGreaterThan(100);
    await student.getByText('当前第 1 页提取文本（可复制）', { exact: true }).click();
    await expect(student.locator('.lesson-extracted-text .lesson-text')).toContainText('Self-authored PPT conversion test fixture.');
    await expect(student.locator('.lesson-extracted-text .lesson-text')).toContainText('not original courseware');
    await student.screenshot({ path: testInfo.outputPath('student-self-authored-pptx-real-pdf-canvas.png') });
    expect(await student.getByRole('dialog', { name: '游戏工作区' }).count()).toBe(1);

    const participation = student.waitForResponse(response => response.url().endsWith(`/api/activities/${activityId}/participation`) && response.request().method() === 'POST');
    await student.getByRole('button', { name: '记录本次学习', exact: true }).click();
    const record = await participation;
    expect(record.status()).toBe(200);
    expect((await record.json()).participation_only).toBe(true);
    await expect(student.getByText('本次学习已记录，任务目标已更新。', { exact: true })).toBeVisible();
    await expect.poll(async () => {
      const response = await request.get(`/api/world?classroom_id=${classroom.id}`, { headers });
      const world = await response.json() as { quests: { id: string; status: string }[] };
      return world.quests.find(item => item.id === questId)?.status;
    }, { timeout: 15_000 }).toBe('complete');
    const assistant = await request.get('/api/assistant/status', { headers });
    expect((await assistant.json()).model_calls).toBe(0);

    await page.getByRole('button', { name: '学习记录', exact: true }).click();
    await page.getByRole('button', { name: '刷新记录', exact: true }).click();
    const member = page.locator('tbody tr').filter({ hasText: studentName });
    await expect(member).toBeVisible();
    await expect(member.locator('td').nth(1)).toHaveText('1');
    await expect(member.locator('td').nth(2)).toHaveText('0'); // A side quest alone does not complete a main chapter.
    await expect(member.locator('td').nth(3)).toHaveText('1');
    await page.screenshot({ path: testInfo.outputPath('teacher-self-authored-pptx-learning-record.png'), fullPage: true });
    expect(errors).toEqual([]);
    const acceptancePath = testInfo.outputPath('presentation-acceptance.json');
    await writeFile(acceptancePath, JSON.stringify({
      fixture: basename(pptxPath!), notice: 'Self-authored test presentation; not original courseware',
      teacher_upload_and_configuration: 'UI', converter: 'real private HTTP service',
      material_id: material.id, classroom_id: classroom.id, chapter_id: chapterId,
      student_pdf_canvas: true, reading_record: true, reading_is_mastery: false, model_calls: 0 }, null, 2));
    await testInfo.attach('presentation-acceptance.json', { contentType: 'application/json', path: acceptancePath });
  } finally {
    await context.close();
  }
});
