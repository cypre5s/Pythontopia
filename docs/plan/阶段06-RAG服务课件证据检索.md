# 阶段 06 — RAG 服务课件证据检索（pythontopia-rag）

## 1. 时间窗口与定位

- 建议日期：2026-05-31 ~ 2026-06-02（第 2 周第 4-6 天，3 天，可与阶段 05 并行）。
- 角色：1 名 Python 服务负责人 + 1 名后端配合（数据库 / Embedding 模型管理）。
- 定位：把 `course_page`（561 页）变成可被多智能体检索、可携带"页码引用"的真实 RAG。是阶段 07 防幻觉、阶段 10 Bug 矿洞反例检索、阶段 13 复盘大厅证据展示的硬依赖。

## 2. 本阶段目标

- 在 `services/pythontopia-rag/` 下实现独立 FastAPI 服务：
  - 接口 `POST /rag/query`：输入 `query` + 可选 `kc_codes`，返回 chunk 列表 + 引用 page id 列表。
  - 接口 `POST /rag/index/rebuild`：管理员触发重建（生产用按需，开发用一次性）。
  - 接口 `GET /healthz`、`GET /rag/index/status`：返回索引状态、chunk 数、最近重建时间。
- 索引来源**只能**是 `course_page`（pack_id=43），禁止从任意网页/PDF 抓取。
- 输出必须强制带 `page_refs`：每个 chunk 标记来源 `page_id`、`page_no`、`chapter_id`、`kc_codes`，无引用页不允许返回。
- 输出 chunk 默认 top-K=8，最大 K=20；分数低于阈值（默认 0.3）的不返回，并明确返回 `"no_evidence_found": true`，禁止编造。

## 3. 上下游关系

- 上游：
  - 阶段 01 `course_page` 数据。
  - 阶段 00 RAG 服务脚手架。
- 下游：
  - 阶段 07 所有内容型 Agent（讲义、思维导图、练习题、代码案例、拓展阅读、动画脚本）必须经 RAG 检索后再生成。
  - 阶段 10 Bug 矿洞 `DebugAgent` 用 RAG 取反例片段。
  - 阶段 13 复盘大厅展示资源时点击"引用页"会回查 RAG。

## 4. Alethicode 借用来源

- RAG 入口与 schema：
  - `/home/cypress/Alethicode/services/alethicode-rag/app/routes/query.py`
  - `/home/cypress/Alethicode/services/alethicode-rag/app/schemas.py`
  - `/home/cypress/Alethicode/services/alethicode-rag/app/rag/builder.py`
- 评估证据节点：
  - `/home/cypress/Alethicode/services/qa-graph/app/nodes/retrieval.py`

**抽取约束**：

- 抽取仅作为算法/路由模板；Pythontopia 必须重写 chunking 策略、向量模型选择、阈值与字段命名。
- 老 alethicode-rag 中针对多 pack、跨课程检索的逻辑全部移除。
- 老服务的同步 IO 改为 `async` + `httpx`，遵循 FastAPI 异步规范。

## 5. 任务清单

### 5.1 索引构建

- T06-01 `pythontopia-rag/pythontopia_rag/data/loader.py`（在阶段 01 已建占位）：
  - 入口：`load_pages(db_url) -> list[PageRecord]`，按 `pack_id=43` 全量读取。
  - `PageRecord` 字段：`page_id`, `page_no`, `document_id`, `chapter_id`, `kc_codes`（通过 `course_kc.page_refs` 反向映射）, `title`, `content_markdown`。
- T06-02 `pythontopia_rag/index/chunker.py`：
  - 简单可控的分块策略：先按 Markdown heading 切分，再按 600~900 字符滑窗（重叠 100）。
  - 单 chunk 长度强制 ≥ 100 字符；过短直接合并到下一个 chunk。
  - 输出 `Chunk(chunk_id, page_id, page_no, chapter_id, kc_codes, text, token_estimate)`。
- T06-03 `pythontopia_rag/index/embedder.py`：
  - 抽象 `class Embedder(Protocol): async def embed(texts: list[str]) -> list[list[float]]`。
  - 实现 `BgeSmallEmbedder`（本地，`sentence-transformers` `bge-small-zh-v1.5`）。
  - 实现 `IflytekSparkEmbedder` 占位（阶段 11 接入）。
  - **CI/Dev 默认走 BGE 本地模型**，无 LLM 依赖。
- T06-04 `pythontopia_rag/index/store.py`：
  - 抽象 `class VectorStore(Protocol): async def upsert(chunks, vectors); async def search(vector, top_k, filters) -> list[Hit]`。
  - 实现 `PgVectorStore`：
    - 在 Postgres 中新建表 `rag_chunk`（`chunk_id`, `page_id`, `page_no`, `chapter_id`, `kc_codes TEXT[]`, `text`, `embedding vector(384)`）。
    - 索引 `USING ivfflat (embedding vector_cosine_ops)`。
    - 表迁移由 RAG 服务自管理（建立 `pythontopia_rag` 内部 Alembic 或简化 SQL 脚本 `init_db.sql`），与后端 Flyway 解耦。
- T06-05 `pythontopia_rag/index/builder.py`：
  - 顶层 `async def rebuild_index(db_url, embedder, store, batch_size=64)`。
  - 启动统计：`built_chunks`、`elapsed_ms`、`embed_model`、`store`。
  - 重建期间通过文件锁 `/tmp/pyt_rag_rebuild.lock` 防止并发。
- T06-06 命令行工具 `pythontopia-rag-index` 注册到 `pyproject.toml`，支持 `pythontopia-rag-index rebuild`、`pythontopia-rag-index status`。

### 5.2 查询接口

- T06-10 `pythontopia_rag/routes/query.py`：
  - `POST /rag/query`：
    - 请求：`RagQueryRequest{query: str (1..512), top_k: int = 8 (1..20), kc_codes?: list[str], chapter_ids?: list[int], min_score?: float = 0.3}`。
    - 响应：`RagQueryResponse{hits: list[RagHit], no_evidence_found: bool, debug?: dict}`。
    - `RagHit{chunk_id, page_id, page_no, chapter_id, kc_codes, text, score}`。
  - `POST /rag/index/rebuild`：仅管理员；通过 `X-Internal-Token` 校验，token 来自环境变量 `PYT_RAG_ADMIN_TOKEN`，缺失时 401。
  - `GET /rag/index/status`：返回 `{built_chunks, last_rebuilt_at, embed_model, store}`。
- T06-11 校验逻辑：
  - `query` 中文/英文混合允许，但禁止超长（前置截断后告警）。
  - `kc_codes` 过滤：在向量搜索后做后置过滤。
  - 命中数=0 或所有 score < `min_score` → `no_evidence_found=true`，`hits=[]`。
- T06-12 接口层使用 `pydantic` 模型，所有返回字段非 Optional 部分必填。

### 5.3 与后端集成

- T06-20 后端新增 `com.pythontopia.rag`：
  - `RagClient`：调用 `pythontopia-rag` 的 `POST /rag/query`；超时 10s；retry 1 次；失败抛 `RagUnavailableException`。
  - `service/RagFacade`：暴露 `searchByKcCodes(query, kcCodes, topK)`、`searchByChapter(query, chapterId, topK)`、`searchOpen(query, topK)`。
- T06-21 控制器 `RagController`（仅教师/管理员）：
  - `POST /api/rag/query`：用于教师后台抽样验证；学生端不直接调用，必须经资源工坊。
- T06-22 `application.yml` 增 `pythontopia.services.ragUrl=${PYT_RAG_URL:http://localhost:18092}`、`pythontopia.services.ragAdminToken=${PYT_RAG_ADMIN_TOKEN:}`。

### 5.4 防幻觉关键规则

- T06-30 `RagQueryResponse.no_evidence_found=true` 时，下游资源生成必须显式标注"基于通识知识"或"无法生成"；阶段 07 强制执行。
- T06-31 RAG 服务严禁返回原始 LLM 输出；只返回数据库中的 `chunk.text`（原文）。
- T06-32 当查询包含敏感词时（来自配置 `forbidden_keywords.txt`），直接拒绝并返回 `no_evidence_found=true`，并在日志记录原 query 摘要。

### 5.5 测试

- T06-40 `services/pythontopia-rag/tests/test_chunker.py`：
  - 验证标题切分、滑窗重叠、最小 chunk 长度。
- T06-41 `services/pythontopia-rag/tests/test_index_builder.py`：
  - mock 数据库 + mock embedder，验证写入向量数量、字段映射。
- T06-42 `services/pythontopia-rag/tests/test_query_route.py`：
  - 构造 5 个 mock chunks（覆盖 `input 函数`、`for 循环`、`列表`），断言查询返回正确 `page_no`、`kc_codes`、`score>=阈值`。
  - 断言查询无命中时 `no_evidence_found=true`。
- T06-43 `services/pythontopia-rag/tests/test_admin_auth.py`：
  - rebuild 接口在缺少 token 时返回 401。
- T06-44 后端：`RagClientIntegrationTest`：使用 WireMock 模拟 RAG 服务，覆盖正常、超时、5xx 三种情况。

### 5.6 部署

- T06-50 `services/pythontopia-rag/Dockerfile`：
  - 基础镜像 `python:3.11-slim`。
  - 预下载 `bge-small-zh-v1.5` 到镜像（容量~500MB），并固定模型版本 hash。
  - `CMD ["uvicorn", "pythontopia_rag.main:app", "--host", "0.0.0.0", "--port", "18092", "--workers", "1"]`。
- T06-51 `deploy/docker-compose.yml`：把 `pythontopia-rag` 加入 `app` profile，依赖 `postgres`。
- T06-52 在 `deploy/README.md` 增加 RAG 首次重建索引指引：
  ```bash
  docker compose --profile app run --rm pythontopia-rag \
    pythontopia-rag-index rebuild
  ```

## 6. 数据输入与输出

- 输入：
  - Postgres 中 `course_page` 全量 561 行。
- 输出：
  - Postgres 中 `rag_chunk` 表（预计 1500~2500 行）。
  - RAG 服务 `/rag/query` 返回 chunk + page_no + kc_codes + score。
- 不输出：任何未在 `course_page` 出现的"创造"内容。

## 7. 验收标准

### 7.1 索引验收

- `pythontopia-rag-index rebuild` 完成后：
  - `GET /rag/index/status` 返回 `built_chunks >= 1500`（具体数取决于 chunker 参数，差异 ±20% 内）。
  - `last_rebuilt_at` 为最近时间。
- `rag_chunk` 表索引存在；`EXPLAIN` 使用 `ivfflat` 索引。

### 7.2 查询验收（与 `AGENTS.md` 第 9 节）

- 查询 `input函数`：返回的前 3 个 chunk 中至少 2 个 `kc_codes` 包含 `input`/`I_O` 等相关 KC，并能在 `course_page` 中找到匹配页码。
- 查询 `for循环`：返回的前 3 个 chunk 中至少 2 个 `kc_codes` 与"循环"相关 KC（如 `for-loop`、`iter`），且 page_no 集中于 `for` 循环章节范围。
- 查询 `列表`：返回前 3 个 chunk 至少 2 个匹配"列表"章节 KC。
- 查询无意义乱串（例如 `奥利给奥利给`）：返回 `no_evidence_found=true`。

### 7.3 防幻觉验收

- 给定查询，所有 hits 必须能在数据库中通过 `SELECT * FROM course_page WHERE id = <page_id>` 找到对应原文片段（chunk.text 必须为原文连续子串，或与切分规则一致）。
- 无任何 hit 的 chunk.text 是后期 LLM 改写产物。
- 管理员重建接口未配置 token 时返回 401，避免被未授权触发昂贵任务。

### 7.4 工程验收

- `python -m pytest -q` 全部通过；覆盖率报告生成（仅产出不强制阈值）。
- `RagClient` 在 RAG 服务下线时抛 `RagUnavailableException`，前端不展示"假数据"，而是显示错误状态。

## 8. 验证命令

```bash
cd /home/cypress/Pythontopia/services/pythontopia-rag
python -m pip install -e ".[dev]"
python -m pytest -q

# 重建索引（需先启动 postgres 且数据已导入）
PYT_DB_URL=postgresql://pytopia:***@localhost:15432/pythontopia \
PYT_RAG_ADMIN_TOKEN=devtoken \
uvicorn pythontopia_rag.main:app --host 0.0.0.0 --port 18092 --workers 1 &

curl -s -H "X-Internal-Token: devtoken" \
  -X POST http://localhost:18092/rag/index/rebuild | jq

curl -s -X POST http://localhost:18092/rag/query \
  -H 'Content-Type: application/json' \
  -d '{"query":"input函数","top_k":5}' | jq

curl -s -X POST http://localhost:18092/rag/query \
  -H 'Content-Type: application/json' \
  -d '{"query":"for循环","top_k":5,"kc_codes":["for-loop"]}' | jq

curl -s -X POST http://localhost:18092/rag/query \
  -H 'Content-Type: application/json' \
  -d '{"query":"奥利给奥利给","top_k":3}' | jq '.no_evidence_found'

# 后端集成
cd /home/cypress/Pythontopia/backend
PYT_RAG_URL=http://localhost:18092 mvn test
```

## 9. 风险与回滚

- 风险：BGE 模型下载耗时长 / CI 拉取失败；缓解：模型缓存到镜像并写明国内镜像替代源，CI 使用预下载缓存层。
- 风险：pgvector 索引内存占用；缓解：limit `ivfflat` `lists=100`，监控 `pg_stat_progress_create_index`。
- 风险：chunker 把代码块切散；缓解：先识别 Markdown 代码块作为整体 chunk，超过 1200 字符再细分。
- 风险：管理员 token 泄露；缓解：仅在 `.env` 中配置，部署文档强调本地生成。
- 回滚：删除 `rag_chunk` 表并执行 `rebuild` 即可重建；服务上线后保留旧 chunk 版本号备份（可选）。

## 10. 不做事项

- 不支持 PDF/网页爬虫导入。
- 不接入外部搜索引擎。
- 不允许学生端直接调用 `/rag/query`（必须经资源工坊或教师端）。
- 不在 RAG 内做 LLM 改写、摘要、翻译。
- 不在 chunk 中存放隐藏测试或题解。
- 不缓存 LLM 调用结果到本服务（与 LLM 提供者解耦）。

