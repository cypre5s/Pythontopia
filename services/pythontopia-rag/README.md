# Pythontopia RAG

FastAPI 课件 RAG 服务，对应 `AGENTS.md` 的 Python 微服务约束。阶段 00 只提供健康检查，阶段 06 实现证据检索。

```bash
python -m pip install -e ".[dev]"
python -m pytest -q
```

阶段 01 提供课程页 loader，只读取数据库并生成本地 parquet 缓存，不建立向量索引：

```bash
PYT_DB_URL=postgresql://pytopia:pytopia_dev_password@localhost:15432/pythontopia \
python -m pythontopia_rag.data.loader --dry-run
```

期望输出包含 `count=561`。不加 `--dry-run` 时写入 `services/pythontopia-rag/data/pages.parquet`，该文件只作本地缓存。

## Embedding

默认部署配置使用智谱 `embedding-3`（OpenAI 兼容 `/embeddings`，2048 维）：

```bash
PYT_RAG_EMBEDDER=zhipu
EMBEDDING_API_KEY=...
EMBEDDING_BASE_URL=https://open.bigmodel.cn/api/paas/v4
EMBEDDING_MODEL=embedding-3
EMBEDDING_DIM=2048
PYT_RAG_EMBEDDING_FALLBACK=hash
```

若 key 缺失、API 过期、超时或返回维度异常，`zhipu` embedder 会在进程内切到同样 2048
维的确定性 hash，保证 `/embed`、课程检索和后端记忆/上传语料向量链路继续可用。离线 CI
仍可显式设 `PYT_RAG_EMBEDDER=hash` 使用 1024 维旧哈希实现。

稳定性探针（不打印密钥，只输出统计摘要）：

```bash
PYT_RAG_EMBEDDER=zhipu \
python -m pythontopia_rag.tools.embedding_stability \
  --mode route --rounds 30 --concurrency 4 --batch-size 4 --fail-on-fallback
```
