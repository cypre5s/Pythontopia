# Pythontopia Beta 演示 Checklist (R-14)

> 当前演示以 R-14「世界收缩方案 A」为准：主线只跑单一中央小镇 `town_hub`，`mine_south` 仅作为 Bug 矿洞支线；历史 R-12 的 5 图大世界、农场/作物、背包/货币/商店不再作为演示验收项。
>
> - 本 checklist 覆盖 **R-14 单 hub** 端到端 QA 路径（town_hub + Bug 矿洞支线 + 真实学习闭环）。
> - 自动化烟雾测试以根目录 `./dev.sh`、后端/前端/graph/rag 测试命令和真实浏览器走查为准；本文件用于人工演示前最终走查。
> - 所有 ✓ 项请用 `[x]` 标记；遇到偏离请补充 `备注:` 说明实际行为，**不要静默通过**。

---

## 0. 前置条件

- [ ] 数据库已迁移：`cd backend && mvn -s .mvn/settings.xml test` 能在空库 Testcontainers 上跑通 Flyway（当前迁移至 V139）。
- [ ] 后端启动：`cd backend && mvn -s .mvn/settings.xml spring-boot:run`，等待 stdout `Started PythontopiaApplication`，端口按本地配置可访问。
- [ ] 前端启动：`cd frontend && npm run dev`，等待 `VITE ready at http://localhost:5173`（`predev` 钩子会同步 NPC 与 course43 数据，请确认无 sync 报错）。
- [ ] 测试账号已就绪：
  - 学生：通过 `/register` 创建，或使用 `bash deploy/scripts/seed_demo_classroom.sh` 本次运行打印的一次性凭据。
  - 教师：使用 seed 脚本本次打印的一次性凭据（邀请码从本地 `.env` 读取）。
- [ ] 浏览器：Chrome / Edge 最新版；DevTools Network / Console 全开；建议宽度 ≥1366。
- [ ] 资产同步：`frontend/public/data/npcs/` 与 `data/npcs/` 一致；R-14 主线 NPC 均能在 `town_hub` 内触达。

---

## A. 学生视角主路径（9 步）

### A1. 登录与 GameBootstrap

- [ ] 访问 `http://localhost:5173/` → 自动 redirect 到 `/login`（未登录时由 `router.beforeEach` 强制跳转）。
- [ ] 表单显示 `用户名` 与 `密码` 标签，按钮文字 `进入小镇`。
- [ ] 输入本次注册或 seed 脚本生成的学生凭据 → 点击 `进入小镇`。
- [ ] 登录成功后跳转链路：`/login → /town`（兼容旧路由）→ `router.ts` 重定向 `/world`。
- [ ] Vue 路由稳定在 `/world`；地址栏 URL 不再变动。
- [ ] DevTools Network 看到关键请求：
  - `POST /api/auth/login` → 200，返回 `accessToken`。
  - `POST /api/game/bootstrap` → 200，body 含 `world / player / quests / mastery / profile / path / skills / calendar / pendingEvents / npcStates / activeSlot / saveSlots`，不要求 crops / currency / shop。
- [ ] Phaser 单 Game 实例创建：DevTools Elements 中只有 1 个 `<canvas>` 元素。
- [ ] BGM 起：BGM `town_theme` 淡入（参考 `frontend/src/utils/celebrate.ts` 或 BGM adapter）。
- [ ] Sara 角色在 `town_hub` 像素 (400, 320) ≈ tile (25, 20) spawn，阴影 ellipse 跟随。

### A2. town_hub 探索与 8 方向移动

- [ ] 单键移动：W / A / S / D / 方向键，4 向丝滑（tween 平滑而非 tile snap）。
- [ ] 八向移动：W+D / W+A / S+D / S+A 同时按，角色沿对角线 45° 移动。
- [ ] 输入降级到后端：角色站立后 `POST /api/world/player` 发送的 `direction` 字段属于 `UP / DOWN / LEFT / RIGHT` 之一（不出现 8 向值）；可在 Network 中验证 `toServerDirection` 已生效。
- [ ] camera setZoom(2)，并平滑跟随 Sara（移动时无明显抖动）。
- [ ] 像素字体 `PythontopiaPixel` 渲染：覆盖 Phaser 中所有 NPC 名牌 + Vue 顶部状态条。

### A3. 与 mayor_py 对话

- [ ] `town_hub` 内 `mayor_py` 站位 tile (15, 11)（参考 `data/npcs/mayor_py/meta.json`）。
- [ ] Sara 走到该 tile 半径 1 范围内 → NPC 上方出现 `F` 提示。
- [ ] 按 `F` → `DialogModal` / `DialogTree` 9-slice 弹起；标题显示 `镇长 Py` + 头像 `portrait_mayor_py.png`。
- [ ] 首句文本以打字机渲染（速度 ~30 字符/秒）。
- [ ] 单击对话面板 → 立即跳过打字机并显示选项列表。
- [ ] 选项中存在引导 town/path 介绍的分支（如 "请告诉我这里有什么？" / "我该从哪开始？"）。
- [ ] 点选项后切到下一个对话节点；若分支带 `effects: friendship +1` 则右上 ❤ icon +1（实际持久化到 `npc_dialog_state.friendship`）。
- [ ] 按 `ESC` 关闭对话 → 角色控制权回到 Phaser，背景输入恢复。

### A4. R-14 单 hub 与 Bug 矿洞支线

> 主线不再要求跨 5 图奔波；所有学习入口与 NPC 收进 `town_hub`，Bug 矿洞作为 `mine_south` 支线入口。

- [ ] `frontend/public/assets/maps/index.json` 只有 `town_hub` 与 `mine_south`；`town_hub` 宽高为 50×40 且无主线出口。
- [ ] `town_hub` 内可以进入画像屋、图书馆、复盘大厅、资源工坊、Bug 矿洞、竞技场、训练场等主线建筑。
- [ ] 从 Bug 矿洞入口进入 `mine_south` 支线，BGM crossfade 切到 `bug_mine` 紧张曲，环境 tint 偏暗。
- [ ] 返回小镇后仍保持单 Phaser Game 实例，无 404 / 5xx。

### A5. 图书馆阅读 561 页课件

- [ ] 在 `town_hub` 走近图书馆建筑 → 按 `F` 或鼠标点击建筑 → `router.push('/library')`。
- [ ] `LibraryPage` 三栏布局：左 7 章树 / 中 页内容 / 右 RAG 检索框。
- [ ] 切换第 5 章「控制流」→ 树节点高亮 + 中栏跳到该章首页。
- [ ] 翻页：`▶` / `◀` 翻页流畅，页码计数从 1 递增直至 561。
- [ ] RAG 检索框输入 `for 循环` → 命中片段 + 引用页码（如有真接通 `pythontopia-rag` 服务）；若服务未拉起，UI 显示明确的"服务未就绪"提示而非空数据。

### A6. 题库做题

- [ ] 回 `/world` → 在 `town_hub` 进入训练场/教室入口 → `router.push('/training')` 或题目入口。
- [ ] `ProblemListPage` 渲染 39 题；过滤器：KC / 难度 / 状态（通过/失败/未做）。
- [ ] 选 KC `loop_for_while` → 列表只显示绑定该 KC 的题。
- [ ] 点 `去做` → `router.push('/training?problemId=<id>')`。
- [ ] `TrainingPage` 显示：题面 / 代码编辑器（Monaco 或类似）/ Pyodide 预览按钮 / 服务端提交按钮。

### A7. 失败提交 → Bug 矿洞

- [ ] 在 `TrainingPage` 故意写错误代码（如缺少 return）→ 点 `服务端提交`。
- [ ] `POST /api/submission` → 200，后端 `SubmissionService` 返回 `verdict: FAILED` 或 `WRONG_ANSWER`。
- [ ] UI 显示 `失败` 状态 + 错误诊断；Toast/提示中出现 "进入 Bug 矿洞复盘" 跳转入口。
- [ ] 点入口 → 回到 `/world` 进 `mine_south`，副本入口物件出现，可与 `foreman_bug` 对话开矿洞副本。
- [ ] DB 层 `practice_submission` 表写入一条 FAILED 记录（如可访问 PostgreSQL，可执行 `SELECT verdict, problem_id, created_at FROM practice_submission ORDER BY id DESC LIMIT 1;`）。

### A8. 任务完成 with evidence 强校验

- [ ] 回 `/board`：`PathTreeTab` + `ClassroomQuestsTab` 两个 tab 均渲染（无白屏）。
- [ ] `PathTreeTab` 中点击任一 KC 节点 → deep link 到 R-14 学习入口：
  - 课件页（`/library?page=...`）/ 资源工坊（`/workshop?kc=...`）/ 训练场（`/training?...`）/ 复盘大厅。
- [ ] 切到 `ClassroomQuestsTab`，选一条 `IN_PROGRESS` 任务 → 点 `提交完成`。
- [ ] 弹出 `evidence` 选择面板：
  - 必选类型与任务 `required_evidence_type` 匹配（submission / resource / page_read / kc_mastery_delta）。
  - 列表只显示当前学生最近成功 submission / 已生成 resource / 已读页 / 已涨 mastery。
- [ ] 选最近通过的 submission → 提交 → `POST /api/learner/quests/{id}/complete` 200，任务状态翻到 `COMPLETED`，奖励 toast 弹出。
- [ ] 反例：故意不选 evidence 提交 → 后端返回 400，错误信息明确（如 "需要选择一条已通过的提交作为证据"），且任务状态保持原样。
- [ ] 进一步反例：传一条不属于该 KC 的 submission → 后端拒绝并提示 KC 不匹配。

### A9. 睡觉推进一天

- [ ] 入口 A：玩家回家（hub 内的卧室建筑），按 `F` 触发睡觉。
- [ ] 入口 B（兜底）：DevTools Console `fetch('/api/world/sleep', {method:'POST', credentials:'include'})`。
- [ ] `WorldAdvanceController.sleep` 返回：`day_index +1`、`weekday` / `season` 推进、`weather` 随机切换。
- [ ] 屏幕黑屏过场 + 第二天画面 tint 早晨 `0xfff4d0`。
- [ ] 日历 UI 显示 `day_index = 2`（首日为 1）。
- [ ] NPC 调度位移：周三 mayor_py 应只在 town_hub (16, 12)，无 campus_east 出现；其他 weekday 走 `meta.json` 中的 schedule。
- [ ] 天气：晴 → 雨 / 雪（依季节）随机切换；BGM 不中断只 crossfade。

### A10. 像素 polish 走查（贯穿全程）

- [ ] 角色阴影 ellipse 始终跟随脚下；不随 `setDepth` 错位。
- [ ] camera setZoom(2)，平滑跟随；切图时 camera 复位无瑕疵。
- [ ] 中文像素字体 `PythontopiaPixel` 在 Phaser Text、Vue 顶栏、对话框中全部应用，无回退到默认 sans。
- [ ] BGM crossfade：`town_theme ↔ bug_mine_theme`，过渡时长 ~1.5s，无爆音。
- [ ] SFX：脚步、点击、对话推进、任务完成 jingle 各就各位（参考 `frontend/public/assets/pixel/sfx/`）。

---

## B. 教师视角主路径（4 步）

### B1. 教师登录与 dashboard

- [ ] 登录 seed 脚本本次打印的一次性教师账号 → 跳转 `/teacher`，看到「我的班级」列表 + 「新建班级」按钮。

### B2. 创建班级与拉学生

- [ ] 点 `新建班级` → 输入班级名（如 `R-12 演示班`）→ 提交。
- [ ] 后端返回 `joinCode`（6-8 位字符），列表立刻出现该班级。
- [ ] 把 `joinCode` 给学生 → 学生 `/board` 输入加入 → 班级 detail 学生数 +1。

### B3. 发布班级任务（带 evidence 要求）

- [ ] 进 `/teacher/classroom/:id` → 点 `新建任务`。
- [ ] 表单含：标题 / KC 多选 / `required_evidence_type`（默认 submission） / `target_problem_ids` / 截止时间。
- [ ] 提交 → 学生端 `ClassroomQuestsTab` 立刻刷新看到该任务（refresh interval ≤ 30s 或显式拉一次）。
- [ ] 学生端任务卡片显示 KC 列表、必需 evidence 类型、截止时间。

### B4. KC 热力图 / 班级看板

- [ ] 班级 detail → 切到 `KC 热力图` tab → 看到 65 KC 网格，颜色深浅 = 班级平均 mastery。
- [ ] hover 任一 KC → tooltip 显示「平均 mastery / 已掌握学生数 / 高频误区 Top3」。
- [ ] 点 KC → 钻取页显示该 KC 关联的最近失败 submission 摘要（匿名编号，参考 `docs/beta_acceptance_checklist.md` 教育验收 #5）。
- [ ] 学生在 A7-A8 完成的提交在班级看板对应 KC 的 mastery 平均值有可见变化（刷新后）。

---

## C. 验证通过标准

- 学生路径 9 步、教师路径 4 步必须全部 `[x]`。
- 任一步出现 5xx、白屏、伪造课件引用、Pyodide 顶替服务端判题、纯前端假数据 → **演示阻断**，写入 `docs/release/known_issues.md`。
- 完成全部勾选 → 在本文件底部填写：

```
Run by:     ____________________
Run date:   ____________________
Backend ver: git rev-parse HEAD
Frontend ver: git rev-parse HEAD
Overall:    [ ] PASS  [ ] FAIL
Notes:
```

---

## D. 已知遗留（R-12 闭环，R-13/R-14 处理）

> 演示前请向观众明确以下「不在本轮交付范围」的能力，避免误导提问。详见 `docs/release/r12/r12_release_notes.md` 与 `docs/sv-progress.md`「遗留 TODO」段。

### D1. Android 端覆盖度

- [ ] Android demo 仅覆盖 `town_hub` + 8 端点（auth / bootstrap / npc state+talk / calendar / inventory / sleep / player patch）。
- [ ] 4 张外围地图（farm_north / mine_south / campus_east / port_west）渲染与场景切换在 Android 上**未实现**。
- [ ] Inventory / Skill / Shop 完整 UI 仅 Web 端可玩；Android 留待 R-13/R-14。

### D2. RAG 检索学生权限

- [ ] `pythontopia-rag` 服务端 `/rag/query` 现仅教师 / 管理员可调；学生在 `LibraryPage` 右栏检索框可能命中"服务未就绪"占位。
- [ ] R-13 计划：backend 代理 + 学生权限解锁 + 课件页码超链接回跳。

### D3. 教师课堂直播 / 实时大屏

- [ ] 班级 KC 热力图当前为 **5 分钟 snapshot 缓存**，非实时推送。
- [ ] R-13 计划：SSE / WebSocket 推送 + 大屏布局；当前可演示 hover tooltip + 钻取页。

### D4. 多语言 / iOS

- [ ] UI 文本全中文，i18n 框架未引入；英文 demo 暂不可演示。
- [ ] iOS 客户端在 R-14+ 评估，不在 Beta 范围。

### D5. 跨地图 8 方向 DTO

- [ ] 后端 `PlayerStatePatch.direction` 已支持 8 方向枚举（V113），Android DTO 已对齐。
- [ ] 前端 `toServerDirection` 在写库时仍降级为 4 向（R-12 既定规范默认）；R-13 评估是否解锁前端真 8 向写库。

### D6. 教师班级公告板 quest 创建表单

- [ ] B3 步骤的「新建任务」表单功能可用，但部分高级配置（如 KC 多选 + evidence_type 复合校验）UI 体验仍为 R-13 优化目标。
- [ ] 当前演示推荐使用 `bash deploy/scripts/seed_demo_classroom.sh` 预置班级任务，再以教师身份编辑。

### D7. 自动化烟雾测试脚本

- [x] **已交付**：`scripts/r12-smoke-test.sh`（W4-DEMO-DOC）覆盖 backend 单测 / frontend typecheck+vitest / 资产 / 地图 / NPC / migration 7 项断言；演示前建议执行一次确认环境健全。
