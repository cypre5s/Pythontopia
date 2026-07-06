# Pythontopia 三大 AI 创新点答辩稿

更新日期：2026-07-06

这份文档面向保研面试、课程答辩和老师追问。讲法要克制：只讲已经在仓库中能找到代码、数据、测试或报告证据的部分；不要把规划中的能力说成已经完全上线。

## 0. 一句话总述

Pythontopia 的 AI 创新不是“给网页套一个聊天框”，而是把课程证据、学生画像、资源生成、练习判题、错因调试和学习路径重规划做成一个可追踪的闭环。模型微调、多智能体、多模态资源都服务同一个目标：让 Python 初学者下一步知道该看什么、练什么、为什么错、什么时候通过。

## 1. 创新点一：证据闭环型多智能体导学

### 我会怎么讲

传统 AI 助教容易停在“问一句答一句”。Pythontopia 把导学拆成多个职责明确的 Agent：画像、课程映射、资源设计、练习生成、代码实验、路径规划、调试、安全和证据审查。每次生成都要求带 run id、agent name、输入摘要、输出 artifact、证据页、安全审查和接地审查结果。

这套设计的关键不是 Agent 数量多，而是 Agent 输出会回到真实学习事件：阅读页、资源消费、正式练习提交、掌握度变化、任务 evidence、错因复盘。系统据此决定通过、补救、换资源或重规划，而不是靠一次静态问卷做推荐。

### 老师可能追问

| 追问 | 答法 |
| --- | --- |
| 这和普通 RAG 问答有什么区别？ | RAG 只解决“回答要引用资料”。Pythontopia 还把回答后的行为接到练习提交、任务 evidence、画像维度和路径重排。也就是说，回答不是终点，后续学习动作会继续反哺系统。 |
| 多智能体是不是堆概念？ | 我按职责拆分，不按噱头拆分。比如 `GroundingCriticAgent` 专门查证据边界，`SafetyAgent` 专门查隐藏测试和完整答案泄露，`PathPlannerAgent` 专门把学习结果转成下一步路径。职责边界能在代码和测试中看到。 |
| 怎么防幻觉？ | 课程内容来自 `packId=43`，RAG/Agent 输出必须绑定 page evidence；无课件证据时要标注 `common_knowledge`，不能伪造课件页码。生成资源还要走 grounding 和 safety 状态。 |

### 证据入口

- `services/pythontopia-graph/`
- `services/pythontopia-rag/`
- `backend/src/main/java/com/pythontopia/`
- `docs/reports/2026-07-01-launch-deep-chain-acceptance.md`

## 2. 创新点二：本地 Qwen3.5/vLLM/QLoRA 结构化 Agent 微调

### 我会怎么讲

我没有把整个系统押在一个大模型上，而是把微调用在一个窄而关键的问题上：让本地模型更稳定地产出 Pythontopia Agent 可解析、可审查、可落库的 JSON。目标不是“更会聊天”，而是提升结构化输出、证据边界和安全拒答边界。

实验路线是：

1. 用本地 Qwen3.5-4B AWQ + vLLM 做 baseline。
2. 构造 1000 条 Pythontopia 真实 Agent case，覆盖 Tutor、Practice、ResourceDesigner、Debug、Safety、GroundingCritic 等 10 类 Agent。
3. 先用 E1 protocol patch 提升 teacher 输出质量。
4. 从 1000 条中筛出 975 条干净响应，按 780 train / 195 heldout 做 SFT。
5. 在 8 GB 显存笔记本上用 QLoRA 训练轻量 adapter。
6. 用 heldout、1000 条新手表达噪声、Graph route A/B 和 Web smoke 验证。
7. 对 SafetyAgent 和 DebugAgent 保留 deterministic guard/fallback，不让模型裸奔上线。

### 最关键结果

| 阶段 | JSON valid | Grounding safe | Forbidden leak |
| --- | ---: | ---: | ---: |
| Raw AWQ baseline, 1000 case | 0.686 | 0.822 | 0.014 |
| E1 protocol patch, 1000 case | 0.980 | 0.963 | 0.007 |
| QLoRA adapter + beginner noise, 1000 case | 0.990 | 0.961 | 0.005 |
| Guarded v1, 1000 case | 1.000 | 1.000 | 0.000 |

训练层 heldout 指标：

| 指标 | 值 |
| --- | ---: |
| Eval loss, epoch 2 | 0.241026 |
| Eval token accuracy, epoch 2 | 0.917043 |
| Raw adapter vs heldout baseline score delta | +0.020940 |
| Raw adapter JSON delta | +0.194872 |
| Raw adapter grounding delta | +0.153846 |

### 老师可能追问

| 追问 | 答法 |
| --- | --- |
| 为什么不是直接调大模型 API？ | 本项目要可复现、可演示、可断网运行。微调对象是结构化 Agent JSON 这种窄任务，本地 4B + adapter 足够验证路线。线上仍保留 provider fallback，不把系统可靠性押在本地模型上。 |
| 微调是不是只是在背答案？ | 训练输入是 Agent 场景、画像、误区、证据和输出合同，目标是结构化行为；评测有 heldout 和新手表达噪声，不是训练集回放。并且 raw adapter 没有被夸大，报告明确写了它还需要 guard。 |
| 为什么 guard 后 1.000 不算刷分？ | guard 解决的是系统上线必须确定性满足的安全/证据契约，例如 JSON schema、required evidence、forbidden scanner。我的结论不是“模型完美”，而是“模型 + validator + repair/fallback”作为工程系统能满足业务边界。 |
| 权重为什么没上传 GitHub？ | `.venv`、Qwen/AWQ 权重、LoRA adapter 权重和长跑缓存太大，也受模型 license/模型仓库管理约束。GitHub 保留可复现实验代码、case、SFT split、训练计划、报告和小型结果摘要。 |

### 证据入口

- `research/vllm-agent-ft-lab/README.md`
- `research/vllm-agent-ft-lab/MODEL_ARTIFACTS.md`
- `docs/reports/2026-07-03-qwen-vllm-finetuning-method-report.md`
- `docs/reports/2026-07-03-vllm-1000-beginner-noise-multifold-eval.md`
- `docs/reports/2026-07-03-vllm-safety-debug-guard-fix.md`
- `docs/reports/2026-07-02-fine-tuned-llm-web-ab.md`

## 3. 创新点三：多模态学习资源编排

### 我会怎么讲

Python 初学者的困难不只来自概念本身，还来自表达方式和学习媒介不匹配。有的人适合代码练习，有的人适合流程图，有的人需要短视频或截图引导。Pythontopia 把学生画像中的 `media_priority` 和学习状态接入资源生成，不只生成文字，还组织代码练习、Mermaid/思维导图、视频速览、图像提示词、操作截图和小镇任务入口。

这里的多模态不是盲目生成花哨媒体，而是被教学闭环约束：资源必须绑定 KC、证据页、学习目标和下一步练习。视频速览只依据真实标题、简介、时长或时间轴概括；图像/视频生成产物作为可重生本地缓存，不把它当作课程事实来源。

### 老师可能追问

| 追问 | 答法 |
| --- | --- |
| 多模态具体在哪里？ | Graph schema 有 `media_priority`、`diagram`、`image_prompt`、B 站视频速览和资源 media 字段；前端指南有真实截图；课程包有视频/PPT/页面证据；工坊可按学生偏好生成不同资源形态。 |
| 会不会为了炫技牺牲教学？ | 项目规则要求“教育优先，游戏承载”。多模态资源必须服务 KC、掌握度和练习评估；纯装饰媒体不进入主链路。 |
| 视频速览会不会编造视频内容？ | 视频速览 prompt 明确只能依据标题、简介、时长和真实元数据概括；不能臆造未出现的章节、数字和结论。 |
| 现在是不是已经有完整长视频生成？ | 不是。当前主线是资源编排、图像提示词、短视频脚本/生成媒体缓存和真实视频速览；不把“真实长视频自动渲染”说成已经完成。 |

### 证据入口

- `services/pythontopia-graph/pythontopia_graph/schemas/resource.py`
- `services/pythontopia-graph/pythontopia_graph/schemas/tutor.py`
- `services/pythontopia-graph/pythontopia_graph/schemas/profile.py`
- `services/pythontopia-graph/pythontopia_graph/routes/video.py`
- `services/pythontopia-graph/pythontopia_graph/routes/video_insight.py`
- `frontend/public/guide/screenshots/`

## 4. 面试时的 60 秒版本

如果老师只给我一分钟，我会这样说：

Pythontopia 的核心 AI 创新有三点。第一，它不是聊天机器人，而是证据闭环型多智能体导学：课程 RAG、学生画像、资源生成、练习判题、错因调试和路径重排互相反馈。第二，我做了本地 Qwen3.5/vLLM/QLoRA 微调，把目标收窄到结构化 Agent JSON、证据边界和安全边界；1000 case baseline JSON 有效率从 0.686 提到 E1 的 0.980，QLoRA 在新手噪声下保持 0.990，guard 后 JSON/grounding/leak 达到 1.000/1.000/0.000。第三，我做的是多模态学习资源编排，按学生画像和学习状态组织文本、代码、思维导图、视频速览、图像提示词和小镇任务，而不是只给自然语言答案。三点共同目标是：让学生每一步学习都有证据、有反馈、有下一步。

## 5. 绝不能夸大的边界

- 不说“模型已经完全替代教师或后端判题”。
- 不说“微调模型裸输出可以直接上线”，正式链路必须保留 guard/fallback。
- 不说“多模态长视频自动生成已经完整生产化”，当前主线是资源编排和可重生媒体产物。
- 不说“GitHub 包含模型权重”，GitHub 包含可复现实验材料，权重需按 artifact policy 重建或放模型仓库。
- 不说“所有功能都已生产规模压测”，本地 vLLM 适合演示、评测和异步资源生成，高并发课堂仍要走 fallback 和生产级模型服务。
