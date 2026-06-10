# Pythontopia R-13 优化执行计划（最弱智 AI 友好版）

> 本文件是 R-13 「其镇复兴」主线 + AI 出题 Boss 系统的**端到端执行说明书**。
> 假定执行者是「最弱智的 AI」：每一步给出确切的文件路径、命令、期望输出、回退方案。
> **绝对不要跳步骤、不要自己脑补。** 不确定时回到 §0 阅读约束。

---

## §-1 用户原话痛点清单（必须全部 100% 解决，不允许跳过任何一项）

> 本节按用户**原话**记录所有反馈，每项后面是「期望状态」和「当前完成度」。
> AI 不允许只解决 80% 然后说「下会话再做」。每一项必须做到 100% 或者
> 明确说明物理上做不到的原因（如缺失资源文件）。
资源工坊这个界面不够星露谷，同时太高了，学生要上下滑动浏览器看，太麻烦了。此外，优化，为什么要解锁了KC才能用，用户可以无限制的用。对了，进去以后的主页，会有传送到所有分地图的快捷通道。
### A. HUD / 按键排布（共 6 条）

| ID | 用户原话 | 期望状态 | 完成度 |
| -- | -- | -- | -- |
| A1 | 按键排布太乱了，请你合理收容归纳 | 9 按钮单行 + 不被截 + 视觉对齐 | 进行中 |
| A2 | 交互栏没有完全被显示 | 9 按钮完整显示，不溢出 viewport | 进行中 |
| A3 | 这些按下对应的字母没有正确的交互 | 按钮 icon 字母 = 真实快捷键 | 进行中 |
| A4 | 上下有两套快捷键，只保留下面那一套 | OperationHints 隐藏，只有 HUD 一套提示 | 进行中 |
| A5 | 之前不是说好要自适应浏览器屏幕大小的吗？怎么还没实现 | 5 个断点（≥1280 / ≥1024 / ≥640 / ≥480 / <480）全部测试 | 进行中 |
| A6 | 我的小地图呢？ | 右上角永远可见 minimap（含玩家点 / 出口箭头 / 地图名） | 进行中 |

### B. 对话框 DialogModal（共 4 条）

| ID | 用户原话 | 期望状态 | 完成度 |
| -- | -- | -- | -- |
| B1 | 头像范围太大了，截取了不该截取的 | portrait 只显示头部第一帧 | 进行中 |
| B2 | 字是白色的，根本看不清 | 文字必须深色 + 与牛皮纸高对比 | 进行中 |
| B3 | 字没有严格限制在牛皮纸的白色范围内 | 文字 padding 在牛皮纸内，不溢出 | 进行中 |
| B4 | 还是没有解决之前说的头像，白字，以及范围的问题 | 必须真正修复后让用户硬刷新看到 | 进行中 |

### C. 移动 / 游戏交互（共 4 条）

| ID | 用户原话 | 期望状态 | 完成度 |
| -- | -- | -- | -- |
| C1 | 为什么键盘交互没有反应 | WASD 移动 + F 与 NPC 交互全部生效 | 进行中 |
| C2 | 我靠近 NPC 无法交互啊，你看看咋回事 | 走到 NPC 旁边 1-2 tile 内，hint 出现 + 按 F 弹对话 | 进行中 |
| C3 | 优化移动逻辑，先改变方向，再向对应的方向移动 | turn-then-walk 星露谷式 | 未修，依旧先移动再转向 |
| C4 | 地图太朴素了，最好可以有些小装饰，包括但不限于小花，小石头 | scatterDecoration 密度提升 + 多色花/灌木/石头/树桩 | 待做 |

### D. 课程 LibraryPage（共 2 条）

| ID | 用户原话 | 期望状态 | 完成度 |
| -- | -- | -- | -- |
| D1 | 课程没有正确显示 PPT/PDF 界面，而是只显示了文本 | 必须渲染原 PDF 页（pdfjs-dist + 服务端 PDF 代理） | 待做 |
| D2 | 直接限制 pdf/ppt 的原页，不要只显示字，会损失很多信息 | 同上 | 待做 |
| D3 | 边框可以自由拉动 | 中央 panel + 侧栏可拖拽 resize | 部分完成 |

### E. 主线 + AI 出题 Boss（用户决策）

| ID | 用户原话 | 期望状态 |
| -- | -- | -- |
| E1 | Boss AI 出题正确性如何确保 | 5 重审查链（已在 §2 详写） |
| E2 | 4 阶段越来越难 | difficulty 1→5 自适应（已在 §2.5） |
| E3 | 先写下详细的优化计划，确保最弱智的 AI 也能照着执行 | 本文件 §5 Phase 1-8 |

---

## §-0.5 解决态度声明

- **不允许**说"留下会话""标记 P2""暂未实现"任何用户报过的问题。
- **不允许**用 v-if=false 隐藏组件代替真实修复。
- **不允许**只跑 typecheck 不在浏览器实测；每改完一项必须 vite restart + 截图自检。
- **必须**为每一项写一个或多个新 spec 测试，避免回归。
- 如果某项物理上做不到（例：课程包真的没 PDF 文件），必须给出**完整 alternative plan** 并立即开始执行（例：找 PDF 源头 / 接 pdf.js stub / 服务端补端点）。
- **必须**让用户硬刷新后看到改动；如果 HMR 不刷，强制 kill vite + restart。

---

## 0. 总体约束（执行前必读）

| 编号 | 约束 | 违反后果 |
| -- | -- | -- |
| 0.1 | 仓库根 `/home/cypress/Pythontopia/`，不要在别处创建文件 | 部署时找不到 |
| 0.2 | 前端 TypeScript strict + `noUncheckedIndexedAccess` + `exactOptionalPropertyTypes` | 编译失败 |
| 0.3 | 前端业务层禁止 `import 'phaser'`，必须经 `src/game/adapters/*` | ESLint 0 error 守门 |
| 0.4 | 前端业务层禁止 `any` | ESLint 0 error |
| 0.5 | 任何 DTO 变更前先查后端 `backend/src/main/java/com/pythontopia/**` 对齐 | 字段错位见 R-12 W4 经验 |
| 0.6 | 每改一个步骤都跑 `npm run typecheck` + `npx vitest run 涉及的 spec`，FAIL 立即停 | 雪崩 |
| 0.7 | 每完成一个 §标记的章节做一次 commit；提交信息按本文件给的模板 | 历史可回溯 |
| 0.8 | 网络代理 `http://127.0.0.1:7892`；`npm install` 前 `export npm_config_proxy=$_` `npm_config_https_proxy=$_` | install 卡住 |
| 0.9 | 后端真启动需要 docker `pgvector/pgvector:pg16` 在 15432，用户 `pytopia`/密码 `pytopia_dev_password` | Flyway 失败 |
| 0.10 | LLM 出题必须走 `services/pythontopia-graph`（pythontopia-graph 端口 18091），不要直接调 OpenAI | 防越权 |
| 0.11 | 所有"假数据"必须明确标 `// PLACEHOLDER`，不能伪装真业务数据 | 见 AGENTS.md「不做纯前端假数据」 |
| 0.12 | 后端测试库与生产库**必须隔离**（用 H2 in-memory 或 testcontainers），不能污染生产 DB | 数据损坏不可恢复 |

---

## 1. 整体目标（One Liner）

> **「学生扮演镇长 Py 新招的实习生，跟 7 章 Python 知识修复 5 块荒废区域，每章末打一场 4 阶段 AI 出题的『Bug 大梦魇』Boss。」**

### 1.1 数字目标

- **主线长度**：7 大章 × 5 子任务 = **35 主线任务**；每章末 1 boss = **7 boss 战**
- **节奏**：1 学期 14 周 → 每周 1 主线日 + 多支线日；总时长 4-6 小时实玩 + 课外
- **完成率目标**：主线 60% / 7 boss 全胜 30%（前 1 个月 Beta 数据）
- **AI 出题正确率目标**：≥ 95%（5 重审查链兜底）

### 1.2 主线骨架（7 章 × 5 任务 = 35）

| 章 | 课程 KC 段 | 复兴区域 | 5 子任务示例 | Boss |
| -- | -- | -- | -- | -- |
| 1 入门 | PY43-KC-001..005（变量/print/input） | town_hub 喷泉广场 | ①扫地（hello world）②找 mayor_py（首次对话）③借灯（变量声明）④打水（input/print）⑤喂鸽（注释） | NameError Boss |
| 2 数据类型 | KC-006..015（int/float/str/list/dict） | farm_north 入门 1 田 | ①翻土（list）②播种（dict）③除草（str slice）④称重（int/float）⑤分拣（type 转换） | TypeError Boss |
| 3 控制流 | KC-016..025（if/elif/else） | mine_south 入口 + 第 1 层 | ①点灯（if）②走迷宫（elif 链）③开宝箱（嵌套）④识别陷阱（and/or/not）⑤跳跃逻辑 | ConditionBug Boss |
| 4 循环 | KC-026..035（for/while/break） | campus_east 图书馆 + 教室 | ①翻书（for）②收作业（while）③拒绝重复（break）④跳过坏页（continue）⑤打印一周 | LoopHangBoss |
| 5 函数 | KC-036..045（def/return/默认参数） | port_west 商船 | ①写问候函数 ②接单计价 ③货物盘点（嵌套）④折扣计算（默认参）⑤打包发货 | RecursionBoss |
| 6 类 | KC-046..055（class/self/继承） | campus_east 工坊 | ①设计学生类 ②添加方法 ③继承教师类 ④覆盖方法 ⑤工厂模式 | InheritanceBoss |
| 7 模块/IO/异常 | KC-056..065 | 全镇毕业典礼 | ①打开课件文件 ②读取 JSON 配置 ③异常兜底 ④导入 random ⑤毕业作业 | SyntheticBoss |

### 1.3 进度可视化（玩家看到的）

- **HUD 顶部新增「主线进度 chip」**：`第 X 章 · 区域名 (i/5 主线任务)`
- **新键 `Q`**：打开主线面板，列出 7 章树 + 每章 5 任务 + 完成状态 + 下一步导航
- **每章 boss 通关后**：解锁该章 5 子任务奖励（map / skill / npc / storyboard / outfit / title / boss_replay 全套）
- **进度持久化**：写入 `save_slot.snapshot` JSONB（向后兼容，无需新表）

---

## 2. AI 出题正确性保障系统（核心难题，写最详）

### 2.1 为什么 AI 出题难

| 风险 | 案例 | 影响 |
| -- | -- | -- |
| 答非所问 | KC 是 `for 循环`，AI 出了 `if` 题 | 教学失效 |
| 超纲 | 在第 2 章出 `decorator` 题 | 学生挫败 |
| 测试样例错 | input/output 写反 | 学生永远过不去 |
| 解法错 | 出题时 reference solution 跑不通 | 评分崩溃 |
| 表述歧义 | 「打印列表」没说格式 | 多解都对，判错 |
| 抄袭网络 | 直接复制 LeetCode 题 | 版权 + 难度不匹配 |
| 答案泄露 | 题面包含完整解 | 学生抄答案 |
| 偏见/敏感 | 引入歧视语 | 教学事故 |

### 2.2 五重审查链（必须全部通过才算合格题）

```
LLM 出题 (PracticeAgent)
  ↓
1. 静态结构校验 (StructValidator)
  ↓
2. 单元测试自洽 (SelfTest)
  ↓
3. KC 引用证据 (GroundingCritic)
  ↓
4. 难度 calibration (DifficultyEstimator)
  ↓
5. 安全 + 反抄袭 (SafetyAgent + PlagiarismChecker)
  ↓
入库 (problem_bank.is_ai_generated=true, status='reviewable')
  ↓
6. 教师审核兜底（异步，可选）
```

### 2.3 每重审查的「最弱智 AI 也能写」具体实现

#### 审查 1：静态结构校验（StructValidator）

**目标**：题目对象格式合法，不抛 ZeroDivisionError 之类的明显傻 bug。

**实现位置**：`services/pythontopia-graph/app/agents/practice_agent/struct_validator.py`

**最小契约**：

```python
def validate_problem_struct(p: dict) -> tuple[bool, list[str]]:
    """返回 (是否合格, 错误列表)。错误列表为空 = 通过。"""
    errors = []
    # 必填字段
    for k in ("title", "statement", "kc_code", "difficulty",
              "reference_solution", "test_cases"):
        if k not in p or not p[k]:
            errors.append(f"missing field: {k}")
    # kc_code 必须命中 ^PY43-KC-\d{3}$
    import re
    if not re.match(r"^PY43-KC-\d{3}$", str(p.get("kc_code", ""))):
        errors.append("kc_code format invalid")
    # difficulty 必须 1..5
    if not isinstance(p.get("difficulty"), int) or not 1 <= p["difficulty"] <= 5:
        errors.append("difficulty must be int 1..5")
    # test_cases ≥ 3 且每条有 input/expected_output
    tcs = p.get("test_cases", [])
    if not isinstance(tcs, list) or len(tcs) < 3:
        errors.append("need ≥3 test_cases")
    for i, tc in enumerate(tcs):
        if "input" not in tc or "expected_output" not in tc:
            errors.append(f"test_case[{i}] missing input/expected_output")
    # 题面 ≤ 500 字符（avoid LLM 罗嗦）
    if len(str(p.get("statement", ""))) > 500:
        errors.append("statement too long")
    return len(errors) == 0, errors
```

**期望输出**：通过返回 `(True, [])`；失败返回 `(False, ["missing field: title", ...])`。

**回退**：失败立即 `raise ValueError`，PracticeAgent 重试 1 次，仍失败标记题目 `status='rejected'`。

#### 审查 2：单元测试自洽（SelfTest，最关键）

**目标**：AI 给的 reference_solution 必须能通过自己给的所有 test_cases。

**实现位置**：`services/pythontopia-graph/app/agents/practice_agent/self_tester.py`

```python
import subprocess, tempfile, json
def self_test(p: dict, timeout_sec: int = 5) -> tuple[bool, str]:
    code = p["reference_solution"]
    for tc in p["test_cases"]:
        inp = tc["input"]
        expected = str(tc["expected_output"]).strip()
        with tempfile.NamedTemporaryFile("w", suffix=".py", delete=False) as f:
            f.write(code)
            path = f.name
        try:
            r = subprocess.run(
                ["python", path],
                input=inp,
                capture_output=True,
                text=True,
                timeout=timeout_sec,
                env={"PYTHONIOENCODING": "utf-8"},
            )
            actual = r.stdout.strip()
            if actual != expected:
                return False, f"test_case input={inp!r}: expected={expected!r}, got={actual!r}"
            if r.returncode != 0:
                return False, f"reference exited non-zero: {r.stderr[:200]}"
        except subprocess.TimeoutExpired:
            return False, f"reference solution timeout (>{timeout_sec}s)"
    return True, "ok"
```

**沙箱**：必须用 docker container 或 subprocess + `resource.setrlimit` 限制 CPU/内存（防恶意代码）。  
**实现要点**：限 `RLIMIT_CPU=5s` `RLIMIT_AS=128MB`，去 network + 拒文件写（chroot 或 seccomp）。

**期望输出**：reference_solution 跑过所有 test_cases → `(True, "ok")`。  
**失败立即 reject**：90% 的 AI 出题错误能被这一关捕获。

#### 审查 3：KC 引用证据（GroundingCritic）

**目标**：题目「真的在考」声称的 KC，而不是无关知识点。

**实现位置**：复用现有 `services/pythontopia-graph/app/nodes/grounding_critic.py`（R-12 已实现）

**最小契约**：

```python
def verify_kc_grounding(p: dict, kc_catalog: dict[str, KcDoc]) -> tuple[bool, str]:
    """
    1. 取出 p['kc_code'] 对应的 KC 文档（含关键词、syntax 列表、典型示例）。
    2. 计算 p['statement'] + p['reference_solution'] 与 KC 关键词的 TF-IDF 相似度。
    3. 相似度 ≥ 0.6 → 通过；否则失败。
    4. 同时拒绝出现「超纲 syntax」（用 ast.walk 检查 reference_solution）。
    """
```

**超纲检查表**（每章可用 syntax 白名单）：

| 章 | 允许 syntax |
| -- | -- |
| 1 | `print`, `input`, 变量赋值, `#` 注释 |
| 2 | `int`, `float`, `str`, `list`, `dict`, `type()`, `+/-/*//` |
| 3 | `if/elif/else`, 比较运算符 `==/!=/<>` |
| 4 | `for`, `while`, `break`, `continue`, `range` |
| 5 | `def`, `return`, 默认参数, `*args/**kwargs` |
| 6 | `class`, `self`, 继承, `super()` |
| 7 | `import`, `try/except/finally`, `open`, `with`, `raise` |

**实现关键**：用 Python `ast.parse(code).body` walk，遇到 unsupported node → reject。例如第 2 章出现 `if` 节点 = 失败。

#### 审查 4：难度 calibration（DifficultyEstimator）

**目标**：AI 自报 difficulty=2 实际是 difficulty=4，玩家挫败。

**实现位置**：`services/pythontopia-graph/app/agents/practice_agent/difficulty_estimator.py`

**计算特征**（数值化）：
- 题面长度 `statement_len`
- reference_solution 行数 `solution_lines`
- AST 节点深度 `ast_max_depth`
- 函数 / 类定义数量 `def_count`
- 循环 / 条件嵌套深度 `loop_if_nest`
- 出现的 syntax 数量 `syntax_count`（white-list 计数）

**公式**：

```python
def estimate_difficulty(p: dict) -> int:
    s = p["reference_solution"]
    import ast
    tree = ast.parse(s)
    feats = {
        "statement_len": len(p["statement"]),
        "solution_lines": len(s.splitlines()),
        "ast_depth": _ast_depth(tree),
        "loop_count": sum(1 for n in ast.walk(tree) if isinstance(n, (ast.For, ast.While))),
        "if_count": sum(1 for n in ast.walk(tree) if isinstance(n, ast.If)),
        "def_count": sum(1 for n in ast.walk(tree) if isinstance(n, (ast.FunctionDef, ast.ClassDef))),
    }
    score = (
        feats["statement_len"] / 100
        + feats["solution_lines"] * 0.3
        + feats["ast_depth"] * 0.5
        + feats["loop_count"] * 1.0
        + feats["if_count"] * 0.7
        + feats["def_count"] * 1.2
    )
    # 经验阈值
    if score < 2: return 1
    if score < 4: return 2
    if score < 7: return 3
    if score < 11: return 4
    return 5
```

**校验**：AI 自报 difficulty 与估算差距 > 1 → 用估算值覆盖；> 2 → reject。

**这条规则可以离线先用 39 道真题做回归 calibration**，确保系数合理。

#### 审查 5：安全 + 反抄袭（SafetyAgent + PlagiarismChecker）

**目标**：
- 不含敏感词（歧视/政治/暴力/性 → 已有 SafetyAgent，复用）
- 不与课程包 43 现有 39 题重复（防 AI 偷懒）

**实现**：

```python
def is_too_similar_to_existing(p: dict, existing_problems: list[dict]) -> tuple[bool, str]:
    from difflib import SequenceMatcher
    for ep in existing_problems:
        sim = SequenceMatcher(None, p["statement"], ep["statement"]).ratio()
        if sim > 0.8:
            return True, f"too similar to existing problem id={ep['id']} sim={sim:.2f}"
    return False, ""
```

**敏感词检查**：复用 `services/pythontopia-graph/app/nodes/safety_agent.py`，已有黑名单。

#### 审查 6：教师审核兜底（异步）

- **触发条件**：上述 1-5 都过的题入库 `problem_bank.is_ai_generated=true, status='reviewable'`
- **教师端**：`/teacher/review` 页面（待新建）批量审，可标 `approved` / `rejected`（带原因）
- **服务端**：被 `rejected` 的题进入 `LearningEvent('AI_PROBLEM_REJECTED')`，PracticeAgent 学习并降权该 prompt 模板

### 2.4 五重审查的失败重试策略

```
PracticeAgent 出题
  ↓
审查 1-5 任一失败
  ↓
重试（最多 3 次，每次 LLM temperature += 0.1 增加多样性）
  ↓
仍失败 → 标记 `status='rejected'` + 错误日志 + LearningEvent('AI_PROBLEM_GIVEUP')
  ↓
退化：从同 KC 的 golden test 题随机抽 1 道使用
```

### 2.5 Boss 4 阶段 AI 出题特化

| 阶段 | 难度递增 | 出题策略 |
| -- | -- | -- |
| 1 | difficulty=1 | 该 KC 最简模板（修改 1 行） |
| 2 | difficulty=2-3 | 同 KC 中等 + 引入 1 个上一章 KC（组合） |
| 3 | difficulty=3-4 | 同 KC 难 + 必须用 ≥2 种 syntax |
| 4 | difficulty=4-5 | 综合题，包含 4-5 个 KC 联合 |

**自适应**：玩家 boss 阶段 1 用了 < 1 min → 阶段 2/3/4 升 1 级。失败 → 降 1 级且解锁 Layer 2 提示。

---

## 3. 数据模型（不改后端 PostgreSQL，全部走 save_slot.snapshot）

> R-13 本会话不做 V120 migration。**所有新数据塞进 `save_slot.snapshot` JSONB**，向后兼容 + 演示风险低。

### 3.1 SaveSlot.snapshot.mainQuest 数据形态

```ts
interface MainQuestProgress {
  /** 当前章节 1..7，未开始 = 0 */
  currentChapter: number;
  /** 当前章节内的子任务序号 1..5，0 = 待领取 */
  currentSubQuest: number;
  /** 已完成的子任务 ID 列表（跨章节） */
  completedSubQuests: string[]; // ["1-1", "1-2", "1-3", ...]
  /** 已击败的 boss 章节列表 */
  defeatedBosses: number[]; // [1, 2, 3]
  /** 已解锁的 outfit / title / storyboard / region 等里程碑奖励 */
  unlockedMilestones: string[]; // ["outfit:apprentice", "title:list_master", ...]
  /** AI 出题历史日志（最近 10 题），用于自适应难度 */
  recentAiProblems: Array<{
    problemId: string;
    kc: string;
    difficulty: number;
    succeededInSec: number | null; // null = 未通过
    timestamp: string;
  }>;
}
```

### 3.2 7 章 × 5 子任务静态数据（前端 const）

**文件**：`frontend/src/data/mainQuests.ts`

每条子任务：

```ts
interface MainSubQuest {
  id: string;          // "1-1"
  chapter: number;     // 1
  index: number;       // 1
  title: string;       // "扫地（hello world）"
  hint: string;        // "去 town_hub 找 mayor_py 取扫帚，然后..."
  mapKey: string;      // "town_hub"
  npcKey?: string;     // "mayor_py"
  kcCodes: string[];   // ["PY43-KC-001"]
  evidenceRequired: {
    type: 'submission' | 'dialog_complete' | 'resource_consumed' | 'building_visit';
    target?: string;   // submission 时是 problemId
  };
  rewards: Array<{ kind: 'coin' | 'gem' | 'item' | 'kc_mastery'; amount: number; ref?: string }>;
}
```

35 条数据先用占位（PLACEHOLDER），后续教师团队填业务文案。

---

## 4. 文件清单（按 commit 顺序，最弱智 AI 照着 git add）

> 每个文件标注 [N/L/M] = 新建 / 已有需改 / 移动。

### 4.1 commit 1：主线数据 + 类型

| 路径 | N/L | 内容 |
| -- | -- | -- |
| `frontend/src/data/mainQuests.ts` | N | 35 条 MainSubQuest 静态数组 |
| `frontend/src/types/mainQuest.ts` | N | 共享类型 MainSubQuest / MainQuestProgress |
| `frontend/tests/data/mainQuests.spec.ts` | N | 数据完整性（35 条、kc_code 合法、每章 5 条） |

### 4.2 commit 2：主线状态 store

| 路径 | N/L | 内容 |
| -- | -- | -- |
| `frontend/src/stores/mainQuest.ts` | N | Pinia store，actions: hydrateFromSlot / completeSubQuest / defeatBoss / nextSubQuest |
| `frontend/tests/stores/mainQuest.spec.ts` | N | store 单测 ≥ 15 |
| `frontend/src/stores/world.ts` | L | 在 `syncFromSlot` 末尾调 `useMainQuestStore().hydrateFromSlot()` |

### 4.3 commit 3：HUD 主线进度 chip + 主线面板

| 路径 | N/L | 内容 |
| -- | -- | -- |
| `frontend/src/components/hud/MainQuestChip.vue` | N | 顶部 chip：`第 X 章 · 区域 (i/5)` |
| `frontend/src/components/hud/MainQuestPanel.vue` | N | 全屏面板：7 章树 + 每章 5 任务 + 状态 + 「去做」按钮 |
| `frontend/src/components/hud/HudShell.vue` | L | 加 `MainQuestChip` 到 hud-top；加 `Q` 键监听切换 panel |
| `frontend/src/pages/WorldPage.vue` | L | 挂载 `<MainQuestPanel />` |
| `frontend/tests/components/MainQuestChip.spec.ts` | N | chip 渲染 ≥ 5 |
| `frontend/tests/components/MainQuestPanel.spec.ts` | N | panel 7 章 + 35 任务 + 「去做」跳转 ≥ 15 |

### 4.4 commit 4：Boss 副本 UI（前端，4 阶段）

| 路径 | N/L | 内容 |
| -- | -- | -- |
| `frontend/src/pages/BossPage.vue` | N | 全屏副本页（左 Pyodide 编辑器 + 右 boss 像素动画 + 顶部阶段提示 + 计时器） |
| `frontend/src/app/router.ts` | L | 加路由 `/boss/:chapter`（lazy import） |
| `frontend/src/composables/useBossSession.ts` | N | 副本状态机：初始化 / 提交代码 / 跳阶段 / 失败 / 完成 |
| `frontend/src/api/boss.ts` | N | `requestBossProblem(chapter, stage)` `submitBossAttempt(...)`（先 mock） |
| `frontend/tests/pages/BossPage.spec.ts` | N | 4 阶段切换 + 通过/失败分支 ≥ 12 |

### 4.5 commit 5：AI 出题服务（services/pythontopia-graph 内）

| 路径 | N/L | 内容 |
| -- | -- | -- |
| `services/pythontopia-graph/app/agents/practice_agent/__init__.py` | L | 加 `generate_boss_problem(chapter, stage, kc, difficulty)` 入口 |
| `services/pythontopia-graph/app/agents/practice_agent/struct_validator.py` | N | §2.3-1 实现 |
| `services/pythontopia-graph/app/agents/practice_agent/self_tester.py` | N | §2.3-2 实现（subprocess + RLIMIT） |
| `services/pythontopia-graph/app/agents/practice_agent/difficulty_estimator.py` | N | §2.3-4 实现 |
| `services/pythontopia-graph/app/agents/practice_agent/plagiarism_checker.py` | N | §2.3-5 实现 |
| `services/pythontopia-graph/tests/practice_agent/test_struct_validator.py` | N | ≥ 20 用例 |
| `services/pythontopia-graph/tests/practice_agent/test_self_tester.py` | N | ≥ 15 用例（含恶意代码沙箱测试） |
| `services/pythontopia-graph/tests/practice_agent/test_grounding.py` | N | ≥ 15 用例 |
| `services/pythontopia-graph/tests/practice_agent/test_difficulty.py` | N | ≥ 15 用例（用 39 道真题做 golden） |
| `services/pythontopia-graph/tests/practice_agent/test_plagiarism.py` | N | ≥ 10 用例 |

### 4.6 commit 6：后端 API 接 Boss 出题

| 路径 | N/L | 内容 |
| -- | -- | -- |
| `backend/src/main/java/com/pythontopia/world/boss/BossController.java` | N | `POST /api/world/boss/{chapter}/problem` `POST /api/world/boss/{chapter}/attempt` |
| `backend/src/main/java/com/pythontopia/world/boss/BossService.java` | N | 调 graph 服务（http 18091）拉 boss 题 |
| `backend/src/main/java/com/pythontopia/world/boss/dto/BossProblemDto.java` | N | 与前端 `MainSubQuest` 对齐 |
| `backend/src/test/java/com/pythontopia/world/boss/BossServiceTest.java` | N | ≥ 8 用例（mock graph 服务） |

### 4.7 commit 7：文档 + e2e + CHANGELOG

| 路径 | N/L | 内容 |
| -- | -- | -- |
| `docs/r13-main-quest-design.md` | N | 把本文 §1-3 复制为终稿 + 35 任务表 |
| `docs/r13-ai-problem-correctness.md` | N | 把本文 §2 复制为终稿 + 5 重审查链 |
| `frontend/tests/integration/main-quest-flow.integration.spec.ts` | N | 完整玩家流：登录 → 主线 panel → 接任务 → 完成 → boss → 通过 ≥ 20 |
| `CHANGELOG.md` | L | 在顶部新增 R-13 Wave 2 章节 |

---

## 5. 执行步骤（最弱智 AI 串行 checklist）

> 每完成一个 □ 立即 `npm run typecheck && npx vitest run` 看是否破坏。

### Phase 1：本地数据准备（1 小时）

- [ ] 1.1 cd `/home/cypress/Pythontopia/frontend`
- [ ] 1.2 创建 `src/types/mainQuest.ts`（按 §3.1 + §3.2）
- [ ] 1.3 创建 `src/data/mainQuests.ts`（PLACEHOLDER 35 条，每条至少有 id/chapter/index/title 三字段，其它先空）
- [ ] 1.4 创建 `tests/data/mainQuests.spec.ts`（仅断言 35 条 + 每章 5 条 + id 不重）
- [ ] 1.5 `npm run typecheck` → 0 error
- [ ] 1.6 `npx vitest run tests/data/mainQuests.spec.ts` → PASS
- [ ] 1.7 `git add` + commit `feat(main-quest): 35 子任务静态数据 + 类型`

### Phase 2：Pinia store（1 小时）

- [ ] 2.1 创建 `src/stores/mainQuest.ts`
- [ ] 2.2 创建 `tests/stores/mainQuest.spec.ts`（≥ 15 用例）
- [ ] 2.3 `npx vitest run tests/stores/mainQuest.spec.ts` → PASS
- [ ] 2.4 改 `src/stores/world.ts` 的 `syncFromSlot` 末尾加一行 `useMainQuestStore().hydrateFromSlot(slot.snapshot)`（snapshot 是 unknown，要做 safe cast）
- [ ] 2.5 `npx vitest run tests/stores/` → 全 PASS
- [ ] 2.6 commit `feat(main-quest): Pinia store + world hydrate`

### Phase 3：HUD chip + 主线面板（2 小时）

- [ ] 3.1 创建 `src/components/hud/MainQuestChip.vue`
- [ ] 3.2 创建 `src/components/hud/MainQuestPanel.vue`
- [ ] 3.3 改 `src/components/hud/HudShell.vue`：
  - 在 `hud-top__group` 后面 append `<MainQuestChip />`
  - keydown 监听加 `q` / `Q` → 触发 `mainQuest.togglePanel()`（避开 input 焦点）
- [ ] 3.4 改 `src/components/hud/index.ts` barrel export 加 `MainQuestChip` `MainQuestPanel`
- [ ] 3.5 改 `src/pages/WorldPage.vue` 模板末尾加 `<MainQuestPanel />`
- [ ] 3.6 新建 `tests/components/MainQuestChip.spec.ts` + `MainQuestPanel.spec.ts`
- [ ] 3.7 `npm run typecheck && npx vitest run` → 全 PASS
- [ ] 3.8 commit `feat(main-quest): HUD chip + 主线面板 + Q 快捷键`

### Phase 4：BossPage（前端，mock 数据）（3 小时）

- [ ] 4.1 创建 `src/composables/useBossSession.ts`（状态机：idle / loading / playing / passed / failed）
- [ ] 4.2 创建 `src/api/boss.ts`（先用静态 mock 数据返回，PLACEHOLDER）
- [ ] 4.3 创建 `src/pages/BossPage.vue`（用 Pyodide 已有 adapter）
- [ ] 4.4 改 `src/app/router.ts` 加路由 `/boss/:chapter`，meta requiresAuth
- [ ] 4.5 创建 `tests/pages/BossPage.spec.ts`（≥ 12 用例，含 4 阶段切换）
- [ ] 4.6 `npm run typecheck && npx vitest run` → 全 PASS
- [ ] 4.7 commit `feat(boss): BossPage 4 阶段 UI + mock 数据`

### Phase 5：AI 出题服务（4-5 小时，Python 后端）

> 注意：这步在 `services/pythontopia-graph/` 下，独立 venv + pytest。

- [ ] 5.1 cd `services/pythontopia-graph` && `python -m pip install -e ".[dev]"`
- [ ] 5.2 按 §2.3-1 实现 `struct_validator.py` + 20 测试
- [ ] 5.3 按 §2.3-2 实现 `self_tester.py` + 15 测试（含 subprocess + RLIMIT；测试要包含正常题、解法错题、超时题、恶意代码 `import os; os.system('rm -rf /')` 必须被沙箱拦截）
- [ ] 5.4 复用 `grounding_critic.py`，加 §2.3-3 KC 引用 + super 语法白名单
- [ ] 5.5 按 §2.3-4 实现 `difficulty_estimator.py` + 用 39 道真题做 golden（误差 < 1 才算通过）
- [ ] 5.6 按 §2.3-5 实现 `plagiarism_checker.py` + 10 测试
- [ ] 5.7 改 `practice_agent/__init__.py` 加 `generate_boss_problem` 编排（5 重审查链 + 失败重试 3 次）
- [ ] 5.8 `python -m pytest -q` → 全 PASS
- [ ] 5.9 commit `feat(graph): practice agent 5 重审查链 + boss 出题`

### Phase 6：后端 API（2 小时）

- [ ] 6.1 创建 `BossController` + `BossService` + `BossProblemDto`
- [ ] 6.2 `BossService.fetchProblem(chapter, stage, userId)` 通过 OkHttp 调 `http://localhost:18091/practice/boss`，传 KC + difficulty + userId
- [ ] 6.3 创建 `BossServiceTest`（MockWebServer 模拟 graph 服务）
- [ ] 6.4 `mvn -q -DskipITs test` → BUILD SUCCESS
- [ ] 6.5 commit `feat(backend): BossController 调 graph 拉 AI 出题`

### Phase 7：前端接真 API + e2e（2 小时）

- [ ] 7.1 改 `src/api/boss.ts` 用真 axios（去 mock）
- [ ] 7.2 改 `useBossSession.ts` 接住 4 阶段真题流
- [ ] 7.3 创建 `tests/integration/main-quest-flow.integration.spec.ts`（≥ 20 用例完整流）
- [ ] 7.4 启后端 + Graph + 前端，手动跑一遍演示链路（按本文 §1.2 第 1 章 5 任务）
- [ ] 7.5 `npm run typecheck && npx vitest run && mvn -q -DskipITs test && python -m pytest -q` → 全 PASS
- [ ] 7.6 commit `feat(main-quest): 前后端真链路打通`

### Phase 8：文档 + CHANGELOG（1 小时）

- [ ] 8.1 创建 `docs/r13-main-quest-design.md`（拷贝本文 §1-3）
- [ ] 8.2 创建 `docs/r13-ai-problem-correctness.md`（拷贝本文 §2）
- [ ] 8.3 更新 `CHANGELOG.md` 顶部新增 R-13 Wave 2
- [ ] 8.4 commit `docs(r13): 主线 + AI 出题 spec 落地文档`

---

## 6. 验证 checklist（每次 commit 都过一遍）

```bash
# 前端
cd /home/cypress/Pythontopia/frontend
npm run typecheck   # 期望 0 error
npm run lint        # 期望 0 error
npx vitest run      # 期望 N/N PASS（N 应该单调递增）
npm run build       # 期望主 bundle ≤ 50KB / Phaser lazy chunk

# 后端
cd /home/cypress/Pythontopia/backend
mvn -q -DskipTests compile   # BUILD SUCCESS
mvn -q -DskipITs test        # BUILD SUCCESS

# Graph 服务
cd /home/cypress/Pythontopia/services/pythontopia-graph
python -m pytest -q           # 全过

# Live API（启后端 + graph 后）
curl http://localhost:18080/healthz                       # {"status":"ok"}
curl -X POST http://localhost:18080/api/world/boss/1/problem \
     -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
     -d '{"stage": 1}'                                    # 返回 BossProblemDto
```

---

## 7. 风险清单（提前知道，免得阴沟翻船）

| 风险 | 概率 | 影响 | 缓解 |
| -- | -- | -- | -- |
| LLM 出题超 1s 卡演示 | 中 | 高 | 预生成 7 章 × 4 阶段 = 28 题缓存到 Redis；超时降级到 golden test 题 |
| Pyodide 在 jsdom 跑不动 | 高 | 中 | BossPage spec 用 mock Pyodide adapter；真 Pyodide 走 Playwright |
| sandbox 逃逸 | 低 | **致命** | subprocess + RLIMIT_AS + 拒 socket + 用 prlimit/firejail；不允许 `import socket/os/subprocess` |
| AI 同题反复出 | 中 | 中 | 出题前查 player.recentAiProblems 拒绝近 10 题重复 |
| 玩家 boss 失败连 5 次 | 低 | 中 | 自动降难度 + 解锁 Layer 2/3 提示 |
| 第 1 章太简单玩家 5min 通关 | 中 | 中 | 子任务 ≥ 5 + 自适应难度（每章预估 30+ 分钟） |
| 教师不审 = 错题永远在线 | 中 | 中 | 7 天未审自动 `status='auto_approved'` + 标记 `quality='unverified'` |
| 主线 panel 遮挡画布 | 低 | 低 | 默认半透明 + ESC 关 + 折叠按钮 |
| 后端 OkHttp 超时阻塞 Tomcat | 中 | 中 | 用 WebClient + reactive + 设 timeout = 8s + 重试 1 次 |

---

## 8. 演示脚本（7 分钟，给评委看）

| 时间 | 行动 | 说明 |
| -- | -- | -- |
| 00:00-00:30 | 登录 → /world → 自动打开 FirstTimeHelp | 解释「7 章修复 + AI 出题 Boss」 |
| 00:30-01:30 | 按 Q 打开主线面板 → 章 1 任务 1 «扫地»  | 走到喷泉，按 F 与 mayor_py 对话 |
| 01:30-03:00 | 完成 4 个章 1 子任务（fast forward） | HUD chip 实时 i/5 → 5/5 |
| 03:00-04:30 | 触发章 1 Boss → /boss/1 | 4 阶段 AI 出题，stage 1-2 现场敲，stage 3-4 demo 自动跑 |
| 04:30-05:30 | 通关 → 解锁动画 + outfit + storyboard | 5 重审查链可视化弹窗（让评委看到正确性保障） |
| 05:30-06:30 | 翻 chapter 2 → 切到 farm_north + 季节背景 + festival ribbon | 体现完整闭环 |
| 06:30-07:00 | 教师视角：班级看板 → AI 题库审核 | 5 重审查链 + 教师兜底 |

---

## 9. 下一会话开始时第一句话

> 「请打开 `/home/cypress/Pythontopia/R13_OPTIMIZATION_PLAN.md` 从 §5 Phase 1 开始执行。每完成一个 □ 报告进度。绝对不允许跳步骤。」

---

## 10. 联系 / Owner

- 主线设计：本会话主 agent
- AI 出题责任：services/pythontopia-graph 模块 owner（参考 R-12 W3 多智能体编排者）
- 教师审核责任：班级 quest 模块
- 演示责任：见 `docs/beta_demo_checklist.md`
