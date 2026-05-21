# Pythontopia Android Roadmap

> 基于 `ADR-001-tech-selection.md` 锁定的技术栈，定义从空白到 MVP 到 Beta 的阶段计划。
>
> 编号约定：**Android-RN**（与 Web 端 R-100 / 素材 R-100 #N 独立命名空间）。
> 每阶段独立 commit，全部跑测试通过再合入下一阶段。

## 全局状态

| 阶段 | 名称 | 状态 |
| --- | --- | --- |
| **Android-R0** | 启动期：调研 + ADR + 骨架文档 | **进行中** |
| Android-R1 | Gradle 项目骨架 + `libs.versions.toml` + `:app` 模块 + Hello World | 待启动 |
| Android-R2 | 多模块拆分（`:core:common / :core:data / :core:design / :core:network`）+ Hilt 总装 | 待启动 |
| Android-R3 | OpenAPI Generator 集成 + 后端 DTO 自动生成 + Retrofit 客户端骨架 | 待启动 |
| Android-R4 | 认证模块（`:feature:auth`）：Login + Register UI + JWT 存储 + EncryptedSharedPreferences + BiometricPrompt | 待启动 |
| Android-R5 | `:core:design`：Material 3 + Pal-1 暗色 + Fusion Pixel / Press Start 2P / JetBrains Mono + token 映射 | 待启动 |
| Android-R6 | `:game` 模块：LibGDX 集成 + `AndroidFragmentApplication` + 第一张瓦片地图（town main） | 待启动 |
| Android-R7 | `:feature:profile`：8 维画像访谈 + `ProfileViewModel` MVI | 待启动 |
| Android-R8 | `:feature:fields`：知识田 + 65 KC 作物 + LibGDX 地块渲染 | 待启动 |
| Android-R9 | `:feature:workshop`：6 类资源生成 + grounding 标注 + `WorkshopViewModel` | 待启动 |
| Android-R10 | `:feature:arena`：训练场 + 代码编辑器 + 服务端判题（Pyodide 不集成） | 待启动 |
| Android-R11 | `:feature:mine`：Bug 矿洞 + 6 卡片 + Layer 1/2/3 + 重试提交 | 待启动 |
| Android-R12 | `:feature:retro`：复盘大厅 + 评估 + 路径 accept/keep | 待启动 |
| Android-R13 | `:feature:path`：公告板 + 每日任务 + 班级任务（学生视角） | 待启动 |
| Android-R14 | `:feature:classroom`：教师视角（班级 + KC 热力图 + 高频误区 + 资源使用） | 待启动 |
| Android-R15 | 视觉打磨 + 像素动效 + dark mode 一致性 + 性能 (Macrobenchmark) + 截图测试 (Roborazzi) | 待启动 |
| Android-R16 | CI/CD 上线（`.github/workflows/android.yml`）+ 签名 release APK + GitHub Releases | 待启动 |
| Android-R17 | Beta 验收 + 演示视频脚本 + 真机测试矩阵（Android 8.0 ~ 14） | 待启动 |
| Android-R100 | 持续小步迭代（与 Web 端 R-11~R-100 对称） | 待启动 |

## Android-R0 任务清单（本轮）

- [x] `android/` 顶级目录
- [x] `android/.gitignore`
- [x] `android/README.md`
- [x] `android/docs/README.md`
- [x] 10 个并发 subagent 调研报告 → `.work/thinking/android_research_{01..10}.md`
- [x] `android/docs/ADR-001-tech-selection.md`
- [x] `android/docs/roadmap.md`（本文件）
- [x] 根 `README.md` 加 Android 章节
- [x] `AGENTS.md` 补 Android 模块约定
- [x] `docs/execution_status.md` 标记方向变更
- [x] `CHANGELOG.md` 追加 Android-R0 段

## Android-R1 启动条件（下一轮）

- 需用户确认 ADR-001 的关键决策（Kotlin 2.2.0 / AGP 8.7.3 / Compose BOM 2025.04.00 / LibGDX 1.13.1 主渲染 / 仅服务端判题）
- 需用户在本机安装 JDK 17（Temurin 21 也兼容）+ Android Studio Ladybug+（或同等版本的 IDE）
- 主项目 `backend` 保持运行（演示用 docker compose）
- `frontend/` Vue 端代码冻结，不接受 R-100 之后的迭代

## 阶段验收原则

每阶段完成后必须：

1. **构建通过**：`cd android && ./gradlew assembleDebug` 退出码 0。
2. **测试通过**：`./gradlew lintDebug testDebugUnitTest` 全绿；触及 Compose UI 的轮次额外跑 `connectedAndroidTest`（或 Robolectric 替代）。
3. **静态分析**：`./gradlew detekt` 无新 violation；`lint-baseline.xml` 仅允许"既有遗留"。
4. **文档**：本文件标记阶段为"已完成"；`CHANGELOG.md` 追加该阶段段落；如果触及决策变更，新增 ADR-N。
5. **commit**：独立 commit，message 格式 `feat(android-rN): ...` 或 `chore(android-rN): ...`。
6. **可回滚**：每阶段 commit 都能 `git revert` 而不破坏前一阶段。

## 时间预估（极粗略）

| 区段 | 阶段 | 估时 | 备注 |
| --- | --- | --- | --- |
| 启动期 | R0 ~ R3 | 1 周 | 骨架 + DTO 自动化 + 网络层 |
| MVP 基础 | R4 ~ R6 | 1.5 周 | 认证 + 设计系统 + 游戏画布 |
| 教学闭环 | R7 ~ R14 | 4 周 | 8 个 feature 模块 |
| 收尾 | R15 ~ R17 | 1.5 周 | 视觉 / CI / Beta |
| 合计 | R0 ~ R17 | ~8 周 | 比 Web 端原 Beta（2026-06-21）推迟约 7-8 周 |

> 这是顺序估时；并发投入可压缩。但 LibGDX + Compose 嵌入、OpenAPI Generator 落地、BiometricPrompt 接入这三个关键路径预期会消耗最多调试时间。

## 与 Web 端 / 主仓库的关系

- **共享**：后端 Spring Boot + Agent + RAG + course-pack-43 数据。
- **共享**：design token 语义（颜色名 + 间距 + 字号）；颜色值反相暗色。
- **共享**：教学闭环 7 步（KC = 种子 / 浇水 / 除草 / 矿洞事件 / 作物成长 / 枯萎 / 收获）。
- **隔离**：构建系统、依赖、测试、CI workflow、`.gitignore` 规则。
- **隔离**：游戏渲染层（Web Phaser / Android LibGDX）。
- **隔离**：编号空间（Web R-N / Android-RN / 素材 R-100 #N）。

## 失败回退预案

如果在 R0 ~ R6 期间发现某项技术决策不可行（如 LibGDX + Compose 嵌入冲突无法解决、OpenAPI Generator 对关键 sealed union 不支持），则：

- 立即写 ADR-N 记录决策变更。
- 重新跑相关 subagent 调研。
- 回退到 R0 baseline commit（如不可逆）或上一稳定 R-N。
- 用户拍板是否继续 Android 化 / 转向其他方向（如 React Native / Flutter / 重启 Web R-100）。
