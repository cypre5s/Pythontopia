# 小型 LLM 微调现实痛点与 Pythontopia 混合优化分析报告

更新日期：2026-07-16

研究对象：Qwen3.5-4B、QLoRA、DPO/ORPO、多 Agent 结构化教学输出

目标领域：高校 Python 初学者的“错因诊断—分层提示—下一教学动作”决策

交付边界：本报告只做现状审计、论文分析、方案设计与汇报指南；本轮没有修改训练代码、没有启动训练、没有变更产品或线上部署。

## 0. 阅读说明与证据等级

为了避免把构想写成成果，本文采用三种证据等级：

- 【本地事实】：来自 2026-07-16 当前工作区的代码、日志、模型产物或评测结果。
- 【论文事实】：来自同行评审论文、官方会议论文页或真实场景研究。
- 【方案假设】：根据本地事实和论文机制提出，必须经过后续消融、完整评测和人工盲审才能成立。

人工智能领域的重要方法通常首先发表在 ICLR、ICML、NeurIPS、ACL、EMNLP 等顶级会议，而不只是在传统期刊。本文优先采用这些会议的官方论文页；教育落地部分另外采用 Scientific Reports 的真实课堂随机对照试验。

## 1. 执行摘要

### 1.1 最重要的判断

业界小模型微调已经出现的主要问题，Pythontopia 不仅可能遇到，而且已有多项本地直接证据：

1. 偏好集准确率很高，不等于生成决策正确。某轮 DPO 的 heldout preference accuracy 达到 100%，但 v2 semantic golden 上 candidate 只通过 5%，IntentRouter 从 champion 的 100% 退到 0%。
2. 单一共享 Adapter 存在跨 Agent 负迁移。CodePlan 定向候选在目标小切片上将 semantic/promotion 从 90% 提高到 95%，但回到旧 golden50 后，raw contract 从 68% 降到 56%，grounding 从 98% 降到 86%，退化集中在 PathPlanner 与 Safety。
3. JSON/schema 正确不等于教学语义正确。GuideCoach 候选 raw contract 达到 100%，但教学动作语义准确率只有 20%，并塌缩到 TRACE。
4. 约束解码和 fallback 能掩盖裸模型缺陷。部分实验在 evidence-bound decoding 后 contract 达到 100%，但 semantic 只有 75%–80%；最终 100% 还包含确定性 fallback，不能归因于模型。
5. 当前数据主要是规则构造、自蒸馏和失败定向合成，不是真实学生交互；完整 5000 例模型评测和人工盲审也尚未完成。
6. 训练和服务存在尚未隔离验证的量化差异：训练用完整 Qwen3.5-4B 的 bitsandbytes NF4 4-bit QLoRA，服务用独立 AWQ 4-bit 基座加载 LoRA。它是否造成质量差异尚无证据，但必须先做 parity test。

因此，下一步不应继续在同一共享 Adapter 上盲目更换 DPO、ORPO、SimPO 或 GRPO，也不应把更多合成样本直接混入同一训练池。

### 1.2 选择解决的具体领域问题

本文不试图用 4B 模型“解决全部教育”。建议聚焦一个窄而有价值、可验证的问题：

> 给定中文初学者的自然语言、代码或报错、当前 KC、学习画像、历史提交与课程证据，模型应正确诊断主要误区，选择下一教学动作与提示层级，绑定真实证据，同时不泄露可直接提交的完整答案；新增该能力还不能破坏其他 Agent。

模型只输出稳定的教学语义中间表示，而不直接决定产品 WorkflowEvent、拼接最终 DTO 或生成不受控的完整代码。建议输出至少包括：

- misconception_type：主要错因；
- event_intent：READ、REVIEW、TRACE、SCAFFOLD、PRACTICE、ASK、CHECK 之一或经批准的多标签集合；
- hint_level：提示强度与答案暴露预算；
- action_goal：本轮希望学生完成的认知动作；
- evidence_refs：只允许引用本请求提供的课程证据；
- safety_flags：完整答案、隐藏测试、越权内容等风险。

### 1.3 推荐的组合方案

建议将方案暂命名为 R-SAGE-IR：

> Routed Specialist Adapter with Grounded Educational Intermediate Representation
> 路由式专属 Adapter + 稳定性约束偏好学习 + 证据绑定教学语义 IR

它不是一个单独的新 loss，而是四层组合：

1. 路由式专属 LoRA：冻结当前 champion；只对目标 Agent 家族训练独立 specialist，由确定性的 agent_name + schema_version 路由。其他 Agent 继续使用 champion，不把所有新能力写回同一 Adapter。
2. 稳定性导向的参数与目标：近期用“从 champion 继续训练的普通 LoRA + LoRA+ 优化器分组”作为低风险主线；CorDA knowledge-preserved、PiSSA、LoftQ 只做互斥初始化消融，不一次叠加。
3. GPO 式软偏好 + RPO 正则 + on-policy failure mining：用当前候选真实生成的失败构造偏好；使用多位教师给出的可接受动作集合与显式软标签，先过滤高 CHES 风险偏好对，再将“软偏好目标”和“chosen SFT regularizer”作为两个可独立消融的组件，最后再加入 retention replay。当前已有 chosen-SFT 消融只修复 likelihood、没有修复语义，因此数据标签和任务隔离必须同时改变。
4. SAGE-IR + Evidence Binder + Compiler：模型预测教学语义；证据白名单、产品状态机、Python AST、schema、safety 和 fallback 由确定性系统处理。评测永久分离 raw、bound、compiler、guard、final 五层。

该组合至少融合了路由式 PEFT、GPO 式软偏好、RPO 正则、on-policy 数据和证据约束 IR，但每一层都能单独消融，不会变成“方法堆砌”。RPO 本身不表达软标签；本文明确将二者视为不同组件。

### 1.4 结论边界

R-SAGE-IR 当前是有本地依据的研究方案，不是已验证成果。现有离线 shadow route 只在 20 条 CodePlan 小切片中选择 5 条 specialist，semantic 从 90% 提到 95%，在旧 golden50 中因未选择 specialist 而保持 champion 输出；serving_enabled 仍为 false。这是“路由隔离值得继续验证”的小样本信号，不是生产级证明。

## 2. 当前模型与实验现状

### 2.1 当前技术事实

| 项目 | 当前事实 | 结论边界 |
|---|---|---|
| 训练基座 | 完整 Qwen3.5-4B，训练时由 bitsandbytes 以 NF4 4-bit 加载，BF16 compute | 属于 QLoRA，不是从头训练或全参数微调 |
| 服务基座 | QuantTrio Qwen3.5-4B-AWQ，AWQ 4-bit、group size 128 | 与 NF4 训练路径不同，尚未做同案例 parity |
| Adapter | r=8、alpha=16、dropout=0.05，覆盖 attention 与 MLP 投影层 | 参数轻；当前已观察到负迁移，但是否由低秩瓶颈、顺序遗忘或数据/优化共同造成仍待分解 |
| 本地 champion | model_registry 将 DPO len512 标为 champion | 注册表是当前本地脏工作区状态，不代表已发布 |
| 默认服务 | serve 脚本默认仍指向早期 SFT Adapter | 不能宣称默认已服务 DPO champion |
| 产品开关 | Docker Compose 默认 PYT_FINE_TUNED_LLM_ENABLED=false | 无法据此推断外部环境，但默认未启用 |
| 正式大评测 | 已构建并校验 5000 条合成评测包 | 完整 5000 推理评测、盲审未完成 |
| 真实学生数据 | 当前训练和主要评测以规则构造、自蒸馏、失败定向合成为主 | 不能外推真实课堂学习效果 |

关键文件：

- NF4 QLoRA：research/vllm-agent-ft-lab/scripts/train_lora.py 第 37–50 行。
- Adapter 配置：research/vllm-agent-ft-lab/outputs/adapters/e1-protocol-patch-v1-1000-v1-dpo-v2-len512/adapter_config.json。
- AWQ 服务：research/vllm-agent-ft-lab/scripts/serve_qwen35_lora.py 第 17–28 行。
- AWQ 量化配置：research/vllm-agent-ft-lab/models/QuantTrio-Qwen3.5-4B-AWQ/config.json 第 106–109 行。
- 本地模型状态：research/vllm-agent-ft-lab/configs/model_registry.json。
- 默认产品开关：deploy/docker-compose.yml 第 95–99 行。
- 5000 评测边界：research/vllm-agent-ft-lab/docs/5000-eval-progress-2026-07-06.md。

### 2.2 当前最可靠的同集结果

同一 stratified500 上：

| 模型 | raw contract | JSON | schema | grounding | leak | fallback |
|---|---:|---:|---:|---:|---:|---:|
| SFT | 63.2% | 95.8% | 75.4% | 86.0% | 0.8% | 4.6% |
| DPO | 71.8% | 99.4% | 73.2% | 98.8% | 0.2% | 0.8% |
| ORPO | 72.0% | 98.6% | 78.2% | 93.2% | 0.4% | 1.8% |

这些数字说明：

- DPO 是当前整体证据更好的本地候选，但 raw contract 仍只有 71.8%。
- ORPO 的 schema 略高，不足以抵消 grounding、leak 和 fallback 退化。
- final contract 的 100% 包含 repair/fallback，不是裸模型 100%。
- 只有 500 条分层合成 case，仍不能证明真实学生场景泛化。

数据来源：research/vllm-agent-ft-lab/docs/quality-flywheel-log-2026-07-10.md 第 26–40 行。

### 2.3 对本报告最关键的失败证据

| 实验 | 看上去成功的指标 | 实际失败 | 暴露的机制 |
|---|---|---|---|
| v2 semantic DPO | heldout preference accuracy 100% | candidate v2 golden20 为 5%，champion 25%；IntentRouter 100%→0% | 偏好拟合与生成语义脱节；共享 Adapter 负迁移 |
| 三 Agent DPO | preference accuracy 100% | raw contract 66.7%→33.3%，MindMap 被破坏 | 共享 Adapter 连续训练产生负迁移；精确机制待消融 |
| Guide event_intent DPO | raw contract 100% | semantic 20%，输出塌缩到 TRACE | 格式成功掩盖动作语义错误 |
| DPO + chosen-SFT | chosen likelihood 明显上升 | semantic 仍为 20%，混淆不变 | RPO 类正则不是错误标签的替代品 |
| Resource evidence-bound | contract 100% | candidate semantic 80%，champion 75%；final 100% 依赖 fallback | 约束解码保证形状，不保证教学含义 |
| CodePlan ORPO | 目标 20 条上 semantic 90%→95% | 旧 golden50 raw contract 68%→56%，grounding 98%→86% | 目标切片增益换来跨 Agent 遗忘 |
| Reflection champion | bound contract 96% | semantic 0%，leak 4%；unconstrained contract 0% | 新领域合同与真实语义均未掌握 |

数据来源：

- research/vllm-agent-ft-lab/docs/quality-flywheel-log-2026-07-11.md 第 35–48、58–69、110–129 行。
- research/vllm-agent-ft-lab/docs/quality-flywheel-log-2026-07-12.md 第 6–50、113–129、142–198 行。
- research/vllm-agent-ft-lab/runs/champion-dpo-v2-len512-reflection-v2-golden50-json-schema-bound-20260713/summary.json。
- research/vllm-agent-ft-lab/runs/champion-dpo-v2-len512-reflection-v2-golden50-unconstrained-eth-20260713/summary.json。

## 3. 业界现实痛点与本项目对应关系

### 3.1 微调容易模仿风格，不一定补足能力

【论文事实】ICLR 2024 的 [The False Promise of Imitating Proprietary Language Models](https://proceedings.iclr.cc/paper_files/paper/2024/hash/4db16435e3a5a2ef3fc39b8f0d12498d-Abstract-Conference.html) 在 1.5B–13B 模型上发现：模仿强模型输出可以显著改善表面指令跟随和风格，但对训练数据未充分覆盖的任务，几乎不能稳定缩小事实与能力差距。

【本地事实】当前 E1 teacher、自蒸馏 SFT 和大量固定 Agent JSON 能有效改善格式、证据字段与拒答风格，但 semantic action、Python pattern、Reflection 决策仍明显失败。

【结论】4B 微调最适合学习项目协议、有限 ontology 和行为边界；缺失的课程事实必须由 packId=43 RAG 提供，产品状态机和代码正确性必须交给 compiler/verifier。不能把更多 teacher 文本等同于补足模型推理能力。

### 3.2 数据数量不等于数据价值，递归合成会放大模板

【论文事实】ICML 2024 的 [LESS](https://proceedings.mlr.press/v235/xia24c.html) 表明，针对目标能力选择的 5% 数据在多项任务上可以超过全量数据，说明训练预算应投向与目标失败真正相关的样本，而不是盲目增量。

【论文事实】ICLR 2024 的 DEITA 强调复杂度、质量与多样性联合筛选；Nature 2024 的 [AI models collapse when trained on recursively generated data](https://www.nature.com/articles/s41586-024-07566-y) 证明递归模型生成数据会丢失分布尾部，保留真实数据能缓解退化。

【本地事实】当前 1000/5000 case 主要由模板和规则组合，SFT target 来自同一基座加 E1 prompt 的自蒸馏，后续 preference 又大量从旧输出和合成失败构造。当前没有真实学生日志和人工偏好金标。

【结论】下一轮的关键不是“从 240 扩到 2400”本身，而是引入真实 Web 输入、当前候选的 on-policy 失败、多教师软标签和严格来源谱系。合成数据只能补覆盖，不能独占训练分布。

### 3.3 DPO 会发生 likelihood displacement，偏好 100% 也可能解对齐

【论文事实】ICLR 2025 的 [Unintentional Unalignment](https://proceedings.iclr.cc/paper_files/paper/2025/hash/3df38ca67befaed9c03b95ffee07d9f8-Abstract-Conference.html) 发现 DPO 可能同时降低 preferred response 的概率，把概率质量移动到意义相反的输出；论文用 CHES 识别高风险、语义过近的偏好对，过滤后可缓解该现象。

【本地事实】首轮 v2 DPO 的 chosen reward 降到 -1.776，heldout preference accuracy 仍为 100%；生成时 IntentRouter 100%→0%。之后加入 0.8×sigmoid + 0.2×chosen-SFT 后，chosen likelihood 提升，但 semantic 仍停在 20%。

【结论】本项目同时存在两个不同问题：

1. likelihood displacement：RPO/chosen-SFT 正则能处理一部分；
2. 标签不唯一或错误、共享参数干扰：单独增加 SFT 权重无法处理。

因此，不能把 RPO 当作万能修复；必须把软标签、CHES 过滤、on-policy failure mining 和 Adapter 隔离一起使用。

### 3.4 离线偏好与部署策略存在分布错位

【论文事实】ICML 2024 的 [Preference Fine-Tuning Should Leverage Suboptimal, On-Policy Data](https://proceedings.mlr.press/v235/tajwar24a.html) 指出，使用当前策略产生的次优样本、并对错误响应施加负梯度，通常优于只在旧离线数据上做最大似然或偏好学习。

【本地事实】多轮 preference 数据是根据历史 candidate 或规则失败构造；部分新 candidate 的真实生成错误与训练 rejected 类型并不一致，例如训练偏好 margin 提升但 golden 输出完全不变。

【结论】每轮候选必须重新在非保护训练池和真实 shadow traffic 上生成响应，再从当前策略实际错误中采样 rejected。不能长期复用同一组 chosen/rejected。

### 3.5 共享 Adapter 的负迁移与 LoRA 子空间干扰假设

【论文事实】ACL 2025 的 [Unraveling LoRA Interference](https://aclanthology.org/2025.acl-long.1284/) 研究的是多个任务 LoRA 的模型合并，论文将合并退化归因于数据分布和参数子空间相互作用，并用正交子空间降低干扰。它与本项目“在同一个 Adapter 上顺序继续训练”的设置不同，只能提供机制假设，不能直接证明本地根因。NeurIPS 2024 的 [CorDA](https://proceedings.neurips.cc/paper_files/paper/2024/hash/83f95bb0ac5046338ea2afe3390e9f4b-Abstract-Conference.html) 通过面向任务或待保留知识的上下文分解构造 task-aware adapter，knowledge-preserved 模式用于减轻遗忘。

【本地事实】IntentRouter、MindMap、PathPlanner、Safety 已出现目标 Agent 微调带来的直接退化；当前 Adapter 只有 r=8，却同时覆盖 attention 与 MLP，并承担多个异质 Agent 的合同、语义、证据和安全目标。

【结论】本地事实只证明“共享 Adapter 连续微调后发生跨 Agent 负迁移”，尚不能区分低秩子空间冲突、顺序灾难遗忘、数据分布偏移和优化超参的贡献。冻结 champion 并显式路由独立 specialist 是风险隔离方案，即使最终根因不是低秩干扰也仍然成立；CorDA/正交子空间只作为后续机制消融，不是已证实修复。

### 3.6 结构化解码只能保证可解析，不能保证决策正确

【论文事实】RANLP 2025 的 [The Hidden Cost of Structure](https://aclanthology.org/2025.ranlp-1.124/) 在 11 个模型上发现，约束解码会迫使 instruction-tuned 模型离开其高概率自然输出，在生成任务上可能降低表现；结构约束需要与 prompt、few-shot 和训练阶段共同设计。

【本地事实】evidence-bound decoding 可以把 JSON/schema/grounding 推到 100%，但 CodeLab 仍会选错模式，GuideCoach 仍会选错教学动作；final 100% 还可能来自 fallback replay。

【结论】必须永久保留 unconstrained raw 作为模型退化哨兵，同时分开报告：

- raw contract；
- constrained contract；
- semantic validity；
- compile validity；
- guard repair；
- fallback；
- final product success。

### 3.7 量化与 PEFT 不是免费午餐

【论文事实】ICLR 2024 的 [LoftQ](https://proceedings.iclr.cc/paper_files/paper/2024/hash/39ec972afab01e0d8ddc6834a9d12ac1-Abstract-Conference.html) 指出量化加 LoRA 与高精度微调之间存在系统差距，并用量化感知的 LoRA 初始化缩小差异。NeurIPS 2024 的 [PiSSA](https://proceedings.neurips.cc/paper_files/paper/2024/hash/db36f4d603cc9e3a2a5e10b93e6428f2-Abstract-Conference.html) 用主奇异方向初始化，QPiSSA 在论文任务中减少了相对 QLoRA 的初始量化误差。

【本地事实】训练使用 NF4，服务使用 AWQ；当前没有同一 Adapter、同一 prompt、同一 seed 在两条路径上的 paired parity 报告。

【结论】LoftQ/PiSSA 是否有效必须先于更大实验做小样本同集 A/B，并验证保存格式、Qwen3.5 multimodal class 和 vLLM AWQ LoRA 兼容。它们不能被当作已经适用。

### 3.8 微调会损害安全，对教育场景还会造成过度拒答

【论文事实】ICLR 2024 的 [Fine-tuning Aligned Language Models Compromises Safety](https://proceedings.iclr.cc/paper_files/paper/2024/hash/83b7da3ed13f06c13ce82235c8eedf35-Abstract-Conference.html) 发现，少量恶意样本可以严重破坏安全，普通 benign fine-tuning 也可能无意削弱既有安全对齐。

【本地事实】定向 schema 候选的总体 leak 从 0.2% 升到 0.4%；CodePlan 目标增益同时造成 Safety 与 PathPlanner grounding 回退。

【结论】教育安全不能只看 leak=0，还应同时看：

- 高风险完整答案泄露；
- 隐藏测试泄露；
- 对合法提示的过度拒答；
- 提示是否仍有教学帮助；
- grounding 与 safety 的最差 Agent 分片。

### 3.9 AI 助教的现实问题是教学策略，不只是答案正确

【论文事实】ACL 2025 的 [Pedagogical Steering](https://aclanthology.org/2025.findings-acl.1348/) 指出通用 LLM 常过快揭示答案、无法维持多轮教学计划；作者使用显式 transition graph 引导 Productive Failure，并在 17 名高中生的现场研究中验证可行性。

【论文事实】NAACL 2025 的 [AI Tutor Evaluation Taxonomy](https://aclanthology.org/2025.naacl-long.57/) 用 8 个教学维度、192 段对话和 1596 个响应区分“好助教”与“会答题的系统”。BEA 2025 shared task 的四个教学能力三分类任务，最好 macro-F1 仍只有 58.34–71.81，说明连自动评估“是否会教学”都很困难。

【论文事实】Scientific Reports 2025 的 [真实大学课堂 RCT](https://www.nature.com/articles/s41598-025-97652-6) 在 194 名学生中得到积极学习效果，但成功系统使用 GPT-4、教师编写的题目专属 prompt、预先准备的逐步答案和平台强制顺序脚手架；论文也明确指出单独 system prompt 不能稳定控制多步骤教学。

【结论】这组现实证据恰好支持 Pythontopia 的系统方向：小模型不应自由决定所有教学流程。模型负责有限语义决策，transition graph、课程证据、compiler、判题和学习事件共同完成闭环。

## 4. 根因分析：为什么当前实验会“指标很好，产品语义很差”

### 4.1 数据层

- chosen/rejected 多由规则或旧模型构造，容易模板化。
- strict 单标签把本来可有多个合理答案的教学动作强行压成唯一 gold。
- 稀缺动作靠反事实补齐，但“覆盖均衡”不代表“标签合理”。
- 当前候选的真实错误没有稳定回流下一轮训练。
- 保护集虽然做了 ID、lineage、prompt fingerprint 排除，但同一模板家族仍可能让小 heldout 饱和。

### 4.2 目标函数层

- DPO 优化相对偏好，不保证 chosen 的绝对概率上升。
- chosen-SFT 可以保护似然，却无法修复错误或歧义标签。
- 将 contract、grounding、安全、教学动作压成单一 score，会鼓励模型走最便宜的代理路径，例如固定输出 TRACE、补齐字段或变得更长。
- 4B 模型在有限容量下更容易学习高频 schema 模式，而不是低频教学语义。

### 4.3 参数层

- 一个 r=8 Adapter 同时修改多类 Agent 的 attention 与 MLP。
- 新任务梯度可能与既有 Agent 能力方向冲突。
- 对某一 Agent 的高频低秩更新会覆盖其他 Agent 稀疏但关键的行为。
- 从共享 champion 继续训练虽保留起点，却不能阻止更新后的 specialist 在所有 Agent 上被错误使用。

### 4.4 解码与系统层

- JSON schema 只限制合法 token，不理解“READ 与 TRACE 哪个更合适”。
- Evidence enum 只防止编造 ID，不能判断引用页是否真正支持结论。
- fallback 能保证产品返回，但会把模型能力不足隐藏在 final=100% 下。
- NF4 训练与 AWQ 服务可能造成输出分布差异，目前未被隔离。

### 4.5 评测层

- 5、15、20、50 条 golden 太小，置信区间很宽。
- 训练 heldout preference accuracy 已饱和，区分不了生成质量。
- 5000 数据包虽然更大，仍是合成 case，且完整推理尚未完成。
- 目前没有真实学生多轮对话、学习增益、下一次提交改进等最终教育指标。

## 5. 三条路线比较

### 5.1 路线 A：继续使用单共享 Adapter，只替换更先进的 loss

候选做法：DPO → ORPO、SimPO、KTO、RPO 或直接 GRPO。

优点：

- 代码改动相对少；
- 部署仍只有一个 Adapter；
- 容易快速获得新的训练曲线和 heldout 指标。

致命问题：

- 本地已有 DPO、ORPO、DPO+SFT 的负结果；
- loss 不能修复多合理答案被标成唯一答案；
- loss 不能单独隔离共享 Adapter 连续训练所产生的跨 Agent 负迁移；
- GRPO 会放大不完备 verifier 的漏洞；
- 8GB 已发生多次 DPO/ORPO OOM，group rollout 更昂贵。

判断：不推荐作为主线，只保留为对照。

### 5.2 路线 B：路由式专属 Adapter + 软偏好稳定训练 + SAGE-IR

优点：

- 直接命中本地已证实的跨 Agent 遗忘；
- Agent 身份和 schema_version 在请求前已知，不需要训练新的 router；
- champion 可冻结、可回滚；specialist 失败只影响小范围；
- 适合 8GB：每个 Adapter 仍是低秩训练；
- 可把主观教学动作与确定性 schema/evidence/compiler 分开；
- 当前已有 offline shadow route 的小样本正向信号。

代价：

- Adapter 数量和服务观测项增加；
- 训练数据必须重做人工软标签；
- vLLM 多 Adapter 预加载、切换延迟和显存需要实测；
- CorDA/PiSSA/LoftQ 与现有 champion continuation 的兼容要单独解决。

判断：推荐主线。

### 5.3 路线 C：Verifier-guided self-training + RLVR/GRPO

适合的子问题：

- CodePlan pattern 是否正确；
- Python AST 是否通过；
- schema 是否递归合法；
- evidence ID 是否在白名单；
- 编译/单元测试是否通过；
- 是否出现明确答案泄露。

暂不适合的子问题：

- 下一教学动作的多种合理选择；
- 认知负荷是否合适；
- 是否过早给答案；
- 教学语气、鼓励与个体适配；
- 证据是否真正帮助学生理解。

原因：

- 这些目标目前没有可靠的确定性 reward；
- 只奖励 schema 会训练出“合法但错误”的 JSON；
- 只奖励 verifier 分数会诱导 reward hacking；
- 当前单卡 8GB 不适合正式 group rollout。

判断：只在路线 B 建立可靠 verifier 后，对 CodePlan 等闭域子任务做第三阶段研究，不作为近期总方案。

## 6. 推荐方案：R-SAGE-IR 深度设计

### 6.1 总体链路

    中文学生输入、代码/报错、画像、KC、证据白名单
                         |
                         v
          确定性 Agent + schema_version 路由
                /                         \
         非目标 Agent                  目标教学决策
             |                              |
       冻结 champion                 TutorDecision specialist
                                            |
                                 教学语义 IR，不输出产品 DTO
                                            |
                          evidence-bound decoding + semantic verifier
                                            |
                         ContractCompiler / EvidenceBinder / AST / Safety
                                    /                     \
                                  通过                    失败
                                   |                       |
                              产品 DTO             champion/fallback/澄清

路由依据必须是系统已知的 agent_name、subagent 和 schema_version，不让另一个 LLM 猜路由。这样可以把路由错误从模型问题变成可测试的确定性配置问题。

### 6.2 方法一：冻结 champion，使用专属 Adapter 隔离更新

第一阶段只创建一个 specialist：TutorDecisionAgent，覆盖 GuideCoach、Practice 和 Reflection 中共享的“诊断—动作—提示层级”语义，不覆盖 PathPlanner、Safety、MindMap、通用 ResourceDesigner。

建议两种构造方式做消融：

#### B1：低风险延续

- 从当前 DPO champion Adapter 继续训练；
- 输出一个完整、可单独服务的 specialist Adapter；
- 只在目标请求上使用 specialist；
- 非目标请求始终走原 champion。

优点是与现有训练脚本最接近。缺点是 specialist 内仍继承 champion 的旧偏差。

#### B2：知识保持型重建

- 从原始基座重新建立 specialist；
- 使用 CorDA knowledge-preserved 初始化，或普通 LoRA 作为对照；
- 用 champion 的非目标/基础能力 replay 作为蒸馏锚点；
- 再加入目标领域 SFT 与软偏好。

优点是可以显式研究知识保持。缺点是 CorDA 不能简单叠在已经训练好的 champion LoRA 上；若把 champion merge 成新基座，服务侧也必须验证 Adapter 相对权重和 vLLM 兼容。因此 B2 是研究消融，不是第一轮默认方案。

#### 为什么暂不合并 Adapter

当前各 specialist 自身尚未完整过门禁，先做 TIES、DARE 或直接 LoRA merge 会同时失去：

- 故障隔离；
- 明确路由；
- 独立回滚；
- 单一变量归因。

只有多个 specialist 分别通过完整门禁、且服务成本成为真实瓶颈后，才研究合并。

### 6.3 方法二：PEFT 的低成本稳定增强

近期优先级：

1. 普通 LoRA continuation：现有可靠基线。
2. LoRA+：分别设置 A/B 矩阵学习率，论文报告在相近计算成本下改善收敛；它不改变服务结构，适合与 B1 组合。[LoRA+，ICML 2024](https://proceedings.mlr.press/v235/hayou24a.html)
3. CorDA knowledge-preserved：用于 B2，自原始基座重建 specialist。
4. PiSSA 与 LoftQ：与 CorDA 互斥的初始化 A/B，不同时启用。
5. DoRA：容量更强但会增加参数与计算，8GB 下暂缓。

重要约束：

- CorDA、PiSSA、LoftQ 都会改变初始化语义；一次只能选一个，与普通 LoRA 同数据、同 seed、同步数比较。
- 当前 rank=8 时 rsLoRA 的高 rank 稳定优势未必显著，不列为第一优先级。
- 所有新初始化先做 20-case 训练/保存/加载 smoke，再做 100–300 case parity，确认 Qwen3.5 与 vLLM AWQ 兼容。

### 6.4 方法三：on-policy、多标签、软偏好数据

#### 第一步：收集当前策略真实错误

在永久训练池而非 protected eval 上，用当前 champion 与 specialist 各生成 2–4 个候选：

- 温度 0：稳定模式；
- 低温采样：暴露局部混淆；
- 中文噪声：错别字、拼音、术语误用、残缺代码、半截 traceback、多轮指代；
- 安全诱导：要求完整答案、隐藏测试或直接可提交代码；
- evidence 冲突：证据不足、证据与问题无关、多个页候选。

只从模型真实产生的错误中构造 rejected，不凭空编造大量容易被识别的坏模板。

#### 第二步：把唯一 gold 改成可接受集合

每条 case 至少由两名 Python 教师或助教独立标注，分歧由第三人裁决：

- admissible_intents：允许的 1–3 个动作；
- preferred_intent：当前上下文中更优的动作；
- forbidden_intents：会破坏教学流程的动作；
- preference_confidence：偏好比例或置信等级；
- misconception_type；
- max_hint_level；
- evidence_support；
- answer_leak；
- rationale：只给标注者和审计，不直接作为模型输出目标。

若两名教师分别选择 READ 与 SCAFFOLD，而两者都合理，不应把其中一个硬标成 rejected。可以用教师投票比例 q 形成 soft preference，q 接近 0.5 时降低训练压力。

#### 第三步：CHES 风险过滤

对 chosen/rejected 语义非常接近、但被硬分成正负的偏好对计算 CHES 或作者实现中的等价风险分数：

- 高 CHES、高分歧样本不直接进入 hard DPO；
- 改为 soft label、SFT-only 或送回人工复核；
- 保留明确相反的安全/证据错误作为 hard negative；
- 阈值只在 training development pool 上校准，不能查看 protected eval。

#### 第四步：来源平衡

训练批次按来源和风险分层：

- 真实学生或真实 shadow 请求；
- 当前策略 on-policy 失败；
- 教师设计的边界案例；
- 规则/反事实补覆盖；
- champion retention replay。

报告必须分别列出各来源数量、权重、重复率和最差分组表现，不能只给总样本数。

### 6.5 方法四：GPO 式软偏好 + RPO 正则 + 保留回放

建议的组合目标为：

    L_total
      = L_soft_preference
      + λ_sft × L_chosen_NLL
      + λ_ret × KL(champion || specialist on replay anchors)

其中：

- L_soft_preference：显式使用教师偏好比例 q，而不是一律 q=1；该组件参考 NeurIPS 2024 的 [Geometric-Averaged Preference Optimization for Soft Preference Labels](https://proceedings.neurips.cc/paper_files/paper/2024/hash/688c7a82e31653e7c256c6c29fd3b438-Abstract-Conference.html)。
- L_chosen_NLL：与软标签不同的第二个组件，即 RPO 中的 SFT regularizer，用于抑制偏好过优化与 chosen likelihood 下移。[RPO，NeurIPS 2024](https://proceedings.neurips.cc/paper_files/paper/2024/hash/fa69e968b7319fd42524febd41475fb3-Abstract-Conference.html)
- KL retention：在独立的训练用 replay anchors 上，让 specialist 不偏离 frozen champion 的基础教学、安全和中文能力。

注意：

- 当前本地 0.8×DPO + 0.2×SFT 已经证明 chosen likelihood 可以提升，但语义不变；因此新实验必须同时改变软标签/on-policy 数据或 Adapter 隔离，不能只再调 λ_sft。
- λ_sft 与 λ_ret 只能在 development set 的 Pareto 前沿上选择，protected eval 只用于最后一次晋级。
- 第一版先分别验证现有 TRL 的 hard-pair DPO+SFT 与 replay SFT；不能把普通 sample weight 冒充 GPO 软标签。只有人工软标签显示稳定分布后，再使用 GPO 作者实现或经单元测试验证的显式概率目标。

### 6.6 方法五：SAGE-IR、Evidence Binder 与确定性 Compiler

模型输出的 IR 必须比产品 DTO 更小、更稳定：

| 层 | 模型负责 | 确定性系统负责 |
|---|---|---|
| 教学语义 | misconception、event_intent、hint_level、action_goal | WorkflowEvent、任务状态迁移 |
| 证据 | 从给定 opaque refs 中选择 | ref→真实 page/KC 映射、引用存在性 |
| Python 示例 | 选择 example_pattern、observation_goal | 生成安全代码、AST、预期输出 |
| 安全 | 标记风险与 reveal budget | 完整答案/隐藏测试硬拦截 |
| 产品输出 | 小型语义 JSON | 最终 DTO、数据库字段、错误码 |

这样做不是降低模型价值，而是把 4B 模型最擅长的“有限语义选择与自然语言解释”与最不可靠的“状态机、引用 ID、代码语法、强安全保证”分开。

约束解码只用于：

- 保证 JSON 可解析；
- 限制 enum 和 evidence allowlist；
- 约束最小字段。

它不能替代 semantic verifier。任何 constrained 结果都必须同时跑 unconstrained raw 哨兵；如果 bound 指标上升而 raw 持续下降，应判定为模型退化。

### 6.7 训练—服务量化一致性门槛

在新训练前先完成三臂、分阶段的 parity test：

1. 同一 champion Adapter；
2. 同一 100–300 个 case；
3. 同一 prompt、temperature=0、max_tokens、seed；
4. 路径 A：BF16 权重离线推理，作为较高精度小样本上界；
5. 路径 B：bitsandbytes NF4 权重 + BF16 compute，复现训练加载方式；
6. 路径 C：vLLM AWQ + LoRA，复现当前服务方式；
7. 核验三臂的基础模型 revision、tokenizer、chat template 和请求序列化哈希；
8. 先用 A vs B 隔离 NF4 量化影响，再把 B vs C 视为“量化格式 + 推理引擎”的联合差异；若需精确归因，必须增加同引擎同量化格式对照；
9. 对比 exact JSON、action、grounding、token 差异与 latency；只有支持同口径 log-prob 的路径才比较 log-prob。

若差异很小，后续继续使用当前经济路径；若 semantic 或 contract 存在显著 paired 回退，再做：

- QLoRA vs QPiSSA；
- QLoRA vs LoftQ；
- AWQ 校准集与当前教学分布对齐；
- 选择可与训练格式一致的服务量化方式；
- 更高精度小样本上界。

这一步的目标是隔离变量，不是预设 AWQ 一定有问题。

## 7. 数据与标注方案

### 7.1 第一阶段最小可用规模

不建议一开始制造上万条。先建立 420 条人工复核的 on-policy pilot：

- 7 类 event_intent；
- 6 类关键失败轴：wrong decision、weak semantics、unbound evidence、over-help/answer leak、under-help/over-refusal、Chinese noisy input；
- 每个“动作×失败轴”至少 10 条。

420 条不是最终训练规模，而是验证标签协议是否稳定。必须先计算：

- admissible set 平均大小；
- 两位标注者 exact agreement；
- Cohen kappa 或 Krippendorff alpha；
- soft preference 分布；
- 高 CHES 与人工分歧的相关性；
- 各动作/章节/噪声覆盖。

若一致性不足，先修 rubric，不训练。

### 7.2 扩展规模

Pilot 通过后，建议扩至 1500–2500 条高质量目标域数据，并保留 300–500 条非目标 replay anchors。数量只作预算建议，最终由学习曲线决定：

- 每增加一批 300–500 条，固定跑同一 development suite；
- 若两批连续没有 semantic 增益，停止扩数据；
- 不用 protected eval 选择数据或停止点；
- synthetic share、真实/准真实 share 和 on-policy share 分别报告。

### 7.3 永久数据分区

| 分区 | 用途 | 是否可用于训练/调参 |
|---|---|---|
| Train pool | SFT、preference、replay | 可以 |
| Development | loss 权重、CHES 阈值、early stop | 可以调参，不训练 |
| Fresh semantic gold | 最终动作/证据/帮助度评测 | 不可以 |
| Existing golden50/stratified500 | 回归与历史对齐 | 不可以 |
| Full5000 | 最后规模评测 | 不可以 |
| Human blind review | 模型身份隐藏的最终审查 | 不可以 |
| Real field pilot | 学习效果与交互质量 | 不可以回填当前候选 |

测试失败若要进入下一轮训练，必须复制到新的 train-mine 分支，并同时创建从未看过的新测试集；旧测试从此只能作为历史参考，不能再承担最终无偏结论。

## 8. 消融实验设计

### 8.1 总原则

- 一次只改变一个可解释变量；
- 同数据、同 seed、同步数、同 max length、同 decoding；
- 首轮单 seed 做 fail-fast；进入候选的配置至少补 3 个 seed；
- 每个候选先目标域，再 retention，再规模评测；
- 任一关键 Agent、安全或 grounding 回退，立即停止；
- 失败运行、OOM、NaN、partial 全部保留，不混入成功统计。

### 8.2 Phase 0：量化与评测基线

| 编号 | 变量 | 目的 |
|---|---|---|
| Q0 | champion，BF16 权重离线路径 | 较高精度小样本上界 |
| Q1 | champion，bitsandbytes NF4 + BF16 compute | 训练加载方式参考 |
| Q2 | champion，AWQ vLLM 路径 | 当前服务参考 |
| Q3 | Q0/Q1/Q2 paired parity | 先隔离 NF4，再测 AWQ+引擎联合差异 |

只有 Q3 完成，且模型 revision、tokenizer、chat template 与请求哈希一致，才能解释后续 PEFT 初始化收益。

### 8.3 Phase 1：隔离是否比共享更重要

| 编号 | 训练方式 | 服务方式 |
|---|---|---|
| A0 | 当前共享 champion | 全部请求走 champion |
| A1 | 在 champion 上继续训练目标数据 | candidate 全局替换 champion |
| A2 | 与 A1 同一 candidate | 只对目标 Agent 路由 candidate |

关键对比：

- A1 vs A0：重现共享更新的目标增益与跨 Agent 税；
- A2 vs A1：证明收益是否来自路由隔离，而非训练算法；
- A2 vs A0：目标域是否提升、非目标是否精确保持 champion。

### 8.4 Phase 2：PEFT 初始化与优化器

按顺序做 winner-takes-next，不跑全组合：

| 编号 | 唯一变化 |
|---|---|
| P0 | 普通 LoRA |
| P1 | 普通 LoRA + LoRA+ |
| P2 | CorDA knowledge-preserved，独立重建路径 |
| P3 | PiSSA，独立初始化 |
| P4 | LoftQ，独立初始化 |

P2/P3/P4 互斥。先用 pilot 数据和小型 parity；只有明显超过 P0 且能被 vLLM 正确加载的配置才进入正式训练。

### 8.5 Phase 3：数据与目标函数

| 编号 | 数据 | Loss |
|---|---|---|
| L0 | 旧离线 hard pair | DPO |
| L1 | 新 on-policy hard pair | DPO |
| L2 | L1 + CHES 过滤 | DPO |
| L3 | L2 + chosen SFT | hard-pair RPO：DPO + chosen NLL |
| L4 | 多教师 soft label + CHES | GPO 式软偏好 + 独立的 chosen NLL 正则 |
| L5 | L4 + replay anchors | L4 + retention distillation |

预期可证伪点：

- 若 L1 不优于 L0，说明当前策略错误挖掘或标注没有价值；
- 若 L2 只改善 chosen likelihood、不改善 semantic，CHES 不是主瓶颈；
- 若 L4 明显减少 action collapse，支持“教学动作多解”假设；
- 若 L5 降低目标增益，说明 retention 权重过高或 replay 分布错误；
- 任何结果都不能只看 heldout preference accuracy。

### 8.6 Phase 4：解码与系统层

对同一模型同一案例分别跑：

| 编号 | 模式 | 可以证明什么 |
|---|---|---|
| D0 | unconstrained | 裸模型真实能力 |
| D1 | JSON schema | 结构约束收益/代价 |
| D2 | evidence-bound schema | 引用白名单合规 |
| D3 | D2 + semantic compiler/verifier | 可执行语义 |
| D4 | D3 + guard/fallback | 最终产品可用性 |

若 D4=100% 而 D0 下降，不得宣布模型提升；应报告系统可用、模型退化。

### 8.7 Phase 5：扩大评测与真实场景

晋级顺序：

1. 新 fresh semantic gold，至少 300 条、双人标注；
2. 现有 golden50，历史回归；
3. stratified500；
4. 完整 5000；
5. 模型身份隐藏的人工盲审；
6. 产品 shadow route；
7. 小规模真实学生 pilot；
8. 预注册的课堂 A/B 或 RCT。

完整 5000 只能证明在该合成分布上的规模鲁棒性，不能替代真实学习效果。

## 9. 指标体系与晋级门槛

### 9.1 模型层

- JSON valid；
- recursive schema valid；
- raw contract；
- semantic valid；
- admissible-set hit：预测是否落入教师可接受集合；
- preferred-intent accuracy；
- action confusion matrix；
- compile/AST pass；
- evidence precision、recall 与 support correctness；
- forbidden leak；
- over-refusal；
- response length 与 latency。

### 9.2 系统层

- constrained contract；
- guard repair rate；
- fallback rate；
- final success；
- specialist selection rate；
- route error rate；
- Adapter 切换 TTFT；
- p50/p95/p99 端到端延迟；
- 峰值显存与 OOM；
- 每次成功教学决策的平均成本。

### 9.3 能力保持

- 每个非目标 Agent 的 raw contract delta；
- 每个非目标 Agent 的 semantic/grounding/safety delta；
- 中文普通问答和代码解释 retention；
- 最差 Agent、最差章节、最差噪声 slice；
- champion 与 specialist 的 paired win/loss/tie。

### 9.4 教育效果

参考 MRBench 的八维框架并适配 Python：

- 是否正确识别错误；
- 是否精确定位错误；
- 是否给出可执行而非空泛的引导；
- 是否控制认知负荷；
- 是否避免过早给答案；
- 是否根据学生水平调整提示；
- 是否促进下一轮学生思考；
- 是否保持事实与课程证据准确。

真实场景还要看：

- 下一次提交是否改善；
- 提示后的独立完成率；
- 同类题迁移；
- pre-test/post-test 学习增益；
- 时间投入；
- 求助依赖与完整答案索取率；
- 学生和教师的主观可用性。

### 9.5 建议晋级规则

以下是项目建议值，不是论文通用定律：

1. 目标域 semantic/admissible-set 指标的 paired bootstrap 95% 置信区间下界必须大于 0。
2. 对任一非目标关键 Agent 预注册非劣界 δ；普通质量指标暂建议 δ=2 个百分点，高风险 leak/伪造证据取 δ=0。使用单侧非劣检验或差值置信区间证明下界高于 -δ；“未发现显著回退”不等于证明能力保持。
3. 高风险答案泄露必须为 0；总体 leak 不高于 champion。
4. grounding 不低于 champion，伪造 evidence 为 0。
5. raw contract 不应靠更高 repair/fallback 换取；fallback 不高于 champion。
6. 至少 3 个 seed 的中位数通过，不能只选最好 seed。
7. 多重 slice 检验使用 Holm–Bonferroni 或预先指定 primary slices，避免事后挑涨点。
8. 人工盲审必须隐藏模型身份与输出顺序，并报告标注一致性。

## 10. 为什么当前不直接上 GRPO/RLVR

GRPO 省去 critic，不等于省去 rollout；同一 prompt 的多样本生成、reward 计算和策略更新对 8GB 仍很昂贵。更重要的是，本项目当前 reward 尚未可靠：

- schema reward 会奖励合法但错误的 JSON；
- evidence ID reward 会奖励“选了白名单”而非“证据真的支持”；
- safety reward 可能通过一律拒答作弊；
- event_intent 单标签 reward 会惩罚其他合理教学动作；
- LLM judge 可能偏好更长、更像教师的模板。

可以先在 CodePlan 闭域做 verifier-guided rejection sampling：

1. 每个 prompt 采样 N 个 IR；
2. AST、schema、evidence、safety verifier 硬过滤；
3. 对剩余候选做人类小样本盲审；
4. 验证 verifier 分数与人工教学质量正相关；
5. 再做离线 self-training；
6. 只有连续两轮 raw 与盲审都改善，才进入小规模 RLVR。

对开放的教学动作选择，应优先使用软偏好，而不是硬规则 RL。

## 11. 风险登记与避免办法

| 风险 | 触发信号 | 避免办法 |
|---|---|---|
| 软标签仍不一致 | kappa 低、admissible set 过大 | 先改 rubric；增加上下文；第三人裁决 |
| specialist 过拟合目标 | 目标提升、同家族其他场景下降 | replay、early stop、最差分片门禁 |
| 路由配置错误 | specialist 被非目标 Agent 调用 | agent_name + schema_version 白名单；fail closed |
| 多 Adapter 服务抖动 | TTFT/p95 上升、显存不足 | 预加载少量 Adapter；离线 shadow；容量上限 |
| CorDA/PiSSA/LoftQ 不兼容 | 无法保存/加载、输出漂移 | 20-case smoke；互斥 A/B；保留普通 LoRA |
| NF4/AWQ 语义漂移 | paired action 不一致 | parity gate；量化感知初始化；统一部署路径 |
| 合成数据模板化 | 近重复高、真实 slice 下降 | 来源限额、真实/on-policy 提升、DEITA/LESS |
| DPO likelihood displacement | chosen log-prob 下移 | CHES、RPO、soft preference、早停 |
| action collapse | 某动作占比异常、entropy 下降 | 多标签、混淆矩阵、分布约束、人工复核 |
| reward hacking | verifier 涨、人工质量跌 | proxy/gold 双轨；对抗 verifier；停止 RL |
| 过度拒答 | leak 降但帮助度/完成率下降 | helpfulness 与 safety Pareto；合法请求集 |
| guard 掩盖模型退化 | final 高、raw 下降 | raw/bound/compiler/final 永久分层 |
| 评测污染 | fresh set 也迅速饱和 | 时间切分、模板族隔离、永久 protected set |

## 12. 建议执行节奏

### 第 0 周：只做测量

- 冻结代码、Adapter、数据和评测哈希；
- 完成 BF16、NF4 与 AWQ 三臂 parity；
- 把当前所有候选按 raw/bound/compiler/final 重算；
- 明确现有 golden 哪些已经被反复查看。

退出条件：训练/服务差异和评测污染边界可解释。

### 第 1–2 周：数据与标注

- 建立 420 条 on-policy pilot；
- 双人独立标注和第三人裁决；
- 形成 admissible-intent、soft preference、evidence support、reveal budget；
- 检查 CHES 与分歧关系。

退出条件：标注一致性达到预先约定阈值，且每个动作/失败轴有足够覆盖。

### 第 3 周：路由与 PEFT 消融

- A0/A1/A2 验证共享替换与专属路由；
- 普通 LoRA vs LoRA+；
- CorDA/PiSSA/LoftQ 只做小型互斥 smoke。

退出条件：目标增益不再造成非目标回退；服务兼容。

### 第 4 周：偏好目标消融

- DPO、on-policy DPO、CHES、RPO、soft preference、retention；
- 记录 chosen/rejected log-prob、action distribution 和所有 raw 指标；
- 只让胜者扩大到 3 seeds。

退出条件：semantic 提升有 paired 置信区间，且安全/grounding/retention 不退化。

### 第 5 周：规模评测与盲审

- fresh semantic gold；
- golden50；
- stratified500；
- full5000；
- 人工盲审；
- shadow route latency 和 fallback。

退出条件：通过预注册门槛，才讨论产品灰度。

### 后续：真实学习效果

- 先做 30–50 人可用性 pilot；
- 再根据 pilot 方差做正式样本量计算；
- 以学习增益、独立完成率和下一次提交改善为主，而不是聊天满意度；
- 获得知情同意，脱敏处理学生代码、画像和对话。

## 13. 可形成的科研贡献

如果实验支持，可以形成四项清晰而不过度的贡献：

1. 问题发现：在 4B 多 Agent 教育模型中，偏好 heldout、JSON/schema 与真实教学语义可能严重解耦。
2. 方法贡献：若消融成立，R-SAGE-IR 将路由式 specialist、GPO 式软偏好、RPO 正则和证据约束语义 IR 组合，用于降低跨 Agent 负迁移与 action collapse 风险。
3. 评测贡献：提出 raw—bound—compiler—guard—final 五层质量协议，防止 fallback 后 100% 被误写成模型 100%。
4. 数据贡献：建立中文 Python 初学者的多合理动作、证据支持、答案暴露预算与噪声输入标注集。

只有完成 3 seeds、fresh human gold、full5000、盲审和真实 shadow 后，才能把“提出方案”升级为“验证方案”。只有完成真实学生实验，才能讨论学习效果。

## 14. 详细汇报指南

### 14.1 推荐题目

面向高校 Python 初学者的证据约束教学决策：小型 LLM 微调负迁移分析与 R-SAGE-IR 混合优化方案

### 14.2 15 页汇报结构

| 页 | 标题 | 必须讲清的一句话 | 推荐证据 |
|---:|---|---|---|
| 1 | 研究背景 | 小模型微调最危险的不是“不涨分”，而是“格式涨分掩盖语义退化” | 一张问题定义图 |
| 2 | 项目任务 | 模型负责教学语义，系统负责证据、判题和状态机 | Pythontopia 学习闭环 |
| 3 | 当前模型 | NF4 QLoRA 训练、AWQ vLLM 服务、r=8 Adapter | 技术栈与部署边界 |
| 4 | 业界痛点 | 模仿、遗忘、DPO displacement、结构约束、安全均有论文证据 | 论文—机制矩阵 |
| 5 | 真实教育问题 | 通用 LLM 会过早给答案，成功 AI tutor 依赖显式脚手架 | 17 人 field study + 194 人 RCT |
| 6 | 本地失败证据 | preference 100% 与 semantic 5% 可以同时发生 | v2 DPO 指标对照 |
| 7 | 跨 Agent 负迁移 | 目标 90%→95%，却使旧集 contract 68%→56% | CodePlan 配对结果 |
| 8 | 根因模型 | 数据歧义、DPO 目标、共享 Adapter 连续训练、约束掩盖共同作用；低秩干扰只是待验假设 | 五层因果链 |
| 9 | 聚焦问题 | 只解决“错因诊断—下一教学动作—分层提示” | 输入/IR/输出定义 |
| 10 | 三路线比较 | 单 Adapter 换 loss 不够；路由 specialist 是主线；GRPO 后置 | A/B/C 对比表 |
| 11 | R-SAGE-IR | 路由隔离 + GPO 式软偏好 + RPO 正则 + 证据 IR | 总体链路图 |
| 12 | 数据方案 | 当前策略真实失败、多教师可接受集合、CHES 过滤 | 420 pilot 设计 |
| 13 | 消融方案 | 每次只改一项，从量化 parity 到 loss、路由和解码 | Q/A/P/L/D 实验矩阵 |
| 14 | 晋级门槛 | raw、semantic、grounding、安全、retention 同时过门 | 预注册 gate |
| 15 | 贡献与边界 | 当前是方案；完整评测和真人实验后才能称为有效 | 已证实/待验证清单 |

### 14.3 10 分钟讲述节奏

- 0:00–1:00：先给反直觉结论——偏好 100% 不等于教学正确。
- 1:00–2:30：说明项目模型当前到底做什么、不做什么。
- 2:30–4:00：用三组本地数字证明相同问题已经发生。
- 4:00–5:30：用论文解释 likelihood displacement、LoRA interference 和结构约束局限。
- 5:30–7:30：讲 R-SAGE-IR 四层组合及每层对应哪一个根因。
- 7:30–9:00：讲消融、数据、指标和晋级门槛。
- 9:00–10:00：讲科研贡献、风险和未验证边界。

### 14.4 建议使用的三张核心图

1. 指标错觉图：横轴训练轮次，左轴 preference accuracy，右轴 semantic accuracy，展示一条升到 100%、另一条停在 20% 或降到 5%。
2. 跨 Agent 热力图：行是 Agent，列是 champion/candidate，颜色为 raw contract 或 grounding，突出 IntentRouter、MindMap、PathPlanner、Safety 回退。
3. 五层漏斗图：raw → constrained → compiler → guard → final，标出每一层新增通过率，避免把 final 100% 归因于模型。

### 14.5 答辩中容易被问的问题

#### 为什么不用更大的模型？

回答：当前目标是 8GB 本地可复现与低成本服务；更大模型可作为 teacher 或上界，但不能替代 4B 系统是否能稳定执行窄教学协议的研究。若 base ceiling 仍不可突破，应诚实升级模型，而不是继续堆 imitation data。

#### 为什么不直接做 GRPO？

回答：当前教学动作没有可靠单值 reward，且已有 schema 100%、semantic 20% 的证据。此时上 RL 会放大 verifier 偏差；先在 CodePlan 等可 AST/测试验证的闭域做 rejection sampling。

#### 多 Adapter 会不会太复杂？

回答：Agent ID 已知，不需要学习 router；只增加一个目标 specialist 先做 shadow。它换来明确隔离和回滚，比把多个未通过门禁的 Adapter 合并更可控。复杂度用 TTFT、p95、显存和 fallback 实测。

#### 为什么 soft label 比单标签好？

回答：教学动作常有多个合理选择；当前 strict 单标签、类别分布、标签噪声、优化和解码偏置都可能促成 action collapse，现有实验尚未完成因果隔离。soft label 的作用是假设性地表达教师分歧、避免把合理动作当 hard negative；是否有效由 L3/L4 受控消融验证。

#### CorDA、PiSSA、LoftQ 到底选哪个？

回答：不预选冠军。三者都是初始化方法，不能同时叠加。普通 LoRA 是基线；CorDA更贴近知识保持假设，PiSSA/LoftQ更贴近量化误差假设，先做互斥小样本与服务兼容测试。

#### 怎样证明不是 guard 在涨分？

回答：所有结果同时报告 D0 unconstrained raw、D1/D2 constrained、D3 compiler、D4 final；模型晋级以 raw semantic 与 retention 为核心，final 只证明系统可用。

#### 怎样证明真正帮助学生？

回答：离线 semantic 和盲审只能证明行为质量；最后必须用真实学生的下一次提交、迁移题、pre/post 学习增益和求助依赖进行 pilot 或 RCT，不能只看满意度。

### 14.6 汇报中禁止使用的表述

不要说：

- “5000 例评测已完成。”
- “DPO 模型已在线上部署。”
- “模型准确率已经 100%。”
- “R-SAGE-IR 已经解决灾难遗忘。”
- “GRPO 一定比 DPO 先进。”
- “更多合成数据一定更好。”
- “结构化解码保证语义正确。”
- “AI 助教已经提高学生成绩。”

可以说：

- “已构建并校验 5000 条合成评测包，完整推理与盲审尚未完成。”
- “当前本地注册表将 DPO len512 标为 champion，但默认产品开关关闭，服务脚本默认仍为早期 SFT。”
- “系统 final 可达到 100%，其中包含 guard/fallback；裸模型 raw 指标明显更低。”
- “R-SAGE-IR 是由本地失败与论文机制共同驱动的可证伪方案。”
- “路由 shadow 在小切片出现正向信号，尚需 fresh gold、500/5000 和真实 shadow 验证。”

## 15. 最终建议

近期只做一件事：围绕“Python 初学者下一教学动作选择”建立一个路由式 specialist，并用人工多标签 on-policy 数据验证它能否在不破坏 champion 的前提下提高 raw semantic。

优先顺序：

1. 完成 NF4/AWQ parity 和评测冻结；
2. 建立 420 条多教师 on-policy pilot；
3. 对比共享全局替换与显式 specialist route；
4. 普通 LoRA/LoRA+ 先跑，CorDA/PiSSA/LoftQ 互斥 smoke；
5. 做 DPO → CHES → RPO → soft preference → retention 的顺序消融；
6. 永久分离 raw、bound、compiler、guard、final；
7. 通过 fresh semantic gold 后再跑 stratified500/full5000/盲审；
8. 只在 CodePlan 等 verifier 足够可靠的闭域考虑 RLVR/GRPO；
9. 最后用真实学生学习效果决定方案是否真正解决教育问题。

如果这条路线最终失败，也能给出清晰结论：失败来自 4B base ceiling、数据标注不可稳定、量化路径或服务成本中的哪一项，而不是继续用更多算法名字掩盖原因。

## 16. 主要参考文献

### 小模型、数据与偏好优化

- Gudibande et al. [The False Promise of Imitating Proprietary Language Models](https://proceedings.iclr.cc/paper_files/paper/2024/hash/4db16435e3a5a2ef3fc39b8f0d12498d-Abstract-Conference.html), ICLR 2024.
- Xia et al. [LESS: Selecting Influential Data for Targeted Instruction Tuning](https://proceedings.mlr.press/v235/xia24c.html), ICML 2024.
- Tajwar et al. [Preference Fine-Tuning of LLMs Should Leverage Suboptimal, On-Policy Data](https://proceedings.mlr.press/v235/tajwar24a.html), ICML 2024.
- Razin et al. [Unintentional Unalignment: Likelihood Displacement in DPO](https://proceedings.iclr.cc/paper_files/paper/2025/hash/3df38ca67befaed9c03b95ffee07d9f8-Abstract-Conference.html), ICLR 2025.
- Liu et al. [Regularized Preference Optimization](https://proceedings.neurips.cc/paper_files/paper/2024/hash/fa69e968b7319fd42524febd41475fb3-Abstract-Conference.html), NeurIPS 2024.
- Furuta et al. [Geometric-Averaged Preference Optimization for Soft Preference Labels](https://proceedings.neurips.cc/paper_files/paper/2024/hash/688c7a82e31653e7c256c6c29fd3b438-Abstract-Conference.html), NeurIPS 2024.

### PEFT、遗忘与结构化输出

- Yang et al. [CorDA](https://proceedings.neurips.cc/paper_files/paper/2024/hash/83f95bb0ac5046338ea2afe3390e9f4b-Abstract-Conference.html), NeurIPS 2024.
- Zhang and Zhou. [Unraveling LoRA Interference](https://aclanthology.org/2025.acl-long.1284/), ACL 2025.
- Li et al. [LoftQ](https://proceedings.iclr.cc/paper_files/paper/2024/hash/39ec972afab01e0d8ddc6834a9d12ac1-Abstract-Conference.html), ICLR 2024.
- Meng et al. [PiSSA](https://proceedings.neurips.cc/paper_files/paper/2024/hash/db36f4d603cc9e3a2a5e10b93e6428f2-Abstract-Conference.html), NeurIPS 2024.
- Schall and de Melo. [The Hidden Cost of Structure](https://aclanthology.org/2025.ranlp-1.124/), RANLP 2025.
- Qi et al. [Fine-tuning Aligned Language Models Compromises Safety](https://proceedings.iclr.cc/paper_files/paper/2024/hash/83b7da3ed13f06c13ce82235c8eedf35-Abstract-Conference.html), ICLR 2024.

### 教育现实证据

- Puech et al. [Towards the Pedagogical Steering of Large Language Models for Tutoring](https://aclanthology.org/2025.findings-acl.1348/), Findings of ACL 2025.
- Maurya et al. [Unifying AI Tutor Evaluation](https://aclanthology.org/2025.naacl-long.57/), NAACL 2025.
- Kochmar et al. [BEA 2025 Shared Task on Pedagogical Ability Assessment](https://aclanthology.org/2025.bea-1.77/), BEA 2025.
- Kestin et al. [AI tutoring outperforms in-class active learning](https://www.nature.com/articles/s41598-025-97652-6), Scientific Reports 2025.
