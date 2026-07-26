# 百晓工坊 Agent 选择路由完整方案

百晓工坊应被定位为：

> **Pythontopia 面向学生的统一 AI 学习入口。**

它本身不等于某一个 Agent，而是负责接收学生的自然语言请求，并在“答疑、调试、资源生成、练习生成、知识整理、学习路径规划”等能力之间完成动态路由。

因此，合理的设计不是：

```text
学生输入 → 一个万能 Agent → 直接回答
```

而是：

```text
学生输入
→ 学习上下文构建
→ 意图与复杂度识别
→ Agent 选择
→ Tool Calling
→ 多 Agent 编排
→ 证据与安全审查
→ 结果展示
→ 学习状态更新
```

---

# 一、总体设计目标

百晓工坊的路由系统需要同时满足五个目标。

## 1. 任务识别准确

能够区分学生是在：

- 询问知识概念；
- 请求课件解释；
- 调试代码错误；
- 寻求解题提示；
- 生成讲义或学习资源；
- 生成练习；
- 生成学习路径；
- 请求复合型学习支持。

## 2. Agent 选择合理

不同任务交给不同专职 Agent，而不是让一个模型同时承担全部职责。

## 3. Tool Calling 受控

Agent 可以使用丰富工具，但工具调用必须满足：

- 权限可控；
- 参数可校验；
- 结果可追踪；
- 调用次数有上限；
- 涉及副作用时能够确认和回滚。

## 4. 输出可信

所有结果都需要经过：

- JSON Schema 校验；
- 课件证据检查；
- 答案泄露检查；
- 难度与学生水平匹配检查；
- 必要时自动重写或回退。

## 5. 形成学习闭环

百晓工坊的结果不能停留在一次回答，而应继续影响：

- 学生画像；
- KC 掌握度；
- 学习记忆；
- 练习推荐；
- 后续路径；
- 教师侧学情分析。

---

# 二、完整分层架构

建议把百晓工坊的 Agent 路由设计成七层。

```text
L0 统一交互入口
L1 学习上下文构建
L2 硬规则与安全门控
L3 意图、复杂度与任务拓扑识别
L4 Agent 选择与编排模式决策
L5 Tool Calling 与任务执行
L6 可信审查、修复与回退
L7 状态更新与学习闭环
```

---

## L0：统一交互入口

百晓工坊接收的不只是文本，还应允许携带上下文引用：

```text
学生文本
当前章节
当前题目
当前代码
最近提交
选中的课件页
引用的知识点
上传的报错信息
前端触发动作
```

例如：

```text
“为什么这题总是少算一个数？”
```

如果学生当前正在做 `range` 相关题目，系统应自动带上：

```json
{
  "problem_id": "py-loop-017",
  "current_chapter": "循环结构",
  "selected_code": "for i in range(1, n):",
  "latest_submission_status": "WA"
}
```

这样 Router 不需要仅凭一句模糊自然语言猜测任务。

---

## L1：学习上下文构建

由 `WorkshopContextBuilder` 统一组装上下文。

建议定义：

```json
{
  "student_id": 1024,
  "session_id": "workshop-20260722-001",
  "message": "我这个循环为什么总少一次？",
  "current_scene": "problem_solving",
  "current_chapter": "loop",
  "current_problem_id": "py-loop-017",
  "current_stage": "ERROR_FEEDBACK",

  "kc_mastery": {
    "for_loop": 0.48,
    "range_boundary": 0.31
  },

  "recent_submission": {
    "status": "WA",
    "attempt_count": 3,
    "error_signature": "off_by_one"
  },

  "learner_profile": {
    "frustration_level": 0.67,
    "preferred_explanation": "example_first",
    "recent_repeated_errors": ["range_boundary"]
  },

  "available_evidence": [
    "page-3-006",
    "page-3-007"
  ]
}
```

这一层只负责收集事实，不负责生成答案。

数据来源包括：

```text
Profile Tool
KC Mastery Tool
Submission Tool
Judge Result Tool
Learning Memory Tool
Courseware RAG Tool
Conversation Memory
Frontend Context
```

---

## L2：硬规则与安全门控

在调用 LLM 路由器之前，先执行确定性规则。

这层主要处理不能交给模型自由判断的情况。

### 1. 权限规则

例如：

```text
学生不能读取其他学生提交
学生不能调用教师统计工具
普通 Agent 不能直接修改 KC 掌握度
资源生成 Agent 不能直接发布教师正式课件
```

### 2. 场景规则

例如：

```text
包含 WA / RE / traceback → 优先判断为代码诊断
包含“生成练习” → 优先进入练习生成
包含“学习路径、怎么学” → 优先进入路径规划
当前无题目、无代码 → 不允许调用 Judge Tool
```

### 3. 风险规则

例如学生说：

```text
“直接把可以提交的完整答案给我。”
```

此时直接标记：

```json
{
  "answer_leak_risk": "high",
  "required_guard": ["SafetyAgent"],
  "forbid_actions": ["GENERATE_FULL_SOLUTION"]
}
```

### 4. 资源限制

例如：

```text
单次最多调用 8 个工具
单 Agent 最多执行 4 轮
Critic 最多返工 1 次
复杂规划最大深度 3
并行 Agent 数量不超过 3
```

这样可以防止 Agent 无限循环和工具爆炸。

---

# 三、L3：任务识别模型

这一层不能只识别“意图”，还需要同时识别三个维度。

## 1. 任务类型 Task Family

建议划分为八大类。

| 一级任务 | 典型请求 | 主 Agent |
|---|---|---|
| 概念答疑 | “列表和元组有什么区别？” | TutorAgent |
| 课件问答 | “老师课件这一页是什么意思？” | CoursewareQAAgent |
| 代码诊断 | “为什么一直 WA？” | DebugAgent |
| 解题引导 | “这题完全没思路” | GuideCoachAgent |
| 资源生成 | “生成一份循环讲义” | ResourceDesignerAgent |
| 练习生成 | “给我几道切片练习” | PracticeAgent |
| 知识整理 | “画一个函数知识图谱” | MindMapAgent |
| 路径规划 | “这一章接下来怎么学？” | PathPlannerAgent |

同时保留：

```text
MULTI_TASK：复合任务
CHAT：一般交流
UNKNOWN：无法可靠判断
```

---

## 2. 任务复杂度 Complexity

建议分为四级。

### C0：简单任务

不需要工具，单 Agent 可直接完成。

例如：

```text
“Python 是什么？”
```

### C1：上下文任务

需要一个 Agent 和少量工具。

例如：

```text
“老师课件里是怎么解释 range 的？”
```

需要：

```text
CoursewareQAAgent + CoursewareRAGTool
```

### C2：协同任务

需要多个 Agent 顺序或并行执行。

例如：

```text
“帮我总结这一章，再生成三道练习。”
```

需要：

```text
ResourceDesignerAgent
→ PracticeAgent
→ GroundingCriticAgent
```

### C3：规划型任务

目标不明确、步骤较多，需要动态调整。

例如：

```text
“我循环这一章学得很乱，你结合我之前的错误帮我安排一下。”
```

需要：

```text
PathPlannerAgent
+ ProfileAgent
+ DebugAgent
+ PracticeAgent
+ 动态重规划
```

---

## 3. 任务拓扑 Topology

即任务应该以什么形式执行。

```text
DIRECT：直接回答
SINGLE_AGENT_TOOL：单 Agent + 工具
SEQUENTIAL：多个 Agent 顺序执行
PARALLEL：多个 Agent 并行执行
PLANNER_EXECUTOR：规划—执行—评估—重规划
```

路由器最终不能只输出：

```json
{"intent": "practice_generation"}
```

而应该输出完整的结构化决策。

```json
{
  "task_family": "MULTI_TASK",
  "sub_tasks": [
    "chapter_summary",
    "practice_generation",
    "learning_path"
  ],
  "complexity": "C2",
  "topology": "SEQUENTIAL",
  "confidence": 0.93
}
```

---

# 四、L4：Agent 体系与选择机制

## 1. 将 Agent 分为三类

### 第一类：业务 Agent

直接完成学生任务。

| Agent | 职责 |
|---|---|
| TutorAgent | 概念解释、类比、分层讲解 |
| CoursewareQAAgent | 基于课件证据回答 |
| DebugAgent | 分析提交错误、错误签名和误区 |
| GuideCoachAgent | 提供启发式提示和解题脚手架 |
| ResourceDesignerAgent | 生成讲义、案例、活动等资源 |
| PracticeAgent | 生成微练习、变式题、巩固题 |
| MindMapAgent | 生成知识结构和概念关系 |
| PathPlannerAgent | 制定学习、补救和迁移路径 |

### 第二类：治理 Agent

不直接承担主要业务，而是保证系统可靠。

| Agent | 职责 |
|---|---|
| IntentRouterAgent | 识别任务和复杂度 |
| SupervisorAgent | 拆解复合任务、组织 Agent |
| GroundingCriticAgent | 检查课件证据和引用 |
| SafetyAgent | 检查答案泄露及安全问题 |
| ProfileAgent | 分析学习者状态，不直接写数据库 |

### 第三类：执行组件

这些最好是确定性程序，不应全部做成 LLM Agent。

```text
ToolRegistry
ToolPolicyEngine
SchemaValidator
AStarScorer
RetryManager
FallbackManager
StatePersistence
EventEmitter
```

---

## 2. Agent 选择评分

对于简单任务，可以直接规则路由。

对于存在多个候选 Agent 的请求，可采用能力评分：

```text
AgentScore =
意图匹配度
+ 上下文适配度
+ 所需工具覆盖度
+ 历史任务成功率
- 预计延迟
- 调用成本
- 安全风险
```

例如“解释我最新一次 WA”的候选 Agent：

| Agent | 意图匹配 | 提交数据能力 | 教学解释能力 | 综合 |
|---|---:|---:|---:|---:|
| TutorAgent | 中 | 低 | 高 | 中 |
| DebugAgent | 高 | 高 | 中 | 最高 |
| CoursewareQAAgent | 中 | 低 | 高 | 中 |

因此：

```text
primary_agent = DebugAgent
supporting_agent = TutorAgent
```

---

# 五、Agent 编排模式

百晓工坊不应对所有任务都使用同一种 Agent 工作流。

## 模式一：单 Agent 直接处理

适合简单概念问题。

```text
IntentRouter
→ TutorAgent
→ SchemaValidator
→ 返回
```

---

## 模式二：单 Agent + Tool Calling

适合需要真实业务数据的问题。

```text
IntentRouter
→ DebugAgent
→ get_latest_submission
→ get_judge_result
→ classify_error_signature
→ DebugAgent 生成诊断
→ SafetyAgent
→ 返回
```

---

## 模式三：顺序多 Agent

适合具有依赖关系的任务。

例如：

```text
“总结课件，然后根据总结出题。”
```

执行顺序：

```text
CoursewareRAG
→ ResourceDesignerAgent
→ PracticeAgent
→ GroundingCriticAgent
```

练习生成依赖前面的课件摘要，所以不能完全并行。

---

## 模式四：并行多 Agent

适合相互独立的子任务。

例如：

```text
“生成章节总结、思维导图和知识点清单。”
```

可以：

```text
CoursewareRAG
  ├─ ResourceDesignerAgent
  ├─ MindMapAgent
  └─ KCOutlineAgent
          ↓
      ResultMerger
```

---

## 模式五：Planner—Executor—Critic

适合复杂复合请求。

例如：

```text
“结合我之前的错误，帮我复习循环，生成资源、练习，并安排顺序。”
```

完整流程：

```text
SupervisorAgent 拆解任务
→ PathPlannerAgent 制定计划
→ 各专业 Agent 执行
→ Critic 检查
→ 根据结果调整计划
→ 返回阶段性学习方案
```

---

# 六、ToT 与 A* 在 Agent 路由中的正确位置

Tree of Thought 和 A* 不应应用于每次请求。

它们只用于：

```text
C3 复杂规划任务
多目标复合任务
存在多条合理 Agent 路径
一次选择会影响后续学习过程
```

## 1. Tree of Thought 生成候选 Agent 路由

例如学生说：

```text
“我循环学得不好，帮我安排复习。”
```

ToT 可以生成：

### 路径 A：诊断优先

```text
ProfileAgent
→ DebugAgent
→ ResourceDesignerAgent
→ PracticeAgent
→ PathPlannerAgent
```

### 路径 B：知识重构优先

```text
CoursewareQAAgent
→ MindMapAgent
→ PracticeAgent
→ PathPlannerAgent
```

### 路径 C：练习探测优先

```text
PracticeAgent
→ JudgeTool
→ DebugAgent
→ PathPlannerAgent
```

---

## 2. A* 选择最优 Agent 路径

对候选路径计算：

```text
f(P) = g(P) + h(P) + penalty(P)
```

其中：

### g(P)：已执行或预计执行成本

包括：

```text
Agent 调用次数
Tool 调用次数
LLM token 成本
预计延迟
学生交互轮数
```

### h(P)：距离学习目标的剩余估计

包括：

```text
KC 掌握度缺口
错误复发概率
缺少的学习证据
尚未完成的子任务
```

### penalty(P)：教育与安全惩罚

包括：

```text
答案泄露风险
缺乏课件证据
认知负荷过高
阶段跳跃
重复生成已有资源
学生挫败风险
```

最终不一定选择调用最少的路径，而是选择：

> **在学习效果、成本、认知负荷和安全之间综合最优的 Agent 路由链。**

---

# 七、Tool Calling 体系

百晓工坊的 Tool Calling 不应由 Agent 任意发明工具名，而应由统一 `ToolRegistry` 管理。

## 1. 工具分类

### 学习者状态工具

```text
get_learner_profile
get_kc_mastery
get_learning_memory
get_recent_learning_events
```

### 课件与知识工具

```text
courseware_rag_search
get_courseware_page
get_kc_definition
get_related_examples
```

### 代码与判题工具

```text
get_latest_submission
get_judge_result
get_testcase_summary
classify_error_signature
run_code_trace
```

### 资源工具

```text
create_handout_draft
create_practice_set
create_mindmap
create_review_card
create_learning_path
```

### 状态写入工具

```text
save_generated_resource
save_learning_plan
record_learning_event
update_task_status
```

写工具必须比读工具受到更严格的控制。

---

## 2. 工具元数据

每个工具都应该注册：

```json
{
  "tool_name": "get_latest_submission",
  "capability": "submission.read",
  "side_effect": "none",
  "allowed_agents": ["DebugAgent", "GuideCoachAgent"],
  "required_context": ["student_id", "problem_id"],
  "timeout_ms": 1500,
  "cost_level": "low",
  "sensitive_data": true,
  "result_schema": "SubmissionSummary"
}
```

这样 Tool Router 可以基于能力选择，而不是只靠模型输出工具名字。

---

## 3. 工具调用策略

### 规则一：最小必要调用

如果已有上下文中包含最新提交，就不要再次调用数据库。

### 规则二：先读后写

生成资源时：

```text
先检索课件和画像
→ 生成草稿
→ 审查
→ 最后保存
```

### 规则三：副作用隔离

涉及以下操作时应由后端确定性逻辑执行：

```text
修改掌握度
发布正式资源
改变任务状态
写入教师统计
```

Agent 只能提出建议，不能自行修改关键数据。

### 规则四：有限循环

建议：

```text
每个 Agent 最多 4 次工具调用
整个请求最多 8 次
同一工具最多重复调用 2 次
Critic 修复最多 1 次
```

---

# 八、微调模型在路由中的正确作用

现有的 Qwen3.5 微调模型最适合负责：

```text
意图路由 JSON
任务拆解 JSON
Agent 路由计划 JSON
资源结构 JSON
Critic 审查 JSON
学习路径 JSON
```

不适合直接承担：

```text
开放式 Tool Calling
长流式聊天
自由 ReAct 循环
所有实时低延迟请求
```

因此最佳架构是：

```text
微调 Qwen：
负责生成稳定、可解析的 RouteDecision 和 TaskPlan

确定性 Graph：
负责真正选择节点、校验工具权限、执行工具

支持 Function Calling 的原 Provider：
处理需要模型参与的工具参数生成

Guard：
负责 JSON、Grounding 和 Safety 审查
```

这比直接让微调模型自由调用工具更稳定。

---

# 九、LangGraph 节点设计

建议构建一个独立的 `WorkshopGraph`。

```text
workshop_ingest
  ↓
build_context
  ↓
policy_gate
  ↓
intent_router
  ↓
complexity_router
  ├─ simple_dispatch
  ├─ tool_dispatch
  ├─ multi_agent_planner
  └─ clarify_or_fallback
        ↓
agent_executor
        ↓
tool_policy_check
        ↓
tool_executor
        ↓
result_assembler
        ↓
grounding_critic
        ↓
safety_critic
        ↓
schema_validator
  ├─ pass → persist_and_emit
  └─ fail → bounded_repair
               ├─ pass → persist_and_emit
               └─ fail → fallback_response
```

## 核心状态

```json
{
  "request": {},
  "learner_context": {},
  "route_decision": {},
  "task_plan": [],
  "active_agent": "",
  "tool_calls": [],
  "intermediate_results": [],
  "evidence_pack": [],
  "critic_results": {},
  "final_card": {},
  "retry_count": 0
}
```

---

# 十、统一路由协议

建议所有路由器统一输出：

```json
{
  "route_id": "route-20260722-001",

  "task": {
    "family": "MULTI_TASK",
    "sub_intents": [
      "chapter_summary",
      "practice_generation",
      "learning_path"
    ],
    "complexity": "C2",
    "confidence": 0.93
  },

  "orchestration": {
    "mode": "SEQUENTIAL",
    "primary_agent": "PathPlannerAgent",
    "supporting_agents": [
      "ResourceDesignerAgent",
      "PracticeAgent",
      "GroundingCriticAgent"
    ]
  },

  "tools": {
    "required": [
      "courseware_rag_search",
      "get_kc_mastery",
      "get_recent_submissions"
    ],
    "optional": [
      "get_learning_memory"
    ],
    "forbidden": [
      "publish_courseware"
    ]
  },

  "policies": {
    "grounding_required": true,
    "safety_check_required": true,
    "answer_leak_risk": "medium",
    "max_tool_calls": 8,
    "max_replans": 1
  },

  "output": {
    "card_type": "compound_learning_support_card",
    "schema_version": "v1"
  },

  "fallback": {
    "low_confidence": "ASK_CLARIFY",
    "rag_empty": "COMMON_KNOWLEDGE_WITH_DISCLOSURE",
    "tool_failure": "PARTIAL_RESULT",
    "model_failure": "FALLBACK_PROVIDER"
  }
}
```

---

# 十一、典型完整案例

学生输入：

```text
“我循环这一章学得很乱，先给我总结一下，再根据我以前犯的错出几道题，并告诉我先做哪一道。”
```

## 第一步：上下文构建

系统读取：

```text
当前章节：循环结构
for_loop 掌握度：0.51
while_loop 掌握度：0.62
range_boundary 掌握度：0.30
历史错误：off_by_one 出现 4 次
最近练习：2 次 WA，1 次 AC
```

## 第二步：路由识别

```json
{
  "task_family": "MULTI_TASK",
  "sub_intents": [
    "resource_generation",
    "practice_generation",
    "path_planning"
  ],
  "complexity": "C3",
  "topology": "PLANNER_EXECUTOR"
}
```

## 第三步：Supervisor 拆解

```text
T1：检索循环章节课件证据
T2：生成个性化章节总结
T3：分析历史错误
T4：生成针对性练习
T5：规划练习顺序
T6：审查证据与答案泄露
```

## 第四步：Agent 编排

```text
CoursewareRAGTool ─────────────┐
                              ├→ ResourceDesignerAgent
ProfileTool + ErrorHistoryTool ┘

ErrorHistoryTool
→ DebugAgent
→ 错误类型摘要

章节总结 + 错误摘要
→ PracticeAgent
→ 生成练习

KC 掌握度 + 练习难度
→ PathPlannerAgent
→ 排列学习顺序

全部结果
→ GroundingCriticAgent
→ SafetyAgent
→ 前端卡片
```

## 第五步：最终展示

百晓工坊返回的不是一大段文本，而是结构化卡片：

```text
循环章节个性化复习方案

你当前的主要薄弱点：
range 右边界、循环次数判断

第一步：阅读 3 分钟总结
第二步：完成边界判断微练习
第三步：完成一道 range 纠错题
第四步：完成原题变式
第五步：通过后进行迁移练习
```

每一步都可以触发：

```text
继续
换一种解释
查看课件证据
生成更简单练习
我已经掌握
```

学生完成后，系统更新画像并决定是否需要重规划。

---

# 十二、失败恢复机制

## 1. 路由置信度不足

```text
confidence < 0.55
```

不直接进入复杂 Agent，而是：

```text
根据当前场景给出有限澄清选项
```

例如：

```text
你希望我：
A. 解释代码错误
B. 讲解相关知识点
C. 生成练习
```

## 2. RAG 没有证据

允许：

```text
使用 common_knowledge 回答
```

但必须明确：

```text
“以下解释未直接引用当前课程课件。”
```

不得伪造页码。

## 3. Tool 调用失败

返回已有的部分结果，而不是整个请求失败。

## 4. 本地微调模型失败

自动切换原 provider。

## 5. Critic 不通过

只允许一次修复：

```text
初稿
→ Critic
→ Repair
→ 再校验
```

仍不通过则返回安全模板。

---

# 十三、评估体系

不能只评估“答案看起来好不好”，还要分别评估路由、工具、输出和教学效果。

## 1. 路由指标

```text
Intent Accuracy
Top-1 Agent Accuracy
Top-2 Agent Recall
复杂度识别准确率
多 Agent 拓扑选择准确率
低置信度识别率
```

## 2. Tool Calling 指标

```text
Tool Precision：调用的工具是否必要
Tool Recall：必要工具是否遗漏
无效工具调用率
重复工具调用率
工具参数合法率
副作用调用违规率
```

## 3. 输出指标

```text
JSON valid rate
Grounding safe rate
Forbidden leak rate
Schema pass rate
Fallback rate
```

## 4. 系统指标

```text
P50 / P95 延迟
平均 Agent 数量
平均 Tool Calling 数量
单请求 token 成本
超时率
重规划率
```

## 5. 教学效果指标

```text
重复错误率是否下降
学生下一次提交是否改善
微测通过率
KC 掌握度变化
路径完成率
学生主动放弃率
教师人工修正率
```

---

# 十四、分阶段实施路线

## 第一阶段：稳定路由 MVP

先实现：

```text
规则门控
+ IntentRouterAgent
+ 单 Agent 路由
+ 固定 Tool Allowlist
+ Grounding / Safety Guard
```

覆盖：

```text
答疑
调试
资源生成
练习生成
学习路径
```

---

## 第二阶段：多 Agent 编排

增加：

```text
SupervisorAgent
顺序执行
并行执行
统一 RouteDecision
统一 TaskPlan
中间结果持久化
```

支持：

```text
总结 + 练习
诊断 + 补救
资源 + 路径
知识图谱 + 微测
```

---

## 第三阶段：个性化路由

将以下特征加入 Agent 选择：

```text
KC 掌握度
历史错误
认知负荷
学习偏好
当前挫败程度
最近资源使用情况
```

同一个问题，不同学生可路由到不同策略。

---

## 第四阶段：ToT + A* 复杂规划

只在 C3 请求中启用：

```text
ToT 生成 3 条候选 Agent 路径
A* 对路径评分
选择最优路径
执行后根据结果进行一次重规划
```

---

# 十五、最终架构总结

百晓工坊最合理的技术定位是：

> **一个基于学习上下文感知的多 Agent 路由与工具编排中枢。**

其核心链路是：

```text
学生请求
→ 上下文构建
→ 规则门控
→ 意图与复杂度识别
→ Agent 路由
→ Tool Calling
→ 多 Agent 编排
→ Grounding / Safety / Schema 审查
→ 结构化卡片
→ 学习状态更新
```

其中：

```text
简单任务：规则 + 单 Agent
上下文任务：Agent + Tool
复合任务：Supervisor + 多 Agent
复杂规划：ToT 生成候选路线，A* 选择最优路线
```

最核心的创新不是“用了很多 Agent”，而是：

> **根据学生当前问题、学习阶段、知识掌握度、历史提交和课件证据，动态决定由哪个 Agent、以什么编排方式、调用哪些工具完成任务，并将结果继续反馈到学生画像与学习路径中。**
