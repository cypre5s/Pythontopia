# Pythontopia Android（原生客户端）

> Pythontopia（Vue 3 + Phaser 3 网页端）的**原生 Android 客户端**：复用同一套 Spring Boot 后端（REST + Graph + RAG）作为唯一数据底座，UI 与像素游戏渲染用 Kotlin + Jetpack Compose + LibGDX 重写。
>
> **状态（2026-06-05 实测）**：骨架早已落地。仓库现有 **22 个 Gradle 模块 / 16 个 feature module / ~24.6K 行 Kotlin / 39 个 JVM 单测**，`./gradlew :app:assembleDebug` 可产出可安装的 `app-debug.apk`（约 16.6 MB）。当前处于 **R-14 单中央 hub 收敛 + OpenAPI 代码生成接线 + 真实像素素材 vendoring** 的并行收尾阶段。
>
> **目录定位**：`/home/cypress/Pythontopia/android/`，独立 Gradle 构建，不影响 `frontend/` Vue 端。

> ℹ️ 本 README 于 2026-06-05 做过一次「对齐真实代码」的重写。早期版本把状态写成「Android-R0 / 技术选型中 / 全部待启动」，与仓库实际严重不符，已更正。关键技术决策仍以 [`docs/ADR-001-tech-selection.md`](docs/ADR-001-tech-selection.md)（含末尾「实锁版本」附录）为准；后端契约差异见 [`docs/04-backend-contract-drift.md`](docs/04-backend-contract-drift.md)。

## 1. 现状速览（实测，非计划）

| 维度 | 实际情况 |
| --- | --- |
| 构建 | `./gradlew :app:assembleDebug` 产出 `app/build/outputs/apk/debug/app-debug.apk`（~16.6 MB，已验证可生成） |
| applicationId / 版本 | `com.pythontopia` / `0.1.0-alpha01`（versionCode 1，namespace `com.pythontopia.app`） |
| SDK / 工具链 | compileSdk 35 / minSdk 26 / targetSdk 35 / JVM target 17 |
| Gradle 模块 | 22 个（见 §3） |
| feature 模块 | 16 个：`auth / hub / dialog / profile / fields / workshop / arena / mine / retro / path / classroom / tutor / library / boss / endless / lecture` |
| 代码量 | ~24,624 行 Kotlin（211 个 main + test 文件） |
| 测试 | 39 个 JVM 单测（`src/test`，JUnit5 + MockK + Robolectric + Turbine）；**暂无** instrumented（`androidTest`）测试 |
| 游戏渲染 | `:game` 用 LibGDX 1.13.1 内嵌像素小镇（`HubLibGdxFragment` / `AndroidFragmentApplication`） |
| 导航 | Compose `NavHost`：登录 → `AppShell`（顶部 dock 挂 15 个功能页，见 `app/src/main/kotlin/com/pythontopia/app/MainScreen.kt`） |
| 数据层 | `:core:data` 为**手写** `@Serializable` DTO（**尚未**由 OpenAPI 生成）；`:core:network` 4 个 Retrofit API + 部分 feature 自带接口 |

## 2. 目标与边界

- **必须**：复刻 Web 端教学闭环（画像 → 看/学/练/测 → Bug 矿洞 → 复盘 → 路径重排）。
- **必须**：复用 Spring Boot 后端全部业务 API，不在端上重写业务逻辑。
- **必须**：保持像素小镇视觉 + dark mode（`:core:design` 已落地 Pal-1 暗色 token）。
- **不做**：端上内嵌 LLM / RAG（所有 Agent 调用经后端）。
- **不做**：端上运行 Python（Beta 首发仅服务端判题）。
- **不做**：复用 Web 端 Phaser 代码；不复制任何商业像素游戏官方素材。

## 3. 真实模块拓扑（22 模块）

以 `settings.gradle.kts` 为准：

```text
:app                                  # application 模块：DI 总装 + Compose 导航壳 + 15 页 AppShell
:core:common                          # 纯 Kotlin 域/工具：MasteryLevel / StudentRole / AppError / Result / UiState
:core:data                            # 手写 @Serializable DTO（auth + world）+ Room（PytDatabase / CalendarCache）
:core:design                          # Material3 主题：PytColors / PytTypography / PytSpace / PytShape / PytFeedback
:core:network                         # Retrofit/OkHttp：RetrofitFactory / NetworkModule / AuthInterceptor / SessionState
:game                                 # LibGDX 像素小镇（见 §5）
:feature:auth :feature:hub :feature:dialog :feature:profile
:feature:fields :feature:workshop :feature:arena :feature:mine
:feature:retro :feature:path :feature:classroom :feature:tutor
:feature:library :feature:boss :feature:endless :feature:lecture
```

`:app` 依赖以上全部 5 个 core/game + 16 个 feature 模块，并引入 `libgdx-backend-android`。

## 4. feature 模块实测内容（16）

每个 feature 模块基本遵循 `Screen + ViewModel + Repository (+ Dto / UiState)` 切分，并自带 JVM 单测。

| 模块 | 关键文件 | 作用 | 自带单测 |
| --- | --- | --- | --- |
| `auth` | LoginScreen / LoginViewModel / RegisterViewModel / AuthRepository / AuthNavGraph / **TokenStorage** | 登录注册 + JWT 加密存储（EncryptedSharedPreferences） | AuthRepository / LoginViewModel / TokenStorage |
| `hub` | HubScreen / HubViewModel / HubRepository | 像素小镇主屏；调 `BootstrapApi` + `CalendarApi`（含 Room 离线回退） | HubViewModel / HubBridge |
| `dialog` | DialogRepository / DialogViewModel / DialogTree / NpcDialogPanel | NPC 对话树推进 | DialogRepository / DialogTree |
| `profile` | ProfileViewModel / data/ProfileRepository / ui/SubjectiveDimensionForm | 8 维画像（含 4 个主观维度自评表单） | ProfileRepository / ProfileViewModel / SubjectiveDimensionForm |
| `fields` | FieldsScreen / FieldsViewModel / FieldsRepository / FieldsDto / FieldsPalette / KcTile | 知识田 / KC 网格 | FieldsViewModel / KcTile |
| `workshop` | WorkshopViewModel / WorkshopRepository / CodeEditor / SubmissionResultPanel | 资源工坊 + 代码编辑 + 提交结果面板 | WorkshopViewModel / WorkshopRepository |
| `arena` | ArenaScreen / ArenaRepository / ArenaViewModel / ChapterTowerScreen / ChapterTowerViewModel / CountdownTimer | 训练场 + 闯关塔 + 倒计时 | ArenaViewModel / ChapterTowerViewModel / CountdownTimer |
| `mine` | MineScreen / MineViewModel / MineRepository / MineDto / MineLayerProgress | Bug 矿洞调试（分层进度） | MineViewModel / MineRepository |
| `retro` | RetroScreen / RetroViewModel / RetroRepository | 复盘大厅 + 评估 | RetroRepository / RetroViewModel |
| `path` | PathScreen / PathViewModel / PathRepository / PathGraphView | 学习路径图 | PathRepository / PathViewModel |
| `classroom` | ClassroomScreen / ClassroomViewModel / ClassroomRepository | 班级视角 | ClassroomRepository / ClassroomViewModel |
| `tutor` | TutorScreen / TutorViewModel / TutorRepository / TutorDto | 导师问答（SSE 流式 `POST /api/tutor/ask`，含 grounding/evidence） | TutorRepository |
| `library` | LibraryScreen / LibraryViewModel / LibraryRepository / LibraryDto | 课件图书馆 | LibraryRepository |
| `boss` | BossScreen / BossViewModel / BossRepository / BossDto | 章节 BOSS 战（`/api/world/boss`） | BossRepository |
| `endless` | EndlessScreen / EndlessViewModel / EndlessRepository / EndlessDto | 无尽练习 | EndlessRepository |
| `lecture` | LectureScreen / LectureViewModel / LectureRepository / LectureDto | 讲义阅读 | LectureRepository |

导航壳（`MainScreen.kt`）顶部 dock 共 15 个入口：小镇 / 答疑 / 画像 / 知识田 / 工坊 / 图书馆 / 训练场 / BOSS 战 / 无尽练习 / 讲义 / 闯关塔（arena 的 ChapterTower）/ 矿洞 / 复盘 / 路径 / 班级。

## 5. `:game` 像素小镇（LibGDX）

18 个 Kotlin 文件，已是一个可运行的 LibGDX 实现：

- 入口/嵌入：`HubLibGdxFragment`（`AndroidFragmentApplication`）、`HubGameLauncher` / `TownGameLauncher`、`HubGameBridge`（与 Compose 通信）。
- 渲染：`PythontopiaGame` / `TownGame` / `HubGame` / `WorldRenderScreen` / `HubScene` / `TiledMapVisual` / `WorldMapData` / `PlayerActor` / `WorldCharacter` / `InputController`（8 向输入）/ `GameAssets` / `PixelTheme`。
- 素材：`ProceduralArt` 在二进制瓦片/精灵缺失时按 Kenney tiny-town 帧语义**程序化生成**等价配色（纯算法、CC0），保证地图与角色可见。
- 已 vendored 的真实素材：`app/src/main/assets/`（Kenney tiny-town `tilemap_packed.png`、`dungeon.png`、`player*.png` 与 16 个 `npc/*.png`，附 `LICENSE-kenney-tiny-town.txt`）；地图为 **Tiled JSON**（`town_hub.json` + 4 张外围图 `farm_north/mine_south/campus_east/port_west`）。
- LibGDX native：`.so` 已 vendored 到 `app/src/main/jniLibs/<abi>/`（见该目录 README）。

## 6. 剩余工作（诚实清单）

- **R-14 单中央 hub 收敛**［进行中］：`:game` 仍带 5 张地图 JSON 与「5 图」注释，需收敛到单一 `town_hub`（参见根 `AGENTS.md` R-14 方案 A）。
- **OpenAPI 代码生成接线**［进行中］：`openapi/pythontopia-api.json` 是手写的最小子集，DTO/API 仍是手写；目标是从后端 `/v3/api-docs` 全量生成（见 §7 与 `openapi/README.md`）。
- **真实像素素材 vendoring**［进行中］：部分帧/精灵已落地，缺失部分目前由 `ProceduralArt` 兜底，仍需补齐审计过的 CC0/CC-BY 素材并同步 `LICENSE-ART.md`。
- **instrumented 测试缺失**：`androidTest` source set 已声明但**无任何用例**；Compose UI / 真机链路目前未覆盖。
- **手写 DTO**：`:core:data` 与各 feature 的 DTO 为手写，存在与后端契约漂移的风险（详见 [`docs/04-backend-contract-drift.md`](docs/04-backend-contract-drift.md)）。
- **契约对齐**：客户端虚构的 inventory/currency、对 `/api/auth/refresh` 的误解、存档槽位范围等需按真实后端修正（同上文档）。
- **CI / release 签名**：`assembleRelease` 在无 `keystore.properties` 时回退 debug 签名；正式 CI workflow 与签名密钥尚未接入。

## 7. 开发命令

```bash
cd /home/cypress/Pythontopia/android
./gradlew :app:assembleDebug      # 构建 debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew test                    # 全模块 JVM 单测（39 个用例）
./gradlew lint                    # Android Lint
./gradlew detekt                  # detekt 静态分析（config/detekt/detekt.yml）
```

### 首次本机准备

1. **JDK 17 或 21**（Temurin LTS；工程 JVM target=17）。
2. **Android SDK platform-35 + build-tools-35**（Android Studio 首启自动下载，或命令行 `sdkmanager "platforms;android-35" "build-tools;35.0.0"`），并在 `android/local.properties` 写 `sdk.dir=...`。
3. **生成 gradle wrapper 一次**：`gradle wrapper --gradle-version 8.11.1 --distribution-type bin && chmod +x gradlew`（详见 `WRAPPER_SETUP.md`）。
4. **跑一次 debug 构建确认链路**：`./gradlew :app:assembleDebug`。

> 国内/WSL2 网络：`settings.gradle.kts` 已配 tencent/aliyun 镜像；访问 `dl.google.com` 慢时先 `export HTTPS_PROXY=http://127.0.0.1:7890`（按本机代理端口改）。

## 8. 不做（边界）

- 不复用 Web 端 Phaser 代码 / `tilemap.ts` / `frontend/src/api/*.ts`。
- 不内嵌 LLM 推理（沿用 backend Graph 服务）。
- 不复制《星露谷物语》官方素材（遵守根 `AGENTS.md` 与 `LICENSE-ART.md`）。
- 不在 Android 端运行 Python（Beta 首发仅服务端判题）。
- 不上架 Google Play（演示阶段直接发 debug APK）。
