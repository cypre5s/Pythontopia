# Qwen3.5 vLLM 微调方法、效果与项目作用报告

更新日期：2026-07-03

## 0. 给第一次了解项目的读者

Pythontopia 是一个面向高校 Python 初学者的网页学习系统。它不是单纯的聊天机器人，而是把课程课件、学生画像、练习判题、个性化资源生成、学习路径规划和像素小镇任务串在一起。学生在系统里可能会经历这样的闭环：

1. 进入 Python 学习小镇，选择当前章节或任务。
2. 阅读课程资源，系统记录阅读证据。
3. 在“百晓工坊”里提问或请求生成学习资源。
4. Agent 根据学生画像、KC 掌握度、课件证据和练习记录生成提示、资源、练习或路径建议。
5. 学生完成微测或正式练习提交，后端判题并记录结果。
6. 系统根据结果更新画像、掌握度、任务状态，并决定通过、补救、换资源或重规划。

这里的“微调 LLM”服务的是第 3-4 步：当系统需要 AI Agent 产出结构化结果时，让本地模型更稳定地返回项目可解析、可审查、可落库的 JSON。它不替代后端、前端、判题器、资源证据库或 Agent 编排器。

### 0.1 本报告回答什么问题

本报告回答五个问题：

- 为什么选择 Qwen3.5-4B，而不是更大的模型。
- 为什么需要 vLLM、AWQ、LoRA/QLoRA 这些组件。
- 微调数据从哪里来，为什么它贴近 Pythontopia 的真实业务。
- 微调后效果如何，哪些指标已经变好，哪些还不能夸大。
- 微调模型在项目里如何接入，项目没有它时是否还能运行。

### 0.2 先记住一句话

vLLM 是“本地模型服务员”，LoRA adapter 是“项目特化小脑”，Graph Agent 才是“会调度工具、证据、资源和学习闭环的业务大脑”。本轮微调增强的是模型生成结构化 Agent JSON 的能力，而不是把整个 Pythontopia 改造成一个模型。

### 0.3 关键术语表

| 术语 | 在本项目里的意思 |
|---|---|
| Qwen3.5-4B | 被微调和服务的基础大模型。4B 规模适合 8 GB 显存本地实验。 |
| AWQ | 一种权重量化格式。这里用于把 Qwen3.5-4B 压到本机可以用 vLLM 服务的显存范围。 |
| vLLM | 高性能 LLM 推理服务。它提供 OpenAI-compatible `/v1/chat/completions` 和 `/v1/models` 接口。 |
| LoRA | 低秩适配器微调方法，只训练少量 adapter 权重，不重训整个模型。 |
| QLoRA | 在量化模型/低显存环境中训练 LoRA 的方法。这里用于 8 GB 显存笔记本训练 adapter。 |
| SFT | Supervised Fine-Tuning，监督微调。这里用清洗后的 Agent JSON 响应作为训练目标。 |
| E1 protocol patch | 一个 prompt 层协议补丁，用来让模型先学会严格 JSON、证据边界和安全输出。 |
| heldout | 未参与训练的保留评测集，用来观察 adapter 是否泛化。 |
| contract pass | `json_valid && schema_valid && grounding_safe && !forbidden_leak`，是当前首要质量指标。 |
| grounding | 输出是否绑定课件证据或明确标注 common_knowledge，不伪造页码或教材内容。 |
| forbidden leak | 是否泄露完整答案、隐藏测试或可直接提交的完整代码。 |
| guard repair / fallback | 分别表示 guard 修复裸输出的比例、以及 guard 无法安全修复而回退的比例；必须与 raw 指标分层报告。 |
| fallback provider | 本地 adapter 不可用或不适合时，Graph 回退到原有 LLM provider 或 dry-run。 |
| JSON mode Agent call | Graph Agent 需要结构化 JSON 的调用，例如资源设计、练习提示、路径建议。 |

## 结论摘要

本轮工作选择的是“本地 Qwen3.5-4B + vLLM 服务 + QLoRA 适配器 + Graph Agent fallback”的路线。它不是把 Pythontopia 的 Agent 重写成一个模型，而是给现有 `pythontopia-graph` 增加一个可选的本地结构化 JSON 生成脑，用于百晓工坊、资源生成、导学、练习提示、路径规划等 Agent 调用。

当前已经完成并有文件证据的结论是：

- 在 8 GB RTX 5060 Laptop GPU 上，全精度 Qwen3.5-4B 依赖 CPU offload 时不稳定；AWQ 4-bit 版本可以通过 vLLM 稳定服务。
- 原始 AWQ baseline 在 1000 个 Pythontopia Agent case 上有明显结构化协议问题：JSON valid rate 为 `0.686000`，grounding safe rate 为 `0.822000`。
- E1 protocol patch 在同一 1000 case 上显著提高结构化可靠性：JSON valid rate 到 `0.980000`，grounding safe rate 到 `0.963000`，forbidden leak rate 从 `0.014000` 降到 `0.007000`。
- 之后将 E1 中审计干净的 975 条响应蒸馏成 SFT 数据，使用 4-bit QLoRA 训练本地 adapter。adapter 在 195 heldout case 上优于 raw baseline：score `+0.020940`，JSON `+0.194872`，grounding `+0.153846`，leak `-0.015385`。
- raw adapter 还没有超过 E1 prompt module。加入确定性 JSON/evidence grounding guard 后，heldout gate 才达到严格通过：score `1.000000`，JSON `1.000000`，grounding `1.000000`，leak `0.000000`。
- vLLM 已能同时暴露 base 与 LoRA：`pythontopia-qwen35-awq-base` 和 `pythontopia-qwen35-e1-lora`。Graph 侧仅在结构化 JSON Agent 调用时启用 adapter；流式文本、工具调用、fast tier、adapter 失败都会回落到原 provider，因此 Agent 不依赖本地 vLLM 才能运行。
- 用户要求的“增强新手表达后的 1000+ 大样本 5-fold vLLM 长测”已经补齐，正式报告见 `docs/reports/2026-07-03-vllm-1000-beginner-noise-multifold-eval.md`。在 1000 条新手噪声样本上，正式 run 的 JSON valid rate 为 `0.990000`，grounding safe rate 为 `0.961000`，forbidden leak rate 为 `0.005000`。

本报告聚焦“模型如何微调、为什么这样选、它在项目中起什么作用”。正式大样本运行的 fold、Agent、噪声类型和 latency 细节放在新手噪声多折评测报告中，避免把方法报告膨胀成全部实验明细。

## 1. 为什么选择这条路线

### 1.1 项目约束

Pythontopia 的 AI 能力不是普通聊天，而是服务于真实学习闭环：

- `ProfileAgent`、`ResourceDesignerAgent`、`PracticeAgent`、`PathPlannerAgent`、`GroundingCriticAgent`、`SafetyAgent` 等需要输出结构化 JSON。
- 资源生成必须绑定课程证据，不能伪造课件页码。
- 练习和调试提示不能泄露完整可提交答案。
- Web/后端/graph 的主链路必须在本地 vLLM 不存在时继续运行。

所以微调目标不是“让模型更会聊天”，而是让本地模型更稳定地产出项目可消费、可审计、可回退的 Agent JSON。

更具体地说，系统希望模型输出的不是散文式回答，而是类似这样的业务对象：

```json
{
  "diagnosis": "把赋值理解成数学等式",
  "next_hint": "先观察变量名左侧和表达式右侧分别代表什么",
  "evidence_ids": ["page-1-006"],
  "safety_flags": []
}
```

如果模型返回 Markdown、夹杂解释文字、漏掉 `evidence_ids`、乱编页码，或者直接给出完整答案，前端和后端就很难把它接入“看、学、练、测”的闭环。因此本轮微调最看重结构化可靠性、证据边界和安全边界。

### 1.2 硬件约束

实测硬件为 NVIDIA GeForce RTX 5060 Laptop GPU，显存约 8 GB。初期尝试加载 `/home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B` 全精度/半精度权重时，CPU offload 触发 vLLM V1 hybrid KV/offload 相关限制，服务不可稳定作为评测基座。

因此采用：

- 服务基座：`research/vllm-agent-ft-lab/models/QuantTrio-Qwen3.5-4B-AWQ`
- 推理框架：`vllm==0.17.1`
- adapter 服务：vLLM `--enable-lora`
- 训练方法：4-bit QLoRA

这个组合的原因很直接：AWQ 降低推理显存，QLoRA 降低训练显存，LoRA adapter 便于在不复制整模型的情况下迭代项目特化行为。

### 1.3 为什么不是直接用更大模型或云 API

更大模型和云 API 当然可能更强，但它们不是本阶段的最佳实验对象：

- 本地比赛/演示需要可控性：断网、限流、余额、外部 API 波动都会影响展示链路。
- 项目需要可复现实验：同一 case、同一 seed、同一模型服务版本，才能判断模块是否真的涨点。
- 结构化 Agent JSON 是窄任务：它更像“把项目协议学稳”，不一定要用最大参数模型。
- 8 GB 显存上更大的全精度模型不可行；即使用 offload，也会牺牲稳定性和延迟。
- LoRA adapter 可以独立保存、加载、比较、回滚，适合持续实验。

因此选择“本地 4B 量化推理 + 小 adapter 微调”是为了在当前电脑上建立一条可跑、可测、可复现、可回退的路线。

### 1.4 为什么先做 E1 protocol patch，再做 QLoRA

直接把 raw baseline 的回答拿去训练会把坏习惯也学进去，例如 Markdown fence、字段缺失、证据缺失。E1 protocol patch 的作用是先在 prompt 层制造一个更好的 teacher：

1. 用项目真实 case 询问同一个 base model。
2. 在 prompt 中加入严格 JSON、证据、安全和字段合同。
3. 运行 1000 case 并审计响应。
4. 只把干净的 teacher response 留作 SFT 目标。
5. 训练时移除 E1 patch 文本，让 adapter 从原始 user/system 输入学会这种输出风格。

这条路线本质是“prompt 发现可行协议 -> 蒸馏成轻量 adapter -> 再用 guard 和 fallback 兜底”。它比一开始就盲目训练更稳。

## 2. 微调数据是怎么来的

### 2.1 先做 1000 个项目真实 Agent case

主数据集为：

`research/vllm-agent-ft-lab/cases/domain_cases_1000_v1.jsonl`

数据覆盖 10 个 Agent，每个 Agent 100 条，共 1000 条：

- TutorAgent
- PracticeAgent
- ResourceDesignerAgent
- IntentRouterAgent
- GuideCoachAgent
- DebugAgent
- GroundingCriticAgent
- SafetyAgent
- PathPlannerAgent
- MindMapAgent

case 包含章节、KC、学习信号、画像、课堂场景、误区线索、证据页、输出合同和 forbidden-answer-risk 标记。它比早期 140 case 更接近项目真实业务，因为它不只换题面，还组合了 learner profile、learning signal、classroom scene、instruction style 和 task focus。

### 2.2 用 E1 protocol patch 生成 teacher 行为

直接让 baseline 学生模型回答时，结构化可靠性不够。1000 case baseline 指标为：

| 指标 | Raw AWQ baseline |
|---|---:|
| case count | 1000 |
| score mean | 0.962467 |
| score std | 0.092543 |
| JSON valid rate | 0.686000 |
| grounding safe rate | 0.822000 |
| forbidden leak rate | 0.014000 |

于是先加入 E1 protocol patch，让同一个 base model 在 prompt 层显式遵守：

- 只输出 JSON
- evidence 或 common_knowledge 边界明确
- 不输出完整答案
- 字段必须匹配 Agent 合同

E1 在同一 1000 case 上的结果：

| 指标 | E1 protocol patch | 对 baseline 变化 |
|---|---:|---:|
| score mean | 0.997050 | +0.034583 |
| score std | 0.024754 | -0.067789 |
| JSON valid rate | 0.980000 | +0.294000 |
| grounding safe rate | 0.963000 | +0.141000 |
| forbidden leak rate | 0.007000 | -0.007000 |

这一步的作用不是最终方案，而是生成“较干净、较可控”的 teacher response，作为后续 SFT 蒸馏来源。

### 2.3 审计后蒸馏成 SFT 数据

从 E1 1000-case run 中审计得到：

- clean responses：975 / 1000
- train：780
- heldout：195
- input policy：训练输入使用原始 case system/user，不包含 E1 protocol patch 文本
- target：E1 的干净 assistant JSON response

SFT manifest：

`research/vllm-agent-ft-lab/outputs/sft/e1-protocol-patch-v1-1000-v1/manifest.json`

这样做的目的，是把 prompt 层的协议行为部分蒸馏进 adapter，而不是让线上每次都依赖很长的 protocol patch。

## 3. QLoRA 微调方法

训练计划文件：

`research/vllm-agent-ft-lab/outputs/training/e1-protocol-patch-v1-1000-v1-full/training_plan.json`

核心参数：

| 项 | 值 |
|---|---|
| base model | `/home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B` |
| method | `qlora_4bit` |
| max sequence length | 768 |
| LoRA rank | 8 |
| LoRA alpha | 16 |
| LoRA dropout | 0.05 |
| target modules | `q_proj,k_proj,v_proj,o_proj,gate_proj,up_proj,down_proj` |
| epochs | 2 |
| learning rate | 0.0002 |
| gradient accumulation | 16 |
| gradient checkpointing | true |
| bf16 | true |

选择这些参数的原因：

- rank 8 与 alpha 16：adapter 约 21 MiB，足够轻，适合多次实验和 vLLM LoRA 热加载。
- 目标模块覆盖 attention 和 MLP：不仅调格式，也调 Agent JSON 字段、证据字段、提示风格等生成习惯。
- max sequence length 768：覆盖当前 Agent case 的 system/user/JSON target，同时控制 8 GB 显存压力。
- per-device batch size 1 + gradient accumulation 16：牺牲速度换显存稳定。
- 2 epoch：先验证趋势和 heldout 泛化，不在小数据上过度训练。

训练命令由 `scripts/preflight_training.py` 生成 plan，再由 `scripts/train_lora.py` 执行。训练环境预检全部通过，关键依赖包括 `torch==2.10.0+cu128`、`transformers==5.13.0.dev0`、`peft==0.19.1`、`trl==0.29.1`、`bitsandbytes==0.49.2`。

## 4. 训练效果

训练输出：

`research/vllm-agent-ft-lab/outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch/`

训练曲线：

`research/vllm-agent-ft-lab/outputs/training/e1-protocol-patch-v1-1000-v1-full/report/training_curve.json`

关键结果：

| Epoch | Step | Eval loss | Eval token accuracy |
|---:|---:|---:|---:|
| 1 | 49 | 0.290419 | 0.907434 |
| 2 | 98 | 0.241026 | 0.917043 |

解释：

- heldout loss 从 epoch 1 到 epoch 2 下降，说明 adapter 确实学到了 E1 teacher 的结构化输出分布。
- token accuracy 提升到 `0.917043`，说明格式、字段、证据标记等局部 token 预测变得更稳定。
- 这只是训练层证据，还不能替代真实生成评测。

## 5. 生成评测效果

### 5.1 Raw adapter heldout generation

运行：

`research/vllm-agent-ft-lab/runs/adapter-offline-heldout-195-20260702T142200Z`

| 指标 | Raw adapter heldout |
|---|---:|
| case count | 195 |
| score mean | 0.989060 |
| score std | 0.050484 |
| JSON valid rate | 0.943590 |
| grounding safe rate | 0.953846 |
| forbidden leak rate | 0.000000 |

相对同一 heldout slice 的 raw baseline：

| 指标 | Delta |
|---|---:|
| score mean | +0.020940 |
| JSON valid rate | +0.194872 |
| grounding safe rate | +0.153846 |
| forbidden leak rate | -0.015385 |

结论：raw adapter 已经优于 raw baseline，但还没有超过 E1 prompt module，不能单独宣称“微调完全替代协议补丁”。

### 5.2 Adapter + grounding guard

运行：

`research/vllm-agent-ft-lab/runs/adapter-offline-heldout-195-grounding-repair-v1-20260702Tnow`

| 指标 | Adapter + grounding guard |
|---|---:|
| case count | 195 |
| score mean | 1.000000 |
| score std | 0.000000 |
| JSON valid rate | 1.000000 |
| grounding safe rate | 1.000000 |
| forbidden leak rate | 0.000000 |

相对同一 heldout baseline：

| 指标 | Delta |
|---|---:|
| score mean | +0.031880 |
| JSON valid rate | +0.251282 |
| grounding safe rate | +0.200000 |
| forbidden leak rate | -0.015385 |

结论：更适合项目的不是“裸 adapter 直接上线”，而是“adapter-first + deterministic grounding/JSON guard + fallback”。这也符合 Pythontopia 的防幻觉规则：模型负责生成，守门逻辑负责证据边界和结构可消费性。

### 5.3 vLLM LoRA serving

服务脚本：

`research/vllm-agent-ft-lab/scripts/serve_qwen35_lora.py`

服务模型证据：

`research/vllm-agent-ft-lab/outputs/serving/vllm_models_1000fold_20260703.json`

vLLM `/v1/models` 暴露：

- `pythontopia-qwen35-awq-base`
- `pythontopia-qwen35-e1-lora`

12 个既往 failure case 的 vLLM LoRA 验证：

| Run | Cases | Score | JSON | Grounding | Leak |
|---|---:|---:|---:|---:|---:|
| raw vLLM LoRA | 12 | 0.979167 | 0.916667 | 0.833333 | 0.000000 |
| vLLM LoRA + guard | 12 | 1.000000 | 1.000000 | 1.000000 | 0.000000 |

这说明 vLLM 服务路径能加载 adapter。后续正式可靠性已由新手噪声 1000+ 大样本 5-fold 长测补齐：`docs/reports/2026-07-03-vllm-1000-beginner-noise-multifold-eval.md`。

## 6. vLLM 在项目中的作用

vLLM 在 Pythontopia 中承担的是“本地模型推理服务层”，不是 Agent 编排层。

当前项目链路是：

`frontend -> backend -> services/pythontopia-graph -> LLM provider -> vLLM/OpenAI-compatible endpoint`

对应代码：

`services/pythontopia-graph/pythontopia_graph/llm/provider.py`

关键设计：

- `FineTunedFallbackProvider` 只在 `json_mode=true` 且无 tool calling、非 fast tier 时调用本地 adapter。
- `chat_stream()` 永远走 fallback provider，避免本地 adapter 影响 SSE 文字流。
- adapter 异常会被捕获并回退到原 provider。
- `PYT_FINE_TUNED_LLM_ENABLED=false` 时，项目完全不走本地 vLLM。

因此：

- vLLM 提供更低成本、本地可控、可复现实验的结构化生成能力。
- Graph Agent、ResourceGraph、Safety/Grounding guard、后端 persistence、Web UI 都仍然独立存在。
- 没有本地 vLLM 时，AGENT 仍可通过原 provider 或 dry-run/fallback 路径运行。
- 有本地 vLLM 时，项目可以把结构化 JSON 生成优先交给 `pythontopia-qwen35-e1-lora`，减少 raw baseline 的 JSON/证据不稳定问题。

## 7. 为什么下一步要做新手表达增强长测

当前 1000 case 的原始 user prompt 相对规范，像“样本场景 1: 围绕某 KC，必须引用课件证据……”这种实验题面。真实 Python 初学者在百晓工坊中更可能出现：

- 描述不清楚：只说“这个不对”“为啥跑不出来”，没有明确目标。
- 句子不连贯：把错误、题目、自己的猜测混在一起。
- 术语混用：把变量赋值说成“等式”，把 `range` 右边界理解成包含。
- 错误消息复制不完整：只贴半截 traceback 或只说红字。
- 概念和自然语言迁移：把数学、自然语言经验误套到程序语义上。

这些增强方向有文献依据：

- Qian 和 Lehman 的 introductory programming literature review 将新手困难归为 syntactic、conceptual、strategic knowledge，并指出自然语言、数学知识、错误心智模型和缺少策略会影响学习困难：[DOI 10.1145/3077618](https://doi.org/10.1145/3077618)。
- “What does this Python code do?” 的 Python 初学者代码解释研究显示，学生会选择性关注输出、遗漏变量赋值和输入等元素，也会被新学构造附近的代码干扰：[TU Delft repository PDF](https://repository.tudelft.nl/file/File_b2b4d597-6d54-4916-9e1c-a561678715a2)。
- 2025 年 Python misconceptions 检测研究强调，单元测试只能判断输出，难以解释误区来源；需要更细的误区症状识别和反馈：[OASIcs Programming 2025 paper](https://drops.dagstuhl.de/storage/01oasics/oasics-vol134-programming2025/OASIcs.Programming.2025.21/OASIcs.Programming.2025.21.pdf)。
- 新手错误消息研究指出，错误消息的可读性会受到术语、长度、句式和词汇影响，这支持在评测里加入“半截错误消息、术语不准、表达混乱”的输入扰动：[ACM SIGCSE 2021 paper](https://dl.acm.org/doi/fullHtml/10.1145/3411764.3445696)。

样本增强已按这个原则落地：在不改变 expected markers、evidence_ids、Agent 合同的前提下，重写 `user` 字段，加入上述新手表达噪声。这样既能更接近真实项目输入，又不会破坏原有可评分性。

## 8. 当前边界与后续验收

已经有证据的完成项：

- 本地模型选择、vLLM 服务、QLoRA 训练、heldout 评测、vLLM LoRA smoke、Graph provider fallback 都已有文件记录。
- `docs/project/CHANGELOG.md` 已记录 2026-07-03 vLLM 1000+ 多折评测最终指标。
- `research/vllm-agent-ft-lab/docs/process-log.md` 已记录 2026-07-02 至 2026-07-03 的主要实验链路。
- `docs/reports/2026-07-03-vllm-1000-beginner-noise-multifold-eval.md` 已记录增强样本、正式 run、图表、fold 指标、Agent 切片和风险结论。

仍需完成的项：

- 将 SafetyAgent deterministic refusal template、DebugAgent evidence repair 和 Graph 端 schema/grounding guard 落到正式业务链路。
- 用浏览器和 API 端到端验证百晓工坊接入后的真实调用、fallback 和失败恢复。

本轮长测已满足的验收口径：

- `summary.json.overall.case_count = 1000`
- `metrics.csv` 有 1000 行指标
- `responses.jsonl` 有 1000 条模型响应
- 5 个 fold 各 200 条样本
- JSON valid、grounding safe、forbidden leak、score、latency 已有总体与 fold 统计
- 图表文件存在且非空
- 报告明确区分“raw adapter 能力”“guard 后能力”“vLLM 服务能力”“Graph 项目集成能力”

## 9. 项目架构补充：Agent、Graph、vLLM 的关系

对没有见过 Pythontopia 的读者，可以把系统分成五层：

| 层 | 作用 | 本轮是否修改 |
|---|---|---|
| 前端 `frontend/` | Vue + Phaser，小镇、工坊、练习、教师界面 | 不修改 |
| 后端 `backend/` | Spring Boot，用户、课程、资源、判题、学习事件、数据库 | 不改 Web/后端主业务 |
| Graph 服务 `services/pythontopia-graph/` | 多 Agent 编排、资源生成、证据审查、LLM provider | 已接入可选 fine-tuned provider |
| RAG 服务 `services/pythontopia-rag/` | 课程课件检索和证据引用 | 不修改 |
| vLLM 实验服务 `research/vllm-agent-ft-lab/` | 本地 Qwen3.5-AWQ + LoRA adapter 推理 | 本轮实验主目录 |

用户请求资源或提示时，大致链路是：

```text
学生在 Web 工坊提问
  -> backend 接收请求并转发/编排
  -> pythontopia-graph 判断需要哪个 Agent
  -> Agent 组装 system/user 消息、证据、画像和输出合同
  -> LLM provider 决定调用 fine-tuned vLLM 或 fallback
  -> 返回 JSON
  -> ResourceGraph / Safety / Grounding guard 审查
  -> backend 持久化资源和学习证据
  -> frontend 展示给学生，并继续读/测/练/判闭环
```

### 9.1 哪些 Agent 受益最大

本轮 adapter 主要服务结构化 JSON Agent 调用，尤其是：

- `TutorAgent`：诊断学生误区，给下一步提示。
- `PracticeAgent`：生成练习提示，避免泄露完整答案。
- `ResourceDesignerAgent`：生成讲义、练习、活动步骤等学习资源草稿。
- `PathPlannerAgent`：根据画像、掌握度和证据规划下一步。
- `MindMapAgent`：生成结构化知识图谱或概念关系。
- `GroundingCriticAgent`：检查输出是否绑定证据。
- `SafetyAgent`：过滤答案泄露和不安全内容。

这些 Agent 的共同点是：输出必须能被系统自动解析和审查。相比普通问答，它们对 JSON、字段、证据和安全约束更敏感。

### 9.2 不受 adapter 直接接管的路径

为保护项目稳定性，以下路径不直接使用 adapter：

- 流式聊天文本：继续走 fallback provider。
- tool calling：继续走支持工具调用的原 provider 或业务逻辑。
- `model_tier=fast`：NPC 寒暄等低延迟路径不走 adapter。
- 本地 vLLM 失败、超时、未启动：自动 fallback。

这意味着 fine-tuned vLLM 是增强项，不是单点依赖。

## 10. 实验资产与目录说明

为了不污染 Web 前后端，本轮实验集中在：

`research/vllm-agent-ft-lab/`

重要文件如下：

| 路径 | 作用 |
|---|---|
| `cases/domain_cases_1000_v1.jsonl` | 1000 个 Pythontopia Agent 评测样本 |
| `scripts/run_baseline.py` | OpenAI-compatible 批量评测 runner |
| `src/pyt_agent_lab/baseline.py` | 每 case 调用、打分、事件和指标记录 |
| `src/pyt_agent_lab/scoring.py` | JSON、证据、安全、期望 marker 评分 |
| `scripts/build_sft_dataset.py` | 从干净响应构造 SFT 数据 |
| `scripts/train_lora.py` | QLoRA 训练入口 |
| `scripts/evaluate_lora_offline.py` | 离线 adapter 生成评测 |
| `scripts/serve_qwen35_lora.py` | vLLM LoRA 服务入口 |
| `scripts/repair_run_responses.py` | JSON/evidence grounding guard |
| `docs/process-log.md` | 实验过程日志 |
| `outputs/training/.../training_plan.json` | 训练配置 |
| `outputs/adapters/...` | LoRA adapter 权重 |
| `runs/.../metrics.csv` | 每条 case 的指标 |
| `runs/.../responses.jsonl` | 原始 prompt/response/score 记录 |
| `runs/.../summary.json` | 总体和 fold 汇总 |
| `runs/.../charts/*.svg` | 图表 |

这些文件让实验能被复查：别人不需要相信口头描述，可以直接看数据集、命令、响应、指标和图表。

## 11. 指标怎么读

| 指标 | 含义 | 为什么重要 |
|---|---|---|
| score mean | 综合分，结合期望 marker、JSON、grounding、安全等 | 衡量整体任务完成度 |
| score std | 分数波动 | 衡量稳定性，越低越稳定 |
| JSON valid rate | 输出是否是合法 JSON object | 决定后端能否自动解析 |
| grounding safe rate | 是否正确处理 evidence/common_knowledge | 防止伪造课件依据 |
| forbidden leak rate | 是否泄露完整答案或禁答内容 | 保护教学和安全边界 |
| clean rate | 审计无重大 flag 的比例 | 衡量可直接进入训练或上线候选的响应比例 |

在 Pythontopia 中，JSON 和 grounding 往往比“看起来回答得不错”更重要。一个自然语言回答即使语气很好，只要不能落库、不能审查、不能绑定证据，对 Agent 系统就是不可靠输出。

## 12. 复现实验命令索引

以下命令索引帮助新读者理解实验链，不要求一次性重跑全部。

### 12.1 服务 vLLM LoRA

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src \
VLLM_HOST=127.0.0.1 VLLM_PORT=18094 GPU_MEMORY_UTILIZATION=0.74 \
MAX_MODEL_LEN=1024 MAX_NUM_SEQS=1 VLLM_API_KEY=local-dev-token \
research/vllm-agent-ft-lab/scripts/serve_qwen35_lora.py
```

### 12.2 查看 vLLM 模型

```bash
curl --noproxy '*' -sS -H 'Authorization: Bearer local-dev-token' \
  http://127.0.0.1:18094/v1/models
```

### 12.3 训练 QLoRA adapter

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src CUDA_VISIBLE_DEVICES=0 \
research/vllm-agent-ft-lab/.venv/bin/python \
research/vllm-agent-ft-lab/scripts/train_lora.py \
  --plan research/vllm-agent-ft-lab/outputs/training/e1-protocol-patch-v1-1000-v1-full/training_plan.json
```

### 12.4 运行 5-fold 评测

```bash
NO_PROXY='*' no_proxy='*' PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/run_baseline.py \
  --base-url http://127.0.0.1:18094/v1 \
  --api-key local-dev-token \
  --model pythontopia-qwen35-e1-lora \
  --cases research/vllm-agent-ft-lab/cases/domain_cases_1000_v1_beginner_noise.jsonl \
  --folds 5 \
  --seed 20260703 \
  --concurrency 1 \
  --run-dir research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-beginner-noise-5fold-20260703T*
```

### 12.5 查看评测产物

每次 run 至少应包含：

```text
events.jsonl
metrics.csv
responses.jsonl
summary.json
charts/score_by_fold.svg
charts/rates_by_fold.svg
```

## 13. 给产品和比赛叙事的一句话

这次微调的产品价值不是“我们也有一个本地大模型”，而是：Pythontopia 把课程证据、学生画像、Agent 合同和安全边界蒸馏进一个可本地服务、可 fallback、可审计的结构化生成 adapter，让百晓工坊的资源生成和导学提示更稳定地接入真实学习闭环。
