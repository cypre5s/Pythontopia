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
