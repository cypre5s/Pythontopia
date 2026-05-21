# LLM Provider Environment Policy

Pythontopia 在 graph / RAG 服务中通过 `LlmProvider` 协议抽象上游模型，主链路 LLM Provider 由
环境变量 `PYT_LLM_PROVIDER` 决定。

## 支持的 Provider

| 取值 | 用途 | 必需 env |
| ---- | ---- | -------- |
| `dryrun` | 测试/单元用，所有 chat 走确定式占位 | 无 |
| `openai_compat` | 开发期 fallback；复用 OpenAI 兼容接口（含 Alethicode `.env` 中的密钥） | `LLM_BASE_URL`/`LLM_API_KEY`/`LLM_MODEL`（或对应 `OPENAI_*`） |
| `spark` | 比赛演示主链路（讯飞 Spark OpenAI-兼容接口） | `PYT_SPARK_APP_ID`、`PYT_SPARK_API_KEY`、`PYT_SPARK_API_SECRET`、`PYT_SPARK_MODEL_ID`，可选 `PYT_SPARK_BASE_URL`（默认 `https://spark-api-open.xf-yun.com/v1`） |

## 切换原则

- 阶段 04 ~ 阶段 10 主链路 LLM Provider 暂时复用 `/home/cypress/Alethicode/backend/.env`（取值 `openai_compat`）。
- 阶段 11 起，主演示链路必须切到 `spark`：
  - `PYT_LLM_PROVIDER=spark` 缺任意必需变量直接 fail fast，不允许默认值降级。
  - 启动日志显示 `provider_label`（如 `spark:spark-pro`）。
  - 任何 4xx/5xx 翻译成 `LlmProviderError`，节点层 fail fast，前端透出"模型限流/欠费"。
- `dryrun` 仅供 Pytest 和本地无密钥环境使用，业务节点禁止默认走 `dryrun`。

## 本地运行示例

```bash
# 开发期 fallback
export PYT_LLM_PROVIDER=openai_compat
set -a
. /home/cypress/Alethicode/backend/.env
set +a

# 演示主链路（讯飞）
export PYT_LLM_PROVIDER=spark
export PYT_SPARK_APP_ID=...
export PYT_SPARK_API_KEY=...
export PYT_SPARK_API_SECRET=...
export PYT_SPARK_MODEL_ID=spark-pro
# 可选
export PYT_SPARK_BASE_URL=https://spark-api-open.xf-yun.com/v1

uvicorn pythontopia_graph.main:app --host 0.0.0.0 --port 18091 --workers 1
```

## 安全与合规

- 严禁把 `.env` / `.env.demo` 或任何形态的 `api_secret` 提交到仓库。
- 日志中 Provider 密钥必须脱敏，仅打印 `provider_label`。
- 演示账号建议使用一次性 token，演示完毕立即吊销。
- `SparkProvider` 不持久化任何用户提交内容到上游模型供应商之外，所有 grounding / safety
  审查仍在本服务进行。

## RAG 嵌入接入

- 当前 `pythontopia-rag` 默认使用 384 维 `HashEmbedder`；生产可切到 384 维 `BgeSmallEmbedder`。
- 讯飞嵌入接口（`IflytekSparkEmbedder`）作为阶段 11 占位，引入时需要：
  - 重新计算 `rag_chunk` 向量列维度（通常 1024）。
  - 同步重建索引（`POST /rag/index/rebuild`）。
- 维度切换必须在新的 Flyway 迁移中处理，避免在生产数据库上手工 DDL。
