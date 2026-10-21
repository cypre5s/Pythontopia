# Embedding Profile 跨语言契约设计

> 日期：2026-07-29
> 切片：P0-2a
> 状态：已批准，进入 TDD

## 1. 问题与边界

生产验收曾观察到 `MemoryEmbeddingWriter expected=2048 actual=1024`。当前持久化列已经由
`V175__embedding_halfvec_2048.sql` 固定为 `halfvec(2048)`，部署环境也声明
`EMBEDDING_DIM=2048`，但 RAG 的显式 `hash` selector 直接构造默认 1024 维
`HashEmbedder()`，忽略了这个部署维度。

同时，RAG `/embed` 已返回 `model` 与 `dim`，Java `RagEmbeddingClient` 却只取
`vectors`，没有验证“响应声明维度”和“实际向量长度”一致。边界元信息存在，但尚未成为可执行契约。

本切片只修复维度 profile 的选择与校验，不迁移数据库、不更换 embedding provider、不重建
RAG 索引，也不把 2048 维改成运行时任意维度。

## 2. 事实源

- 持久化事实源：PostgreSQL `memory_embedding.embedding` 与
  `workshop_upload_chunk.embedding` 均为 `halfvec(2048)`；
- 部署 profile：`PYT_RAG_EMBEDDER`、`PYT_RAG_EMBEDDING_DIM` /
  `EMBEDDING_DIM`、`EMBEDDING_MODEL`；
- 运行态事实源：Pydantic `EmbedResponse(model, dim, vectors)`；
- Java 边界事实源：`EmbedResponseDto`，只接受 `vector.size == dim` 的响应；
- 最终存储守卫：`MemoryEmbeddingWriter` 继续要求 2048 维，防止错误 profile 写入数据库。

## 3. 最小方案

### 3.1 RAG selector 服从显式维度

抽取一个正整数维度解析函数，优先读取 `PYT_RAG_EMBEDDING_DIM`，其次读取
`EMBEDDING_DIM`。`select_embedder()` 选择 `hash` 时用该维度构造 `HashEmbedder`；
两项均未配置时保留现有 1024 维离线默认。

`zhipu + hash fallback` 复用同一解析函数，避免两个 selector 分支再次出现维度规则漂移。
非正整数或非法字符串在进程启动时 fail fast，不允许运行到首个请求才暴露除零或向量错误。

### 3.2 Java 在 HTTP 边界执行 profile 校验

`RagEmbeddingClient` 在投影 `double[] → float[]` 前先校验：

1. `dim > 0`；
2. 每个非空向量的实际长度等于响应声明的 `dim`；
3. 响应不足仍按既有行为补 `Optional.empty()`，响应多余仍截断。

profile 不一致时该位置返回空向量并记录一次不含向量内容的 warn；调用方继续走既有
fail-soft 字面/KC 回退，不把错误向量送入数据库。

## 4. 验收

- `PYT_RAG_EMBEDDER=hash + EMBEDDING_DIM=2048` 生成 2048 维 `hash-bow-2048`；
- `PYT_RAG_EMBEDDING_DIM` 优先于通用 `EMBEDDING_DIM`；
- 非正维度在 selector 阶段拒绝；
- Java 对 `dim=3` 但实际长度为 2 的向量返回 empty；
- 既有 2048 维智谱/同维 fallback、批量顺序、超时 fail-soft 行为不变；
- RAG 全量与 Backend 全量测试通过，或如实记录可复现的既有环境门禁。

## 5. 明确不做

- 不创建兼容 512/1024/2048 的多列或动态 pgvector schema；
- 不删除 `MemoryEmbeddingWriter` 的 2048 最终守卫；
- 不把 Embedding API 合并进 OpenAPI 精选客户端；
- 不顺手处理周报 SQL、SSE、事务拆分或 RAG 快照。
