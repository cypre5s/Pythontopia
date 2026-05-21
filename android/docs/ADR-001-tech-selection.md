# ADR-001 · Pythontopia Android 客户端关键技术选型

**状态**：已锁定（2026-05-22 / Android-R0 启动期）  
**作者**：基于 10 个并发 subagent 调研报告（`.work/thinking/android_research_{01..10}.md`）汇总  
**取代**：无  
**关联**：`android/README.md`、`android/docs/roadmap.md`

## 1. 决策摘要

| # | 决策项 | 锁定结论 | 关键依据 | 来源 |
| --- | --- | --- | --- | --- |
| 1 | 构建系统 | Gradle 8.11.1 + Kotlin DSL + `gradle/libs.versions.toml` 版本目录 | 类型安全、IDE 提示、版本集中托管、Renovate 友好 | A1 |
| 2 | 模块拓扑 | `:app + :core:common + :core:data + :core:design + :core:network + :game + :feature:{profile,fields,workshop,arena,mine,retro,path,classroom}` 多模块 | 7 个学习面板物理隔离强制职责单一，与 Web `frontend/src/pages/` 一一对应 | A1, A6, A9 |
| 3 | 语言 / UI 框架 | Kotlin 2.2.0 + Jetpack Compose（Material 3）+ Compose BOM 2025.04.00 | 团队 Kotlin 母语 + Material 3 含 dark mode 双主题 + 测试友好 | A1, A6 |
| 4 | minSdk / targetSdk | minSdk 26（Android 8.0）/ targetSdk 35（Android 15）/ compileSdk 35 / JVM Toolchain 17 | 95% 设备覆盖 + 满足 Google Play 2025-08-31 政策 + 保留 36 升级路径 | A1 |
| 5 | 像素游戏渲染 | **主渲染**：LibGDX 1.14.0 + TmxMapLoader（Tiled `.tmx` 一等公民）；**教学面板挂件**：Compose Canvas DrawScope；嵌入方式 `AndroidView { GLSurfaceView / GameSurface }` | LibGDX 评分 36/40，APK 增量仅 4-6 MB，社区活跃，与 Web Phaser `pixelArt:true` 范式一致 | A2 |
| 6 | Python 执行 | **Beta 首发仅服务端判题**：所有提交走 `POST /api/submissions` → 后端 `SubprocessPythonSandbox` → trace 走 `GET /api/bug-mine/sessions/{id}`；Chaquopy 17.0.0 仅作为 Beta+1 可选预览插件，UI 明确区分 | AGENTS.md "Pyodide 不能当正式判题结果" 同等约束、APK 体积小、与 Web 端判题 100% 一致 | A3 |
| 7 | 主题与字体 | **配色**：Material 3 + 局部替换策略 + Pal-1 深夜炉火（反相 Web 棕色 tokens.css）；**字体**：Fusion Pixel 12px（OFL，中文正文）+ Press Start 2P 8px（OFL，英文标题）+ JetBrains Mono（OFL，代码） | 三字体均 OFL 商用免费、与 Web LXGW WenKai 形成姊妹感、可访问性兼顾 | A4 |
| 8 | 网络层 | Retrofit 2.12.0 + OkHttp 5.3.2 + `kotlinx-serialization-json` 1.11.0 + `kotlinx-coroutines-android` 1.10.2 + `kotlinx-datetime` 0.6.2 | Android 圈事实标准、Spring Boot ApiError 映射零样板、协程一等公民、sealed 多态对 `BugMineCardDto` 友好 | A5 |
| 9 | 依赖注入 | Hilt 2.59.2 + `hilt-navigation-compose` 1.3.0 + KSP（不用 KAPT） | 编译期安全、Google 官方推荐、NavGraph ViewModel 共享原生支持 | A6 |
| 10 | 状态管理 | **复杂页（Training / BugMine / Evaluation / ProfileInterview）**：MVI（sealed Intent + sealed UiState + Reducer）；**简单页（Session / Dialog / Toast）**：MVVM + StateFlow；分界线：mutable 字段 ≥ 4 走 MVI | 单一 UiState 消除"loading=true 但 phase=done"不一致 | A6 |
| 11 | ViewModel 切分 | 把 Web `learning.ts`（21KB，9 子状态）拆成 8 个独立 ViewModel：`ProfileViewModel / FieldViewModel / WorkshopViewModel / PathViewModel / QuestsViewModel / TrainingViewModel / BugMineViewModel / EvaluationViewModel`，总计 12 个 ViewModel | 单 store 21KB 在 Android 单测不可写 | A6 |
| 12 | JWT 存储 | `EncryptedSharedPreferences`（`androidx.security:security-crypto 1.1.0-alpha06`）+ `MasterKey.setUserAuthenticationRequired(true)` + Keystore；BiometricPrompt（`androidx.biometric 1.1.0`）+ `BIOMETRIC_STRONG` + `CryptoObject` | OWASP MASVS-L1 达标、minSdk 26 兼容、API 与 SharedPreferences 同源、迁移成本最低 | A7 |
| 13 | Refresh Token | **短期不做**（后端 `/api/auth/refresh` 仍返回 501）；客户端在 token 过期前 5 分钟提示重新登录 | 后端 R-100 期不扩接口；本地认证 + 生物识别覆盖体验 | A7 |
| 14 | DTO 对齐 | **OpenAPI Generator 7.22.0** + 后端 springdoc 2.6.0 `/v3/api-docs` 自动生成 Kotlin Retrofit2 client；**少量** sealed union（如 `WorkshopContent`）走手工补丁层 `core/api/manual/` + `.openapi-generator-ignore` | 后端是单一真理源、消灭"三套 DTO 漂移"、CI 集成 `openapi-diff` 自动检测 schema drift | A9 |
| 15 | 测试栈 | JUnit 5（+ vintage 兼容 JUnit 4 用于 Compose）+ MockK 1.13.12 + Turbine 1.1.0 + Robolectric 4.13 + Compose UI Test + MockWebServer 4.12 + Macrobenchmark 1.3 + Roborazzi 1.32（截图） + Kover 0.8.3（覆盖率） | 不依赖真机即可跑 90% 测试、与 Web Vitest / 后端 JUnit5 等价 | A8 |
| 16 | 静态分析 | Android Lint（AGP 内置） + Detekt 1.23.7（含 `detekt-formatting` 集成 ktlint 规则） | 三层覆盖：语义 / 风格 / 平台规则；避免 Spotless 与 detekt 重叠 | A1, A8 |
| 17 | CI / CD | GitHub Actions `ubuntu-latest` + `actions/setup-java@v4`（Temurin 21）+ `gradle/actions/setup-gradle@v4`；PR 跑 `lintDebug + testDebugUnitTest + assembleDebug`；release 事件签名 APK + AAB（keystore base64 → GitHub Secrets）；R8 默认开启 | 零额外费用、与现有 backend/frontend/python CI 风格一致、签名密钥安全 | A10 |
| 18 | 发布合规 | 演示阶段直接发 debug APK（GitHub Releases pre-release）；上架 Google Play 留到 Beta+1，需要补隐私政策 + 权限说明 + 移除真实学生 PII | 比赛交付不必上架、降低首发合规成本 | A10 |

## 2. 版本统一锁定（`gradle/libs.versions.toml` 草案）

各 subagent 报告的 Kotlin / AGP / Compose BOM 版本不一致（2.0.21 / 2.1.20 / 2.2.20 / 2.3.21）。Android-R0 锁定为**中位稳健组合**：

```toml
[versions]
# 构建工具链
agp                 = "8.7.3"          # LTS-like, 与 Kotlin 2.2 / Compose BOM 2025.04 兼容
kotlin              = "2.2.0"          # Compose Compiler 内嵌 (Kotlin 2.0+ 起内嵌)
ksp                 = "2.2.0-1.0.24"
jvmToolchain        = "17"

# Android SDK
compileSdk          = "35"
minSdk              = "26"
targetSdk           = "35"

# Compose
composeBom          = "2025.04.00"
material3           = "1.3.0"

# Lifecycle / Navigation
lifecycle           = "2.8.6"
navigationCompose   = "2.8.3"
hiltNavCompose      = "1.2.0"

# DI
hilt                = "2.52"           # 与 Kotlin 2.2 兼容; 后续可升 2.59.x
hiltExt             = "1.2.0"

# 网络 / 序列化
retrofit            = "2.11.0"
okhttp              = "4.12.0"         # 与 Retrofit 2.11 默认依赖一致, 后续单独升 5.x
kotlinxSerialization = "1.7.3"
kotlinxCoroutines   = "1.9.0"
kotlinxDatetime     = "0.6.1"

# 安全
securityCrypto      = "1.1.0-alpha06"
biometric           = "1.1.0"

# Room / DataStore
room                = "2.6.1"
datastore           = "1.1.1"

# 游戏引擎
libgdx              = "1.13.1"         # 稳定通道; 1.14.x 待跟踪

# 测试
junit5              = "5.10.3"
junit4              = "4.13.2"
mockk               = "1.13.12"
turbine             = "1.1.0"
robolectric         = "4.13"
composeTest         = "1.7.5"
mockwebserver       = "4.12.0"
macrobenchmark      = "1.3.2"
roborazzi           = "1.32.2"
kover               = "0.8.3"

# 静态分析
detekt              = "1.23.7"
```

> 后续升级遵守"小步迭代 + 单独 PR"原则；每次只升 1-2 个版本，跑全量测试通过再合入。

## 3. 与 Web 端 Pythontopia 的对接矩阵

| Android 模块 | 对接 Web 端 / 后端 | 备注 |
| --- | --- | --- |
| `:app` | 无直接对接 | DI 总装 + Navigation 图入口 |
| `:core:common` | `frontend/src/dto/` 类型语义 | Kotlin sealed/enum 与 TS 类型对齐 |
| `:core:data` | OpenAPI Generator → `backend springdoc /v3/api-docs` | 单一真理源，自动 schema drift 检测 |
| `:core:network` | `backend/src/main/java/com/pythontopia/*/controller/` 所有 REST | 至少 28 个端点（A5 与 A9 报告内列出） |
| `:core:design` | `frontend/src/styles/tokens.css` | 1:1 token 名映射 + 反相暗色（A4 §4.1 矩阵） |
| `:game` | `frontend/src/game/tilemap.ts` 的 5 建筑 + 3 NPC 语义 | 重新导出 `town_main.tmx`，**不复用** Phaser 代码 |
| `:feature:profile` | `frontend/src/pages/ProfileRoomPage.vue` + `ProfileInterviewPanel.vue` + backend `/api/learner/profile*` | 8 维画像访谈 |
| `:feature:fields` | `FieldPage.vue` + `FieldCanvas.vue` + backend `/api/world/crops` + `/api/learner/mastery` | 65 KC 作物状态 |
| `:feature:workshop` | `WorkshopPage.vue` + backend `/api/workshop/*` + graph `/agents/resource/*` | 6 类资源生成 |
| `:feature:arena` | `TrainingPage.vue` + backend `/api/submissions` + `/api/problems/{id}/public` | Bug 矿洞入口 |
| `:feature:mine` | `BugMinePage.vue` + backend `/api/bug-mine/*` | trace + 6 卡片 + Layer 1/2/3 |
| `:feature:retro` | `ReviewHallPage.vue` + backend `/api/learner/evaluation/*` | 复盘大厅 |
| `:feature:path` | `BoardPage.vue` + backend `/api/path` + `/api/quests` | 公告板 + 每日任务 |
| `:feature:classroom` | `TeacherPage.vue` + backend `/api/teacher/*` + `/api/classroom/*` | 教师 + 班级（学生视角可选） |

## 4. 决策的非目标 / 不做事项

- **不复用** Web 端 Phaser 代码 / `tilemap.ts` / `frontend/src/api/*.ts`
- **不内嵌** LLM 推理（沿用 backend graph 服务）
- **不复制** 《星露谷物语》官方素材（沿用 AGENTS.md）
- **不在 Android 端**运行 Python（Beta 首发，仅服务端判题）
- **不破坏** Web 端 234 测试基线（Web 冻结在 R-11，本目录独立运作）
- **不引入** Vuex / 散装 API 字段（沿用 AGENTS.md "前端硬约束"在 Android 等价物上）
- **不引入** ReAct 循环（沿用 AGENTS.md）
- **不上架** Google Play（Beta 首发，仅 GitHub Releases pre-release）

## 5. 版本冲突解决记录

| 来源 | Kotlin | AGP | Compose BOM | 结论 |
| --- | --- | --- | --- | --- |
| A1 | 2.1.20 | 8.7.3 / 8.9.x | 2025.04.00 | 部分采纳 AGP 8.7.3 |
| A3 | 2.3.21 | 9.2 | 2026.05.00 | 不采纳（过新，AGP 9.x 仍 alpha） |
| A4 | - | - | 2026.04.01 | 不采纳（与 Kotlin 2.2 配套不够稳） |
| A6 | 2.2.20 | - | 2026.05.00 | Kotlin 部分采纳（取 2.2.0 而非 2.2.20，差异极小） |
| A8 | 2.0.21 | 8.6.1 | 2024.09.03 | 不采纳（过旧） |
| **本 ADR 锁定** | **2.2.0** | **8.7.3** | **2025.04.00** | **中位稳健组合** |

后续若 Kotlin 2.3 / AGP 9 / Compose BOM 2026.05 全部 GA 且与 Hilt 2.59 / KSP 等配套，按"小步迭代单独 PR"规则升级。

## 6. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| LibGDX + Compose 嵌入的 GLSurface 生命周期问题 | 计划期内使用 `AndroidFragmentApplication`，后续切 `AndroidExternalSurface`（社区 2025-2026 推荐） |
| OpenAPI Generator 对 sealed union 支持有限 | 保留手工补丁层 `core/api/manual/` + `.openapi-generator-ignore` |
| Chaquopy 体积 25-35 MB 增量 | Beta 首发不集成；如必须，使用 `abiFilters arm64-v8a` 限单架构 |
| 后端无 Refresh Token 接口 | 客户端 5 分钟前提示重登；长期建议后端 R-100 后扩 `/api/auth/refresh` |
| Material 3 默认动效与像素风冲突 | 自定义 `MotionScheme`（≤ 80ms linear）+ 替换 ripple 为 PixelIndication |
| 字体子集体积（Fusion Pixel 12px ~ 1.6 MB） | Beta 首发可接受；后续按使用频率拆 woff2 / ttf 子集 |
| `image-rendering: pixelated` 在 Android 等价物（`BitmapFactory.Options` + `Paint.FILTER_BITMAP_FLAG=false`）覆盖率 | 设计阶段 lint 自定义规则强制 |
| 签名密钥泄露 | 仅入 GitHub Secrets；本仓库 `.gitignore` 已加 `*.jks / *.keystore / keystore.properties` |
| 比赛叙事：Beta 日期推迟 | 用户已明确接受（2026-05-22 决策） |

## 7. 决策依据子代理报告索引

- `.work/thinking/android_research_01.md`（295 行）项目结构与构建系统
- `.work/thinking/android_research_02.md`（499 行）像素游戏引擎对比 + LibGDX 选型
- `.work/thinking/android_research_03.md`（274 行）Python 沙箱方案
- `.work/thinking/android_research_04.md`（353 行）暗色像素主题 + 字体
- `.work/thinking/android_research_05.md`（429 行）网络层 + Spring Boot REST 对接 + 28 端点列表
- `.work/thinking/android_research_06.md`（493 行）DI + 状态管理 + 12 ViewModel 草案
- `.work/thinking/android_research_07.md`（392 行）JWT 存储 + 生物识别 + MASVS 评估
- `.work/thinking/android_research_08.md`（369 行）测试金字塔 + 版本号
- `.work/thinking/android_research_09.md`（349 行）DTO 对齐 + OpenAPI Generator + 28 DTO 映射表
- `.work/thinking/android_research_10.md`（498 行）CI/CD + APK 签名 + 商店合规

> 上述子代理报告位于 `.gitignore` 的 `.work/` 目录，**不入主 commit**；本 ADR-001 已提炼关键结论，原始报告作为留档参考。
