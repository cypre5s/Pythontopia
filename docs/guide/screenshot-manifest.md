# 引导手册截图清单（screenshot manifest）

本文档是 `frontend/public/guide/screenshots/` 下 29 张引导截图的唯一权威说明：每张图的页面路径、前置状态、稳定等待条件、遮蔽规则与重拍方法。

- 采集套件：`frontend/e2e/guideScreenshots.spec.ts`（Playwright，chromium 单 worker 串行）
- 会话辅助：`frontend/e2e/support/demoSession.ts`（真实 API 注册/登录，注入 localStorage，无任何静态伪造）
- 配置：`frontend/playwright.config.ts`（`testDir` 固定为 `e2e/`，与 vitest 的 `tests/` 严格分离）
- 视口：统一 **1440x900**，`deviceScaleFactor=1`，截图先输出 PNG（中间产物在 `frontend/e2e/.artifacts/`，每次运行自动清理），再用 sharp 转 WebP（quality 92，保证面板小字可读）
- 失败即报错：任一页面不可达（被踢回登录页 / 加载失败 / 错误态可见）、输出文件缺尺寸或小于 4KB，对应测试都会失败

## 环境变量契约

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `BASE_URL` | 否 | 目标站点，默认 `http://127.0.0.1:5173`（vite dev，`/api` 代理到后端 `:18080`） |
| `PYT_E2E_USERNAME` / `PYT_E2E_PASSWORD` | 二选一¹ | 学生演示账号凭证（直接登录） |
| `PYT_E2E_REGISTER` | 二选一¹ | 置 `1` 时通过 `POST /api/auth/register` 注册临时演示账号：若同时给了 `PYT_E2E_USERNAME/PASSWORD` 按该凭证注册（已存在则直接登录），否则生成一次性随机账号 |
| `PYT_E2E_TEACHER_USERNAME` / `PYT_E2E_TEACHER_PASSWORD` | 第 12、21 张² | 教师演示账号凭证（直接登录） |
| `PYT_E2E_TEACHER_INVITE_CODE` | 第 12、21 张² | 配合 `PYT_E2E_REGISTER=1` 注册临时教师号，须与后端 `PYT_TEACHER_INVITE_CODE` 一致 |

¹ 学生会话两条路径至少满足一条，否则学生视角截图全部失败。
² 教师会话两条路径至少满足一条，否则 `12-teacher-classroom` 与 `21-teacher-board` 失败（不会用学生号凑数）。

凭证只经环境变量传入，绝不写死在源码；登录失败的报错信息不回显用户名/密码。

## 通用前置状态

所有学生视角截图（01–11、13–20、22–29）共用同一套注入逻辑：

1. 通过真实 API 拿到 JWT 会话（注册或登录，见上表）；
2. `context.addInitScript` 在页面脚本执行前写入 `pythontopia.token` / `pythontopia.expiresAt` / `pythontopia.user` 三个 localStorage key（与玩家真实登录后的持久化完全一致，应用启动时还会向 `/api/auth/refresh` 换发校验）；
3. 预置 `pyt_first_world_visit=1`、`pyt_helped_v1=1`，抑制首次进入小镇的新手帮助弹窗与镇长自动对话（等价于「玩家已点掉过引导」的真实状态），保证拍到的是页面本身。

全局遮蔽（每张图都生效）：`.header-status__name`（顶栏登录昵称）、`time` 元素（任何真实时间戳）。遮蔽用 Playwright `mask` 实现，盖深色块（`#10131a`），不改动页面 DOM。

## 29 张截图明细

| # | 文件名 | 页面路径 | 进入方式与前置状态 | 稳定等待条件 | 额外遮蔽 |
| --- | --- | --- | --- | --- | --- |
| 01 | `01-town-hub.webp` | `/world` | 学生会话 + 引导抑制 | `hud-shell` 可见 → 加载遮罩 `.world-load-overlay` 从 DOM 移除（≤90s，首次需预载像素素材）→ Phaser 画布可见 → 8 帧 rAF 稳定 | 无 |
| 02 | `02-pause-menu.webp` | `/world` + `Esc` | 在 01 的就绪状态上按 `Escape` 呼出暂停菜单 | `.pause-panel` 与 `pause-resume` 按钮可见 | `.pause-player`（昵称·第N天） |
| 03 | `03-ai-guide.webp` | `/world` + 点击 Dock 向导按钮 | 在 01 的就绪状态上点击 `hud-guide` | `guide-overlay` 可见且 `guide-thread` 含内置欢迎语「AI 助教」（不依赖 LLM 后端） | 无 |
| 04 | `04-mainline-map.webp` | `/mainline-map` | 学生会话；对章节 1 的 a11y 镜像按钮派发 click 展开右侧抽屉 | `mainline-map-page` 可见 → `mainline-loading` 消失 → 无 `mainline-error` → 地图画布可见 → `mainline-stage-panel` 可见 | 无 |
| 05 | `05-stage-learning.webp` | `/mainline/stage/:id` | 从 04 的抽屉点击第一个可进入（`:enabled`）的小节按钮，落到后端解锁的第一个小节（新账号即 1-1） | URL 匹配 `/mainline/stage/\d+` → `stage-learn-page` 可见 → `stage-learn-loading` 消失且无 `missing`/`error` → `stage-learn-toc` 可见 | 无 |
| 06 | `06-library.webp` | `/library` | 学生会话 | `library-split-body` 可见 → 首个 `chapter-doc-*` 可见 → `library-page-content` 可见 → 10 帧 rAF（等 PDF.js 出图） | 无 |
| 07 | `07-workshop.webp` | `/workshop` | 学生会话 | `workshop-page` 与 `workshop-body` 可见 | 无 |
| 08 | `08-training.webp` | `/training` | 学生会话；页面自动加载默认共享课程题 | `training-panel` 可见 → `problem-statement` 可见（≤30s，等判题题面接口）→ CodeMirror `.cm-editor` 可见 | 无 |
| 09 | `09-bug-mine.webp` | `/bug-mine` | 学生会话；无入口 id 时直接展示错题本与回声板 | `bug-mine-panel` 与 `error-book` 可见 | 无 |
| 10 | `10-review-hall.webp` | `/review-hall` | 学生会话；新账号可能是空态（真实状态，不伪造数据） | `review-hall-panel` 可见 | 无 |
| 11 | `11-profile.webp` | `/profile` | 学生会话；8 维画像按账号真实数据渲染 | `profile-page` 可见 → 6 帧 rAF（等雷达图） | `.profile-name`（登录昵称） |
| 12 | `12-teacher-classroom.webp` | `/teacher` | **教师会话**；若名下无班级，先经真实 API `POST /api/classrooms` 创建「E2E 演示班级」 | `teacher-home` 与 `classroom-list` 可见 | 页头「这里是 XX 的班级总览」（昵称）、`.classroom-card .code`（班级加入码） |
| 13 | `13-npc-dialog.webp` | `/world` | 学生会话；通过真实 dialog store 打开镇长 Py 对话 | 对话框可见且打字机已输出正文 | 无 |
| 14 | `14-ai-guide-plan.webp` | `/world` + AI 助教 | 学生会话；提交零基础第 1 章学习目标 | 真实编排返回「看 / 学 / 练 / 测」计划且输入框恢复 | 无 |
| 15 | `15-stage-reading.webp` | `/mainline/stage/:id` | 进入首个解锁小节，切换到「学」课件阅读 | 阶段页加载完成，课件渲染稳定 | 无 |
| 16 | `16-stage-practice.webp` | `/mainline/stage/:id` | 在同一小节切换到「练」随堂练习 | 练习视图渲染稳定 | 无 |
| 17 | `17-library-rag.webp` | `/library` | 查询 `input函数` | 检索结果可见且含真实页码证据 | 无 |
| 18 | `18-workshop-agents.webp` | `/workshop` | 提交第 1 章完整学习规划请求 | 多智能体工作室可见，至少一个智能体工位出现 | 无 |
| 19 | `19-training-verdict.webp` | `/training?problemId=476` | 提交真实可通过代码 | 判题结果出现 `AC` | 无 |
| 20 | `20-bugmine-debug.webp` | `/bug-mine` | 同题提交 WA 后从训练场进入 Bug 矿洞 | 错题进入矿洞且逐层调试界面可见 | 无 |
| 21 | `21-teacher-board.webp` | `/teacher` | **教师会话**；进入真实演示班级看板 | `teacher-detail` 可见 | 加入码、教师昵称 |
| 22 | `22-code-theater.webp` | `/code-theater` | 学生会话；点击「运行」执行默认机关广场代码 | `code-theater-page` 与 `ct-quest` 可见 → 点击 `ct-run` → `ct-world` 与 `ct-steps` 可见 | 无 |
| 23 | `23-clients.webp` | `/guide?topic=clients` | 学生会话；滚动到「多端下载」主题的下载卡片区 | `guide-content` 含「多端下载」→ `guide-downloads` 可见并含 Android / Windows 下载卡 | 无 |
| 24 | `24-code-theater-stage.webp` | `/code-theater` | 学生会话；点「运行」后代码傀儡执行到循环中段（脚本梯发光行、变量牌翻新、循环跑道计数） | `code-theater-page` 与 `ct-quest` 可见 → 点 `ct-run` → `ct-stage` 与 `ct-steps` 可见，傀儡停在 `ct-world` 内某一执行行 | 无 |
| 25 | `25-endless-result.webp` | `/endless` | 学生会话；点 `endless-start` 连答数题后命中一次判题结算 | `endless-page` → `endless-start` → `endless-body` → 至少一次 `endless-submit` → `endless-result` 可见，且 `endless-streak` / `endless-total-score` 有值 | 无 |
| 26 | `26-arena-tower.webp` | `/arena/1` | 学生会话；展示第 1 章闯关塔 10 关阶梯（9 小关 + 第 10 关 BOSS），部分已通关点亮 | `arena-page` → `arena-steps` 可见 → 10 个 `arena-step-1-1`…`arena-step-1-10` 渲染（末关为 boss） | 无 |
| 27 | `27-loop-mastery.webp` | `/arena/1` | 学生会话；点开某一关详情，展示该关 KC、弱项推荐徽标与画像接入提示（练→测→掌握→画像回流的落点） | `arena-page` → 点某个 `arena-step-1-*` → `arena-detail` 可见 → `arena-reco-badge` 与 `arena-profile-hint` 可见 | 无 |
| 28 | `28-code-theater-debug.webp` | `/code-theater` | 学生会话；模拟「从失败练习跳入」（载入一段未通过代码）运行后，世界目标未全部达成（如第 4 盏灯仍黑） | `code-theater-page` 与 `ct-quest` 可见 → 点 `ct-run` → `ct-world` 显示部分目标未点亮（未达成态） | 无 |
| 29 | `29-endless-weak-kc.webp` | `/endless` | 学生会话；锁定 `endless-focus-chapter` 后连答，展示按画像弱项出题 + 历史记录 | `endless-page` → 选择 `endless-focus-chapter` → `endless-start` → `endless-history` 可见且含知识点 / 难度记录 | 无 |

> 24–29 为 R-14「代码剧场 × 无尽闯关」统一玩法新增：与 01–23 一致，均走学生会话注入、视口 **1440×900**、`deviceScaleFactor=1`、全局遮蔽规则相同。这 6 张当前未落盘，指南页以「截图准备中」文字占位优雅降级；对应的 Playwright 采集用例待在 `frontend/e2e/guideScreenshots.spec.ts` 中补齐后再实拍（Wave 2）。

等待策略说明：全套不使用长 `sleep`，也不使用 `networkidle`（应用挂有 SSE 长连接，`networkidle` 永远不触发）；统一用「目标元素可见 / 加载态元素从 DOM 消失 / N 帧 `requestAnimationFrame`」组合。

## 遮蔽规则汇总

- **token**：仅存在于 localStorage，不会出现在画面中；报错信息也不回显凭证。
- **昵称/用户名（个人信息）**：`.header-status__name`（全局）、`.pause-player`（02）、`.profile-name`（11）、教师页头欢迎语（12、21）。
- **邮箱**：注册演示账号时不填邮箱字段，页面无邮箱可见面；全局 `time` 遮蔽兜底其余真实时间戳。
- **班级加入码**：`12-teacher-classroom` 与 `21-teacher-board` 的加入码均遮蔽，防止演示班级被陌生人加入。
- 游戏内日历（第N天/季节/天气）是虚构世界状态，不属于真实时间戳，不遮蔽。

## 重拍方法

```bash
# 1. 起本地全栈（项目根目录，含 backend/graph/rag/Postgres）
./dev.sh

# 2. 在 frontend/ 下带环境变量采集（推荐临时注册模式，不污染常用账号）
cd frontend
PYT_E2E_REGISTER=1 \
PYT_E2E_TEACHER_INVITE_CODE=<与后端 PYT_TEACHER_INVITE_CODE 一致> \
npm run capture:guide

# 已有固定演示账号时：
PYT_E2E_USERNAME=demo_student PYT_E2E_PASSWORD=*** \
PYT_E2E_TEACHER_USERNAME=demo_teacher PYT_E2E_TEACHER_PASSWORD=*** \
npm run capture:guide
```

- 只重拍某一张：在命令后加 `-g "08-training"`（按测试标题过滤），例如
  `PYT_E2E_REGISTER=1 npm run capture:guide -- -g "08-training"`。
- 对非默认环境拍图：加 `BASE_URL=http://<host>:<port>`。
- 输出直接覆盖 `frontend/public/guide/screenshots/` 下的同名 `.webp`；中间 PNG 与失败 trace 在 `frontend/e2e/.artifacts/`（trace 仅失败时保留）。
- 仅校验套件可被发现（不真的拍，无需本地栈）：`npx playwright test --list`。

## 已知风险与注意事项

1. **截图必须随指南内容同步复验**：当前指南引用 29 张 WebP（01–29）；发布前用内容源提取 URL，并确认每个文件存在、尺寸为 1440×900 且非空。其中 24–29 为本轮新增，尚待采集套件补齐用例后实拍。
2. **教师邀请码**：后端默认 `PYT_TEACHER_INVITE_CODE` 为空（注册教师会被 403）。注册临时教师号前需先给后端配置该变量，或直接提供已有教师账号凭证。
3. **Phaser 在无头浏览器下的渲染**：配置里已加 `--enable-unsafe-swiftshader` 放行软件 WebGL；若 WebGL 仍不可用，Phaser 会自动退回 Canvas2D，画面内容一致但渲染保真度略降。
4. **首次运行较慢**：`/world` 需预加载像素素材（等待上限 90s）；后续用例复用已起的浏览器与会话，整体明显变快。
5. **runner 分离**：vitest 的 `npm run test` / `test:watch` 已通过 `--exclude "e2e/**"` 排除本目录；Playwright 的 `testDir` 固定 `e2e/`，不会扫到 `tests/`。两边互不读取对方的用例。
6. **随机临时账号会在数据库留下记录**（`pyt.e2e.s.*` / `pyt.e2e.t.*` 及「E2E 演示班级」），属真实注册数据，演示库可定期清理。
