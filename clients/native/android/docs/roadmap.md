# Pythontopia Android Roadmap

> 本文件于 **2026-06-05** 按仓库实际代码重写。早期版本把所有阶段标成「待启动」，与实际严重不符。
>
> 实测基线：**22 个 Gradle 模块 / 16 个 feature module / ~24.6K 行 Kotlin / 39 个 JVM 单测**，`./gradlew :app:assembleDebug` 已能产出可安装的 `app-debug.apk`（~16.6 MB）。
> 编号约定：**Android-RN**（与 Web 端 R-N、素材轮编号独立）。

## 全局状态（实测）

骨架、设计系统、网络/认证、游戏渲染、以及全部 16 个学习面板的脚手架 **均已落地并可构建**。原 roadmap 中 R1~R14 列出的"逐个新建 feature 模块"工作，绝大多数已经在仓库里完成（虽与原计划的顺序/编号不完全对应）。

| 区块 | 内容 | 状态 |
| --- | --- | --- |
| 工程骨架 | Gradle 8.x KTS + `libs.versions.toml` + 22 模块 + Hilt/KSP 总装 | ✅ 已落地 |
| `:core:design` | Material3 + Pal-1 暗色 token（Colors/Typography/Space/Shape/Feedback） | ✅ 已落地 |
| `:core:network` | Retrofit/OkHttp + AuthInterceptor + SessionState + 4 个 API | ✅ 已落地（API 覆盖不全，见下） |
| `:core:data` | 手写 `@Serializable` DTO + Room（PytDatabase/CalendarCache） | ✅ 已落地（**手写**，未走 codegen） |
| `:feature:auth` | 登录/注册 + JWT 加密存储（EncryptedSharedPreferences） | ✅ 已落地 |
| `:game` | LibGDX 1.13.1 像素小镇 + `AndroidFragmentApplication` 嵌入 + 程序化美术兜底 | ✅ 可运行（R-14 已收敛单 town_hub） |
| 16 个学习面板 | hub/dialog/profile/fields/workshop/arena/mine/retro/path/classroom/tutor/library/boss/endless/lecture | ✅ 脚手架 + ViewModel + Repository + 单测均在 |
| Compose 导航壳 | 登录 → AppShell（15 页顶部 dock） | ✅ 已落地 |
| JVM 单测 | 39 个用例（JUnit5 + MockK + Robolectric + Turbine） | ✅ 全模块覆盖 ViewModel/Repository |
| debug APK | `:app:assembleDebug` 产出 `app-debug.apk` | ✅ 已验证 |

## 进行中 / 下一步（真实待办）

| 优先级 | 事项 | 说明 | 状态 |
| --- | --- | --- | --- |
| 高 | **R-14 单中央 hub 收敛** | ✅ `WorldMaps` 仅留 `townHub`，外围 4 图定义与 `mine_south.json` 资产移除，单测/注释/地图 README 同步收敛（根 `AGENTS.md` R-14 方案 A） | ✅ 已完成 |
| 高 | **OpenAPI 代码生成接线** | 用后端 `/v3/api-docs` 全量生成 Kotlin Retrofit2 client，替换手写 DTO/API；现仅有手写最小子集 `openapi/pythontopia-api.json` | 🚧 进行中 |
| 高 | **后端契约对齐** | ✅ 移除虚构 inventory/currency（D-1/D-2）+ `GameBootstrapResponseDto` 补齐真实字段（D-3，新增 `:core:data` 学习域 DTO）；⏳ `/api/auth/refresh` 语义（D-4）与存档槽位（D-5）随会话/存档层处理（详见 `04-backend-contract-drift.md`） | 🟡 进行中 |
| 中 | **真实像素素材 vendoring** | 补齐审计过的 CC0/CC-BY 瓦片与精灵，减少 `ProceduralArt` 兜底；同步 `LICENSE-ART.md` | 🚧 进行中 |
| 中 | **网络层补全** | `:core:network` 目前仅 4 个 API（auth/bootstrap/npc/calendar）；profile/fields/workshop/arena/mine/retro/path/classroom/boss/endless/lecture 等端点需要补齐（部分 feature 现自带内联接口） | 🚧 待办 |
| 中 | **instrumented 测试** | `androidTest` source set 已声明但**无任何用例**；Compose UI / 真机链路未覆盖 | ❌ 未开始 |
| 低 | **CI/CD + release 签名** | `assembleRelease` 无 keystore 时回退 debug 签名；正式 GitHub Actions workflow + 签名密钥未接入 | ❌ 未开始 |
| 低 | **Macrobenchmark / Roborazzi** | ADR-001 规划的性能基准与截图测试尚未引入版本目录 | ❌ 未开始 |
| 低 | **Beta 验收** | 真机矩阵（Android 8.0~15）+ 演示脚本 | ❌ 未开始 |

## 阶段验收原则（保持不变）

每个改动按影响范围验证：

1. **构建通过**：`./gradlew :app:assembleDebug` 退出码 0。
2. **测试通过**：`./gradlew testDebugUnitTest` 全绿；触及 Compose UI 的轮次需补 Robolectric/instrumented 用例。
3. **静态分析**：`./gradlew lint detekt` 无新增 violation。
4. **文档**：本文件与 `CHANGELOG.md` 同步；触及决策变更时新增 ADR-N 或在 ADR-001 追加附录（不改写历史）。
5. **可回滚**：每次 commit 可 `git revert` 而不破坏前一稳定态。

## 与 Web 端 / 主仓库的关系

- **共享**：后端 Spring Boot + Agent + RAG + course-pack-43 数据；design token 语义（颜色名/间距/字号，颜色值反相为暗色）。
- **隔离**：构建系统、依赖、测试、CI workflow、游戏渲染层（Web Phaser / Android LibGDX）、编号空间。
- **教学闭环映射**（R-14 已去农场/作物隐喻）：看（课件/RAG）→ 学（工坊资源）→ 练（训练场判题）→ 测（掌握度）；Bug = 矿洞调试支线；复盘 = 复盘大厅。

## 失败回退预案

如发现某项决策不可行（如 OpenAPI Generator 对关键 sealed union 不支持、LibGDX + Compose 嵌入生命周期冲突）：

- 写 ADR-N 记录变更，或在 ADR-001 追加附录。
- 回退到上一稳定 commit。
- 由用户拍板是否继续当前路线。
