import { test, expect, type APIRequestContext, type Page, type Response } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";

interface Building {
  id: string;
  object_name: string;
  label: string;
  purpose: string;
  status: "open" | "closed" | "landmark";
  anchor: { x: number; y: number };
  portal_id?: string;
}
interface Player {
  map_key: string;
  tile_x: number;
  tile_y: number;
  direction: string;
}
interface World {
  player: Player;
  flags: string[];
  npcs: { key: string; name: string; map_key: string; tile_x: number; tile_y: number }[];
}
interface Definition {
  default_spawn: Player;
  maps: Record<string, {
    width: number; height: number; label: string; asset?: string; buildings: Building[];
    camera_mode?: "centered" | "bounded";
    render_bounds?: { x: number; y: number; width: number; height: number };
  }>;
  portals: {
    id: string;
    label: string;
    action_label: string;
    blocked_message: string;
    from: { map_key: string; tile_x: number; tile_y: number; radius: number };
    to: Player;
  }[];
}
interface TiledMap {
  width: number;
  height: number;
  layers: { objects?: { name: string; type: string; x: number; y: number; width: number; height: number; properties?: { name: string; value: unknown }[] }[] }[];
}

const definition = JSON.parse(readFileSync(
  new URL("../../data/world/world.json", import.meta.url), "utf8",
)) as Definition;
const buildingNames: Record<string, string> = {
  mayor_hall: "镇长大厅",
  cottage_a: "松风小屋",
  review_hall: "居民会馆",
  workshop: "教学工坊",
  cottage_e: "湖畔小屋",
  library: "图书馆",
  training: "训练小屋",
  cottage_b: "花园小屋",
  cottage_c: "石径小屋",
  cottage_d: "南街小屋",
  arena: "练习会馆",
};

async function readWorld(request: APIRequestContext, classroomId: number, headers: Record<string, string>): Promise<World> {
  const response = await request.get(`/api/world?classroom_id=${classroomId}`, { headers });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json() as Promise<World>;
}
async function openMap(page: Page) {
  const map = page.getByRole("dialog", { name: "地图工作区", exact: true });
  if (!(await map.isVisible()))
    await page.getByRole("button", { name: "地图", exact: true }).click();
  await expect(map).toBeVisible();
  await expect(map.getByRole("img", { name: "地图预览", exact: true })).toBeVisible();
  return map;
}
async function goToTarget(page: Page, kind: "建筑" | "人物", name: string) {
  const map = await openMap(page);
  await map.getByRole("button", { name: `筛选${kind}`, exact: true }).click();
  await map.getByRole("button", { name: `选择${name}`, exact: true }).click();
  await map.getByRole("button", { name: "前往所选目标", exact: true }).click();
  await expect(map).toBeHidden();
}
async function goToBuilding(page: Page, name: string) {
  await goToTarget(page, "建筑", name);
}
async function findNpc(page: Page, name: string) {
  await goToTarget(page, "人物", name);
}
function waitForTransition(page: Page, portalId: string) {
  return page.waitForResponse(
    response => response.url().endsWith("/api/world/transition") &&
      response.request().method() === "POST" &&
      response.request().postDataJSON().portal_id === portalId,
    { timeout: 60_000 },
  );
}
async function expectTransition(response: Response, destination: Player) {
  expect(response.ok(), await response.text()).toBeTruthy();
  const payload = await response.json() as { player: Player };
  expect(payload.player).toMatchObject({ ...destination });
}
async function finishPlainStory(page: Page) {
  const dialogue = page.getByRole("dialog", { name: "剧情对白", exact: true });
  await expect(dialogue).toBeVisible({ timeout: 60_000 });
  for (let step = 0; step < 12; step++) {
    await expect(dialogue.locator(".dialogue-response")).toHaveCount(0);
    const response = page.waitForResponse(candidate =>
      /\/api\/story\/sessions\/[^/]+\/advance$/.test(candidate.url()) && candidate.request().method() === "POST",
    );
    await dialogue.locator(".dialogue-text").click();
    const received = await response;
    expect(received.ok(), await received.text()).toBeTruthy();
    const view = await received.json() as { status: string; node: { text: string } | null };
    if (view.status === "ended") {
      await expect(dialogue).toBeHidden();
      return;
    }
    expect(view.status).toBe("active");
    await expect(dialogue.locator(".dialogue-text")).toHaveText(view.node!.text);
  }
  throw new Error("The plain NPC scene did not end within twelve nodes");
}

/** Project a public Tiled object's center into the ordinary player-following view. */
async function clickVisibleBuilding(page: Page, map: TiledMap, player: Player, building: Building) {
  const object = map.layers.flatMap(layer => layer.objects ?? []).find(item => item.name === building.object_name)!;
  expect(object).toBeTruthy();
  await clickVisibleWorldPoint(page, definition.maps[player.map_key]!, player, {
    x: object.x + object.width / 2, y: object.y + object.height / 2,
  });
}
async function clickVisibleWorldPoint(
  page: Page,
  map: Definition["maps"][string],
  player: Player,
  point: { x: number; y: number },
) {
  const canvas = page.locator(".world-canvas canvas");
  const box = await canvas.boundingBox();
  expect(box).not.toBeNull();
  if (!box) throw new Error("The world canvas has no visible bounds");
  const zoom = Math.min(3, Math.max(1.5, Math.min(box.width / 380, box.height / 240)));
  const viewWidth = box.width / zoom, viewHeight = box.height / zoom;
  const bounds = map.render_bounds ?? { x: 0, y: 0, width: map.width, height: map.height };
  const rawLeft = player.tile_x * 16 + 8 - viewWidth / 2;
  const rawTop = player.tile_y * 16 + 2 - viewHeight / 2;
  const cameraLeft = map.camera_mode === "centered" ? rawLeft : Math.max(bounds.x * 16, Math.min((bounds.x + bounds.width) * 16 - viewWidth, rawLeft));
  const cameraTop = map.camera_mode === "centered" ? rawTop : Math.max(bounds.y * 16, Math.min((bounds.y + bounds.height) * 16 - viewHeight, rawTop));
  const x = (point.x - cameraLeft) * zoom;
  const y = (point.y - cameraTop) * zoom;
  expect(x).toBeGreaterThan(0);
  expect(y).toBeGreaterThan(0);
  expect(x).toBeLessThan(box.width);
  expect(y).toBeLessThan(box.height);
  await canvas.click({ position: { x, y } });
}

test("the eleven canonical Chinese building labels match retained Tiled objects and portal metadata", async ({ request }) => {
  const response = await request.get(definition.maps.town_hub!.asset!);
  expect(response.ok()).toBeTruthy();
  const map = await response.json() as TiledMap;
  const buildings = definition.maps.town_hub!.buildings;
  const objects = map.layers.flatMap(layer => layer.objects ?? []);
  expect(buildings).toHaveLength(11);
  const configuredObjectNames = new Set(buildings.map(building => building.object_name));
  expect(objects.filter(object => configuredObjectNames.has(object.name))).toHaveLength(11);
  expect(new Set(buildings.map(building => building.id)).size).toBe(11);
  for (const building of buildings) {
    expect(building.label).toBe(buildingNames[building.id]);
    expect(building.purpose.length).toBeGreaterThan(5);
    expect(objects.filter(object => object.name === building.object_name)).toHaveLength(1);
    expect(Number.isInteger(building.anchor.x)).toBe(true);
    expect(Number.isInteger(building.anchor.y)).toBe(true);
    expect(building.anchor.x).toBeGreaterThanOrEqual(0);
    expect(building.anchor.y).toBeGreaterThanOrEqual(0);
    expect(building.anchor.x).toBeLessThan(definition.maps.town_hub!.width);
    expect(building.anchor.y).toBeLessThan(definition.maps.town_hub!.height);
  }
  const entrance = definition.portals.find(portal => portal.id === "library_door")!;
  const exit = definition.portals.find(portal => portal.id === "library_exit")!;
  expect(entrance.label).toBe("图书馆入口");
  expect(entrance.action_label).toBe("进入图书馆");
  expect(exit.action_label).toBe("离开图书馆，返回小镇");
  expect(entrance.to.map_key).toBe(exit.from.map_key);
  expect(exit.to.map_key).toBe(entrance.from.map_key);
});

test("map preview, filters, WASD, library doors and cross-map guidance share one continuous world canvas", async ({ page, request }, testInfo) => {
  test.setTimeout(300_000);
  const email = process.env.E2E_TEACHER_EMAIL, password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(!email || !password, "Explicit isolated teacher credentials are required; no default users or mocked business APIs.");
  await page.emulateMedia({ reducedMotion: "reduce" });
  const pageErrors: string[] = [], assistantMessages: string[] = [];
  page.on("pageerror", error => pageErrors.push(error.message));
  page.on("request", outgoing => {
    if (outgoing.method() === "POST" && /\/api\/assistant\/sessions\/[^/]+\/messages$/.test(outgoing.url()))
      assistantMessages.push(outgoing.url());
  });
  const login = await request.post("/api/auth/login", { data: { email, password } });
  expect(login.ok()).toBeTruthy();
  const teacher = { Authorization: `Bearer ${(await login.json()).access_token}` };
  const created = await request.post("/api/classrooms", {
    headers: teacher, data: { name: `地图配置验收 ${randomUUID().slice(0, 8)}` },
  });
  expect(created.status()).toBe(201);
  const classroom = await created.json() as { id: number; join_code: string };
  const release = await request.put(`/api/teacher/classrooms/${classroom.id}/releases/179`, {
    headers: teacher, data: { status: "published", },
  });
  expect(release.ok(), await release.text()).toBeTruthy();
  await page.goto("/");
  await page.getByRole("button", { name: "初次到访？注册居民证" }).click();
  await page.getByLabel("名字", { exact: true }).fill("地图新居民");
  await page.getByLabel("邮箱", { exact: true }).fill(`${randomUUID()}@example.test`);
  await page.getByLabel("密码", { exact: true }).fill(`World-${randomUUID()}`);
  await page.getByRole("button", { name: "注册并进入", exact: true }).click();
  await page.getByLabel("班级加入码").fill(classroom.join_code);
  await page.getByRole("button", { name: "加入班级", exact: true }).click();
  const welcome = page.getByRole("dialog", { name: "欢迎回到小镇", exact: true });
  await welcome.getByRole("button", { name: "按任意键继续" }).click();
  await expect(welcome).toBeHidden();
  const token = await page.evaluate(() => sessionStorage.getItem("pythontopia-reborn-session"));
  expect(token).toBeTruthy();
  const student = { Authorization: `Bearer ${token}` };
  const world = () => readWorld(request, classroom.id, student);
  const canvas = page.locator(".world-canvas canvas");
  await expect(canvas).toHaveCount(1);
  const initialCanvas = await canvas.elementHandle();
  expect(initialCanvas).not.toBeNull();
  const checkCanvas = async () => {
    await expect(canvas).toHaveCount(1);
    expect(await initialCanvas!.evaluate(node => node.isConnected)).toBe(true);
    expect(await canvas.evaluate((node, original) => node === original, initialCanvas)).toBe(true);
  };
  await expect(page.getByRole("button", { name: "地点导航", exact: true })).toHaveCount(0);
  const navigation = await openMap(page);
  const playerMarker = navigation.getByLabel("玩家当前位置", { exact: true });
  await expect(playerMarker).toBeVisible();
  const initialMarker = await playerMarker.evaluate(element => ({ left: parseFloat((element as HTMLElement).style.left), top: parseFloat((element as HTMLElement).style.top) }));
  const initialPosition = (await world()).player;
  expect(initialMarker.left).toBeCloseTo((initialPosition.tile_x + 0.5) / definition.maps.town_hub!.width * 100, 1);
  expect(initialMarker.top).toBeCloseTo((initialPosition.tile_y + 0.5) / definition.maps.town_hub!.height * 100, 1);
  for (const building of definition.maps.town_hub!.buildings) {
    const entry = navigation.getByRole("button", { name: `选择${building.label}`, exact: true });
    await expect(entry).toBeVisible();
    await expect(entry).toContainText(building.label);
  }
  const beforePreview = (await world()).player;
  await expect(navigation.getByLabel("地图选择", { exact: true })).toHaveCount(0);
  await expect(navigation.getByRole("combobox")).toHaveCount(0);
  await navigation.getByRole("button", { name: "筛选人物", exact: true }).click();
  await expect(playerMarker).toBeVisible();
  await expect(navigation.getByRole("button", { name: "选择镇长 Py", exact: true })).toBeVisible();
  await expect(navigation.getByRole("button", { name: "选择图书管理员", exact: true })).toHaveCount(0);
  expect((await world()).player).toEqual(beforePreview);
  await navigation.getByRole("button", { name: "筛选建筑", exact: true }).click();
  await expect(playerMarker).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("town-map-workspace.png") });
  await navigation.getByRole("button", { name: "选择图书馆", exact: true }).click();
  await expect(navigation).toContainText(definition.portals.find(item => item.id === "library_door")!.blocked_message);
  await expect(navigation.getByRole("button", { name: "前往所选目标", exact: true })).toBeDisabled();
  await navigation.getByRole("button", { name: "关闭当前工作区", exact: true }).click();
  await expect(navigation).toBeHidden();
  await expect(page.locator(".world-canvas")).toBeFocused();

  const beforeWalk = (await world()).player;
  await expect(page.locator(".world-hint")).toHaveText("E · 交谈");
  const hintBounds = await page.locator(".world-hint").boundingBox();
  expect(hintBounds!.width).toBeLessThan(180);
  await page.keyboard.down("d");
  try {
    // The prompt must disappear while the key is still held, before movement stops.
    await expect(page.locator(".world-hint")).toBeHidden({ timeout: 2_000 });
  } finally { await page.keyboard.up("d"); }
  const immediateMap = await openMap(page);
  const movedMarker = immediateMap.getByLabel("玩家当前位置", { exact: true });
  await expect.poll(async () => movedMarker.evaluate(element => parseFloat((element as HTMLElement).style.left)), { timeout: 750 }).toBeGreaterThan(initialMarker.left);
  await immediateMap.getByRole("button", { name: "关闭当前工作区", exact: true }).click();
  await expect.poll(async () => (await world()).player.tile_x, { timeout: 10_000 }).toBeGreaterThan(beforeWalk.tile_x);
  await expect(page.locator(".world-canvas")).toBeFocused();

  // Click an actual building sprite, rather than submitting a movement or event API request.
  const tiled = await (await request.get(definition.maps.town_hub!.asset!)).json() as TiledMap;
  const closedBuilding = definition.maps.town_hub!.buildings.find(building => building.id === "cottage_d")!;
  await clickVisibleBuilding(page, tiled, (await world()).player, closedBuilding);
  await expect.poll(async () => {
    const position = (await world()).player;
    return Math.abs(position.tile_x - closedBuilding.anchor.x) + Math.abs(position.tile_y - closedBuilding.anchor.y);
  }, { timeout: 40_000 }).toBeLessThanOrEqual(2);
  await expect(page.locator(".world-hint")).toBeHidden();
  await page.keyboard.press("e");
  await expect(page.locator(".world-hint")).toBeHidden();
  await expect(page.locator(".toast-layer")).not.toContainText("未开放");
  expect((await world()).player.map_key).toBe("town_hub");
  await page.screenshot({ path: testInfo.outputPath("closed-building-feedback.png") });

  const entrance = definition.portals.find(portal => portal.id === "library_door")!;
  const exit = definition.portals.find(portal => portal.id === "library_exit")!;
  const denied = await request.post("/api/world/transition", {
    headers: student, data: { classroom_id: classroom.id, portal_id: entrance.id },
  });
  expect(denied.status()).toBe(422);
  expect((await denied.json()).detail).toBe("请先到达对应地图入口附近");
  expect((await world()).player.map_key).toBe("town_hub");
  await checkCanvas();

  await findNpc(page, "镇长 Py");
  await finishPlainStory(page);
  await expect.poll(async () => (await world()).flags).toContain("library_unlocked");
  const enterResponse = waitForTransition(page, entrance.id);
  await goToBuilding(page, "图书馆");
  await expectTransition(await enterResponse, entrance.to);
  await expect.poll(async () => (await world()).player.map_key).toBe("library");
  await expect(page.locator(".world-hint")).toHaveText("E · 返回");
  await expect(page.locator(".toast-layer p")).toHaveCount(0, { timeout: 8_000 });
  await checkCanvas();
  await page.screenshot({ path: testInfo.outputPath("library-exit-marker.png") });
  // A different real bookcase must open the same reading library, far from the old western point.
  const libraryArt = await (await request.get(definition.maps.library!.asset!)).json() as TiledMap;
  const cabinets = libraryArt.layers.flatMap(layer => layer.objects ?? []).filter(object =>
    object.properties?.some(property => property.name === "interaction_id" && property.value === "library_shelf"),
  );
  expect(cabinets.length).toBeGreaterThanOrEqual(8);
  const eastCabinet = cabinets.filter(object => object.y >= 80).sort((a, b) => b.x - a.x)[0]!;
  expect(eastCabinet).toBeTruthy();
  await clickVisibleWorldPoint(page, definition.maps.library!, (await world()).player, {
    x: eastCabinet.x + eastCabinet.width / 2, y: eastCabinet.y + eastCabinet.height / 2,
  });
  const readingLibrary = page.getByRole("dialog", { name: "游戏工作区", exact: true });
  await expect(readingLibrary).toBeVisible({ timeout: 20_000 });
  await expect(readingLibrary).toContainText("已开放资料");
  await expect(readingLibrary.getByRole("button", { name: /阅读$/ }).first()).toBeVisible();
  const atShelf = (await world()).player;
  expect(Math.abs(atShelf.tile_x - 4) + Math.abs(atShelf.tile_y - 4)).toBeGreaterThan(2);
  await readingLibrary.getByRole("button", { name: "关闭当前工作区", exact: true }).click();
  await expect(page.locator(".world-hint")).toHaveText("E · 阅读");
  const indoorNavigation = await openMap(page);
  await expect(indoorNavigation.getByLabel("玩家当前位置", { exact: true })).toBeVisible();
  await expect(indoorNavigation.getByRole("button", { name: "选择图书馆书架", exact: true })).toBeVisible();
  await expect(indoorNavigation.getByText("西侧书架", { exact: true })).toHaveCount(0);
  await expect(indoorNavigation.getByRole("combobox")).toHaveCount(0);
  await expect(indoorNavigation.getByRole("button", { name: "选择镇长 Py", exact: true })).toHaveCount(0);
  await expect(indoorNavigation.getByRole("button", { name: "选择返回小镇", exact: true })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("library-map-workspace.png") });
  const exitResponse = waitForTransition(page, exit.id);
  await indoorNavigation.getByRole("button", { name: "选择返回小镇", exact: true }).click();
  await indoorNavigation.getByRole("button", { name: "前往所选目标", exact: true }).click();
  await expectTransition(await exitResponse, exit.to);
  await expect.poll(async () => (await world()).player.map_key).toBe("town_hub");
  await checkCanvas();

  const reenter = waitForTransition(page, entrance.id);
  // A real canvas portal-arrow click must still work after DOM pointer-through guards.
  await clickVisibleWorldPoint(page, definition.maps.town_hub!, (await world()).player, {
    x: entrance.from.tile_x * 16 + 8, y: entrance.from.tile_y * 16 + 8,
  });
  await expectTransition(await reenter, entrance.to);
  const secondExit = waitForTransition(page, exit.id);
  await goToTarget(page, "建筑", "返回小镇");
  await expectTransition(await secondExit, exit.to);
  await findNpc(page, "镇长 Py");
  const reminder = page.getByRole("dialog", { name: "剧情对白", exact: true });
  await expect(reminder).toBeVisible({ timeout: 60_000 });
  await expect(reminder.locator(".dialogue-text")).toContainText("去找图书管理员吧");
  await finishPlainStory(page);
  await checkCanvas();
  const finalWorld = await world();
  const mayor = finalWorld.npcs.find(npc => npc.key === "mayor_py")!;
  expect(finalWorld.player.map_key).toBe(mayor.map_key);
  expect(Math.abs(finalWorld.player.tile_x - mayor.tile_x) + Math.abs(finalWorld.player.tile_y - mayor.tile_y)).toBeLessThanOrEqual(2);
  expect(assistantMessages).toEqual([]);
  expect(pageErrors).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath("returned-to-mayor.png") });
  const desktopNavigation = await openMap(page);
  await expect(desktopNavigation.getByLabel("玩家当前位置", { exact: true })).toBeVisible();
  await desktopNavigation.getByRole("button", { name: "筛选建筑", exact: true }).click();
  await expect(desktopNavigation.getByRole("button", { name: "选择图书馆", exact: true })).toBeVisible();
  const desktopMarker = await desktopNavigation.getByLabel("玩家当前位置", { exact: true }).boundingBox();
  const desktopStage = await desktopNavigation.locator(".map-stage").boundingBox();
  expect(desktopMarker!.x).toBeGreaterThanOrEqual(desktopStage!.x);
  expect(desktopMarker!.y).toBeGreaterThanOrEqual(desktopStage!.y);
  expect(desktopMarker!.x + desktopMarker!.width).toBeLessThanOrEqual(desktopStage!.x + desktopStage!.width);
  expect(desktopMarker!.y + desktopMarker!.height).toBeLessThanOrEqual(desktopStage!.y + desktopStage!.height);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("desktop-map-workspace.png") });
  await desktopNavigation.getByRole("button", { name: "关闭当前工作区", exact: true }).click();
  await expect(desktopNavigation).toBeHidden();
  await checkCanvas();
  await initialCanvas!.dispose();
});
