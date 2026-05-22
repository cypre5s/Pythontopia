# Pythontopia Agent 工作准则
可以使用子代理，且子代理并行上限是10
Pythontopia 是基于 Alethicode 技术资产抽取重构的独立新项目：一个面向高校 Python 初学者的网页版像素学习小镇。项目核心是用多智能体、学生画像、课件 RAG、游戏化任务和真实练习评估，生成个性化学习资源并规划学习路径。

## 核心原则

- 先思考，再编码。不确定且会影响架构、数据、比赛叙事或工作量时，先问用户。
- 简单优先。使用最小正确方案，不做提前抽象、兼容层或无关功能。
- 外科手术式修改。只改完成目标必须触达的文件，不做顺手重构、顺手格式化或顺手删代码。
- 目标驱动。每个任务都要有可验证结果；修 bug 先复现，做功能先定义验收。
- 教育优先，游戏承载。玩法必须服务画像、路径、资源生成、练习评估和班级教学。

## 项目硬约束

- 项目名：`Pythontopia`。
- 默认独立仓库路径：`/home/cypress/Pythontopia`。
- 唯一课程知识底座：Alethicode `packId=43`，课程名 `Python语言基础`。
- 课程种子数据必须内置到 `data/course-pack-43/`。
- 初始化后必须验证：7 份文档、561 页、7 章、65 个 KC、46 个示例、39 道题。
- 不复制《星露谷物语》官方素材，不复制未确认 license 的素材。
- 不启用 ReAct 循环，除非用户明确要求。
- 不做纯前端假数据冒充真实业务数据。

## 技术栈

### 前端

- 使用 `Vue 3`、`Vite`、`TypeScript strict`、`Pinia`、`Vitest`。
- 游戏渲染使用 `Phaser 3`。
- 新增前端代码必须使用 TypeScript。
- 禁止业务层扩散 `any`；Phaser 和第三方动态对象必须隔离在 adapter 层。
- API client 必须使用明确 DTO 类型。
- 组件文件名使用 `PascalCase.vue`，普通工具模块使用 `camelCase.ts`。
- 游戏画布之外的学习内容必须可读、可复制、可引用。

### 后端

- 使用 Java 21、Spring Boot、Maven。
- 类名使用 `PascalCase`，方法和变量使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。
- REST API 使用资源名，不使用动作式路径。
- 新增 API 必须有 DTO、校验、统一错误返回和测试。
- 数据库表名和字段名使用 `snake_case`。
- 迁移文件必须说明目的、影响表和不可逆风险。

### Python 微服务

- 使用 FastAPI + LangGraph。
- 文件名、函数名、变量名使用 `snake_case`，类名使用 `PascalCase`。
- Agent 输出必须结构化，不能只返回自然语言大段文本。
- Agent 故障必须 fail fast，不允许静默降级成普通聊天。
- 生产和演示默认单 worker，避免进程内状态错乱。

### Android 客户端（自 2026-05-22 新增）

- 使用 Kotlin 2.2.0、Jetpack Compose（Material 3）、Compose BOM 2025.04.00。
- 构建系统使用 Gradle 8.11.1 + Kotlin DSL + `gradle/libs.versions.toml` 版本目录。
- 像素游戏渲染使用 LibGDX 1.13.1（主层）+ Compose Canvas（教学面板挂件）。
- 依赖注入使用 Hilt + KSP（不用 KAPT）。
- 网络使用 Retrofit 2.11 + OkHttp 4.12 + `kotlinx-serialization-json` 1.7.3 + 协程。
- JWT 存储使用 `EncryptedSharedPreferences` + Keystore + BiometricPrompt（OWASP MASVS-L1）。
- DTO 由 OpenAPI Generator 7.22 从后端 springdoc 自动生成；少量 sealed union 走手工补丁层。
- 测试使用 JUnit 5 + MockK + Turbine + Robolectric + Compose UI Test + MockWebServer + Roborazzi + Kover。
- 静态分析使用 Android Lint + Detekt（含 ktlint 规则）。
- minSdk 26 / targetSdk 35 / compileSdk 35 / JVM Toolchain 17。
- 业务层禁 `Any`；类名 PascalCase，方法变量 camelCase，常量 UPPER_SNAKE_CASE。
- 模块拓扑：`:app + :core:{common,data,design,network} + :game + :feature:{profile,fields,workshop,arena,mine,retro,path,classroom}`。
- 不复用 Web 端 Phaser 代码 / `tilemap.ts` / `frontend/src/api/*.ts`。
- Android 端不运行 Python（Beta 首发仅服务端判题）。
- 不上架 Google Play 直到 Beta+1（演示阶段直接发 debug APK）。
- 完整决策见 `android/docs/ADR-001-tech-selection.md`。

## 目录约定

```text
/home/cypress/Pythontopia/
├── backend/                    # Spring Boot 后端（Web + Android 双端共用）
├── frontend/                   # Vue 3 + Phaser 3 网页端（冻结在 R-11，不再迭代）
├── android/                    # Kotlin + Compose + LibGDX 原生 Android 客户端（2026-05-22 新增）
├── services/
│   ├── pythontopia-graph/       # 多智能体资源生成与路径规划
│   └── pythontopia-rag/         # 课件 RAG 服务
├── data/course-pack-43/         # Python语言基础完整课程种子数据
├── assets/                      # 像素素材与 license（双端共用）
├── docs/                        # 系统设计、测试说明、比赛文档（双端共用）
├── deploy/                      # Docker Compose 与启动脚本
└── AGENTS.md
```

## Alethicode 来源模块

Pythontopia 是独立项目，但允许抽取 Alethicode 中必要能力。抽取时必须保留来源说明。

- 课程包与课件结构：`/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/languagepack`
- 课程数据库迁移：`/home/cypress/Alethicode/backend/src/main/resources/db/migration/V22__bootstrap_language_pack_core.sql` 至 `V26__language_pack_examples.sql`，以及 `V40__language_pack_course_enrichment.sql`
- RAG 与证据引用：`/home/cypress/Alethicode/services/alethicode-rag/app`
- 防幻觉审查：`/home/cypress/Alethicode/services/qa-graph/app/nodes/grounding_critic.py`、`hallucination_detector.py`、`token_grounding_verifier.py`
- 学生画像：`/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/profile`
- 学习进度：`/home/cypress/Alethicode/backend/src/main/resources/db/migration/V41__learner_course_progress.sql`
- 多智能体工作流：`/home/cypress/Alethicode/services/tutor-graph/app`
- 判题与调试：`/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/submission`
- 班级架构：`/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/classroom`

## 游戏化规则

- 画风参考星露谷式像素小镇，但不得复制官方素材。
- 可参考 `Notyourbing/StardewValley` 的玩法结构：农场、天气、日夜、任务、NPC、背包、技能树、存档。
- 教育化映射：
  - KC = 知识种子
  - 学习资源 = 浇水/施肥
  - 练习 = 除草
  - Bug = 病害/矿洞事件
  - 掌握度 = 作物成长
  - 遗忘 = 枯萎
  - 复盘 = 收获与再播种
- 不做无教育目的的装饰系统。
- Beta 版本以紧凑小镇闭环为准，不做大型开放世界。

## 个性化规则

画像至少包含 8 维：

1. 专业背景
2. 知识基础
3. 学习目标
4. 认知风格
5. 学习偏好
6. 易错模式
7. 实践能力
8. 情绪与坚持度

画像来源包括对话访谈、练习提交、判题结果、Bug 调试、资源反馈、任务完成和班级表现。不得只根据一次问卷做静态推荐。

## 多智能体规则

默认 Agent：

- `ProfileAgent`
- `CurriculumMapperAgent`
- `ResourceDesignerAgent`
- `MindMapAgent`
- `PracticeAgent`
- `CodeLabAgent`
- `StoryboardAgent`
- `PathPlannerAgent`
- `DebugAgent`
- `GroundingCriticAgent`
- `SafetyAgent`

资源生成必须保存 run id、agent name、input summary、output artifact、evidence page ids、safety result、grounding result 和 created time。

## 防幻觉与安全

- 所有生成资源必须经过证据审查。
- 不得伪造课件页码、章节、题目、作者或教材内容。
- 如果内容来自通识知识而非课件，必须明确标注。
- 不允许输出隐藏测试。
- 不允许直接给可复制提交的完整答案，除非任务类型明确允许且受教师视角限制。

## 班级架构

Pythontopia 保留轻量班级能力：

- 教师创建班级。
- 学生加入班级。
- 班级绑定 `Python语言基础`。
- 教师发布班级任务。
- 学生在公告板领取班级任务。
- 教师查看班级 KC 热力图、任务完成率、高频误区和资源使用情况。

不做完整课堂协作编码、复杂评分系统、多课程后台和实时监控大屏，除非用户明确要求。

## Python 练习与判题

- 使用双轨执行：
  - Pyodide：前端即时预览。
  - 服务端判题：正式提交、统计、班级分析、调试副本。
- 正式提交结果必须入库。
- 失败提交可触发 Bug 矿洞。
- 不允许把 Pyodide 预览结果当作正式判题结果。

## 开发命令

后端：

```bash
cd /home/cypress/Pythontopia/backend
mvn clean compile -DskipTests
mvn test
mvn spring-boot:run
```

前端：

```bash
cd /home/cypress/Pythontopia/frontend
npm ci
npm run dev
npm run typecheck
npm run lint
npm run test
```

Python 服务：

```bash
cd /home/cypress/Pythontopia/services/pythontopia-graph
python -m pip install -e ".[dev]"
python -m pytest -q

cd /home/cypress/Pythontopia/services/pythontopia-rag
python -m pip install -e ".[dev]"
python -m pytest -q
```

本地演示：

```bash
cd /home/cypress/Pythontopia/deploy
docker compose up -d
```

## 验证与交付

每次修改后按影响范围验证：

- 前端类型改动：`npm run typecheck`
- 前端组件改动：`npm run test`
- 后端 API 改动：`mvn test`
- Python Agent 改动：`python -m pytest -q`
- 数据库迁移改动：空库初始化并导入 seed
- RAG 改动：查询 `input函数`、`for循环`、`列表` 并检查引用页
- 判题改动：验证通过、失败、运行错误三类提交

交付说明必须包含：做了什么、验证了什么、哪些未验证、剩余风险、是否影响比赛演示链路。

## 禁止事项

- 不把 Alethicode 老前端技术债带进新仓库。
- 不使用 Vuex、宽松 TypeScript 或散装 API 字段。
- 不伪造课件引用。
- 不复制星露谷官方素材。
- 不启用 ReAct 循环。
- 不把班级架构删成单机游戏。
- 不为了展示效果牺牲教育目标。
