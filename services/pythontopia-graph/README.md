# Pythontopia Graph

FastAPI + LangGraph 多智能体资源生成与路径规划服务，对应 `AGENTS.md` 的 Python 微服务约束：结构化输出、fail fast、默认单 worker。

```bash
python -m pip install -e ".[dev]"
python -m pytest -q
```

## DeepSeek configuration

The graph service loads the repository's `deploy/.env` when run outside Compose. Configure the project variables there (keep the API key out of source control):

```dotenv
PYT_LLM_PROVIDER=openai_compat
PYT_OPENAI_BASE_URL=https://api.deepseek.com
PYT_OPENAI_API_KEY=your-key
PYT_OPENAI_MODEL=deepseek-v4-pro
PYT_OPENAI_MODEL_FAST=deepseek-flash
PYT_OPENAI_MODEL_VISION=deepseek-flash
```

Text and image input use DeepSeek; responses are text. Fine-tuned local providers are disabled, and setting `PYT_FINE_TUNED_LLM_ENABLED=true` makes startup fail.
