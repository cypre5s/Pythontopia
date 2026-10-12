# Tutor AutoGen 运行时替换工作日志

初始实施：2026-10-12；恢复链补齐：2026-10-13

## 范围

- 将 Graph 的 `TutorWorkflowGraph.run` 从 LangGraph `StateGraph` 调度切换为 AutoGen AgentChat `RoundRobinGroupChat`。
- 保留既有五个节点 handler、SSE 事件、Java 投影请求/结果 DTO 和持久化边界；不迁移 Alethicode 数据库/API。
- 状态经 JSON 消息边界传递，并显式恢复 Pydantic 请求、画像/教练状态、动作和 RAG 命中对象。

## 实施

- 新增 `pythontopia_graph/graph/autogen_tutor_runtime.py`：固定顺序 `BaseChatAgent`，每次 `max_turns=1`；每阶段完成后继续下一个 Agent，发现 `runtime_state=FAILED` 就停止。
- `routes/tutor_workflow.py` 在 SSE 清理路径取消 AutoGen 共享令牌，同时取消并等待 graph task，避免断连后继续生成或提交会话卡片。
- `pyproject.toml` 增加与 Alethicode Tutor 运行时一致的 `autogen-agentchat==0.7.5`、`autogen-core==0.7.5`。

## RED / GREEN 与验证

- RED：先运行新增测试，因 AutoGen adapter 模块不存在而在收集阶段失败；随后增加取消令牌场景，确认 runner 尚不接受 route 传入的共享 token。
- GREEN：新增 AutoGen 状态往返、失败短路、runner 取消和 SSE 断连取消用例通过。
- Graph 定向回归：`tests/test_tutor_autogen_runtime.py`、`tests/test_tutor_workflow.py`、`tests/test_tutor_workflow_coach_graph.py`、`tests/test_sse_agui.py` 共 39 passed。
- Ruff 对新增/修改 Graph 源码及新测试通过；`compileall` 通过。
- Mypy 定向尝试仍报已有类型错误：`contracts/semantic_ir.py` 两项、`graph/tutor_workflow_graph.py` 的动作构造两项、`routes/tutor_workflow.py` 的依赖注入返回类型两项；AutoGen adapter 本身的两项兼容问题已修正。

## 未验证 / 风险

- 未运行完整 Graph/RAG 套件、Java 后端测试、真实 DeepSeek 调用或浏览器端到端流程；本次验证仅覆盖导师 Graph/SSE 定向链路。
- 10 月 12 日初版每个 HTTP 请求使用短生命周期 AutoGen Team，没有跨请求 checkpoint；这一边界由下方 10 月 13 日补丁更新。

## 2026-10-13 — 对照 Alethicode 最新 AutoGen Tutor 补齐恢复链

### 来源核实

- Alethicode 工作树 HEAD：`10a97fe669c3dc40f3c6eb96a849a7161b6d3358`（2026-05-20，`docs(项目总结报告): 5.4 数据流转图汉化`）。当前 `git status --porcelain` 有 586 个已跟踪路径改动、449 个未跟踪路径；AutoGen Tutor 实现仍在未提交工作区，不能把 HEAD 当作其实现提交。
- 直接对照 `/home/cypress/Alethicode/services/tutor-graph/app/main.py`：固定阶段循环每阶段调用 AutoGen `team.save_state()`，恢复时调用 `team.load_state()`；同文件也实现 run owner、取消 watcher、显式 run ID 重放和中断恢复。数据库来源为 `backend/src/main/resources/db/migration/V124__ai_agent_runtime_checkpoint.sql`，独立保存 Tutor/QA AutoGen 原生状态。
- 对照 Codex rollout `01a12f26-6bab-7371-8763-06f922c1b13b`（2026-10-12，Alethicode 工作树）：该日志记录了无提交边界下的 AutoGen Tutor、取消/恢复/idempotency 测试，以及不启用 ReAct 的决定；日志说明验证为本地离线检查，没有真实模型请求。 rollout 汇总的工作树规模同当前 status：586 tracked / 449 untracked。

### 实施

- `run_autogen_tutor_workflow` 每个固定阶段完成后保存 AutoGen 原生 `team.save_state()` 与业务状态 JSON；恢复时用 `team.load_state()`，从 checkpoint 的下一阶段继续。恢复前精确比对原 `WorkflowRunRequest`，拒绝跨用户/会话/题目/事件复用。
- 新增 V199 `tutor_workflow_autogen_checkpoint`，按会话/run 保存请求 SHA-256 指纹、阶段和 checkpoint；同一会话仅允许一个 RUNNING run，单调阶段写入。相同指纹的 INTERRUPTED run 复用原 run ID 和阶段状态；成功投影在原子事务内标记 COMPLETED，终态投影仍按 session/run 唯一键去重。
- Java 以内部 token 和 `X-Internal-Subject: user:<id>` 接收 Graph 阶段快照，再由 session owner 查询和 run/fingerprint/status 校验；checkpoint 限 1 MiB，只收文本与结构数据，图片字段、字节和图片 data URI 均拒绝。没有改变 `/api/tutor/workflow` 外部请求/响应合同、pack 43 引用解析或图片附件 owner/session 校验。
- 同 session 的 Graph 路由和数据库各自拒绝并发 run。Java SSE 下游发送失败会中止并关闭上游 Graph HTTP body；Graph comment heartbeat 用于及时探测断连，Python 路由随即取消共享 `CancellationToken`、AutoGen task 并释放 session 占用。正常断流和浏览器断连都把 run 标成 INTERRUPTED，明确 Graph `error` 则标为 FAILED。
- 只移植「固定 AutoGen 阶段 + 原生状态恢复」所需行为。Alethicode 异步 run/Redis event bus、V124 表名、QA ReAct、language-pack/API 实现未复制，Pythontopia 继续使用既有 Java API、SSE、会话投影和私有附件边界。

### RED / GREEN 与验证

- Python RED/GREEN 覆盖 native team 保存与下一阶段恢复、请求 scope 不匹配、每阶段 owner-scoped checkpoint 写入、同 session 并发拒绝、SSE 断连取消、拒绝图片进入 checkpoint：`tests/test_tutor_autogen_runtime.py` 与 `tests/test_tutor_workflow_attachments.py` 共 15 passed；Tutor 工作流、coach、Task 7、AG-UI 联测 74 passed。
- Ruff 对 AutoGen runtime、Tutor graph/session/route、checkpoint client 和定向测试通过。
- Java Tutor 服务、Graph SSE、内部 checkpoint controller 与 Testcontainers repository integration 共 41 tests 通过；空 PostgreSQL 从旧 schema 迁移经过 V199，并与根任务的 V200 Boss migration 共存。
- 未启动本地 Web 全栈、未调用 DeepSeek、未运行远征性能测量；没有 commit/push。真实模型恢复质量、跨服务浏览器断连仍未联测。
