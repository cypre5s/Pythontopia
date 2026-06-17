# DeepSeek Tool-Use A/B 200-Case Challenge Report

Date: 2026-06-17

## Purpose

The previous A/B used the compact `DEFAULT_TASKS` set: 20 prompts covering the 9
consolidated workshop tools. That run was useful for regression confidence, but
it was too small and too close to the authored golden set.

This follow-up run uses **200 different prompts** to reduce overfitting risk and
stress the selector boundaries that students actually hit:

- `ask` vs `my_space`
- `make_resource` vs `plan_study`
- `make_resource` vs `quiz`
- `make_practice` vs `quiz`
- `quiz` vs `make_practice`
- `navigate` vs `my_space`
- `browse` vs `ask`

The goal is not to prove a perfect score. The goal is to find the semantic edges
where the product contract needs sharper routing policy.

## Dataset

The challenge set is checked in as:

```text
services/pythontopia-graph/pythontopia_graph/eval/tool_use_eval.py
CHALLENGE_TASKS
```

It contains 200 unique prompts and does not reuse the 20 default prompts.

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

## Command

```bash
cd /home/cypress/Pythontopia/services/pythontopia-graph
timeout 7200 ./.venv/bin/python -u scripts/run_deepseek_tool_use_ab.py \
  --model deepseek-v4-pro \
  --task-set challenge \
  --votes 1 \
  > /tmp/deepseek_tool_use_ab_200.log 2>&1
echo "AB200_EXIT=$?" >> /tmp/deepseek_tool_use_ab_200.log
```

Run metadata:

- provider: `openai_compat:deepseek-v4-pro (fast=deepseek-v4-flash)`
- temperature: `0`
- task set: `challenge`
- cases: `200`
- votes: `1`
- elapsed: `2784s`
- exit: `AB200_EXIT=0`

## Result

```text
PURE-LLM TOOL-LOOP : 183/200 = 92%
HYBRID ROUTER      : 188/200 = 94%
DELTA router-loop  : +2pp
```

Per-tool accuracy:

| Expected tool | Cases | Pure loop | Hybrid router |
|---|---:|---:|---:|
| `ask` | 23 | 23 | 23 |
| `make_resource` | 23 | 21 | 19 |
| `make_practice` | 22 | 20 | 22 |
| `plan_study` | 22 | 18 | 22 |
| `my_space` | 22 | 22 | 19 |
| `quiz` | 22 | 18 | 18 |
| `video` | 22 | 21 | 22 |
| `navigate` | 22 | 19 | 21 |
| `browse` | 22 | 21 | 22 |

Failure distribution:

| Selector | Failure groups |
|---|---|
| Pure loop | `plan=4`, `quiz=4`, `navigate=3`, `resource=2`, `practice=2`, `video=1`, `browse=1` |
| Hybrid router | `resource=4`, `quiz=4`, `my_space=3`, `navigate=1` |

Failure output choices:

| Selector | Wrong / non-hit outputs |
|---|---|
| Pure loop | `my_space=7`, `(none)=3`, `quiz=3`, `ask=2`, `make_practice=1`, `plan_study=1` |
| Hybrid router | `(clarify)=4`, `make_practice=4`, `quiz=2`, `ask=1`, `my_space=1` |

## Failure Cases

`clarify` is counted as a strict miss in the table below because the expected
tool was executable. It is still safer than choosing a wrong tool, so it is
separated in the interpretation.

| Case | Prompt | Expected | Pure loop | Hybrid router | Notes |
|---|---|---|---|---|---|
| `resource_04` | 把异常处理画成学习路线图 | `make_resource` | `plan_study` | OK | "路线图" pulls pure loop toward long-term planning. |
| `resource_07` | 整理一份 input/print 的常见坑清单 | `make_resource` | OK | `(clarify)` | Router too conservative on a clear resource artifact request. |
| `resource_12` | 给列表切片做一个可视化说明稿 | `make_resource` | OK | `(clarify)` | Router low-confidence on "说明稿" phrasing. |
| `resource_17` | 帮我把 while 循环做成错题复盘资料 | `make_resource` | `quiz` | `quiz` | Product-label boundary: "复盘" can mean quiz flow or generated review material. |
| `resource_20` | 做个海报式总结：Python 数据类型 | `make_resource` | OK | `(clarify)` | Router under-routes creative resource wording. |
| `practice_09` | 出题考我字符串 split 和 join | `make_practice` | `quiz` | OK | "出题考我" straddles generated practice and live quiz. |
| `practice_19` | 用小测形式练 range 的边界 | `make_practice` | `quiz` | OK | Same practice-vs-quiz boundary. |
| `plan_07` | 给我安排今天 90 分钟的学习节奏 | `plan_study` | `my_space` | OK | Pure loop over-associates scheduling with personal state. |
| `plan_11` | 明天要测验，今晚怎么复习最稳 | `plan_study` | `(none)` | OK | Pure loop abstains on planning phrasing without explicit "计划". |
| `plan_18` | 请根据薄弱点安排后续学习路径 | `plan_study` | `my_space` | OK | Needs a path proposal, not just profile lookup. |
| `plan_22` | 帮我决定接下来三次练习各练什么 | `plan_study` | `my_space` | OK | Pure loop treats "next practice" as history/profile lookup. |
| `space_09` | 我是不是一直卡在 range 边界 | `my_space` | OK | `ask` | Router sees conceptual help instead of personal evidence lookup. |
| `space_19` | 有没有我反复提交失败的题 | `my_space` | OK | `quiz` | Router turns historical failure lookup into review/quiz. |
| `space_22` | 我的最近一次训练场提交结果是什么 | `my_space` | OK | `(clarify)` | Router should directly inspect student state. |
| `quiz_05` | 带我过一遍旧知识点，最好边问边看 | `quiz` | `my_space` | OK | Pure loop treats "旧知识点" as historical lookup. |
| `quiz_07` | 我想做一次课前自测 | `quiz` | OK | `make_practice` | Router maps self-test to generated practice. |
| `quiz_09` | 给我来 5 个判断题复习异常处理 | `quiz` | `make_practice` | `make_practice` | Product-label boundary: live quiz vs generated question set. |
| `quiz_12` | 做一个不计分的小 quiz | `quiz` | OK | `make_practice` | Router should honor explicit quiz wording. |
| `quiz_14` | 先别生成讲义，直接问我题 | `quiz` | `(none)` | OK | Pure loop abstains despite explicit "问我题". |
| `quiz_17` | 我想确认自己是不是真的懂了 | `quiz` | `my_space` | OK | Pure loop prefers profile/status lookup over active check. |
| `quiz_20` | 让我自己回答几题递归基础 | `quiz` | OK | `make_practice` | Router maps active questioning to generated practice. |
| `video_18` | 想看别人怎么调试 TypeError | `video` | `ask` | OK | Pure loop gives advice instead of video search. |
| `nav_12` | 带我到公告板 | `navigate` | `(none)` | OK | Pure loop abstains on unsupported/less common landmark. |
| `nav_14` | 我要去看资源的地方 | `navigate` | `my_space` | OK | Pure loop interprets destination as personal resources. |
| `nav_22` | 我想去个人空间 | `navigate` | `my_space` | `my_space` | Product-label boundary: navigate to personal-space UI vs inspect personal-space data. |
| `browse_20` | 查一下 dataclasses 的默认值工厂 | `browse` | `ask` | OK | Pure loop answers from knowledge instead of browsing. |

## Interpretation

The 200-case run weakens the earlier "router is always perfect" impression from
the compact default set. That is good: the larger set found real routing policy
edges.

Still, the architecture decision remains mostly intact:

- The hybrid router is stronger overall on challenge prompts: `188/200` vs
  pure loop `183/200`.
- The pure loop has a clearer failure mode: it often chooses a plausible but
  product-wrong tool (`my_space`, `quiz`, `ask`) or abstains with `(none)`.
- The hybrid router's misses include four conservative clarifications. These
  are strict misses for this eval, but they are safer than silent wrong-tool
  execution.
- The biggest unresolved product boundary is not "LLM vs router"; it is the
  taxonomy between `quiz`, `make_practice`, `make_resource`, and `my_space`.

## Product / Engineering Actions

1. Keep the hybrid selector as the default for tool choice. It still wins on the
   broader set and is easier to govern.
2. Add explicit policy text and tests for these boundaries:
   - `quiz`: immediate interactive questioning of the student.
   - `make_practice`: generated practice artifacts or exercise sets.
   - `make_resource`: durable study material, summaries, visual explainers,
     posters, checklists, and review materials.
   - `my_space`: inspect the student's actual history, generated artifacts,
     progress, submissions, or profile evidence.
3. Treat `clarify` separately from wrong-tool execution in future metrics:
   strict accuracy should count it as a miss, but safety analysis should report
   it as "conservative abstention".
4. Add targeted regression cases for the shared misses:
   - `resource_17`: "错题复盘资料"
   - `quiz_09`: "判断题复习"
   - `nav_22`: "去个人空间"
5. Do not tune only against this 200-case file. Keep a rotating held-out set for
   future changes, especially around Chinese near-synonyms like "复盘", "小测",
   "自测", "资料", "路径", and "个人空间".

## Bottom Line

The 20-case result (`95%` vs `100%`) was optimistic. The anti-overfit challenge
run gives a more useful picture:

```text
pure loop    183/200 = 92%
hybrid router 188/200 = 94%
```

The router remains the better primary selector, but the next quality gain should
come from **policy-level boundary definitions and regression cases**, not from
blindly adding more schema wording.
