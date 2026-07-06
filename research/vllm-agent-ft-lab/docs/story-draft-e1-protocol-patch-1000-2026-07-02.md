# Story Draft: Local Agent Brain Became Governable

On the 1000-case Pythontopia Agent evaluation, the local Qwen3.5-4B-AWQ baseline already knew much of the Python tutoring content, but it behaved like a loose chat model: JSON validity was 68.6 percent, audit cleanliness was 61.2 percent, and evidence IDs were often dropped or wrapped in Markdown.

The stitched E1 module changed the contract around the same model. It combined concise tool-card style instructions, strict JSON-only output, evidence/common-knowledge discipline, tutoring-safety redaction, and verifier-style audit gates. On the same 5 folds and 1000 cases, JSON validity rose to 98.0 percent, audit cleanliness rose to 97.5 percent, grounding safety rose to 96.3 percent, and forbidden leakage fell to 0.7 percent.

The honest story is not "we made the model bigger." The story is: Pythontopia made a small local model governable enough for an Agent system. The Agent layer can ask for structured artifacts, check evidence behavior, reject unsafe outputs, and preserve a fallback path where the project still runs without the fine-tuned vLLM.

The strict research gate is still not fully closed, because grounding improved by +14.1 points rather than the pre-set +20 point threshold. So the competition story should say "major protocol reliability uplift, grounding still under active improvement", not "grounding solved."

The next stronger story is weight-level: 975 clean protocol responses were distilled into a 1000-scale QLoRA adapter, with heldout loss improving from 0.2904 after epoch 1 to 0.2410 after epoch 2. On the 195 heldout source cases, the adapter beat the original baseline: score rose from 0.9681 to 0.9891, JSON validity rose from 74.87 percent to 94.36 percent, grounding safety rose from 80.0 percent to 95.38 percent, and forbidden leakage dropped to zero.

The adapter does not yet beat the E1 prompt itself. That gives the cleanest narrative: Pythontopia first found a protocol module that makes a local Agent governable, then distilled much of that behavior into a local QLoRA adapter. The adapter is already better than the raw model on heldout prompts, while the prompt module remains the upper control baseline and the next target for vLLM LoRA serving or a stronger grounding pass.
