# ADR-002 · 代码剧场（Code Theater）Android 端暂缓与服务端 trace 路线

**状态**：已决定（2026-06-18 / Android-RN parity sweep）
**作者**：Web→Android 平价补齐评估
**取代**：无
**关联**：`ADR-001-tech-selection.md`（决策 6：Python 执行）、`frontend/src/services/codeTheater/*`、`frontend/src/pages/CodeTheaterPage.vue`

## 1. 背景

Web 端 `/code-theater`（机关广场）是「只有游戏能做」的具身执行可视化：学生写真实 Python →
逐步看「执行过程」（赋值/循环/分支/调用/异常）+ 用「世界条件」给可见反馈。其核心是
`services/codeTheater/trace/runTrace.ts`：用 `ast` + `sys.settrace` 的 **Python 插桩 harness**，
经 **Pyodide（WASM 内 CPython）** 执行学生代码，从 sentinel 包裹的 stdout 解析出结构化 `Trace`，
再由纯函数 `buildTrace` / `traceToRenderCommands` / `checkQuestWorldGoal` 整形与判定。

整条管线**完全在客户端**，**无任何后端端点**参与。

## 2. 约束冲突

| 路径 | 可行性 | 依据 |
| --- | --- | --- |
| 设备内跑 Pyodide / WASM CPython | ❌ 禁止 | ADR-001 决策 6、AGENTS.md「不在 Android 端运行 Python」 |
| 设备内 Chaquopy / on-device CPython | ❌ 禁止（Beta 首发） | 同上 |
| 复用现有后端端点产出 trace | ❌ 不存在 | 后端无 code-theater / 执行 trace 端点（仅 tutor-workflow card 与 LangGraph observability） |
| 端侧伪造/降级 trace | ❌ 禁止 | AGENTS.md「不做纯前端假数据冒充真实业务数据」「不做无教育目的的装饰系统」 |
| **服务端跑 harness 产出 trace** | ✅ 可行（未来） | harness 是自洽 Python 源码，可在后端判题 sandbox 内执行 |

结论：代码剧场**无法按 Web 原样移植到 Android**，且不应以假数据冒充。本轮 parity sweep
**暂缓** Android 代码剧场，优先补齐其它可干净移植的缺口（feynman / problems / board /
teacher-review / chapter-quiz / 装饰页）。

## 3. 决策

1. 本轮**不**在 Android 实现 `/code-theater`。
2. 不引入任何端侧 Python / Pyodide / Chaquopy（坚守 ADR-001 决策 6）。
3. 不以合成 trace 冒充执行（坚守 AGENTS.md 防幻觉/不造假约束）。
4. 记录**唯一受认可的未来落地路线**（见 §4），待用户决定是否投入。

## 4. 未来落地路线（服务端 trace）

当决定投入时，按「Python 在服务端、客户端只渲染」实现，全程不破坏 ADR-001 约束：

1. **后端**：新增 `POST /api/code-theater/trace`（学生代码 → 在既有判题 sandbox 内运行
   `frontend/src/services/codeTheater/trace/harness.ts` 等价的 Python 插桩 harness →
   解析 sentinel 块 → 返回结构化 `Trace` JSON）。须复用现有沙箱安全边界与步数预算（防死循环），
   并配 pass/fail/runtime-error 三类用例（判题路径敏感，遵守 AGENTS.md 判题验证约束）。
2. **共享纯逻辑移植 Kotlin**：`quests/*`（任务目录）、`worldConditionChecker`、
   `traceToRenderCommands`（纯函数，可离线单测，与 Web 逐例对齐）。
3. **Android**：`:feature:codetheater`（任务选择 + 代码编辑 + 调用端点 + 执行步骤列表 +
   世界条件命中可视化）；可选 LibGDX 舞台装饰层（傀儡走代码），渐进增强、缺省纯 Compose。
4. **红线沿用**：trace 仅用于「看执行/学习预览」，掌握度唯一权威仍是服务端 `submission` 判题。

## 5. 影响

- 教学闭环不缺：代码剧场是「看执行」的增强可视化，非掌握度判定路径；其缺位不影响
  「看→学→练→测」主链（练/测仍由 arena/mainline + 服务端判题承载）。
- 可回滚/可推进：本 ADR 不写任何代码，纯记录决策；§4 路线随时可单独立项。
