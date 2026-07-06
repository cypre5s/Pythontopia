# Paper Module Map

## M0: Serving Substrate

- Source: Qwen3.5-4B model card says the model artifacts are compatible with Transformers, vLLM, SGLang, and KTransformers: https://huggingface.co/Qwen/Qwen3.5-4B
- Source: QuantTrio Qwen3.5-4B-AWQ provides vLLM usage guidance: https://huggingface.co/QuantTrio/Qwen3.5-4B-AWQ
- Source: vLLM supported models docs recommend runtime checks and describe model support: https://docs.vllm.ai/en/latest/models/supported_models.html
- Local decision: use AWQ 4-bit for baseline because FP/BF16 4B needs CPU offload and fails on this machine's vLLM path.

## M1: Per-Agent LoRA Serving

- Source: vLLM LoRA adapter docs: https://docs.vllm.ai/en/latest/features/lora/
- Source: S-LoRA: Serving Thousands of Concurrent LoRA Adapters: https://arxiv.org/abs/2311.03285
- Stitching idea: train small adapters for router, tutor, practice, grounding critic, and safety behavior; serve only the selected adapter during Agent calls.
- Experiment metric: adapter should improve JSON valid rate and grounding safe rate without raising forbidden leak rate.
- Local status: 1000-scale QLoRA adapter trained at `outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch`; offline heldout generation beats the raw baseline but does not yet beat the E1 prompt. vLLM LoRA serving validation is still pending.

## M2: Pedagogical Steering

- Source: Towards the Pedagogical Steering of Large Language Models for Tutoring, Findings of ACL 2025: https://aclanthology.org/2025.findings-acl.1348/
- Key module: represent tutoring policy as a transition graph, then steer generation toward the next teaching move.
- Stitching idea: map Pythontopia's "看 -> 学 -> 练 -> 测 -> 补救/换资源/继续" into a compact state transition graph.
- Local status: encoded into Agent case prompts through `task_focus`, learning signals, and GuideCoach/PathPlanner JSON contracts.

## M3: Coding Tutor Trace-and-Verify

- Source: Training Turn-by-Turn Verifiers for Dialogue Tutoring Agents, ACL Findings 2025/arXiv: https://arxiv.org/abs/2502.13311
- Source code/reference: https://github.com/iwangjian/Coding-Tutor
- Key module: knowledge tracing plus turn-by-turn verification for coding tutoring.
- Stitching idea: add a verifier score for every practice/debug dialogue turn before the Agent response is accepted.
- Local status: implemented as response scoring plus `audit_responses.py`; not yet a learned verifier.

## M4: Concise Tool Instruction

- Source: EASYTOOL, NAACL 2025: https://aclanthology.org/2025.naacl-long.44/
- Key module: compress long tool documentation into concise, structured tool instructions.
- Stitching idea: convert Pythontopia tool contracts into short JSON tool cards for IntentRouterAgent and ResourceDesignerAgent.
- Local status: implemented in `protocol_patch_v1` as compact strict JSON/tool-contract guidance.

## M5: Self-Correction and Verifier-Gated Improvement

- Source: SCoRe, ICLR 2025: https://openreview.net/forum?id=CjwERcAU7w
- Key module: train or prompt a model to produce a correction pass instead of trusting the first answer.
- Stitching idea: after generation, run a cheap self-check for JSON validity, evidence IDs, and forbidden answer leakage; only accept corrected output if it improves verifier score.
- Local status: partially implemented as audit-gated promotion; actual second-pass self-correction is a next experiment.

## M6: Agent Planning RL

- Source: Reinforcement Learning for LLM Agent Planning, EMNLP 2025 Industry Track: https://aclanthology.org/2025.emnlp-industry.116/
- Key module: separate action planning quality from final answer summarization quality.
- Stitching idea: train/evaluate the Agent planner on action sequence correctness before optimizing natural-language fluency.

## M7: Modern LoRA Practice

- Source: LoRA Without Regret, Thinking Machines Lab: https://thinkingmachines.ai/blog/lora/
- Source: Learning Rate Matters: Vanilla LoRA May Suffice for LLM Fine-tuning: https://arxiv.org/html/2602.04998v2
- Stitching idea: start with vanilla LoRA/QLoRA, sweep learning rate and rank before trying exotic adapters.
- Local status: used conservative rank-8 QLoRA first; no exotic adapter variant added.

## Current Stitched Module

E1 combines M2, M3, M4, and part of M5:

- pedagogical next-step state,
- concise tool/Agent contract,
- strict JSON artifact shape,
- evidence/common-knowledge discipline,
- safety redaction instead of direct answer leakage,
- post-run scoring and audit gate.

On the 1000-case set, the stitched module improves JSON validity from 0.686 to 0.980, grounding safety from 0.822 to 0.963, forbidden leak from 0.014 to 0.007, and audit clean rate from 0.612 to 0.975.

## Story Gate

No competition story should be written until a larger evaluation set shows:

- `json_valid_rate` improves by at least +0.25 absolute.
- `grounding_safe_rate` improves by at least +0.20 absolute.
- `forbidden_leak_rate` remains <= 0.02.
- Improvement holds across 5 folds and at least 3 random seeds on the expanded domain set.
