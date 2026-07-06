# Dataset Plan

## Design

Use a two-layer dataset strategy:

1. Large open tutoring/dialogue data for pedagogy, dialogue policy, and refusal/hint style.
2. Pythontopia-specific Python/Agent cases for domain evaluation and final story claims.

The large open data should not be used blindly as final evidence because most public tutoring datasets are math, general education, or AI-course chats rather than Pythontopia's `Python语言基础` workflow.

## Candidate Open Datasets

| Dataset | Size Signal | Role | Source |
|---|---:|---|---|
| Google Education Dialogue Dataset | 40,000 train + 7,234 test conversations | Main large-scale tutoring dialogue source | https://github.com/google-research-datasets/Education-Dialogue-Dataset |
| StudyChat | real student interactions with an LLM tutor in a university AI course | Real LLM-student behavior and programming-assignment context | https://arxiv.org/html/2503.07928v4 |
| WHJ programming tutoring corpus | 6K conversations, 18.5K utterances | Closest programming-tutoring style signal found | https://www.research-collection.ethz.ch/bitstreams/e53a7998-775f-4f1e-a181-3f510b771fac/download |
| MathDial | about 3K one-to-one tutoring dialogues | Pedagogical hinting and avoid-solution behavior | https://github.com/eth-nlped/mathdial |
| Eedi Question-Anchored Tutoring Dialogues | 2K real student-tutor dialogues | Privacy-aware real tutoring conversations | https://huggingface.co/datasets/Eedi/Question-Anchored-Tutoring-Dialogues-2k |

## Fold Strategy

- Minimum: 5-fold evaluation for local Pythontopia cases.
- Preferred: 5 folds x 3 seeds once the evaluation set reaches at least 100 domain cases.
- Stratification keys:
  - `agent`
  - `chapter_or_kc`
  - `task_type`
  - `requires_evidence`
  - `forbidden_answer_risk`

## Data Scaling Plan

Phase D0, current:

- 10 handcrafted Pythontopia cases.
- Purpose: smoke baseline and tooling.

Phase D0.5, completed on 2026-07-02:

- Generated `cases/domain_cases_140.jsonl`.
- Downloaded and normalized Google ED eval into `outputs/datasets/normalized/google-ed-eval.jsonl`.
- Normalized row count: 7,234.
- Raw/normalized external files remain ignored by Git; the manifest and normalizer are tracked.

Phase D1:

- Completed generated expansion to `cases/domain_cases_1000_v1.jsonl`.
- 1000 cases, 10 Agents x 100 cases each, chapters 1-7 balanced to 142/143 cases each.
- 1000 unique prompts with learner profile, learning signal, classroom scene, misconception, and task-focus variation.
- 300 no-evidence cases and 66 forbidden-answer-risk cases.
- Next: create a manually reviewed benchmark slice from the 1000 generated cases for final claims.
- Include every default Agent class and at least 7 chapters of pack 43.
- Keep human-readable expected markers and evidence IDs.

Phase D2:

- Import/slice large open datasets into normalized records:
  - `dialogue_context`
  - `student_state`
  - `tutor_action`
  - `target_json`
  - `source_dataset`
  - `license_or_terms_note`

Phase D3:

- Completed first SFT distillation from the clean 1000-case E1 run.
- SFT output: `outputs/sft/e1-protocol-patch-v1-1000-v1`.
- Split: 780 train / 195 heldout.
- Use automatic audit plus manual review before final competition claims.
