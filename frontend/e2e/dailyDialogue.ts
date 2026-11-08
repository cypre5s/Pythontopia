import { expect, type APIRequestContext, type Page, type TestInfo } from "@playwright/test";

type DailyView = { id: string; revision: number; status: string; mode: string; node: { id: string; text: string; choices?: { id: string; label: string }[] } };

async function meetMayor(page: Page) {
  const response = page.waitForResponse(item => item.url().endsWith("/api/story/interactions") && item.request().method() === "POST");
  await page.keyboard.press("e");
  const received = await response;
  expect(received.ok(), await received.text()).toBe(true);
  const view = await received.json() as DailyView;
  expect(view.mode).toBe("ambient");
  expect(view.node.id).toBe("mayor-daily-menu");
  await expect(page.getByRole("dialog", { name: "剧情对白", exact: true })).toBeVisible();
  return view;
}

async function choose(page: Page, view: DailyView, choiceId?: string) {
  const dialogue = page.getByRole("dialog", { name: "剧情对白", exact: true });
  await expect(dialogue.locator(".dialogue-text")).toHaveText(view.node.text);
  const response = page.waitForResponse(item => /\/story\/sessions\/[^/]+\/advance$/.test(item.url()) && item.request().method() === "POST");
  if (choiceId) {
    const selected = view.node.choices!.find(item => item.id === choiceId)!;
    expect(selected).toBeTruthy();
    await dialogue.getByRole("button", { name: selected.label, exact: true }).click();
  } else await dialogue.locator(".dialogue-text").click();
  const received = await response;
  expect(received.ok(), await received.text()).toBe(true);
  return { view: await received.json() as DailyView, requestBody: received.request().postDataJSON() };
}

export async function verifyDailyDialogue(page: Page, request: APIRequestContext, classroomId: number, headers: Record<string, string>, info: TestInfo) {
  const state = async () => {
    const world = await (await request.get(`/api/world?classroom_id=${classroomId}`, { headers })).json();
    const paper = await (await request.get(`/api/activities/ch1-assessment/assessment?classroom_id=${classroomId}`, { headers })).json();
    const ai = await (await request.get("/api/assistant/status", { headers })).json();
    return { quests: world.quests, flags: world.flags, paper, modelCalls: ai.model_calls };
  };
  const before = await state();
  const menu = await meetMayor(page);
  expect(menu.node.choices!.map(item => item.id)).toContain("first-lesson");
  expect(menu.node.choices!.map(item => item.id)).not.toContain("next-chapter");
  const random = await choose(page, menu, "town-life");
  expect(["mayor-daily-fountain", "mayor-daily-path"]).toContain(random.view.node.id);
  const duplicate = await request.post(`/api/story/sessions/${menu.id}/advance`, { headers, data: random.requestBody });
  expect(duplicate.ok()).toBe(true);
  expect(await duplicate.json()).toMatchObject({ id: menu.id, revision: random.view.revision, node: random.view.node });
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.getByRole("button", { name: "按任意键继续", exact: true }).click();
  const restored = await (await request.get(`/api/story/current?classroom_id=${classroomId}`, { headers })).json() as DailyView;
  expect(restored).toMatchObject({ id: menu.id, revision: random.view.revision, mode: "ambient", node: random.view.node });
  await expect(page.getByRole("dialog", { name: "剧情对白", exact: true }).locator(".dialogue-text")).toHaveText(random.view.node.text);
  await page.screenshot({ path: info.outputPath("daily-random-resumed.png"), animations: "disabled" });
  expect((await choose(page, restored)).view.status).toBe("ended");
  await expect(page.getByRole("dialog", { name: "剧情对白", exact: true })).toBeHidden();
  expect(await state()).toEqual(before);
}

export async function verifyUnlockedDailyChoice(page: Page, info: TestInfo) {
  const menu = await meetMayor(page);
  expect(menu.node.choices!.map(item => item.id)).toContain("next-chapter");
  await page.screenshot({ path: info.outputPath("daily-chapter-unlocked-options.png"), animations: "disabled" });
  const next = await choose(page, menu, "next-chapter");
  expect(next.view.node.id).toBe("mayor-daily-next");
  expect((await choose(page, next.view)).view.status).toBe("ended");
  await expect(page.getByRole("dialog", { name: "剧情对白", exact: true })).toBeHidden();
}
