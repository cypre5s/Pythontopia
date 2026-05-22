# Pythontopia Android（原生客户端）

> Pythontopia 主项目（Vue 3 + Phaser 3 网页版）的**原生 Android 客户端分支**。
> 复用 Pythontopia 后端（Spring Boot REST + Graph + RAG）作为唯一数据底座，前端完全用 Kotlin + Jetpack Compose（+ 像素游戏渲染引擎）重写。
>
> **状态**：Android-R0 启动期，技术选型调研中。
> **目录定位**：本目录在 `/home/cypress/Pythontopia/android/` 下独立运作，**不影响**主仓库的 `frontend/` Vue 端代码。

## 1. 为什么新增 Android 端

- 网页 Phaser 端已完成 234 / 234 测试基线（详见根 `README.md`），核心教学闭环已验证。
- 用户决策（2026-05-21）：在不放弃后端 / Agent / RAG 复用的前提下，**新增**一条原生 Android 客户端路线，把"像素学习小镇"带到移动端。
- 原 Web 端 R-100 迭代暂停，资源迁移到 Android 化路线。

## 2. 目标

- **必须**：完整复刻 Web 端 7 步教学闭环（画像 → 知识田 → 资源工坊 → 训练场 → Bug 矿洞 → 复盘大厅 → 路径重排）。
- **必须**：复用 Spring Boot 后端的全部 REST API，不重写业务逻辑。
- **必须**：保持像素小镇视觉风格 + dark mode 主题。
- **应该**：支持手机 + 平板两种屏幕形态。
- **可以**：离线缓存学习资源（Room + DataStore）。
- **不做**：脱离后端独立运行（不内嵌 RAG / Agent，所有 LLM 调用经后端）。

## 3. 技术栈（草案，待 ADR-001 锁定）

| 层 | 技术 | 备注 |
| --- | --- | --- |
| 语言 | Kotlin 2.x | 不用 Java |
| UI | Jetpack Compose（Material 3） | 不用 XML View |
| 游戏渲染 | LibGDX（候选）/ Compose Canvas（候选）/ 自研 GameSurface（候选） | 待 R01 调研锁定 |
| 状态 | MVI + StateFlow | 不用 LiveData |
| 依赖注入 | Hilt | 不用 Koin |
| 网络 | Retrofit + OkHttp + Moshi | JWT 拦截器 |
| 本地存储 | DataStore（preferences）+ Room（缓存） | EncryptedSharedPreferences 存 JWT |
| 测试 | JUnit 5 + MockK + Compose Test + Robolectric | + Espresso E2E |
| 构建 | Gradle 8.x + KTS | 不用 Groovy DSL |
| 最低 SDK | 26（Android 8.0） | 覆盖 ~95% 设备 |
| 目标 SDK | 34（Android 14） | - |
| CI | GitHub Actions + APK debug build | 待 R10 调研 |

最终技术栈以 `docs/ADR-001-tech-selection.md` 为准（即将由 10 个并发 subagent 调研后汇总）。

## 4. 目录结构（占位，待骨架落地后扩展）

```text
android/
├── README.md                 # 本文件
├── .gitignore                # 忽略 build/.gradle/.idea 等
├── docs/                     # ADR / roadmap / 设计文档
│   ├── README.md             # 文档导览
│   ├── ADR-001-tech-selection.md
│   └── roadmap.md
├── app/                      # 主 application module（待 R0 骨架落地）
│   └── src/main/...
├── core/                     # 共享 kotlin module（dto / domain）
├── game/                     # 像素小镇渲染 module
└── feature-*/                # 各业务面板独立 feature module
```

## 5. 与主仓库的关系

- **共享**：后端 Spring Boot REST API、Agent 服务、RAG 服务、course-pack-43 数据。
- **隔离**：构建系统（Gradle 独立）、依赖、测试、CI workflow。
- **复用**：`frontend/src/dto/` 的类型定义会被翻译成 `android/core/data/dto/*.kt`（待 R09 自动化映射）。
- **不影响**：Web 端代码、Web 端测试、`deploy/docker-compose.yml`。

## 6. Android-R0 启动清单（本次 commit）

- [x] 顶级目录 `android/`
- [x] `android/.gitignore`
- [x] `android/README.md`
- [x] `android/docs/README.md`
- [ ] 10 个并发 subagent 调研报告（`.work/thinking/android_research_{01..10}.md`，10 个 agent 独立产出）
- [ ] `android/docs/ADR-001-tech-selection.md`（汇总后产出）
- [ ] `android/docs/roadmap.md`（基于 ADR-001 排期）
- [ ] 根 `README.md` / `AGENTS.md` / `docs/execution_status.md` / `CHANGELOG.md` 同步

## 7. 开发命令（待骨架落地后填充）

```bash
cd android
./gradlew tasks                          # 列任务
./gradlew assembleDebug                  # build debug APK
./gradlew test                           # JVM 单测
./gradlew connectedAndroidTest           # 真机 / Emulator E2E
./gradlew lint                           # Android Lint
```

## 7.1 首次本机准备

新协作者第一次在本机跑这个工程时，按以下 4 步初始化：

1. **装 JDK 21**（Temurin LTS 推荐）

   ```bash
   # SDKMAN 一行装
   sdk install java 21.0.5-tem
   java -version   # 期望：openjdk 21.x.x
   ```

2. **装 Android SDK platform-35 + build-tools-35**
   - 路径 A：用 Android Studio（Ladybug+），首启会自动下载 platform-35。
   - 路径 B：纯命令行：

     ```bash
     export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
     mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
     # 在 https://developer.android.com/studio#command-line-tools-only 拿到最新链接
     curl -L https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdline.zip
     unzip /tmp/cmdline.zip -d "$ANDROID_SDK_ROOT/cmdline-tools"
     mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
     export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
     yes | sdkmanager --licenses
     sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
     echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties   # in android/ 目录
     ```

3. **生成 gradle wrapper 一次**（详见 `WRAPPER_SETUP.md`）

   ```bash
   cd /home/cypress/Pythontopia/android
   gradle wrapper --gradle-version 8.11.1 --distribution-type bin
   chmod +x gradlew
   ```

4. **跑一次 debug 构建确认链路**

   ```bash
   ./gradlew :app:assembleDebug
   # 产物：android/app/build/outputs/apk/debug/app-debug.apk
   ```

### 已知问题

- **国内访问 `dl.google.com` 慢 / 失败**：先 `export HTTPS_PROXY=http://127.0.0.1:7890`（按本机代理端口改），
  再跑 `sdkmanager` 和 `./gradlew`。或在 `android/gradle.properties` 配 `systemProp.https.proxyHost=...`。
- **`./gradlew --version` 报找不到 jar**：说明 `gradle-wrapper.jar` 还没生成，回到第 3 步。
- **`SDK location not found`**：缺 `android/local.properties`，按上面第 2 步末尾的命令生成。

### 快速命令速查

```bash
cd /home/cypress/Pythontopia/android
./gradlew :app:assembleDebug      # 构建 debug APK
./gradlew test                    # 所有模块的 JVM 单测
./gradlew lint                    # Android Lint 全模块
./gradlew detekt                  # detekt 静态分析（配置 config/detekt/detekt.yml）
```

## 8. 不做（边界）

- 不复制 Web 端 Phaser 代码到 Android（重新设计游戏渲染层）。
- 不内嵌 LLM 推理（沿用 backend Graph 服务）。
- 不复制《星露谷物语》官方素材（继续遵守 `AGENTS.md`）。
- 不在 Android 端运行 Python（Pyodide 不可用；Chaquopy 性能与一致性待 R03 调研结论）。
- 不破坏 Web 端 234 测试基线（Web 端代码冻结在 R-11，不再迭代）。
