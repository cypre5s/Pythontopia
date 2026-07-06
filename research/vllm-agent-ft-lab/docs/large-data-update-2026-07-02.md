# Large Data Update: 2026-07-02

## What Changed

- Added a tracked 140-case Pythontopia domain evaluation set at `cases/domain_cases_140.jsonl`.
- Added a Google ED normalizer and normalized 7,234 real eval conversations into ignored output data.
- Added experiment comparison tooling and a strict story gate.

## Evidence

Commands run:

```bash
PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/generate_domain_cases.py \
  --count 140 \
  --output research/vllm-agent-ft-lab/cases/domain_cases_140.jsonl

curl -L --fail --max-time 120 \
  -o research/vllm-agent-ft-lab/outputs/datasets/raw/google-ed/conversations_eval.json \
  https://raw.githubusercontent.com/google-research-datasets/Education-Dialogue-Dataset/main/conversations_eval.json

PYTHONPATH=research/vllm-agent-ft-lab/src \
python3 research/vllm-agent-ft-lab/scripts/normalize_open_dataset.py \
  --dataset google-ed \
  --input research/vllm-agent-ft-lab/outputs/datasets/raw/google-ed/conversations_eval.json \
  --output research/vllm-agent-ft-lab/outputs/datasets/normalized/google-ed-eval.jsonl \
  --split eval
```

Observed counts:

- `cases/domain_cases_140.jsonl`: 140 lines.
- `outputs/datasets/normalized/google-ed-eval.jsonl`: 7,234 lines.

## Sources

- Google Education Dialogue Dataset: https://github.com/google-research-datasets/Education-Dialogue-Dataset
- MathDial: https://github.com/eth-nlped/mathdial
- Eedi Question-Anchored Tutoring Dialogues 2k: https://huggingface.co/datasets/Eedi/Question-Anchored-Tutoring-Dialogues-2k

## Next Gate

Do not claim an AI story from the small 10-case baseline. Use the 140-case domain set next, then expand to 200-300 reviewed rows. A candidate only becomes story-worthy if `compare_experiments.py` passes the story gate across repeated folds/seeds.
