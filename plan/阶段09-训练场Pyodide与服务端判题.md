# 阶段 09 — 训练场、Pyodide 与服务端判题

## 1. 时间窗口与定位

- 建议日期：2026-06-04 ~ 2026-06-10（与阶段 07、08 部分并行，3~4 天主线 + 联调）。
- 角色：1 名后端（判题服务） + 1 名前端（Pyodide 集成） + 1 名 DevOps（沙箱安全）。
- 定位：Pythontopia 的"练习闭环"。Pyodide 仅作"前端即时预览"，**所有正式提交、统计、班级分析、Bug 矿洞触发**必须使用服务端判题。两条轨道严格区分，禁止把 Pyodide 结果当正式判题结果。

## 2. 本阶段目标

- 前端"训练场"场景与 `TrainingPanel.vue`：
  - 选题：从 `learning_resource` 中的 `PRACTICE` 类资源或 `course_problem` 直接拉。
  - 编辑器：CodeMirror 6 + Python 语法高亮。
  - 即时预览：调用 Pyodide adapter（隔离在 `frontend/src/game/adapters/pyodide.ts`），运行学生代码并显示输出/异常。
  - 正式提交：调用后端 `POST /api/submissions`，显示判题结果。
- 后端"判题服务"：
  - 接收提交，调度沙箱（Docker 或受限子进程），用题目隐藏测试评判通过/失败。
  - 把结果写入 `practice_submission`、`judge_result`，同步触发 `MasteryEvaluator` 与 `quest` 状态变更。
  - 失败提交携带 `failure_reason`、`trace`，可供阶段 10 Bug 矿洞读取。
- 严格区分两条轨道：
  - Pyodide 预览结果**不写库**、**不触发 mastery**、**不影响 quest**。
  - 服务端判题是唯一权威。

## 3. 上下游关系

- 上游：
  - 阶段 01 题库 `course_problem`（含 `hidden_test_cases`）。
  - 阶段 05 mastery 接口。
  - 阶段 07 练习题资源（关联到 `course_problem.id` 或临时题）。
  - 阶段 08 quest 状态机。
- 下游：
  - 阶段 10 Bug 矿洞读取最近失败提交。
  - 阶段 12 班级看板聚合班级提交统计。
  - 阶段 13 复盘大厅展示提交时间序列。

## 4. Alethicode 借用来源

- 判题与执行：
  - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/submission/`
  - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/execution/PythonExecutionTraceService.java`
  - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/visualize/VisualizeCapabilityService.java`
- Pyodide 参考：
  - `/home/cypress/Alethicode/game/src/engine/PythonRunner.js`（仅参考调用模式，不复制 JS 源码）。

**抽取约束**：

- Alethicode 老 OJ 中与多语言、复杂赛制、SPJ 相关的逻辑全部剔除；Pythontopia 只判 Python 3.11。
- `PythonExecutionTraceService` 仅参考执行轨迹结构思路，Pythontopia 重写为 Pythontopia 自己的服务，并显式标注来源。

## 5. 任务清单

### 5.1 schema

- T09-01 Flyway 迁移 `V060__practice_submission.sql`：
  - `practice_submission`：`id`, `user_id`, `problem_id`（FK to `course_problem.id`），`quest_id NULL`, `source`（`TRAINING`/`PATH_QUEST`/`BUG_MINE_RETRY`），`language` 默认 `PYTHON_3_11`, `code TEXT`, `submitted_at`, `status`（`PENDING`/`RUNNING`/`PASSED`/`FAILED`/`ERROR`），`runtime_ms INT NULL`, `memory_kb INT NULL`。
  - `judge_result`：`id`, `submission_id` UNIQUE, `verdict`（`AC`/`WA`/`RE`/`TLE`/`CE`/`MLE`/`SYSTEM_ERROR`），`passed_cases INT`, `total_cases INT`, `failure_reason TEXT NULL`, `trace JSONB NULL`, `created_at`。
  - `debug_case`：`id`, `submission_id`, `kind`（`COUNTER_EXAMPLE`/`VARIABLE_TRACE`/`WRONG_ASSUMPTION`/`HINT_LAYER`），`payload` JSONB, `created_at`。本阶段建表，写入由阶段 10 完成。
- T09-02 在 `course_problem` 上新增校验：`hidden_test_cases` JSON 结构必须 `{cases: [{stdin?, args?, expected_stdout, timeout_ms?}], ...}`。如阶段 01 已校验则跳过。

### 5.2 服务端判题核心

- T09-10 `com.pythontopia.submission`：
  - `controller/SubmissionController`：
    - `POST /api/submissions`：DTO `SubmissionCreateRequest{problemId, code, questId?}`，返回 `SubmissionDto`（`status=PENDING`）。
    - `GET /api/submissions/{id}`：返回 `SubmissionDto` + `JudgeResultDto`。
    - `GET /api/submissions?problemId=&status=`：当前学生历史。
  - `service/SubmissionService`：
    - 写 `practice_submission`，发布事件给判题 worker。
    - 限频：单学生每 10 秒最多 1 次正式提交。
- T09-11 `com.pythontopia.judge`：
  - `JudgeWorker`：使用 Spring `@Async` + 受控线程池（默认 4）；本阶段不引入消息队列，简单异步即可。
  - `PythonSandbox`：
    - 默认使用 Docker `python:3.11-slim`，`--network=none`、`--read-only`、`--memory=256m`、`--cpus=1.0`、`--ulimit nproc=64`。
    - 学生代码写入临时目录；测试用例 stdin 通过 stdin pipe；输出捕获 stdout/stderr；单测超时默认 5s。
    - 沙箱执行结果不可包含宿主机敏感信息；日志 redact 文件路径。
  - 若 Docker 不可用，提供 `SubprocessSandbox` 兜底（仅在 `dev` profile 启用，明确警告）。
- T09-12 判题流程：
  1. 拉 `course_problem.hidden_test_cases`。
  2. 顺序跑每个 case，超时/异常分别记录 verdict。
  3. 所有通过 → `AC`；任一不通过 → 取最先失败 verdict（`WA`/`RE`/`TLE` 等）。
  4. 写 `judge_result`、更新 `practice_submission.status`。
- T09-13 判题完成回调：
  - `AC` → `MasteryEvaluator.applyEvent(userId, kcCode, PRACTICE_PASS, +0.10)`；若关联 quest，则 `QuestService.complete`。
  - 非 `AC` → `MasteryEvaluator.applyEvent(userId, kcCode, PRACTICE_FAIL, -0.02)`；触发"是否进入 Bug 矿洞？"（阶段 10 决定）。
- T09-14 `dto`：`SubmissionDto`（不含 `hidden_test_cases`）、`JudgeResultDto`（不暴露完整 hidden cases，只汇总通过数）。

### 5.3 题目查询

- T09-20 复用阶段 01 的 `CoursePackController.getProblem`，新增：
  - `GET /api/problems/{id}/public`：返回学生可见字段（不含 hidden cases）。
- T09-21 `learning_resource.type=PRACTICE` 的内容里 `problem_id` 字段必须指向 `course_problem.id`（在阶段 07 PracticeAgent 输出 schema 中加入）；否则视为"自定义题"，由 PracticeAgent 给出 sample IO，但**不会触发服务端判题**（仅 Pyodide 预览）。

### 5.4 Pyodide 即时预览（前端 adapter）

- T09-30 `frontend/src/game/adapters/pyodide.ts`：
  - 暴露 `class PyodideRunner { async init(): Promise<void>; async run(code: string, stdin?: string): Promise<RunResult>; dispose(): void; }`。
  - `RunResult`：`{ stdout: string; stderr: string; durationMs: number; isError: boolean; }`。
  - 使用官方 `pyodide` 包，CDN 或本地化加载；首次加载缓存到 `IndexedDB`。
- T09-31 业务层不直接 import `pyodide`；通过 `useTrainingStore` 调用 adapter。
- T09-32 Pyodide 运行时长 > 10 秒自动 `kill`（Web Worker 模式更安全；可在 Beta 中先单线程，后续阶段改 worker）。

### 5.5 前端训练场

- T09-40 `frontend/src/game/scenes/TrainingScene.ts`：进入小镇训练场建筑后打开 `TrainingPanel.vue`。
- T09-41 `frontend/src/components/training/TrainingPanel.vue`：
  - 左侧：题目描述、KC 标签、难度、`sample_io`，**禁止显示** hidden cases。
  - 中部：CodeMirror 6 编辑器，Python 模式；显示 "即时预览" 与 "正式提交" 两个按钮。
  - 右侧：即时预览输出（绿色 stdout / 红色 stderr）；正式提交结果（轮询 `GET /api/submissions/{id}`，2 秒一次，最长 60 秒）。
  - 顶部明确显示"即时预览结果不入库；正式提交才会记录"。
- T09-42 `frontend/src/stores/learning.ts` 新增 `training` 子状态：`currentProblem`, `previewResult`, `submissions`, `activeSubmission`。
- T09-43 `frontend/src/api/submissions.ts`、`problems.ts` typed client。
- T09-44 与 quest 联动：从 BoardPanel 跳过来时携带 `?questId=`；正式提交时回传，AC 后自动跳回任务面板。

### 5.6 安全与执行

- T09-50 后端在调用沙箱前对 `code` 做静态检查：
  - 长度 ≤ 8KB。
  - `import os`、`import sys`、`import subprocess`、`open()`、`__import__` 等敏感符号在沙箱环境中已被 seccomp/`--read-only` 抑制；额外做白名单 import 检查，命中黑名单时直接 `CE` 并附 `failure_reason`。
- T09-51 沙箱日志 redact：删除 `/home/`、`/root/` 等绝对路径；保留行号与异常类型。
- T09-52 资源限制：每提交最大 CPU 30s 总和；超过 → `TLE`。

### 5.7 测试

- T09-60 后端：
  - `SubmissionControllerTest`：未登录 401、跨用户 403、限频 429。
  - `JudgeWorkerTest`：mock 沙箱，覆盖 `AC`/`WA`/`RE`/`TLE`/`CE`。
  - `PythonSandboxIntegrationTest`：用 Testcontainers 启 Docker，跑 5 道课程真实题（覆盖 input/print、for、list）。
  - 验证 `practice_submission` 与 `judge_result` 字段完整、`MasteryEvaluator` 被调用。
- T09-61 前端：
  - `TrainingPanel.spec.ts`：即时预览不调用 `/api/submissions`；正式提交触发轮询。
  - `pyodide.adapter.spec.ts`：mock pyodide global，验证 init/run/dispose 顺序。
- T09-62 数据契约测试：DTO 不含 `hidden_test_cases`、`expectedOutputPattern` 等敏感字段。

## 6. 数据输入与输出

- 输入：
  - 学生代码（前端编辑器）。
  - 题目（`course_problem`，含 hidden cases，仅后端读）。
- 输出：
  - `practice_submission`、`judge_result`。
  - `kc_mastery_event`（`source=PRACTICE_PASS`/`PRACTICE_FAIL`）。
  - `quest_progress`（若关联 quest）。
- 接口：
  - `POST /api/submissions`、`GET /api/submissions/{id}`、`GET /api/submissions`、`GET /api/problems/{id}/public`。

## 7. 验收标准

### 7.1 功能验收

- 学生在训练场选一道题：
  - 即时预览能返回 stdout/stderr，不写库。
  - 正式提交后 ≤ 30 秒返回 `AC` 或 `WA`/`RE`/`TLE`。
  - `practice_submission`、`judge_result` 各增加 1 行。
  - 若 `AC` 且关联 quest：quest `COMPLETED`，KC mastery +0.10。
  - 若失败：mastery -0.02；前端显示"是否进入 Bug 矿洞？"按钮（功能在阶段 10 实现）。
- 学生在 10 秒内连续两次提交：第二次返回 429。
- 沙箱拒绝 `import os`：返回 `CE`，`failure_reason=banned_import`。

### 7.2 数据保护验收

- API 响应字段中**绝无**`hidden_test_cases`、`expected_output_pattern`；通过自动化 schema 校验。
- Pyodide 预览不会写库；通过前端 spec 与后端审计日志确认。

### 7.3 工程验收

- `mvn test`、`npm run test` 通过。
- `JudgeWorker` 在 Docker 不可用时给出明确启动日志（不静默降级到危险子进程）。
- 沙箱日志 redact 验证：使用 `grep` 在测试日志中找不到宿主路径。

## 8. 验证命令

```bash
cd /home/cypress/Pythontopia/backend
mvn test -Dtest=PythonSandboxIntegrationTest
mvn spring-boot:run &

TOKEN=...
PID=$(curl -s -H "Authorization: Bearer $TOKEN" http://localhost:18080/api/course/problems/1 | jq -r .id)

# 即时预览（前端 only），后端无此接口
# 正式提交
SID=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"problemId\":$PID,\"code\":\"print(input())\"}" \
  http://localhost:18080/api/submissions | jq -r .id)

for i in 1 2 3 4 5; do
  curl -s -H "Authorization: Bearer $TOKEN" \
    http://localhost:18080/api/submissions/$SID | jq '.status'
  sleep 2
done

cd /home/cypress/Pythontopia/frontend
npm run typecheck
npm run test
```

## 9. 风险与回滚

- 风险：沙箱启动慢导致超时；缓解：保留 1 个常驻 `pyrunner` 容器或预热镜像。
- 风险：Pyodide CDN 不稳定；缓解：自托管 `pyodide` 静态文件到 `/home/cypress/Pythontopia/frontend/public/pyodide/` 并固定版本。
- 风险：CodeMirror 6 与 Vue 3 集成复杂；缓解：使用社区包 `vue-codemirror`，但仍按 adapter 模式隔离。
- 风险：沙箱误判通过；缓解：CI 包含一组"已知必通过" + "已知必失败"题目作回归。
- 回滚：删除 `practice_submission`、`judge_result` 行 + 回滚 `kc_mastery_event` 即可还原。

## 10. 不做事项

- 不把 Pyodide 结果写入 `practice_submission` 或 `judge_result`。
- 不暴露 hidden cases；不允许"教师视角"在学生端泄题。
- 不支持非 Python 语言。
- 不支持 stdin 巨量输入（>1MB）。
- 不在判题里调用 LLM 解释代码（解释属于 Bug 矿洞阶段）。
- 不允许沙箱开网络（`--network=none`）。

