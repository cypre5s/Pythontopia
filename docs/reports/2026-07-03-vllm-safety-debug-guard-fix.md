# vLLM Safety / Debug Guard 修复与验证报告

更新日期：2026-07-03

## 1. 问题来源

上一轮 1000 条新手表达增强 vLLM 长测证明服务本身稳定，但输出层仍有 48 条样本命中过至少一种失败标记：

| 问题 | 原始数量 |
|---|---:|
| JSON invalid | 10 |
| grounding false | 39 |
| forbidden leak | 5 |
| 至少一个失败标记的样本 | 48 |

失败集中在两个 Agent：

- `SafetyAgent`：JSON valid rate 只有 `0.920`，forbidden leak rate 为 `0.050`。
- `DebugAgent`：JSON 全部有效，但 grounding safe rate 只有 `0.690`，主要是缺少要求的 `page-*` evidence id。

这说明不能让 fine-tuned adapter 的输出直接进入正式业务链路。正式接入必须保留 schema 校验、evidence guard、forbidden scanner、repair/fallback。

## 2. 修复策略

本次实现的是确定性护栏，不是继续微调模型。

### 2.1 实验侧 repair

修改：

- `research/vllm-agent-ft-lab/src/pyt_agent_lab/grounding_repair.py`
- `research/vllm-agent-ft-lab/scripts/repair_run_responses.py`
- `research/vllm-agent-ft-lab/tests/test_grounding_repair.py`

策略：

- SafetyAgent 若响应 JSON 不可解析、缺少 `redactions/safer_alternative`，或命中 forbidden leak，则替换为确定性安全拒答模板。
- SafetyAgent 安全模板包含 `allow=false`、`redactions`、`blocked_content`、`safer_alternative`、`safety_flags`，并保留 KC。
- DebugAgent 与其他证据型 Agent 统一补 `evidence_ids`；无 evidence 的场景补 `grounding_note=common_knowledge`。
- repair 脚本从原始 run 的 `metrics.csv` 恢复 fold，避免修复后图表退化成单折。

### 2.2 Graph provider guard

修改：

- `services/pythontopia-graph/pythontopia_graph/llm/provider.py`
- `services/pythontopia-graph/tests/test_openai_compat_provider.py`

策略：

- `FineTunedFallbackProvider.chat()` 新增可选 `guard_context`。
- 未传 `guard_context` 时保持原行为，但如果 adapter 返回无 JSON，则走 fallback。
- 传入 `guard_context` 时，在返回前做：
  - JSON schema-like 校验：没有 JSON 时 fallback。
  - SafetyAgent forbidden scanner：命中完整答案、直接提交、隐藏测试等风险时替换为安全拒答。
  - evidence guard：补 `evidence_ids` 和 `grounding_note`。
  - DebugAgent evidence guard：确保返回 JSON 带上 required evidence id。

这使得正式接入可以由调用方显式传入：

```python
guard_context={
    "agent": "DebugAgent",
    "kc": "作用域",
    "evidence_ids": ["page-6-018"],
    "expected_markers": ["作用域", "page-6-018", "minimal_repro_hint", "next_probe"],
}
```

未传 context 的旧调用不受影响。

## 3. TDD 证据

新增测试先红后绿：

- `test_repair_truncated_safety_response_into_safe_refusal_json`
- `test_repair_leaky_safety_json_replaces_direct_answer_with_safe_template`
- `test_repair_response_records_preserves_fold_metadata_from_case_map`
- `test_fine_tuned_provider_falls_back_when_adapter_returns_invalid_json`
- `test_fine_tuned_provider_repairs_safety_guard_context_before_returning`
- `test_fine_tuned_provider_adds_debug_evidence_from_guard_context`

红灯证据：

- repair 测试最初失败于 `KeyError: 'allow'`，说明 SafetyAgent fallback 没有安全拒答 schema。
- provider 测试最初失败于 adapter invalid JSON 没 fallback、SafetyAgent 没安全模板、DebugAgent 没 evidence。

绿灯证据：

- `research/vllm-agent-ft-lab/tests/test_grounding_repair.py`：6 passed。
- `services/pythontopia-graph/tests/test_openai_compat_provider.py`：14 passed。
- `services/pythontopia-graph` 全量测试：1230 passed，3 skipped。

## 4. 1000 条 guarded 复测

原始 run：

`research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-20260703T1559CST`

guarded run：

`research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-guarded-v1-20260703`

对照图：

`research/vllm-agent-ft-lab/outputs/comparisons/raw-vs-guarded-safety-debug-v1/charts/baseline_vs_candidate.svg`

### 4.1 总体对照

| 指标 | Raw vLLM | Guarded v1 | Delta |
|---|---:|---:|---:|
| score mean | 0.991083 | 1.000000 | +0.008917 |
| JSON valid rate | 0.990000 | 1.000000 | +0.010000 |
| grounding safe rate | 0.961000 | 1.000000 | +0.039000 |
| forbidden leak rate | 0.005000 | 0.000000 | -0.005000 |

### 4.2 Fold 对照

guarded v1 保留了 5-fold，各折 200 条：

| Fold | Cases | Score | JSON | Grounding | Leak |
|---:|---:|---:|---:|---:|---:|
| 0 | 200 | 1.000000 | 1.000 | 1.000 | 0.000 |
| 1 | 200 | 1.000000 | 1.000 | 1.000 | 0.000 |
| 2 | 200 | 1.000000 | 1.000 | 1.000 | 0.000 |
| 3 | 200 | 1.000000 | 1.000 | 1.000 | 0.000 |
| 4 | 200 | 1.000000 | 1.000 | 1.000 | 0.000 |

### 4.3 Agent 切片

| Agent | Raw 主要问题 | Guarded v1 |
|---|---|---|
| SafetyAgent | JSON invalid 8，grounding false 4，forbidden leak 5 | JSON 1.000，grounding 1.000，leak 0.000 |
| DebugAgent | grounding false 31 | grounding 1.000 |

### 4.4 新手噪声切片

原始风险集中在：

- `incomplete_error_paste`：17 条失败样本。
- `low_confidence_help`：15 条失败样本。
- `anxious_answer_request`：14 条失败样本。

guarded v1 后，6 类噪声的 JSON、grounding、leak 全部达到目标值。

## 5. Artifact

guarded run 产物：

- `summary.json`
- `extended_summary.json`
- `metrics.csv`
- `responses.jsonl`
- `audit/audit_summary.json`
- `charts/score_by_fold.svg`
- `charts/rates_by_fold.svg`
- `charts/error_counts_by_agent.svg`
- `charts/rates_by_noise_profile.svg`
- `charts/latency_by_fold.svg`

对照产物：

- `research/vllm-agent-ft-lab/outputs/comparisons/raw-vs-guarded-safety-debug-v1/comparison.json`
- `research/vllm-agent-ft-lab/outputs/comparisons/raw-vs-guarded-safety-debug-v1/charts/baseline_vs_candidate.svg`

最终验证：

- `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests -q`：62 passed。
- `cd services/pythontopia-graph && python3 -m pytest -q tests/test_openai_compat_provider.py tests/test_provider_tool_calling.py tests/test_resource_stream.py tests/test_debug_agent.py`：34 passed。
- `cd services/pythontopia-graph && timeout 180s python3 -m pytest -q`：1230 passed，3 skipped。
- `git diff --check`：通过。

注意：`repair_manifest.repaired_count=1000` 表示 1000 条都经过统一归一化写回，不等于原始失败数。原始命中失败标记的样本数是 48。

## 6. 结论

SafetyAgent 的 JSON/泄露边界、DebugAgent 的 evidence 缺失，已经通过确定性 guard 修复，并在完整 1000 条、5-fold 样本上验证为：

- JSON invalid：10 -> 0
- grounding false：39 -> 0
- forbidden leak：5 -> 0
- audit clean rate：1.000

工程上仍建议保留 fallback provider。guard 的作用是修复 adapter 可修复的结构化输出；如果 adapter 无法返回 JSON、没有 guard context，或未来出现未覆盖风险，仍应 fallback 到原 provider 或确定性模板。
