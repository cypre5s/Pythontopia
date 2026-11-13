import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { randomUUID } from "node:crypto";
import { chooseOption, go, json, setup } from "./helpers";
import type { Authoring, CharacterAsset } from "../src/lib/authoring";

test("统一角色草稿、私有素材、摆放与真实游戏身份", async ({ page, request, browser }, info) => {
  test.setTimeout(150000);
  const scope = await setup(page, request, "角色验收");
  const endpoint = `/api/teacher/classrooms/${scope.cid}/authoring`;
  const read = () => request.get(endpoint, { headers: scope.headers }).then(json<Authoring>);
  const body = (doc: Authoring) => ({ expected_revision: doc.revision, npcs: doc.npcs, flows: doc.flows, quests: doc.quests, configuration: doc.configuration });
  const save = async () => {
    await page.getByRole("button", { name: "保存课程", exact: true }).click();
    await expect(page.getByRole("button", { name: "保存课程", exact: true })).toBeDisabled();
  };
  const roles = () => page.getByRole("tab", { name: "助教教学角色", exact: true }).click();
  const map = async () => {
    await page.getByRole("tab", { name: "地图交互与开放规则", exact: true }).click();
    await page.getByRole("button", { name: "角色摆放与出现", exact: true }).click();
  };
  await go(page, "教学流程");
  await roles();
  await page.getByRole("button", { name: "新增教学角色", exact: true }).click();
  await page.getByLabel("角色名称").fill("自定义向导");
  await page.getByLabel("AI 助教人设描述").fill("PRIVATE-PERSONA-ONLY-STORED");
  await expect(page.getByText("未放置，可到地图交互与开放规则中安排位置。")).toBeVisible();
  await save();
  const initial = await read();
  const character = initial.npcs.find((item) => item.name === "自定义向导")!;
  const original = initial.npcs[0];
  expect(character.placement).toBeNull();
  await page.getByRole("tab", { name: "对话立绘", exact: true }).click();
  async function upload(kind: "sprite" | "portrait", path: string) {
    await page.getByRole("tab", { name: kind === "portrait" ? "对话立绘" : "像素角色", exact: true }).click();
    const response = page.waitForResponse((r) => r.request().method() === "POST" && r.url().endsWith("/character-assets"));
    await page.locator(`#upload-${kind}`).setInputFiles({ name: `custom-${kind}.png`, mimeType: "image/png", buffer: readFileSync(resolve(path)) });
    const result = await response;
    expect(result.status(), await result.text()).toBe(201);
    return result.json() as Promise<CharacterAsset>;
  }
  const portrait = await upload("portrait", "../frontend/public/assets/pixel/portraits/galgame/professor.png");
  const sprite = await upload("sprite", "../frontend/public/assets/pixel/sprites/super-retro-world/characters/chara_4.png");
  await page.getByLabel("地图像素角色", { exact: true }).click();
  await expect(page.getByRole("option", { name: /像素角色 4 系统素材/ })).toBeVisible();
  await page.screenshot({ path: info.outputPath("character-picker.png"), animations: "disabled" });
  await page.getByRole("option", { name: /custom-sprite.png/ }).click();

  await expect(page.getByRole("img", { name: "custom-sprite.png" }).last()).toBeVisible();
  await chooseOption(page, page.getByLabel("预览朝向"), "向上");
  await expect(page.getByRole("img", { name: "custom-sprite.png" })).toHaveCSS("background-position", "-64px -240px");
  await page.getByRole("checkbox", { name: "预览行走" }).check();
  await page.screenshot({ path: info.outputPath("character-editor.png"), animations: "disabled" });
  await save();
  let doc = await read();
  expect(doc.npcs.find((n) => n.id === character.id)).toMatchObject({ portrait: portrait.id, sprite: sprite.id, persona: "PRIVATE-PERSONA-ONLY-STORED" });
  const failed = page.waitForResponse((r) => r.request().method() === "POST" && r.url().endsWith("/character-assets"));
  await page.locator("#upload-sprite").setInputFiles({ name: "invalid.png", mimeType: "image/png", buffer: Buffer.from("not an image") });
  expect((await failed).status()).toBe(422);
  await expect(page.getByRole("alert")).toContainText("图片无法完整解码");
  expect((await read()).npcs.find((n) => n.id === character.id)?.sprite).toBe(sprite.id);
  await map();
  await page.getByRole("button", { name: "自定义向导 未放置", exact: true }).click();
  const canvas = page.getByRole("group", { name: "教学角色摆放地图" });
  const box = (await canvas.boundingBox())!;
  await canvas.click({ position: { x: box.width * 22.5 / 50, y: box.height * 35.5 / 40 } });
  await expect(page.getByLabel("横坐标")).toHaveValue("22");
  await expect(page.getByLabel("纵坐标")).toHaveValue("35");
  const marker = page.getByRole("button", { name: "选择 自定义向导", exact: true });
  await marker.hover();
  const dragBox = (await canvas.boundingBox())!;
  await page.mouse.down();
  await page.mouse.move(dragBox.x + dragBox.width * 22.5 / 50, dragBox.y + dragBox.height * 34.5 / 40, { steps: 5 });
  await page.mouse.up();
  await expect(page.getByLabel("纵坐标")).toHaveValue("34");
  await chooseOption(page, page.getByLabel("朝向", { exact: true }), "向左");
  await chooseOption(page, page.getByLabel("条件类型", { exact: true }), "条件不满足");
  await save();
  doc = await read();
  expect(doc.npcs.find((n) => n.id === character.id)).toMatchObject({ placement: { tile_x: 22, tile_y: 34, direction: "left" }, appearance_condition: { not: { always: true } } });
  expect(doc.npcs[0]).toEqual(original);
  await chooseOption(page, page.getByLabel("条件类型", { exact: true }).first(), "总是");
  await save();
  await page.screenshot({ path: info.outputPath("character-placement.png"), animations: "disabled" });
  await page.getByRole("button", { name: "撤回地图放置" }).click();
  await save();
  expect((await read()).npcs.find((n) => n.id === character.id)?.placement).toBeNull();
  await canvas.click({ position: { x: box.width * 22.5 / 50, y: box.height * 34.5 / 40 } });
  await save();
  await roles();
  await page.getByRole("button", { name: "自定义向导", exact: true }).click();
  await page.getByLabel("角色名称").fill("保留的并发草稿");
  doc = await read();
  await json(await request.put(endpoint, { headers: scope.headers, data: {
    ...body(doc),
    npcs: doc.npcs.map((n) => n.id === character.id ? { ...n, name: "服务端新名称" } : n),
  } }));
  await page.getByRole("button", { name: "保存课程", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByLabel("角色名称")).toHaveValue("保留的并发草稿");
  await page.getByRole("button", { name: "取消编辑", exact: true }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "放弃修改", exact: true }).click();
  await page.reload();
  await roles();
  await page.getByRole("button", { name: "服务端新名称", exact: true }).click();
  await expect(page.getByLabel("角色名称")).toHaveValue("服务端新名称");
  await page.setViewportSize({ width: 1024, height: 768 });
  await page.screenshot({ path: info.outputPath("character-editor-1024.png"), animations: "disabled" });
  doc = await read();
  doc.flows.push({ id: "character-test-flow", title: "角色身份", quest_id: null, chapter_id: null, content: {
    schema_version: 1, mode: "public", bindings: {}, entries: { [character.id]: [{ when: { always: true }, node: "hello" }] },
    nodes: [{ id: "hello", type: "dialogue", speaker_npc_id: character.id, text: "这是统一角色资料。", next: "end" }, { id: "end", type: "end" }],
  } });
  await json(await request.put(endpoint, { headers: scope.headers, data: body(doc) }));
  const classes = await json<{ id: number; join_code: string }[]>(await request.get("/api/classrooms", { headers: scope.headers }));
  const student = await json<{ access_token: string }>(await request.post("/api/auth/register", { data: {
    student_id: BigInt(`0x${randomUUID().replaceAll("-", "").slice(0, 24)}`).toString(), name: "角色验收学生", password: randomUUID(),
  } }));
  const studentHeaders = { Authorization: `Bearer ${student.access_token}` };
  await json(await request.post("/api/classrooms/join", { headers: studentHeaders, data: { join_code: classes.find((c) => c.id === scope.cid)!.join_code } }));
  const world = await json<{ npcs: { key: string; stable_id: string; name: string }[] }>(await request.get(`/api/world?classroom_id=${scope.cid}`, { headers: studentHeaders }));
  const npc = world.npcs.find((n) => n.stable_id === character.id)!;
  expect(npc.name).toBe("服务端新名称");
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 }, reducedMotion: "reduce" });
  await context.addInitScript((token) => sessionStorage.setItem("pythontopia-reborn-session", token), student.access_token);
  const game = await context.newPage();
  const aiCalls: string[] = [], images: number[] = [];
  game.on("request", (r) => { if (r.method() === "POST" && /assistant|agent/.test(r.url())) aiCalls.push(r.url()); });
  game.on("response", (r) => { if (r.url().includes(`/character-assets/${sprite.id.slice(6)}`)) images.push(r.status()); });
  await game.goto(new URL("/world", page.url()).href);
  await game.getByRole("button", { name: "按任意键继续" }).click();
  await expect(game.locator(".world-canvas canvas")).toHaveCount(1);
  await expect.poll(() => images).toContain(200);
  await json(await request.put("/api/world/position", { headers: studentHeaders, data: { classroom_id: scope.cid, map_key: "town_hub", tile_x: 22, tile_y: 35, direction: "up" } }));
  await json(await request.post("/api/story/interactions", { headers: studentHeaders, data: { classroom_id: scope.cid, npc_key: npc.key } }));
  await game.reload();
  const arrival = game.getByRole("button", { name: "按任意键继续" });
  await arrival.click();
  await expect(arrival).toBeHidden();
  await expect(game.getByRole("dialog", { name: "剧情对白" })).toContainText("服务端新名称");
  await expect(game.locator(".dialogue-stage img.portrait")).toBeVisible();
  await game.screenshot({ path: info.outputPath("private-character-dialogue.png"), animations: "disabled" });
  await game.setViewportSize({ width: 390, height: 844 });
  await game.screenshot({ path: info.outputPath("private-character-mobile.png"), animations: "disabled" });
  expect(aiCalls).toEqual([]);
  await context.close();
});
