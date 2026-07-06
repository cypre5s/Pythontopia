# DeepSeek Tool-Use A/B 200-Case Boundary V2 Report

Date: 2026-06-17

## Purpose

This run answers the follow-up request:

1. research current tool-use / agent-eval practice online;
2. tighten the tool-selection boundaries;
3. rerun a real DeepSeek A/B on 200 prompts, with prompts different from the
   previous 200-case challenge set.

The key result is deliberately not hidden:

```text
PURE-LLM TOOL-LOOP : 186/200 = 93%
HYBRID ROUTER      : 180/200 = 90%
DELTA router-loop  : -3pp
```

So this specific boundary optimization did **not** generalize into a higher
hybrid-router score on the new held-out prompt set. That is useful evidence. It
means the next improvement should be a cleaner product-boundary taxonomy and a
new train/holdout split, not another round of hard-coding the failed v2 rows.

## Online Research Adopted

Primary sources checked:

- [OpenAI Function calling guide](https://developers.openai.com/api/docs/guides/function-calling)
- [Anthropic: Writing effective tools for agents](https://www.anthropic.com/engineering/writing-tools-for-agents)
- [Anthropic: Demystifying evals for AI agents](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents)
- [Anthropic: Effective context engineering for AI agents](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents)
- [Anthropic: Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)
- [LangGraph StateGraph reference](https://langchain-ai.github.io/langgraphjs/reference/classes/langgraph.StateGraph.html)

Actionable lessons applied locally:

| Source lesson | Local interpretation |
|---|---|
| Tools need explicit names, descriptions, examples, edge cases, and clear when/when-not guidance. | Treat `quiz`, `make_practice`, `make_resource`, `my_space`, and `navigate` as product contracts, not just English synonyms. |
| Invalid states should be made harder or impossible through code. | Add deterministic boundary policy after the LLM route: when a prompt clearly describes a product action, code can correct the LLM intent. |
| Keep the initial tool surface small and evaluate different tool counts. | Preserve the 17-to-9 consolidated tool layer; do not expand the tool list to solve boundary misses. |
| Evals should include real failures, balanced problem sets, and held-out data to avoid overfitting. | Keep `CHALLENGE_TASKS` intact and add a separate `CHALLENGE_TASKS_V2`; do not rewrite old evidence. |
| Avoid overspecifying one path when multiple valid strategies exist. | The eval still grades strict first-tool choice, but interpretation separates wrong-tool execution from conservative clarification. |
| Ambiguous or overlapping tools confuse agents; if humans cannot choose cleanly, the model will not either. | The remaining issue is product taxonomy, especially UI navigation vs inspecting personal state. |
| Graphs route through explicit nodes and edges over state. | Boundary correction belongs in graph/router code when the product edge is deterministic, rather than leaving all edges to the model. |

## Boundary Optimization Implemented Before Rerun

Implemented in:

```text
services/pythontopia-graph/pythontopia_graph/agents/intent_router_agent.py
```

New behavior:

- After the LLM/fallback route returns a legal intent, `_boundary_policy_intent`
  checks four high-signal product boundaries:
  - durable resource artifact request -> resource intent (`HANDOUT` as safe generic resource);
  - immediate student-answering quiz request -> `ASKME`;
  - personal history / evidence lookup -> `MY_STUDY`;
  - navigation action plus destination -> `NAVIGATE`.
- If the boundary policy changes the intent:
  - confidence is raised to at least `0.82`;
  - `reason` is annotated with `边界裁决`;
  - secondary intent is cleared;
  - non-navigation target is cleared.

Regression tests added in:

```text
services/pythontopia-graph/tests/test_intent_router_agent.py
```

Covered examples include:

- "整理一份 input/print 的常见坑清单" should not clarify or answer; it is a resource artifact.
- "做一个不计分的小 quiz" should be live quiz, not generated practice.
- "有没有我反复提交失败的题" should inspect personal history.
- "我想去个人空间" should navigate to the personal-space UI, not inspect history.

## New Prompt Set

Implemented in:

```text
services/pythontopia-graph/pythontopia_graph/eval/tool_use_eval.py
CHALLENGE_TASKS_V2
```

Properties:

- 200 prompts.
- All IDs are prefixed with `v2_`.
- No prompt overlaps with `DEFAULT_TASKS`.
- No prompt overlaps with the prior `CHALLENGE_TASKS` 200-case set.
- Balanced coverage over all 9 consolidated tools.

| Expected tool | Cases |
|---|---:|
| `ask` | 23 |
| `make_resource` | 23 |
| `make_practice` | 22 |
| `plan_study` | 22 |
| `my_space` | 22 |
| `quiz` | 22 |
| `video` | 22 |
| `navigate` | 22 |
| `browse` | 22 |
| **Total** | **200** |

The runner now accepts:

```bash
--task-set challenge_v2
```

## Command

```bash
cd /home/cypress/Pythontopia/services/pythontopia-graph
timeout 7200 ./.venv/bin/python -u scripts/run_deepseek_tool_use_ab.py \
  --model deepseek-v4-pro \
  --task-set challenge_v2 \
  --votes 1 \
  > /tmp/deepseek_tool_use_ab_200_v2.log 2>&1
echo "AB200V2_EXIT=$?" >> /tmp/deepseek_tool_use_ab_200_v2.log
```

Run metadata:

- provider: `openai_compat:deepseek-v4-pro (fast=deepseek-v4-flash)`
- temperature: `0`
- task set: `challenge_v2`
- cases: `200`
- votes: `1`
- elapsed: `2924s`
- exit: `AB200V2_EXIT=0`
- raw log: `/tmp/deepseek_tool_use_ab_200_v2.log`

`votes=1` means this is a strict pass@1-style tool-selection run, not a
majority-vote stability run.

## Result

```text
PURE-LLM TOOL-LOOP : 186/200 = 93%
HYBRID ROUTER      : 180/200 = 90%
DELTA router-loop  : -3pp
```

Per-tool accuracy:

| Expected tool | Cases | Pure loop | Hybrid router |
|---|---:|---:|---:|
| `ask` | 23 | 23 | 22 |
| `make_resource` | 23 | 23 | 21 |
| `make_practice` | 22 | 22 | 22 |
| `plan_study` | 22 | 20 | 21 |
| `my_space` | 22 | 22 | 19 |
| `quiz` | 22 | 19 | 17 |
| `video` | 22 | 21 | 21 |
| `navigate` | 22 | 14 | 15 |
| `browse` | 22 | 22 | 22 |
| **Total** | **200** | **186** | **180** |

Failure distribution:

| Selector | Failure groups |
|---|---|
| Pure loop | `navigate=8`, `quiz=3`, `plan_study=2`, `video=1` |
| Hybrid router | `navigate=7`, `quiz=5`, `my_space=3`, `make_resource=2`, `ask=1`, `plan_study=1`, `video=1` |

Wrong / non-hit outputs:

| Selector | Wrong / non-hit outputs |
|---|---|
| Pure loop | `my_space=7`, `(none)=3`, `make_practice=2`, `quiz=1`, `ask=1` |
| Hybrid router | `make_practice=3`, `my_space=4`, `ask=3`, `(clarify)=3`, `quiz=3`, `make_resource=3`, `navigate=1` |

## Pure Loop Failures

| Case | Prompt | Expected | Pure loop |
|---|---|---|---|
| `v2_plan_06` | 请安排一套先概念后练习的学习路径 | `plan_study` | `(none)` |
| `v2_plan_21` | 帮我决定今天先看课件还是先刷题 | `plan_study` | `my_space` |
| `v2_quiz_07` | 给我做 6 道 set/dict 自测 | `quiz` | `make_practice` |
| `v2_quiz_10` | 安排一次生成器概念问答 | `quiz` | `ask` |
| `v2_quiz_13` | 给我一组不计分的 pytest 小测 | `quiz` | `make_practice` |
| `v2_video_04` | 有没有异常处理的动画讲解 | `video` | `make_resource` |
| `v2_nav_02` | 我想打开个人空间 | `navigate` | `my_space` |
| `v2_nav_07` | 帮我导航到复盘大厅 | `navigate` | `(none)` |
| `v2_nav_08` | 切换到课堂公告板 | `navigate` | `(none)` |
| `v2_nav_10` | 打开学习路径界面 | `navigate` | `my_space` |
| `v2_nav_14` | 去资源库，不要生成新资料 | `navigate` | `my_space` |
| `v2_nav_16` | 切到今天任务面板 | `navigate` | `my_space` |
| `v2_nav_21` | 去复习区开始回顾 | `navigate` | `quiz` |
| `v2_nav_22` | 打开我的资料夹页面 | `navigate` | `my_space` |

## Hybrid Router Failures

| Case | Prompt | Expected | Hybrid router | Boundary note |
|---|---|---|---|---|
| `v2_ask_04` | 不要生成资料，直接解释一下 lambda 是什么 | `ask` | `make_resource` | Negative wording should override resource artifact markers. |
| `v2_resource_12` | 用材料形式讲清楚类和对象 | `make_resource` | `ask` | Resource action vocabulary missed "用材料形式/讲清楚". |
| `v2_resource_14` | 把 list.sort 与 sorted 做成对比卡 | `make_resource` | `ask` | Artifact marker missed "对比卡". |
| `v2_plan_15` | 把我的学习节奏改成少量高频练习 | `plan_study` | `(clarify)` | Plan boundary needs "学习节奏/改成" as a scheduling signal. |
| `v2_space_07` | 我的错题里有没有 KeyError 相关的 | `my_space` | `quiz` | History marker missed "我的错题". |
| `v2_space_08` | 回顾一下我过去三次训练场提交 | `my_space` | `navigate` | Personal evidence lookup collided with "训练场" destination. |
| `v2_space_16` | 我上次自测分数是多少 | `my_space` | `quiz` | History marker missed "上次/分数". |
| `v2_quiz_04` | 别给讲义，直接测文件读写 | `quiz` | `make_practice` | "直接测" should be live quiz even with resource negation. |
| `v2_quiz_11` | 现在就检查短路求值 | `quiz` | `ask` | Quiz marker missed "现在就检查". |
| `v2_quiz_13` | 给我一组不计分的 pytest 小测 | `quiz` | `make_practice` | "给我一组" was over-treated as generated practice. |
| `v2_quiz_16` | 做一次推导式转换的即时测验 | `quiz` | `make_practice` | "即时测验" should dominate practice generation. |
| `v2_quiz_22` | 不要看历史，直接现场考我模块基础 | `quiz` | `my_space` | Negative history wording should suppress `my_space`. |
| `v2_video_04` | 有没有异常处理的动画讲解 | `video` | `make_resource` | "动画讲解" means video here, not storyboard material. |
| `v2_nav_08` | 切换到课堂公告板 | `navigate` | `(clarify)` | Destination lexicon missed classroom board phrasing. |
| `v2_nav_10` | 打开学习路径界面 | `navigate` | `my_space` | UI page target collided with path/progress semantics. |
| `v2_nav_11` | 送我去能看历史资源的位置 | `navigate` | `my_space` | Navigation action was present but destination wording was indirect. |
| `v2_nav_14` | 去资源库，不要生成新资料 | `navigate` | `make_resource` | Negative generation wording should preserve navigation. |
| `v2_nav_15` | 导航到自测入口 | `navigate` | `quiz` | "入口" should indicate page navigation. |
| `v2_nav_16` | 切到今天任务面板 | `navigate` | `(clarify)` | Destination lexicon missed task-panel phrasing. |
| `v2_nav_22` | 打开我的资料夹页面 | `navigate` | `my_space` | "页面/打开" should override personal-data lookup. |

## Interpretation

### 1. The v2 run falsifies a simple "router always wins" claim

The prior 200-case challenge showed:

```text
pure loop     183/200 = 92%
hybrid router 188/200 = 94%
```

This v2 set, after adding boundary correction, showed:

```text
pure loop     186/200 = 93%
hybrid router 180/200 = 90%
```

Because the prompt set changed, this is not a direct before/after controlled
metric. But it is a genuine holdout result: the boundary policy did not
generalize across the newer prompt distribution.

### 2. The worst shared boundary is navigation

Both selectors struggle with navigation when the destination words overlap with
content/state concepts:

- `个人空间`
- `学习路径`
- `资源库`
- `今天任务面板`
- `资料夹页面`
- `自测入口`

The product contract is clear: verbs like `打开`, `切到`, `导航到`, `入口`,
`页面`, `送我去` should bias to `navigate` even when the noun is also a
content/data concept.

### 3. The router's quiz boundary is too narrow

The current policy protects against over-routing generated exercise sets into
`quiz`, but it overcorrects. In product terms:

- `quiz`: the assistant asks the student now and waits for answers.
- `make_practice`: generate a durable exercise artifact/set for later use.

Phrases such as `直接测`, `现在就检查`, `即时测验`, `现场考我`, and `不计分小测`
should enter `quiz` even if they contain count/set words.

### 4. The resource boundary lacks negative and artifact handling

Two opposite misses appeared:

- `不要生成资料，直接解释...` was incorrectly pulled into `make_resource`.
- `对比卡` and `用材料形式...` were not recognized as durable artifacts.

This shows the boundary needs explicit negative-intent precedence, not just more
artifact words.

### 5. Personal history needs evidence words, but not every "history" word wins

The router missed:

- `我的错题`
- `过去三次训练场提交`
- `上次自测分数`

But it also over-selected personal-space on:

- `不要看历史，直接现场考我...`
- navigation prompts containing `历史资源的位置`

So the fix should be a two-layer rule:

1. negative history phrase suppresses `my_space`;
2. actual evidence nouns (`错题`, `提交`, `分数`, `记录`, `档案`, `历史生成物`) plus first-person ownership route to `my_space`, unless navigation verbs/page markers dominate.

## Why I Did Not Tune Again Against v2 Immediately

Tuning directly against the 20 failed v2 rows and rerunning the same file would
almost certainly improve the reported score, but it would weaken the experiment.
The online guidance consistently points toward held-out evals and avoiding
overfitting to one task file.

The safer next step is:

1. convert the failure categories into generic boundary rules and regression
   tests;
2. keep `CHALLENGE_TASKS_V2` as evidence;
3. create a fresh `CHALLENGE_TASKS_V3` or random sampled holdout for the next
   real 200-case run.

## Recommended Next Engineering Actions

1. Add a proper navigation lexicon table with destination aliases:
   - `personal_space`, `path_panel`, `resource_library`, `task_panel`,
     `quiz_entry`, `review_zone`, `class_board`.
   - Use verbs/page markers (`打开`, `切换`, `切到`, `入口`, `页面`, `送我去`) as
     higher-priority navigation signals.
2. Add negative-signal precedence:
   - `不要生成资料`, `别给讲义` should suppress resource routing when paired with
     `解释/测/问`;
   - `不要看历史` should suppress `my_space` when paired with `考我/测`.
3. Split quiz/practice by interaction model, not by "题" vocabulary:
   - live answer loop -> `ASKME`;
   - durable exercise artifact -> `PRACTICE`.
4. Add a boundary policy table instead of scattering markers:
   - `priority`
   - `positive_signals`
   - `negative_signals`
   - `required_pairs`
   - `target_intent`
5. Track two metrics in future reports:
   - strict first-tool accuracy;
   - safety-adjusted accuracy where `(clarify)` is separate from wrong execution.
6. Use `votes=3` for stability once prompt volume and time budget allow it.
   This run used `votes=1` because 200 real cases already took about 49 minutes.

## Bottom Line

The good news: the test harness now has a second, non-overlapping 200-prompt set,
and the real DeepSeek run completed successfully end-to-end.

The uncomfortable but important result: the first generalized boundary policy
did not improve held-out performance. The next quality gain should come from a
more explicit product taxonomy for navigation, live quiz, durable practice,
durable resources, and personal evidence lookup, then validation on a fresh
holdout prompt set.
