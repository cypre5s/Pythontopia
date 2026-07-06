# Pythontopia 执行状态实时记录

> 该文档与 `docs/project/CHANGELOG.md` 配合使用：CHANGELOG 记录“已完成”，本文档记录“正在做+下一步+卡点+最近 2 分钟动作”。
> 每完成一个关键节点都要追加；每 2 分钟最低汇报一次。

## 全局阶段进度

| 阶段 | 名称 | 状态 | 说明 |
| ---- | ---- | ---- | ---- |
| 00 | 仓库骨架 | 已完成 | 见 CHANGELOG |
| 01 | 课程包导出与种子数据 | 已完成 | 见 CHANGELOG |
| 02 | 账号、班级与小镇基础后端 | 已完成 | 见 CHANGELOG |
| 03 | 前端骨架与 Phaser 小镇 MVP | 已完成 | 见 CHANGELOG |
| 04 | 画像屋与 ProfileAgent | 已完成 | 见 CHANGELOG |
| 05 | 知识田与 KC 作物系统 | 已完成 | backend 26 / graph 11 / frontend 21 测试通过 |
| 06 | RAG 服务课件证据检索 | 已完成 | backend 29 / rag 18 测试通过 |
| 07 | 资源工坊与六类资源生成 | 已完成 | backend 41 / graph 31 / rag 18 / frontend 30 测试通过 |
| 08 | 学习路径、每日任务、公告板 | 已完成 | backend 52 / graph 40 / rag 18 / frontend 37 测试通过 |
| 09 | 训练场、Pyodide、服务端判题 | 已完成 | backend 72 / frontend 41 / graph 40 / rag 18 测试通过 |
| 10 | Bug 矿洞与 DebugAgent | 已完成 | backend 76 / frontend 44 / graph 45 / rag 18 测试通过 |
| 11 | 讯飞 Provider 接入 | 已完成 | graph 53 测试通过（含 8 Spark provider） |
| 12 | 班级架构与教师看板 | 已完成 | backend 87 / frontend 48 测试通过（见 CHANGELOG） |
| 13 | 学习效果评估与复盘大厅 | 已完成 | backend 100 / graph 59 / rag 18 / frontend 52 测试通过 |
| 14 | Docker Compose、视觉打磨、文档 | 已完成 | 6 service 编排 / 5 个脚本 / 6 类文档 / tokens.css / UX checklist |
| 15 | Beta 验收、视频、PPT | 已完成（文档/脚本/checklist） | 视频/PPT 本体需真人产出 |
| R-10 | 10 轮代码质量审查与性能优化 | 已完成 | 最终 234 测试通过 |
| R-11 | 根 README 重写为成熟教学游戏作品门面 | 已完成 | 单文件 30 → 147 行；R-0 baseline commit `59761c5` 建立 |
| R-12 | 星露谷改造（Web 端从 R-11 解冻） | 已完成 | Phaser 像素小镇改造；原 5 图分块 + 20 NPC（**已被 R-14 收缩推翻**，见 R-14 行） |
| R-13 | 主线 35 任务 + 章末 Boss + 竞技场 + AI 出题审核 | 已完成 | 详见 `docs/r13-*` |
| R-14 | 世界收缩方案 A：废弃 5 图大世界，收敛为单一中央 `town_hub` | 已完成 Phase 0 | 2 地图（`town_hub` 主玩图 + `mine_south` 支线）/ 12 NPC / 5 占位 building 已去路由；Phase 0 地基止血完成（双 V135 修复 + 跨服务韧性 + 判题沙箱加固 + 文档对齐） |
| Android-R0 | Android 客户端启动：10 subagent 调研 + ADR-001 + roadmap | 已完成 | 详见 `clients/native/android/docs/ADR-001-tech-selection.md` 与 `clients/native/android/docs/roadmap.md` |
| Android-R1 | 多模块 Gradle 骨架 + 真实可构建 + 28 测试通过 + 11MB APK 产出 | 已完成 | commit `8a49643`；本机已装 Android SDK 35 / JDK 17 toolchain / Gradle 8.11.1 |
| Android-R2 ~ R17 | Android 客户端从骨架到 Beta | 进行中 | 详见 `clients/native/android/docs/roadmap.md` |

## 当前状态：R-14 世界收缩（方案 A）进行中 — Web 端已从 R-11 解冻并收敛为单一中央小镇 `town_hub`（外围图弃用）；Android 侧 Android-R1 已交付（真实可构建 APK + 28/28 测试），R-2 待启

> 注：下方「最近进度汇报记录」为历史时间线（含 R-10/R-11 期一度暂停 Web、转 Android 的旧决策），R-12 起 Web 已解冻、R-14 收缩为单 hub，以本表与本行为准。

## 阶段 15 — Beta 验收、演示视频与 PPT 收尾（文档与脚本部分已完成）

### 计划任务清单（详见 `plan/阶段15-Beta验收视频与PPT收尾.md`）
1. Beta 验收 checklist：`docs/beta_acceptance_checklist.md` 按 `plan.md` 第 9 节 + AGENTS.md 硬要求逐项打勾。
2. 演示视频脚本扩展为 `docs/release/demo_v1.md`（剪辑时间轴 + 字幕清单）；视频本体不入 git。
3. PPT 大纲 → 终稿（pptx + pdf 仅放 `docs/release/`，本仓库只留 outline.md 与脚本）。
4. 提交包：`docs/release/README.md` 列源码 tarball / docs / images / images_tar / checksum / 演示清单。
5. 全量回归报告 `docs/release/regression_report.md`：4 套件 + demo_smoke 退出码 + 耗时。
6. 已知缺陷 `docs/release/known_issues.md`：分演示阻断 / 非阻断两类。

### 卡点 / 注意
- 比赛文件中不得包含真实 LLM 密钥；PPT/视频中不得包含真实学生姓名。
- 演示前 24 小时冻结代码，只允许文档与演示数据修改。

## 最近进度汇报记录

- 18:46 收到用户重申长期指令；继续阶段 07。修正 execution_status.md（阶段 06 已完成，进入 07）。
- 18:55 阶段 07 graph：V040/V041 + resource schema + 5 节点 + 6 agent + ResourceGraph + `/agents/resource/produce`；graph `pytest -q` -> `31 passed`。
- 19:02 阶段 07 backend：workshop 模块 + Stage07 Testcontainers 全过；backend `mvn test` -> `Tests run: 41`。
- 19:10 阶段 07 前端：`/workshop` + WorkshopPage + 9 个 workshop 组件 + workshop store/api/dto + 4 个 Vitest 全过；frontend `typecheck/lint/test` -> `17 files / 30 tests passed`；阶段 07 验收完成。
- 19:10 进入阶段 08：学习路径、每日任务与公告板。
- 19:20 阶段 08 graph：path schemas + PathPlannerAgent + PathGraph + `/agents/path/plan`；graph `pytest -q` -> `40 passed`（含 9 个 path planner 测试）。
- 19:30 阶段 08 backend：V050/V051/V052 + 三模块（path/quest/classroom-quest）+ 单测 + Stage08 Testcontainers；`mvn test` -> `Tests run: 52`。修复 classroom_members.student_id 列名后整体通过。
- 19:35 阶段 08 前端：`/board` BoardPage + BoardPanel + 4 个 tab + path/quests/classroom DTOs + path/quests API + store 子状态 + Town 入口 + 2 套 Vitest；`typecheck/lint/test` -> `Test Files 19 / Tests 37`；阶段 08 验收完成。
- 19:35 进入阶段 09：训练场、Pyodide、服务端判题。
- 19:42 阶段 09 backend：V060 + submission/judge 模块（SubprocessPythonSandbox + StaticCodeChecker + JudgeService + SubmissionService + Public/SubmissionController）+ 15 个单测全过。
- 19:45 阶段 09 集成测试 Stage09SubmissionAcceptanceIntegrationTest 5/5（含真实 python3 subprocess 跑出 AC/WA/CE/限频 429/DTO 无 hidden cases）；`mvn test` -> `Tests run: 72`。
- 19:50 阶段 09 frontend：`/training` TrainingPage + TrainingPanel + Pyodide adapter (CDN 0.25.0) + submissions/problems API + dto + store.training 子状态 + Vitest 4 个；`typecheck/lint/test` -> `Test Files 21 / Tests 41`；阶段 09 验收完成。
- 19:50 进入阶段 10：Bug 矿洞与 DebugAgent。
- 20:00 阶段 10 graph：debug schemas + DebugAgent + safety 降级 + `/agents/debug/analyze`；graph 测试 45/45。
- 20:05 阶段 10 backend：V070 + bug_mine 模块（PythonTraceCollector + CounterExampleSelector + DebugAgentClient + BugMineService + BugMineController）+ Stage10 集成测试 4/4；`mvn test` -> Tests run: 76。
- 20:10 阶段 10 frontend：`/bug-mine` + BugMinePage + BugMinePanel + api/bugMine + bugMine store + TrainingPanel 失败入口；frontend 44/44。
- 20:10 进入阶段 11：讯飞 Provider 接入。
- 20:15 阶段 11 完成：SparkProvider（OpenAI 兼容接口）+ LlmProviderError 分级 + provider_from_env/provider_label + 8 个 spark 测试 + docs/llm-provider-env.md 更新；graph 53/53。
- 20:15 进入阶段 12：班级架构与教师看板。
- 21:00 阶段 12 完成（见 CHANGELOG 2026-05-21）：V080/V081 + classroom-quest 写路径 + dashboard 5 聚合 + 教师 6 tabs；backend 87 / frontend 48 全过。
- 21:05 进入阶段 13：学习效果评估、路径重排与复盘大厅。计划并行实施：graph EvaluationNarrativeAgent + backend evaluation 服务 + frontend ReviewHallPanel。
- 22:10 阶段 13 完成：graph 修复 NUMBER/KC pattern 双计 bug + 6 单测；backend 新增 evaluation service/controller/listener + 13 单测/集成；frontend 复盘大厅 + 3 子组件 + 4 spec；全量 backend 100 / graph 59 / rag 18 / frontend 52 通过。
- 22:10 进入阶段 14：Docker Compose 部署、视觉打磨与文档。
- 22:25 阶段 14 完成：4 套 Dockerfile + nginx 反代 + 6 服务 compose；5 个 deploy 脚本 + demo seed.py；tokens.css；6 类核心文档 + UX checklist；前端 typecheck/lint/test 52/52 通过。
- 22:25 进入阶段 15：Beta 验收 + 演示视频脚本 + PPT 大纲 + 提交包目录。
- 22:35 阶段 15 文档完成：beta_acceptance_checklist / release/README / regression_report / known_issues / demo_v1；视频与 pptx 本体需真人录制制作（已写脚本与剪辑指南）。
- 22:35 启动 R-10：10 轮小步代码质量审查与性能优化，聚焦"让项目更像成熟教学游戏作品"。
- 22:50 R-10 完成：router 全量懒加载、教学闭环引导文案、复盘 store + planDraft 单测补强、文档闭环；最终 backend 101 / graph 59 / rag 18 / frontend 56 = 234 测试通过。
- 22:50 全部阶段交付完毕；视频与 PPT 物件需真人按 `docs/presentations/demo_script.md` 与 `docs/release/demo_v1.md` 录制制作。
- 23:50 R-0 baseline commit `59761c5`：把 R-10 完成态固化为首个 git commit（13032 文件 / 222589 行），建立 R-11 ~ R-100 可回滚锚点。
- 23:55 R-11 完成：根 `README.md` 重写为成熟教学游戏作品门面（30 → 147 行，11 节）；新建 `docs/release/r100_iterations.md`；同步本文件与 `docs/project/CHANGELOG.md`。本轮无代码改动，无需跑测试套件。
- 00:00 用户决策方向变更：在本项目下新增 `clients/native/android/` 文件夹做原生 Android 客户端；接受比赛 Beta 日期推迟；放弃 Web 端 R-12 ~ R-100 迭代（保留 R-11 完成态代码）；授权使用 subagent 并发。
- 00:05 Android-R0 启动：新建 `clients/native/android/` + `clients/native/android/README.md` + `clients/native/android/.gitignore` + `clients/native/android/docs/README.md`；启动 10 个并发 subagent 调研（Gradle / 像素引擎 / Python 沙箱 / 暗色主题 / 网络 / DI 状态 / JWT 安全 / 测试 / DTO 对齐 / CI/CD），输出到 `.work/thinking/android_research_{01..10}.md`。
- 00:08 10 个 subagent 全部完成，3951 行调研报告就位。
- 00:12 Android-R0 ADR-001 + roadmap.md 落地：18 项关键决策锁定（Kotlin 2.2.0 / AGP 8.7.3 / Compose BOM 2025.04.00 / LibGDX 1.13.1 主渲染 / 仅服务端判题 / Hilt + KSP / Retrofit 2.11 + OkHttp 4.12 / EncryptedSharedPreferences + BiometricPrompt / JUnit5 + Robolectric + Compose UI Test / OpenAPI Generator 7.22 / GitHub Actions ubuntu-latest）；roadmap 列 Android-R0 ~ R17 + R100 共 19 个阶段。
- 00:25 Android-R1 启动：6 个并发 subagent 写源码（M1 :app / M2 :core×4 / M3 :game / M4 :feature×2 / M5 :feature×7 / M6 CI+detekt+wrapper）。
- 00:35 105 个源码文件就位（含 15 模块 build.gradle.kts + Kotlin 类 + AndroidManifest + 单测）。
- 08:00 工具链准备完成（走代理 127.0.0.1:7892）：Android cmdline-tools 11076708 解压；sdkmanager 装 platform-tools / platforms;android-35 / build-tools;35.0.0；Gradle 8.11.1 二进制装；gradle wrapper 生成（gradlew + gradlew.bat + gradle-wrapper.jar 43.5KB）。
- 08:30 修 ADR-001 中 Kotlin 2.2.0 与 KSP 2.2 兼容性问题 → 实际锁定 Kotlin 2.0.21 + KSP 2.0.21-1.0.27 + Compose BOM 2024.12.01；settings.gradle.kts 加 foojay-resolver-convention 自动下载 JDK 17 toolchain。
- 08:43 `./gradlew :app:assembleDebug --no-daemon` BUILD SUCCESSFUL in 3m 45s（271 tasks），产出 `app-debug.apk` = 11 MB。
- 08:45 `./gradlew test --no-daemon` BUILD SUCCESSFUL in 1m 27s（613 tasks），**28 / 28 unit tests passed**（覆盖 15 模块）。
- 08:50 Android-R1 commit `8a49643` 落成（107 文件 / 3053 行新增）。- 08:50 R-100 阶段 1 启动：素材爬取 + 集成 + P0 优化。
- 09:00 阶段 1.1 License 热修完成：删 `sprout_lands_basic`(Cup Nooble 专有) + `simple_farm_tiles`(来源未知) + 2 个冗余 + 迁 `lpc_farm` 到 `opengameart_farming/`；sprout_lands 顶级目录已删除；`.gitignore` 加 sprout_lands/ + *.zip。Commit `0d167a4`。
- 09:05 阶段 1.2 10 个素材爬取子代理产出已完成 5/10（seasons_school 21M / characters_extra 5M / mines_extra 11M / sfx_extra 25M / bgm_extra 32M），剩余 5 个子代理因 worktree 切换中断后由主代理接管。
- 09:10 阶段 1.2B 主代理接管 #06-#10：补 icons_kc 19 张缺失类目至 60 张全覆盖；#10 effects_extra 删 zip 瘦身 105M→60M；#07 ark-pixel-font 13M 下载 + OFL-1.1；#08 Kenney UI 4 pack 36M；#09 itch.io 严过滤主动跳过(2 个 CC0 候选因 itch.io session token 限制不可自动下载)。
- 09:12 阶段 1.3 综合：md5 去重（仅清理 macOS `__MACOSX` 残留）+ `LICENSE-ART.md` 顶层声明就位（5.5KB / 全协议矩阵）。
- 09:13 Commit 2 `2ecf1af`: stage1 inbox expansion 10169 文件 / +194053 行。Commit 3 `faf6eac`: 补遗（相对路径符号链接 + icons/kenney_curated + buildings/kenney_1bit-pack + characters/armm1998_zelda_cc0 + oga_farming/lpc_farm + edu_diagrams 5 个新流程图）。
- 09:18 阶段 1.4 集成到前端：`frontend/public/assets/pixel/` 从 60KB 扩到 29MB；`tokens.css` 加 @font-face for Ark Pixel 12（zh_cn + latin × mono + prop）+ `.pyt-pixel-text` / `.pyt-pixel-code` 工具类；`BootScene.ts` 重写 preload：22 个 key 覆盖 tileset / KC icons / character spritesheet / SFX / BGM；前端 typecheck OK / 26 files 56 tests PASS / lint 0 错。Commit 4 `6d07383`。
- 09:25 阶段 2 P0 优化实施：`frontend/src/utils/sfx.ts`（66L，全局 SFX 服务 + localStorage 持久化开关）+ 5 单测 PASS；`frontend/src/pages/CreditsPage.vue`（200L，CC-BY-SA / OFL / CC-BY 强制署名页）+ `/credits` 路由。前端总计 27 files / 61 tests PASS / lint 0 错。Commit 5 `546152c`。
- 09:30 全套测试回归：backend mvn test = 101/101 PASS / BUILD SUCCESS；frontend = 61/61 PASS；graph pytest = 59/59 PASS；android = 28/28 PASS（用户 worktree 已 commit 8a49643）；**总计 249/249 通过**。rag pytest 因本机 Python 3.10 不满足 `>=3.11` 跳过。
- 09:35 `docs/r100_test_report.md` 测试报告就位（92 行，含合规验证 + 素材规模 + P0 实施 + backlog）。
- 09:35 R-100 阶段 1 + 阶段 2 P0 任务全部完成；剩余 P1/P2 优化、browser MCP 截图、judge e2e docker compose、RAG pytest 等列入 backlog 留待用户醒来推进。
- 09:50 R-100 阶段 2 P1 任务（"更像成熟游戏"优化）启动：5 个并发 subagent A/B/C/D/E。受 worktree 多次切换影响 subagent 反复 Aborted，主代理接管完成 4 个主题。
- 10:00 P1-A BGM 引擎落成：`utils/bgm.ts` (113L) + `utils/bgm.test.ts` (85L / 8 tests)；TownScene + FieldScene + BugMinePage + ReviewHallPage 接入；切场景 fade-in/out + localStorage `pyt:bgm` 持久化开关。
- 10:05 P1-B KC 收获 confetti 庆祝：`utils/celebrate.ts` (50L) + 3 tests；接入 `learning.loadField` 与 `refreshAfterEvent` 在 KC 状态 → MATURE 转换时触发 + 播 `jingleLevelUp`；tokens.css +68L celebrate keyframes。
- 10:10 P1-C 训练/工坊 SFX 反馈：TrainingPanel 提交成功 → `jingleComplete`，失败 → `error.ogg`；WorkshopPanel 资源生成成功 → `success + coin`，失败 → `error`。
- 10:15 P1 集合 commit `57bd410`：14 文件 / +479 -21 行；前端 29 files / 73 tests 全过 / lint 0 错。
- 10:18 P1-D 玩家 LPC Sara walk 动画：SaraFullSheet 832×1344 / 13×21 (LPC 标准)；BootScene preload `player_sara` spritesheet (64x64)；`sprite.ts` 加 LPC 4 方向 walk frame 表 (rows 8/9/10/11)；moveToTile 时 setFrame 切走路帧；LPC sheet 不可用时 fallback 到 `student_default`；sprite.spec.ts 改 2 测试覆盖 LPC + fallback 路径。
- 10:22 P1-D commit `3d54cfa`：前端 29 files / 74 tests 全过。
- 10:25 P1-E TownScene 真渲染（用 kenney_tiny_town spritesheet 替换 SVG 地面）跳过——子代理仅完成 BootScene spritesheet 注册与 sceneTypes 加 frame 参数（已并入 57bd410），实际 tilemap.ts 改造涉及 SceneLike 接口扩展（PhaserTileMap / TileSprite layer），风险大且无人 review，留 backlog。
- 10:30 R-100 阶段 1 + 阶段 2 (P0 + P1) 全部交付完成。本会话 main 分支新增 8 个 commit（不含用户 worktree 已 commit 的 2 个 Android-R1）。前端测试从 R-11 基线 52 → 74（+22 测试 + 18%）；后端 / graph / android 持平不破坏；总测试 262/262 通过（不含 rag 因环境）。
