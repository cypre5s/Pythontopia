# DeepSeek 模型配置

项目的生成式 AI 请求统一走 Graph 的 DeepSeek OpenAI 兼容 provider。provider 会校验
HTTPS 和 `api.deepseek.com` 主机；Spark、微调模型和其它兼容地址不会被选用。Graph 只读取
Pythontopia 自己的 `deploy/.env` 与容器环境，不再回退 Alethicode 的 `.env`。

```dotenv
PYT_LLM_PROVIDER=openai_compat
PYT_OPENAI_BASE_URL=https://api.deepseek.com
PYT_OPENAI_API_KEY=<DeepSeek API key>
PYT_OPENAI_MODEL=deepseek-v4-pro
PYT_OPENAI_MODEL_FAST=deepseek-flash
PYT_OPENAI_MODEL_VISION=deepseek-flash
```

文本消息由默认模型处理；含图消息使用视觉模型。导师和工坊支持 JPEG、PNG、GIF、WebP，
每轮最多 3 张、单张不超过 5 MiB。图片以 base64 `image_url` 内容块直接发给 DeepSeek，
返回仍是文本流；图片不会存入聊天历史。Graph 对图片格式、base64、数量和大小做校验，
视觉模型缺失时拒绝图片请求，不回退到其它图像服务。

RAG 使用本地 hash 向量和 BM25，不调用第三方 embedding API。hash 不写入学习记忆的语义向量表；
当没有语义 embedding 时，记忆检索退回现有 KC/时间/重要度排序。RAG `readyz` 在 BM25
索引可用但语义向量降级时返回 `ready_degraded`，索引状态仍显式标记 degraded。

配音、语音识别、浏览器语音输入和独立文生图/图片理解入口关闭。导师图片理解由 DeepSeek
直接完成；视频讲解的文字摘要也使用同一 DeepSeek provider，视频时间点只取真实分 P 元数据，
不请求第三方 AI 总结。

密钥只放在被 Git 忽略的 `deploy/.env`，不可提交、打印到日志或写入文档。生产配置门禁要求
DeepSeek API key、文本模型和视觉模型，并拒绝非 DeepSeek 地址及外部 embedding provider。

官方图片输入格式与模型说明：[DeepSeek Vision 文档](https://api-docs.deepseek.com/guides/vision/)。
