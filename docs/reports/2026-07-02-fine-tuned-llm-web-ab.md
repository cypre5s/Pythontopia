# Fine-Tuned LLM Web A/B Report

Updated: 2026-07-03

## Scope

This report records the Web-ready integration of the local Qwen3.5 LoRA adapter. The model is used as an optional graph-service provider for structured Pythontopia Agent JSON calls, not as a replacement for the existing Agent runtime.

The Web path stays:

`frontend -> backend -> pythontopia-graph -> LLM provider`

The adapter path is feature-flagged in `pythontopia-graph`; if local vLLM is unavailable, non-JSON streaming is requested, tool calling is used, or a fast model tier is requested, graph falls back to the existing provider. The existing Agent runtime remains runnable without the fine-tuned vLLM process.

## Serving Evidence

- vLLM served model: `pythontopia-qwen35-awq-base`
- LoRA adapter model: `pythontopia-qwen35-e1-lora`
- Endpoint: `http://127.0.0.1:18094/v1`
- `/v1/models` evidence: `research/vllm-agent-ft-lab/outputs/serving/vllm_models_rerun.json`
- Smoke output: `research/vllm-agent-ft-lab/outputs/serving/lora_smoke_mindmap_if.json`
- Graph provider smoke: `research/vllm-agent-ft-lab/outputs/serving/graph_provider_finetuned_smoke.json`
- Graph provider fallback smoke: `research/vllm-agent-ft-lab/outputs/serving/graph_provider_fallback_smoke.json`
- Observed GPU memory while serving: about 7.18 GiB on RTX 5060 Laptop GPU with `MAX_MODEL_LEN=1024`, `MAX_NUM_SEQS=1`, and `GPU_MEMORY_UTILIZATION=0.74`.

## Offline A/B Result

Heldout comparison uses the same 195 heldout source cases:

| Candidate | Score Δ | JSON Δ | Grounding Δ | Leak Δ | Gate |
|---|---:|---:|---:|---:|---|
| Raw adapter | +0.020940 | +0.194872 | +0.153846 | -0.015385 | HOLD |
| Adapter + grounding guard | +0.031880 | +0.251282 | +0.200000 | -0.015385 | PASS |

The raw adapter improves the baseline, but it still misses the strict gate. The adapter with deterministic JSON/evidence repair passes the gate and keeps forbidden leak at 0 on the heldout slice.

Serving failure-slice validation on 12 prior failures:

| Run | Cases | JSON | Grounding | Leak |
|---|---:|---:|---:|---:|
| vLLM LoRA raw | 12 | 0.916667 | 0.833333 | 0.000000 |
| vLLM LoRA + guard | 12 | 1.000000 | 1.000000 | 0.000000 |

## Live Graph Route A/B

Latest live route report: `research/vllm-agent-ft-lab/outputs/web-ab/graph-route-live-v5/graph_route_ab_report.json`.

This test runs the same three Web resource routes through the graph app with a static evidence fetcher and real vLLM calls. The non-streaming Graph route shares the ResourceGraph, provider, evidence, grounding, and safety logic used by the Web SSE route.

| Variant | Cases | HTTP | JSON | Resource | Grounding | Safety | Leak | Fallback | Mean ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| baseline_base_model | 3 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 0.000 | 1.000 | 28543 |
| candidate_fine_tuned_provider | 3 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 0.000 | 0.000 | 27528 |

Gate: `PASS`.

Interpretation: the candidate route preserved reliability gates and reduced fallback-like output from 1.000 to 0.000 on this live route slice. The handout route triggered a grounding retry once and then passed, which is the intended guarded behavior.

## Web Embedding

Implemented in `services/pythontopia-graph/pythontopia_graph/llm/provider.py`:

- `PYT_FINE_TUNED_LLM_ENABLED=true` enables adapter-first routing.
- `PYT_FINE_TUNED_BASE_URL`, `PYT_FINE_TUNED_API_KEY`, and `PYT_FINE_TUNED_MODEL` point at local vLLM.
- Only structured JSON Agent calls use the adapter.
- Text streaming, tool calling, and `model_tier=fast` keep using the original provider.
- Adapter failures are caught and routed to the fallback provider, so Web Agent workflows can still run without vLLM.

Config templates were added to `deploy/.env.example` and `deploy/docker-compose.yml`.

The Web SSE persistence path also needed a backend hardening fix: `ResourceProduceResponseDto.ResourceDraftDto.contentPayload()` now skips Jackson `NullNode` values. Without this, Graph artifacts shaped like `{ "handout": null, "practice": {...} }` could be stored as JSON `null` before the real body field was reached.

## Web Acceptance

Backend SSE smoke: `research/vllm-agent-ft-lab/outputs/web-ab/backend-workshop-stream-v2/backend_workshop_stream_smoke.json`.

| Check | Result |
|---|---|
| Resource id present | true |
| Safety status | PASSED |
| Grounding status | PASSED |
| Evidence bound | true |
| Content non-empty | true |
| Non-fallback practice | true |

Database confirmation for resource `107`:

- `learning_resource.content` type: JSON object.
- `content.problems`: present.
- Problem count: 3.
- `safety_status`: `PASSED`.
- `grounding_status`: `PASSED`.

Browser smoke: `research/vllm-agent-ft-lab/outputs/web-ab/workshop-browser-smoke-v2/workshop_browser_smoke.json`.

| Browser check | Result |
|---|---|
| `/workshop` visible | true |
| Chat pane visible | true |
| Study drawer visible | true |
| Calls drawer visible | true |
| Page errors | 0 |

Screenshot: `research/vllm-agent-ft-lab/outputs/web-ab/workshop-browser-smoke-v2/workshop_page.png`.

## Verification

- `PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests -q` -> 52 passed.
- `cd services/pythontopia-graph && python3 -m pytest -q tests/test_resource_agents.py tests/test_openai_compat_provider.py` -> 27 passed.
- `cd backend && mvn -q -s .mvn/settings.build.xml -Dtest=WorkshopGraphClientTest test` -> passed.
- vLLM adapter `/v1/models` listed both base and LoRA models.
- Adapter chat completion smoke produced valid grounded JSON before repair.
- Graph provider smoke returned `FineTunedFallbackProvider` and valid JSON from `pythontopia-qwen35-e1-lora`.
- Graph provider fallback smoke used a bad adapter port and still returned a fallback response, proving the Web Agent is not hard-dependent on local vLLM.
- Live Graph route A/B v5 passed the Web route gate.
- Full-stack backend SSE smoke created resource `107` with non-empty `practice.problems`.
- Playwright browser smoke loaded `/workshop` and opened study/calls drawers without page errors.

## Remaining Risk

- The adapter should stay in shadow/canary mode until a larger manually reviewed Web slice confirms no hidden pedagogy regression.
- Current live route A/B covers the three core resource shapes, not every Agent branch.
- Local vLLM on 8 GB VRAM is usable for evaluation and demo routing, but latency remains variable; production should keep the existing provider fallback enabled.
