# Claude Code 未完成任务接手工作日志

> 开始时间：2026-07-28
> 当前分支：`main`
> 接手基线：`a8e51f10`

## 交付纪律

- 以瞎猜接口为耻，以认真查询为荣。
- 以模糊执行为耻，以寻求确认为荣。
- 以臆想业务为耻，以人类确认为荣。
- 以创造接口为耻，以复用现有为荣。
- 以跳过验证为耻，以主动测试为荣。
- 以破坏架构为耻，以遵循规范为荣。
- 以假装理解为耻，以诚实无知为荣。
- 以盲目修改为耻，以谨慎重构为荣。

上述要求在每个阶段开始前重新核对。

## Phase 0：接手审计与设计

### 已查询

- Claude Code 最新主会话与两个失败子代理会话；
- `main` 最近提交、Changelog、百晓工坊路由工作日志；
- 后端 Flyway 迁移、费曼 session/graph/judge、JudgeWorkerPool；
- AI Runtime summary/operations、学习证据图、TutorBench；
- 前端费曼 API/Pinia/组件和 Runtime operations 调用链；
- Claude 遗留工作树的 staged/unstaged diff。

### 权威发现

1. 百晓工坊 Phase 1–4 与 ToT/A* 已完成，不重复建设。
2. 当前真正未完成项是：
   - 后端 Flyway 版本冲突；
   - 费曼教学与真判题的十项正确性缺陷；
   - 教师 Runtime 数据主体错误；
   - TutorBench 补救率与零失败语义错误。
3. Claude 的费曼 WIP 未完成 RED/GREEN，且普通前置查询不足以保证并发计分幂等，不能直接合并。
4. 根目录 `A*.md` 为既有未跟踪文件，排除在所有提交之外。

### 基线验证

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:05 CST | 后端全量 | `mvn -s /home/cypress/.m2/settings.xml clean test` | 1042 tests，0 failures，94 errors；根因是四个重复 V186，其他为 ApplicationContext 连锁错误 |

### 用户确认

- 教师 AI Runtime 采用全平台聚合状态，不新增学生/班级选择功能。
- 批准采用“在 `main` 上按证据重新 TDD 实现”的设计。
- 最终所有本任务工作树改动必须收敛到 `main`。
- 工作树只关注并合并最近一周（2026-07-21 至今）有 tip 提交活动的分支；
  更早历史工作树不纳入本次合并，也不删除。

### Phase 0 状态

- [x] 当前仓库与 Claude Code 进度核对
- [x] 失败基线复现
- [x] 方案比较与设计批准
- [x] 设计文档落盘
- [x] 详细实施计划

## Phase 1：恢复后端基线

### 修改

- 保留 `V186__learner_profile_cognitive_style_jsonb.sql`；
- 将另外三个冲突迁移改为连续且唯一的 `V188`、`V189`、`V190`；
- JWT 会话代次注释同步指向 `V188`；
- 新增纯文件系统测试，阻断重复 Flyway 版本再次进入主分支。

### TDD 与验证

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:19 CST | RED | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FlywayMigrationVersionUniquenessTest test` | 1 test / 1 failure；准确列出 4 个 `V186` |
| 2026-07-28 12:20 CST | 增量构建诊断 | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FlywayMigrationVersionUniquenessTest,CognitiveStyleColumnMigrationTest,LoopRunServiceTest test` | 唯一性测试通过，但 `target/classes` 残留旧迁移使集成测试失败；确认必须 clean 验证迁移重命名 |
| 2026-07-28 12:21 CST | GREEN | `mvn -s /home/cypress/.m2/settings.xml clean -Dtest=FlywayMigrationVersionUniquenessTest,CognitiveStyleColumnMigrationTest,LoopRunServiceTest test` | 105 个迁移从空库执行到 `V190`；19 tests，0 failures，0 errors |

状态：完成。

## Phase 2：费曼教学链

### 修改

- Java Graph 请求补齐既有 Python `history` 合同；
- 读取本会话最近 12 条历史，在 6000 字符预算内保留最新上下文；
- 删除按解释长度伪造成功与代码的 `fallbackStep`，Graph 不可达统一 502；
- 空误解列表保持 `OPEN`，以 `noticeMd` 区分系统提示和学弟回复。

### TDD 与验证

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:23 CST | RED 1 | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FeynmanGraphClientTest test` | 编译失败：缺少 `ConversationTurn` 与四参数 `step`，证明 history 合同尚未实现 |
| 2026-07-28 12:24 CST | RED 2 | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FeynmanSessionServiceTest,FeynmanSessionServiceSecurityTest test` | 编译失败：缺少 history-aware 调用与 `noticeMd`，新行为测试有效 |
| 2026-07-28 12:25 CST | GREEN | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FeynmanGraphClientTest,FeynmanSessionServiceTest,FeynmanSessionServiceSecurityTest,FeynmanControllerSecurityTest test` | 12 tests，0 failures，0 errors |
| 2026-07-28 12:27 CST | 格式门诊断 | `mvn -s /home/cypress/.m2/settings.xml spotless:check` | Maven 镜像无法解析 Spotless 运行时依赖；按约定经 7892 代理重试仍未进入格式检查，非代码格式失败；`git diff --check` 另行通过 |

状态：完成。

## Phase 3：费曼真判题链

### 修改

- 教学文本防作弊与 AI 产出代码防泄答分离，合法完整程序不再被 `def/import` 误伤；
- Markdown 代码围栏在送入沙箱前剥离；
- 复用 `JudgeWorkerPool` 执行沙箱评测；
- 裁定扩为 `PASSED / FAILED / ERROR / NA`，保留 `passed` 兼容字段；
- 缺用例、`SYSTEM_ERROR`、worker 异常均不写失败状态或失败学习事件；
- `JUDGED_PASS` 通过条件更新原子认领，只有首个成功请求回流掌握度和成功事件；
- 失败条件更新不得覆盖并发已经写入的 `JUDGED_PASS`；
- 公开/隐藏用例失败信息不回显期望输出。

### TDD 与验证

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:31 CST | RED 1 | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FeynmanAntiCheatTest test` | 编译失败：缺少代码专用泄答检查与围栏剥离 |
| 2026-07-28 12:33 CST | RED 2 | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FeynmanAntiCheatTest,FeynmanJudgeServiceTest test` | 编译失败：缺少四态 DTO、worker 注入与新防泄答方法 |
| 2026-07-28 12:35 CST | 夹具修正 | 同上 | 17 项已通过，1 项 Mockito 重设桩时误执行旧 Answer；改用 `doThrow`，未修改生产语义 |
| 2026-07-28 12:36 CST | GREEN 1 | 同上 | 18 tests，0 failures，0 errors |
| 2026-07-28 12:36 CST | GREEN 2 | `mvn -s /home/cypress/.m2/settings.xml -Dtest='com.pythontopia.feynman.*Test' test` | 费曼后端全量 30 tests，0 failures，0 errors |

### 前端事实投影

- API DTO 接收 `noticeMd` 与四态判题 `status`，教学/判题超时分别为 35 秒/60 秒；
- Pinia 直接使用后端状态，只有真实 `PASSED/FAILED` 改写当前会话状态；
- 教学失败保留输入并提供重试，系统 notice 与学弟台词分离；
- 概念模式和已通过会话禁用真判题；
- `FAILED / ERROR / NA` 分别使用失败、警示、中性视觉语义。

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:39 CST | RED | `npx vitest run tests/components/FeynmanPanel.spec.ts tests/api/feynman.spec.ts tests/stores/feynman.spec.ts` | 15 项中 9 项预期失败，命中旧超时、布尔强转、缺重试/notice、概念和已通过仍可判题 |
| 2026-07-28 12:41 CST | GREEN | 同上（`--coverage.enabled=false`） | 15 tests，0 failures |
| 2026-07-28 12:41 CST | 类型 | `npm run typecheck` | 通过 |
| 2026-07-28 12:43 CST | 格式与静态检查 | 定向 `prettier --check`、`eslint` | Prettier 通过；ESLint 0 errors / 2 个既有 Vue 模板换行 warnings |
| 2026-07-28 12:43 CST | 格式后回归 | 同一组 Vitest | 15 tests，0 failures |

状态：完成。

## Phase 4：AI Runtime 与 TutorBench

### AI Runtime 修改

- 保持既有 `/api/ai/runtime/operations` 契约路径，按登录角色选择数据主体：
  - `STUDENT`：既有本人证据摘要；
  - `TEACHER / ADMIN`：全平台活跃学生聚合。
- 新增 `subject(scope, label, learnerCount, evidenceLearnerCount)`，不返回学习者 ID；
- 新增单条 CTE 聚合查询，统计活跃学生、证据学习者、失败练习、资源运行和触达 KC；
- 平台聚合中的学习事件仅信任 `backend / graph` 来源，拒绝学生可写的 `frontend` 观察事件污染；
- 前端卡片展示“本人学习证据”或“全平台学习证据”及证据覆盖人数。

### AI Runtime TDD 与验证

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:48 CST | 后端 RED | `mvn -s /home/cypress/.m2/settings.xml -Dtest=AiRuntimeSummaryServiceTest,AiRuntimeSummaryControllerTest,AiRuntimePlatformSnapshotRepositoryTest test` | 编译失败：缺少 `AiRuntimePlatformSnapshotRepository`，证明教师平台聚合尚未实现 |
| 2026-07-28 12:49 CST | 后端 GREEN 1 | 同上 | 空库完整执行 105 个迁移到 `V190`；10 tests 全绿 |
| 2026-07-28 12:52 CST | 后端 GREEN 2 | 同上 | 增加混合角色真实数据：禁用学生/教师排除、学习者/KC 去重均通过；11 tests 全绿 |
| 2026-07-28 12:50 CST | 前端 RED | `npm run test -- --run tests/api/aiRuntime.spec.ts tests/components/workshop/RuntimeOperationsPanel.spec.ts tests/pages/TeacherReviewPage.spec.ts tests/pages/WorkshopPage.spec.ts` | 28 项中 2 项预期失败：卡片尚未展示数据主体 |
| 2026-07-28 12:50 CST | 前端 GREEN | 同上 | 28 tests 全绿；测试日志保留既有 jsdom `127.0.0.1:3000` 探测与 Pyodide sourcemap 警告，不影响结果 |
| 2026-07-28 12:55 CST | 前端类型/静态检查 | `npm run typecheck`、定向 ESLint | `vue-tsc --noEmit` 通过；ESLint 通过 |
| 2026-07-28 13:04 CST | 安全 RED | `mvn -s /home/cypress/.m2/settings.xml -Dtest=AiRuntimePlatformSnapshotRepositoryTest test` | 伪造 `frontend practice_failed / FAKE-KC` 把预期 `2/2/2` 污染为 `3/3/3` |
| 2026-07-28 13:06 CST | 安全 GREEN | 同上 | 只采信 `backend / graph`；真实 PostgreSQL 2 tests 全绿 |

### TutorBench 修改

- 补救率分母由全部微测改为失败微测；
- 零失败时补救条件视为不适用并通过，但掌握度增量上限仍强制检查；
- 原因文案区分不适用、补救缺失和刷分风险。

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:53 CST | RED | `mvn -s /home/cypress/.m2/settings.xml -Dtest=LearningEffectTraceServiceTest,TutorBenchEvaluatorTest test` | 6 项中 2 项预期失败：旧分母返回 `0.5`；零失败被误判 |
| 2026-07-28 12:54 CST | GREEN | 同上 | 真实 PostgreSQL trace 与评测边界共 6 tests 全绿 |
| 2026-07-28 13:07 CST | 边界 RED | `mvn -s /home/cypress/.m2/settings.xml -Dtest=TutorBenchEvaluatorTest test` | 7 项中 2 项预期失败：零失败文案未明确“不适用” |
| 2026-07-28 13:08 CST | 边界 GREEN | `mvn -s /home/cypress/.m2/settings.xml -Dtest=LearningEffectTraceServiceTest,TutorBenchEvaluatorTest test` | 新增 1 失败 + 99 通过、全零新学习者、零补救三条边界；9 tests 全绿 |

### 独立审查与生产回滚防护

- 只读代码审查结论先为 `NOT READY`，发现两项 Important：学生可伪造平台 Runtime 证据；回滚旧 release 会用 HTTP-only Nginx 配置覆盖现有 HTTPS。
- Runtime 可信来源缺陷已按上表完成 RED/GREEN。
- 回滚新增配置选择器：目标 release 满足双协议契约才采用，否则保留当前合格主机配置；两者都不合格时拒绝切换。
- 回滚终验增加使用 `--resolve 47.120.29.62:443:127.0.0.1` 的真实证书校验。

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 13:04 CST | 回滚 RED | `bash deploy/scripts/test_rollback_nginx_selection.sh` | 预期失败：尚无回滚配置选择器 |
| 2026-07-28 13:05 CST | 回滚 GREEN | `bash -n ...`、`bash deploy/scripts/test_rollback_nginx_selection.sh`、`bash deploy/scripts/verify_nginx_dual_protocol.sh` | 语法、三条选择路径与双协议模板契约通过 |

### 首轮全量验证

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:58 CST | 前端测试 | `npm run test -- --run` | 242 files / 2410 tests 全绿 |
| 2026-07-28 12:59 CST | 前端类型 | `npm run typecheck` | 通过 |
| 2026-07-28 13:00 CST | 前端静态检查 | `npm run lint` | 首轮发现 1 个未使用测试 helper；删除后 0 errors / 114 既有 warnings |
| 2026-07-28 13:06 CST | Graph | `.venv/bin/python -m pytest -q` | 1680 passed / 1 skipped / 11 dependency warnings |
| 2026-07-28 13:08 CST | RAG | `.venv/bin/python -m pytest -q` | 150 passed / 2 skipped |
| 2026-07-28 13:07 CST | 后端首轮全量 | `mvn -s /home/cypress/.m2/settings.xml clean test` | 1086 tests / 0 failures / 1 error；与定向 Maven 并发共享 `backend/target`，`DecisionTraceControllerTest` 登录响应为空，结果作废 |
| 2026-07-28 13:09 CST | 后端隔离复现 | `mvn -s /home/cypress/.m2/settings.xml -Dtest=DecisionTraceControllerTest test` | 1 test 全绿，确认非业务回归；合并后仍须串行重跑全量 |

状态：代码修复完成，等待合并后最终全量复验。

## Phase 5：全链路验收与工作树收敛

### 最近一周 worktree 审计与合并

- 口径固定为 tip 提交时间在 2026-07-21 至 2026-07-28 的分支；
- 16 个 Claude 分支的 tip 均已是 `main` 祖先；
- `infra/dual-http-https` 的补丁已由 `22153d04` 等价落入 `main`，再以 `--no-ff` 合并记录分支祖先关系，未引入内容变化；
- 两个 Claude worktree 留有未提交 WIP：迁移/JWT 草稿已被当前实现覆盖，费曼/Runtime/TutorBench 旧方案也已被本轮更严格实现取代；未擅自移动或提交这些工作树内容；
- 18 个最近一周分支最终均显示 merged；未删除任何 worktree。

### 串行全量与基础设施复验

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 13:12 CST | 后端最终串行全量 | `mvn -s /home/cypress/.m2/settings.xml clean test` | 1082 tests / 0 failures / 4 Testcontainers errors；WSL 临时端口区间仅 `44620-48715`，Docker 启动 pgvector 时命中瞬时端口占用，非业务断言失败 |
| 2026-07-28 13:21 CST | 后端失败类隔离复验 | `mvn -s /home/cypress/.m2/settings.xml -Dtest=CoursePackServiceIntegrationTest,DecisionTraceControllerTest,MemoryRetrievalSemanticIntegrationTest,RecommendationServiceTest test` | 11 tests / 0 failures / 0 errors |
| 2026-07-28 13:24 CST | 本地启动 RED | `./dev.sh` | JWT 安全门拒绝 `deploy/.env` 的公开占位值，后端 fail fast；确认开发启动脚本与安全约束不一致 |
| 2026-07-28 13:25 CST | 本地启动 GREEN | `bash deploy/scripts/test_resolve_dev_jwt_secret.sh` + `./dev.sh` | 解析器测试通过；启动仅在内存生成临时高熵 JWT，前后端与 graph/rag 健康 |
| 2026-07-28 13:46 CST | 公共浏览器主流程 | `BASE_URL=http://127.0.0.1:5173 PYT_E2E_REGISTER=1 npx playwright test e2e/publicAcceptance.spec.ts --project=chromium --retries=0` | 27/27 passed（11.3 分钟），覆盖认证、主城、AI 向导 SSE/重试、工坊、RAG 页码证据、AC/WA/RE、画像、刷新与安全错误面 |

### 费曼真实闭环补充发现与修复

首轮专项使用新账号时，页面正确提示“无真实做题错误，不计入掌握度”；为验证真判题链，验收脚本改为先对代表题 `481 / PY43-KC-031` 提交真实 WA。由此复现新的产品断点：Graph 把 `WA` 误区标为 `resolved=true`，但 DeepSeek 返回 `produced_code=null`，数据库会话已是 `TAUGHT`，前端仍只能显示“等学弟写代码”。

- RED：新增 `test_resolved_llm_output_without_code_does_not_create_dead_taught_state`，旧实现错误接受 `resolved=true + produced_code=null`；
- GREEN：Graph 输出归一化拒绝该不完整组合，规则兜底保持未解决；提示合同同时强制解决误区时返回完整可运行代码；
- Graph 单文件 25 tests 全绿；Graph 全量 `1681 passed / 1 skipped / 11 dependency warnings`；
- 真实浏览器复验通过：WA 事件 `636` 被抽取为真实误区，找茬命中 1 / 误 0 / 漏 0，学弟产出偶数求和代码，沙箱真判题显示“教学成功：学弟代码通过沙箱运行，掌握度已回流”；
- 课件外“装饰器”模式显示“通识 · 非课件”，真判题按钮为禁用态并明确“不计入掌握度”。

状态：最近一周工作树已全部收敛到 `main`，本地全链路完成；等待构建发布包、ECS 部署与公网全面验收。

## Phase 6：ECS 发布与公网全面验收

- 构建并复验 allowlist 发布包；
- 上传到 `ecs-pythontopia`，核对本地/远端 SHA-256；
- 使用本轮最新事务式安装脚本切换 release，保留可回滚版本；
- 公网同时验证 HTTP / HTTPS、证书与服务健康；浏览器验收不止 27 项公共主流程，按用户追加门禁执行不少于 100 个多工作流样例；
- 当前 Playwright 清单为 8 files / 251 tests；公网主跑排除会覆盖手册静态图的截图生成套件，执行其余 7 files / 227 tests，并保留逐用例 JSON/HTML 证据；
- 再跑费曼真实 WA 全闭环，确认生产 Graph、后端、数据库与前端一致。

### HTTPS 登录故障：部署前复现与修复

公网现行版本先跑认证专项而不是直接假设 HTTPS 可用：

```text
BASE_URL=https://47.120.29.62 PYT_E2E_REGISTER=1 \
  npx playwright test e2e/publicAcceptance.spec.ts \
  --project=chromium --retries=0 -g '认证与会话'
```

结果为 2 passed / 2 failed：未登录守卫和已有本地会话路由正常；账号表单登录仍停在 `/login`，快速演示登录的 `POST /api/auth/login` 返回 403。线上主机 Nginx 已正确写入 `X-Forwarded-Proto $scheme`，但前端容器 Nginx 又用内部连接的 `$scheme=http` 覆盖它，Spring 同时未启用转发头还原，因此浏览器 `Origin: https://47.120.29.62` 被误判为跨域。

- RED：新增 `test_https_forwarded_proto_contract.sh`，前端协议保留、Spring 转发头处理、发布 HTTPS 终验共 4 项全部按预期失败；
- GREEN：前端容器用受限 `map` 保留外层 `http/https` 并在直连时回退；Spring 设置 `server.forward-headers-strategy: framework`；安装器增加证书校验后的 443 健康检查；
- 静态/局部验证：HTTPS 契约 4/4、双协议主机 Nginx 契约、`nginx -t`、Shell 语法与 `git diff --check` 全绿；
- 后端认证/安全定向 `SecurityConfigCorsTest,AuthServiceSecurityTest,JwtAuthenticationFilterTest` 全绿；首次依赖解析受 WSL 网络超时阻断，切换仓库已有的构建代理设置后成功。

部署后门禁：HTTPS 认证专项必须从 2/4 恢复为 4/4，随后才允许启动 227 项公网工作流矩阵。

状态：进行中。

### 首次生产发布与 227 项公网矩阵

- 首个发布包：`pythontopia-web-20260728T060840Z.tar.gz`，大小约 695 MiB，SHA-256
  `c7ad27ca9195a3dd247e7adb1534a8e889caed5ed066e91f40cd58bd06ec7616`；
  本地 allowlist 复验为 4088 个源码文件与 4 个 Docker 镜像，上传后远端哈希一致。
- 安装为 `/opt/pythontopia/releases/20260728-141301`，切换前版本
  `/opt/pythontopia/releases/20260704-204351`，备份位于
  `/opt/pythontopia/backups/20260728-141301`；6 个容器健康，迁移到 `V190`，
  课程包统计精确为 7 文档 / 561 页 / 7 章 / 65 KC / 46 示例 / 39 题。
- HTTPS 认证专项从旧版 2/4 恢复为新版本 4/4，确认账号表单登录、快速演示登录、
  未登录守卫和已有会话路由均已恢复。
- 首轮 HTTPS 227 项矩阵结果：221 passed / 1 flaky / 1 skipped / 4 failed。
  其中一项学生微测资源创建首次遭遇 502、Playwright 重试后通过；教师全流程未传生产教师邀请码，
  属测试环境缺项；另外三项百晓失败均显示 `routeExecutionToken=null`，形成下一轮生产配置修复输入。

### 生产路由执行密钥缺失：fail-fast 修复

只读检查确认共享 `.env` 与后端容器均缺少 `PYT_ROUTE_EXECUTION_SECRET`。预检脚本虽然已有要求，
但事务式安装器没有调用该预检，Compose 也没有把变量传入后端，因此系统处于“普通请求可用、
多 Agent 编排缺少签名令牌”的半可用状态。

- RED：新增 `test_install_release_secret_contract.sh`，安装器强度校验和 Compose fail-fast 共 2 项失败；
- GREEN：安装器在解包/切换前检查 `PYT_JWT_SECRET`、`PYT_RAG_ADMIN_TOKEN`、
  `PYT_ROUTE_EXECUTION_SECRET`，拒绝缺失、公开占位与少于 32 字符的值且永不打印秘密；
  Compose 使用 `${PYT_ROUTE_EXECUTION_SECRET:?...}` 强制注入；
- 线上先备份共享 `.env`，再生成独立 64 位十六进制随机值并只重建后端；全程不把值写入终端输出；
- 百晓专项复验由 3 项执行令牌失败恢复为 4/5 通过，剩余一项已进入真实 Agent 执行而非鉴权失败。

### `for 循环` 课程锚点竞态：权威目录修复

剩余百晓失败不是放宽 grounding 能解决的问题。数据库 trace 显示 HandoutAgent 三次都因引用
`PY43-KC-001` 的计算机历史课件而被证据审查正确拒绝；公网请求在工坊首屏加载完成前提交，
携带空 `courseKcs`，Graph 在没有课程名称的情况下只能回退到列表首个 code。

- 方案：`IntentService` 在生产路由前从 `CoursePackService` 读取 pack 43 全量 KC，
  覆盖客户端课程目录；手动 KC、最近上下文及其他路由字段保持原合同。
- RED：新增 `productionRouteReplacesEmptyClientCatalogWithAuthoritativePack43Kcs`，
  首次编译因生产构造器尚无 `CoursePackService` 参数而失败。
- GREEN：Intent Java 定向测试通过；Graph 既有
  `test_compound_route_grounds_distinctive_fragment_from_real_course_kc_name`
  通过，确认真实目录能把目标语句唯一映射为 `PY43-KC-031`。
- 公网 Playwright 用例追加 `kcCodes == ["PY43-KC-031"]`，最终 release 必须同时满足正确路由、
  两步 Agent 全成功和证据审查通过。

状态：修复已完成本地定向验证；等待第二个 release 发布后，在最终服务器状态重跑无重试的 227 项矩阵。
