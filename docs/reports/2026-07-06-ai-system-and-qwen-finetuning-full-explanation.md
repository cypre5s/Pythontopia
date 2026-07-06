# Pythontopia AI 系统与 Qwen 微调完整说明

更新日期：2026-07-06

这份文档是给零上下文读者看的总说明。目标很直接：即使读者不了解 Pythontopia、不熟悉大模型微调，也能顺着本文理解：

- 大模型在项目中到底负责什么；
- 为什么项目不是“套一个聊天框”；
- Qwen3.5/vLLM/QLoRA 微调是怎么做的；
- 借鉴了哪些论文思想，并如何落到工程实现；
- 从数值上看，优化前后提升了多少；
- 哪些结论已经有证据，哪些不能夸大。

## 1. 一句话讲清项目

Pythontopia 是一个面向高校 Python 初学者的 AI 像素学习小镇。学生不是只和机器人聊天，而是在一个完整学习闭环里前进：

```text
画像访谈
  -> 看课程页 / 视频 / PPT / RAG 问答
  -> 工坊生成个性化资源
  -> 训练场做真实 Python 题
  -> 后端正式判题
  -> DebugAgent 解释错因
  -> 掌握度和画像更新
  -> PathPlannerAgent 规划下一步
```

项目的核心判断是：AI 助教真正有价值的地方，不是回答一句话，而是能把回答后的学习行为继续接到证据、练习、判题、画像和路径重规划上。

## 2. 大模型在项目中的作用

大模型在 Pythontopia 里不是唯一大脑，也不替代后端判题器、数据库、课程包或教师。它承担的是“结构化生成与解释”层。

| 场景 | LLM 做什么 | 非 LLM 系统做什么 | 为什么要这样分工 |
| --- | --- | --- | --- |
| 课件 RAG 问答 | 根据检索到的课程片段解释概念 | RAG 服务检索课程页，后端记录引用证据 | 防止模型凭空编教材内容 |
| 画像访谈 | 从学生表达中提取学习偏好、目标和困难 | 后端维护 8 维画像，学习事件被动更新 | 不让一次问卷决定长期推荐 |
| 百晓工坊资源生成 | 生成讲义、练习、思维导图、故事化资源草稿 | Graph 记录 agent run、证据页、safety/grounding 结果 | 生成内容必须可追溯、可审查 |
| 练习提示 | 给分层提示和下一步思路 | 服务端正式判题，不能用 Pyodide 预览冒充成绩 | AI 辅导不替代真实评估 |
| Bug 矿洞调试 | 根据失败提交解释错因、给最小定位问题 | 后端保存正式提交、verdict、错因事件 | 调试提示回流画像和掌握度 |
| 路径规划 | 根据画像、KC、阅读和提交结果规划下一步 | 后端强制任务 evidence 和掌握度阈值 | 路径不是聊天推荐，而是由证据驱动 |
| Safety/Grounding 审查 | 判断是否泄露答案、是否伪造证据 | 确定性 guard、schema 校验和 fallback 最终兜底 | 不能相信模型裸输出自动安全 |

因此，项目中的大模型更像“会写草稿、会解释、会生成候选方案的业务助手”；真正的学习闭环由 Graph 编排、课程证据、后端事件、判题和 guard 共同约束。

## 3. 为什么需要微调

没有微调时，本地 Qwen3.5-4B-AWQ 已经能说出很多 Python 教学内容，但它有一个对 Agent 系统很致命的问题：输出经常不像一个可被程序消费的业务对象。

Pythontopia 的 Agent 调用需要模型输出类似这样的 JSON：

```json
{
  "diagnosis": "把赋值语句理解成数学等式",
  "next_hint": "先判断等号左边是变量名，右边是表达式求值结果",
  "evidence_ids": ["page-1-006"],
  "safety_flags": []
}
```

如果模型输出 Markdown、漏字段、伪造页码、缺 evidence、或者直接给学生可提交完整答案，后端就很难落库，前端也无法稳定展示，安全审查更无法自动执行。

所以微调目标不是“让模型更会聊天”，而是让模型更稳定地满足三类业务契约：

1. **JSON 契约**：输出必须是可解析 JSON object。
2. **证据契约**：来自课件就绑定 `page-*` evidence；不是课件内容就标明 `common_knowledge`。
3. **安全契约**：不能泄露隐藏测试、完整可提交答案或绕过练习目的。

## 4. 整体优化路线

本项目不是一步到位训练模型，而是按工程可验证路线推进：

```text
Raw Qwen3.5-4B-AWQ baseline
  -> E1 protocol patch 找到更可靠的输出协议
  -> 审计 E1 响应，筛出干净 teacher data
  -> 用 975 条 clean responses 构造 SFT
  -> 4-bit QLoRA 训练 lightweight adapter
  -> vLLM 加载 base + LoRA adapter
  -> 新手表达噪声 1000 case 5-fold 长测
  -> SafetyAgent / DebugAgent 加确定性 guard
  -> Graph provider 只在结构化 JSON 调用中可选启用 adapter，并保留 fallback
```

这个路线背后的思想是：先用 prompt/protocol 发现“什么输出是对的”，再把其中一部分行为蒸馏进 adapter，最后用确定性 guard 修复模型仍不可靠的边界。

## 5. 借鉴的论文与工程落点

下面列的不是“论文堆名词”，而是“论文思想 -> Pythontopia 怎么用”。

| 论文 / 方法 | 原始思想 | Pythontopia 的落点 |
| --- | --- | --- |
| RAG: Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks, Lewis et al., 2020, <https://arxiv.org/abs/2005.11401> | 生成模型结合外部可检索知识，减少只依赖参数记忆导致的事实错误，并提供来源依据。 | 课程问答必须检索 `packId=43` 的课程页，回答绑定 evidence page ids；无课件证据时不能伪造页码。 |
| LoRA: Low-Rank Adaptation of Large Language Models, Hu et al., 2021, <https://arxiv.org/abs/2106.09685> | 冻结基座模型，只训练低秩 adapter，大幅降低可训练参数和显存成本。 | 不重训 Qwen3.5-4B 全量权重，只训练 Pythontopia Agent JSON 行为 adapter，便于回滚和多版本比较。 |
| QLoRA: Efficient Finetuning of Quantized LLMs, Dettmers et al., 2023, <https://arxiv.org/abs/2305.14314> | 在量化基座上反传到 LoRA adapter，降低单卡微调门槛。 | 在 8 GB 显存笔记本上用 4-bit QLoRA 训练 adapter，而不是依赖大显存服务器。 |
| AWQ: Activation-aware Weight Quantization, Lin et al., 2023, <https://arxiv.org/abs/2306.00978> | 用 activation-aware 的低比特权重量化降低部署显存压力。 | 使用 Qwen3.5-4B-AWQ 作为本地 vLLM 推理基座，解决 8 GB 显存下服务不稳定问题。 |
| vLLM / PagedAttention, Kwon et al., 2023, <https://arxiv.org/abs/2309.06180> | 通过 PagedAttention 管理 KV cache，提高 LLM serving 吞吐和显存利用。 | 把本地 Qwen3.5-AWQ 和 LoRA adapter 暴露为 OpenAI-compatible HTTP 服务，Graph provider 可以像调用云模型一样调用本地模型。 |
| ReAct, Yao et al., 2022, <https://arxiv.org/abs/2210.03629> | 让模型在推理和行动之间交替，通过外部工具降低幻觉并提高可解释性。 | 项目借鉴“推理要连接外部行动”的思想，但不启用自由 ReAct 循环；改为有界 Graph Agent、白名单工具、任务 evidence 和 fallback。 |
| Toolformer, Schick et al., 2023, <https://arxiv.org/abs/2302.04761> | 让模型学会何时调用外部工具以及如何使用结果。 | Pythontopia 不把所有工具选择交给裸模型，而是用 IntentRouter/Graph 路由把 RAG、资源生成、练习、路径规划等能力结构化接起来。 |
| Self-Refine, Madaan et al., 2023, <https://arxiv.org/abs/2303.17651> | 生成、反馈、修订的迭代过程可以提高输出质量。 | 资源生成后引入 GroundingCritic/Safety 审查和 repair/fallback；不是相信第一次模型输出。 |
| Constitutional AI, Bai et al., 2022, <https://arxiv.org/abs/2212.08073> | 用原则约束模型行为，减少有害输出，同时保留有帮助性。 | SafetyAgent 和确定性 refusal template 明确“不能直接给完整答案、不能泄露隐藏测试、不能伪造证据”的项目宪法。 |

项目中最重要的取舍是：借鉴 ReAct/Toolformer 的“模型要能连接外部世界”，但不让模型自由循环乱调用工具；借鉴 Self-Refine/Constitutional AI 的“输出要被反馈和原则约束”，但最终落成确定性 schema/evidence/safety guard。

## 6. 数据是怎么构造的

核心数据文件：

```text
research/vllm-agent-ft-lab/cases/domain_cases_1000_v1.jsonl
research/vllm-agent-ft-lab/cases/domain_cases_1000_v1_beginner_noise.jsonl
research/vllm-agent-ft-lab/outputs/sft/e1-protocol-patch-v1-1000-v1/
```

### 6.1 1000 个 Agent case

最初构造 1000 条 Pythontopia 业务 case，覆盖 10 类 Agent，每类 100 条：

| Agent | 作用 |
| --- | --- |
| TutorAgent | 概念讲解、误区诊断、下一步提示 |
| PracticeAgent | 练习提示、微测引导、防止直接给答案 |
| ResourceDesignerAgent | 个性化讲义、练习、活动步骤 |
| IntentRouterAgent | 判断学生想问答、练习、生成资源还是规划路径 |
| GuideCoachAgent | 新手引导和学习节奏解释 |
| DebugAgent | 报错解释、最小定位提示 |
| GroundingCriticAgent | 检查输出是否绑定课程证据 |
| SafetyAgent | 检查完整答案泄露和隐藏测试风险 |
| PathPlannerAgent | 下一步学习路径规划 |
| MindMapAgent | 概念关系和知识结构生成 |

case 不只是换几个题面，而是组合了：

- 课程章节和 KC；
- 学生画像；
- 学习信号；
- 课堂/工坊场景；
- 误区线索；
- evidence page ids；
- expected markers；
- forbidden-answer-risk 标记；
- 每个 Agent 的输出合同。

### 6.2 E1 teacher 数据

Raw baseline 不够稳定，所以先加入 E1 protocol patch，让同一个模型在 prompt 层显式遵守：

- 只输出 JSON；
- 字段匹配 Agent 合同；
- evidence 或 `common_knowledge` 边界明确；
- 不输出完整答案；
- 不包 Markdown fence。

然后从 E1 run 中审计响应，得到：

| 项 | 数值 |
| --- | ---: |
| E1 总响应 | 1000 |
| clean responses | 975 |
| SFT train | 780 |
| SFT heldout | 195 |
| heldout ratio | 0.2 |
| seed | 20260702 |

训练输入使用原始 system/user，不包含 E1 patch 文本；训练目标使用干净的 E1 assistant JSON。这相当于把“prompt 层协议能力”蒸馏进 adapter。

### 6.3 新手表达噪声数据

真实新手不会总是用规范实验题面提问，所以又构造了 1000 条 beginner-noise 样本，只改写用户表达，不改变 Agent 合同、证据页和 expected markers。

| 噪声类型 | 数量 | 模拟什么 |
| --- | ---: | --- |
| anxious_answer_request | 167 | “快给我答案/我来不及了” |
| fragmented_sentence | 167 | 断裂句、跳跃式表达 |
| incomplete_error_paste | 167 | 只贴半截 traceback 或缺上下文 |
| low_confidence_help | 166 | “我是不是全错了/不知道怎么问” |
| mixed_terminology | 167 | 数学、自然语言、编程术语混用 |
| vague_reference | 166 | “那个/这里/上面这个”指代不清 |

这个评测更接近真实工坊输入，因为它测试模型能否在混乱表达下仍遵守 JSON、证据和安全契约。

## 7. QLoRA 训练怎么做

训练目录：

```text
research/vllm-agent-ft-lab/outputs/training/e1-protocol-patch-v1-1000-v1-full/
research/vllm-agent-ft-lab/outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch/
```

核心参数：

| 项 | 值 |
| --- | --- |
| base model | `/home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B` |
| serving baseline | `QuantTrio-Qwen3.5-4B-AWQ` |
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

为什么这样配：

- rank 8 / alpha 16：adapter 足够轻，便于多次实验、回滚和 vLLM 加载。
- attention + MLP target modules：不仅学字段格式，也学 Agent 输出风格、证据字段和拒答边界。
- sequence length 768：覆盖当前 case，同时控制 8 GB 显存压力。
- batch size 1 + accumulation 16：用速度换稳定。
- 2 epoch：先验证 heldout 泛化趋势，避免在 780 条 train 上过拟合。

## 8. 数值提升总表

### 8.1 1000-case Raw -> E1 protocol patch

同一 1000 case 上，E1 prompt/protocol 先证明“输出合同”能显著改善：

| 指标 | Raw AWQ baseline | E1 protocol patch | 变化 |
| --- | ---: | ---: | ---: |
| score mean | 0.962467 | 0.997050 | +0.034583 |
| score std | 0.092543 | 0.024754 | -0.067789 |
| JSON valid rate | 0.686000 | 0.980000 | +0.294000 |
| grounding safe rate | 0.822000 | 0.963000 | +0.141000 |
| forbidden leak rate | 0.014000 | 0.007000 | -0.007000 |

换成计数理解：

| 问题 | Raw 约等于 | E1 约等于 | 变化 |
| --- | ---: | ---: | ---: |
| JSON invalid | 314 / 1000 | 20 / 1000 | 少 294 条 |
| grounding false | 178 / 1000 | 37 / 1000 | 少 141 条 |
| forbidden leak | 14 / 1000 | 7 / 1000 | 少 7 条 |

结论：原始模型不是不会 Python，而是不会稳定遵守 Agent 协议；E1 证明协议约束非常有效。

### 8.2 QLoRA 训练层指标

| Epoch | Eval loss | Eval token accuracy |
| ---: | ---: | ---: |
| 1 | 0.290419 | 0.907434 |
| 2 | 0.241026 | 0.917043 |

解释：

- heldout loss 下降，说明 adapter 学到了 teacher response 分布。
- token accuracy 提升，说明 JSON 字段、证据 token、拒答格式等局部预测更稳。
- 训练指标只能证明“学到了”，不能单独证明上线可靠，所以还需要生成评测。

### 8.3 Raw adapter vs heldout baseline

在 195 条 heldout source case 上，raw adapter 相对 raw baseline 的变化：

| 指标 | Delta |
| --- | ---: |
| score mean | +0.020940 |
| JSON valid rate | +0.194872 |
| grounding safe rate | +0.153846 |
| forbidden leak rate | -0.015385 |

Raw adapter 绝对值：

| 指标 | Raw adapter heldout |
| --- | ---: |
| case count | 195 |
| score mean | 0.989060 |
| JSON valid rate | 0.943590 |
| grounding safe rate | 0.953846 |
| forbidden leak rate | 0.000000 |

结论：adapter 已经优于 raw baseline，但还不能说裸 adapter 完全替代 E1 prompt 或 guard。

### 8.4 1000 条新手表达增强 vLLM LoRA 评测

正式 run：

```text
research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-20260703T1559CST
```

总体结果：

| 指标 | 结果 |
| --- | ---: |
| case count | 1000 |
| folds | 5 x 200 |
| score mean | 0.991083 |
| score std | 0.057171 |
| JSON valid rate | 0.990000 |
| grounding safe rate | 0.961000 |
| forbidden leak rate | 0.005000 |
| latency mean | 9201.83 ms |
| latency p95 | 18268.20 ms |

与前序阶段对照：

| 阶段 | JSON valid | Grounding safe | Leak rate |
| --- | ---: | ---: | ---: |
| Raw AWQ baseline | 0.686 | 0.822 | 0.014 |
| E1 protocol patch | 0.980 | 0.963 | 0.007 |
| QLoRA adapter + beginner noise | 0.990 | 0.961 | 0.005 |

注意：这里的 beginner-noise 与原始 baseline 不是完全相同输入集，因此要诚实表述为“同一评分体系下的阶段对照”，不能说是严格同分布 A/B。

### 8.5 Guarded v1 复测

raw vLLM LoRA 在 1000 条 beginner-noise 中仍有：

| 问题 | Raw vLLM |
| --- | ---: |
| JSON invalid | 10 |
| grounding false | 39 |
| forbidden leak | 5 |
| 至少一个失败标记样本 | 48 |

加入确定性 Safety/Debug guard 后：

| 指标 | Raw vLLM | Guarded v1 | Delta |
| --- | ---: | ---: | ---: |
| score mean | 0.991083 | 1.000000 | +0.008917 |
| JSON valid rate | 0.990000 | 1.000000 | +0.010000 |
| grounding safe rate | 0.961000 | 1.000000 | +0.039000 |
| forbidden leak rate | 0.005000 | 0.000000 | -0.005000 |

换成计数：

| 问题 | Raw vLLM | Guarded v1 |
| --- | ---: | ---: |
| JSON invalid | 10 | 0 |
| grounding false | 39 | 0 |
| forbidden leak | 5 | 0 |
| audit clean | 952 / 1000 | 1000 / 1000 |

结论不是“模型完美”，而是“模型 + validator + repair/fallback”组成的工程系统满足当前评测边界。

## 9. 哪些优化最有效

| 优化 | 解决的问题 | 证据 |
| --- | --- | --- |
| E1 protocol patch | raw model 容易输出非 JSON、漏 evidence、格式漂移 | JSON `0.686 -> 0.980`，grounding `0.822 -> 0.963` |
| QLoRA adapter | 把 E1 teacher 行为蒸馏进轻量本地 adapter | heldout score `+0.020940`，JSON `+0.194872` |
| vLLM LoRA serving | 让本地 adapter 通过 OpenAI-compatible API 接入 Graph provider | `/v1/models` 暴露 base 和 LoRA model ids，1000 条 run 完整完成 |
| beginner-noise eval | 检查真实新手混乱表达下的鲁棒性 | 1000 条、5-fold、6 类噪声，JSON 仍达 `0.990` |
| Safety/Debug guard | 修复模型裸输出的安全和证据边界 | JSON invalid `10 -> 0`，grounding false `39 -> 0`，leak `5 -> 0` |
| fallback provider | 本地 vLLM 失败时不拖垮项目 | `FineTunedFallbackProvider` 只接管 JSON mode，流式/工具/fast tier 保持 fallback |

## 10. 项目三大 AI 创新点

### 10.1 证据闭环型多智能体导学

普通 AI 助教常见问题是：回答看似正确，但系统不知道学生后来有没有看、有没有练、有没有错、有没有掌握。

Pythontopia 的做法是把 Agent 输出放进学习闭环：

- 课件 RAG 回答绑定 page evidence；
- 工坊生成资源记录 run id、agent、artifact、safety 和 grounding；
- 正式练习由后端判题并写入 submission；
- QuestService 强校验 evidence；
- PassiveProfileService 监听学习事件更新 8 维画像；
- PathPlannerAgent 根据证据决定通过、补救、换资源或重规划。

创新点不在于“有很多 Agent”，而在于 Agent 的产出能继续驱动学习证据。

### 10.2 本地 Qwen3.5/vLLM/QLoRA 结构化 Agent 微调

本项目没有把微调做成孤立 demo，而是把目标限定在项目最需要的地方：结构化 Agent JSON。

最核心数字：

- Raw AWQ baseline JSON valid：`0.686`
- E1 protocol patch JSON valid：`0.980`
- QLoRA adapter + beginner noise JSON valid：`0.990`
- Guarded v1 JSON/grounding/leak：`1.000 / 1.000 / 0.000`

这说明优化方向不是“模型更会闲聊”，而是“模型更像项目里的可审计组件”。

### 10.3 多模态学习资源编排

Pythontopia 不只输出文字答案，还把资源组织成多种学习媒介：

- 文字讲义；
- 代码练习；
- Mermaid/思维导图；
- 视频速览；
- 图像提示词；
- 小镇任务入口；
- 前端操作截图和学习引导。

多模态不是为了炫技，而是服务学生画像中的学习偏好和当前掌握度。所有资源都要绑定 KC、课程证据和下一步练习，不能脱离教学目标。

## 11. 面试时怎么讲

### 11.1 60 秒版本

Pythontopia 的 AI 创新主要有三点。第一，它不是聊天机器人，而是证据闭环型多智能体导学：课程 RAG、学生画像、资源生成、练习判题、错因调试和路径重排会互相反馈。第二，我做了本地 Qwen3.5/vLLM/QLoRA 微调，目标不是闲聊，而是让模型稳定输出可解析、可审查、可落库的 Agent JSON；1000 case baseline JSON 有效率从 `0.686` 提到 E1 的 `0.980`，QLoRA 在新手表达噪声下达到 `0.990`，guard 后 JSON/grounding/leak 达到 `1.000/1.000/0.000`。第三，我做的是多模态学习资源编排，按学生画像组织文本、代码、思维导图、视频速览、图像提示词和小镇任务，所有资源都要回到 KC、证据页和练习评估。

### 11.2 老师追问：为什么这算创新

答法：

“我没有把大模型当成一个万能聊天框，而是把它拆成可审计的工程组件。RAG 解决课程证据，Agent 解决职责拆分，QLoRA 解决本地结构化输出稳定性，guard/fallback 解决上线边界。每个环节都有指标，不是只靠主观感觉说效果好。”

### 11.3 老师追问：微调是不是只是调 prompt

答法：

“不是。E1 protocol patch 是 prompt 层 teacher，用来先生成更干净的监督数据；之后筛出 975 条 clean responses，按 780/195 做 SFT，用 4-bit QLoRA 训练 adapter。adapter 在 heldout 上相对 raw baseline 的 JSON valid rate 提升 `+0.194872`，grounding 提升 `+0.153846`。但我也不夸大：raw adapter 仍要配合 guard，正式结论是 adapter + validator + repair/fallback。”

### 11.4 老师追问：为什么不直接用大模型 API

答法：

“云模型更强，但比赛演示和研究复现需要本地可控。我的微调任务很窄，是让 Agent JSON 稳定，不是训练通用大模型。本地 4B + AWQ + QLoRA 能在 8 GB 显存上完成 baseline、训练、vLLM serving、1000 case 长测，能证明路线和工程闭环。生产环境仍然保留 provider fallback，不把系统可靠性押在本地模型上。”

### 11.5 老师追问：guard 后 1.000 是不是刷分

答法：

“不能把它说成模型裸能力。guard 后的 `1.000/1.000/0.000` 证明的是工程系统满足当前 schema/evidence/safety 契约。模型裸输出还有 10 个 JSON invalid、39 个 grounding false、5 个 forbidden leak；我把这些暴露出来，再用确定性规则修复和兜底。这正是上线系统应该做的。”

## 12. GitHub 读者怎么复查

推荐阅读顺序：

1. `README.md`：看项目定位和三大 AI 创新点。
2. `docs/reports/2026-07-06-ai-system-and-qwen-finetuning-full-explanation.md`：读本文，获得完整故事。
3. `research/vllm-agent-ft-lab/MODEL_ARTIFACTS.md`：看 GitHub 包含/排除哪些模型资产。
4. `research/vllm-agent-ft-lab/README.md`：看复现实验命令。
5. `docs/reports/2026-07-03-qwen-vllm-finetuning-method-report.md`：看训练方法细节。
6. `docs/reports/2026-07-03-vllm-1000-beginner-noise-multifold-eval.md`：看 1000 条新手表达增强评测。
7. `docs/reports/2026-07-03-vllm-safety-debug-guard-fix.md`：看 guard/fallback 修复和复测。
8. `docs/presentations/2026-07-06-ai-innovation-defense.md`：看保研面试版讲法。

GitHub 不包含：

- Qwen/AWQ 基座权重；
- LoRA adapter 权重；
- `.venv`；
- 长跑 `runs/` 原始缓存；
- 本地密钥或 `.env`。

GitHub 包含：

- 代码；
- case；
- SFT split 摘要；
- 训练计划；
- 测试；
- 小型结果摘要；
- 方法报告；
- 指标表和说明文档。

这样做的原因是：GitHub 适合放可复查的研究材料和源码，不适合直接塞多 GB 权重；权重应放模型仓库或按 artifact policy 本地重建。

## 13. 不能夸大的边界

- 不说“模型完全替代教师”。
- 不说“模型完全替代服务端判题”。
- 不说“裸 adapter 可以直接上线”。
- 不说“guard 后 1.000 等于模型本身完美”。
- 不说“GitHub 包含全部模型权重”。
- 不说“多模态长视频自动生成已生产化”。
- 不说“本地 8 GB GPU 能支撑高并发课堂直播”。

更准确的说法是：

> Pythontopia 已经证明了一个本地可复现、可评测、可 fallback 的结构化 Agent 微调路线。微调提升了本地模型遵守 JSON、证据和安全契约的能力；最终上线可靠性来自模型、Graph 编排、确定性 guard、课程证据和后端学习事件的组合。

## 14. 证据索引

| 结论 | 证据文件 |
| --- | --- |
| 1000 case baseline 与 E1 提升 | `research/vllm-agent-ft-lab/docs/e1-protocol-patch-v1-1000-v1-2026-07-02.md` |
| QLoRA 训练参数与 heldout 指标 | `docs/reports/2026-07-03-qwen-vllm-finetuning-method-report.md` |
| vLLM LoRA 1000 条新手表达增强评测 | `docs/reports/2026-07-03-vllm-1000-beginner-noise-multifold-eval.md` |
| Safety/Debug guard 复测 | `docs/reports/2026-07-03-vllm-safety-debug-guard-fix.md` |
| 微调实验命令 | `research/vllm-agent-ft-lab/README.md` |
| GitHub artifact policy | `research/vllm-agent-ft-lab/MODEL_ARTIFACTS.md` |
| 保研面试讲法 | `docs/presentations/2026-07-06-ai-innovation-defense.md` |
