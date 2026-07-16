# R-SAGE-IR 自动标注与训练流水线设计

日期：2026-07-16

## 目标

把根目录 `todo.md` 中可在学生放假期间自动执行的研究主线实现成一组可审计、可恢复、可通过脚本启动的流水线：课程证据驱动的案例生成、强教师多次投票、规则验证、缩减后的训练集、TutorDecision specialist QLoRA、GDPO 软偏好与 RPO chosen-NLL 正则、分级评测、实时日志和图表。

本设计不把自动教师投票表述为人工金标，也不把最终 guard/fallback 成功率表述为裸模型能力。真实学生学习效果仍需要返校后的 field pilot 才能验证。

## 现状约束

- 工作目录保持在 `/home/cypress/Pythontopia`，修改范围限定为 `research/vllm-agent-ft-lab/` 与本设计/计划文档。
- 当前工作区已有未提交的训练、评测、语义 IR 与服务路由工作；这些文件是权威现状，不能通过新 worktree、stash 或 reset 丢失。
- GPU 为 8GB RTX 5060 Laptop，主线继续采用 NF4 QLoRA、小 batch、梯度累积和短序列。
- 强教师使用 `deploy/.env` 已配置的 OpenAI-compatible `deepseek-v4-pro`；凭证不写入日志、manifest 或训练数据。
- 课程唯一证据源为 `data/course-pack-43/`。Full5000、golden50、stratified500 均是 protected evaluation，不得进入训练或教师提示。

## 方案取舍

### 方案 A：仅用 Shell 串接现有脚本

优点是实现最快。缺点是数据谱系、恢复、单次 full5000 预算、教师调用重试和实时状态难以可靠测试，不采用。

### 方案 B：薄 CLI + 可测试 Python 核心 + 总编排器（采用）

每个阶段由独立 CLI 启动，业务规则放在 `pyt_agent_lab` 包内；总编排器只负责阶段状态、命令执行和恢复。它能复用现有 TRL、实验日志、评测与 SVG 报告代码，同时让每个脚本都有独立 demo。

### 方案 C：引入 Snakemake、MLflow 或 Airflow

依赖和迁移成本高于当前单机研究收益，也会重复已有 run manifest、JSONL 和图表能力，不采用。

## 流水线架构

```text
pack43 KC/page evidence
        |
        v
build_rsage_cases.py ----------> 420 non-protected candidate cases
        |
        v
label_rsage_teacher_votes.py --> 3 votes/case + rule/evidence verifier
        |
        v
curate_rsage_dataset.py -------> 546 SFT train + 208 preference train
        |                           (70% budget, separate dev/heldout)
        +----------------------- retention anchors
        |
        v
build_rsage_training_plans.py
        |
        +--> launch_rsage_sft.py -------> TutorDecision specialist SFT
        |
        +--> launch_rsage_gdpo_rpo.py --> GDPO + chosen-NLL RPO
        |
        v
run_rsage_eval_gate.py
        |
        +--> golden50
        +--> stratified500 (90% smaller than 5000)
        +--> three seeds for promoted winner
        +--> one final full5000 stage, guarded by a durable receipt
```

`run_rsage_pipeline.py` orchestrates the stages, writes durable state after every transition, and resumes from existing valid artifacts rather than overwriting them.

## 数据设计

### 自动案例

The 420-case pilot is generated as a deterministic Cartesian coverage target:

- 7 `event_intent` values: READ, REVIEW, TRACE, SCAFFOLD, PRACTICE, ASK, CHECK;
- 6 failure axes: wrong decision, weak semantics, unbound evidence, answer leak, over-refusal, Chinese noisy input;
- 10 cases per intent/failure combination.

Cases use only pack43 KC/page identifiers and bounded page excerpts. Every row records source hashes, chapter, KC, evidence refs, generation seed and protected-set exclusion fingerprints.

### 教师投票

Each case receives three independent calls to the configured strong teacher with varied reviewer roles. A vote is accepted only when:

- output is valid JSON with the required IR fields;
- intent and hint level are within the approved ontology;
- every evidence ref belongs to the case allowlist;
- the output contains no hidden tests or directly submit-ready answer;
- preferred intent belongs to the admissible set and not the forbidden set.

Invalid calls are retried with bounded exponential backoff. The raw response, verifier result, latency and model name are stored incrementally, but credentials are never stored. Cases without at least two valid votes or with unresolved ties remain excluded and are reported rather than silently coerced.

### 70% training budget

- Target SFT rows: 294 verified target cases (70% of 420).
- Retention anchors: 252 stratified rows from the existing non-target SFT train pool.
- Final SFT train count: 546, matching a 30% reduction from the current 780-row SFT train set.
- Preference train count: 208, approximately a 30% reduction from the current 297-row DPO train set.
- Remaining verified target cases form development/heldout data and are never trained.

Selection is deterministic and stratified by intent, failure axis, chapter and confidence. Near duplicates and protected fingerprints are rejected before selection.

## 训练目标

The main preference trainer combines two distinct paper mechanisms.

For each pair, teacher vote probability `q` is normalized to `[0.5, 1.0]`. GDPO applies the paper's geometric scaling to the usual DPO log-ratio margin:

```text
L_gdpo = -log sigmoid(beta * (2q - 1) * dpo_log_ratio)
```

RPO remains a separate chosen-response imitation term:

```text
L_total = L_gdpo + lambda_sft * mean_token_nll(chosen)
```

The first implementation does not relabel ordinary sample weighting as GPO. Unit tests must prove that `q=1` reduces to ordinary DPO, `q=0.5` produces zero preference-gradient scaling, and the chosen-NLL term changes the total loss independently.

Retention is supplied first through mixed SFT anchors and reported separately. A future KL replay experiment may be added only after the core mainline passes; it is not silently claimed by the initial trainer.

## 实时实验记录

Every run directory contains at minimum:

- `experiment_manifest.json`: immutable inputs, hashes, model, seed and stage status;
- `events.jsonl`: append-only lifecycle and failure events;
- `metrics.csv` or `trainer_log.jsonl`: incremental metrics;
- `progress_snapshots.jsonl`: completed/total, throughput and ETA;
- `gpu_metrics.jsonl`: timestamped utilization, memory and temperature;
- `charts/*.svg`: atomically refreshed loss, reward, throughput and GPU charts;
- `EXPERIMENT_LOG.md`: machine-maintained chronological summary;
- `failure.json`: exception, failed stage and resumable boundary when applicable.

The watcher is read-only with respect to checkpoints. It may regenerate reports while training is running, but cannot mutate model state.

## 评测预算与晋级

1. Script demos and one-step GPU smokes.
2. One-seed mainline.
3. Golden50 target and retention regression.
4. Stratified500, which is 10% of full5000 and therefore implements the 90% intermediate reduction.
5. Only a promoted configuration is repeated for seeds 42, 43 and 44.
6. Only the final winner may claim the durable full5000 receipt and run the final stage once.

The receipt contains dataset hash, adapter hash, command hash and completion status. A second non-identical full5000 attempt fails closed unless an explicit override is added in a future user-authorized experiment.

Metrics remain layered: raw, schema-bound, compiler/verifier, guard and final. Promotion fails on answer leakage, fabricated evidence, non-target regression beyond the configured non-inferiority margin, or higher fallback rate.

## Demo 与验收

Every newly added CLI supports a small demo fixture or bounded mode. Data/label scripts use temporary output directories; the teacher script first passes a deterministic fixture demo and then a one-case live endpoint smoke. Training launchers must perform a real one-step GPU run, save an adapter, reload it and emit charts. The pipeline demo executes all stages with tiny fixture counts and must not consume the one-time full5000 receipt.

After all demos pass, the full mainline starts immediately. During the live run, user-facing progress is reported once per hour with stage, completed steps/cases, latest loss and preference metrics, GPU memory, ETA and artifact paths; completion or failure is reported immediately.
