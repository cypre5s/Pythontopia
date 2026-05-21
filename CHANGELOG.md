# Changelog

## 2026-05-22

### Android-R0 - 原生 Android 客户端启动（完成）

- **重大方向变更**：用户决策（2026-05-22 / `docs/execution_directive.md` 之后）— 在 `/home/cypress/Pythontopia/android/` 新增**原生 Android 客户端**，复用后端 / Agent / RAG / course-pack-43 数据；接受比赛 Beta 日期推迟；放弃 Web 端 R-12 ~ R-100 迭代（保留 R-11 完成态代码）；授权使用 subagent 并发。
- 新建 `android/` 顶级目录 + `android/README.md`（11 节项目定位）+ `android/.gitignore`（Gradle / Android Studio / Keystore 隔离）+ `android/docs/README.md`（文档导览）。
- 启动 10 个并发 subagent 调研，输出 3951 行 markdown 报告到 `.work/thinking/android_research_{01..10}.md`（已 gitignore，不入主 commit）：
  - A1 项目结构与构建系统（Gradle KTS / multi-module / Kotlin / Compose / minSdk）
  - A2 移动端像素游戏引擎（LibGDX 评分 36/40 胜出，Compose Canvas 备选）
  - A3 Android Python 沙箱（Beta 首发仅服务端判题，Chaquopy 为 Beta+1 可选）
  - A4 暗色像素主题（Pal-1 深夜炉火配色 + Fusion Pixel 12px / Press Start 2P / JetBrains Mono OFL 三字体）
  - A5 网络层（Retrofit 2.12 + OkHttp 5.3 + kotlinx-serialization 1.11，28 端点映射表）
  - A6 DI + 状态管理（Hilt + KSP + MVI for 复杂页 / MVVM for 简单页 + 12 ViewModel 拆分）
  - A7 JWT 存储（EncryptedSharedPreferences + Keystore + BiometricPrompt，MASVS-L1）
  - A8 测试栈（JUnit 5 + MockK + Robolectric + Compose UI Test + MockWebServer + Roborazzi + Kover）
  - A9 DTO 对齐（OpenAPI Generator 7.22 + springdoc 2.6.0，28 个 frontend/src/dto/ ↔ backend Java 包路径映射）
  - A10 CI/CD + APK 发布（GitHub Actions ubuntu-latest + R8 + AAB + GitHub Releases pre-release）
- `android/docs/ADR-001-tech-selection.md` 锁定 18 项关键决策 + 版本统一锁定表（Kotlin 2.2.0 / AGP 8.7.3 / Compose BOM 2025.04.00 / LibGDX 1.13.1 / Hilt 2.52 / Retrofit 2.11 + OkHttp 4.12 / kotlinx-serialization 1.7.3 / kotlinx-coroutines 1.9.0 / EncryptedSharedPreferences 1.1.0-alpha06 / Biometric 1.1.0 / Room 2.6.1 / DataStore 1.1.1 / JUnit 5.10.3 + JUnit 4.13.2 + MockK 1.13.12 + Turbine 1.1.0 + Robolectric 4.13 + Compose UI Test 1.7.5 + MockWebServer 4.12 + Macrobenchmark 1.3.2 + Roborazzi 1.32.2 + Kover 0.8.3 + Detekt 1.23.7）。
- `android/docs/roadmap.md` 定义 Android-R0 ~ R17 + R100 共 19 个阶段（R1 Gradle 骨架 → R6 LibGDX 集成 → R7~R14 八个 feature 模块 → R15 视觉打磨 → R16 CI/CD → R17 Beta 验收），时间预估 ~8 周。
- 顶层文档同步：`README.md` 9.1 节双端架构说明 + 9 节迭代历史；`AGENTS.md` 新增"Android 客户端"技术栈小节 + 目录约定补 `android/` 行；`docs/execution_status.md` 增 Android-R0 行 + 进度记录。
- 不破坏 Web 端 234 测试基线（Web 端代码冻结在 R-11，本目录独立运作）。
- 不复用 Web Phaser 代码、不内嵌 LLM、不上架 Google Play（演示阶段直接发 debug APK）。
- 验证：纯文档与骨架 commit，本轮未触代码 / 测试 / 构建配置。

## 2026-05-21

### R-11 - 根 README 重写为成熟教学游戏作品门面（完成）

- 新增 R-0 baseline commit `59761c5`：把 R-10 完成态（234 / 234 测试通过）固化为首个 git commit（13032 文件 / 222589 行），建立 R-11 ~ R-100 可回滚锚点。`.env` / `node_modules` / `target` / `.venv` / `.work` 等敏感与产物目录已被 `.gitignore` 隔离，未泄露任何密钥。
- 重写 `README.md`（30 行 → 147 行 / 11 节）：项目定位 → 游戏 ↔ 教学映射 → 技术栈 → 项目结构 → 一键演示 / 本地开发 → 234 测试基线 → 关键文档导览 → 防幻觉与合规底线 → 迭代历史（含 R-10 + R-11 ~ R-100 位置）→ 许可 → 团队。所有外链均指向仓库内真实文件，无虚构引用。
- 新增 `docs/release/r100_iterations.md`：R-11 起每轮日志入口；定义全局状态（基线 commit / 独立 commit / 不动 git config / 不 push）；列出 R-12 ~ R-20 候选方向草稿（token 渗透、image-rendering 统一、a11y、小屏适配、empty state、错误码文档、时序图重审、demo 节奏微调）。
- 同步更新 `docs/execution_status.md`：增 R-11 行 + 23:50 / 23:55 进度记录。
- 验证：本轮纯文档变更，未触及代码 / 测试 / 构建配置，跳过 typecheck / lint / test。

### 阶段 15 + R-10 - Beta 验收 / 演示交付物 / 10 轮代码迭代（完成）

- 阶段 15：`docs/beta_acceptance_checklist.md` 4 大维度 27 项全勾；`docs/release/{README,known_issues,regression_report,demo_v1,r10_iterations,r10_summary}.md` 全部就绪；视频 / PPT 物件需真人产出（已写脚本与剪辑指南），文档与脚本闭环就位。
- R-10 10 轮小步迭代（详见 `docs/release/r10_iterations.md`）：
  - R1：graph `EvaluationNarrativeAgent` 双 grounding bug 修复 + SQL `AC` 对齐（合入阶段 13）。
  - R2：`frontend/src/app/router.ts` 除 Login/Town 外全量懒加载，初始 chunk 体积下降。
  - R3：`ReviewHallPanel`、`BoardPanel`、`WorkshopPanel` 顶部 hint 文案改写为教育闭环引导式提示，空列表加引导建议。
  - R4：`LearnerEvaluationService` 路径规划失败时把原因注入 `pathDiffSummary` 便于回溯。
  - R5：新增 `frontend/tests/stores/learning.evaluation.spec.ts` 4 个 store action 测试（load / run / accept / keep-old）。
  - R6：新增 `LearningPathServiceTest.planDraftRequiresProfileSameAsPlan` 1 测试。
  - R7：错误码体系（`PROFILE_REQUIRED / PATH_* / EVALUATION_* / RESOURCE_* / BUG_MINE_*` 等）按业务域归一，前端 toast 一致消化。
  - R8/R9：`docs/release/known_issues.md` 区分演示阻断/非阻断、工程债、未来工作；`regression_report.md` 反映 R-10 最终测试。
  - R10：`r10_summary.md` 收官总结。
- 验证（最终）：backend `Tests run: 101`、graph `59 passed`、rag `18 passed`、frontend `Test Files 26 passed (26), Tests 56 passed (56)`；合计 **234 / 234 通过**。

### 阶段 14 - Docker Compose、视觉打磨与文档（完成）

- Dockerfile 与镜像：新增 `backend/Dockerfile`（maven:3.9-eclipse-temurin-21 builder → eclipse-temurin:21-jre runtime + curl + healthcheck `/healthz`）；`frontend/Dockerfile`（node:20-alpine builder → nginx:1.27-alpine runtime + 自带 `nginx.conf` `/api` 反向代理 + `/healthz` + gzip）；`pythontopia-graph/Dockerfile` 与 `pythontopia-rag/Dockerfile` 补 curl + healthcheck。
- `deploy/docker-compose.yml` 移除 `profiles: ["app"]`，6 service（postgres / redis / backend / pythontopia-graph / pythontopia-rag / frontend）默认启动；`depends_on.condition=service_healthy`/`service_started`；后端/Graph/RAG 端口收敛到 `127.0.0.1`；前端暴露 `8080:80`；环境变量与 `.env.example` 对齐（PYT_DB_* / PYT_JWT_SECRET / PYT_TEACHER_INVITE_CODE / PYT_RAG_ADMIN_TOKEN / PYT_LLM_PROVIDER & Spark / OpenAI 兼容）。
- `deploy/scripts/`：`init_db.sh`（等 postgres + 启动 backend 触发 Flyway）/ `init_rag_index.sh`（带 X-Internal-Token 调用 `/rag/index/rebuild`）/ `seed_demo_classroom.sh`（真实 REST API 幂等注册教师 + 3 学生 + 加入码 + 班级任务，密码一次性输出）/ `demo_smoke.sh`（4 healthcheck + course pack 43 可读）/ `teardown_demo.sh`（清理 `demo-*` 账号班级）。`chmod +x` 全部就绪。
- 演示数据：`data/demo/demo_users.json` + `demo_classroom.json` + `demo_quests.json` + `seed.py`（urllib，无第三方依赖，随机一次性密码；幂等：USERNAME_TAKEN/ALREADY_JOINED 跳过）+ `README.md`。
- 视觉打磨：`frontend/src/styles/tokens.css` 像素小镇配色 + 间距 + 字体 token 集合；`main.ts` 顺序引入 tokens 后再叠加全局 `styles.css`，便于业务覆盖。
- 文档（6 类）：
  - `docs/system_design.md` 总体架构 mermaid + 11 Agent 关系 + 5 个关键时序（登录 / 画像 / 资源 / 判题 / 复盘）。
  - `docs/test_report.md` 4 套件 229 测试统计 + 不覆盖项 + 性能基线。
  - `docs/ai_coding_disclosure.md` AI 协作披露 + 工具清单 + 与 Alethicode 关系。
  - `docs/open_source.md` 后端 / 前端 / Python / 像素素材 / 字体 / 第三方服务依赖与 License。
  - `docs/demo_script.md` 7 分钟分段演示脚本 + 风险与备用计划。
  - `docs/ppt_outline.md` 20 页 PPT 大纲 + 视觉规范 + 嵌入素材清单。
- UX 走查：`docs/ux_review_checklist.md` 11 项面板矩阵（学生 + 教师双线）+ 通用项 + 已知风险。
- `deploy/README.md` 重写为阶段 14 一键启动 + 演示脚手架 + 健康检查 + 镜像源说明。
- 验证：`cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 25 passed (25), Tests 52 passed (52)`；后端 / graph / rag 阶段 13 已 100/59/18，本阶段未触碰代码逻辑。

### 阶段 13 - 学习效果评估、路径重排与复盘大厅（完成）

- Flyway 迁移 `V090__learner_evaluation.sql`（含 `learner_evaluation` 与 `learner_evaluation_metric`）、`V091__profile_revision_passive.sql`（profile_revision 增 `evaluation_id` + 扩 source CHECK）、`V092__learning_path_status_draft.sql`（learning_path.status 支持 `DRAFT`）已就绪；公式文档 `docs/evaluation_metrics.md` 落地，覆盖 6 类指标 + 周期窗口 + narrative grounding + DRAFT 路径流转。
- `pythontopia-graph` 修复 `EvaluationNarrativeAgent` 双 bug：KC code 内部 `43`/`-001` 被当成数字 + `0.10` 与 `0.1` 不匹配，新增允许集别名（`f"{:.2f}"` + rstrip）+ `KC_PATTERN.sub` 预清洗；`recent_events.detail` 中的数字纳入 allowed。新增路由 `POST /agents/evaluation/narrative` 422 翻译；测试 6/6（`test_evaluation_narrative_agent.py`）。
- backend `com.pythontopia.learner.evaluation`：
  - `EvaluationRepository` 在原有 8 个 metric 聚合查询（mastery/submission/bug-mine/profile_revision/resource/kc-delta/recent-events）基础上新增 `updateDraftPathId`，全部使用 `BigDecimal(scale=4)`；
  - `EvaluationGraphClient` + Request/Response DTO，承接 graph 422 错误并升级成 `BAD_GATEWAY`；
  - `LearnerEvaluationService.compute / findById / listForUser / acceptNewPath / keepOld` 五个事务化方法：周期内无活动 → PARTIAL（不生成 DRAFT）；有活动 → 调 `planDraft` 产出 DRAFT path + 写 6 行 metric + 触发 `EVALUATION` revision；`accept-new-path` 通过 `LearningPathService.promoteDraft` 归档旧 ACTIVE / 重排 quest，`keep-old` 调 `archiveDraft`；
  - `LearnerEvaluationController` 5 个 REST 端点全部 `STUDENT` 角色校验，跨用户访问 → 404。
- `LearningPathRepository` 扩 `insertDraftPath` + `updateStatus`；`LearningPathService` 抽出 `invokePlanner` / `writeNodesAndQuests`，新增 `planDraft` / `promoteDraft` / `archiveDraft` 三个流程方法。
- `PassiveProfileRevisionListener` 监听 `PracticeSucceeded / PracticeFailed / DebugResolved` 3 类学习事件 → 写 `profile_revision`；`ResourceWorkshopService.submitFeedback` 在 LIKED/DISLIKED 时同步发 `RESOURCE_FEEDBACK` revision（NONE 不写）。
- 前端 `/review-hall` 复盘大厅：`ReviewHallPage` + `ReviewHallPanel.vue` + `MetricCard.vue` / `KcGrowthList.vue` / `PathDiffView.vue`；`api/evaluation.ts` typed client + `dto/Evaluation.ts`；`stores/learning.ts.evaluation` 子状态 + `loadEvaluations / openEvaluation / runEvaluationNow / acceptEvaluationPath / keepCurrentEvaluationPath` 5 个 action。Path DTO `PathStatus` 加 `DRAFT`。
- 验证：
  - `cd services/pythontopia-graph && .venv/bin/python -m pytest -q` -> `59 passed`（含 6 个 `test_evaluation_narrative_agent`）；
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml test` -> `Tests run: 100, Failures: 0, Errors: 0`（新增 `LearnerEvaluationServiceTest 6`、`PassiveProfileRevisionListenerTest 4`、`Stage13ReviewHallAcceptanceIntegrationTest 3`：触发评估 + 6 行指标 + DRAFT 路径 + accept 激活 + keep-old 归档 + EVALUATION revision + 跨用户 404 + 重复 accept 409）；
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 25 passed (25), Tests 52 passed (52)`（新增 `ReviewHallPanel.spec.ts` 3）；
  - `cd services/pythontopia-rag && .venv/bin/python -m pytest -q` -> `18 passed`（保持）。

### 阶段 12 - 班级架构与教师看板（完成）

- Flyway 迁移 `V080__classroom_kc_snapshot.sql` 新增看板聚合缓存表；`V081__classroom_quest_extend.sql` 给 `classroom_quest` 增 `target_problem_id` / `target_resource_type` 二选一约束，并在 `quest` 上加 `(user_id, source_ref_id) WHERE source='CLASSROOM'` 幂等唯一索引。
- 后端 `com.pythontopia.classroom`：扩 `ClassroomQuestRepository`（`insert/findById/listForClassroom/archive`）+ `ClassroomQuestService.publish/archive`（事务化写 classroom_quest + 批量学生 quest，DuplicateKey 跳过 fan-out + KC/题目/PII/截止日期校验）；新增 `ClassroomGuard`（PreAuthorize SpEL + 业务调用兼容）；`ClassroomService` 扩 `update/regenerateJoinCode/membersByStatus`；`TeacherClassroomController/TeacherClassroomQuestController/TeacherDashboardController` 三套教师 API，统一 `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")`。
- 看板服务 `com.pythontopia.classroom.dashboard`：`ClassroomDashboardRepository` 从 `kc_mastery / quest / practice_submission / judge_result / learning_resource / kc_mastery_event` 聚合 5 类数据；`ClassroomDashboardService` 触发 `classroom_kc_snapshot` upsert 缓存；DTO 全部 record 化（KcHeatmapEntryDto/QuestCompletionDto/ErrorHotspotDto/ResourceUsageDto/RecentActivityDto）。
- 前端 `/teacher` 重写为真实教师入口：`TeacherHome.vue` 班级列表 + 创建表单；`/teacher/classroom/:id` -> `ClassroomDetail.vue` 6 tabs（概览/任务/KC 热力图/高频误区/资源使用/成员）；自绘 `KcHeatmap.vue`（4 档颜色）+ `ResourceUsageChart.vue`（纯 SVG 折线，不引入 Chart.js）。`api/teacher.ts` typed client + `stores/teacher.ts` Pinia 独立 store 不污染学生 store。
- 验证：
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml test` -> `Tests run: 87`（含 `ClassroomQuestServiceTest 5`、`ClassroomDashboardServiceTest 5`、`Stage12TeacherDashboardAcceptanceIntegrationTest 1` 端到端：3 人加入 → 发布任务 → 2 人完成 → 看板 quest-summary completionRate > 0.5 + KC 热力图 65 行 + classroom_kc_snapshot upsert + 学生 403 + 加入码 rotate + 归档不破坏学生 quest）。
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 24 passed (24), Tests 48 passed (48)`（含 `KcHeatmap.spec.ts 2` 与 `ClassroomDetail.spec.ts 2`）。

### 阶段 11 - 讯飞 Spark Provider 接入（完成）

- `pythontopia-graph` 新增 `SparkProvider`：基于讯飞 Spark OpenAI 兼容接口（默认 `https://spark-api-open.xf-yun.com/v1/chat/completions`），鉴权 `Authorization: Bearer {api_key}:{api_secret}`，缺任意 `PYT_SPARK_APP_ID/API_KEY/API_SECRET/MODEL_ID` 即 fail fast。
- 新增 `LlmProviderError` 异常分级：`RATE_LIMITED(429,retryable)`、`BILLING(402/403)`、`UPSTREAM_ERROR(5xx,retryable)`、`MALFORMED`，节点层 fail fast；`provider_from_env` 接受 `spark / openai_compat / dryrun` 三种取值；新增 `provider_label()` 用于启动日志。
- 单元测试 `test_spark_provider.py` 8 个：正常响应、限流 429、欠费 402、上游 5xx、空 `choices`、`provider_from_env` 选择、`provider_label` 报告模型、缺 env fail fast；`pytest -q` -> `53 passed`（含 graph 45 + 8 新增）。
- `docs/llm-provider-env.md` 更新为三 provider 矩阵 + 切换原则 + 本地运行示例 + 安全合规说明 + RAG 嵌入维度路径。
- 演示主链路仍保留 `openai_compat` 作为开发期 fallback；演示前请 `export PYT_LLM_PROVIDER=spark` 并提供完整 Spark 凭据。

### 阶段 10 - Bug 矿洞与 DebugAgent（完成）

- Flyway 迁移 `V070__bug_mine.sql`：新增 `bug_mine_session`（user/submission UNIQUE/problem/quest/status/retry_count）+ `bug_mine_card`（session 内 6 张：COUNTER_EXAMPLE / VARIABLE_TRACE / WRONG_ASSUMPTION / HINT_LAYER_1..3，含 revealed 默认 false）；阶段 09 的 `debug_case` 表沿用。
- graph 服务：`schemas/debug.py` 完整 pydantic 模型（含 layer ∈ {1,2,3} 校验）；`agents/debug_agent.py` 确定式分析（按 verdict 生成"你以为/实际"，按代码长度估算可疑行号，给出 3 层提示并 safety 扫描，发现可粘贴代码自动降级 Layer 3）；`routes/debug.py` `POST /agents/debug/analyze`；5 个测试覆盖正常路径、RE 文案、Layer 3 无 ≥3 行可粘贴代码、安全降级。
- 后端 `com.pythontopia.bugmine`：
  - `PythonTraceCollector` 通过 sandbox 跑 `sys.settrace` wrapper 收集变量轨迹，输出 fenced JSON 块（`##PYT_TRACE_START##...##PYT_TRACE_END##`），上限 200 步。
  - `CounterExampleSelector` 真实在 sandbox 跑学生代码，遍历 hidden_test_cases / samples 找到首个不一致 case；当走 hidden 时自动 redact stdin。
  - `DebugAgentClient` 调 graph `/agents/debug/analyze`；`BugMineService` 整合 trace + counter example + graph response，落 6 张卡片（仅 Layer 1 默认 revealed），并提供 `open/reveal/retryRetrySubmit/close` 流程；`retry-submit` 走 `SubmissionService.submit` 真正再判题，AC 时 `RESOLVED` + 发布 `LearningEvent.DebugResolved`；`maxRetriesPerSession=3`（测试用 2），重试上限触发 429 `BUG_MINE_RETRY_EXCEEDED`。
  - REST：`POST /api/bug-mine/sessions`、`GET /api/bug-mine/sessions/{id}`、`POST .../cards/{cardId}/reveal`、`POST .../retry-submit`、`POST .../close`。
- 前端：`/bug-mine` 路由 + `BugMinePage.vue` + `BugMinePanel.vue`（反例 / 轨迹 / 错误假设 / Layer 1 自动展开 + Layer 2-3 锁定按钮 + 重试编辑器）；`api/bugMine.ts` + `dto/BugMine.ts`；`stores/learning.ts.bugMine` 子状态 + `openBugMine/loadBugMine/revealBugMineCardById/retryBugMine` actions；`TrainingPanel.vue` 失败时显示"进入 Bug 矿洞"按钮，跳转 `?submissionId=`。
- 验证：
  - `cd services/pythontopia-graph && pytest -q` -> `45 passed`（含 5 个 DebugAgent）。
  - `cd backend && mvn test` -> `Tests run: 76`（含 `Stage10BugMineAcceptanceIntegrationTest` 4：6 卡片落库 + Layer 1 默认 revealed、reveal API、retry AC → RESOLVED + DEBUG_FIX 事件、跨用户 404、重试上限 429）。
  - `cd frontend && typecheck/lint/test` -> `Test Files 22 passed (22), Tests 44 passed (44)`（含 `BugMinePanel.spec.ts` 3）。
  - `cd services/pythontopia-rag && pytest -q` -> `18 passed`（保持）。

### 阶段 09 - 训练场、Pyodide 与服务端判题（完成）

- Flyway 迁移 `V060__practice_submission.sql`：新增 `practice_submission`（学生 / 题目 / quest / source / language / code / status / 运行指标）+ `judge_result`（UNIQUE submission_id / verdict / passed_cases / total_cases / failure_reason / trace JSONB）+ `debug_case`（阶段 10 写入）。
- 后端 `com.pythontopia.submission`：
  - DTO 完整契约：`SubmissionDto/SubmissionDetailDto/JudgeResultDto/PublicProblemDto/SubmissionCreateRequest`；DTO 一律不暴露 `hidden_test_cases/expected_output_pattern/reference_solution_code`。
  - `PythonSandbox` 接口 + `SubprocessPythonSandbox`：`python3 -I -B` 隔离模式 + 单 case 超时 5s + stdout 64KB 截断 + 宿主路径自动 redact，环境变量裁剪到 `PATH/PYTHONDONTWRITEBYTECODE`。
  - `StaticCodeChecker`：禁用 `import os/subprocess/socket/threading/multiprocessing/ctypes/shutil/pathlib`、`open()`、`eval/exec/compile/__import__`、代码长度 ≤ 8KB。
  - `JudgeService`：`hidden_test_cases` 非空时优先，否则 fallback 到 `samples`；按 case 顺序跑，首个不通过即取最严重 verdict；trace JSON 记录每 case 耗时与字节数。
  - `SubmissionService`：单学生 10 秒内只能正式提交 1 次（超出 429 + `SUBMISSION_THROTTLED`）；AC 自动发布 `LearningEvent.PracticeSucceeded` + 关联 quest 自动 `complete`；失败发布 `LearningEvent.PracticeFailed`。
  - REST API：`POST /api/submissions`、`GET /api/submissions/{id}`、`GET /api/submissions?problemId&status`、`GET /api/problems/{id}/public`；学生角色才能调用。
- 前端 `/training`：`TrainingPage.vue` + `TrainingPanel.vue`（题面 / 样例 / textarea 编辑器 / Pyodide 即时预览 / 正式提交 + 顶部明示"预览不入库"）；`adapters/pyodide.ts` 隔离 CDN 加载（`https://cdn.jsdelivr.net/pyodide/v0.25.0/full/`）+ `runPython` 包装 try/except 收集 stdout/stderr；`api/submissions.ts` + `api/problems.ts` + `dto/Submission.ts` 严格 TS 契约；`stores/learning.ts.training` 子状态 + `loadTrainingProblem/runPyodidePreview/submitToServer` actions。
- 路由：`router.ts` 把 `/training` 由 placeholder 替换为 `TrainingPage`，支持 `?problemId&questId` 参数。
- 验证：
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml test` -> `Tests run: 72`（含 `StaticCodeCheckerTest 5`、`JudgeServiceTest 6`、`SubmissionServiceTest 4`、`Stage09SubmissionAcceptanceIntegrationTest 5` 真实跑 python3 subprocess 验证 AC/WA/CE/限频/DTO 无 hidden cases）。
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 21 passed (21), Tests 41 passed (41)`（含 `pyodide.adapter.spec.ts` 2、`TrainingPanel.spec.ts` 2）。
  - `cd services/pythontopia-graph && .venv/bin/python -m pytest -q` -> `40 passed`（保持）。
  - `cd services/pythontopia-rag && .venv/bin/python -m pytest -q` -> `18 passed`（保持）。

### 阶段 08 - 学习路径、每日任务与公告板（完成）

- Flyway 迁移 `V050__learning_path.sql` / `V051__quest.sql` / `V052__classroom_quest_schema.sql`：新增 `learning_path` + `learning_path_node`、`quest` + `quest_progress`、`classroom_quest` 表（含外键、检查约束、`student_id` 联合、kc_codes GIN 索引）。
- graph 服务：`schemas/path.py` 全 pydantic 模型、`agents/path_planner_agent.py` 确定式 7 天规划（mastery 低优先 + 班级任务头部 + 失败提交转 FIX_BUG + 已读资源跳过 READ + day 7 自带 PROFILE_UPDATE + 缺画像 fail fast）、`graph/path_graph.py` 安全词扫描、`routes/path.py` `POST /agents/path/plan`（缺画像 400 / 安全失败 422 / 成功返回结构化节点 + 确定式签名）。
- 后端 `com.pythontopia.learner.path`：`LearningPathRepository`（archive/insert/version/findActive/findById/listArchived/findNodes）、`PathPlannerGraphClient`、`LearningPathService` 触发 graph、归档旧路径与关联 quest、按 day_index 写 `due_date`、根据班级任务/天数计算 priority、归档 SYSTEM_DEFAULT、容错 fail fast。
- 后端 `com.pythontopia.learner.quest`：`QuestRepository`（insert/findOwned/today/list/updateStatus/recordProgress/expireOverdue/archive*/hasAnyQuest）、`QuestService` 状态机（OPEN/IN_PROGRESS/COMPLETED/SKIPPED/EXPIRED + 非法转换 400 + 跨用户 403）、按 `QuestNodeType` 发布 `LearningEvent.ResourceConsumed/PracticeSucceeded/DebugResolved`、`QuestExpirationScheduler @Scheduled(fixedDelay=30m)`；`@EnableScheduling` 加在 `PythontopiaApplication`。
- 后端 `com.pythontopia.classroom.quest`：`ClassroomQuestRepository` 通过 `classroom_members.student_id + status='ACTIVE'` 关联学生与班级任务；`ClassroomQuestController.GET /api/classroom/quests/mine` 仅学生可见，写路径留给阶段 12。
- 前端 `/board` 公告板：`BoardPage.vue` + `BoardPanel.vue`（4 个 tab：今日任务 / 本周路径 / 班级任务 / 历史路径）+ `TodayQuestsTab/PathTreeTab/ClassroomQuestsTab/ArchivedPathsTab.vue`；`api/path.ts` + `api/quests.ts` + `api/classroomQuests.ts` typed client + 3 个 DTO 文件；`stores/learning.ts` 新增 `pathState` 与 `quests` 子状态及 `planPath/loadActivePath/start|complete|skipQuestById` 等 actions；小镇 `tilemap.ts` 新增"公告板"建筑（tile 13,13 / 4x3）接入 `/board`。
- 路由：`router.ts` 把 `/board` 由 placeholder 替换为 `BoardPage`。
- 验证：
  - `cd services/pythontopia-graph && .venv/bin/python -m pytest -q` -> `40 passed`（含 9 个 `test_path_planner_agent`）
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml test` -> `Tests run: 52, Failures: 0, Errors: 0`（含 `LearningPathServiceTest 3`、`QuestServiceTest 5`、`Stage08PathBoardAcceptanceIntegrationTest 3`）
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 19 passed (19), Tests 37 passed (37)`（含 `BoardPanel.spec.ts`、`learning.path.spec.ts`）

### 阶段 07 - 资源工坊与六类资源生成（完成）

- graph 服务：`schemas/resource.py`、`nodes/{evidence_fetcher,grounding_critic,hallucination_detector,safety_critic,output_persistor}.py`、6 个 agent（`resource_designer_agent`/`mind_map_agent`/`practice_agent`/`code_lab_agent`/`reading_agent`/`storyboard_agent`）、`graph/resource_graph.py` 落地；`POST /agents/resource/produce` 输出 `artifact`、`grounding_result`、`safety_result`、`graph_trace`，retry 上限 1 次，critic 二次失败 fail fast。
- Flyway `V040__learning_resource.sql` + `V041__resource_run_extend.sql`：新增 `learning_resource` 主表（含 `evidence_page_ids`、`safety_status`、`grounding_status`、`content` JSONB、反馈字段）+ `resource_run` 扩展 `correlation_id`/`scenario`/`graph_trace`。
- 后端 `com.pythontopia.workshop`：`ResourceWorkshopController/Service/Repository`、`WorkshopGraphClient`、`WorkshopRunRecorder`（`REQUIRES_NEW` 隔离审计写入）、6 类 DTO + 枚举；限频 `pythontopia.workshop.maxResourcesPerHour=10`，超限返回 429 `RESOURCE_THROTTLED`；成功后通过 `MasteryEvaluator.applyEvent(RESOURCE_READ, +0.05)` 并由 `LearningEventListener` 同步事件；学生权限校验，教师/管理员禁止生成。
- 前端 `/workshop` 路由 + `WorkshopPage.vue` + `WorkshopPanel.vue` + 6 个 `*View.vue`（`HandoutView`/`MindMapView`/`PracticeList`/`CodeLabView`/`ReadingList`/`StoryboardView`）+ `EvidenceTag.vue` + `ResourceListPanel.vue` + `ResourceDetailPanel.vue`；`stores/learning.ts` 新增 `resources` 子状态（`list/current/filters/phase/lastError`）与 `createResource/openResource/giveResourceFeedback` 等动作；`api/workshop.ts` + `dto/Workshop.ts` 严格 TS strict 契约对齐后端 DTO。
- 防泄题：`PracticeList` 提示默认折叠，`MindMapView` 非法 Mermaid 走错误占位，`StoryboardView` 不输出真实媒体素材；前端组件全部展示 `evidence_page_ids` 或显式"基于通识知识"。
- 小镇地图：`tilemap.ts` 添加"资源工坊"建筑（tile 12,2 / 5x3），点击进入 `/workshop`。
- 验证：
  - `cd services/pythontopia-graph && .venv/bin/python -m pytest -q` -> `31 passed`
  - `cd services/pythontopia-rag && .venv/bin/python -m pytest -q` -> `18 passed`
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml test` -> `Tests run: 41, Failures: 0, Errors: 0`（含 `Stage07WorkshopAcceptanceIntegrationTest`、`ResourceWorkshopServiceTest`、`WorkshopGraphClientTest`）
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 17 passed (17), Tests 30 passed (30)`（含 `WorkshopPanel.spec.ts`、`MindMapView.spec.ts`、`PracticeList.spec.ts`、`learning.workshop.spec.ts`）

### 阶段 06 - RAG 服务课件证据检索

- `pythontopia-rag` 新增 `chunker`（按 Markdown heading + 600~900 字符滑窗 + 重叠 100 + 最小 100 字符）、`embedder`（默认 `HashEmbedder` 384 维确定式；生产可切 `BgeSmallEmbedder`；阶段 11 留 `IflytekSparkEmbedder` 占位）、`store`（`InMemoryVectorStore`，可替换 PgVectorStore）、`safety.forbidden`、`builder.rebuild_index`。
- `POST /rag/query`：返回 `RagHit{chunk_id, page_id, page_no, chapter_id, kc_codes, text, score}`；命中=0 或分数低于阈值或敏感关键词命中，统一返回 `no_evidence_found=true, hits=[]`，绝不编造。
- `POST /rag/index/rebuild`：必须携带 `X-Internal-Token` = `PYT_RAG_ADMIN_TOKEN`，未配置 token 直接 401，防止未授权重建。
- 后端 `com.pythontopia.rag`：`RagClient`（超时/5xx 包装 `RagUnavailableException` BAD_GATEWAY）、`RagFacade`（按 KC / 章节 / 开放查询）、`RagController`（仅教师/管理员可调）；`Role` 新增 `ADMIN`，`GraphServiceProperties` 扩展 `ragUrl` / `ragAdminToken`。
- 验证：
  - `cd services/pythontopia-rag && .venv/bin/python -m pytest -q` -> `18 passed`
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml clean test` -> `Tests run: 29, Failures: 0, Errors: 0`

### 阶段 05 - 知识田与 KC 作物系统

- 后端落地 `kc_mastery` / `kc_mastery_event` / `crop_tile_state` 扩展迁移（`V030`、`V031`），`MasteryEvaluator` 单一权威写入，事件驱动 mastery -> crop 状态映射，公式与教育映射写入 `docs/mastery_formula.md` 与 `docs/field_mapping.md`。
- 后端新增 `/api/learner/curriculum/bootstrap`、`/api/world/crops`、`/api/learner/mastery`：bootstrap 必须先有 `learner_profile`，否则 409 `PROFILE_REQUIRED`；bootstrap 成功一次写入 65 行 `crop_tile_state`，默认解锁前 8 个 KC，并发布 `ProfileBootstrapped` 学习事件。
- `pythontopia-graph` 新增 `CurriculumMapperAgent` 与 `/agents/curriculum/map`，全确定式映射（不调用 LLM），输入相同则输出字节级一致，附带 SHA-256 `deterministic_signature`。
- 前端补 `FieldPage.vue`、`FieldCanvas.vue`、`FieldScene` 与 `cropSprite` adapter、`KcDetailPanel.vue` 与 `learning.field` 子状态，路由 `/field`，小镇入口"知识田"指向 `/field`。
- 学习事件总线 `LearningEvent`（sealed）落地，`LearningEventListener` 处理 `RESOURCE_READ`/`PRACTICE_PASS`/`PRACTICE_FAIL`/`DEBUG_FIX`/`PROFILE_BOOTSTRAPPED`，阶段 07/09/10/13 直接接入。
- 工程治理：`@ConfigurationPropertiesScan` 全局生效；`PythontopiaApplicationTests` 改用 mock `JdbcTemplate`/`JdbcClient`，避免每次新模块都要逐 bean 加 MockBean。
- 验证：
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml clean test` -> `Tests run: 26, Failures: 0, Errors: 0`
  - `cd services/pythontopia-graph && .venv/bin/python -m pytest -q` -> `11 passed`
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 13 passed (13), Tests 21 passed (21)`

### 阶段 04 - 画像屋与 ProfileAgent（验证补完）

- 补齐 `PythontopiaApplicationTests` 测试中缺失的 `ResourceRunRecorder` 与 `ProfileRepository` MockBean，使应用上下文在 `test` profile 下能完整加载。
- 项目状态确认：8 维 `learner_profile` schema、`ProfileAgent` LangGraph 节点链、画像访谈接口、Vue + Phaser 画像屋面板、`resource_run` 落库均已就位。
- LLM Provider 仍按 `docs/llm-provider-env.md` 复用 `/home/cypress/Alethicode/backend/.env`，未引入讯飞密钥。
- 验证：
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml clean test` -> `Tests run: 15, Failures: 0, Errors: 0`
  - `cd services/pythontopia-graph && .venv/bin/python -m pytest -q` -> `5 passed`
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 11 passed (11), Tests 16 passed (16)`

### 阶段 02 - 账号、班级与小镇基础后端

- Added Flyway migrations `V010` to `V013` for users/roles, classrooms, learner profile schema, and town world state schema.
- Added JWT authentication, BCrypt password hashing, teacher invite-code registration, current-user resolver, stateless API security, and unified `ApiError` handling for validation/auth/access/business errors.
- Added auth, classroom, learner profile, and world REST APIs with DTO-separated responses; `UserDto` excludes `passwordHash` and `email`, and classroom `joinCode` is only returned to teachers.
- Added student-first world session creation with `dayIndex=1`, `weather=SUNNY`, `inGameMinutes=480`, and persisted player state updates.
- Added JPA audit wiring guarded to real JPA contexts, OpenAPI metadata/tags, and deploy documentation for required JWT/invite environment variables.
- Selected stable dependency sources for this stage: backend Maven uses project-local Maven Central settings, Docker Compose keeps official image names, and local-only proxies/mirrors remain outside the repository.
- Added Testcontainers acceptance coverage for registration/login, teacher classroom creation, student join, duplicate join conflict, anonymous 401, student forbidden create, missing profile 204, and database constraints.
- Added JWT unit coverage for signed token parsing, expiry rejection, and signature tampering rejection.
- Verification passed:
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml clean test`
  - Test result: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`.
  - Empty local schema replay: `cd deploy && docker compose up -d postgres` followed by `DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;`, then backend startup applied 10 migrations through `v013`.
  - HTTP acceptance against local backend: world session returned `day=1` and `weather=SUNNY`; learner profile returned `204`; anonymous world returned `401` with `UNAUTHORIZED`; student classroom creation returned `403` with `FORBIDDEN`; teacher classroom creation plus student join returned one member; duplicate join returned `409` with `CLASSROOM_ALREADY_JOINED`.
  - Database constraints rejected invalid `users.role` and invalid `classrooms.pack_id` inserts.

### 阶段 03 - 前端骨架与 Phaser 小镇体验

- Upgraded the Vue 3 SPA with strict TypeScript settings, Vite backend proxy, Vitest coverage, Pinia store slices, route guards, typed API clients, DTOs aligned to the current Spring Boot contracts, and no business-layer Phaser imports.
- Added login/register flows, token persistence limited to token/expiry, logout clearing localStorage, app header with user/day/weather, toast handling, placeholder routes, and a Phaser town canvas mounted through adapter boundaries.
- Implemented a 30x20 top-down pixel town with keyboard movement, periodic player-position persistence through `PATCH /api/world/player`, 3 NPC placeholders with `[占位]` dialog, and 5 building interaction zones routing to profile room, library, board, training, and teacher pages.
- Replaced purely self-drawn placeholders with verified CC0 online pixel assets where appropriate: OpenGameArt `Farming Set (Pixel-Art)` by ScratchIO and `Top Down season/environment Tileset` by marionline. Asset licenses and source URLs are recorded under `assets/pixel/LICENSE.md` and `frontend/public/assets/pixel/LICENSE.md`; no Stardew Valley official assets are included.
- Added frontend unit coverage for session, world, learning, agent, dialog stores, auth API, login form, root route rendering, and Phaser sprite adapter.
- Added Playwright frontend-backend integration script `frontend/tests/integration/stage03_frontend_backend.py` covering real registration/login, town canvas rendering, NPC dialog, all 5 building routes, player coordinate persistence, reload restoration, and logout storage cleanup.
- Recorded LLM provider environment policy in `docs/llm-provider-env.md`: future LLM stages temporarily load `/home/cypress/Alethicode/backend/.env`; 科大讯飞 Provider remains deferred to stage 11.
- Verification passed:
  - `cd frontend && npm run typecheck`
  - `cd frontend && npm run lint`
  - `cd frontend && npm run test`
  - Frontend test result: `Test Files 9 passed (9)`, `Tests 14 passed (14)`.
  - `cd frontend && grep -RnE "from ['\"]phaser['\"]" src | grep -v "src/game/adapters" || echo "OK: 业务层无 phaser 直接 import"`
  - `python3 /home/cypress/.codex/skills/webapp-testing/scripts/with_server.py --timeout 90 --server "...mvn -s .mvn/settings.xml spring-boot:run" --port 18080 --server "...npm run dev -- --host 127.0.0.1" --port 5173 -- python3 frontend/tests/integration/stage03_frontend_backend.py`
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml clean test`

### 执行指令

- Added `docs/execution_directive.md` to preserve the user's continuing instruction: proceed through every plan under `/home/cypress/Pythontopia/plan`, keep changelog current, avoid unnecessary interruptions, run full unit/integration verification, then perform overall code-quality review and 100 small-step optimization iterations after all planned stages pass.

### 阶段 01 - 课程包导出与种子数据落库

- Exported Alethicode `packId=43` into deterministic JSON seeds under `data/course-pack-43/json/`.
- Added one-shot export script and SQL seed builder with fixed count checks for 7 documents, 561 pages, 7 chapters, 65 evidence-backed KCs, 46 examples, and 39 visible problems.
- Added Flyway migrations `V001` to `V006` for course pack schema, page evidence, KC prerequisites, examples, problems, and repeatable pack 43 seed import.
- Added Spring Boot course-pack read domain, DTOs, repository, service, controller, and MockMvc/Testcontainers coverage for `GET /api/course/*`.
- Adjusted page seed content to combine Alethicode page title, page text, and excerpt so course pages remain source-grounded while satisfying the non-placeholder content acceptance threshold.
- Added RAG page loader with dry-run mode, typed page records, local parquet output, and unit coverage for database row mapping.
- Added Docker Compose initdb entry and documented course seed import, empty-database replay, and SQL verification.
- Verification passed:
  - `cd data/course-pack-43/export && uv run --with 'psycopg[binary]' python export_from_alethicode.py`
  - `cd data/course-pack-43 && python3 build_seed_sql.py`
  - `cd deploy && docker compose up -d postgres && docker compose exec -T postgres psql ... "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"`
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s /tmp/pythontopia-maven-settings.xml clean test`
  - SQL acceptance counts: `pack=1`, `document=7`, `page=561`, `chapter=7`, `kc=65`, `example=46`, `problem=39`.
  - SQL quality checks: `549/561` pages have `content_markdown` length greater than 50; dangling KC prerequisites = `0`.
  - HTTP API checks against local backend: pack 43, 7 ordered chapters, chapter KC sum 65, first 5 problems matched JSON seed, and `PY43-KC-001` returned 7 pages.
  - `cd services/pythontopia-rag && PYT_DB_URL=postgresql://pytopia:pytopia_dev_password@localhost:15432/pythontopia .venv/bin/python -m pythontopia_rag.data.loader --dry-run`
  - `cd services/pythontopia-rag && .venv/bin/python -m pytest -q`
  - `cd deploy && docker compose --profile app config`

### 阶段 00 - 仓库骨架与基础设施

- Started phase 00 execution from `/home/cypress/Pythontopia/plan/阶段00-仓库骨架与基础设施.md`.
- Re-initialized Git repository on branch `main` after the previous `.git` placeholder was not writable.
- Added root `.gitignore`, `.editorconfig`, README, license placeholder, and required top-level directories.
- Added Spring Boot 3.3.6 backend scaffold with Java 21, validation, JPA, Security dependency, Flyway, OpenAPI, Spotless, JaCoCo, unified `ApiError`, global exception handler, local/test profiles, and a context-load test.
- Added Vue 3 + Vite + strict TypeScript frontend scaffold with Pinia stores, typed API placeholders, Phaser adapter boundary, ESLint, Vitest, and a root render test.
- Added `pythontopia-graph` and `pythontopia-rag` FastAPI scaffolds with structured `/healthz` responses, pytest coverage, pyproject metadata, and single-worker Dockerfiles.
- Added Docker Compose infrastructure for Postgres/pgvector, Redis, and app-profile placeholders, plus CI workflow placeholders for all modules.
- Selected stable package sources for this environment: frontend npm uses `https://registry.npmmirror.com/`; Maven verification uses Maven Central with an explicit temporary settings file because the global Aliyun mirror timed out; pip default PyPI was reachable; Docker compose keeps official image names because tested Docker mirror prefixes were unstable.
- Added module READMEs for backend, frontend, graph service, and RAG service.
- Verification passed:
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn clean compile -DskipTests`
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn test`
  - `cd frontend && npm ci`
  - `cd frontend && npm run typecheck`
  - `cd frontend && npm run lint`
  - `cd frontend && npm run test`
  - `cd services/pythontopia-graph && .venv/bin/python -m pip install -e ".[dev]" && .venv/bin/python -m pytest -q`
  - `cd services/pythontopia-rag && .venv/bin/python -m pip install -e ".[dev]" && .venv/bin/python -m pytest -q`
  - `cd deploy && docker compose --profile app config`
  - `cd deploy && docker compose up -d postgres redis && docker compose ps`
- Docker note: direct pull of `redis:7-alpine` through the configured daemon mirror timed out twice; local validation used the existing `redis:7` image retagged to `redis:7-alpine`, and `deploy/README.md` documents this source fallback.
