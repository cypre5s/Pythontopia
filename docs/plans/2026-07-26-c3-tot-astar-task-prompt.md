你是一名资深 Python、LangGraph、多智能体系统与智能教育算法工程师。

请直接在现有项目基础上，增量实现面向 C3 复杂任务的 ToT＋A* 多 Agent 动态路由功能。

## 一、重要前提

项目中的以下设计已经完成，不需要重新设计：

* 百晓工坊统一入口；
* 学习上下文构建；
* IntentRouter；
* 任务复杂度识别；
* C0、C1、C2、C3 分类；
* AgentRegistry；
* ToolRegistry；
* ToolPolicyEngine；
* 单 Agent 路由；
* 顺序多 Agent 编排；
* 并行多 Agent 编排；
* Supervisor；
* Tool Calling；
* GroundingCritic；
* SafetyAgent；
* SchemaValidator；
* Fallback；
* 状态持久化；
* LangGraph 基础工作流。

项目设计以以下文档为唯一基准：

```text
baixiao_workshop_agent_routing_complete_plan(1).md
```

开始编码前，请先完整阅读该 MD，并检查当前仓库的实际代码结构。

不要重复生成整套 Agent 架构，不要推翻已有模块，不要另起一套平行系统。

你的任务是在现有实现中补充：

> C3 复杂规划任务下的 ToT 候选生成、A* 路径搜索、路径评分、硬规则剪枝、有限执行和动态重规划。

---

## 二、本次开发目标

当任务被现有 `complexity_router` 判定为 `C3` 时，系统不再立即选择一条固定 Agent 路线，而是执行：

```text
当前任务状态
→ ToT 生成多个候选下一步
→ PolicyEngine 过滤非法动作
→ A* 计算候选节点分数
→ 优先选择 f 值最低的节点
→ 执行一个 Agent 或 Tool
→ 根据真实结果更新状态
→ 继续搜索或进行一次重规划
→ 达到目标后进入现有 Critic 流程
```

C0、C1 和 C2 的现有逻辑必须保持不变。

---

## 三、核心算法边界

必须明确区分以下职责。

### 1. ToT 候选生成器

ToT 只负责根据当前状态生成 2～3 个候选动作。

候选动作可以是：

* 调用一个业务 Agent；
* 调用一个只读 Tool；
* 执行一个局部任务；
* 进入 Critic；
* 请求有限澄清；
* 结束当前计划并返回部分结果。

ToT 不负责：

* 维护优先队列；
* 累计成本；
* 决定最终执行哪个节点；
* 绕过权限检查；
* 直接执行 Tool；
* 自由创建 Agent 或 Tool。

ToT 输出必须是结构化 JSON。

候选动作必须来自现有：

```text
AgentRegistry
ToolRegistry
AllowedTransitions
```

模型不得生成不存在的 Agent、Tool 或动作类型。

### 2. AStarController

AStarController 必须由确定性 Python 程序实现，负责：

* 维护 OPEN 优先队列；
* 维护 CLOSED 或 best_cost；
* 计算并累计 `g_score`；
* 调用 `h_score` 估计器；
* 调用 `penalty_score` 估计器；
* 计算 `f_score`；
* 选择当前最优节点；
* 检测重复状态；
* 控制最大搜索深度；
* 控制最大展开节点数；
* 控制 Agent 和 Tool 调用预算；
* 控制重规划次数；
* 在搜索失败时进入现有 Fallback。

禁止让大模型直接维护 OPEN、CLOSED 或决定最终优先级。

### 3. PolicyEngine

PolicyEngine 在候选节点进入 OPEN 之前执行。

以下情况必须直接剪枝，不能只增加 penalty：

* Agent 无权调用对应 Tool；
* Tool 不存在；
* 缺少 Tool 所需上下文；
* 无题目或代码上下文却调用 JudgeTool；
* 越权读取其他学生数据；
* 普通 Agent 直接修改 KC 掌握度；
* 未经审查发布正式课程资源；
* 生成可直接提交的完整答案；
* 超过 Agent 或 Tool 调用预算；
* 违反已有安全策略；
* 产生不允许的副作用操作。

### 4. Critic

现有 Grounding、Safety 和 Schema Critic 保持不变。

A* 找到满足任务目标的路径后，必须进入现有 Critic 流程。

Critic 不负责路径搜索，只负责最终结果审查。

---

## 四、搜索节点设计

请基于现有 State 增量扩展，不要重新定义一套与项目不兼容的状态。

建议增加以下搜索字段：

```python
search_mode: str
search_node_id: str
parent_node_id: str | None
search_depth: int
route_history: list[str]

completed_tasks: list[str]
remaining_tasks: list[str]

agent_call_count: int
tool_call_count: int
tool_call_counter: dict[str, int]
interaction_turns: int

token_cost: int
latency_ms: int

g_score: float
h_score: float
penalty_score: float
f_score: float

open_nodes: list
closed_state_keys: list[str]
best_cost: dict[str, float]

expansion_count: int
replan_count: int
search_status: str
```

请根据当前项目使用 TypedDict、Pydantic、dataclass 或其他已有状态风格进行适配。

不要为了实现该功能破坏现有 State 序列化、持久化和 LangGraph checkpoint。

---

## 五、候选动作结构

请定义严格的候选动作 Schema，例如：

```json
{
  "action_id": "action-001",
  "action_type": "CALL_AGENT",
  "target": "DebugAgent",
  "reason": "学生历史中存在多次 off_by_one 错误，需要先明确薄弱点",
  "expected_outputs": [
    "primary_error",
    "error_confidence",
    "recommended_strategy"
  ],
  "required_context": [
    "student_id",
    "recent_submissions"
  ],
  "estimated_cost": {
    "agent_calls": 1,
    "tool_calls": 1,
    "token_cost": 1000,
    "latency_ms": 1500
  },
  "risk_estimate": {
    "cognitive_load": 0.1,
    "frustration": 0.1,
    "uncertainty": 0.2
  }
}
```

允许的 `action_type` 必须采用枚举，例如：

```text
CALL_AGENT
CALL_TOOL
RUN_SUBTASK
ENTER_CRITIC
ASK_CLARIFY
RETURN_PARTIAL
```

`target` 必须在 Registry 中存在。

---

## 六、ToT 生成要求

实现一个 `ToTCandidateGenerator`。

输入至少包括：

```text
当前学生请求
学习上下文
已完成任务
剩余任务
已有中间结果
课件证据状态
历史执行路线
当前搜索深度
剩余调用预算
允许使用的 Agent
允许使用的 Tool
当前风险状态
```

输出 2～3 个互相具有明显差异的候选动作。

不要生成只是措辞不同、实际动作相同的候选。

例如对于：

```text
我循环这一章学得很乱，结合以前的错误帮我安排复习。
```

可生成：

```text
候选一：先由 DebugAgent 分析历史错误；
候选二：先由 CoursewareQAAgent 重构知识框架；
候选三：先由 PracticeAgent 生成诊断微测。
```

ToT 输出必须通过 Pydantic 或 JSON Schema 校验。

如果输出非法：

```text
第一次：进行结构修复；
第二次仍失败：返回规则生成的安全候选动作。
```

---

## 七、A* 评分函数

采用：

```text
f(n) = g(n) + h(n) + penalty(n)
```

所有评分指标先归一化到 0～1。

### 1. 已执行成本 g(n)

```text
g(n)
= 0.30 × C_agent
+ 0.15 × C_tool
+ 0.20 × C_latency
+ 0.15 × C_token
+ 0.20 × C_interaction
```

归一化预算：

```text
max_agent_calls = 4
max_tool_calls = 8
latency_budget_ms = 10000
token_budget = 8000
max_interaction_turns = 3
```

计算方式：

```text
C_agent = min(agent_call_count / 4, 1)

C_tool = min(tool_call_count / 8, 1)

C_latency = min(latency_ms / 10000, 1)

C_token = min(token_cost / 8000, 1)

C_interaction = min(interaction_turns / 3, 1)
```

`g_score` 必须基于已发生的真实成本累计。

如果候选节点尚未真正执行，可以使用 Registry 中的估计成本；执行完成后必须用真实结果更新。

### 2. 剩余成本 h(n)

```text
h(n)
= 0.35 × U_task
+ 0.30 × G_mastery
+ 0.20 × G_evidence
+ 0.15 × R_error
```

其中：

```text
U_task = 未完成必要子任务数 / 必要子任务总数

G_mastery = 目标 KC 掌握度与当前掌握度之间的平均缺口

G_evidence = 当前必要证据的缺失比例

R_error = 相关错误复发风险
```

如果当前任务不涉及某个指标，例如不涉及 KC 掌握度，则不要简单写死为 0。

请重新归一化剩余有效权重，避免某类任务天然获得更低分。

例如只有 `U_task` 和 `G_evidence` 有效，则：

```text
h =
0.35 / 0.55 × U_task
+ 0.20 / 0.55 × G_evidence
```

### 3. 软惩罚 penalty(n)

```text
penalty(n)
= 0.35 × R_cognitive
+ 0.30 × R_frustration
+ 0.20 × R_redundancy
+ 0.15 × R_uncertainty
```

其中：

* `R_cognitive`：认知负荷；
* `R_frustration`：学生挫败风险；
* `R_redundancy`：重复生成或重复调用风险；
* `R_uncertainty`：该路径结果不确定性。

硬安全问题不得放入 penalty。

---

## 八、评分实现要求

请实现独立的：

```python
normalize_costs(...)
calculate_g_score(...)
estimate_h_score(...)
calculate_penalty_score(...)
calculate_f_score(...)
```

同时定义：

```python
AStarScoringConfig
```

所有预算和权重必须配置化，禁止散落在业务代码中。

建议配置结构：

```json
{
  "version": "v1",
  "normalization": {
    "max_agent_calls": 4,
    "max_tool_calls": 8,
    "latency_budget_ms": 10000,
    "token_budget": 8000,
    "max_interaction_turns": 3
  },
  "g_weights": {
    "agent": 0.30,
    "tool": 0.15,
    "latency": 0.20,
    "token": 0.15,
    "interaction": 0.20
  },
  "h_weights": {
    "unfinished_tasks": 0.35,
    "mastery_gap": 0.30,
    "evidence_gap": 0.20,
    "error_recurrence": 0.15
  },
  "penalty_weights": {
    "cognitive_load": 0.35,
    "frustration": 0.30,
    "redundancy": 0.20,
    "uncertainty": 0.15
  }
}
```

`f_score` 的绝对值没有独立意义，只用于同一次搜索中的候选节点排序。

---

## 九、搜索流程

请实现以下流程：

```text
1. complexity_router 输出 C3；
2. 初始化根搜索节点；
3. 根节点进入 OPEN；
4. 从 OPEN 取出 f_score 最小节点；
5. 检查是否满足 Goal Test；
6. 若未完成，由 ToT 生成 2～3 个候选动作；
7. 使用 PolicyEngine 过滤候选；
8. 对合法候选执行轻量状态转移或成本预估；
9. 计算 g、h、penalty 和 f；
10. 加入 OPEN；
11. 使用 canonical_state_key 去重；
12. 如果新路径成本不优于已有路径，则丢弃；
13. 选择下一最优节点；
14. 执行一个真实 Agent 或 Tool；
15. 使用真实返回值更新 State 和成本；
16. 根据新状态继续搜索；
17. 达到目标后进入现有 Critic；
18. Critic 通过后输出；
19. 超过预算则进入现有 Fallback。
```

---

## 十、搜索限制

使用以下默认限制：

```text
每个节点最多生成候选动作：3
最大搜索深度：4
最大展开节点数：20
最大 Agent 调用数：4
最大 Tool 调用数：8
同一 Tool 最多重复调用：2
Critic 修复次数：1
动态重规划次数：1
```

这些限制必须配置化。

当预算耗尽时，禁止继续调用模型或 Tool。

系统应根据已有结果返回：

```text
PARTIAL_RESULT
SAFE_FALLBACK
ASK_CLARIFY
```

之一。

---

## 十一、Goal Test

Goal Test 必须结合当前任务动态生成，不能写死为所有任务都必须有总结、练习和路径。

例如复合复习任务可以要求：

```text
历史错误已经识别；
个性化总结已经生成；
针对性练习已经生成；
练习顺序已经确定；
课件证据已经准备；
结果可以进入最终 Critic。
```

请实现：

```python
build_goal_requirements(route_decision)
is_goal(state, goal_requirements)
```

Goal Test 只判断任务内容是否完成。

Grounding、Safety、Schema 是否通过，继续交给现有 Critic 流程判断。

---

## 十二、状态去重

请实现稳定的：

```python
canonicalize_search_state(state) -> str
```

去重键至少考虑：

```text
已完成子任务集合
剩余子任务集合
当前关键中间结果类型
已经使用的关键 Agent
已经调用的关键 Tool
证据准备状态
当前教学阶段
```

不要将：

```text
时间戳
随机 ID
完整自然语言回答
Token 精确数量
微小延迟差异
```

直接纳入状态键，否则无法有效去重。

---

## 十三、动态重规划

只允许一次动态重规划。

满足以下条件之一时允许重规划：

```text
Tool 返回空结果；
RAG 没有课程证据；
Agent 输出与预期不一致；
发现新的主要错误类型；
学生掌握度与原预测差异较大；
当前路径被 PolicyEngine 阻断；
Critic 指出计划层面缺失必要步骤。
```

重规划时：

```text
保留已有有效中间结果；
保留真实 g 成本；
更新 remaining_tasks；
重新调用 ToT；
重新计算后续候选路径；
不得清空整个状态重新开始。
```

---

## 十四、LangGraph 集成

请先检查现有 WorkshopGraph 的节点和状态定义，再以最小侵入方式添加：

```text
tot_candidate_generator
astar_controller
search_policy_filter
search_step_executor
search_state_evaluator
search_replanner
```

推荐 C3 分支：

```text
complexity_router
→ tot_candidate_generator
→ search_policy_filter
→ astar_controller
→ search_step_executor
→ search_state_evaluator
```

`search_state_evaluator` 的条件边：

```text
GOAL_REACHED
→ 现有 grounding_critic

CONTINUE_SEARCH
→ astar_controller

NEED_REPLAN
→ search_replanner

BUDGET_EXCEEDED
→ 现有 fallback_response
```

不要修改 C0、C1 和 C2 的原有边。

---

## 十五、示例任务

输入：

```text
我循环这一章学得很乱，先给我总结一下，再根据我以前犯的错出几道题，并告诉我先做哪一道。
```

上下文：

```json
{
  "current_chapter": "循环结构",
  "kc_mastery": {
    "for_loop": 0.51,
    "while_loop": 0.62,
    "range_boundary": 0.30
  },
  "recent_errors": {
    "off_by_one": 4,
    "loop_count_reasoning": 2
  },
  "recent_submissions": {
    "WA": 2,
    "AC": 1
  },
  "preferred_explanation": "example_first",
  "frustration_level": 0.67
}
```

ToT 第一轮候选应类似：

```text
候选 A：
DebugAgent，先分析历史错误。

候选 B：
CoursewareQAAgent，先重构循环知识框架。

候选 C：
PracticeAgent，先生成低难度诊断练习。
```

A* 应综合判断：

```text
候选 A 虽然增加一次诊断调用，
但能够显著降低剩余任务不确定性，
且学生已有足够历史错误数据，
因此整体 f_score 最低。
```

可能的最终路径：

```text
DebugAgent
→ CoursewareRAGTool
→ ResourceDesignerAgent
→ PracticeAgent
→ PathPlannerAgent
→ GroundingCriticAgent
→ SafetyAgent
```

---

## 十六、测试要求

至少补充以下测试。

### 单元测试

```text
g_score 归一化测试
h_score 有效权重重新归一化测试
penalty_score 测试
f_score 排序测试
PolicyEngine 硬剪枝测试
Registry 非法 Agent 测试
Registry 非法 Tool 测试
最大调用预算测试
重复状态检测测试
Goal Test 测试
重规划次数测试
Fallback 测试
```

### 场景测试

至少覆盖：

```text
诊断优先路线胜出；
知识重构路线胜出；
练习探测路线胜出；
JudgeTool 缺少题目上下文被剪枝；
完整答案泄露路线被剪枝；
RAG 无证据后重规划；
Tool 失败后返回部分结果；
超过展开节点预算进入 Fallback；
C0、C1、C2 现有流程不受影响。
```

### 可观测性测试

每次搜索至少记录：

```text
route_id
search_node_id
parent_node_id
action
g_score
h_score
penalty_score
f_score
policy_decision
expansion_count
search_depth
selected_node
pruned_reason
replan_reason
final_route
```

禁止记录不必要的敏感学生原始数据。

---

## 十七、交付要求

请直接修改现有项目，不要只输出概念设计。

完成后给出：

1. 当前仓库结构分析；
2. 实际修改的文件列表；
3. 每个新增模块的职责；
4. 与现有 MD 设计的对应关系；
5. ToT 候选 JSON Schema；
6. SearchNode 和搜索状态定义；
7. AStarController 实现；
8. g、h、penalty、f 评分实现；
9. LangGraph C3 分支集成；
10. PolicyEngine 剪枝逻辑；
11. 动态重规划逻辑；
12. 单元测试与场景测试；
13. 测试运行结果；
14. 示例请求的完整搜索日志；
15. 仍存在的限制；
16. 后续基于真实教学数据调整权重的方法。

---

## 十八、禁止事项

禁止：

* 重新设计整套百晓工坊架构；
* 删除或重写现有 C0、C1、C2 路由；
* 创建与现有 Registry 平行的新 Agent 系统；
* 让 LLM 自由创建 Tool；
* 让 LLM 维护优先队列；
* 将硬安全问题仅作为 penalty；
* 将所有任务都强制进入 ToT＋A*；
* 伪造尚未存在的数据库、API 或工具；
* 只输出伪代码而不修改现有项目；
* 为追求形式上的 A* 而忽略现有工程结构；
* 宣称该多目标启发式搜索必然获得经典 A* 意义上的全局最优解。

请以“增量实现、最小侵入、可运行、可测试、可回滚”为原则完成本次开发。
