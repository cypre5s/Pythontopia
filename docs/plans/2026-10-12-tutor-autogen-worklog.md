# Tutor AutoGen 运行时替换工作日志

日期：2026-10-12

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
- 每个 HTTP 工作流请求使用一个短生命周期 AutoGen Team，不新增跨请求 checkpoint，也不改变 Java 侧持久化模型。
