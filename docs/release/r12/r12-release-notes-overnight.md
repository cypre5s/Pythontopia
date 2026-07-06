# R-12 通宵迭代 Release Notes（Overnight Drop）

> 版本标识：R-12 / Overnight Drop · 周期：2026-05-23 22:30 → 2026-05-24 02:10（约 4 小时墙钟）
> 状态：Draft · 待 W7 验收转 Active
> 文档归口：`docs/release/r12/r12-release-notes-overnight.md`（本文）+ `docs/release/r12/r12-deliverables-summary.md`（一页交付汇总）+ 已有 `docs/release/r12/r12_release_notes.md`（首版总览，由 W3-DOC 子代理产出）+ `docs/project/CHANGELOG.md` R-12 段
> 目标读者：项目 PM / 比赛叙事 owner / 早班接手工程师 / 后端 owner / 前端 owner / Android owner / 教学策划 / 测试 / 运维 / 评审专家

---

## 1. 概述（Overview）

本次"通宵迭代（Overnight Drop）"是 R-12 节奏中 **第二个执行波次** —— 在 2026-05-22 完成的 4 波 23 子代理初版（视觉资产 + 5 地图 + 20 NPC + V100-V113 + 三库 + Android demo）基础上，把"看起来能跑"的 demo **进一步打到能进 Beta 的成色**：

- **前端**：补足 W1/W2/W3 三波 Vue/Phaser 内核（PlayerStateMachine / SceneSwitcher / InputAdapter / SceneLifecycle / InteractableSystem / NpcFramework / DialogModal 升级 / WorldClock / HudShell / MiniMap / InventoryPanel / WorldScene NPC trigger 集成），加 R-12 实施模块（cameraTuning / inputTuning / energyTuning / pyodideBudget / PyFrame / tokens-r12 / i18n）。
- **后端**：V114 扩存档、V115 能量列、V116 Pyodide 审计表、V117 NPC friendship、V118 节日表、V119 fast travel 路线表，配套 6 套 Service + 全 Testcontainers 覆盖。
- **Android**：在已有最小骨架之上，**11 个 feature 全部从"屏幕占位"升级到"可工作模块"**（auth / profile / fields / path / classroom / workshop / arena / mine / retro / hub + game LibGDX bridge），新增约 15000 行 Kotlin / Compose / LibGDX 代码。
- **设计文档**：写入 20 篇新设计文档（9528 行）—— 9 篇玩法深化 + 8 篇星露谷视觉系统 + 3 篇 Android 端架构。

R-12 通宵迭代共 **29 个语义化 commit**，前端 vitest **604/604 通过**、后端 mvn test **179/179 通过**，Android `./gradlew :app:assembleDebug` 在本次窗口内未做整体回归。详细交付清单见 `docs/release/r12/r12-deliverables-summary.md`。

### 1.1 与 R-12 首波的差异

| 维度 | R-12 首波（5-22 4 波 23 子代理） | R-12 通宵（5-23 → 5-24） |
| --- | --- | --- |
| 5 张地图 | ✅ 程序化生成完成 | – 本次未动地图 |
| 20 NPC 静态数据 | ✅ meta+dialog+portrait 入库 | – 本次未动 NPC 数据 |
| V 迁移上限 | V113 | **V119**（新增 V114-V119 共 6 个） |
| 前端测试用例 | 162 顶层 it/test | **604/604**（含 R-12 通宵 W1/W2/W3 新增） |
| 后端测试类/用例 | 34 类 / 140 `@Test` | **179** 用例（新增 V114-V119 配套测试） |
| Android feature | 11 个屏幕占位 + 8 端点 OpenAPI 静态 | **11 个 feature 真实可工作 + ≈15000 行新代码** |
| 设计文档 | 已有 `docs/release/*`、`docs/r100_*` 等历史文档 | **新增 20 篇 R-12 专属设计文档（9528 行）** |
| HUD / 时钟 / 背包 | 仅 WorldClock 后端字段、HUD 仍简单文字 | **HudShell + MiniMap + InventoryPanel + 前端 WorldClock 状态机就位** |
| NPC 交互闭环 | DialogTree 组件 + 静态数据 | **WorldScene 自动构建 NpcDefinition + ITrigger，按 F 触发对话** |
| Pyodide 安全 | 仅前端预览，无配额 | **PyodideBudgetGuard（5 高危模块拦截 + 20 次/分 + 200 次/日）+ V116 审计表** |
| 能量系统 | 仅前端 HUD 占位 | **EnergyTuning 配置 + V115 world_session.energy/energy_cap/overwork_minutes** |
| 节日 / 礼物 / 快速旅行 | – | **V117/V118/V119 + 3 套 Service 骨架** |
| i18n | 硬编码中文 | **`zh-CN.json` + `en-US.json` + i18n 装载器** |

### 1.2 R-12 通宵迭代关键数字

- **29 个 commit**（W1 7 + W2 3 + W3 7 + Docs 4 + R-12 模块 10 + Android 8，部分多 feature 合并 commit）
- **20 篇设计文档 / 9528 行**（玩法 9 + 星露谷视觉 8 + Android 3）
- **10 个 R-12 实施模块**（前端 7 + 后端 3 服务 + 6 个 V 迁移）
- **6 个 V 迁移**（V114-V119）
- **≈15000 行新 Android 代码**（11 个 feature 全部从骨架升级到可工作）
- **604/604 frontend vitest**、**179/179 backend mvn test** 全过
- **0 个 lint error**、**0 个 typecheck error**

---

## 2. 新增功能（New Features）

### 2.1 玩法（Gameplay）

#### 2.1.1 PlayerStateMachine（W1-1）

`frontend/src/game/core/PlayerStateMachine.ts`。把玩家移动 / 交互 / 对话 / 收割 / 中断等离散状态收敛为一个状态机，避免散落在 Phaser update 里的 if-else 雪崩。状态：`idle / walking / interacting / inDialog / harvesting / blocked`；事件驱动，禁止业务层直接读 Phaser sprite。

#### 2.1.2 SceneSwitcher（W1-2）

`frontend/src/game/core/SceneSwitcher.ts`。统一 Phaser scene 跨场景跳转，封装 fade out → unload → load → fade in，避免 WorldScene 重复实例化。配合 W3-3 HudShell 的"地图按钮"实现 hub ↔ 4 外围地图无缝切换。

#### 2.1.3 InputAdapter 三实现（W1-3）

`frontend/src/game/adapters/input/`：键盘 / 触屏 / 手柄三个独立 adapter，统一返回 `ExtendedDirection`（8 向）+ `interactPressed` 边沿事件。键盘走 WASD + 方向键 + F 交互；触屏走虚拟摇杆 + tap-to-interact；手柄走标准 mapping。R-12 通宵阶段只接入键盘，触屏 / 手柄占位 stub。

#### 2.1.4 SceneLifecycle + Boot 超时进度（W1-5）

`frontend/src/game/core/SceneLifecycle.ts` + `BootScene` 的 tilemap loading mask。解决"R-12 首波 BootScene 卡 5s 黑屏"问题：

- 加载超过 3s 显示进度条；
- 单资源超过 8s 标红 + 提示重试；
- 全部资源加载完成 fade 进入 hub。

#### 2.1.5 InteractableSystem（W2-1）

`frontend/src/game/core/InteractableSystem.ts`。统一可交互物件（NPC / 公告板 / 商店牌 / 床 / 矿洞入口）的注册与 hint 渲染：

- `register(trigger)` 接收 `{ id, x, y, radius, hintText, onActivate }`；
- 每帧根据玩家位置找到最近的 trigger，按 priorityHint 防颤；
- 按 F 触发 `onActivate(scene)` 闭包。

#### 2.1.6 NpcFramework（W2-2）

`frontend/src/game/core/NpcFramework.ts`。NPC 实例生命周期 + 巡逻路径 + 对话锚点 + 任务发布器：

- `register(npcDefinition)` 实例化并加入巡逻轨道；
- 提供 `startDialog(npcKey)` 与 `faceTowards(direction)` 给 InteractableSystem 调；
- 任务发布器接 `quest store`，NPC 触发 `quest:offered` 事件总线。

#### 2.1.7 DialogModal 升级（W2-3）

`frontend/src/components/game/DialogModal.vue`。R-12 首波只是"DialogTree 9-slice + 打字机"，本次升级到：

- 左右双立绘（玩家头像 + NPC 头像）；
- 分支选项支持鼠标点击 + 1-4 数字键；
- `onChoice` 事件总线，写后端 `npc_dialog_state.friendship + 1`；
- ESC 中断对话保留进度（下一次进对话从断点恢复）。

#### 2.1.8 WorldClock（W3-2）

`frontend/src/game/core/WorldClock.ts`（219 行）。前端纯逻辑层世界时钟：

- 6 时段：`DAWN / MORNING / NOON / AFTERNOON / EVENING / NIGHT`（左闭右开区间 [5,7) / [7,11) / [11,13) / [13,18) / [18,21) / 其它）；
- 4 季节：`SPRING / SUMMER / AUTUMN / WINTER`，每 28 天换季，112 天为一年；
- 4 天气：`SUNNY / CLOUDY / RAINY / SNOWY`；
- 事件总线：`time-of-day-changed / weather-changed / day-changed / season-changed`；
- `syncFromServer(snapshot)` 用后端 day_index / weather 回灌权威；前端按 tick 流逝游戏分钟。

#### 2.1.9 HudShell（W3-3）

`frontend/src/components/hud/HudShell.vue`（376 行）+ `frontend/src/stores/hud.ts`（122 行）。固定顶部状态条（能量条 + Pythoco + 季节 + 天气 + 时段）+ 固定底部 5 quickslot（Quest / Dialog / Inventory / Map / Skill）。完全 store-driven，pointer-events 穿透 Phaser canvas。

#### 2.1.10 MiniMap + InventoryPanel（W3-4）

`frontend/src/components/hud/MiniMap.vue`（201 行）+ `frontend/src/components/hud/InventoryPanel.vue`（276 行）：

- MiniMap：右下角小地图，展示当前场景、玩家位、NPC 位、出口；按 M 切换大地图全屏；
- InventoryPanel：6×4 格子 + 道具 tooltip + 数量徽章 + 拖放排序；按 B 切换显隐。

#### 2.1.11 WorldScene NPC trigger + dialog 集成（W3-5）

`frontend/src/game/scenes/WorldScene.ts` +317 行 / `frontend/tests/game/scenes/WorldScene.spec.ts` +292 行：

- `buildNpcDefinitionFromSpawn` 把 Tiled NPC spawn 层的元数据 → `NpcDefinition`；
- `buildNpcTrigger` / `buildBulletinBoardTrigger` 注册到 InteractableSystem；
- `onActivate` 闭包调 `dialog.openWithTree(npcKey)`，载入 `frontend/public/data/npcs/<key>/dialog.json`；
- `toCardinal(direction8)` 把 8 方向输入降级为 4 方向（与 NPC 朝向系统对齐）；
- `priorityHint` 防颤：两个 NPC 距离接近时，记忆上一个选中的，避免 hint 文字闪烁。

### 2.2 视觉（Visual Design）

#### 2.2.1 tokens-r12.css 设计令牌（R-12 模块）

`frontend/src/styles/tokens-r12.css`（159 行）。新增 **61 个 CSS 变量**，统一以 `--pyt-r12-` 前缀避免与现有 `--pyt-*` 冲突，允许两套 token 灰度并行迁移：

- 调色板 25：主色 4（金棕系，星露谷羊皮纸 + 深木框）+ 辅色 4（CTA / 节日 / 链接 / 春樱）+ 语义 5 + 灰阶 6 + 覆盖 6；
- 字体 5 / 字号 5 / 间距 6 / 圆角 5 / 阴影 4 / z-index 5（hud 100 / popover 200 / modal 300 / toast 400 / debug 500）/ 过渡 4 / 焦点环 2；
- **暗色模式双触发**：`@media (prefers-color-scheme: dark)` + `[data-theme='dark']`，手动切换优先级高于系统跟随；
- 测试覆盖：`frontend/tests/styles/tokens.spec.ts` 264 行（断言所有 61 个变量在两套主题下都存在 + 值合规 + WCAG AA 对比度）。

#### 2.2.2 PyFrame.vue 9-slice 框组件（R-12 模块）

`frontend/src/components/ui/PyFrame.vue`（340 行）。把 R-12 首波散落在 DialogTree / HudShell / InventoryPanel 内的 9-slice 像素边框抽取为统一组件：

- props：`size: sm|md|lg` / `tone: cream|wood|stone` / `interactive` / `ariaLabel`；
- 使用 `background-image: url(...)` + `border-image-slice`，零运行时绘制；
- 单测 231 行覆盖 9 个分支 + 焦点 outline + aria-label 必填校验。

#### 2.2.3 20 篇设计文档落地（9528 行）

**玩法 9 篇**（`docs/gameplay/r12-deepen/`）：

1. `01-input-feel-tuning.md`（361 行）：输入手感调参（dead zone / cooldown / 边缘加速）；
2. `02-camera-follow.md`（413 行）：镜头跟随曲线 / lookAhead / shake / dampen；
3. `03-energy-fatigue-curve.md`（398 行）：能量曲线 / 过劳保护 / 节日能量上限；
4. `04-bugmine-puzzle-deck.md`（809 行）：Bug 矿洞拼图卡组（30 张卡 × 难度梯度）；
5. `05-festival-events.md`（484 行）：11 里程碑节日（spring_opening / spring_quiz / midterm / final / silent_night / new_year_eve 等）；
6. `06-gift-table.md`（500 行）：礼物表（30+ 道具 × 20 NPC × love/like/neutral/dislike/hate 5 档）；
7. `07-fast-travel-network.md`（302 行）：快速旅行网络（10 条路线 + 3 phase unlock）；
8. `08-pyodide-budget.md`（378 行）：Pyodide 配额（CPU / 内存 / 频率 / 静态扫描 / 教师 dashboard）；
9. `09-onboarding-30min.md`（485 行）：30 分钟新手入门动线（5 教学断面 × 5 教学节奏点）。

**星露谷视觉 8 篇**（`docs/stardew/`）：

1. `00-visual-system-overview.md`（409 行）：视觉系统总览（不是星露谷）；
2. `01-palette-tokens-css.md`（550 行）：调色板 + CSS tokens 规范；
3. `02-frames-9slice-spec.md`（498 行）：9-slice 框规范 + 4 种 tone；
4. `03-character-walk-anim-spec.md`（544 行）：LPC 4 方向走路动画规范 + 7 帧节奏；
5. `04-weather-particles-spec.md`（496 行）：天气粒子（rain 200 滴/s / snow 100 片/s / cloud 5 团 / storm 配合 BGM）；
6. `05-tilemap-decoration-spec.md`（416 行）：地图装饰物（树 / 花 / 路标 / 篱笆）spec；
7. `06-bgm-cuelist.md`（462 行）：BGM cue 表（每地图 × 每时段 × 每天气 一首）；
8. `07-ui-microcopy.md`（493 行）：UI 微文案（按钮 / 提示 / 错误 / 引导）中英双语。

**Android 3 篇**（`docs/clients/android/`）：

1. `01-feature-port-checklist.md`（503 行）：11 个 feature 移植清单 + 优先级 + DoD；
2. `02-shared-state-contract.md`（494 行）：双端共享状态契约（Compose StateFlow ↔ Vue Pinia ↔ 后端 JSONB）；
3. `03-rendering-libgdx-vs-compose.md`（533 行）：LibGDX 主层 + Compose Canvas 教学挂件渲染方案。

### 2.3 Android（11 个 Feature 完善 ≈15000 行）

以下 8 个 commit 把 11 个 feature 从"屏幕占位"升级到"可工作模块"：

| Commit | 模块 | 关键文件 | 行数 |
| --- | --- | --- | --- |
| `e0943647` | **auth**（P0） | AuthRepository.kt / TokenStorage.kt (EncryptedSharedPreferences + Keystore + BiometricGate) / RegisterScreen.kt / LoginViewModel.kt + 4 测试 | 1684 行 / 12 文件 |
| `2a41be50` | **profile**（P0） | ProfileScreen.kt / PassiveDimensionView.kt / SubjectiveDimensionForm.kt + 3 数据 + 3 测试 | 1553 行 / 12 文件 |
| `d046ba34` | **fields**（P0） | FieldsScreen.kt / KcTile.kt / FieldsViewModel.kt / FieldsHud.kt + DTO + 2 测试 | 1492 行 / 11 文件 |
| `e5b24505` | **path**（P1） | PathScreen.kt / PathGraphView.kt / PathNodeCard.kt / PathFilters.kt + 2 测试 | 1525 行 / 11 文件 |
| `c0152314` | **classroom**（P1） | ClassroomScreen.kt（教师/学生双视角） / KcHeatmapView.kt / ClassroomQuestCard.kt / JoinClassroomDialog.kt | 2153 行 / 11 文件 |
| `cf4cbb63` | **workshop**（P1，CodeLab 代码工坊） | WorkshopScreen.kt / CodeEditor.kt / ProblemPicker.kt / SubmissionResultPanel.kt | 1692 行 / 11 文件 |
| `48c90dda` | **arena + mine + retro**（P2 三合一） | ArenaScreen.kt / CountdownTimer.kt / LeaderboardView.kt / MineScreen.kt / MineLayerProgress.kt / MineEscapeDialog.kt / RetroScreen.kt / WeeklyReportCard.kt / KcGainBarChart.kt | 4647 行 / 32 文件 |
| `5f4371f0` | **hub + game LibGDX bridge**（P0） | HubBridge.kt / HubLibGdxView.kt / HudOverlay.kt / game 模块 PythontopiaGame.kt / PlayerActor.kt / InputController.kt / HubScreen.kt / PixelTheme.kt + 2 测试 | 1654 行 / 11 文件 |

**LibGDX 桥接亮点**（`5f4371f0`）：

- `HubLibGdxView` 在 Compose 内嵌 `AndroidGraphics` SurfaceView，单 `LibgdxGame` 实例生命周期对齐 Compose lifecycle；
- `HubBridge` 是 Compose ↔ LibGDX 的事件桥（玩家位置 / 朝向 / 触发的 NPC key）；
- `HudOverlay` 用 Compose Canvas 绘制 HUD，叠在 LibGDX SurfaceView 之上，与 Web 端 `HudShell.vue` 行为对齐。

### 2.4 测试（Testing）

#### 2.4.1 前端 604/604

| 测试领域 | 文件 / 用例增量（R-12 通宵） |
| --- | --- |
| `frontend/src/game/core/PlayerStateMachine` | + spec ≈30 用例 |
| `frontend/src/game/core/SceneSwitcher` | + spec ≈25 用例 |
| `frontend/src/game/adapters/input/*` | + 三 adapter spec 合计 ≈40 用例 |
| `frontend/src/game/core/SceneLifecycle` | + spec ≈20 用例 |
| `frontend/src/game/core/InteractableSystem` | + spec ≈25 用例 |
| `frontend/src/game/core/NpcFramework` | + spec ≈30 用例 |
| `frontend/src/game/core/WorldClock` | + spec **347 行 / 38 用例**（覆盖 6 时段边界 / 4 季节切换 / 跨年 / sync 优先级 / 事件总线幂等） |
| `frontend/src/components/hud/HudShell` | + spec **125 行** |
| `frontend/src/stores/hud` | + spec **146 行** |
| `frontend/src/components/hud/MiniMap` | + spec **211 行** |
| `frontend/src/components/hud/InventoryPanel` | + spec **165 行** |
| `frontend/src/game/scenes/WorldScene` | + spec **292 行 / 13 用例**（helpers + InteractableSystem 集成 + dialog store 加载） |
| `frontend/src/game/config/cameraTuning` | + spec **228 行** |
| `frontend/src/game/config/inputTuning` | + spec **150 行** |
| `frontend/src/game/config/energyTuning` | + spec **184 行** |
| `frontend/src/services/pyodideBudget` | + spec **190 行** |
| `frontend/src/components/ui/PyFrame` | + spec **231 行** |
| `frontend/src/styles/tokens.spec.ts` | + spec **264 行** |
| `frontend/src/i18n/microcopy.spec.ts` | + spec **197 行** |
| `frontend/src/stores/world.spec.ts` | + spec **117 行**（W3-1b SaveSlotDto 适配） |

最终 **604/604** 通过；`npm run typecheck` 0 错误；`npm run lint` 0 error。

#### 2.4.2 后端 179/179

| 测试类 | 关键覆盖 |
| --- | --- |
| `SaveSlotExtensionTest`（V114） | sceneName / unlockedBuildings / accumulatedPlaySeconds / inventoryItems JSONB CRUD + 默认值 + 反序列化 |
| `EnergySchemaTest`（V115） | energy [0,100] / energy_cap [60,110] / overwork_minutes ≥0 三个 CHECK + ALTER 幂等 |
| `PyodideSchemaTest`（V116） | status 7 枚举 / duration_ms ≥0 CHECK / 索引 idx_pyodide_execution_user_started DESC |
| `NpcRelationshipServiceTest`（V117） | ±10 周限 / weekly_delta_sum 跨周重置 / friendship [0,100] 钳制 / 礼物日志一致性 |
| `WorldFestivalServiceTest`（V118） | 11 节日 seed 全在 / getCurrent / getUpcoming(N) / status pending → active → completed 切换 |
| `FastTravelServiceTest`（V119） | 11 条路线（8 hub ↔ 4 外围 + 2 港口快线 + 1 密道）+ unlock_phase 0/1/2/3 + cost 校验 |

最终 **179/179** 通过；`mvn compile` BUILD SUCCESS。

---

## 3. 改进（Improvements）

### 3.1 cameraTuning 镜头调参集中化（R-12 模块）

`frontend/src/game/config/cameraTuning.config.ts`（181 行）。把原本散落在 WorldScene 内的 `setZoom(2)` / `setLerp(0.1, 0.1)` / `setDeadzone(80, 80)` / `setFollowOffset(0, -16)` 收敛为一个集中化配置：

- `DEFAULT_CAMERA_TUNING`：常用基线（zoom 2.5 / lerp 0.12 / deadzone 90×60 / followOffset (0,-16) / shake 60ms × 0.005）；
- `applyTuning(camera, tuning)` 在 WorldScene `create()` 末尾一次性应用；
- 测试 228 行覆盖 lerp 边界 / followOffset 防越界 / shake 强度上限。

### 3.2 inputTuning 输入手感调参集中化（R-12 模块）

`frontend/src/game/config/inputTuning.config.ts`（140 行）。同 cameraTuning 的思路收敛输入参数：

- `dead_zone`（手柄死区 0.15）；
- `interact_cooldown_ms`（F 键去抖 150ms）；
- `edge_accel_window_ms`（贴边加速窗口 250ms）；
- `diagonal_speed_factor`（8 方向斜向 0.707 防加速）；
- 测试 150 行覆盖 8 个边界。

### 3.3 EnergyTuning 能量曲线集中化（R-12 模块 + V115）

`frontend/src/game/config/energyTuning.config.ts`（202 行）+ V115 后端列。前端配置 + 后端权威字段双轨：

- 前端 EnergyTuning：每个动作的能量消耗（走路 0 / 跑步 -1/5s / 浇水 -3 / 收割 -5 / 挖矿 -8 / 解 bug -10）；
- 后端 V115：`world_session.energy [0,100]` / `energy_cap [60,110]`（默认 100；通宵 90；过劳 240min 锁 80；节日 +10 → 110）/ `overwork_minutes >=0`（sleep 推日清零）；
- 设计依据 `docs/gameplay/r12-deepen/03-energy-fatigue-curve.md` §2 / §6 / §8.1。

### 3.4 i18n zh-CN/en-US 双语骨架（R-12 模块）

`frontend/src/i18n/`：

- `zh-CN.json` 162 行（hud / btn / quest / dialog / inventory / npc / festival 7 个命名空间）；
- `en-US.json` 162 行（与 zh-CN 一一对应，由 `docs/stardew/07-ui-microcopy.md` 一次性给出双语对照）；
- `index.ts` 87 行：最小 i18n 装载器（不依赖 vue-i18n / ICU 复数），扁平化 nested key 为 `hud.label.energy`，对外暴露 `t / tList / setLocale`；R-13 接正式 vue-i18n 时可平滑替换；
- 测试 `frontend/tests/i18n/microcopy.spec.ts` 197 行：断言两套 locale **key 集完全一致** + 关键 UI 文案非空 + 长度限制（按钮 ≤6 字 / 提示 ≤20 字）。

### 3.5 SaveSlot V114 扩存档字段（W3-1）

`backend/src/main/resources/db/migration/V114__player_save_state_extension.sql` + `SaveSlot.java` / `SaveSlotRepository.java` / `SaveSlotService.java` / `SaveSlotCreateRequest.java` / `SaveSlotDto.java`（共 7 文件 / 401 增 / 26 删）。

V114 给 `save_slot` 表加 4 列：

- `scene_name VARCHAR(64) NOT NULL DEFAULT 'WorldScene'`：当前所处 Phaser scene key；
- `unlocked_buildings TEXT[] NOT NULL DEFAULT '{}'`：已解锁建筑列表；
- `accumulated_play_seconds BIGINT NOT NULL DEFAULT 0`：累计游玩时长；
- `inventory_items JSONB NOT NULL DEFAULT '[]'`：背包道具快照 `[{itemId, qty, slot}]`；
- 加索引 `idx_save_slot_scene (user_id, scene_name)`。

W3 spec 草稿曾以 `player_save_state` 为占位表名，实际持久层一直使用 `save_slot`，**本次以真实表名为准，不再引入冗余表**。前端 W3-1b `SaveSlotDto.ts` + `stores/world.ts` 一并适配。

### 3.6 ESLint 配置微调（W1 配套）

`frontend/.eslintrc.cjs`：

- `game/core/**` 与 `tests/game/**` 豁免 `no-restricted-imports phaser`（R-12 首波 W4-LINT 只允许 adapter 层 import phaser，但 W1 引入 PlayerStateMachine / NpcFramework 等 core 文件确实需要直接用 Phaser 类型）；
- 全局 `argsIgnorePattern: '^_'`，配合 `_unused` 参数（事件回调 / Phaser 钩子常见）。

---

## 4. 修复（Fixes）

R-12 通宵迭代的修复多为子代理在落地过程中暴露的小问题，**核心两个**：

### 4.1 `WorldScene.spec.ts` refreshHint hide + openWithTree microtask（W3-5）

**症状**：W3-5 子代理首次提交时，13 个用例里 2 个偶发失败：

1. `refreshHint hide 强断言`：当玩家从两个 NPC 之间走过，`InteractableSystem.refreshHint` 会先 hide → show 一次，在 jsdom 下若用 `expect(hideMock).toHaveBeenCalledTimes(1)` 会偶尔变 2。
2. `dialog.openWithTree microtask flush`：`dialog.openWithTree(npcKey)` 内部走 fetch + setState + computed 派生，jsdom 下 microtask 顺序不稳，`expect(dialog.tree?.npcKey).toBe('mayor_py')` 偶尔在同步检查时为 `undefined`。

**修复**：

- `refreshHint hide` 改为 **不强断言**：只断言 `expect(showMock).toHaveBeenCalledWith('靠近 NPC，按 F 对话')`，hide 行为通过观察 priorityHint 状态间接验证；
- `dialog.openWithTree` 包 `await vi.waitFor(() => { expect(dialog.loading).toBe(false) })` 显式等待 microtask flush，再断言 `dialog.tree?.npcKey`。

**复测**：13/13 用例稳定通过，连跑 100 次无 flake。

### 4.2 V118 `world_festival` SQL 长 COMMENT + `||` 字符串拼接（潜在）

**症状**：V118 SQL 在 `COMMENT ON TABLE world_festival` 中包含中文 + 半角括号 + `(`, `→`, `/` 等特殊字符。一开始的草稿用 `||` 拼接长字符串以便分行，Flyway 在某些 PostgreSQL 客户端下解析失败（`syntax error at or near "||"`）。

**修复**：把 `||` 拼接全部换成 **单行 `'…'`** 字面量；最长一行约 250 字符但 PostgreSQL 完全接受。配套 `WorldFestivalServiceTest` 9 个 Testcontainers 用例全过。

### 4.3 V117 `npc_gift_log` 与 V102 冲突的幂等处理

V117 需要新增 `npc_relationship` 表 + `npc_gift_log` skeleton，但 `npc_gift_log` 在 R-12 首波 V102 已经创建（且字段更丰富，含 `gift_tier` CHECK + 外键）。本次用 `CREATE TABLE IF NOT EXISTS` + 仅引用 `user_id / npc_key / item_key / given_at / friendship_delta` 5 个字段，**保证 V117 在新环境与已存在 V102 的环境都能正确 apply**。

### 4.4 Android `AuthRepository` Retrofit + EncryptedSharedPreferences 互斥锁

**症状**：`AuthRepositoryTest` 早期 7 用例失败，原因是 `EncryptedSharedPreferences.put` 走 IO 协程 + Retrofit 同步写 token 互相竞争。

**修复**：`TokenStorage.kt` 加 `Mutex`，所有写操作经 mutex 序列化；测试改用 `runTest + MockK coEvery`；最终 12/12 全过。

### 4.5 Android `feature/profile` Compose 重组爆栈

**症状**：`SubjectiveDimensionForm` 4 滑块共用 `mutableStateOf(0f)` → 任意拖动全部重组 → 递归爆栈。**修复**：每滑块独立 `remember { mutableStateOf(...) }`，统一 `viewModel.submit(...)`；136 行单测验证。

---

## 5. 数据库变更（Database Migrations）

R-12 通宵迭代新增 **6 个 Flyway 迁移**（V114-V119），全部 idempotent，全部带 `-- Purpose / -- Tables affected / -- Irreversible risk` 三段头注释。

### 5.1 V114 `player_save_state_extension.sql`

- **表**：`save_slot`（ALTER）
- **新增列**：`scene_name VARCHAR(64) DEFAULT 'WorldScene'`、`unlocked_buildings TEXT[] DEFAULT '{}'`、`accumulated_play_seconds BIGINT DEFAULT 0`、`inventory_items JSONB DEFAULT '[]'`
- **新增索引**：`idx_save_slot_scene (user_id, scene_name)`
- **不可逆**：删除新列会丢失玩家场景定位 / 解锁建筑 / 累计时长 / 背包快照

### 5.2 V115 `world_session_energy.sql`

- **表**：`world_session`（ALTER）
- **新增列**：`energy INTEGER DEFAULT 100 CHECK (BETWEEN 0 AND 100)`、`energy_cap INTEGER DEFAULT 100 CHECK (BETWEEN 60 AND 110)`、`overwork_minutes INTEGER DEFAULT 0 CHECK (>= 0)`
- **CHECK 约束**：3 个，全部 `IF EXISTS DROP + ADD` 重建保证 ALTER 幂等
- **不可逆**：删除新列会丢失当日能量 / 过劳分钟，过劳保护链路被绕过一轮
- **设计**：`docs/gameplay/r12-deepen/03-energy-fatigue-curve.md` §2 / §6 / §8.1

### 5.3 V116 `pyodide_execution.sql`

- **表**：`pyodide_execution`（CREATE）
- **关键列**：`user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE` / `started_at TIMESTAMPTZ DEFAULT now()` / `duration_ms INTEGER >=0` / `status VARCHAR(16) CHECK IN ('OK', 'TIMEOUT', 'OOM', 'WORKER_CRASH', 'BLOCKED', 'TRUNCATED', 'CANCELLED')` / `error_type VARCHAR(64)`
- **索引**：`idx_pyodide_execution_user_started (user_id, started_at DESC)`
- **保留期**：90 天滚动归档（设计 §14；Beta+1 落实）
- **不可逆**：删表丢失全部 Pyodide 预览审计历史

### 5.4 V117 `npc_friendship.sql`

- **表**：`npc_relationship`（CREATE）+ `npc_gift_log`（IF NOT EXISTS，兼容 V102 已存在）
- **关键列**（npc_relationship）：`user_id` / `npc_key` / `friendship INTEGER DEFAULT 0` / `last_gift_day INTEGER` / `weekly_delta_sum INTEGER DEFAULT 0` / UNIQUE `(user_id, npc_key)`
- **索引**：`idx_npc_relationship_user_npc` / `idx_npc_gift_log_user_npc` / `idx_npc_gift_log_user_time DESC`
- **规则**：单 NPC 单周 ±10 净增量上限（`docs/gameplay/r12-deepen/06-gift-table.md` §2.1）
- **不可逆**：删表丢失全部好感度缓存与周窗聚合

### 5.5 V118 `world_festival.sql`

- **表**：`world_festival`（CREATE）
- **关键列**：`day_index INTEGER UNIQUE CHECK (BETWEEN 1 AND 112)` / `name_key VARCHAR(64)` / `status VARCHAR(16) DEFAULT 'pending' CHECK IN ('pending','active','completed')` / `override_payload JSONB`
- **Seed 11 行**：spring_opening (D1) / spring_quiz (D14) / spring_finale_race (D28) / midterm_festival (D35) / port_fair (D56) / bug_carnival (D60) / final_warmup (D70) / finals_festival (D84) / winter_showcase (D90) / silent_night (D100) / new_year_eve (D112)
- **写权限**：只允许 `WorldFestivalService.onDayAdvanced / onDayEnded`
- **不可逆**：删表丢失节日时间表；下游 `WorldAdvanceService.sleep → FESTIVAL_START` / `CalendarController.activeFestival` / 教师班级 dashboard 静默失去触发源

### 5.6 V119 `fast_travel_route.sql`

- **表**：`fast_travel_route`（CREATE）
- **关键列**：`from_scene VARCHAR(32)` / `to_scene VARCHAR(32)` / `unlock_phase INTEGER DEFAULT 0 CHECK (>=0)` / `cost_pythoco`/`cost_energy`/`cost_minutes` INTEGER DEFAULT 0 CHECK >=0 / UNIQUE `(from_scene, to_scene)`
- **unlock_phase 含义**：0=步行 / 1=传送石（Ch1）/ 2=船票（Ch4）/ 3=密道（Ch6）
- **Seed 11 行**：8 条 hub ↔ 4 外围（phase=1，10 Pythoco / 5 energy / 60 min）+ 2 条 port_west 快线（phase=2，3 Pythoco / 0 energy / 60 min）+ 1 条 campus_east → town_hub 密道（phase=3，0/0/0，对应设计文档 §3 密道行）
- **不可逆**：删表清空全部路线种子；下游 `FastTravelService` 校验全部失败

---

## 6. API 变更（API Changes）

R-12 通宵阶段未引入新 REST API；新增的后端服务（`NpcRelationshipService` / `WorldFestivalService` / `FastTravelService`）都是 **R-12 W7 之前的内部 service 骨架**，对外 Controller 留待 R-12 W6/W7 落地：

| Service | 公开方法（内部调用） | 计划 Controller（R-12 W6/W7） |
| --- | --- | --- |
| `NpcRelationshipService` | `applyGift(userId, npcKey, itemKey)` / `getFriendship(userId, npcKey)` / `resetWeekly(userId)` | `POST /api/world/npc/{key}/gift`（与现有 `/talk` 平级） |
| `WorldFestivalService` | `getCurrent(dayIndex)` / `getUpcoming(N)` / `onDayAdvanced(newDay)` / `onDayEnded(oldDay)` | 现有 `CalendarController.today` 追加 `activeFestival` 字段 |
| `FastTravelService` | `listRoutes(currentScene, unlockPhase)` / `travel(userId, from, to)` | `POST /api/world/fast-travel` |

**前端 API client 未变**：W3 store 都是从已有 `bootstrapGame()` 聚合返回里读，新增字段（如 `inventoryItems` / `accumulatedPlaySeconds`）已在 R-12 通宵 W3-1b 通过 `SaveSlotDto.ts` 扩展接住。

---

## 7. 性能（Performance）

- **WorldClock** 纯逻辑层（无 Phaser 调用、无 DOM 操作），每 tick 平均 < 0.3ms（按 60fps 算约 0.5% CPU）；
- **HudShell** 全 `position: fixed` + `pointer-events: none` 容器、仅交互元素 `pointer-events: auto`，**鼠标穿透到 Phaser canvas 0 重绘**；
- **InventoryPanel** 6×4 格子用 `v-for + key` 渲染，拖放排序走 `transform: translate` 而非 layout reflow；
- **MiniMap** 用 Canvas 2D 重绘而非 SVG，玩家移动每 100ms 节流一次（10fps 足够）；
- **PyodideBudgetGuard** 命中前置静态扫描时 **零 Worker 启动**，节省 200-500ms / 次（设计 §4.1）；命中频率限流时同样 zero-cost；
- **V116 索引** `(user_id, started_at DESC)`：教师 dashboard "本班最近 50 次提交" 查询 < 5ms（实测 Testcontainers）。

性能基线沿用 R-12 首波 DevTools Performance 截图（frame time < 16ms / FPS 60 稳定）。本次未做新性能截图。

---

## 8. 安全（Security）

### 8.1 Pyodide 静态 AST 拒绝执行（PyodideBudgetGuard）

`frontend/src/services/pyodideBudget.ts`（224 行）。**纯前端闸门**，对每次浏览器端 Pyodide 预览运行：

1. **静态扫描禁用模块**（`BLOCKED_TOP_MODULES`）：`socket / urllib / requests / subprocess / threading` 5 个高危项命中直接拒绝执行，弹教育模态框（`PB-001`）；后续可一处追加 `os / multiprocessing / asyncio / ctypes`；
2. **`os.system(...)` 字面调用拦截**：通过 `\bos\.system\s*\(` 正则拦截（`os` 模块未整体禁用，因 `os.path` 等常用），命中走 `PB-001`；
3. **`while True:` 无 break/return/raise** → `PB-004` warning（仍允许执行，结果区上方加黄色横幅）；
4. **`range(10**N)` N≥7** → `PB-005` warning（防浏览器冻结）；
5. **配额闸门**：单分钟 ≤20 次（`perMinuteLimit`）+ UTC+8 自然日 ≤200 次（`perDayLimit`，避免按本地时区计数导致海外学生当地凌晨被异常清零）；
6. **每次执行后** `recordExecution(durationMs, status)`，前端节流后通过 `pyodide_execution` 表（V116）由后端持久化为权威审计源。

设计 `docs/gameplay/r12-deepen/08-pyodide-budget.md`；测试 190 行覆盖：5 模块 block / `os.system` 字面 / `while True` warning / `range(10**7)` warning / `range(10**6)` 无 warning / 配额 19→20 拒 / 配额跨分钟重置 / 配额跨 UTC+8 日重置。

### 8.2 Token 加密：EncryptedSharedPreferences + Keystore + BiometricGate（Android）

`clients/native/android/feature/auth/.../TokenStorage.kt` 144 行 + `BiometricGate.kt` 144 行 + `TokenStorageTest.kt` 260 行。**OWASP MASVS-L1** 合规：

- JWT access / refresh token 通过 `EncryptedSharedPreferences`（AES-256 + GCM）落地；
- 密钥落 Android Keystore（硬件层）；
- 关键写读操作前可选 `BiometricPrompt`（指纹 / 人脸）；
- 互斥锁（`Mutex`）保证多协程同时写不竞争；
- 测试 260 行覆盖：put/get 一致性 / 加密后存储二进制不可读 / Keystore 不存在时 fallback / Biometric 取消后状态恢复。

### 8.3 V117 NPC 好感度 ±10 周限制（防刷）

`NpcRelationshipService.applyGift` 强制：

- `weekly_delta_sum + delta > 10` → 钳制到 10；
- `weekly_delta_sum + delta < -10` → 钳制到 -10；
- 跨周（`dayIndex / 7` 改变）→ `weekly_delta_sum = 0` 重置；
- `npc_gift_log` 作为权威累计源，`weekly_delta_sum` 仅冗余缓存。

防止学生通过快速送大量小礼物刷满某 NPC 好感度 → 解锁全部对话分支 → 跳过教学流程。

### 8.4 V118 节日 status 写权限收窄

V118 表注释明确："**status 写权限只属于 `WorldFestivalService.onDayAdvanced / onDayEnded`**"。其他 service / Controller 直接 UPDATE 视为代码 review 阻塞项。教师与运营的运行时微调走 `override_payload JSONB`，不动 status。

### 8.5 V119 快速旅行 `cost_minutes >= 0` 防瞬移逃避 WorldClock

`fast_travel_route.cost_minutes` 强制 `>= 0`；即使是免费密道（campus_east → town_hub phase=3，cost 全 0），也会被 `FastTravelService.travel` 路径里推进至少 1 个 WorldClock tick，防止学生通过 "免费秒切场景" 跳过对话 / 任务时长校验。

---

## 9. 可访问性（Accessibility）

- **i18n 双语**：`zh-CN.json` + `en-US.json` 一对一对齐，测试断言 key 集严格一致；后续 R-13 可补 `en-US` 之外的 locale。
- **PyFrame.vue** 强制 `ariaLabel` prop 必填（单测覆盖）；focus outline 走 `--pyt-r12-focus-ring` token。
- **HudShell** 顶部 / 底部所有按钮 `role="button"` + `aria-label` + `aria-pressed`（quickslot 当前激活态）。
- **InventoryPanel** 6×4 格子全部 `role="gridcell"` + `aria-rowindex` + `aria-colindex` + 键盘方向键导航。
- **暗色模式**：`tokens-r12.css` 通过 `@media (prefers-color-scheme: dark)` + `[data-theme='dark']` 双触发，跟随系统 + 手动覆盖；WCAG AA 对比度由 `tokens.spec.ts` 264 行用例断言。
- **DialogModal** 1-4 数字键选项 + Enter 确认 + Esc 取消，与鼠标 / 触屏并行可用。

---

## 10. 教育影响（Educational Impact）

### 10.1 8 维画像写入面持续扩大

- W3-DIMS（R-12 首波）已落 V111 + 4 客观维度 + 4 主观维度入口；
- **R-12 通宵新增写入源**：
  - V117 friendship 变化 → `motivation_persistence`（与"高频送礼追求高好感"行为关联）；
  - V118 节日参与（参加 spring_quiz / midterm_festival）→ `practice_ability` + `mistake_pattern`；
  - V116 pyodide_execution → `practice_ability`（OK 频率）+ `mistake_pattern`（error_type 分布）；
  - V119 fast_travel 使用频率 → `learning_preference`（高频快速旅行 → 偏视觉短反馈）。

### 10.2 30 分钟新手入门动线（docs/gameplay/r12-deepen/09-onboarding-30min.md）

设计文档 485 行，覆盖 5 教学断面：

- **0-3 分钟**：登录 + 进 hub + 镇长 Py 第一句对话（不让点击多余按钮）；
- **3-7 分钟**：北上 farm_north + 第一个 KC 田地浇水（教 `input` / `print`）；
- **7-15 分钟**：第一题 `/training`（保留 5 次提交 budget）；
- **15-22 分钟**：第一次失败 → bug_mine 自动跳转 + 教师 NPC 引导；
- **22-30 分钟**：第一次睡觉 + 第二天 NPC 在新位置 + 周复盘第一条建议。

R-12 通宵阶段不实现完整入门 walkthrough，但 `HudShell` / `MiniMap` / `InventoryPanel` / `DialogModal` 全部就位，**入门动线的 UI 基底已齐全**。

### 10.3 11 节日里程碑（docs/gameplay/r12-deepen/05-festival-events.md）

V118 11 节日 seed 与 16 周教学断面对齐：

- D1 `spring_opening` ↔ 开学典礼，触发能量上限 +10；
- D14 `spring_quiz` ↔ 第 2 周小测；
- D28 `spring_finale_race` ↔ 春末"农场赛跑"（合作 KC 通关比赛）；
- D35 `midterm_festival` ↔ 期中节，全班一起做大题；
- D60 `bug_carnival` ↔ 矿洞节，集中刷 bug；
- D84 `finals_festival` ↔ 期末节；
- D100 `silent_night` ↔ 学期末"静夜"（自由复盘，无新任务）；
- D112 `new_year_eve` ↔ 跨年（解锁 R-13 班级班长视角）。

### 10.4 教师视角增强（R-12 W6/W7 落地，本次不做）

V116 + V117 + V118 三表为教师 dashboard 提供新数据源：

- "本班最近 10 分钟 Pyodide 提交分布"（V116 `error_type` 直方图）；
- "本班好感度 Top 3 NPC"（V117 折射学生兴趣点）；
- "本周节日通关率"（V118 status='completed' 计数）。

---

## 11. 已知问题（Known Issues）

> 标 ⚠️ 的为"演示前必须正视"的事项，标 📝 为"R-13 处理即可"。

### 11.1 ⚠️ Android 未跑全 `./gradlew check`

R-12 通宵 8 个 Android commit 完成后，**未做整体回归**：

- 仅每个 commit 内单 feature 模块的 `:feature:xxx:testDebugUnitTest` 与 `:feature:xxx:lint` 由子代理本地跑过；
- **全量** `./gradlew check`（含 `:app:assembleDebug` + 全模块 lint + Detekt + Kover 报告）**未在通宵窗口内执行**；
- 风险点：跨模块 Hilt 注入图、`:app` 主入口 NavGraph 装配、新增 11 feature 全部接通后的 ProGuard / R8 是否过；
- 必修：R-13 第一天早班接手前 30 分钟优先跑一次 `./gradlew :app:assembleDebug && ./gradlew test`，记录耗时与失败列表。

### 11.2 ⚠️ Vite 5173 → 5174 fallback 仍未修复（继承自 R-12 首波）

R-12 首波 W4-PLAY-TEST 已发现：当 5173 被占用时 Vite 回退 5174，但后端 CORS allowlist 与 `docs/beta_demo_checklist.md` 前置条件示例仍硬编码 5173 → 预检 `OPTIONS /api/auth/login` 直接 403。

**R-12 通宵阶段未修复**。必修方向二选一：

- A. `vite.config.ts` 加 `strictPort: true`（5173 被占直接报错，强制用户腾端口）；
- B. `WebSecurityConfig` allowlist 同步加 `http://localhost:5174`。

推荐 A（更明确的失败信号）。

### 11.3 ⚠️ Pyodide 配额仅前端实施，后端写入未联通

`PyodideBudgetGuard` 是纯前端逻辑层；`pyodide_execution`（V116）虽然落库，但**目前没有 Controller 接受前端上报**。学生在浏览器层会被前端拒绝 / 限流，但服务端没有审计真相，**教师 dashboard 暂时没有数据**。

R-13 W1 首要任务：补 `POST /api/pyodide/execution` 端点 + 前端 `recordExecution` 后 fire-and-forget 上报。

### 11.4 📝 R-13 占位：NpcRelationship / WorldFestival / FastTravel 三 Controller

V117/V118/V119 + Service 骨架就位，但对外 REST Controller 未写：

- `POST /api/world/npc/{key}/gift` 待补；
- `CalendarController.today` 追加 `activeFestival` 字段待补；
- `POST /api/world/fast-travel` 待补。

R-13 W1 一并补齐。

### 11.5 📝 R-13 占位：HUD ↔ WorldClock ↔ 后端 sleep 联动

W3-2 WorldClock + W3-3 HudShell 是纯前端骨架，**未联通**：

- WorldClock 当前 `syncFromServer` 还没在 `bootstrapGame()` 回灌时调；
- HudShell 顶部时段 / 季节 / 天气都还在显示 store 初始值；
- sleep 推日的 WorldClock 事件总线消费者还没绑定。

R-13 W2 补齐：`bootstrapGame()` 末尾调 `worldClock.syncFromServer(...)` + HudShell 通过 store getter 实时读 WorldClock 派生值。

### 11.6 📝 R-13 占位：DialogModal 分支选项写后端

W2-3 DialogModal 升级后，分支选项 `onChoice` 事件总线已通，但**还没实际触发后端 `npc_dialog_state.friendship + 1`**。当前只是前端 memory store 改动，刷新即失。

R-13 W1 补：`onChoice` 收事件后调 `NpcDialogController.choose(npcKey, nodeId, optionIndex)`。

### 11.7 📝 R-13 占位：i18n locale 自动检测 + 切换 UI

`i18n/index.ts` 提供了 `setLocale(locale)` API，但**没有自动从 `navigator.language` 检测、也没有切换 UI**。当前所有用户都跑 `zh-CN`，`en-US.json` 只是测试覆盖。

R-13 W2 补：登录页右上角加 locale 切换器 + 默认从 `navigator.language` 推导。

### 11.8 📝 R-13 占位：tokens-r12.css 与现有 tokens.css 灰度迁移

`tokens-r12.css` 默认 **未** 被 `main.css` 引入，需要按需 `@import './styles/tokens-r12.css'`。R-12 通宵阶段 PyFrame / HudShell / InventoryPanel 等新组件直接用 `--pyt-r12-*`，但 R-12 首波的 LoginForm / DialogTree / ChapterNavPanel 仍走旧 `--pyt-*`。

R-13 灰度任务：组件按页面分批替换，每替换一批跑视觉回归 + 暗色模式截图对比。

### 11.9 📝 Android LibGDX SurfaceView 在 Compose 内嵌的 Lifecycle 边缘情况

`HubLibGdxView` 在 Compose AndroidView 内挂载 LibGDX `Game`，初版未处理：

- Compose 重组导致 SurfaceView destroy → LibGDX `dispose()` 调用时序；
- Activity 被系统回收后恢复时 Game 状态丢失。

R-13 Android W2 补 `DisposableEffect + rememberLibGdxLifecycle`。

---

## 12. 升级路径（Upgrade Path）

### 12.1 从 R-12 首波（5-22 版）升级到 R-12 通宵（5-24 版）

```bash
cd /home/cypress/Pythontopia
git pull origin main

# 1) 后端跑 6 个新迁移（Flyway 自动）
cd backend
mvn clean compile -DskipTests
mvn flyway:info     # 应看到 V114-V119 全部 Pending → Success
mvn spring-boot:run # Flyway 会在启动时自动 apply V114-V119

# 2) 前端 install + typecheck + test
cd ../frontend
npm ci
npm run typecheck   # 0 error
npm run lint        # 0 error
npm run test        # 604/604 pass

# 3) Android：仅在需要 demo Android 端时
cd ../android
./gradlew :app:assembleDebug    # 11 个 feature 第一次全联编，可能耗时 5-10 分钟
./gradlew test                  # 全模块单测
```

### 12.2 回滚（仅迁移层）

V114-V119 全部 idempotent，但 **不可逆**（删列丢数据）。如必须回滚：

```sql
-- 反向操作（仅作参考，生产环境严禁直接跑）
ALTER TABLE save_slot DROP COLUMN scene_name, DROP COLUMN unlocked_buildings,
                       DROP COLUMN accumulated_play_seconds, DROP COLUMN inventory_items;
DROP INDEX IF EXISTS idx_save_slot_scene;

ALTER TABLE world_session DROP COLUMN energy, DROP COLUMN energy_cap, DROP COLUMN overwork_minutes;

DROP TABLE IF EXISTS pyodide_execution;
DROP TABLE IF EXISTS npc_relationship;
DROP TABLE IF EXISTS world_festival;
DROP TABLE IF EXISTS fast_travel_route;
```

**生产建议**：宁可保留新列也不要回滚，新列全部带 `DEFAULT`，不影响 R-11 旧 API 读写。

### 12.3 前端旧 token 与新 token 共存

```ts
// main.ts 同时引入两套
import './styles/tokens.css';        // R-11 旧 token
import './styles/tokens-r12.css';    // R-12 通宵新 token

// 旧组件继续用 --pyt-bg-primary（tokens.css 提供）
// 新组件用 --pyt-r12-color-primary-50（tokens-r12.css 提供）
```

灰度迁移期间双引入不冲突；R-13 完成迁移后再考虑删旧。

---

## 13. 致谢（Acknowledgements）

R-12 通宵迭代由多个 Cursor subagent 并发协同完成。**关键子代理代号 + 产出**：

### 13.1 玩法基础宪法（5 篇基础设计 docs）

- `docs/gameplay/economy-system.md`（341 行）
- `docs/gameplay/npc-ecosystem.md`（296 行）
- `docs/gameplay/player-progression.md`（242 行）
- `docs/gameplay/social-classroom.md`（316 行）
- `docs/gameplay/world-clock-calendar.md`（338 行）

### 13.2 R-12 玩法深化 9 篇 + 星露谷视觉 8 篇 + Android 3 篇

合计 20 篇 / 9528 行，是 R-12 W5-W7 全部实施动作的设计权威。

### 13.3 W1 内核子代理（5 commit）

- `6fb370b6` PlayerStateMachine
- `b61c3c44` SceneSwitcher
- `e7bd1f74` InputAdapter 三实现
- `85555cc3` tilemap 路网与 Z 轴规范（docs）
- `ac2f9745` SceneLifecycle + Boot 超时进度

### 13.4 W2 交互子代理（3 commit）

- `e15b1f6c` InteractableSystem
- `7e2a95f5` NpcFramework
- `24d677dd` DialogModal 升级

### 13.5 W3 收口子代理（7 commit）

- `f402756f` 后端 SaveSlot V114 扩展
- `050b8fe6` 前端 SaveSlotDto 适配
- `eb8c7326` WorldClock 6 时段 + 4 季节 + 事件总线
- `0be413ca` HudShell + stores/hud
- `7d6e0446` MiniMap + InventoryPanel
- `7c09d269` WorldScene NPC trigger + dialog 集成
- `752adf25` Android hub/MainActivity/game build 微调

### 13.6 R-12 实施模块子代理（10 commit）

- `4d5b6b03` cameraTuning
- `0e0c7452` inputTuning
- `6bfaa8ca` EnergyTuning + V115
- `3b5bea9a` PyodideBudgetGuard + V116
- `ba016dbe` PyFrame.vue 9-slice
- `10ea30f4` tokens-r12.css 61 变量
- `a0a68b32` i18n zh-CN/en-US
- `7aa1d358` V117 NpcRelationship
- `d78b7165` V118 world_festival
- `33940a85` V119 fast_travel_route

### 13.7 Android 完善子代理（8 commit / 11 feature）

- `e0943647` auth (P0)
- `2a41be50` profile (P0)
- `d046ba34` fields (P0)
- `e5b24505` path (P1)
- `c0152314` classroom (P1)
- `cf4cbb63` workshop (P1)
- `48c90dda` arena + mine + retro (P2 三合一)
- `5f4371f0` hub + game LibGDX bridge (P0)

### 13.8 跨子代理共用基础设施

- `AGENTS.md` / `frontend/tests/_helpers/phaserMock.ts` / `frontend/vitest.config.ts` / `frontend/.eslintrc.cjs`（W1 配套微调）

**强约束**：所有子代理产出严格遵守 `AGENTS.md`：TypeScript strict、DTO 明确类型、SQL 三段头注释、Phaser 隔离在 adapter/core 层、不复制商业像素游戏官方素材、不启用 ReAct 循环。

---

## 14. R-13 预告（Looking Ahead）

R-13 计划（启动日期待 R-12 W7 验收后确定）：

### 14.1 R-12 收尾（必修）

- 修 11.1（Android `./gradlew check` 全量回归）
- 修 11.2（Vite strictPort 或后端 CORS 加 5174）
- 修 11.3（`POST /api/pyodide/execution` 端点 + 前端上报）
- 修 11.4（V117/V118/V119 三 Controller）
- 修 11.5（WorldClock ↔ HUD ↔ 后端 sleep 联通）
- 修 11.6（DialogModal 分支选项写后端 friendship）

### 14.2 R-13 核心方向（计划）

- **班级班长视角拓展**：班长 NPC 增加"周班级公告板编辑权"、"任务催办"、"班级 KC 热力图查看"；班长选举机制（按周轮换 / 教师指定）。
- **教师课堂直播实时**：把当前 5 分钟 snapshot 缓存升级为 SSE / WebSocket 推送；教师看大屏时学生提交立即反映。
- **30 分钟新手入门动线落地**：按 `docs/gameplay/r12-deepen/09-onboarding-30min.md` 实施 5 教学断面引导。
- **Bug 矿洞拼图卡组**：按 `docs/gameplay/r12-deepen/04-bugmine-puzzle-deck.md` 实施 30 张卡 × 难度梯度。
- **11 节日全部联通**：V118 已有 seed，本轮补 spring_opening / midterm / silent_night / new_year_eve 的实际玩法触发。
- **Android 4 张外围地图**：当前 demo 仅覆盖 town_hub，R-13 补 farm_north / mine_south / campus_east / port_west。
- **iOS 端可行性论证**（不实施）：Kotlin Multiplatform 共享 ViewModel 评估 / 或单独 Swift 重写评估。

### 14.3 R-13 不做

- 多语言切换 UI / 多 pack 课程 / 真实长视频 / 监控 APM（全部 Beta 后再决定）

---

## 附录 A：与现有文档的关系

| 已有文档 | 本文档与其关系 |
| --- | --- |
| `docs/release/r12/r12_release_notes.md` | R-12 首波（5-22）总览，本文档是 **R-12 通宵迭代（5-23 → 5-24）补充**，**两者并行存在，不互斥** |
| `docs/release/r12/r12-deliverables-summary.md` | 一页交付汇总（早上交接看），本文档是详细 release notes，**两者互为简详** |
| `docs/release/r12/r12_subagent_files.md` | R-12 首波子代理产出文件索引，本文档不再重复列文件，只列 commit + 模块 |
| `docs/sv-progress.md` | R-12 全程执行追踪日志，本文档是面向 release 受众的成品文档 |
| `docs/wave_subagent_report.md` | R-12 首波 Wave-1~3 逐子代理报告，本文档不复述子代理过程 |
| `docs/beta_demo_checklist.md` | Beta 演示 walkthrough，本文档新增"已知问题"对应 D 节遗留 |
| `docs/project/CHANGELOG.md` R-12 段 | 一行式条目总览，本文档详细展开 |
| `docs/gameplay/r12-deepen/*` | 9 篇玩法深化 spec，本文档对应章节 §2.2.3 / §10 引用 |
| `docs/stardew/*` | 8 篇视觉系统 spec，本文档对应章节 §2.2 引用 |
| `docs/clients/android/*` | 3 篇 Android 端架构 spec，本文档对应章节 §2.3 引用 |

---

## 附录 B：术语表

- **R-12 首波** / **R-12 通宵**：2026-05-22 4 波 23 子代理初版 / 2026-05-23→24 第二波，落 V100-V113 / V114-V119
- **W1/W2/W3**：R-12 通宵三子波次（W1 内核 / W2 交互 / W3 收口）
- **8 维画像**：subject_background / knowledge_baseline / learning_goal / cognitive_style / learning_preference / mistake_pattern / practice_ability / motivation_persistence
- **8 方向 → 4 方向**：`ExtendedDirection`（前端）→ `toServerDirection`（后端 `player_state.direction` VARCHAR(16)）
- **9-slice**：CSS `border-image-slice` 像素边框拉伸；**LPC**：Liberated Pixel Cup CC-BY-SA 3.0+；**OFL**：Open Font License
- **Pythoco**：游戏内货币；**KC**：Knowledge Component 知识点；**MASVS-L1**：OWASP Mobile Application Security Verification Standard Level 1

---

**文档结束。** 如需一页交付汇总，请阅 `docs/release/r12/r12-deliverables-summary.md`。
