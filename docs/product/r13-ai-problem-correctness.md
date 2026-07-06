# R-13 §5 AI 出题正确性保障系统（落地版）

> 本文记录 `pythontopia-graph/agents/boss/` 5 重审查链的最终实现，与
> `R13_OPTIMIZATION_PLAN.md §2` 设计一致，差异已在「实现说明」段标注。

## 1. 五重审查链

```
LLM 出题 (BossProblem dataclass)
  ↓
1. struct_validator        必填字段 / KC code 正则 / difficulty 1..5 / 至少 3 test_cases
  ↓
2. self_tester             POSIX subprocess + RLIMIT_CPU=5s + RLIMIT_AS=256MB + forbidden imports
  ↓
3. grounding               按章 syntax 白名单 + KC 关键词命中
  ↓
4. difficulty_estimator    AST 特征加权打分 vs AI 自报，差距 ≤1 通过、==2 自动覆盖、>2 reject
  ↓
5. plagiarism_checker      SequenceMatcher 阈值 0.8 + 敏感词黑名单
  ↓
入库（problem_bank.is_ai_generated=true, status='reviewable'）  ← 待 R-14 后端表
  ↓
6. （可选）教师审核兜底     ← 待 R-14 /teacher/review 页面
```

## 2. 重试与回退

`generate_boss_problem(...)`：

- 最多 3 次重试，每次 LLM `temperature += 0.1` 提升多样性；
- 仍失败 → 若 `golden_fallback` 提供则用 golden 题（仍过完整审查）；否则返回最后一次 review，
  调用方按 `BossProblemReview.passed=False` 决定降级方案。

## 3. 审查 1：结构校验（struct_validator）

`pythontopia_graph/agents/boss/struct_validator.py`

| 检查 | 错误 code |
| -- | -- |
| 必填字符串 `title` | `missing_title` |
| 必填字符串 `statement` | `missing_statement` |
| 必填字符串 `reference_solution` | `missing_reference_solution` |
| `kc_code` 匹配 `^PY43-KC-\d{3}$` | `invalid_kc_code` |
| `difficulty` 为 int 且 1..5 | `invalid_difficulty` |
| `chapter` 1..7 / `stage` 1..4 | `invalid_chapter` / `invalid_stage` |
| `test_cases` >= 3 条且每条有 input/expected_output | `insufficient_test_cases` / `missing_test_input` |
| 至少 1 条 `is_sample=True` | `missing_sample_flag` |
| `len(statement) <= 1500` | `statement_too_long` |
| `len(hints) <= 3` | （warning）`too_many_hints` |

10 个 pytest 用例：`tests/agents/boss/test_struct_validator.py`。

## 4. 审查 2：自洽沙箱（self_tester）

`pythontopia_graph/agents/boss/self_tester.py`

- AST 扫描 forbidden imports：socket / subprocess / shutil / ctypes / multiprocessing /
  asyncio.subprocess / urllib.request / requests / httpx / smtplib / ftplib / ssl。
- 子进程跑 reference_solution，stdin = test_case.input，stdout 与 expected_output 对比。
- POSIX RLIMIT：`RLIMIT_CPU=5s`、`RLIMIT_AS=256MB`，Windows / Linux 容器内 fallback 到
  subprocess timeout。
- `env={"PYTHONIOENCODING": "utf-8"}` 清空其它环境变量；建议在生产用 firejail / seccomp
  做系统级网络隔离。

15 个 pytest 用例：`tests/agents/boss/test_self_tester.py`，含恶意 `os.system`、
forbidden imports、超时、解法错、SyntaxError 等。

## 5. 审查 3：KC grounding + 超纲 syntax（grounding）

`pythontopia_graph/agents/boss/grounding.py`

- 关键词检索：`statement + reference_solution` 中至少 1 个 KC 关键词命中；
  默认关键词按章硬编码（plan §2.3-3），教师可注入 `kc_keywords` 字典覆盖。
- 超纲 syntax：每章 AST 节点白名单。例如第 3 章只允许 `if/elif/else`，
  出现 `For/While/FunctionDef/ClassDef/Import` 等 → 拒绝。
  详见 `CHAPTER_SYNTAX_WHITELIST`。
- 第 1 章允许 `BinOp/Add/Attribute/Tuple` 等基础节点（与原 plan 收紧版略放宽，
  以便教师题面用 `a + b`、`.split()` 等不被误判）。

14 个 pytest 用例：`tests/agents/boss/test_grounding.py`。

## 6. 审查 4：难度估算（difficulty_estimator）

`pythontopia_graph/agents/boss/difficulty_estimator.py`

特征 + 加权（与 plan §2.3-4 一致）：

```
score = statement_len / 100
      + solution_lines * 0.3
      + ast_depth * 0.5
      + loop_count * 1.0
      + if_count * 0.7
      + def_count * 1.2
```

桶阈值：`<2 → 1`、`<4 → 2`、`<7 → 3`、`<11 → 4`、否则 5。
ast_depth 跳过 `Load/Store/Del/Param` 等 ctx 节点（避免简单 `print('hi')` 被误判为 2）。

差距策略：

| diff | 行为 |
| -- | -- |
| 0 | 通过，无 issue |
| 1 | 通过 + warning `difficulty_offset_minor` |
| 2 | 通过 + warning `difficulty_overwritten`，自动用估算值覆盖 `problem.difficulty` |
| ≥3 | error `difficulty_far_off`，reject |

19 个 pytest 用例：`tests/agents/boss/test_difficulty.py`。

## 7. 审查 5：反抄袭 + 敏感词（plagiarism_checker）

`pythontopia_graph/agents/boss/plagiarism_checker.py`

- `SequenceMatcher(None, problem.statement, existing.statement).ratio()`；
- 阈值 0.8（可调），命中即 reject；
- 敏感词黑名单：色情/暴力/歧视/种族/政治/辱骂 + porn/violence/racist/fuck/shit。
  检索范围：statement + reference_solution + hints 全部 lowercased 后子串匹配。

10 个 pytest 用例：`tests/agents/boss/test_plagiarism.py`。

## 8. 编排（orchestrator）

`pythontopia_graph/agents/boss/orchestrator.py`

- `run_all_reviews(problem, ...)` 串行跑 1→5，任一 error 即早退。
  支持 `skip_self_test=True` 让 e2e 测试无需真起 Python 子进程。
- `generate_boss_problem(...)` 协程：
  - 接收异步 `problem_factory(chapter, stage, kc_code, difficulty, temperature)`
    返回 `BossProblem`；
  - 最多 3 次重试，每次 temperature +0.1；
  - 仍失败用 `golden_fallback`（必须自身通过审查）；
  - 全程通过 Python logging warn / info 输出。

11 个 pytest 用例：`tests/agents/boss/test_orchestrator.py`。

## 9. 前后端契约

后端 `BossController` 把 `BossProblem.to_payload()` 直接转发给前端
`BossProblemDto`（camelCase JSON 字段对齐，无需 mapper）。

前端 `frontend/src/api/boss.ts` 失败 fallback 不依赖审查链；只有真后端走 graph
才享受 5 重审查。这点对玩家透明：前端 `BossProblemDto.aiGenerated=false` 时知道是
golden 兜底。

## 10. 已知差距 & 下次会话

- LLM provider 尚未注入 graph：当前所有 boss 题都是 golden fallback；
  spark provider 已在 `pythontopia_graph/llm/provider.py` 接好，
  只需把 `problem_factory` 改为 spark 调用即可。
- 教师审核兜底页面 `/teacher/review` 待建。
- `problem_bank` 表 + `is_ai_generated` 字段 R-14 加（V120 migration）。
- `recentAiProblems` 自适应难度逻辑还在前端本地，未上送给 graph 调难度。
