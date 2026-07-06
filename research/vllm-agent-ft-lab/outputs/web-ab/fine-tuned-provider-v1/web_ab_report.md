# Fine-Tuned LLM Web A/B Report

## Recommendation

- `enable_fine_tuned_provider_with_grounding_guard`

## Serving

- Adapter model: `pythontopia-qwen35-e1-lora`
- Models: `pythontopia-qwen35-awq-base, pythontopia-qwen35-e1-lora`

## A/B Metrics

| Candidate | Score Δ | JSON Δ | Grounding Δ | Leak Δ | Gate |
|---|---:|---:|---:|---:|---|
| Raw adapter | +0.020940 | +0.194872 | +0.153846 | -0.015385 | HOLD |
| Adapter + grounding guard | +0.031880 | +0.251282 | +0.200000 | -0.015385 | PASS |

## Web Embedding

- Entrypoint: `services/pythontopia-graph/pythontopia_graph/llm/provider.py`
- Feature flag: `PYT_FINE_TUNED_LLM_ENABLED`
- Scope: `structured_json_agent_calls_only`
- Fallback: required; the Web Agent must keep running when local vLLM is down.
