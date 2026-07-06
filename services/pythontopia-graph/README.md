# Pythontopia Graph

FastAPI + LangGraph 多智能体资源生成与路径规划服务，对应 `AGENTS.md` 的 Python 微服务约束：结构化输出、fail fast、默认单 worker。

```bash
python -m pip install -e ".[dev]"
python -m pytest -q
```

## Optional Local Fine-Tuned Provider

The local Qwen3.5 LoRA adapter can be enabled for structured JSON Agent calls while keeping the normal provider as fallback:

```bash
export PYT_FINE_TUNED_LLM_ENABLED=true
export PYT_FINE_TUNED_BASE_URL=http://127.0.0.1:18094/v1
export PYT_FINE_TUNED_API_KEY=local-dev-token
export PYT_FINE_TUNED_MODEL=pythontopia-qwen35-e1-lora
```

Only JSON-mode Agent calls are routed to the adapter. Text streaming, tool calling, `model_tier=fast`, and adapter failures fall back to `PYT_LLM_PROVIDER`.
