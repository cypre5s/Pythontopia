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

## 多模态生成接入（文生图 / 配音 / 合成短视频）

多模态能力统一接入**科大讯飞**，并复用与 Spark LLM 相同的讯飞控制台应用凭据
（`PYT_SPARK_APP_ID` / `PYT_SPARK_API_KEY` / `PYT_SPARK_API_SECRET`），无需额外申请独立 key。

| 能力 | 服务 | 必需 env | 缺失时降级 |
| ---- | ---- | -------- | -------- |
| 配音 TTS | `XfyunTtsService`（`tts-api.xfyun.cn/v2/tts`） | `PYT_SPARK_APP_ID/API_KEY/API_SECRET` | 前端 `speechSynthesis` 浏览器语音 |
| 文生图 | `XfyunImageService`（讯飞星火 TTI，`PYT_SPARK_IMAGE_URL` 可覆盖） | 同上 | 本地占位 PNG（`degraded=true`，`MediaPlaceholderFactory` 内存生成） |
| 短视频合成 | `StoryboardCompositionService`（图序列 + TTS 经 ffmpeg） | 同上 + 容器内 `ffmpeg` | 前端「图 + TTS + 动效」实时剧场（不落地 mp4） |

降级原则：**任一凭据缺失或上游失败都不阻塞业务**，落到本地占位产物并打 `degraded=true` 标记落库
（`media_asset` 表），保证无 apikey 时多模态验收链路仍可完整跑通；配置真实密钥后自动切回真实生成。
多模态产物落盘在 `PYT_MEDIA_GENERATED_DIR`（默认 `./data/course-pack-43/generated-media`，不入 git、可重生）。

> 注：讯飞未提供公开「文生视频」接口，故短视频走「文生图序列 + TTS 配音 → ffmpeg 程序化合成」路线，
> 而非端到端文生视频模型（如 SeeDance）。

## RAG 嵌入接入

- 当前 `pythontopia-rag` 默认使用 384 维 `HashEmbedder`；生产可切到 384 维 `BgeSmallEmbedder`。
- 讯飞嵌入接口（`IflytekSparkEmbedder`）作为阶段 11 占位，引入时需要：
  - 重新计算 `rag_chunk` 向量列维度（通常 1024）。
  - 同步重建索引（`POST /rag/index/rebuild`）。
- 维度切换必须在新的 Flyway 迁移中处理，避免在生产数据库上手工 DDL。
