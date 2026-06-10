# 阶段 11 — 讯飞 Provider 接入与主链路切换

## 1. 时间窗口与定位

- 建议日期：2026-06-09 ~ 2026-06-10（第 3 周末，约 1.5 ~ 2 天）。
- 角色：1 名 Python Agent 负责人 + 1 名后端配合（密钥与配置管理）。
- 定位：把 Pythontopia 主链路 LLM Provider 切换为科大讯飞 Spark 模型，并保留 OpenAI 兼容 Provider 仅作为开发期 fallback。比赛演示链路必须**走讯飞**。

## 2. 本阶段目标

- 在 `pythontopia-graph` 实现 `SparkProvider`：
  - 走讯飞官方 Spark API（HTTP + WebSocket 二者择一，本阶段采用 HTTP / SSE 模式以便统一节点处理）。
  - 支持文本对话；如所选模型不支持 JSON 模式，则采用"提示词约束 + pydantic 解析 + retry 1 次"。
- 在 `pythontopia-rag` 实现 `IflytekSparkEmbedder`（如讯飞提供嵌入接口），否则继续用 BGE 本地模型；接口选择写入 `docs/llm_provider_matrix.md`。
- 配置以环境变量为唯一密钥来源，禁止硬编码。
- 演示前置：所有 Agent 切到讯飞跑通至少 1 次端到端"画像 → KC → 资源 → 路径 → 训练 → Bug 矿洞"。
- 保留 `OpenAiCompatProvider`、`DryRunProvider` 作为开发期 fallback，但启动主链路时必须显式选讯飞。

## 3. 上下游关系

- 上游：阶段 04、05、07、08、10 中的 Agent 抽象。
- 下游：本阶段不会新增业务表；演示阶段（15）会依赖本阶段验证过的链路。

## 4. Alethicode 借用来源

- 多模型 Provider 路由仅参考 Alethicode 已有抽象**思路**：
  - `/home/cypress/Alethicode/services/tutor-graph/app/`（如有 provider 抽象）。

**抽取约束**：

- Pythontopia 必须自有 `LlmProvider` 协议（阶段 04 已建），本阶段只增加 `SparkProvider` 实现。
- 不在 graph 服务中泄漏密钥到日志（统一通过 `structlog` 的 `redact_keys`）。

## 5. 任务清单

### 5.1 SparkProvider 实现

- T11-01 在 `pythontopia_graph/llm/providers/spark.py` 实现：
  - `class SparkProvider(LlmProvider)`：构造接受 `app_id`、`api_key`、`api_secret`、`base_url`、`model_id`（如 `general_v3.5`/`generalv3`/`spark-pro` 等具体名称需由实际可用模型确认）、`timeout_seconds`。
  - `async def chat(messages: list[ChatMessage], **kwargs) -> LlmResponse`：
    - 组装鉴权 header（HMAC-SHA256）。
    - 发起 HTTP/SSE 请求；解析 `delta`/`message` 字段；拼装到完整字符串后输入 `pydantic` 解析。
    - 提供 `response_format='json_schema'` 或"提示词约束"两种模式；JSON 解析失败 retry 1 次。
- T11-02 配置 `pythontopia_graph/llm/config.py`：
  - 读取环境变量 `PYT_LLM_PROVIDER`（取值 `spark`/`openai_compat`/`dryrun`）。
  - 读取 `PYT_SPARK_APP_ID`、`PYT_SPARK_API_KEY`、`PYT_SPARK_API_SECRET`、`PYT_SPARK_BASE_URL`、`PYT_SPARK_MODEL_ID`。
  - 缺失任意必需变量 → 启动 fail fast，禁止默认值降级。
- T11-03 `pythontopia_graph/main.py` 启动时打印 Provider 选择 + 模型名（不打印密钥）；任何运行节点禁止再次读取密钥环境变量。
- T11-04 关键节点（`ResourceDesignerAgent`、`PracticeAgent`、`PathPlannerAgent`、`DebugAgent`、`ProfileAgent`）的 prompt 模板需在 `prompts/` 目录维护，并对讯飞模型做 1 轮"提示稳定性"实验：
  - 同 input 跑 3 次，确认 grounding 与 schema 合规率 ≥ 95%。
  - 不合规直接调整 prompt 模板或加 `response_format`，禁止放宽 schema。

### 5.2 RAG 服务 Provider

- T11-10 评估讯飞嵌入接口（如 `embedding_v1`）：
  - 若可用：实现 `IflytekSparkEmbedder`，存储维度按讯飞返回为准（如 1024）。
  - 否则：保持 BGE，写入 `docs/llm_provider_matrix.md`。
- T11-11 `pythontopia-rag` 在启动时打印当前 Embedder 与维度，便于排查向量索引维度一致性。
- T11-12 如更换嵌入维度需要重建 `rag_chunk` 表（向量列维度不同），增加迁移 `init_db_v2.sql` 与重建脚本。

### 5.3 后端集成与配置

- T11-20 `application.yml` 增加 `pythontopia.services.providerLabel=${PYT_LLM_PROVIDER:dryrun}`；启动日志中显示当前 Provider。
- T11-21 `ResourceWorkshopService`、`BugMineService` 等调用 graph 服务的入口在响应中携带 `provider_label`，便于回溯。
- T11-22 演示用账号在 `.env.demo` 文件中固定 Provider 与模型，但**不写入仓库**；仓库内仅 `.env.example` 列出变量名。

### 5.4 安全与合规

- T11-30 `pythontopia_graph` 日志中 Provider 密钥必须脱敏；ELK/控制台禁止出现 `api_secret`。
- T11-31 SparkProvider 必须支持配置 `max_tokens`、`temperature`、`top_p`，并允许按 Agent 分别配置（在 `prompts/<agent>.yaml` 中声明）。
- T11-32 增加 `pythontopia_graph/llm/cost_logger.py`：记录每次调用的 token 估算（输入/输出长度），便于演示成本评估。
- T11-33 SparkProvider 在限流/欠费时返回明确 `LlmProviderError`，节点层 fail fast 并把错误写入 `resource_run.status=FAILED`，前端显示"模型限流，请稍后重试"。

### 5.5 测试

- T11-40 单元测试 `tests/llm/test_spark_provider.py`：
  - 用 `respx`/`httpx_mock` 桩 HTTP 响应；覆盖正常响应、限流、欠费、JSON 解析失败 retry。
- T11-41 集成测试（可选 / CI 中跳过）：
  - 在本地用真实 Spark API 跑 `ProfileAgent`、`ResourceDesignerAgent`、`PathPlannerAgent` 各 1 次；用打开标志位 `PYT_RUN_LIVE_LLM=1` 控制。
- T11-42 演示链路自检脚本 `/home/cypress/Pythontopia/deploy/scripts/demo_smoke.py`：
  - 自动登录演示账号 → 触发画像若空 → 触发知识田初始化 → 资源工坊生成 HANDOUT → 路径规划 → 训练场提交 → Bug 矿洞修复。
  - 全程报告状态 + provider_label；任何步骤失败立即退出非零，便于演示前最后 60 秒自检。

## 6. 数据输入与输出

- 输入：环境变量、Spark API 凭据。
- 输出：所有 Agent 的实际 LLM 调用走讯飞；日志中显示 provider_label。
- 不输出业务表变更。

## 7. 验收标准

### 7.1 功能验收

- 切换 `PYT_LLM_PROVIDER=spark` 后：
  - `pythontopia-graph` 启动日志显示讯飞 model_id。
  - 触发画像访谈一次能正常返回结构化响应。
  - 触发资源生成（HANDOUT、MINDMAP、PRACTICE、CODE_LAB、READING、STORYBOARD）各 1 次成功。
  - 触发路径规划与 Bug 矿洞各 1 次成功。
- 缺少任意 Spark 环境变量时启动失败（fail fast）。
- 切换回 `dryrun` 后仍能运行，但启动日志显式提示"非生产 provider"。

### 7.2 质量验收

- 同输入下连续 3 次资源生成，schema 合规率 ≥ 95%；不达标必须调整 prompt 或 `response_format`。
- 引用页码命中率（grounding 检查通过率）≥ 95%。
- safety 节点对"答案泄露"模式仍能拦截。

### 7.3 工程验收

- 单元测试 `test_spark_provider.py` 通过。
- 演示自检脚本 `demo_smoke.py` 在本地真实环境跑通。
- 日志中无任何 `api_secret` 或 `api_key` 明文。

## 8. 验证命令

```bash
export PYT_LLM_PROVIDER=spark
export PYT_SPARK_APP_ID=...
export PYT_SPARK_API_KEY=...
export PYT_SPARK_API_SECRET=...
export PYT_SPARK_BASE_URL=...
export PYT_SPARK_MODEL_ID=...

cd /home/cypress/Pythontopia/services/pythontopia-graph
python -m pytest -q tests/llm/test_spark_provider.py
uvicorn pythontopia_graph.main:app --host 0.0.0.0 --port 18091 --workers 1 &

cd /home/cypress/Pythontopia/deploy
python scripts/demo_smoke.py --base http://localhost:18080 --user demo01 --password demo01
```

## 9. 风险与回滚

- 风险：讯飞 API 限流/欠费导致演示失败；缓解：演示前自检脚本提前 5 分钟跑一次；保留 `OpenAiCompatProvider` 作为最后 fallback（演示中 PPT 注明）。
- 风险：模型回答格式不稳定；缓解：使用 `response_format=json_schema`（若支持）或加 1 轮 retry；不放宽 schema。
- 风险：密钥泄露；缓解：`.gitignore` 包含 `.env`、`.env.demo`；CI 不写入任何 secret；演示机用一次性 token。
- 回滚：把 `PYT_LLM_PROVIDER` 改回 `openai_compat` 或 `dryrun`，无业务数据变更。

## 10. 不做事项

- 不在仓库中提交任何讯飞密钥。
- 不让讯飞 Provider 默认开启 ReAct 循环。
- 不为了"看起来稳定"放宽 grounding/safety/Pydantic schema。
- 不混用不同 Provider 跨 Agent（一次演示链路必须统一 Provider）。
- 不在节点内部直接 import `openai`、`requests`，必须经 `LlmProvider` 抽象。

