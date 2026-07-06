# vLLM 1000+ 新手表达增强多折可靠性评测报告

更新日期：2026-07-03

## 1. 评测目的

本次评测回答一个实际工程问题：Pythontopia 的本地 Qwen3.5 + LoRA adapter 通过 vLLM 服务后，能不能承接项目中真实 Agent 的结构化 JSON 调用，并在 Python 初学者表达不清、句子不连贯、错误信息不完整、焦虑求答案等情况下保持可靠。

这里的“可靠”分两层：

- vLLM 服务可靠性：本地服务是否能连续完成 1000 条调用，不拒连、不崩溃、不丢日志。
- 模型输出正确性：输出是否是可解析 JSON、是否遵守证据边界、是否避免泄露完整答案或隐藏测试。

本次没有修改 Web 前端和后端。所有新增实验代码、样本、run artifact 都在 `research/vllm-agent-ft-lab/` 或文档目录内。

## 2. 项目背景

Pythontopia 是面向高校 Python 初学者的学习系统。项目中的 Graph Agent 需要在以下场景调用 LLM：

- TutorAgent：诊断概念误区，给下一步提示。
- PracticeAgent：拆分练习提示，但不直接给完整答案。
- ResourceDesignerAgent：生成个性化学习资源。
- PathPlannerAgent：根据阅读、微测、练习提交和掌握度规划下一步。
- DebugAgent：根据失败提交和报错信息给最小复现提示。
- GroundingCriticAgent / SafetyAgent：审查证据边界和安全边界。

因此，微调模型不是替代后端、判题器或 Agent 编排器，而是作为 Graph Agent 的“结构化生成层”。vLLM 则负责把本地模型变成 OpenAI-compatible HTTP 服务。即使 vLLM 不可用，Agent 仍应通过 fallback provider 或 dry-run 路线继续运行。

## 3. 被测对象

| 项 | 值 |
|---|---|
| 服务框架 | vLLM `0.17.1` |
| 服务模型 | `pythontopia-qwen35-e1-lora` |
| 基座 | Qwen3.5-4B AWQ |
| Adapter | E1 protocol patch 蒸馏得到的 QLoRA adapter |
| 服务端口 | `127.0.0.1:18094` |
| `MAX_MODEL_LEN` | 1024 |
| `MAX_NUM_SEQS` | 2 |
| `GPU_MEMORY_UTILIZATION` | 0.74 |
| 客户端并发 | 2 |
| 评测 seed | 20260703 |
| 折数 | 5 |

硬件为 NVIDIA GeForce RTX 5060 Laptop GPU，显存约 8 GB。正式运行前做过两次校准：

- `concurrency=1 / MAX_NUM_SEQS=1`：稳定，但 1000 条预计超过 2 小时。
- `concurrency=4 / MAX_NUM_SEQS=4`：启动后很快出现 HTTP connection refused，GPU 剩余显存过低。
- 最终选择 `concurrency=2 / MAX_NUM_SEQS=2`：完整跑完 1000 条，期间 `/health` 持续可用。

## 4. 样本设计

基础样本来自：

`research/vllm-agent-ft-lab/cases/domain_cases_1000_v1.jsonl`

增强样本写入：

`research/vllm-agent-ft-lab/cases/domain_cases_1000_v1_beginner_noise.jsonl`

增强后的 1000 条样本仍保留原始 Agent 合同、证据页、期望 marker 和安全风险标记，只改写用户表达方式。6 类新手表达噪声分布如下：

| 新手表达类型 | 数量 | 设计目的 |
|---|---:|---|
| anxious_answer_request | 167 | 模拟“快给我答案/我来不及了”的焦虑求助，测试安全拒答边界。 |
| fragmented_sentence | 167 | 模拟断裂句、跳跃式表达，测试意图恢复能力。 |
| incomplete_error_paste | 167 | 模拟只贴一半报错或缺上下文的调试请求。 |
| low_confidence_help | 166 | 模拟“我可能全错了/不知道怎么问”的低信心求助。 |
| mixed_terminology | 167 | 模拟自然语言、数学语言和编程术语混用。 |
| vague_reference | 166 | 模拟“那个、这里、上面这个”这类指代不清表达。 |

这些噪声类型参考了 novice programming 研究中常见问题：新手难以解释错误消息、容易产生 Python 概念误解、描述程序行为和意图时常缺上下文。参考资料见报告末尾。

## 5. 运行方法

正式 run：

`research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-20260703T1559CST`

核心命令：

```bash
RUN_DIR=research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-20260703T1559CST
NO_PROXY='*' no_proxy='*' PYTHONPATH=research/vllm-agent-ft-lab/src python3 research/vllm-agent-ft-lab/scripts/run_baseline.py \
  --base-url http://127.0.0.1:18094/v1 \
  --api-key local-dev-token \
  --model pythontopia-qwen35-e1-lora \
  --cases research/vllm-agent-ft-lab/cases/domain_cases_1000_v1_beginner_noise.jsonl \
  --folds 5 \
  --seed 20260703 \
  --concurrency 2 \
  --run-dir "$RUN_DIR"
```

运行期间实时写入：

- `events.jsonl`：每条 case 的 start/finish 事件，共 2000 行。
- `metrics.csv`：每条 case 的得分、JSON、grounding、leak、latency，共 1000 行。
- `progress_snapshots.jsonl`：每 30 秒聚合一次进度，共 134 条快照。
- `responses.jsonl`：完整响应，共 1000 行。

结束后生成：

- `summary.json`
- `extended_summary.json`
- `charts/score_by_fold.svg`
- `charts/rates_by_fold.svg`
- `charts/error_counts_by_agent.svg`
- `charts/rates_by_noise_profile.svg`
- `charts/latency_by_fold.svg`

## 6. 总体结果

| 指标 | 结果 |
|---|---:|
| case count | 1000 |
| score mean | 0.991083 |
| score std | 0.057171 |
| JSON valid rate | 0.990000 |
| grounding safe rate | 0.961000 |
| forbidden leak rate | 0.005000 |
| JSON invalid count | 10 |
| grounding false count | 39 |
| forbidden leak count | 5 |
| latency mean | 9201.83 ms |
| latency p50 | 8164.50 ms |
| latency p95 | 18268.20 ms |
| latency max | 29322.00 ms |

解释：

- vLLM 服务可靠性通过：正式配置完整完成 1000 条请求，5 折各 200 条，未出现运行中断。
- 输出正确性基本可用但不能裸奔上线：JSON 有效率达到 99%，但仍有 10 条不可解析；5 条 forbidden leak 必须被 Safety guard 拦截；39 条 grounding false 说明证据字段仍需硬校验。
- 延迟符合本地 8 GB GPU + 4B AWQ + LoRA 的预期，但 p95 接近 18.3 秒，不能用于高并发课堂直播场景；更适合异步资源生成、导学提示、工坊生成。

## 7. 5-fold 结果

| Fold | Cases | Score mean | JSON valid | Grounding safe | Leak rate | Latency mean | Latency p95 |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 0 | 200 | 0.992583 | 0.990 | 0.960 | 0.005 | 9725.24 ms | 20817.00 ms |
| 1 | 200 | 0.989833 | 0.990 | 0.960 | 0.000 | 9213.20 ms | 17683.55 ms |
| 2 | 200 | 0.989583 | 0.990 | 0.960 | 0.010 | 8878.70 ms | 18724.95 ms |
| 3 | 200 | 0.991833 | 0.985 | 0.975 | 0.005 | 9291.40 ms | 19375.40 ms |
| 4 | 200 | 0.991583 | 0.995 | 0.950 | 0.005 | 8900.62 ms | 15174.15 ms |

5 折之间没有出现某一折彻底失效。波动主要体现在：

- Fold 3 的 JSON valid rate 最低，为 98.5%。
- Fold 4 的 grounding safe rate 最低，为 95.0%。
- Fold 2 的 leak rate 最高，为 1.0%。

因此，当前模型不是“不稳定崩坏”，而是存在可定位的协议边界缺口。

## 8. 按 Agent 的可靠性

| Agent | Cases | Score mean | JSON valid | Grounding safe | Leak rate | 主要问题 |
|---|---:|---:|---:|---:|---:|---|
| DebugAgent | 100 | 0.944500 | 1.000 | 0.690 | 0.000 | 常漏 `page-*` evidence，证据边界最弱。 |
| SafetyAgent | 100 | 0.968667 | 0.920 | 0.960 | 0.050 | JSON invalid 和 forbidden leak 主要集中于此。 |
| MindMapAgent | 100 | 0.999000 | 0.980 | 1.000 | 0.000 | 偶发 JSON 格式问题。 |
| IntentRouterAgent | 100 | 0.998667 | 1.000 | 0.980 | 0.000 | 少量 no-evidence/common_knowledge 边界问题。 |
| PathPlannerAgent | 100 | 1.000000 | 1.000 | 0.980 | 0.000 | 少量 no-evidence/common_knowledge 边界问题。 |
| 其他 5 个 Agent | 500 | 1.000000 | 1.000 | 1.000 | 0.000 | 本轮未发现结构化或安全失败。 |

工程结论：

- DebugAgent 上线前必须强制补证据：有 `evidence_ids` 时响应必须包含全部 evidence id；无证据时必须显式 `common_knowledge` 或 `no_course_evidence`。
- SafetyAgent 不能直接信任模型输出；必须使用确定性 schema validator、forbidden pattern scanner 和 refusal template。
- MindMapAgent 的 JSON 问题可以通过 JSON repair 或重新采样兜底。

## 9. 按新手表达类型的结果

| 噪声类型 | Cases | Score mean | JSON valid | Grounding safe | Leak rate | 结论 |
|---|---:|---:|---:|---:|---:|---|
| mixed_terminology | 167 | 1.000000 | 1.000 | 1.000 | 0.000 | 术语混用并未明显破坏协议。 |
| vague_reference | 166 | 1.000000 | 1.000 | 1.000 | 0.000 | 指代模糊对当前合同影响较小。 |
| fragmented_sentence | 167 | 1.000000 | 1.000 | 0.988 | 0.000 | 片段表达总体稳，仅少量证据边界问题。 |
| anxious_answer_request | 167 | 0.987625 | 0.982 | 0.928 | 0.012 | 焦虑求答案最容易触发安全和证据风险。 |
| low_confidence_help | 166 | 0.985040 | 0.970 | 0.940 | 0.012 | 低信心表达最容易造成 JSON 与安全边界松动。 |
| incomplete_error_paste | 167 | 0.973852 | 0.988 | 0.910 | 0.006 | 缺上下文报错是 DebugAgent grounding 的主风险来源。 |

这个切片比平均分更重要：真正难的不是“表达乱”，而是学生在焦虑、缺上下文、低信心状态下同时要求答案、报错定位和快速通过。项目上线时应该把这些状态作为高风险输入，走更严格的 guard。

## 10. 失败样本摘要

本轮共有 48 条样本命中至少一种失败标记。其中：

- JSON invalid：10 条。
- grounding false：39 条。
- forbidden leak：5 条。
- 部分样本同时命中多种失败。

典型失败形态：

- SafetyAgent 在拒答或允许解释时输出接近 JSON，但存在格式错误，导致 `json.loads` 失败。
- SafetyAgent 在“焦虑求答案”类输入中正确表达了拒答意图，但仍触发 forbidden pattern，需要确定性红线扫描。
- DebugAgent 输出了合理 debug hint，却没有包含要求的 `page-*` evidence id。
- no-evidence 场景下，PathPlannerAgent / IntentRouterAgent 偶尔没有显式标注 `common_knowledge`。

这说明 adapter 已经学会了大部分 Agent 合同，但最终系统不能只靠模型自觉；仍需要“模型生成 + deterministic validator + repair/fallback”的组合。

## 11. 与前序结果对照

注意：下表不是完全同一输入集，因为本轮加入了新手表达噪声；但 Agent 合同、1000-case 业务覆盖和评分器保持同一体系。

| 阶段 | JSON valid | Grounding safe | Leak rate | 说明 |
|---|---:|---:|---:|---|
| Raw AWQ baseline | 0.686 | 0.822 | 0.014 | 原始模型结构化协议弱。 |
| E1 protocol patch | 0.980 | 0.963 | 0.007 | prompt 层显著改善。 |
| QLoRA adapter + beginner noise | 0.990 | 0.961 | 0.005 | 噪声输入下保持接近 E1，并略升 JSON / leak。 |

结论是：QLoRA adapter 没有把协议能力训练坏；在更难的新手表达下，JSON 有效率和泄露率仍优于 E1 的早期结果，grounding 基本持平但仍是上线主风险。

## 12. 项目接入建议

建议接入方式：

1. Graph Agent 默认保持 provider 抽象，不让业务依赖 vLLM 必然存在。
2. 仅对结构化 JSON Agent 调用启用 `pythontopia-qwen35-e1-lora`。
3. 对每次输出做 schema 校验、JSON repair、evidence id 校验和 forbidden pattern 扫描。
4. SafetyAgent 和 DebugAgent 走更严格策略：
   - SafetyAgent：不通过 schema 或命中 forbidden pattern 时，直接用确定性 refusal template。
   - DebugAgent：缺 evidence 时自动触发 grounding repair，不能把未审查回答落库。
5. 高并发课堂场景不使用本地 8 GB GPU 的同步 vLLM 作为唯一依赖；资源生成和工坊提示可异步使用。

一句话：vLLM 适合作为 Pythontopia Graph Agent 的本地结构化生成加速器，但不应该成为项目唯一大脑；Agent 编排、证据审查、安全审查和 fallback 必须保留。

## 13. 验证记录

已验证：

- 1000 条 metrics 完整写入。
- 1000 条 responses 完整写入。
- 5 折各 200 条。
- `events.jsonl` 共 2000 行。
- `progress_snapshots.jsonl` 共 134 条。
- 5 张 SVG 图表非空。
- 实验工具链测试通过：`59 passed in 0.14s`。

验证命令摘要：

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src python3 research/vllm-agent-ft-lab/scripts/analyze_run.py \
  --run-dir research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-20260703T1559CST

PYTHONPATH=research/vllm-agent-ft-lab/src python3 -m pytest research/vllm-agent-ft-lab/tests -q
```

待继续验证：

- 接入 Graph 服务后的端到端 API 链路。
- 真实百晓工坊 UI 点击流。
- SafetyAgent deterministic refusal template 与 DebugAgent grounding repair 的新 guard 实现。
- 更高并发或更长时长的服务压测。

## 14. 参考资料

- ACM CHI 2021: On Designing Programming Error Messages for Novices, https://dl.acm.org/doi/fullHtml/10.1145/3411764.3445696
- TU Delft repository: What does this Python code do?, https://repository.tudelft.nl/file/File_b2b4d597-6d54-4916-9e1c-a561678715a2
- OASIcs Programming 2025: A Comparison of Three Program Query Languages to Detect Python Programming Misconceptions, https://drops.dagstuhl.de/storage/01oasics/oasics-vol134-programming2025/OASIcs.Programming.2025.21/OASIcs.Programming.2025.21.pdf
- Qian & Lehman 2017 literature review, https://doi.org/10.1145/3077618
