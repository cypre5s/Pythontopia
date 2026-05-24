# R-12 通宵迭代 · 一页交付汇总

> 早班接手专用 · 5 分钟读完 · 详细 release notes 见 `docs/r12-release-notes-overnight.md`
> 周期：2026-05-23 22:30 → 2026-05-24 02:10（约 4 小时墙钟）
> 状态：交付完成 · 待早班验收

---

## 1. Commit 总数 + 链路图

**总计 29 个语义化 commit**（按时间倒序）：

```
2026-05-24 02:10  [Android · 8 commit / ≈15000 行 Kotlin]
  ├─ 5f4371f0  feat(android-hub-libgdx)    P0 hub + game LibGDX bridge        11 文件  1654 行
  ├─ 48c90dda  feat(android-p2)            P2 arena + mine + retro 三 feature  32 文件  4647 行
  ├─ cf4cbb63  feat(android-workshop)      P1 CodeLab 代码工坊                  11 文件  1692 行
  ├─ c0152314  feat(android-classroom)     P1 班级架构 教师/学生双视角           11 文件  2153 行
  ├─ e5b24505  feat(android-path)          P1 完善 path 学习路径                 11 文件  1525 行
  ├─ d046ba34  feat(android-fields)        P0 完善 fields KC 农场                11 文件  1492 行
  ├─ 2a41be50  feat(android-profile)       P0 完善 profile 8 维画像 dashboard    12 文件  1553 行
  └─ e0943647  feat(android-auth)          P0 完善 auth 12 文件可工作模块         12 文件  1684 行

2026-05-24 01:51  [R-12 实施模块 · 10 commit / 后端 3 service + 前端 7 模块]
  ├─ 33940a85  feat(backend-travel)        V119 fast_travel_route + Service       5 文件   439 行
  ├─ d78b7165  feat(backend-festival)      V118 world_festival + Service          5 文件   402 行
  ├─ 7aa1d358  feat(backend-npc)           V117 NpcRelationship + GiftLog + Svc   5 文件   564 行
  ├─ a0a68b32  feat(frontend-i18n)         zh-CN/en-US + microcopy 规范           4 文件   608 行
  ├─ 10ea30f4  feat(frontend-style)        tokens-r12.css 61 CSS 变量 + 暗色      2 文件   423 行
  ├─ ba016dbe  feat(frontend-ui)           PyFrame.vue 9-slice 框组件             2 文件   571 行
  ├─ 3b5bea9a  feat(pyodide)               PyodideBudgetGuard + V116 审计表       4 文件   549 行
  ├─ 6bfaa8ca  feat(energy)                EnergyTuning + V115 world_session 列   4 文件   628 行
  ├─ 0e0c7452  feat(frontend-config)       inputTuning 输入手感调参集中化         2 文件   290 行
  └─ 4d5b6b03  feat(frontend-config)       cameraTuning 镜头调参集中化            2 文件   409 行

2026-05-24 01:29  [设计 docs · 4 commit / 20 篇 / 9528 行]
  ├─ 89e1446c  docs(gameplay)              基础设计 5 篇                          5 文件  1533 行
  ├─ 79fb4e17  docs(android)               Android 端架构 3 篇                    3 文件  1530 行
  ├─ c229a71a  docs(stardew)               星露谷视觉系统 8 篇                    8 文件  3868 行
  └─ 06839958  docs(gameplay)              r12-deepen 玩法深化 9 篇               9 文件  4130 行

2026-05-24 01:29  [W3 收口 · 7 commit / HUD + WorldClock + WorldScene 集成]
  ├─ 752adf25  chore(android)              W3-6 hub/MainActivity/game build 微调  3 文件    19 行
  ├─ 7c09d269  feat(frontend-worldscene)   W3-5 WorldScene NPC trigger + dialog   2 文件   600 行
  ├─ 7d6e0446  feat(frontend-hud)          W3-4 MiniMap + InventoryPanel          4 文件   853 行
  ├─ 0be413ca  feat(frontend-hud)          W3-3 HudShell + stores/hud             4 文件   769 行
  ├─ eb8c7326  feat(frontend-game-core)    W3-2 WorldClock 6 时段 + 4 季节        2 文件   566 行
  ├─ 050b8fe6  feat(frontend-save)         W3-1b SaveSlotDto + world store 适配   3 文件   174 行
  └─ f402756f  feat(backend-save)          W3-1 save_slot V114 扩展 4 列          7 文件   401 行
```

**关键链路**：

```
Docs 设计先行（20 篇 9528 行）
       │
       ├──→  W1 内核（PlayerStateMachine / SceneSwitcher / InputAdapter / SceneLifecycle）  [R-12 首波已落]
       ├──→  W2 交互（InteractableSystem / NpcFramework / DialogModal 升级）              [R-12 首波已落]
       │
       ├──→  W3 收口（V114 + WorldClock + HudShell + MiniMap + InventoryPanel + WorldScene NPC）
       │                                                                                  ↓
       ├──→  R-12 模块（cameraTuning / inputTuning / EnergyTuning+V115 / PyodideBudget+V116
       │                / PyFrame / tokens-r12 / i18n / V117 NPC + V118 节日 + V119 快速旅行）
       │                                                                                  ↓
       └──→  Android 11 feature 完善（≈15000 行 Kotlin/Compose/LibGDX）

底色：测试基线 frontend 604/604 + backend 179/179 全过 + typecheck 0 错误 + lint 0 error
```

---

## 2. 文档清单（20 篇 / 9528 行）

| 路径 | 篇数 | 行数 | 说明 |
| --- | --- | --- | --- |
| `docs/gameplay/r12-deepen/01-input-feel-tuning.md` | 1 | 361 | 输入手感调参 |
| `docs/gameplay/r12-deepen/02-camera-follow.md` | 1 | 413 | 镜头跟随曲线 |
| `docs/gameplay/r12-deepen/03-energy-fatigue-curve.md` | 1 | 398 | 能量曲线 + 过劳保护 |
| `docs/gameplay/r12-deepen/04-bugmine-puzzle-deck.md` | 1 | 809 | Bug 矿洞 30 张拼图卡 |
| `docs/gameplay/r12-deepen/05-festival-events.md` | 1 | 484 | 11 里程碑节日 |
| `docs/gameplay/r12-deepen/06-gift-table.md` | 1 | 500 | 礼物表 30 道具×20 NPC×5 档 |
| `docs/gameplay/r12-deepen/07-fast-travel-network.md` | 1 | 302 | 快速旅行网络 10 条路线 |
| `docs/gameplay/r12-deepen/08-pyodide-budget.md` | 1 | 378 | Pyodide 配额 + 静态扫描 |
| `docs/gameplay/r12-deepen/09-onboarding-30min.md` | 1 | 485 | 30 分钟新手入门动线 |
| **玩法深化小计** | **9** | **4130** | |
| `docs/stardew/00-visual-system-overview.md` | 1 | 409 | 视觉总览（不是星露谷） |
| `docs/stardew/01-palette-tokens-css.md` | 1 | 550 | 调色板 + CSS tokens |
| `docs/stardew/02-frames-9slice-spec.md` | 1 | 498 | 9-slice 框规范 |
| `docs/stardew/03-character-walk-anim-spec.md` | 1 | 544 | LPC 4 向走路动画 |
| `docs/stardew/04-weather-particles-spec.md` | 1 | 496 | 天气粒子 4 类 |
| `docs/stardew/05-tilemap-decoration-spec.md` | 1 | 416 | 地图装饰物规范 |
| `docs/stardew/06-bgm-cuelist.md` | 1 | 462 | BGM cue 表 |
| `docs/stardew/07-ui-microcopy.md` | 1 | 493 | UI 微文案中英双语 |
| **星露谷视觉小计** | **8** | **3868** | |
| `docs/android/01-feature-port-checklist.md` | 1 | 503 | 11 feature 移植清单 |
| `docs/android/02-shared-state-contract.md` | 1 | 494 | 双端共享状态契约 |
| `docs/android/03-rendering-libgdx-vs-compose.md` | 1 | 533 | LibGDX + Compose 渲染方案 |
| **Android 端架构小计** | **3** | **1530** | |
| **20 篇 R-12 设计文档合计** | **20** | **9528** | |

**额外**：`docs/gameplay/` 5 篇基础设计（economy / npc / progression / social / world-clock）1533 行 由 `89e1446c` 一并提交，作为玩法 9 篇深化的上游宪法（不计入 20 篇统计）。

---

## 3. 代码清单

### 3.1 前端（Vue 3 + Phaser 3 + TypeScript strict）

| 模块 | 文件 | 行数 | Commit |
| --- | --- | --- | --- |
| `frontend/src/game/core/PlayerStateMachine.ts` | 1 | ≈250 | `6fb370b6` |
| `frontend/src/game/core/SceneSwitcher.ts` | 1 | ≈200 | `b61c3c44` |
| `frontend/src/game/adapters/input/*` | 3 | ≈400 | `e7bd1f74` |
| `frontend/src/game/core/SceneLifecycle.ts` | 1 | ≈180 | `ac2f9745` |
| `frontend/src/game/core/InteractableSystem.ts` | 1 | ≈220 | `e15b1f6c` |
| `frontend/src/game/core/NpcFramework.ts` | 1 | ≈260 | `7e2a95f5` |
| `frontend/src/components/game/DialogModal.vue` | 1 | ≈300（升级） | `24d677dd` |
| `frontend/src/game/core/WorldClock.ts` | 1 | 219 | `eb8c7326` |
| `frontend/src/components/hud/HudShell.vue` + `stores/hud.ts` | 2 | 376 + 122 | `0be413ca` |
| `frontend/src/components/hud/MiniMap.vue` + `InventoryPanel.vue` | 2 | 201 + 276 | `7d6e0446` |
| `frontend/src/game/scenes/WorldScene.ts`（+317 行） | 1 | 317（增量） | `7c09d269` |
| `frontend/src/game/config/cameraTuning.config.ts` | 1 | 181 | `4d5b6b03` |
| `frontend/src/game/config/inputTuning.config.ts` | 1 | 140 | `0e0c7452` |
| `frontend/src/game/config/energyTuning.config.ts` | 1 | 202 | `6bfaa8ca` |
| `frontend/src/services/pyodideBudget.ts` | 1 | 224 | `3b5bea9a` |
| `frontend/src/components/ui/PyFrame.vue` | 1 | 340 | `ba016dbe` |
| `frontend/src/styles/tokens-r12.css` | 1 | 159 | `10ea30f4` |
| `frontend/src/i18n/{index.ts,zh-CN.json,en-US.json}` | 3 | 87 + 162 + 162 | `a0a68b32` |
| `frontend/src/api/dto/SaveSlotDto.ts` + `stores/world.ts` 适配 | 2 | 19 + 39 | `050b8fe6` |
| **前端新增小计** | **≈27 文件** | **≈4854 行** | |

测试文件略多，见 §4。

### 3.2 后端（Spring Boot + Java 21 + Maven + Flyway）

| 模块 | 文件 | 行数 | Commit |
| --- | --- | --- | --- |
| V114 + `SaveSlot{,Repository,Service}` + `dto/SaveSlot{,Create}.java` | 7 | 401（净增） | `f402756f` |
| V115 + `EnergySchemaTest` | 2 | 36 + 206 | `6bfaa8ca` |
| V116 + `PyodideSchemaTest` | 2 | 30 + 105 | `3b5bea9a` |
| V117 + `NpcRelationship{,Repository,Service}` + Test | 5 | 564 | `7aa1d358` |
| V118 + `WorldFestival{,Repository,Service}` + Test | 5 | 402 | `d78b7165` |
| V119 + `FastTravel{Route,Repository,Service}` + Test | 5 | 439 | `33940a85` |
| **后端新增小计** | **≈26 文件** | **≈2183 行** | |

### 3.3 Android（Kotlin + Compose + LibGDX 1.13.1）

| Feature | 文件数 | 行数 | Commit |
| --- | --- | --- | --- |
| `feature/auth` | 12 | 1684 | `e0943647` |
| `feature/profile` | 12 | 1553 | `2a41be50` |
| `feature/fields` | 11 | 1492 | `d046ba34` |
| `feature/path` | 11 | 1525 | `e5b24505` |
| `feature/classroom` | 11 | 2153 | `c0152314` |
| `feature/workshop` | 11 | 1692 | `cf4cbb63` |
| `feature/arena + mine + retro` | 32 | 4647 | `48c90dda` |
| `feature/hub + :game LibGDX bridge` | 11 | 1654 | `5f4371f0` |
| `app + hub build 微调`（W3-6） | 3 | 19 | `752adf25` |
| **Android 新增小计** | **114 文件** | **≈16419 行**（约 15000 行业务 + 测试 + Hilt + Compose 模板） | |

**全 Android Kotlin 文件总数**（含 R-12 首波）：168 个；**全 Kotlin 总行数**：19156 行（其中 `:game` 模块 1486 行）。

### 3.4 文档

| 类别 | 文件 | 行数 | Commit |
| --- | --- | --- | --- |
| `docs/gameplay/r12-deepen/*` | 9 | 4130 | `06839958` |
| `docs/stardew/*` | 8 | 3868 | `c229a71a` |
| `docs/android/*` | 3 | 1530 | `79fb4e17` |
| `docs/gameplay/{economy,npc,progression,social,world-clock}-*.md` | 5 | 1533 | `89e1446c` |
| **设计文档合计** | **25 篇** | **11061 行** | |

### 3.5 全局新增汇总

| 维度 | 数量 |
| --- | --- |
| Commit | **29** 个语义化 commit |
| 新增源码文件（含测试） | 前端 ≈55 + 后端 ≈26 + Android 114 = **≈195 文件** |
| 新增代码行 | 前端 ≈4854 + 测试 ≈3200 + 后端 ≈2183 + Android ≈16419 + Docs 11061 = **≈37700 行** |
| 业务代码（不含 docs/测试） | **≈19400 行**（用户口述"≈15000 行 Android"为业务核心，其余 ≈4400 行为前后端） |

---

## 4. 测试基线

### 4.1 前端 vitest 604/604 通过

```
$ cd /home/cypress/Pythontopia/frontend
$ npm run test

Test Files  64 passed (64)
     Tests  604 passed (604)
  Duration  ~25s

$ npm run typecheck    # 0 error
$ npm run lint         # 0 error
```

**新增测试文件清单**（R-12 通宵 W1/W2/W3 + R-12 模块）：

| 测试 | 行数 |
| --- | --- |
| `tests/game/core/WorldClock.spec.ts` | 347 |
| `tests/game/scenes/WorldScene.spec.ts` | 292 |
| `tests/styles/tokens.spec.ts` | 264 |
| `tests/game/config/cameraTuning.spec.ts` | 228 |
| `tests/components/PyFrame.spec.ts` | 231 |
| `tests/components/MiniMap.spec.ts` | 211 |
| `tests/i18n/microcopy.spec.ts` | 197 |
| `tests/services/pyodideBudget.spec.ts` | 190 |
| `tests/game/config/energyTuning.spec.ts` | 184 |
| `tests/components/InventoryPanel.spec.ts` | 165 |
| `tests/game/config/inputTuning.spec.ts` | 150 |
| `tests/stores/hud.spec.ts` | 146 |
| `tests/components/HudShell.spec.ts` | 125 |
| `tests/stores/world.spec.ts`（W3-1b 适配） | 117 |
| **新增测试合计** | **≈2847 行** |

### 4.2 后端 mvn test 179/179 通过

```
$ cd /home/cypress/Pythontopia/backend
$ mvn test

Tests run: 179, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

$ mvn compile          # BUILD SUCCESS（288+ Java 源文件）
```

**新增测试类清单**（R-12 通宵 V114-V119 配套）：

| 测试类 | 用例数 | 说明 |
| --- | --- | --- |
| `SaveSlotExtensionTest` | ≈10 | V114 4 列 CRUD + 默认值 + JSONB |
| `EnergySchemaTest` | ≈8 | V115 3 CHECK + ALTER 幂等 |
| `PyodideSchemaTest` | ≈5 | V116 status 枚举 + 索引 DESC |
| `NpcRelationshipServiceTest` | ≈9 | V117 ±10 周限 + 跨周重置 |
| `WorldFestivalServiceTest` | 9 | V118 11 seed + status 切换 |
| `FastTravelServiceTest` | ≈10 | V119 11 路线 + phase + cost |
| **新增 @Test 合计** | **≈51 用例** | 全部 Testcontainers PostgreSQL 真实迁移验证 |

### 4.3 Android 测试

| 命令 | 状态 |
| --- | --- |
| 每 feature 模块单独 `:feature:xxx:testDebugUnitTest` | ✅ 子代理本地各自跑过（commit message 中"全过"声明） |
| `./gradlew :app:assembleDebug` 全量联编 | ⚠️ **本次窗口内未跑** |
| `./gradlew check`（全模块 lint + Detekt + Kover） | ⚠️ **本次窗口内未跑** |

**风险点**：见 §6.1。

---

## 5. V114-V119 迁移列表

| 迁移 | 表 | 操作 | 关键列 | Seed 行数 | 不可逆 | 设计文档 |
| --- | --- | --- | --- | --- | --- | --- |
| **V114** `player_save_state_extension` | `save_slot` | ALTER 加 4 列 | `scene_name` / `unlocked_buildings` / `accumulated_play_seconds` / `inventory_items` | – | 删列丢场景定位 / 解锁建筑 / 累计时长 / 背包快照 | W3-1 spec |
| **V115** `world_session_energy` | `world_session` | ALTER 加 3 列 + 3 CHECK | `energy [0,100]` / `energy_cap [60,110]` / `overwork_minutes >=0` | – | 删列绕过过劳保护一轮 | `r12-deepen/03-energy-fatigue-curve.md` |
| **V116** `pyodide_execution` | `pyodide_execution`（新） | CREATE | `user_id` FK / `started_at` / `duration_ms` / `status` 7 枚举 / `error_type` | – | 删表丢全部预览审计 | `r12-deepen/08-pyodide-budget.md` |
| **V117** `npc_friendship` | `npc_relationship`（新） + `npc_gift_log`（兼容 V102） | CREATE IF NOT EXISTS | `friendship [0,100]` / `last_gift_day` / `weekly_delta_sum` | – | 删表丢全部好感度缓存 | `r12-deepen/06-gift-table.md` |
| **V118** `world_festival` | `world_festival`（新） | CREATE + SEED | `day_index [1,112]` UNIQUE / `name_key` / `status pending/active/completed` / `override_payload` JSONB | **11** | 删表丢节日时间表 | `r12-deepen/05-festival-events.md` |
| **V119** `fast_travel_route` | `fast_travel_route`（新） | CREATE + SEED | `from_scene` / `to_scene` UNIQUE / `unlock_phase 0-3` / `cost_pythoco/energy/minutes` | **11** | 删表清空全部路线 | `r12-deepen/07-fast-travel-network.md` |

**Seed 总计**：V118 11 节日 + V119 11 路线 = **22 行**。

**Flyway 验证命令**：

```bash
cd /home/cypress/Pythontopia/backend
mvn flyway:info        # 应看到 V114-V119 全部 Pending → Success
mvn spring-boot:run    # 启动时自动 apply
```

---

## 6. 待做事项（早班接手第一周）

> 标 ⚠️ 为"演示前必修"，标 📝 为"R-13 处理"。详细背景见 `docs/r12-release-notes-overnight.md` §11。

### 6.1 ⚠️ Android 全量回归（最优先）

```bash
cd /home/cypress/Pythontopia/android
./gradlew clean
./gradlew :app:assembleDebug     # 期望 BUILD SUCCESSFUL，11 feature 全联编
./gradlew test                   # 全模块单测
./gradlew lintDebug              # Android Lint
# 可选：./gradlew detekt && ./gradlew koverHtmlReport
```

预计耗时：5-10 分钟。失败优先处理：跨模块 Hilt 注入图、`:app` NavGraph 装配、ProGuard/R8 配置。

### 6.2 ⚠️ Vite 5173 → 5174 fallback 修复

**二选一**：

- A. `frontend/vite.config.ts` 加 `server: { strictPort: true }`（推荐，明确失败信号）
- B. `backend/.../WebSecurityConfig.java` CORS allowlist 同步加 `http://localhost:5174`

### 6.3 ⚠️ Pyodide 配额上报 Controller

补 `POST /api/pyodide/execution` 端点 + 前端 `recordExecution` 后 fire-and-forget 上报，让 V116 `pyodide_execution` 表真正接到数据。

### 6.4 📝 V117/V118/V119 三 Controller

- `POST /api/world/npc/{key}/gift`
- `CalendarController.today` 追加 `activeFestival` 字段
- `POST /api/world/fast-travel`

### 6.5 📝 WorldClock ↔ HUD ↔ 后端 sleep 联通

`bootstrapGame()` 末尾调 `worldClock.syncFromServer(...)` + HudShell 通过 store getter 实时读 WorldClock 派生值 + sleep 推日触发 WorldClock 事件总线 + HUD 时段 / 季节 / 天气实时刷新。

### 6.6 📝 DialogModal 分支选项写后端

`onChoice` 事件总线收事件后调 `NpcDialogController.choose(npcKey, nodeId, optionIndex)`，让 W2-3 升级的分支选项真改 `npc_dialog_state.friendship + 1`。

### 6.7 📝 i18n locale 检测 + 切换 UI

登录页右上角加 locale 切换器 + 默认从 `navigator.language` 推导（`zh-CN` 还是 `en-US`）。

### 6.8 📝 tokens-r12.css 与 tokens.css 灰度迁移

按页面分批替换旧组件用新 token；每批跑视觉回归 + 暗色模式截图。

---

## 7. 重要警告（不要踩坑）

### 7.1 ⚠️ 不要直接 UPDATE `world_festival.status`

V118 表注释明确：**status 写权限只属于 `WorldFestivalService.onDayAdvanced / onDayEnded`**。其他 service / Controller 直接 UPDATE 视为代码 review 阻塞。教师 / 运营的运行时微调走 `override_payload JSONB`，不动 status。

### 7.2 ⚠️ 不要回滚 V114-V119（除非完全测试环境）

6 个迁移全部带 `DEFAULT`，不影响 R-11 旧 API 读写。**生产建议宁可保留新列也不要回滚**。删列丢数据 + 6 套 Service 全部失效。

### 7.3 ⚠️ 不要在业务层 `import 'phaser'`

R-12 首波 W4-LINT 配置：业务层禁止 `import 'phaser'`，强制走 `src/game/adapters/*`。R-12 通宵 W1 配套 ESLint 微调：仅 `game/core/**` 与 `tests/game/**` 豁免（PlayerStateMachine / NpcFramework 等内核确实需要直接用 Phaser 类型）。**新业务组件依然走 adapter**。

### 7.4 ⚠️ 不要把 PyodideBudgetGuard 当作后端权威

`PyodideBudgetGuard` 是 **纯前端闸门**（节流 + 教育提示）。后端 `pyodide_execution`（V116）是审计真相。当前 6.3 未做，**教师 dashboard 暂时没有数据**。R-13 W1 必补。

### 7.5 ⚠️ 不要复制商业像素游戏官方素材

AGENTS.md 硬约束：仅用 CC0 / CC-BY 3.0/4.0 / CC-BY-SA 3.0 / OFL-1.1 已审计资产。LPC CC-BY-SA 3.0 传染已接受：包装作品同时以 CC-BY-SA 3.0+ 发布，代码层保持 MIT。任何新素材必须在 `LICENSE-ART.md` 与 `frontend/public/assets/pixel/LICENSE.md` 同步更新。

### 7.6 ⚠️ 不要在 `:app` 之外的 Android 模块跑 Python

AGENTS.md 硬约束：Android 端 Beta 首发仅服务端判题。前端调 `POST /api/submission` 走后端，**不在客户端跑 Pyodide / CPython embedded**。

### 7.7 ⚠️ 不要把 Pinia 改成 Vuex / 不要散装 API 字段

AGENTS.md 禁止事项。所有 store 走 `defineStore`、所有 API 走 `frontend/src/api/dto/*` 明确 DTO 类型。新增 service / controller 必须配套 DTO + 校验 + 统一错误返回 + 测试。

### 7.8 ⚠️ 不要启用 ReAct 循环

AGENTS.md 硬约束：不启用 ReAct 循环，除非用户明确要求。多智能体协同走 `pythontopia-graph` 服务的结构化 LangGraph，不允许子代理在 service / scene 内嵌 ReAct 循环。

---

## 8. 三句话总结

1. R-12 通宵迭代以 **29 个语义化 commit** 完成 W1/W2/W3 内核收口、10 个 R-12 实施模块（前端 7 + 后端 V115/V116/V117/V118/V119 三 service + 6 个 V 迁移）和 11 个 Android feature 全部从骨架升级到 ≈15000 行可工作模块，前端 vitest **604/604** + 后端 mvn test **179/179** 全过、typecheck/lint 全零错。
2. **20 篇设计文档（9528 行）** 落地三大方向（玩法深化 9 + 星露谷视觉系统 8 + Android 端架构 3），全部成为 R-12 W5-W7 与 R-13 实施动作的设计权威，对应代码同步落地（WorldClock / HudShell / MiniMap / InventoryPanel / WorldScene NPC trigger / PyodideBudgetGuard 等）。
3. 早班接手第一周需优先做 **Android 全量 `./gradlew check` 回归 + Vite 5173 fallback 修复 + Pyodide 配额上报 Controller + V117/V118/V119 三 Controller + WorldClock↔HUD↔后端联通 + DialogModal 分支选项写后端**；不要回滚 V114-V119、不要在业务层 import phaser、不要把 PyodideBudgetGuard 当后端权威、不要复制商业像素游戏官方素材。

---

**文档结束。**
详细 release notes 见 `docs/r12-release-notes-overnight.md`。
本汇总 5 分钟内可读完，覆盖早班接手所需全部关键信息。
