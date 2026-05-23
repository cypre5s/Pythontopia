---
title: Pythontopia Android 11 Feature 移植清单
version: v1.0
date: 2026-05-24
status: Draft
owner: Android 端架构师
audience: Android 开发组 / Web 端架构师 / 后端架构师 / 比赛叙事
related:
  - android/docs/ADR-001-tech-selection.md
  - android/docs/roadmap.md
  - AGENTS.md
---

# Pythontopia Android 11 Feature 移植清单

## 1. 文档定位

本清单是 **Android Beta 首发 11 feature 实施路线图**，回答三个核心问题：

1. **现状**：11 个 feature 模块壳（`android/feature/<name>/`）当前各自已有多少代码、缺多少代码。
2. **顺序**：按 Beta / Beta+1 / Beta+2 三档优先级，确定 P0/P1/P2 移植顺序，避免"教学闭环未通就先做花活"。
3. **契约**：每个 feature 的 Web 端对照源码、Android 目标文件、Compose 组件清单、ViewModel/UseCase、API endpoint、状态管理、测试矩阵。

本清单 **不重复** ADR-001 的技术选型决策（Kotlin 2.2 / AGP 8.7.3 / Compose BOM 2025.04.00 / LibGDX 1.13.1 / 仅服务端判题），只在引用时给出锚点；**不解锁** R-100 之后的 Web 端代码迭代，Android 端独立编号 `Android-RN`。

> 边界：本清单仅覆盖 11 个 `:feature:*` 模块的移植；`:app / :core:* / :game` 共享层在第 5 章统一描述；OpenAPI Generator 流水线在第 6 章描述。

## 2. 当前进度评估表（2026-05-24 快照）

| Feature | 已有文件数 (`src/main`) | 已实现接口 | 已实现 UI | 缺失项 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| `:feature:auth` | 4（LoginScreen/RegisterScreen/LoginViewModel/AuthNavGraph） | `LoginUiState` MVVM + 500ms delay 桩 | 用户名/密码 OutlinedTextField + 登录按钮 | 接 `AuthApi.login`、`EncryptedSharedPreferences` JWT 持久化、`BiometricPrompt`、注册流、字段校验、错误码映射 | **P0** |
| `:feature:hub` | 4（HubScreen/HubViewModel/HubRepository/HubNavGraph） | `bootstrap()` + `today()` + `reportMove()` + `HubGameBridge` | LibGDX hub 渲染 + HUD 季节/天气 chip + NPC 在范围提示 + 退出登录 | 4 张外围地图（farm_north / mine_south / campus_east / port_west）切换、fade transition、minimap、fast travel 网络 | **P0** |
| `:feature:dialog` | 4（NpcDialogPanel/DialogViewModel/DialogRepository/DialogTree demo） | `loadState()` + `advance()` + 离线 fallback + 3 类 effect (`FRIENDSHIP_DELTA`/`SET_FLAG`/`COMPLETE_NODE`) | 节点文本 + 选项按钮 + 关闭 + 好感度展示 + 加载态 | 从 `assets/data/npcs/<key>/dialog.json` 加载真实数据、20 NPC 树、礼物 `giftNpc`、`quest_recommend` 节点、`give_item` effect | **P0** |
| `:feature:fields` | 2（FieldsScreen 占位/FieldsViewModel） | 无 | 占位 Column "Android-R8 实施中" | 65 KC 作物渲染（LibGDX `farm_north`）、`fetchCrops` + `fetchMastery` 接入、`KcDetailPanel` Compose、浇水/施肥/除草动作映射 | **P0** |
| `:feature:profile` | 3（ProfileScreen 占位/ProfileViewModel/ProfileNavGraph） | 无 | 占位 Column "8 维画像访谈待 Android-R7" | `openProfileInterview` + `sendProfileTurn` + `finishProfileInterview` 接入、`ProfileInterviewPanel` Compose 多轮聊天、4 维主观自评 PUT、Markdown 渲染 | **P0** |
| `:feature:path` | 2（PathScreen 占位/PathViewModel） | 无 | 占位 Column | `planLearningPath` + `getActiveLearningPath` + `getArchivedLearningPaths`、节点树渲染、归档 tab、规划按钮 | **P1** |
| `:feature:classroom` | 2（ClassroomScreen 占位/ClassroomViewModel） | 无 | 占位 Column | `listMyClassrooms` + `joinClassroom`（学生）、`createClassroom` + 7 个 teacher dashboard 接口、KC 热力图、班级任务发布、班级成员管理 | **P1** |
| `:feature:workshop` | 2（WorkshopScreen 占位/WorkshopViewModel） | 无 | 占位 Column | 6 类资源生成 UI（HANDOUT/MINDMAP/PRACTICE/CODE_LAB/READING/STORYBOARD）、Markdown/Mermaid/JSON 渲染、`EvidenceTag`、`feedback` LIKE/DISLIKE | **P1** |
| `:feature:arena` | 2（ArenaScreen 占位/ArenaViewModel） | 无 | 占位 Column "Android-R10 实施中" | 代码编辑器（推荐 `code-editor` Compose 库或 BasicTextField）、`createSubmission` + 轮询 `getSubmission`、Bug 矿洞跳转、最近提交列表 | **P2** |
| `:feature:mine` | 2（MineScreen 占位/MineViewModel） | 无 | 占位 Column | `openBugMineSession` + 6 类卡片解锁（COUNTER_EXAMPLE/VARIABLE_TRACE/WRONG_ASSUMPTION/HINT_LAYER_1/2/3）、Layer 渐进提示、`retryBugMineSubmit` 重试、矿洞场景渲染（可选 LibGDX 装饰层） | **P2** |
| `:feature:retro` | 2（RetroScreen 占位/RetroViewModel） | 无 | 占位 Column | `listEvaluations` + `runEvaluation` + `acceptEvaluationPath` + `keepOldEvaluationPath`、MetricCard、KcGrowthList、PathDiffView | **P2** |

> 已有文件数仅计 `src/main` 业务代码；不计 `src/test` 测试与 `build/` 生成物。当前 7/11 feature 仅有 `Screen + ViewModel` 占位骨架，预计需引入 35-45 个新 Composable 与 11-15 个新 Repository/UseCase。

## 3. 优先级总览

| 档位 | 阶段 | Feature 集合 | 教育闭环角色 | 验收节点 |
| --- | --- | --- | --- | --- |
| **P0** | Beta（Android-R4 ~ Android-R8） | auth / hub / dialog / fields / profile（5 项） | 登录 → 进入小镇 → 与镇长对话 → 看到知识田 → 完成画像访谈，跑通"播种 → 第一次浇水" | 真机 debug APK 能完成上述 5 步并截图，自动化测试通过 |
| **P1** | Beta+1（Android-R9 ~ Android-R11） | path / classroom / workshop（3 项） | 规划路径 → 加入班级领班级任务 → 在课程工坊生成讲义/思维导图 | 路径 + 班级 + 工坊三屏均能拉真实后端数据，6 类资源至少 3 类渲染达标 |
| **P2** | Beta+2（Android-R12 ~ Android-R14） | arena / mine / retro（3 项） | 训练场提交代码 → 失败进入矿洞调试 → 复盘大厅评估并接受新路径 | 服务端判题 + 6 卡片 + 评估 accept/keep 全链路打通，无前端假数据 |

**P0 原则**：Beta 不强求 11 feature 全可用，但 5 项 P0 必须保证教学叙事可演示（"Pythontopia 是怎么学 Python 的"）。任何 P1/P2 不得回头削减 P0 验收口径。

**P1 原则**：路径 → 班级 → 工坊三件套是"被动学习"骨架；可在不接 P2 判题的情况下展示班级管理与资源生成。

**P2 原则**：训练场/矿洞/复盘是"主动练习"骨架，依赖后端 `SubprocessPythonSandbox` 与 `BugMineService`；如 Beta+1 期间后端接口未完成回归测试，可整体回退到 P1 演示版本。

## 4. 每 Feature 移植子清单

### 4.1 `:feature:auth`（P0 / Android-R4）

| 维度 | 内容 |
| --- | --- |
| Web 对照 | `frontend/src/pages/LoginPage.vue` + `RegisterPage.vue` + `components/auth/LoginForm.vue` + `RegisterForm.vue` + `api/auth.ts` + `stores/session.ts` |
| Android 目标 | `kotlin/com/pythontopia/feature/auth/{LoginScreen,RegisterScreen,LoginViewModel,RegisterViewModel,AuthNavGraph,SessionRepository,BiometricGate}.kt` |
| Compose 组件 | `LoginScreen` / `RegisterScreen` / `BiometricPromptBox` / `PasswordStrengthMeter` / `ErrorBanner` / `FormFieldRow` |
| ViewModel + UseCase | `LoginViewModel` + `RegisterViewModel` + `LoginUseCase`（封装 `AuthApi.login` + JWT 写盘 + Biometric 注册） + `BiometricUnlockUseCase` |
| LibGDX 参与 | 否，纯 Compose |
| API endpoint | `POST /api/auth/login` / `POST /api/auth/register`；`/api/auth/refresh` 留 501（ADR-001 §1 决策 13） |
| 状态管理 | `MutableStateFlow<LoginUiState>` MVVM；JWT 持久化用 `EncryptedSharedPreferences` (`androidx.security.crypto`) + Keystore；Session 提升到 `:core:common` 单例 `SessionRepository` |
| 测试 | unit：`LoginViewModelTest`（已有）补真实 API 桩 / Compose UI test：用户名密码空校验 + 错误提示 / Roborazzi：登录页 light/dark 两套快照 |

**实施步骤**：① 删除 `LoginViewModel.login()` 的 500ms `delay` 桩；② 注入 `AuthApi` + `SessionRepository`；③ 成功后写 JWT 与 userId 到 `EncryptedSharedPreferences`；④ 在 `MainActivity.onCreate` 检查 token 与生物识别可用性，命中则跳过 `LoginScreen`；⑤ Register 表单加密码强度校验（≥ 8 字符 + 字母数字混合）。
**已知坑**：MIUI / OneUI 部分 ROM 的 `BiometricPrompt` 在密钥 `setUserAuthenticationRequired(true)` 模式下 setupkey 异常，需 fallback 到 `BIOMETRIC_WEAK`；首发支持 4 个 OEM 机型已在 §10 风险表登记。

### 4.2 `:feature:hub`（P0 / Android-R6, R-13）

| 维度 | 内容 |
| --- | --- |
| Web 对照 | `frontend/src/pages/WorldPage.vue` + `components/game/WorldCanvas.vue` + `game/scenes/WorldScene.ts` + `game/core/WorldClock.ts` + `stores/world.ts` |
| Android 目标 | `kotlin/com/pythontopia/feature/hub/{HubScreen,HubViewModel,HubRepository,HubNavGraph,MapTransitionFader,MiniMapPanel,FastTravelDialog}.kt`；`:game` 模块新增 `FarmGame/MineGame/CampusGame/PortGame` |
| Compose 组件 | `HubScreen`（已有） / `HubHud` / `HudChip` / `MapTransitionFader` / `MiniMapPanel` / `FastTravelDialog` / `WorldClockBadge` |
| ViewModel + UseCase | `HubViewModel`（已有） + `BootstrapWorldUseCase` + `ChangeMapUseCase` + `ReportPlayerMoveUseCase` + `LoadCalendarUseCase` |
| LibGDX 参与 | **是**，主渲染层；`HubGame / FarmGame / MineGame / CampusGame / PortGame` 共用 `HubGameBridge`，map_key 切换由 ViewModel 派发 |
| API endpoint | `POST /api/game/bootstrap` / `GET /api/world/calendar/today` / `PATCH /api/world/player` / `GET /api/world/inventory` / `POST /api/world/sleep` |
| 状态管理 | `StateFlow<HubUiState>` + `HubGameBridge` 单实例桥（已有）；`mapKey` 切换走 `MutableSharedFlow<MapTransition>` 触发 fade |
| 测试 | unit：`HubViewModelTest`（已有）补 mapKey 切换分支 / `TownGameLauncherTest`（已有）保留 / Compose UI：bridge poll → state 更新 / Roborazzi：4 张地图入口截图 |

**实施步骤**：① 在 `:game` 模块以 `MapScene` 基类抽出 `HubScene` 公共逻辑（输入 / NPC 检测 / 边缘退出）；② 复制出 `FarmScene/MineScene/CampusScene/PortScene` 4 个子类，分别加载 `farm_north.tmx / mine_south.tmx / campus_east.tmx / port_west.tmx`；③ `HubGame` 接收 `mapKey` 切换指令，重建 `Scene` 实例（不重建 `Game`）；④ Compose 侧 `MapTransitionFader` 用 `AnimatedVisibility` 做 200ms 黑屏渐变；⑤ HUD 增加 `MiniMapPanel` 与 `InventoryPanel` 抽屉。
**已知坑**：5 张地图在 LibGDX `TmxMapLoader` 首次加载时长 200-400ms，会卡到主线程，必须在 `Scene.show()` 中起 `Thread { ... }` 异步加载 + 主线程 swap；fade 时间需 ≥ 加载耗时上限。

### 4.3 `:feature:dialog`（P0 / Android-R12 / Android-R13）

| 维度 | 内容 |
| --- | --- |
| Web 对照 | `frontend/src/components/game/DialogModal.vue` + `DialogTree.vue` + `api/worldNpc.ts` + `stores/dialog.ts` + `stores/npc.ts` + `data/npcs/<key>/dialog.json` |
| Android 目标 | `kotlin/com/pythontopia/feature/dialog/{NpcDialogPanel,DialogViewModel,DialogRepository,DialogTree,DialogTreeLoader,GiftPickerSheet,FriendshipBadge}.kt` |
| Compose 组件 | `NpcDialogPanel`（已有） / `DialogHeader` / `DialogNodeBody` / `LoadingBlock` / `GiftPickerSheet` / `FriendshipBadge` / `QuestRecommendCard` |
| ViewModel + UseCase | `DialogViewModel`（已有）+ `LoadDialogTreeUseCase`（从 assets/data/npcs/<key>/dialog.json 解析）+ `AdvanceDialogUseCase` + `GiftNpcUseCase` |
| LibGDX 参与 | 否，纯 Compose（但接受 `HubViewModel.bridge` 派发的 `NpcInRange` 事件） |
| API endpoint | `GET /api/world/npcs/{key}/state` / `POST /api/world/npcs/{key}/talk` / `POST /api/world/npcs/{key}/gift` |
| 状态管理 | `StateFlow<DialogUiState>`；本地 `DialogTree` 缓存 `Map<String, DialogNode>`；assets 解析走 `kotlinx-serialization-json` |
| 测试 | unit：`DialogTreeTest`（已有）覆盖 20 NPC 树拓扑 / `DialogRepositoryTest`（已有）增加 gift 与离线场景 / Compose UI：选项点击 → state 更新 / Roborazzi：好感度 0 / 50 / 100 三档样式 |

**实施步骤**：① `frontend/scripts/sync-npc-data.mjs` 在 `predev / prebuild` 钩子已写过同步脚本，新增 `android/scripts/sync-npc-data.sh` 把 `data/npcs/<key>/{meta,dialog}.json` 复制到 `:app/src/main/assets/data/npcs/`；② `DialogTreeLoader` 用 `AssetManager.open()` 读取并以 `kotlinx-serialization-json` 解析；③ 把 `DemoDialogTree` 收敛为 fallback（仅 `mayor_py` 1 棵树），其它 19 NPC 走 assets；④ 引入 `GiftPickerSheet`（`ModalBottomSheet` + 背包 `InventoryItemDto` 列表）；⑤ `quest_recommend` 节点点击跳转到 `:feature:path` `nav("path?questId=...")`。
**已知坑**：模板插值 `{{playerName}}` 必须在渲染前用 `SessionRepository.user.displayName` 替换，避免 demo 文案外泄；`set_flag` 与后端 `JsonNode` 自由结构对齐时收敛为 `Map<String, String>`（同 ADR-001 §1 决策 14）。

### 4.4 `:feature:fields`（P0 / Android-R8）

| 维度 | 内容 |
| --- | --- |
| Web 对照 | `frontend/src/pages/FieldPage.vue` (重定向到 World) + `components/game/FieldCanvas.vue` + `components/field/KcDetailPanel.vue` + `api/field.ts` + `stores/learning.ts` |
| Android 目标 | `kotlin/com/pythontopia/feature/fields/{FieldsScreen,FieldsViewModel,FieldsRepository,KcDetailPanel,CropTileItem,MasteryGrowthBar,KcFilterBar}.kt`；`:game` 模块在 `FarmGame` 中按 `CropTileDto` 渲染地块 |
| Compose 组件 | `FieldsScreen` / `KcDetailPanel` / `CropTileItem` / `MasteryGrowthBar` / `KcFilterBar`（按章节筛选） / `WaterFertilizeWeedActions`（动作按钮） |
| ViewModel + UseCase | `FieldsViewModel` + `LoadCropsUseCase` + `LoadMasteryUseCase`（合并 `fetchCrops` + `fetchMastery`） + `BootstrapCurriculumUseCase`（首次进入触发） |
| LibGDX 参与 | **是**（仅 `FarmGame` 子场景）；65 KC 作物按 `tileX/tileY` 渲染，成长度由 `mastery * 100` 映射到 4 个 sprite 帧 |
| API endpoint | `GET /api/world/crops` / `GET /api/learner/mastery` / `POST /api/learner/curriculum/bootstrap` |
| 状态管理 | `StateFlow<FieldsUiState>`（`crops: List<CropTileDto>` + `mastery: Map<String, MasterySnapshotDto>`）；`Room` 本地缓存 `crop_tile` 表加速冷启动 |
| 测试 | unit：`FieldsViewModelTest`（已有）补齐数据合并逻辑 / Compose UI：KcDetailPanel 点击展开 / Roborazzi：4 种作物成长度截图 |

**实施步骤**：① `FarmGame`（继承 `MapScene`）在 `create()` 时一次性加载 65 KC 的 `kc_code → tileX/tileY` 索引；② Hub 派发 `ChangeMap("farm_north")` 后，`FieldsViewModel.bootstrap()` 并发拉 `fetchCrops + fetchMastery`，合并为 `FieldCropVm(kcCode, x, y, masteryLevel, growthStage)`；③ `growthStage = floor(mastery * 4)` 映射到 sprite 帧；④ Compose 层 `KcDetailPanel` 用 `AnimatedVisibility` 滑入，包含浇水/施肥/除草按钮（动作映射到 `:feature:workshop / :feature:arena` 跳转）。
**已知坑**：首次 `bootstrapCurriculum` 接口耗时 3-8s（后端要导入 course-pack-43），UI 需显式 loading 提示；65 KC 在 6.7 寸屏 1×1 tile 渲染时会贴边，需 `Camera.zoom = 1.5f`。

### 4.5 `:feature:profile`（P0 / Android-R7）

| 维度 | 内容 |
| --- | --- |
| Web 对照 | `frontend/src/pages/ProfileRoomPage.vue` + `components/profile/ProfileInterviewPanel.vue` + `api/learner.ts` + `stores/learning.ts` `profileInterview` 子状态 |
| Android 目标 | `kotlin/com/pythontopia/feature/profile/{ProfileScreen,ProfileViewModel,ProfileRepository,ProfileNavGraph,InterviewChatList,DimensionEditCard,ProfileSummaryCard,EvidenceAnchorList}.kt` |
| Compose 组件 | `ProfileScreen` / `InterviewChatList` / `ChatBubble` / `DimensionEditCard` / `ProfileSummaryCard` / `EvidenceAnchorList` / `SafetyBadge` |
| ViewModel + UseCase | `ProfileViewModel` + `OpenInterviewUseCase` + `SendTurnUseCase` + `FinishInterviewUseCase` + `UpdateDimensionUseCase`（4 维主观自评 PUT） + `ListRevisionsUseCase` |
| LibGDX 参与 | 否，纯 Compose（画像屋 NPC 静态背景图即可，不渲染地图） |
| API endpoint | `GET /api/learner/profile` / `POST /api/learner/profile/interview` / `POST /api/learner/profile/interview/{sessionId}/turn` / `POST /api/learner/profile/interview/{sessionId}/finish` / `PUT /api/learner/profile/dimensions/{key}` / `GET /api/learner/profile/revisions` |
| 状态管理 | MVI（`ProfileIntent` sealed + `ProfileUiState`）：访谈是复杂多状态（chat history + draft + done + dimensionsFilled + evidence），按 ADR-001 §1 决策 10 走 MVI |
| 测试 | unit：`ProfileViewModelTest`（已有）补 turn 推进 + dimension 自评 / Compose UI：长消息滚动 + 自评 dialog / Roborazzi：8 维填充进度截图 |

**实施步骤**：① 进入 `ProfileScreen` 自动 `openInterview()`，把 `interviewSessionId` 存 `savedStateHandle` 防进程被杀丢失；② `InterviewChatList` 用 `LazyColumn` + `key(messageId)`；③ 每次 `sendProfileTurn` 后渲染 `ProfileTurnResponse.evidenceAnchors` 到 `EvidenceAnchorList`（点击可查看引用页）；④ `dimensionsFilled` 反向高亮 8 维 chip；⑤ 4 维主观自评（`learning_goal / cognitive_style / learning_preference / emotion_persistence`）走 `DimensionEditCard` + `PUT /api/learner/profile/dimensions/{key}`。
**已知坑**：`ProfileAgent` 的回复偶尔超 2KB，`Text` 单段渲染会卡，必须按段落拆分到多个 `Text` Composable；`safetyResult / groundingResult` 任一 `passed=false` 时 UI 必须红色提示并禁止继续，遵守 AGENTS.md "防幻觉" 约束。

### 4.6 `:feature:path`（P1 / Android-R9）

| 维度 | 内容 |
| --- | --- |
| Web 对照 | `frontend/src/pages/BoardPage.vue` `PathTreeTab` + `ArchivedPathsTab` + `api/path.ts` + `stores/learning.ts` `path` 子状态 |
| Android 目标 | `kotlin/com/pythontopia/feature/path/{PathScreen,PathViewModel,PathRepository,PathNavGraph,PathTreeView,PathNodeCard,ArchivedPathList,PlanPathButton}.kt` |
| Compose 组件 | `PathScreen` / `PathTreeView`（递归节点） / `PathNodeCard` / `ArchivedPathList` / `PlanPathButton` / `PathNodeStatusChip` |
| ViewModel + UseCase | `PathViewModel` + `LoadActivePathUseCase` + `LoadArchivedPathsUseCase` + `PlanPathUseCase` + `OpenPathDetailUseCase` |
| LibGDX 参与 | 否，纯 Compose |
| API endpoint | `POST /api/learner/path/plan` / `GET /api/learner/path/active` / `GET /api/learner/path/archived` / `GET /api/learner/path/{id}` |
| 状态管理 | `StateFlow<PathUiState>`（active + archived + planning）；MVVM，节点 ≤ 30 个不需要 MVI |
| 测试 | unit：`PathViewModelTest`（已有）补 plan 触发 / Compose UI：节点点击展开 / Roborazzi：空路径 / 7 节点 / 归档 3 种状态 |

**实施步骤**：① 进入 `PathScreen` 默认 tab "本周路径"，并发拉 `getActiveLearningPath + getTodayQuests`；② `PathTreeView` 用递归 Composable 渲染节点树，节点状态：`待启动 / 进行中 / 已完成 / 已跳过` 四色 chip；③ `PlanPathButton` 点击调用 `planLearningPath`，loading 500ms 内不可重复点；④ "历史路径" tab 用 `getArchivedLearningPaths` 列表 + 点击进入只读详情；⑤ 节点点击跳到对应 feature（`HANDOUT → :feature:workshop`、`PRACTICE → :feature:arena`、`READING → :feature:workshop`）。
**已知坑**：路径节点的 `evidence` 校验在后端，UI 仅展示，不要本地伪造完成；`planLearningPath` 调用 graph 服务平均 5-15s，必须显式 loading 并禁止重复点。

### 4.7 `:feature:classroom`（P1 / Android-R14, R-13 学生视角）

| 维度 | 内容 |
| --- | --- |
| Web 对照 | `frontend/src/pages/TeacherPage.vue` + `pages/teacher/TeacherHome.vue` + `ClassroomDetail.vue` + `components/teacher/*.vue` + `components/board/ClassroomQuestsTab.vue` + `api/teacher.ts` + `api/classroom.ts` + `api/classroomQuests.ts` + `stores/teacher.ts` |
| Android 目标 | `kotlin/com/pythontopia/feature/classroom/{ClassroomScreen,ClassroomViewModel,ClassroomRepository,TeacherHomeScreen,ClassroomDetailScreen,KcHeatmapView,QuestPublishSheet,MemberList,ErrorHotspotsList,ResourceUsageChart}.kt` |
| Compose 组件 | `ClassroomScreen`（学生） / `TeacherHomeScreen` / `ClassroomDetailScreen` / `KcHeatmapView`（自绘 Compose Canvas） / `QuestPublishSheet`（ModalBottomSheet） / `MemberList` / `ErrorHotspotsList` / `ResourceUsageChart`（Compose Canvas 柱状图） |
| ViewModel + UseCase | `ClassroomViewModel` + `TeacherClassroomViewModel`（拆 2 个 ViewModel 避免单 store 膨胀） + `ListMyClassroomsUseCase` + `JoinClassroomUseCase` + `CreateClassroomUseCase` + 5 个 Dashboard UseCase（KC 热力图 / 任务统计 / 误区 / 资源使用 / 最近活动） |
| LibGDX 参与 | 否，纯 Compose |
| API endpoint | `POST/GET /api/classrooms` / `POST /api/classrooms/join` / `GET /api/classrooms/{id}/members` / `PUT /api/teacher/classrooms/{id}` / `POST /api/teacher/classrooms/{id}/regenerate-code` / `POST /api/teacher/classrooms/{id}/quests` / `GET /api/teacher/classrooms/{id}/dashboard/{kc-heatmap,quest-summary,error-hotspots,resource-usage,recent-activity}` |
| 状态管理 | `StateFlow<ClassroomUiState>` + `StateFlow<TeacherUiState>`；班级列表 + Dashboard 数据各走独立 StateFlow，避免重组爆炸 |
| 测试 | unit：`ClassroomViewModelTest`（已有）补 join / Dashboard 拆 5 个测试 / Compose UI：QuestPublishSheet 校验 / Roborazzi：KC 热力图 + 资源使用柱状图 |

**实施步骤**：① 学生与教师视角共用 `ClassroomScreen` 但按 `SessionRepository.role == TEACHER` 分发；② `TeacherHomeScreen` 列出我创建的班级 + "新建" FAB；③ `ClassroomDetailScreen` 4 tab：成员 / 任务 / KC 热力图 / 误区 + 资源使用；④ `KcHeatmapView` 用 Compose Canvas 自绘 65 KC × ≤ 60 人格子，颜色按 mastery 区间映射；⑤ `QuestPublishSheet` 校验必填（title / kc_codes / due_at）后调 `publishClassroomQuest`。
**已知坑**：Teacher Dashboard 5 个接口并发拉取，需用 `coroutineScope { async { ... } }` 限制并发到 3 个；KC 热力图首次绘制 > 200ms 时考虑 `RecyclerView` 降级。

### 4.8 `:feature:workshop`（P1 / Android-R9）

| 维度 | 内容 |
| --- | --- |
| Web 对照 | `frontend/src/pages/WorkshopPage.vue` + `components/workshop/{WorkshopPanel,ResourceListPanel,ResourceDetailPanel,HandoutView,MindMapView,PracticeList,CodeLabView,ReadingList,StoryboardView,EvidenceTag}.vue` + `api/workshop.ts` |
| Android 目标 | `kotlin/com/pythontopia/feature/workshop/{WorkshopScreen,WorkshopViewModel,WorkshopRepository,ResourceListPane,ResourceDetailPane,HandoutRenderer,MindMapRenderer,PracticeRenderer,CodeLabRenderer,ReadingRenderer,StoryboardRenderer,EvidenceTag,FeedbackBar}.kt` |
| Compose 组件 | `WorkshopScreen` / `WorkshopCreateForm`（KC 多选 + 类型单选 + focusHint） / `ResourceListPane` / `ResourceDetailPane` / 6 个 Renderer（按 `WorkshopResourceType` 分发） / `EvidenceTag` / `FeedbackBar`（LIKED/DISLIKED） |
| ViewModel + UseCase | `WorkshopViewModel`（list） + `WorkshopDetailViewModel`（detail） + `CreateResourceUseCase` + `ListResourcesUseCase` + `GetResourceUseCase` + `SubmitFeedbackUseCase` |
| LibGDX 参与 | 否；Mermaid 渲染走 `Markwon` + `MarkwonPlugin`（或 `compose-richtext`），不集成 Web 端 Mermaid.js |
| API endpoint | `POST /api/workshop/resources` / `GET /api/workshop/resources` / `GET /api/workshop/resources/{id}` / `POST /api/workshop/resources/{id}/feedback` |
| 状态管理 | MVI（`WorkshopIntent` + `WorkshopUiState`）：生成是异步多阶段流程（idle → submitting → grounding_checking → success/failed），按 ADR-001 §1 决策 10 走 MVI；`WorkshopContent` sealed union 由手工补丁层定义，避免 OpenAPI Generator 不支持 |
| 测试 | unit：`WorkshopViewModelTest`（已有）覆盖 6 类资源解析 / Compose UI：每类 Renderer 至少 1 个快照 / Roborazzi：HANDOUT + MINDMAP 截图（光暗双模式） |

**实施步骤**：① 表单：`KcMultiPickRow`（来自 `:feature:fields` 暴露的 KC 列表） + `ResourceTypeRadio` + `FocusHintField`；② 提交后跳到 `ResourceDetailPane`；③ 按 `WorkshopResourceDto.type` 分发到 6 个 Renderer：HANDOUT 渲染 `HandoutSection[]`、MINDMAP 渲染 Mermaid 文本（Markwon `SyntaxHighlightPlugin` + `ImagePlugin` 渲染为 SVG）、PRACTICE 渲染 `ProblemSpec[]` 卡片、CODE_LAB 渲染 `Walkthrough[]` 代码片段、READING 渲染 `ReadingItem[]` summary、STORYBOARD 渲染 `StoryboardScene[]` 时间线；④ `EvidenceTag` 显示 `evidencePageIds`，点击展示对应 `course-pack-43` 页码引用。
**已知坑**：Mermaid 在 Android 端可走 `Markwon` + `MermaidPlugin`（第三方）或 WebView fallback（不推荐，体积大）；Beta 首发 MINDMAP 可降级为"显示 Mermaid 源码 + 跳浏览器渲染"。

### 4.9 `:feature:arena`（P2 / Android-R10）

| 维度 | 内容 |
| --- | --- |
| Web 对照 | `frontend/src/pages/TrainingPage.vue` + `components/training/TrainingPanel.vue` + `api/submissions.ts` + `api/problems.ts` + `stores/learning.ts` `training` 子状态 |
| Android 目标 | `kotlin/com/pythontopia/feature/arena/{ArenaScreen,ArenaViewModel,ArenaRepository,ProblemStatementCard,CodeEditorPane,SubmissionResultCard,RecentSubmissionsList,SampleIoView}.kt` |
| Compose 组件 | `ArenaScreen` / `ProblemStatementCard`（Markdown 渲染） / `CodeEditorPane`（基于 `BasicTextField` + 语法高亮 `KotlinSyntaxHighlighter` 简化版） / `SampleIoView` / `SubmissionResultCard` / `RecentSubmissionsList` / `OpenMineButton`（失败时跳 `:feature:mine`） |
| ViewModel + UseCase | `ArenaViewModel` + `LoadProblemUseCase` + `CreateSubmissionUseCase` + `PollSubmissionUseCase`（每 1.5s 轮询直到 status != PENDING） + `ListRecentSubmissionsUseCase` |
| LibGDX 参与 | 否，纯 Compose；**严禁集成 Pyodide / Chaquopy**（ADR-001 §1 决策 6） |
| API endpoint | `GET /api/problems/{id}/public` / `POST /api/submissions` / `GET /api/submissions/{id}` / `GET /api/submissions?problemId={id}` |
| 状态管理 | MVI（`ArenaIntent` + `ArenaUiState`）：提交状态机有 IDLE / EDITING / SUBMITTING / POLLING / SUCCESS / FAILED / RUNTIME_ERROR 7 个状态，按 ADR-001 §1 决策 10 走 MVI |
| 测试 | unit：`ArenaViewModelTest`（已有）覆盖三类提交结果（通过/失败/运行错误） / Compose UI：编辑器输入 + 提交按钮 / Roborazzi：三类结果卡片截图 |

**实施步骤**：① 默认进入题目 476（与 Web 端 `DEFAULT_PROBLEM_ID = 476` 保持一致）；② `ProblemStatementCard` 用 `Markwon` 渲染 `statement_md`；③ `CodeEditorPane` 基于 `BasicTextField` + `VisualTransformation` 实现简化高亮（关键字 / 字符串 / 数字 三色）；④ `createSubmission` 后开始 1.5s 间隔轮询 `getSubmission`，超时 60s 强制断开；⑤ `SubmissionResultCard` 按 status 渲染三类结果，FAILED 时显示 `OpenMineButton` 跳 `:feature:mine`。
**已知坑**：禁止集成 Pyodide / Chaquopy（ADR-001 §1 决策 6 + AGENTS.md "Pyodide 不能当正式判题结果"）；轮询必须用 `viewModelScope.launch { while (isActive) ... }` 保证 ViewModel 销毁时停止。

### 4.10 `:feature:mine`（P2 / Android-R11）

| 维度 | 内容 |
| --- | --- |
| Web 对照 | `frontend/src/pages/BugMinePage.vue` (重定向到 World) + `components/bugmine/BugMinePanel.vue` + `api/bugMine.ts` + `stores/learning.ts` `bugMine` 子状态 |
| Android 目标 | `kotlin/com/pythontopia/feature/mine/{MineScreen,MineViewModel,MineRepository,MineCardGrid,CardReveal{CounterExample,VariableTrace,WrongAssumption,HintLayer1,HintLayer2,HintLayer3},RetrySubmitPane}.kt`；`:game` 模块 `MineGame` 提供矿洞场景装饰 |
| Compose 组件 | `MineScreen` / `MineCardGrid`（3×2 卡片网格） / 6 个 `CardReveal{Type}` Renderer / `RetrySubmitPane` / `CardLockOverlay` / `LayerProgressBar` |
| ViewModel + UseCase | `MineViewModel` + `OpenSessionUseCase`（基于失败的 submissionId） + `GetSessionUseCase` + `RevealCardUseCase` + `RetrySubmitUseCase` + `CloseSessionUseCase` |
| LibGDX 参与 | **是**（仅装饰层）；`MineGame` 在 `mine_south` 地图上渲染矿洞背景 + 玩家像素角色，Compose 卡片浮在上层 |
| API endpoint | `POST /api/bug-mine/sessions` / `GET /api/bug-mine/sessions/{id}` / `POST /api/bug-mine/sessions/{id}/cards/{cardId}/reveal` / `POST /api/bug-mine/sessions/{id}/retry-submit` / `POST /api/bug-mine/sessions/{id}/close` |
| 状态管理 | MVI（`MineIntent` + `MineUiState`）：6 张卡片 × revealed/locked + retry status + layer progression，状态字段 ≥ 4 走 MVI |
| 测试 | unit：`MineViewModelTest`（已有）覆盖 6 类卡片解锁顺序 + retry 成功/失败 / Compose UI：卡片解锁动画 / Roborazzi：3 个 Layer 渐进截图 |

**实施步骤**：① 从 `:feature:arena` 提交失败时携带 `submissionId` 跳转到 `MineScreen?submissionId=...`；② `openBugMineSession` 创建后展开 6 卡片网格，初始全部 lock，按 Layer 1 → Layer 2 → Layer 3 顺序解锁（前一层 ≥ 1 张 revealed 才能解锁下一层）；③ 每类 `CardReveal*` Renderer 按 `BugMineCardDto.payload`（`Map<String, JsonElement>`，由手工补丁层 sealed 化）渲染不同布局；④ `RetrySubmitPane` 直接复用 `:feature:arena/CodeEditorPane` 组件；⑤ 重试成功后 `closeBugMineSession(status=RESOLVED)` 并跳回 `:feature:arena`。
**已知坑**：`BugMineCardDto.payload` 是自由 `Record<string, unknown>`，反序列化需按 `cardType` 分发到 6 个 typed model（`CounterExamplePayload / VariableTracePayload / ...`），手工补丁层用 `JsonContentPolymorphicSerializer` 实现。

### 4.11 `:feature:retro`（P2 / Android-R12）

| 维度 | 内容 |
| --- | --- |
| Web 对照 | `frontend/src/pages/ReviewHallPage.vue` + `components/review/{ReviewHallPanel,MetricCard,KcGrowthList,PathDiffView}.vue` + `api/evaluation.ts` + `stores/learning.ts` `evaluation` 子状态 |
| Android 目标 | `kotlin/com/pythontopia/feature/retro/{RetroScreen,RetroViewModel,RetroRepository,EvaluationListPane,MetricCard,KcGrowthList,PathDiffView,AcceptOrKeepActions}.kt` |
| Compose 组件 | `RetroScreen` / `EvaluationListPane` / `MetricCard`（4 个：mastery_delta / new_kc_count / weak_kc_count / streak_days） / `KcGrowthList`（按 mastery 增量排序） / `PathDiffView`（旧 vs 新路径节点 diff） / `AcceptOrKeepActions` |
| ViewModel + UseCase | `RetroViewModel` + `ListEvaluationsUseCase` + `GetEvaluationUseCase` + `RunEvaluationUseCase`（最近 7 天） + `AcceptEvaluationPathUseCase` + `KeepOldEvaluationPathUseCase` |
| LibGDX 参与 | 否，纯 Compose |
| API endpoint | `GET /api/learner/evaluations?limit=4` / `GET /api/learner/evaluations/{id}` / `POST /api/learner/evaluations` / `POST /api/learner/evaluations/{id}/accept-new-path` / `POST /api/learner/evaluations/{id}/keep-old` |
| 状态管理 | `StateFlow<RetroUiState>`（list + current + loading + lastError）；MVVM 即可，状态字段 < 4 |
| 测试 | unit：`RetroViewModelTest`（已有）补 accept / keep 二选一 / Compose UI：PathDiffView 旧节点 vs 新节点 / Roborazzi：MetricCard 4 种数值边界（0 / 中位 / 极值） |

**实施步骤**：① 进入 `RetroScreen` 默认 `listEvaluations(limit=4)`，自动 `openEvaluation(first.id)`；② `MetricCard` 4 张并排展示关键指标 + 增量箭头；③ `KcGrowthList` 按 `mastery_delta` 降序排序，展示前 10 项；④ `PathDiffView` 两栏对比旧/新路径节点，差异节点高亮；⑤ `AcceptOrKeepActions` 仅在 `actedStatus == PENDING` 时显示，二选一后整张评估状态机进入 ACCEPTED / KEPT 终态。
**已知坑**：`runEvaluation` 调用 graph 服务平均 8-20s，必须显式 loading 阻断重复点；接受新路径后必须刷新 `:feature:path` 缓存（通过 `SharedFlow<PathInvalidated>` 通知）。

## 5. 共享层清单

### 5.1 `:core:common`
- 新增：`SessionRepository`（JWT 单例 + EncryptedSharedPreferences 封装） / `Money`、`Currency`、`Mastery` 等业务值对象 / `AppError` 错误统一（已有 stub） / `Result<T>` 封装（已有）。
- 引用关系：所有 feature 模块通过 `implementation(project(":core:common"))` 依赖。

### 5.2 `:core:network`
- 通过 **OpenAPI Generator 7.22** 生成 Kotlin Retrofit2 client（详见第 6 章）；输出落到 `:core:network/build/generated/openapi/`。
- 手工补丁层：`core/network/api/manual/` 容纳 `WorkshopContent` 等 sealed union 的手写实现，配 `.openapi-generator-ignore`。
- 现有：`AuthApi` / `BootstrapApi` / `CalendarApi` / `NpcDialogApi`（demo 手写）；Beta 完成后由 generator 自动覆盖。

### 5.3 `:core:data`
- Room 数据库：表 `crop_tile` / `mastery_snapshot` / `quest_local` / `learning_path_active` / `npc_dialog_state`；用于冷启动加速与离线展示。
- DataStore：`learner_preferences`（深浅色 / 字号 / 音量）。
- DTO：迁移现有 `dto/` 下手写文件到 generator 输出；保留 demo 手写文件直到 R-13。

### 5.4 `:core:design`
- 已有：`PythontopiaTheme.kt` / `Color.kt` / `Typography.kt` / `Spacing.kt` / `Shape.kt`。
- 待补：`PixelIndication`（替代 Material Ripple） / `MotionScheme`（≤ 80ms linear） / `LightDarkPreview` Compose Preview 注解 / 9-slice `PixelCardShape`。
- token 来源：`frontend/src/styles/tokens.css` 反相暗色版本（ADR-001 §1 决策 7）。

### 5.5 `:game`
- 已有：`HubGame` / `HubScene` / `HubLibGdxFragment` / `HubGameBridge` / `HubGameLauncher` / `TownGame`（legacy）。
- 待补：`FarmGame` / `MineGame` / `CampusGame` / `PortGame`；每张地图独立 `GameScene` 子类继承通用基类 `MapScene`。
- 共享：`PixelInputAdapter`（8 方向输入 → 4 方向上报 ADR-001 / AGENTS.md）、`CharacterAnimator`（基于 LPC 4 方向 walk 帧）、`TmxMapLoader` 包装。

### 5.6 模块依赖图

```
:app
 ├─ :feature:auth, :feature:hub, :feature:dialog, :feature:fields, :feature:profile,
 │  :feature:path, :feature:classroom, :feature:workshop, :feature:arena,
 │  :feature:mine, :feature:retro
 │      ├─ :core:common
 │      ├─ :core:design
 │      ├─ :core:data
 │      ├─ :core:network
 │      └─（hub/fields/mine）→ :game
 └─ :core:common, :core:design, :core:data, :core:network, :game
```

**依赖约束**：① `:feature:*` 之间不允许相互依赖（避免循环），跨 feature 跳转走 `NavController.navigate("route")` + DeepLink；② `:game` 只允许被 `hub/fields/mine` 引用，其它 feature 严禁 `import com.pythontopia.game.*`；③ `:core:network` 不允许依赖 `:core:data`（防止 generator 输出反向污染）。

## 6. API 契约对齐

### 6.1 Web DTO → Kotlin 类型映射规则

| TS 类型 | Kotlin 对应 | 备注 |
| --- | --- | --- |
| `interface Foo { ... }` | `data class Foo(@SerialName("...") val ...)` | 字段顺序按 OpenAPI schema 输出 |
| `type Status = 'A' \| 'B'` | `enum class Status { A, B }` | `@Serializable` |
| `type Union = A \| B \| C`（discriminated union） | `sealed class Union { data class A(...); data class B(...) }` | 走手工补丁层；如 `WorkshopContent`、`BugMineCardPayload` |
| `Record<string, unknown>` | `Map<String, JsonElement>` | `kotlinx.serialization.json` |
| `string \| null` | `String?` | nullable |
| `Array<T>` | `List<T>` | `@Serializable` |
| `Date` ISO 8601 | `kotlinx.datetime.Instant` | `kotlinxDatetime` 0.6.1 |

### 6.2 OpenAPI Generator 集成命令

```bash
cd /home/cypress/Pythontopia/backend
mvn spring-boot:run -Dspring-boot.run.profiles=demo &
PID=$!
sleep 30
curl -s http://localhost:18080/v3/api-docs \
  -o /home/cypress/Pythontopia/android/openapi/pythontopia-api.json
kill $PID

cd /home/cypress/Pythontopia/android
./gradlew :core:network:openApiGenerate
```

- 输出路径：`android/core/network/build/generated/openapi/src/main/kotlin/com/pythontopia/core/network/generated/`
- 包名：`com.pythontopia.core.network.generated.{api,model}`
- 手工补丁层：`android/core/network/src/main/kotlin/com/pythontopia/core/network/manual/`（在 `.openapi-generator-ignore` 中加 `manual/**`）
- CI 集成：每次 PR 跑 `openapi-diff` 比对 `pythontopia-api.json` 与上一次的差异，若有 breaking change 则阻断合并。

### 6.3 API endpoint 总览（28 个，按 feature 归口）

| Feature | endpoints |
| --- | --- |
| auth | `/api/auth/login` / `/api/auth/register` |
| hub | `/api/game/bootstrap` / `/api/world/calendar/today` / `/api/world/inventory` / `/api/world/sleep` / `/api/world/player` |
| dialog | `/api/world/npcs/{key}/state` / `/api/world/npcs/{key}/talk` / `/api/world/npcs/{key}/gift` |
| fields | `/api/world/crops` / `/api/learner/mastery` / `/api/learner/curriculum/bootstrap` |
| profile | `/api/learner/profile` / `/api/learner/profile/interview` / `/turn` / `/finish` / `/api/learner/profile/dimensions/{key}` / `/revisions` |
| path | `/api/learner/path/plan` / `/active` / `/archived` / `/{id}` |
| workshop | `/api/workshop/resources`（POST/GET） / `/{id}` / `/{id}/feedback` |
| arena | `/api/problems/{id}/public` / `/api/submissions`（POST/GET） / `/{id}` |
| mine | `/api/bug-mine/sessions` / `/{id}` / `/cards/{cardId}/reveal` / `/retry-submit` / `/close` |
| retro | `/api/learner/evaluations`（POST/GET） / `/{id}` / `/accept-new-path` / `/keep-old` |
| classroom | `/api/classrooms`（POST/GET） / `/join` / `/{id}/members` / `/api/teacher/classrooms/{id}` PUT / `/regenerate-code` / `/quests`（POST/GET） / `/{questId}/archive` / `/dashboard/{kc-heatmap,quest-summary,error-hotspots,resource-usage,recent-activity}` |

## 7. 测试矩阵

| Feature | Unit (JUnit5 + MockK + Turbine) | Compose UI Test (Robolectric) | Roborazzi 截图 | MockWebServer | Macrobenchmark |
| --- | --- | --- | --- | --- | --- |
| auth | 必须 | 必须 | 必须（light/dark） | 必须 | 否 |
| hub | 必须 | 必须 | 必须（4 地图入口） | 必须 | 必须（map 切换 fade） |
| dialog | 必须 | 必须 | 必须（3 档好感度） | 必须 | 否 |
| fields | 必须 | 必须 | 必须（4 成长度） | 必须 | 必须（65 KC 滚动） |
| profile | 必须 | 必须 | 必须（8 维填充） | 必须 | 否 |
| path | 必须 | 必须 | 必须（空/7 节点/归档） | 必须 | 否 |
| classroom | 必须 | 必须 | 必须（KC 热力图） | 必须 | 必须（heatmap 渲染） |
| workshop | 必须 | 必须 | 必须（6 类 ≥ 2） | 必须 | 否 |
| arena | 必须 | 必须 | 必须（3 类结果） | 必须 | 否 |
| mine | 必须 | 必须 | 必须（3 Layer） | 必须 | 否 |
| retro | 必须 | 必须 | 必须（MetricCard） | 必须 | 否 |

**单测覆盖率门禁**：Kover 0.8.3，整仓库 line coverage ≥ 60%（教学闭环 5 个 P0 feature ≥ 75%）；新增 ViewModel/UseCase 测试在 PR 中可见。
**截图测试基线**：Roborazzi 输出按 device profile（Pixel 6 / API 33）固定；新增截图在 PR 中预览。

## 8. CI 流水线

```yaml
# .github/workflows/android.yml（Android-R16 落地，本清单仅描述目标）
on:
  push:
    paths: ['android/**']
  pull_request:
    paths: ['android/**']

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4
      - working-directory: android
        run: |
          ./gradlew assembleDebug
          ./gradlew testDebugUnitTest
          ./gradlew detekt
          ./gradlew lintDebug
          ./gradlew verifyRoborazziDebug   # 截图回归
          ./gradlew koverHtmlReportDebug    # 覆盖率
```

**PR 阻断规则**：`assembleDebug + testDebugUnitTest + detekt` 任一失败立即阻断；`verifyRoborazziDebug` 失败需要 reviewer 二次确认（截图变化可能是有意 UI 改动）。

**Release 流水线**（仅 GitHub Releases pre-release，**不上架 Google Play**）：tag `android-rN` 触发签名 APK + AAB 构建；keystore 从 `KEYSTORE_BASE64` secret 解码注入，R8 默认开启。

## 9. 时间预算

| 档位 | 阶段 | Feature | 估时 | 备注 |
| --- | --- | --- | --- | --- |
| **P0** | Beta（Android-R4 ~ R-8） | auth (4h) / hub (10h) / dialog (4h，已有 60%) / fields (8h) / profile (4h，访谈复杂度可控) | **30h** | hub 含 4 张地图 LibGDX 集成；fields 含 65 KC 渲染调试 |
| **P1** | Beta+1（Android-R9 ~ R-11） | path (5h) / workshop (7h，6 类 Renderer) / classroom (6h，含教师 Dashboard) | **18h** | workshop sealed union 手工补丁 1h；classroom KC 热力图 2h |
| **P2** | Beta+2（Android-R12 ~ R-14） | arena (5h，代码编辑器自实现) / mine (6h，6 卡片 × 3 Layer) / retro (4h) | **15h** | arena 不集成 Pyodide 节省 8-10h；mine LibGDX 装饰层 2h |
| | **合计** | | **63h** | 单人顺序估时；2-3 人并行可压缩到 25-30h，但 LibGDX/OpenAPI/Biometric 三个关键路径不可并行 |

> 时间基于"已有占位骨架 + ADR-001 锁定的技术栈 + 后端 API 稳定可用"前提；如后端某接口需要扩展，单 feature 增加 2-4h 联调成本。

## 10. 风险

| 风险 | 影响 feature | 缓解措施 |
| --- | --- | --- |
| **LibGDX + Compose 嵌入内存泄漏**（`AndroidFragmentApplication` 生命周期与 Compose 不对齐） | hub / fields / mine | Compose `onDispose` 显式 `fm.beginTransaction().remove(fragment)`（hub 已有实现）；LeakCanary debug build 强制接入；Android-R6 后定期跑 4 张地图切换 100 次 macrobenchmark |
| **OpenAPI Generator 7.22 不支持 sealed union** | workshop（`WorkshopContent`） / mine（`BugMineCardPayload`） | 在 `.openapi-generator-ignore` 中加 `*WorkshopContent*` 等条目；手工补丁层 `core/network/manual/` 实现 `JsonContentPolymorphicSerializer`；单测覆盖 6 类 + 6 类反序列化 |
| **像素字体（Fusion Pixel 12px / Press Start 2P 8px）在 Compose Text 渲染抗锯齿失真** | 全部 11 feature | `Typography.kt` 中字体设 `FontVariation.Settings()` + `TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))`；Roborazzi 像素级对比锁基线 |
| **JWT 持久化与 BiometricPrompt 组合在某些 OEM 上的兼容性** | auth | Beta 仅支持 `BIOMETRIC_STRONG`，OEM 不支持时降级到密码二次输入；首发覆盖 MIUI 14 / OneUI 6 / OriginOS 4 / EMUI 14 4 个测试机型 |
| **后端 `/api/auth/refresh` 仍返回 501** | auth | 客户端在 token 过期前 5 分钟 toast 提示重登（ADR-001 §1 决策 13）；Beta+1 后端补完后再做 silent refresh |
| **6 类 Workshop Renderer 在大 Markdown 文档下卡顿** | workshop | `LazyColumn` + `key()` 稳定 ID；Markwon + `noteSpan` 异步加载；超过 5KB 文本走 `derivedStateOf` 折叠 |
| **classroom KC 热力图 Compose Canvas 自绘性能** | classroom | 65 KC × 班级人数（≤ 60 人）= 3900 格，`drawIntoCanvas` 一次性绘制；动态过滤走 `remember`；如卡顿降级 `RecyclerView` |
| **数据契约漂移**（后端 schema 变更未同步） | 全部 | CI 跑 `openapi-diff` 阻断 breaking change；周二例会同步前后端契约变更；后端 PR 触发 schema 摘要 |

## 11. 验收

每个 **P0 feature** 必须满足以下三条才算交付：

1. **真机 debug APK 截图**：在至少 1 台 minSdk 26 + 1 台 targetSdk 35 真机上跑通主路径，截图归档到 `android/docs/screenshots/<feature>/`。
2. **自动化测试通过**：`./gradlew :feature:<name>:testDebugUnitTest :feature:<name>:verifyRoborazziDebug :feature:<name>:lintDebug :feature:<name>:detekt` 全部退出码 0。
3. **后端联调录像**：用 `scrcpy` 录制 30s 演示视频，证明界面是真后端数据而非 stub fallback，归档到 `android/docs/recordings/<feature>.mp4`。

**P1 / P2 feature** 必须满足前两条；后端联调录像为可选项（如 Beta+1 后端不可用，可用 MockWebServer 录像替代，注明）。

**整体 Beta 验收门禁**（5 项 P0 全部完成时）：

- 真机：从启动 APK → 登录 → 进入 hub → 与镇长 Py 对话 → 进入 fields → 完成画像访谈 → 看到第一个 KC 作物，全流程 < 90s。
- APK 体积：debug ≤ 50 MB，release ≤ 25 MB（R8 + LibGDX `arm64-v8a` 单架构）。
- 启动性能：冷启动到首屏可交互 < 2.5s（Pixel 6 / API 33）。

## 12. 附录 A：Web vs Android 命名对照表

| # | Web 端 | Android 端 | 备注 |
| --- | --- | --- | --- |
| 1 | `frontend/src/pages/LoginPage.vue` | `:feature:auth/LoginScreen.kt` | 已有 |
| 2 | `frontend/src/pages/RegisterPage.vue` | `:feature:auth/RegisterScreen.kt` | 已有 |
| 3 | `frontend/src/api/auth.ts:login` | `core/network/api/AuthApi.kt:login` | 已有 |
| 4 | `frontend/src/stores/session.ts` | `core/common/SessionRepository.kt` | 待新建 |
| 5 | `frontend/src/pages/WorldPage.vue` | `:feature:hub/HubScreen.kt` | 已有 |
| 6 | `frontend/src/game/scenes/WorldScene.ts` | `:game/HubScene.kt` + 4 个 MapScene 子类 | 部分已有 |
| 7 | `frontend/src/game/core/WorldClock.ts` | `:feature:hub/HubViewModel.kt` (calendar 字段) | 已有 |
| 8 | `frontend/src/components/hud/HudShell.vue` | `:feature:hub/HubScreen.kt` `HubHud` | 已有（最小化） |
| 9 | `frontend/src/components/hud/MiniMap.vue` | `:feature:hub/MiniMapPanel.kt` | 待新建 |
| 10 | `frontend/src/components/hud/InventoryPanel.vue` | `:feature:hub/InventoryPanel.kt` | 待新建 |
| 11 | `frontend/src/components/game/DialogModal.vue` | `:feature:dialog/NpcDialogPanel.kt` | 已有 |
| 12 | `frontend/src/components/game/DialogTree.vue` | `:feature:dialog/DialogTree.kt` + `DialogTreeLoader.kt` | 部分已有 |
| 13 | `frontend/src/api/worldNpc.ts:talkToNpc` | `core/network/api/NpcDialogApi.kt:talk` | 已有 |
| 14 | `frontend/src/components/game/FieldCanvas.vue` | `:game/FarmGame.kt` | 待新建 |
| 15 | `frontend/src/components/field/KcDetailPanel.vue` | `:feature:fields/KcDetailPanel.kt` | 待新建 |
| 16 | `frontend/src/api/field.ts:fetchCrops` | `core/network/api/FieldApi.kt:fetchCrops` | 待生成 |
| 17 | `frontend/src/components/profile/ProfileInterviewPanel.vue` | `:feature:profile/InterviewChatList.kt` | 待新建 |
| 18 | `frontend/src/api/learner.ts:openProfileInterview` | `core/network/api/LearnerProfileApi.kt:openInterview` | 待生成 |
| 19 | `frontend/src/components/board/PathTreeTab.vue` | `:feature:path/PathTreeView.kt` | 待新建 |
| 20 | `frontend/src/components/board/TodayQuestsTab.vue` | `:feature:path/TodayQuestsList.kt` | 待新建（也可入 path） |
| 21 | `frontend/src/components/workshop/HandoutView.vue` | `:feature:workshop/HandoutRenderer.kt` | 待新建 |
| 22 | `frontend/src/components/workshop/MindMapView.vue` | `:feature:workshop/MindMapRenderer.kt` | 待新建（Markwon） |
| 23 | `frontend/src/components/workshop/EvidenceTag.vue` | `:feature:workshop/EvidenceTag.kt` | 待新建 |
| 24 | `frontend/src/components/training/TrainingPanel.vue` | `:feature:arena/CodeEditorPane.kt` + `ProblemStatementCard.kt` | 待新建 |
| 25 | `frontend/src/api/submissions.ts:createSubmission` | `core/network/api/SubmissionApi.kt:create` | 待生成 |
| 26 | `frontend/src/components/bugmine/BugMinePanel.vue` | `:feature:mine/MineCardGrid.kt` + 6 个 `CardReveal*.kt` | 待新建 |
| 27 | `frontend/src/api/bugMine.ts:revealBugMineCard` | `core/network/api/BugMineApi.kt:reveal` | 待生成 |
| 28 | `frontend/src/components/review/MetricCard.vue` | `:feature:retro/MetricCard.kt` | 待新建 |
| 29 | `frontend/src/components/teacher/KcHeatmap.vue` | `:feature:classroom/KcHeatmapView.kt` | 待新建（Compose Canvas） |
| 30 | `frontend/src/api/teacher.ts:fetchKcHeatmap` | `core/network/api/TeacherDashboardApi.kt:fetchKcHeatmap` | 待生成 |

---

## 13. 附录 B：常用 Gradle 命令速查

| 目的 | 命令 | 备注 |
| --- | --- | --- |
| 全量编译 debug APK | `./gradlew assembleDebug` | 产物在 `android/app/build/outputs/apk/debug/` |
| 仅运行单 feature 单测 | `./gradlew :feature:hub:testDebugUnitTest` | 替换 `hub` 为目标 feature |
| 跑全仓库单测 | `./gradlew testDebugUnitTest` | 包含所有 feature + core |
| 截图测试回归 | `./gradlew verifyRoborazziDebug` | 输出在 `*/build/outputs/roborazzi/` |
| 截图基线更新 | `./gradlew recordRoborazziDebug` | UI 故意改动后执行 |
| 覆盖率报告 | `./gradlew koverHtmlReportDebug` | 输出在 `*/build/reports/kover/` |
| 静态分析 | `./gradlew detekt` | 配置在 `config/detekt/detekt.yml` |
| Android Lint | `./gradlew lintDebug` | baseline 在 `*/lint-baseline.xml` |
| 重新生成 OpenAPI client | `./gradlew :core:network:openApiGenerate` | 前提：`android/openapi/pythontopia-api.json` 已更新 |
| Hilt 编译诊断 | `./gradlew :app:kspDebugKotlin --info` | 注入失败时查看 |
| 清缓存重跑 | `./gradlew clean assembleDebug` | 升级 AGP / Compose BOM 后必跑 |

## 14. 附录 C：第一个 P0 feature（auth）落地的细化步骤

> 作为示范，把 `:feature:auth` 在 Android-R4 的落地拆成 6 个独立可 commit 的 sub-step；其它 P0 feature 按此模板复用。

1. **R-4.1 接 AuthApi**：删除 `LoginViewModel.login()` 的 `delay(500)` 桩；注入 `AuthApi`，调用 `login(LoginRequest)` 并把异常映射到 `LoginUiState.errorMessage`。
2. **R-4.2 SessionRepository**：在 `:core:common` 新增 `SessionRepository`，封装 `EncryptedSharedPreferences` 读写 `jwt + userId + role + displayName`；启动期从盘读，登录后写盘。
3. **R-4.3 AuthInterceptor 接 Session**：把现有 `:core:network/AuthInterceptor` 从硬编码 token 改为读 `SessionRepository`；登出（`MainScreen.onLogout`）时清空 + 跳 Login。
4. **R-4.4 Register 闭环**：实现 `RegisterScreen` + `RegisterViewModel`，对接 `POST /api/auth/register`；成功后跳回 Login 并 prefill 用户名；用户名重复 / 密码弱 / 字段缺失三类错误分别给中文提示。
5. **R-4.5 BiometricPrompt**：MainActivity 启动时检查盘上 token 是否过期；未过期且设备支持 `BIOMETRIC_STRONG` 则弹生物识别；通过后直接跳 Hub；不通过或不支持则降级到 LoginScreen。
6. **R-4.6 测试与截图**：补 `LoginViewModelTest` 真实 API 桩；写 Compose UI test 覆盖空字段 / 错误密码 / 登录成功三场景；用 Roborazzi 记 light/dark 两套基线截图。

**完成节点**：6 个 commit 全部合入，`./gradlew :feature:auth:testDebugUnitTest :feature:auth:verifyRoborazziDebug :feature:auth:detekt :feature:auth:lintDebug` 全绿，真机 debug APK 能完成"输入用户名密码 → 跳 Hub → 退出登录 → 第二次启动自动生物识别进入"全流程。

## 15. 附录 D：与 R-12 演进的兼容性约定

- R-12 已落地的 `:feature:dialog` demo（DemoDialogTree）不删除，保留为 `mayor_py` 的 fallback；新增 `DialogTreeLoader` 优先读 assets，读取失败回落到 `DemoDialogTree`。
- R-12 已落地的 `:feature:hub` hub 单图实现保留为 `mapKey == "town_hub"` 的特化场景；R-13 新增 4 张外围地图后，Hub 自动支持地图切换菜单。
- R-12 演示阶段使用的手写 OpenAPI 静态规格 `android/openapi/pythontopia-api.json`，在 R-13 OpenAPI Generator 集成后立即被 `springdoc /v3/api-docs` 自动产出覆盖；保留文件名但内容由 CI 维护。
- R-12 `MainActivity` / `MainScreen` 的 `Login → Hub` 双路由，在本清单 P0 落地后扩展为 `Login → Hub → (dialog/fields/profile)` 多入口 Scaffold + 底部 Tab；不破坏既有 `AuthDestination.HOME = "home"` 路由约定。

---

**编辑约定**：本清单是 living document，每完成一个 feature 移植即更新第 2 章"进度评估表"对应行的"已实现 UI"与"缺失项"，并在第 11 章"验收"打勾。若某 feature 实施时发现需要 ADR 变更，新建 `android/docs/ADR-NNN-*.md` 并在本文件锚点处引用。

**版本变更记录**：

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.0 | 2026-05-24 | 初稿（Draft）：覆盖 11 feature P0/P1/P2 全景 + 共享层 + API 契约 + 测试矩阵 + CI + 时间预算 + 风险 + 验收 + 4 附录 |
