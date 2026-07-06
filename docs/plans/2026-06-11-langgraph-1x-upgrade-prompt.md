# LangGraph 0.2 → 1.x 升级执行 Prompt（升级 / 测试 / 验证全流程）

> 用法：把本文档整体作为任务 prompt 交给执行 agent（或人工照做）。
> 预算：0.5~1 天单人窗口。**严禁与其它改动混在同一窗口**——本升级必须是干净、可整体回滚的独立变更。

---

## 你的任务

把 `/home/cypress/Pythontopia/services/pythontopia-graph` 的 LangGraph 依赖从 `>=0.2,<0.3` 升级到 1.x 最新稳定版，完成 API 迁移，并落地**唯一的功能性目标：学习总管（orchestrator）会话状态 checkpointer 持久化**（服务重启后规划对话可续聊）。可选目标（时间富余才做）：澄清追问 `interrupt()` 化。

## 背景与现状（已盘点，可信）

- 依赖声明：`services/pythontopia-graph/pyproject.toml` 中 `langgraph>=0.2,<0.3`。
- **实际用法只有一处**：`pythontopia_graph/graph/orchestrator_graph.py` 使用 `from langgraph.graph import END, START, StateGraph`，节点 `understand / build_plan / dispatch / replan / extract_profile / compose / clarify`，普通 `add_node/add_edge/conditional` 编排，**未用** checkpointing / interrupt / Send。
- 会话状态：`pythontopia_graph/graph/orchestrator_session.py` 手写**进程内存**会话（重启即丢）——这是本次升级要替换/增强的点。
- 其余管线（resource_graph / debate_graph / 答疑路由）是手写类，不依赖 langgraph，**禁止顺手迁移**。
- 运行约束（AGENTS.md 红线）：生产/演示**单 worker**；Agent 输出结构化、fail-fast；**不启用 ReAct 循环**；SSE 线协议（plan/trace/token/result/error 事件）是前后端冻结契约，**绝对不能变**。
- R-16 已落地的治理层 `pythontopia_graph/tools/`（registry/harness：熔断/预算/ToolObservation）与 langgraph 正交，**只读不改**。
- 测试基线（升级前必须复核）：`python3 -m pytest -q` 全量 **521 passed**（环境 Python 3.10.12，注意命令是 `python3`）。

## 硬性红线（违反任意一条即任务失败）

1. **先上网核实，再写代码**：你训练数据中的 LangGraph 1.x API 可能过时/错误。动手前必须检索并通读：1.x 当前最新稳定版本号、官方 0.x→1.x 迁移指南、checkpointer（MemorySaver / PostgresSaver）现行用法、`interrupt()` 现行用法。**禁止凭记忆编造 API**；每个用到的新 API 在代码注释里标注来源（官方文档 URL）。
2. SSE 事件线协议字节级兼容：事件名、字段名、大小写、snake_case 全部不变（前端 `useSseStream` 与 Java `OrchestratorService` 是消费方）。
3. 既有 521 个测试**零回归**（允许因 API 迁移修改测试内部构造，不允许删测试/弱化断言）。
4. 单 worker 内存约束：checkpointer 起步用 `MemorySaver`（演示够用）；若上 `PostgresSaver`，复用 deploy 既有 Postgres（127.0.0.1:15432），新表必须有迁移说明，且连接失败时**降级回 MemorySaver + 告警**，不得让 orchestrator 整体不可用。
5. 不动 `tools/`、`agents/`（除非 API 迁移强制触及 import）、`routes/` 的对外路径、`schemas/` 的对外字段。
6. 升级在独立 git 分支进行（`feat/langgraph-1x`），单一可整体 revert 的提交序列。

## 执行步骤

### Phase 0 · 基线（必须先做）
1. `git checkout -b feat/langgraph-1x`。
2. `cd services/pythontopia-graph && python3 -m pytest -q` 记录基线数字（应为 521 passed；若不同，停下报告，不要继续）。
3. 上网完成红线 #1 的调研，写一份 ≤30 行的《迁移差异速记》进 PR 描述/报告（版本号、breaking changes 清单、checkpointer 选型结论）。

### Phase 1 · 依赖升级 + API 迁移
1. `pyproject.toml`：`langgraph>=1,<2`（按调研到的实际最新稳定版收紧上限）。`python3 -m pip install -e ".[dev]"`。
2. 修复 `orchestrator_graph.py` 的 import / 编译期不兼容（`StateGraph`/`START`/`END`/`compile` 签名变化按迁移指南处理）。
3. 跑全量 pytest——此阶段目标：**行为零变化、全量绿**。绿了再进 Phase 2。

### Phase 2 · orchestrator 会话 checkpointer 持久化（核心交付）
1. `OrchestratorGraph.compile()` 接入 checkpointer（`MemorySaver` 起步），`thread_id` 采用现有会话 id（`orchestrator_session` 的 session key / 前端 `sessionId`，对齐 `routes/orchestrator.py` 现有入参，不改对外契约）。
2. `orchestrator_session.py` 的手写会话态与 checkpointer 的关系二选一并在代码注释说明理由：
   - A（推荐，小步）：保留现有 session 结构，checkpointer 仅承载图状态（多轮规划上下文随 thread 恢复）；
   - B（彻底）：会话历史并入图 state 由 checkpointer 全管，session.py 退役——仅在测试证明 SSE 行为不变时才允许。
3. 若做 PostgresSaver：连接串从环境变量读取（与现有 env 约定一致），启动失败 fallback MemorySaver + `LOG.warning`。

### Phase 3 ·（可选）澄清 `interrupt()` 化
仅当 Phase 2 全绿且时间富余：`clarify` 节点改为 `interrupt()` 原地等待，前端答复后从断点续跑。**前置条件**：确认现有 SSE 一问一答模式可以无契约变化地承载（`needs_user_input=true` 的 result 事件语义不变）。做不到无契约变化就放弃本 Phase，写明原因。

### Phase 4 · 测试补充（新行为必须有锁）
新建 `tests/test_orchestrator_checkpoint.py`，至少覆盖：
1. 同 `thread_id` 两轮调用：第二轮图状态包含第一轮的规划上下文（断言具体字段，不允许只断言"不报错"）；
2. 不同 `thread_id` 互相隔离；
3. 模拟"重启"：新建图实例 + 同一 checkpointer 存储 → 状态可恢复（MemorySaver 场景用同一 saver 实例模拟；PostgresSaver 场景用真连接或跳过标记）;
4. checkpointer 故障注入：saver 抛异常时 orchestrator 仍能完成单轮（降级路径）；
5. SSE 契约回归：升级后 `routes/orchestrator.py` 输出的事件序列与字段，与升级前固定夹具逐字段断言。

### Phase 5 · 验证矩阵（全部实跑，报告里贴数字）
| 项 | 命令/方法 | 通过标准 |
|---|---|---|
| graph 全量 | `python3 -m pytest -q` | ≥521+新增 全绿 |
| 后端联调 | `cd backend && mvn -q test -Dtest='*Orchestrator*'` | 全绿（Java 客户端契约未破） |
| 真栈冒烟 | `./dev.sh` 起栈 → 同一 sessionId 连发两轮 `/api/orchestrator/turn/stream`（curl SSE） | 第二轮回复体现第一轮上下文；**重启 graph 容器后**第三轮仍续上（PostgresSaver 时；MemorySaver 注明预期不续） |
| 性能 | 升级前后各测 3 次 orchestrator 单轮耗时 | 首 token 延迟劣化 <15% |
| 前端 | `npm run typecheck && npx vitest run` | 全绿（不应有任何前端改动） |

### 回滚预案
任一阶段卡死超过 2 小时或全量测试无法转绿：`git checkout main`，报告卡点与建议，**不留半成品分支合并**。

## 交付报告格式
做了什么（含《迁移差异速记》）/ 版本号变化 / 测试新增清单与全量数字 / 真栈冒烟记录（命令+输出摘录）/ 未做与原因（尤其 Phase 3）/ 风险与回滚点。
