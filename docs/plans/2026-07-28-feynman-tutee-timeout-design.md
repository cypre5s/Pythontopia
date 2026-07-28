# 费曼 AI 学弟生产超时收口设计

## 背景

ECS HTTPS 真实闭环连续两次在教学回合显示“重试提交”。后端日志均明确为
`POST /agents/feynman/tutee/step` 在 30 秒处 `Read timed out`；代码找茬、会话创建和真实 WA
证据均已成功。当前 `FeynmanGraphClient` 与短请求共用 `graphRestTemplate`，而教学端点需要一次
真实 LLM 理解、误区更新和可判题代码生成，30 秒预算与生产耗时不匹配。前端另有 35 秒预算，
即使只放宽后端也会由浏览器先中止。

## 方案比较

1. **全局 Graph 超时改为 90 秒**：实现最少，但意图路由、RAG、普通工具调用都会失去 30 秒
   fail-fast 边界，扩大线程占用与故障半径，不采用。
2. **30 秒失败后自动重试教学调用**：会重复触发同一 LLM 生成，增加成本且可能产生两个不同回答；
   两次 30 秒仍已在公网实测失败，不采用。
3. **费曼教学端点使用专用客户端（采用）**：连接超时仍为 3 秒，只把
   `/agents/feynman/tutee/step` 的读取预算设为 90 秒；代码找茬和其他 Graph 调用保持 30 秒。
   前端教学请求设为 95 秒，为公网代理和 JSON 映射保留 5 秒。

## 数据流与错误边界

`FeynmanGraphClient.step` 使用 `feynmanTuteeRestTemplate`；
`FeynmanGraphClient.bugExposure` 继续使用 `graphRestTemplate`。90 秒内成功时沿用既有结构化
响应、grounding 和真判题链；超过 90 秒仍返回现有 `FEYNMAN_TUTEE_UNAVAILABLE`，前端保留输入并
展示可见重试，不增加静默降级或伪造代码。

## 验收

- 后端属性缺失或非正值回落 90000 ms；
- 专用 RestTemplate 使用独立读取超时且沿用 Graph 连接超时；
- Spring 全上下文能唯一注入两个 RestTemplate；
- 前端教学请求为 95000 ms，代码找茬仍为 35000 ms；
- ECS HTTPS 真实执行 WA → 找茬 → 教学产码 → 沙箱判题 → 掌握度回流；
- 课件外概念仍为 `NA`，真判题禁用且不计入掌握度。
