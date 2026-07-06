# Graph Route A/B Report

- Recommendation: `web_route_candidate_passed`
- Endpoint: `/agents/resource/produce`
- Web path: `/api/workshop/resources/stream -> /agents/resource/produce/stream`

## Models

- Baseline: `pythontopia-qwen35-awq-base`
- Candidate: `pythontopia-qwen35-e1-lora`

## Metrics

| Variant | Cases | HTTP | JSON | Resource | Grounding | Safety | Leak | Fallback | Mean ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| baseline_base_model | 3 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 0.000 | 1.000 | 28543 |
| candidate_fine_tuned_provider | 3 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 0.000 | 0.000 | 27528 |

## Deltas

| Metric | Delta |
|---|---:|
| http_ok_rate | +0.000000 |
| json_valid_rate | +0.000000 |
| resource_success_rate | +0.000000 |
| grounding_safe_rate | +0.000000 |
| safety_safe_rate | +0.000000 |
| forbidden_leak_rate | +0.000000 |
| fallback_like_rate | -1.000000 |

- Gate: `PASS`

## Case Results

| Variant | Case | Status | Grounding | Safety | Leak | Fallback | Error |
|---|---|---|---|---|---|---|---|
| baseline_base_model | web-route-handout-input | SUCCESS | yes | yes | no | yes |  |
| baseline_base_model | web-route-mindmap-branch | SUCCESS | yes | yes | no | yes |  |
| baseline_base_model | web-route-practice-loop | SUCCESS | yes | yes | no | yes |  |
| candidate_fine_tuned_provider | web-route-handout-input | SUCCESS | yes | yes | no | no |  |
| candidate_fine_tuned_provider | web-route-mindmap-branch | SUCCESS | yes | yes | no | no |  |
| candidate_fine_tuned_provider | web-route-practice-loop | SUCCESS | yes | yes | no | no |  |
