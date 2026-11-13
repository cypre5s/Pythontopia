import { test, expect, type Page } from "@playwright/test";
import { cp, rm } from "node:fs/promises";
import { resolve } from "node:path";
import { chooseOption, credentials, json } from "./helpers";
const stamp = Date.now().toString().slice(-9);
async function login(page: Page) {
  await page.goto("/teacher/");
  await page.getByLabel("学号", { exact: true }).fill(credentials.student_id);
  await page.getByLabel("密码", { exact: true }).fill(credentials.password);
  await page.getByRole("button", { name: "进入工作台" }).click();
  await expect(page.getByLabel("当前班级")).toBeVisible();
}
async function navigate(page: Page, name: string) {
  await page.getByRole("link", { name, exact: true }).click();
}
async function chooseFromGroup(page: Page, label: string, group: string, choice: string) {
  await page.getByRole("button", { name: label, exact: true }).click();
  await page.getByRole("menuitem", { name: group, exact: true }).press("ArrowRight");
  await page.getByRole("menuitem", { name: choice, exact: true }).click();
}
test("真实班级、学生、内容、编辑器与包往返", async ({ page }, info) => {
  await login(page);
  page.on("dialog", (d) => d.accept());
  await page
    .getByRole("button", { name: "创建班级", exact: true })
    .first()
    .click();
  await page.getByLabel("班级名称").fill(`后台验收 ${stamp}`);
  await chooseOption(page, page.getByLabel("课程来源"), "创建空课程");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "创建班级", exact: true })
    .click();
  await expect(page.getByText("此班级的课程独立维护，修改不会同步到其他班级。", { exact: true })).toBeVisible();
  await navigate(page, "学生账号");
  await page.getByRole("button", { name: "添加学生", exact: true }).click();
  await page.getByLabel("学号", { exact: true }).fill(`00${stamp}`);
  await page.getByLabel("姓名", { exact: true }).fill("验收学生甲");
  await page.getByRole("button", { name: "创建学生", exact: true }).click();
  await expect(
    page.getByRole("cell", { name: `00${stamp}`, exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "导入学生", exact: true }).click();
  await page.getByLabel("CSV 或 XLSX 文件").setInputFiles({
    name: "名册.csv",
    mimeType: "text/csv",
    buffer: Buffer.from(`学号,姓名\n01${stamp},验收学生乙\n`),
  });
  await expect(page.getByText(/将新建 1 人/)).toBeVisible();
  await page.getByRole("button", { name: "确认导入", exact: true }).click();
  await expect(
    page.getByRole("cell", { name: `01${stamp}`, exact: true }),
  ).toBeVisible();
  await page.screenshot({ path: info.outputPath("students.png"), animations: "disabled" });
  await navigate(page, "课程结构");
  await page.getByRole("button", { name: "新增章节", exact: true }).click();
  await page.getByLabel("名称", { exact: true }).fill("验收第一章");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "保存", exact: true })
    .click();
  await expect(
    page.getByRole("cell", { name: "验收第一章", exact: true }),
  ).toBeVisible();
  await navigate(page, "资料库");
  await page.getByRole("button", { name: "上传资料", exact: true }).click();
  await page.getByLabel("资料名称").fill("验收讲义");
  await page.getByLabel("文件", { exact: true }).setInputFiles({
    name: "讲义.txt",
    mimeType: "text/plain",
    buffer: Buffer.from("欢迎学习 Python。这里是真实上传转换后的讲义。"),
  });
  await page
    .getByRole("dialog")
    .getByText("验收第一章", { exact: true })
    .click();
  await page.getByRole("button", { name: "保存资料", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(async () => {
    await page.reload();
    await expect(page.getByLabel("预览验收讲义")).toBeEnabled();
  }).toPass({ timeout: 30000 });
  await page.getByLabel("预览验收讲义").click();
  await expect(
    page.getByText("欢迎学习 Python。这里是真实上传转换后的讲义。", {
      exact: true,
    }),
  ).toBeVisible();
  await page.getByRole("button", { name: "关闭", exact: true }).click();
  await navigate(page, "题库");
  await page.getByRole("button", { name: "新增题目", exact: true }).click();
  await page.getByLabel("题目名称").fill("验收单选题");
  await page.getByLabel("题干").fill("哪一个是 Python 输出函数？");
  await page.getByLabel("选项1", { exact: true }).fill("print");
  await page.getByLabel("选项2", { exact: true }).fill("write");
  await page.getByLabel("选项1为正确答案").check();
  await page
    .getByRole("dialog")
    .getByText("验收第一章", { exact: true })
    .click();
  await chooseOption(page, page.getByRole("dialog").getByLabel("状态", { exact: true }), "可用");
  await page.getByRole("button", { name: "保存题目", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByText("验收单选题", { exact: true })).toBeVisible();
  await navigate(page, "教学流程");
  await page
    .getByRole("tab", { name: "助教教学角色", exact: true })
    .click();
  await page.getByRole("button", { name: "新增教学角色", exact: true }).click();
  await page.getByLabel("角色名称").fill("验收向导");

  await page.getByRole("tab", { name: "地图交互与开放规则", exact: true }).click();
  await page.getByRole("button", { name: "角色摆放与出现", exact: true }).click();
  const placement = page.getByRole("group", { name: "教学角色摆放地图" });
  const bounds = await placement.boundingBox();
  await placement.click({ position: { x: bounds!.width * 22.5 / 50, y: bounds!.height * 36.5 / 40 } });
  await page
    .getByRole("tab", { name: "公共与章节流程", exact: true })
    .click();
  await page.getByRole("button", { name: "新建流程", exact: true }).click();
  await page.getByLabel("流程名称").fill("验收欢迎流程");
  await page.getByRole("button", { name: "关闭", exact: true }).click();
  await page.getByRole("button", { name: "触发入口", exact: true }).click();
  await chooseFromGroup(page, "选择入口位置", "Pythontopia 小镇", "验收向导");
  const entryCard = page.locator(".react-flow__node").filter({ hasText: "验收向导" });
  await page.getByRole("button", { name: "校验", exact: true }).click();
  await page.getByRole("region", { name: "校验结果" }).getByRole("button", { name: "定位", exact: true }).first().click();
  await expect(entryCard).toBeFocused();
  await page.getByRole("button", { name: "对白", exact: true }).click();
  const dialogueCard = page.locator(".react-flow__node").filter({ hasText: "请输入对白" });
  await dialogueCard.click();
  await expect(page.getByLabel("对白 1", { exact: true })).toHaveCount(0);
  await dialogueCard.click({ button: "right" });
  await chooseFromGroup(page, "选择说话 NPC", "Pythontopia 小镇", "验收向导");
  await expect(page.getByLabel("说话人", { exact: true })).toHaveValue("验收向导");
  await page.getByLabel("对白 1", { exact: true }).fill("欢迎来到测试课程。");
  await page.getByRole("button", { name: "添加对白", exact: true }).click();
  await page.getByLabel("对白 2", { exact: true }).fill("这个流程由教师编辑。请开始学习吧。");
  await page.getByRole("button", { name: "关闭属性", exact: true }).click();
  await page.getByRole("button", { name: "打开页面", exact: true }).click();
  await expect(page.locator(".react-flow__edge")).toHaveCount(0);
  await page.getByRole("button", { name: "自动排列", exact: true }).click();
  const welcomeCard = page.locator(".react-flow__node").filter({ hasText: "欢迎来到测试课程" });
  const pageCard = page.locator(".react-flow__node").filter({ hasText: "打开图书馆" });
  const endCard = page.locator(".react-flow__node").filter({ hasText: "流程结束" });
  await pageCard.click({ button: "right" });
  await expect(page.getByLabel("打开页面", { exact: true })).toContainText("图书馆");
  await expect(page.getByLabel("提示文字", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "关闭属性", exact: true }).click();
  await entryCard.getByLabel("输出：否则").dragTo(welcomeCard.getByLabel("输入"));
  await welcomeCard.getByLabel("输出：下一步").dragTo(pageCard.getByLabel("输入"));
  await pageCard.getByLabel("输出：下一步").dragTo(endCard.getByLabel("输入"));
  await expect(page.locator(".react-flow__edge")).toHaveCount(3);
  const write = page.waitForRequest((request) => request.method() === "PUT" && request.url().endsWith("/authoring"));
  await page.getByRole("button", { name: "保存课程", exact: true }).click();
  const saved = (await write).postDataJSON();
  const savedNodes = saved.flows.find((flow: { title: string }) => flow.title === "验收欢迎流程").content.nodes;
  const savedPage = savedNodes.find((node: { type: string }) => node.type === "action");
  expect(savedPage).toMatchObject({ action: "open_workspace", workspace: "library" });
  expect(savedPage).not.toHaveProperty("text");
  expect(savedNodes.find((node: { type: string }) => node.type === "dialogue").next).toBe(savedPage.id);
  await expect(
    page.getByRole("button", { name: "保存课程", exact: true }),
  ).toBeDisabled();
  await expect(page.getByLabel("选择流程")).toContainText("验收欢迎流程");
  await page.screenshot({ path: info.outputPath("flow-editor.png"), animations: "disabled" });
  await page.getByRole("button", { name: "流程设置与入口", exact: true }).click();
  await page.getByLabel("流程名称").fill("待放弃的流程名");
  await page.getByRole("button", { name: "关闭", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("未保存");
  await page.getByRole("button", { name: "取消编辑", exact: true }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "放弃修改", exact: true }).click();
  await expect(page.getByLabel("选择流程")).toContainText("验收欢迎流程");
  await expect(page.getByRole("button", { name: "保存课程", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "预览", exact: true }).click();
  await page.getByRole("button", { name: "执行只读预览", exact: true }).click();
  await expect(page.getByText(/预览停止/)).toBeVisible();
  await page.getByRole("button", { name: "关闭", exact: true }).click();
  await page
    .getByRole("tab", { name: "地图交互与开放规则", exact: true })
    .click();
  await page
    .getByRole("button", { name: /选择地图对象/ })
    .first()
    .click();
  await page.getByLabel("交互名称").fill("验收地图入口");
  await chooseOption(page, page.getByLabel("触发流程"), "验收欢迎流程");
  await page.getByRole("button", { name: "保存课程", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "保存课程", exact: true }),
  ).toBeDisabled();
  await page
    .getByRole("tab", { name: "任务目标与依赖", exact: true })
    .click();
  await page.getByRole("button", { name: "新建任务", exact: true }).click();
  await page.getByLabel("任务名称").fill("验收可选任务");
  await page.getByRole("button", { name: "关闭属性", exact: true }).click();
  await page.getByRole("button", { name: "与角色交谈", exact: true }).click();
  await page.getByRole("button", { name: "回报角色", exact: true }).click();
  await expect(page.locator(".react-flow__edge")).toHaveCount(0);
  await page.getByRole("button", { name: "自动排列", exact: true }).click();
  const talk = page.getByLabel(/^节点 \d+：与角色交谈$/);
  const report = page.getByLabel(/^节点 \d+：回报角色$/);
  await talk.getByLabel("输出：添加后续目标").dragTo(report.getByLabel("输入"));
  await expect(page.locator(".react-flow__edge")).toHaveCount(1);
  await report.click({ button: "right" });
  await expect(page.getByLabel("目标标题")).toHaveValue("回报角色");
  await chooseOption(page, page.getByLabel("前置依赖关系"), "任一前置完成（ANY）");
  await page.getByRole("button", { name: "关闭属性", exact: true }).click();
  const questWrite = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith("/authoring/validate"));
  await page.getByRole("button", { name: "校验", exact: true }).click();
  const savedQuest = (await questWrite).postDataJSON().quests.find((quest: { title: string }) => quest.title === "验收可选任务");
  const [talkGoal, reportGoal] = savedQuest.definition.nodes;
  expect(talkGoal.dependencies).toEqual([]);
  expect(reportGoal).toMatchObject({ dependencies: [talkGoal.id], aggregation: "ANY" });
  await expect(page.getByRole("region", { name: "校验结果" })).toContainText("流程对白");
  await page.getByRole("button", { name: "取消编辑", exact: true }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "放弃修改", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "保存课程", exact: true }),
  ).toBeDisabled();
  await page.getByRole("tab", { name: "助教教学角色", exact: true }).click();
  await page.getByRole("button", { name: "新增教学角色", exact: true }).click();
  await page.getByRole("button", { name: "保存课程", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "保存课程", exact: true }),
  ).toBeDisabled();
  // 课程默认角色已从课程编辑器的角色页拆到“课程设置”：发布后在设置里再指定。
  await navigate(page, "课程设置");
  await chooseOption(page, page.getByLabel("课程默认答疑角色", { exact: true }), "新的教学角色");
  await page.getByRole("button", { name: "保存设置", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("课程设置已保存");
  await navigate(page, "教学管理");
  await page.getByRole("button", { name: "设置", exact: true }).click();
  await page.getByRole("button", { name: "保存安排", exact: true }).click();
  await expect(page.getByText("章节安排已保存")).toBeVisible();
  await navigate(page, "课程包");
  const exportResponse = page.waitForResponse((response) => response.url().endsWith("/course/export"));
  await page.getByRole("button", { name: "导出课程包", exact: true }).click();
  const exported = await json<{ directory: string; path: string }>(await exportResponse);
  await expect(page.getByText(`课程已导出到 ${exported.path}`, { exact: true })).toBeVisible();
  const exchangeRoot = process.env.REBORN_COURSE_EXCHANGE_ROOT;
  if (!exchangeRoot) throw new Error("REBORN_COURSE_EXCHANGE_ROOT must point to the isolated browser test exchange directory.");
  expect(exported.directory).toMatch(/^export-\d+-.+-\d{8}-\d{6}-[a-f\d]{8}$/);
  const sharedCopy = resolve(exchangeRoot, `browser-roundtrip-${exported.directory}`);
  await page
    .getByRole("button", { name: "创建班级", exact: true })
    .first()
    .click();
  await page.getByLabel("班级名称").fill(`导入验收 ${stamp}`);
  await chooseOption(page, page.getByLabel("课程来源"), "创建后导入课程包");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "创建班级", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(
    page.getByLabel("当前班级"),
  ).toContainText(`导入验收 ${stamp}`);
  await cp(resolve(exchangeRoot, exported.directory), sharedCopy, { recursive: true, force: false, errorOnExist: true });
  try {
    await page.getByRole("button", { name: "刷新列表", exact: true }).click();
    await page.getByRole("button", { name: `预览 browser-roundtrip-${exported.directory}`, exact: true }).click();
    await expect(page.getByRole("heading", { name: "导入差异", exact: true })).toBeVisible();
  } finally {
    await rm(sharedCopy, { recursive: true, force: true });
  }
  await page.getByRole("checkbox", { name: "我已核对差异，确认更新当前班级课程" }).click();
  await page.getByRole("button", { name: "确认导入", exact: true }).click();
  await expect(
    page.getByText("课程包已导入，班级课程已更新。", { exact: true }),
  ).toBeVisible();
  await navigate(page, "课程结构");
  await expect(
    page.getByRole("cell", { name: "验收第一章", exact: true }),
  ).toBeVisible();
  await page.getByLabel("编辑验收第一章").click();
  await page.getByLabel("名称", { exact: true }).fill("仅导入班修改");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "保存", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await chooseOption(page, page.getByLabel("当前班级"), `后台验收 ${stamp}`);
  await expect(
    page.getByRole("cell", { name: "验收第一章", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "仅导入班修改", exact: true }),
  ).toHaveCount(0);
  await chooseOption(page, page.getByLabel("当前班级"), `导入验收 ${stamp}`);
  await navigate(page, "学生账号");
  await expect(
    page.getByText("没有匹配的学生", { exact: false }),
  ).toBeVisible();
  await page.screenshot({ path: info.outputPath("imported-class.png") });
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "学生账号", exact: true }),
  ).toBeVisible();
});
