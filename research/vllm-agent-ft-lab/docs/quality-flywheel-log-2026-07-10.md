# Qwen contract quality flywheel — 2026-07-10

## 2026-07-11 architecture hardening

- 将历史 `AGENT_SCHEMA` 移入版本化 Schema Registry；`agent-schema-v1` 冻结，既有结果不重算口径。
- 产品侧新增 GuideCoach / Practice 语义 IR、Evidence Binder 与确定性 Contract Compiler；导出的
  `agent-schema-v2` 用于后续产品对齐数据，不覆盖 v1。
- `FineTunedFallbackProvider` 的 guard 拆为 contract / grounding / safety 三段，并记录逐层拒绝、
  `guard_repair_rate`、`fallback_rate`；GuideCoach 与 Practice 的线上调用现在实际传入 schema 和证据白名单。
- 新增可选 agent-family adapter router；默认仍使用单 champion，专用 adapter 必须独立过完整门禁。
- 新增模型状态机和 `configs/model_registry.json`。当前 DPO v2 是 champion；2026-07-10 schema 候选保持
  rejected，禁止以新平均分绕过既有失败证据重新标记。

本日志只记录可复现的同集结果。首要指标为 `contract_pass_rate = json_valid && schema_valid && grounding_safe && !forbidden_leak`；`score` 仅作辅助。guard / final 满分不得描述为裸模型满分。

## 固定协议

- 代码基线 commit：`a65dc4939746d2ed066166005d1af7e52d55c94f`（运行时工作区有本任务未提交修改）。
- schema：`agent-schema-v1`。
- 分层评测集：`cases/domain_cases_5000_v1_stratified500.jsonl`，500 条，SHA-256 `69e33f6dddff052577630672d2aa4cf7903363e87e1e505d6f1ba2fb2522c1e0`。
- prompt：`baseline`；temperature：`0`；seed：`20260708`；folds：`1`；并发：`4`。
- ORPO adapter：`outputs/adapters/e1-protocol-patch-v1-1000-v1-orpo-v1-len384-filtered`。
- 本机版本：TRL `0.29.1`，vLLM `0.17.1`，Transformers `5.13.0.dev0`，PEFT `0.19.1`，Torch `2.10.0+cu128`。
- 输出采用逐案例 JSONL / CSV 增量写入并支持 `--resume`。本轮第一次 ORPO serve 因 WSL 临时目录导致 ZMQ 失败，失败证据保存在 `outputs/abc-eval/orpo-v1-stratified500-20260710/serve_stdout.log`；修正 `TMP/TEMP/TMPDIR=/tmp` 后完成评测。

## 同一分层 500 的模型结果

| 模型 | raw contract | JSON | schema | grounding | leak | 辅助 score | guard contract | final contract | repair | fallback |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| SFT | 63.2% | 95.8% | 75.4% | 86.0% | 0.8% | 0.9832 | 95.4% | 100% | 32.2% | 4.6% |
| DPO | 71.8% | 99.4% | 73.2% | 98.8% | 0.2% | 0.9980 | 99.2% | 100% | 27.4% | 0.8% |
| ORPO | 72.0% | 98.6% | 78.2% | 93.2% | 0.4% | 0.990367 | 98.2% | 100% | 26.2% | 1.8% |

raw / guard / final 分层和逐案例配对证据在 `outputs/quality-flywheel/stratified500-20260710/`。这里的 guard 是离线确定性 contract repair 回放，final 的 100% 包含 fallback，并不等于模型达到 100%。ORPO 先前 57 条 heldout 的 100% 因此不能外推到分层 500。

## 失败发现与定向数据

- 三模型共同主因是 schema：GuideCoachAgent 与 PracticeAgent 的关键字段类型持续错误；DPO 还集中在 `learner_state_update`（50）、`blocked_content`（49）、`evidence_needed`（48）、`safety_flags`（27）。
- ORPO 相比 DPO schema 较好，但 grounding 从 98.8% 退到 93.2%，DebugAgent 和 PathPlannerAgent 是主要退化分片，因此不晋级。
- 失败矩阵覆盖 agent、chapter、beginner-noise、Web 输入、evidence、安全场景、缺失字段和类型错误；SVG 图表与逐失败 JSONL 同目录保存。
- 新偏好集共 300 条：270 train / 30 heldout；10 个 agent 各 30 条；134 条缺字段、166 条类型错误。
- 来源为完整 5000 候选池；golden 50 与分层 500 均按 ID、prompt fingerprint 和 lineage 排除。保护集重叠为 0。
- train SHA-256：`d02d5c5a31b7e39d1c7be13f3bcde67c4acdcf94f9630043d64c54878a81a15c`；heldout SHA-256：`29869d5319ff2b658d51c06fe68373dc9cb03c1129eae63456c68ae702143a9e`。
- 训练前检查通过：最长 379 / 512 token，零 label 0，重复 ID/prompt/pair 均 0，train/heldout prompt 重叠 0，保护集重叠 0。

## 单变量候选轮次

- 方法：DPO LoRA；起点：现有 DPO champion `outputs/adapters/e1-protocol-patch-v1-1000-v1-dpo-v2-len512`。
- 唯一修改变量：`preference_dataset`；beta `0.1`，1 epoch，batch `1`，gradient accumulation `16`，learning rate `5e-5`，trainer/data seed `42`。偏好数据构造 seed 独立固定为 `20260710`，不参与训练控制变量比较。
- 第一次输出：`outputs/adapters/dpo-schema-contract-v1-300-20260710`。在第 7/17 步主动终止：检查发现 plan 虽记录 seed `20260710`，旧 launcher 未把它传给 TRL，实际会落到默认 seed `42`。该轮不得作为候选；失败证据在 `failed_runs/20260710T085136Z/failure.json`。
- launcher 修复后曾准备显式使用 trainer/data seed `20260710`，但在第 1 步前的单变量审计中停止：现有 champion 的旧 launcher 实际使用 TRL 默认 seed `42`，改成 `20260710` 会同时改变数据集与训练随机性。该配置审计失败记录在 `outputs/training/dpo-schema-contract-v1-300-seeded-20260710/failure_manifest.json`。
- 正式单变量轮次显式固定 trainer/data seed `42`（与 champion 实际行为一致）；数据构造 seed 仍为 `20260710`。干净输出为 `outputs/adapters/dpo-schema-contract-v1-300-seed42-20260710`，plan 为 `outputs/training/dpo-schema-contract-v1-300-seed42-20260710/training_plan.json`。
- 自动单变量审计已通过：候选与 champion 的方法、base、LoRA、beta、epoch、batch、梯度累积、学习率、最大长度和实际 seed 均一致，仅 `preference_dataset` 不同；证据为 `outputs/training/dpo-schema-contract-v1-300-seed42-20260710/one_variable_audit.json`。
- 正式训练完成：17/17 step，heldout 30/30；train loss `0.2294`，eval loss `0.04003`，heldout preference accuracy `100%`。该 100% 只证明定向偏好被拟合，不能描述为模型泛化 100%。adapter manifest 最终状态为 `rejected`，训练日志写入 `trainer_log.jsonl`，实验状态写入 `experiment_manifest.json` 与 `experiment_events.jsonl`。
- 晋级顺序：golden 50 → 分层 500 → 完整 5000 + 人工盲审。任一关键分片退化、schema 不减少、安全敏感样本有泄露、repair/fallback 高于 champion 时立即停止晋级。

## 候选回归与晋级结论

| 套件 / 模型 | raw contract | JSON | schema | grounding | leak | guard contract | final contract | repair | fallback |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| golden 50 / champion | 68.0% | 100% | 70.0% | 98.0% | 0 | 100% | 100% | 32.0% | 0 |
| golden 50 / candidate | 80.0% | 100% | 80.0% | 100% | 0 | 100% | 100% | 20.0% | 0 |
| 分层 500 / champion | 71.8% | 99.4% | 73.2% | 98.8% | 0.2% | 99.2% | 100% | 27.4% | 0.8% |
| 分层 500 / candidate | 78.4% | 99.2% | 79.0% | 99.4% | 0.4% | 98.8% | 100% | 20.4% | 1.2% |

- 分层 500 逐案例：candidate 胜 42、champion 胜 7、完全平 451；先按 contract、再按辅助 score 排序。
- schema invalid 从 131 降到 101。`safety_flags` 类型错从 27 降到 2，DebugAgent contract 从 32% 升到 88%，PracticeAgent 从 2% 升到 6%；但 GuideCoachAgent 仍为 0%，`learner_state_update` 类型错仍有 50，Practice 的 `blocked_content` 类型错仍有 47。
- 两条 forbidden leak 均出现在 PathPlannerAgent 的 Web 工坊练习输入；安全敏感样本 leak 仍为 0。Web 输入 contract 却从 74% 降到 72%。
- **不晋级**。正式失败项：总体 raw leak `0.4% > 0.2%`、fallback `1.2% > 0.8%`、Web 输入关键分片 `72% < 74%`。决策证据在 `outputs/training/dpo-schema-contract-v1-300-seed42-20260710/promotion_decision.json`。
- 因 golden 50 + 分层 500 门禁未通过，按既定顺序不运行完整 5000 与人工盲审；这不是遗漏，而是防止用后续平均分掩盖已确认退化。产品继续使用原 DPO champion，并保持 `FineTunedFallbackProvider + guard_context`，不裸接本候选。
- raw/guard/final 逐案例、失败 JSONL、矩阵和 SVG 图表在 `outputs/quality-flywheel/dpo-schema-contract-v1-300-seed42-20260710/`。final 100% 均包含确定性 guard/fallback 回放，不能归因于裸模型。
