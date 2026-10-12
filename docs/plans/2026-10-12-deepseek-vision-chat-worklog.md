# DeepSeek Vision Chat 工作日志

## 2026-10-12

- 用户确认只改导师/工坊聊天，由 DeepSeek 直接处理文字与图片并输出文字。
- 复用导师 API 的 `images` DTO 和现有 Graph Provider；Spring 直接转发图片，Graph 校验 base64、格式和上限后构造 DeepSeek 图片内容块。
- 含图请求使用单独的 `PYT_OPENAI_MODEL_VISION`；本地 `deploy/.env` 配置 DeepSeek key 与 `deepseek-flash`，保留 NPC 快档模型。密钥未写入跟踪文件或日志。
- 工坊 composer 新增图片 Data URL 附件，最多 3 张、每张 5 MiB；工坊文档附件仍走既有提取链路。图片仅用于当轮，不持久化。
- 现有课件 RAG、grounding、安全审查及 SSE 文本答复保持接入；含图问题不进入不支持图像的 debate/quiz 工具路径。
- Graph 图片输入与 provider 定向回归：24 项通过；Ruff 通过。
- 工坊图片 composer 定向测试通过，完整前端测试与 `npm run typecheck` 通过；lint 0 errors（116 warnings）。
- Java Tutor 请求转发和 SSE 定向测试通过。Maven 全量运行 1124 项、0 assertion failures，但 6 个 Testcontainers 集成测试因 Docker 随机宿主端口冲突启动失败；串行重跑这 6 个类共 18 项通过。
- `deploy/.env` 仅在 Git 忽略文件中保存凭证；视觉模型与 DeepSeek 地址已检查匹配。
- 未发送真实 DeepSeek API 请求；凭证未打印，也未写入跟踪文件。
