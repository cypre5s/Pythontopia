# Pythontopia RAG

FastAPI 课件 RAG 服务：从 Python 课程包加载页、构建 BM25 与本地 hash 索引，再按课程证据检索。
部署 Compose 固定使用 `PYT_RAG_EMBEDDER=hash`，不会调用第三方 embedding API。

```bash
python -m pip install -e ".[dev]"
python -m pytest -q
```

课程检索主要依赖 BM25；本地 hash 向量只用于兼容现有混合排序，索引状态会标记语义检索降级。
后端学习记忆不会把 hash 向量写入语义向量表，缺少真实语义向量时继续用现有 KC、时间和重要度排序。
`/readyz` 在 BM25 课程索引可用时返回 `ready_degraded`，空索引仍返回 503。

稳定性探针（只输出统计摘要）：

```bash
PYT_RAG_EMBEDDER=hash \
python -m pythontopia_rag.tools.embedding_stability \
  --mode route --rounds 30 --concurrency 4 --batch-size 4
```
