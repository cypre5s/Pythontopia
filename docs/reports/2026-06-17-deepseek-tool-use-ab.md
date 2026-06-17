# DeepSeek Tool-Use A/B Report

Date: 2026-06-17

## Scope

This report records the real DeepSeek end-to-end A/B that was left unfinished in
the previous Cursor session.

The experiment compares two selectors on the same in-distribution task set:

- Pure LLM tool-loop: DeepSeek selects from the 9 consolidated tool schemas.
- Hybrid router: `IntentRouterAgent` selects an intent, then maps it to the same
  consolidated tool namespace.

The dataset is `pythontopia_graph.eval.tool_use_eval.DEFAULT_TASKS`: 20 prompts
covering all 9 consolidated workshop tools.

## Command

The completed run used the same harness now committed as:

```bash
cd /home/cypress/Pythontopia/services/pythontopia-graph
timeout 1800 ./.venv/bin/python -u /tmp/ab_eval.py > /tmp/ab_eval.log 2>&1
```

For future reproducible runs, use the checked-in script:

```bash
cd /home/cypress/Pythontopia/services/pythontopia-graph
./.venv/bin/python -u scripts/run_deepseek_tool_use_ab.py --model deepseek-v4-pro --votes 3
```

The provider loaded DeepSeek/OpenAI-compatible credentials from existing env /
`deploy/.env`; no secret values were printed or stored.

## Result

Provider:

```text
openai_compat:deepseek-v4-pro (fast=deepseek-v4-flash)
```

Round-level accuracy:

| Round | Pure LLM Tool-Loop | Hybrid Router |
|---|---:|---:|
| 1 | 18/20 = 90% | 20/20 = 100% |
| 2 | 19/20 = 95% | 20/20 = 100% |
| 3 | 19/20 = 95% | 20/20 = 100% |

3x majority summary:

```text
PURE-LLM TOOL-LOOP : 19/20 = 95%  fails=["review->my_space(exp ['quiz'])"]
HYBRID ROUTER      : 20/20 = 100%  fails=[]
DELTA router-loop  : +5pp
```

The stable pure-loop miss was:

- `review`: prompt `帮我复习一下快忘的旧知识点`
- expected: `quiz`
- pure loop majority: `my_space`
- hybrid router majority: `quiz`

## Interpretation

The in-distribution run is much better than the earlier held-out rewrite set for
pure LLM selection, but it still supports the same architecture decision:

- DeepSeek function calling works end-to-end.
- Pure LLM schema selection is usable, but not perfectly stable on ambiguous
  review/progress wording.
- The hybrid router keeps the selector deterministic on the shipped task set and
  should remain the default selector.
- The tool loop should continue to own bounded multi-step orchestration after the
  router-selected first tool, not replace the router as the primary selector.

## Follow-up

- Keep `PYT_TOOL_LOOP` flag-gated until a local UI smoke run proves the full
  frontend -> Java -> graph SSE path with this selector boundary.
- Add a small regression case around the `review` prompt if future prompt/schema
  tuning attempts try to improve pure-loop selection.
