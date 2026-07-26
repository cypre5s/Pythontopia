# GuideCoach Semantic Adjudication Rubric

This is a pre-promotion label-quality audit, not the final full-evaluation blind review.
Do not open the answer key until every row is complete.

For each item:

1. Read only the learner context and task focus. Before comparing responses, fill
   `acceptable_intents` with every defensible intent, comma-separated.
2. Select one `primary_intent`; use the most direct next teaching action, not a downstream action.
3. Compare response A and B for intent fit, evidence boundary, safety, and action usefulness.
4. Fill `preferred_response` with `A`, `B`, `TIE`, or `NEITHER`.
5. Confidence: 1=ambiguous, 2=moderate, 3=clear. Explain confidence 1 or any NEITHER.

Intent definitions:

- `READ`: 先阅读或重读允许证据，定位关键句。
- `REVIEW`: 复盘已有误区、概念边界或先修知识。
- `TRACE`: 追踪程序执行、变量或控制流变化。
- `SCAFFOLD`: 提供分步支架、骨架或渐进提示。
- `PRACTICE`: 安排最小练习或迁移任务。
- `ASK`: 追问学生的判断、证据或缺失上下文。
- `CHECK`: 检查提交、解释或达到停止条件。

Invalid review rows: missing primary intent, primary not in acceptable set, or preferred response
outside A/B/TIE/NEITHER. At least two independent reviewers are required before labels can replace
the conservative strict rule in promotion scoring.
