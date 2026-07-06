# Story Draft: From Local Qwen3.5 to Protocol-Governed Learning Agents

Pythontopia does not need a bigger chat box. It needs a small local model that can behave like an auditable learning Agent.

The first local experiment used Qwen3.5-4B-AWQ on vLLM. The baseline was already strong at producing useful educational content, but weak at behaving like a production Agent. On the cleaned 140-case domain set, raw output reached a high mean score, yet strict JSON stayed at 60 percent and grounding discipline stayed at 64.29 percent. This is exactly the failure mode that breaks downstream Agent systems: the answer sounds good, but the protocol is unreliable.

The E1 protocol module changed the contract, not the weights. It required one JSON object, no Markdown wrappers, explicit evidence IDs when evidence is required, and `common_knowledge` when no page evidence is required. On the same five folds, JSON validity rose from 60 percent to 100 percent, grounding safety rose from 64.29 percent to 100 percent, and response-audit cleanliness rose from 57.14 percent to 100 percent, while forbidden-answer leakage stayed at zero.

The story is therefore not "we made the model bigger." The story is: we made the local model governable. Pythontopia's Agent layer can ask for evidence, check output contracts, record failures, and only promote a module when it passes an eval gate. This turns local LLM use from a demo into an engineering loop.

Next, the protocol behavior should be distilled into LoRA training data and tested on held-out cases. If the adapter preserves the E1 gains without carrying the prompt scaffold, the story becomes stronger: Pythontopia can run a local, fine-tuned, evidence-aware Agent brain that remains independent from the web/backend runtime and can still be served through vLLM.
