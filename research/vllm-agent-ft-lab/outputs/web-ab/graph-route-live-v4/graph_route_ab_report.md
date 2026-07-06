# Graph Route A/B Report

- Recommendation: `keep_shadow_mode_for_web_route`
- Endpoint: `/agents/resource/produce`
- Web path: `/api/workshop/resources/stream -> /agents/resource/produce/stream`

## Models

- Baseline: `deepseek-v4-pro`
- Candidate: `pythontopia-qwen35-e1-lora`

## Metrics

| Variant | Cases | HTTP | JSON | Resource | Grounding | Safety | Leak | Fallback | Mean ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| baseline_base_model | 3 | 0.000 | 0.000 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 26 |
| candidate_fine_tuned_provider | 3 | 0.000 | 0.000 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 41 |

## Deltas

| Metric | Delta |
|---|---:|
| http_ok_rate | +0.000000 |
| json_valid_rate | +0.000000 |
| resource_success_rate | +0.000000 |
| grounding_safe_rate | +0.000000 |
| safety_safe_rate | +0.000000 |
| forbidden_leak_rate | +0.000000 |
| fallback_like_rate | +0.000000 |

- Gate: `HOLD`

## Case Results

| Variant | Case | Status | Grounding | Safety | Leak | Fallback | Error |
|---|---|---|---|---|---|---|---|
| baseline_base_model | web-route-handout-input | HTTP_ERROR | no | no | no | yes | HTTPStatusError: Client error '401 Unauthorized' for url 'http://127.0.0.1:18091/chat/completions'
For more information check: https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/401 |
| baseline_base_model | web-route-mindmap-branch | HTTP_ERROR | no | no | no | yes | HTTPStatusError: Client error '401 Unauthorized' for url 'http://127.0.0.1:18091/chat/completions'
For more information check: https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/401 |
| baseline_base_model | web-route-practice-loop | HTTP_ERROR | no | no | no | yes | HTTPStatusError: Client error '401 Unauthorized' for url 'http://127.0.0.1:18091/chat/completions'
For more information check: https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/401 |
| candidate_fine_tuned_provider | web-route-handout-input | HTTP_ERROR | no | no | no | yes | HTTPStatusError: Client error '401 Unauthorized' for url 'http://127.0.0.1:18091/chat/completions'
For more information check: https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/401 |
| candidate_fine_tuned_provider | web-route-mindmap-branch | HTTP_ERROR | no | no | no | yes | HTTPStatusError: Client error '401 Unauthorized' for url 'http://127.0.0.1:18091/chat/completions'
For more information check: https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/401 |
| candidate_fine_tuned_provider | web-route-practice-loop | HTTP_ERROR | no | no | no | yes | HTTPStatusError: Client error '401 Unauthorized' for url 'http://127.0.0.1:18091/chat/completions'
For more information check: https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/401 |
