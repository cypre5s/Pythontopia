# 工坊重构 + 侧栏 marvis 实时活动 + 收尾项 — 详细计划

- 状态：草案（待用户过目后开工）
- 日期：2026-06-04（R-14）
- 来源：用户在浏览器深度试用后提出的整改清单
- 关联：`docs/plans/2026-06-04-bilibili-cards-and-agent-studio-plan.md`（B站/工作室/VIDEO 意图）

---

## 0. 背景与已完成（本轮已修，按严重度）

| 项 | 状态 | 做法 |
|---|---|---|
| NPC 对话卡住 (S1) | ✅ | `openWithTree` 只 await 本地 meta+dialog 立即显示；context 后台异步；`fetchJson` 加 8s 超时 |
| 切页浮层残留 (S1) | ✅ | `router.afterEach` 关闭对话 store（DialogTree 全局挂载，原不随路由卸载） |
| 看课页"网络超时"toast (S1) | ✅ | `http.ts` 加 `suppressNetworkToast`；文生图/B站/课程视频列表等后台请求 opt-out 全局 toast，各自降级 |
| 看课页精简 (S2) | ✅ | 去掉 AI 图解（慢+凑数），保留 视频/小节/B站精选/学习闭环/问答 |
| Problems 表格 | ✅ | 单元格居中 + 标题去下划线（悬停变色） |

**本计划覆盖剩余项**：DeepSeek 连通、工坊重构（布局 + 答案优先 + 不强制@KC）、侧栏 marvis 实时活动、PY43 UI 隐藏。

---

## 1. 用户拍板的决策（前一轮）

- **PY43**：仅 UI 隐藏前缀（代码/DB 不动）。
- **marvis**：常驻**侧栏**面板；搜索时像 Claude **实时展示"正在做什么"**（参考 Alethicode 课件问答）。
- **不强制 @KC**：直接答学生问题；**优先限定课件/PPT/学生自己的学习资源**；检索不到再 LLM 输出；**工具按需自动调用并嵌入回答**。

---

## 2. Alethicode 课件问答参考（marvis 实时活动来源）

`Alethicode/.../languagepack/agents/QaAgentProgressBar.vue`：
- 输入 `activeNode`（当前 SSE 节点）+ `elapsedMs`。
- `NODE_GROUPS`：node → 分组（planner / retrieval / synthesizer / critic / diagram / done）。
- `HUMAN_LABELS`：分组 → 人话（"正在规划检索策略 / 正在并行检索课件 / 正在合成答案 / 正在审核与改写 / 正在生成示意图 / 正在收尾"）。
- 视觉：spinner + 跳动标签 + 三点 + "已等 Ns"。

**Pythontopia 已有基础**：`useSseStream` 暴露 `traces`（node/status/elapsed_ms）+ 新增 `plan`（工位清单）。marvis 侧栏直接消费即可，无需新管线。

---

## 3. 详细设计

### 3.1 工坊布局重构（ws_layout）
目标：聊天占全局、书房默认收起、去掉"按KC/标题检索"。

- `WorkshopPage.vue`：`drawerOpen` 默认 `false`（书房默认收起，仅留一条可点开的竖把手）；聊天 pane `flex:1` 占满；书房展开时再 360px。
- 右栏改为**两段**：上=**marvis 面板**（常驻，见 3.4）；下=**我的书房**（默认收起为窄条，点开浮出/推入）。
- `StudioDrawer.vue`：移除"按 KC / 标题 / 类型检索书房…"搜索框（`searchbox`）及其过滤逻辑；书房只保留"待复习"+资源列表（点开时）。
- 移动端：marvis + 书房折叠到聊天下方可展开区。

### 3.2 答案优先 + 不强制 @KC（ws_nokc）
目标：直接答学生问题；优先课件/PPT/自己资源；检索不到再 LLM；工具按需自动调用并嵌入回答。

现状：`IntentRouter` 选 1 个工具；资源类无 KC 直接报错"需先选知识点"。

改造（分两步，先保证可用，再做 agentic）：
- **Step A（去强制 + 默认答疑）**：
  - 工坊默认走 **ASK（导师 RAG 答疑）**，不再因缺 KC 报错。
  - `IntentRouter`/`WorkshopChat`：当判定为资源类但**无 KC** 时，不报错——
    - 若能从消息/当前章节兜底到 KC，则照常生成；
    - 否则**降级为 ASK**（先把问题答了），并在答案末尾附"要不要我据此生成讲义/练习？"的轻量工具建议（用户点了再带 KC 生成）。
  - 导师答疑的 grounding 已限定课件；**PPT/学生自己资源**纳入检索范围见 Step B。
- **Step B（答案优先 + 自动工具嵌入，agentic）**：
  - 检索范围扩展：课件 RAG（已有）+ 学生自己的工坊资源（书房，按 KC/标题本地或后端检索）+ PPT（HANDOUT 资源）。
  - 答案合成：优先用检索到的证据作答（标注来源）；**检索不到** → 明确"以下为通识知识（非课件）"再给 LLM 答案（沿用现有 grounding 标注）。
  - **自动工具嵌入**：导师在答完后，按问题类型自动附带工具产物卡片（参考 Alethicode genui / GenerativeCardSlot）：
    - 想看视频 → 内联 B站卡片（复用 BilibiliVideoCards）；
    - 想练 → 练习卡/去训练场；
    - 想要讲义/导图 → 一键生成入口。
  - 实现位：优先前端编排（WorkshopChat 在 ASK 答案后按意图副信号附卡），避免大改 graph；后端/graph 仅在必要时加"建议工具"字段。

> 范围控制：Step A 必做（解决"强制@KC"痛点）；Step B 的"自动工具嵌入"做到**B站视频 + 生成入口**即可，复杂 genui 留后续。

### 3.3 PY43 UI 隐藏（py43_hide）
仅 UI，代码/DB 不动。已有 `kcDisplayName(code)→名` 与 `humanizeKcText(text)`。

- **提及菜单**（`CoursewareMentionMenu` / `useCoursewareMention`）：选项显示 `kcDisplayName`（知识点中文名），不显示 `PY43-KC-031` 原码；必要处用 `KC-031`（去 `PY43-` 前缀）。
- **学习总管计划文本**（GuideOverlay `step.detail`/`step.title`）：渲染时过 `humanizeKcText`，把内联 `PY43-KC-001` 替换为「中文名」。
- 巡检其余直显原码处（题库 title 提示、KC chips 等）统一走 `kcDisplayName`/`humanizeKcText`。
- 新增 `kcCodeShort(code)`（`PY43-KC-031`→`KC-031`）工具，用于确需展示编码处。

### 3.4 侧栏 marvis 实时活动（marvis_sidebar，S3）
常驻工坊右栏上段；空闲展示智能体待命，处理时实时展示"正在做什么"。

- 新组件 `MarvisPanel.vue`：
  - **空闲态**：像素"智能体事务所"缩略——馆员/设计师/审查官/校对官/安全员（+导师）头像 + "待命中"。
  - **活动态**（由 `useSseStream` 的 `status`/`plan`/`traces` 驱动）：
    - 顶部一行 **实时活动条**（移植 Alethicode `QaAgentProgressBar` 思路）：当前节点 → 人话标签（"正在检索课件 / 正在合成答案 / 正在引用审查 / 正在安全审查 / 正在找 B站视频…"）+ spinner + "已等 Ns"。
    - 下面**工位列表**：按 `plan.nodes` 点亮（pending→active→done ✓/retry↻/fail✗），复用现 `AgentOfficeScene` 的工位→sprite 映射。
  - 节点→人话标签映射（Pythontopia 版）：`evidence_fetcher`=正在检索课件证据；`*Agent.attempt*`=正在生成{资源名}；`grounding_critic`=正在核对课本引用；`hallucination_detector`=正在防幻觉核查；`safety_critic`=正在安全审查；`TutorAgent`/`image_understanding`（答疑）=正在作答/看图。
- 复用而非重写：`AgentOfficeScene` 的"过场动画"保留为生成时的展开态；marvis 侧栏是**常驻精简版**（活动条 + 工位状态），二者共享 `useSseStream` 状态。
- 文案/视觉：星露谷像素风（沿用 tokens + NPC sprite）。

### 3.5 DeepSeek 连通 + 条件代理（deepseek，并入执行A）
- `deploy/.env` 已配 `openai_compat` + `PYT_OPENAI_*`（DeepSeek）。当前宿主 graph 跑的是 `dryrun`，故"没通"。
- 执行 A（重启栈）时：宿主 graph 用 `PYT_LLM_PROVIDER=openai_compat` + DeepSeek key + `HTTPS_PROXY=http://127.0.0.1:7892`（**有代理走 7892、无则不设即直连**，靠 httpx `trust_env`）+ `NO_PROXY=127.0.0.1,localhost`（内网 RAG 直连）。
- 验证：`/api/intent/route`、`/api/tutor/ask`、`/api/orchestrator/turn` 返回真实 LLM 结果（非 dry-run 占位）。
- 若 `deepseek-v4-pro` 模型名报错（model not found）→ 改为有效模型（如 `deepseek-chat`）并回填 `.env`，向用户确认。

---

## 4. 验收标准

### 工坊布局
- [ ] 进入 `/workshop`：聊天占满主区；书房默认收起（窄把手），点开才展开。
- [ ] 无"按 KC / 标题 / 类型检索书房"搜索框。

### 答案优先 / 不强制 @KC
- [ ] 直接发"for 循环怎么用？"不带 @KC：得到**课件 grounded 答案**（有证据页标注），不报"需先选知识点"。
- [ ] 课件检索不到的通识问题：答案标注"通识知识（非课件）"。
- [ ] 资源类（如"讲义"）无 KC：不报错——能兜底则生成，否则先答疑 + 附"生成讲义"入口。
- [ ] 答案按需自动嵌入工具卡（至少：想看视频→B站卡片；生成入口可点）。

### PY43 UI 隐藏
- [ ] 提及菜单、学习总管计划、题库/各处**不再出现 `PY43-` 原码**（显示中文名或 `KC-031`）。
- [ ] 代码/DB/种子不变（grep `PY43` 在 src 外的数据/迁移仍在）。

### marvis 侧栏
- [ ] 工坊右栏常驻 marvis 面板；空闲显示智能体待命。
- [ ] 发起答疑/生成时：实时活动条按当前节点显示人话标签 + spinner + 已等秒数；工位随 `plan`/`phase` 点亮。
- [ ] 答疑（ASK）与资源生成都驱动 marvis（不只生成）。

### DeepSeek
- [ ] 重启后 `/api/intent/route` 等返回真实 LLM（学习总管不再显示"本地 dry-run"）。
- [ ] 无代理环境（不设 HTTPS_PROXY）时不强行走 7892（条件代理）。

### 回归
- [ ] `npm run typecheck`/`lint`/`test` 全绿；`pytest -q`、后端编译全绿。
- [ ] Tutor/Orchestrator/既有 SSE 不回归；NPC 对话/切页/看课页修复保持。

---

## 5. 执行顺序（按严重度 + 依赖）
1. **PY43 UI 隐藏**（py43_hide）——小、独立、用户高频可见。
2. **工坊布局**（ws_layout）——全宽聊天 + 书房收起 + 去搜索。
3. **答案优先 Step A**（ws_nokc 去强制 + 默认答疑兜底）。
4. **marvis 侧栏**（marvis_sidebar，移植 QaAgentProgressBar 思路）。
5. **答案优先 Step B**（自动工具嵌入：B站卡片 + 生成入口）。
6. **执行 A**：重启全栈（openai_compat + 条件代理）→ 端到端验证全部 + DeepSeek。

每步：改完即 `typecheck`/`lint` + 相关单测；第 6 步统一全量回归 + 浏览器实跑。

---

## 6. 风险
- R1 工坊"答案优先"改交互模型：分 Step A/B，先去强制不破坏既有 ASK/生成；Step B 优先前端编排，少动 graph。
- R2 marvis 与 AgentOfficeScene 重复：共享 `useSseStream`，侧栏=常驻精简、过场=展开，不重复管线。
- R3 PY43 仅 UI：生成文本（LLM/默认）里仍可能含原码 → 用 `humanizeKcText` 渲染期净化兜底。
- R4 DeepSeek 模型名/代理：执行 A 时实测，不对就改 `.env` 并回报。
- R5 离线/无网演示：DeepSeek 走代理；无代理回退 dryrun（条件代理保证可降级）。
