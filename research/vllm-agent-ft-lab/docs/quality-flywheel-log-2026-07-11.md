# Qwen v2 semantic IR quality flywheel — 2026-07-11

本日志按事件增量维护。所有模型质量结论以 raw contract 为首要指标；guard/fallback 后的结果不得归因于裸模型。

## 事件 1：产品契约审计与 v2 扩展

- `agent-schema-v2` 已覆盖十个历史 Agent 名称，但明确区分运行时性质：
  - adapter eligible semantic IR：GuideCoach、Practice、IntentRouter、MindMap。
  - deterministic product output：Debug、PathPlanner、GroundingCritic、Safety。
  - streaming product output：Tutor。
  - product output pending IR split：ResourceDesigner。
- IntentRouter 和 MindMap 已接入真实产品调用的 `guard_context`；MindMap 不再让模型直接拼 Mermaid，而由 ContractCompiler 从节点/边 IR 编译。
- 不对确定性 Agent 或 Tutor 流式主回答做 JSON 偏好训练，避免历史评测名称与产品运行时再次错位。

## 事件 2：v2 偏好数据

- 路径：`outputs/preferences/v2-ir-semantic-400-20260711/`。
- 总计 400：train 360 / heldout 40；GuideCoach、Practice、IntentRouter、MindMap 各 100。
- 偏好轴：事件/意图选择、题目/图谱语义、证据白名单、安全边界各 100。
- source SHA-256：`23ae6fdf4eeef3653eac6ba7ea580cdd358fae4dc3bdeedca7e0bd03b4bd3039`。
- train SHA-256：`3cd1c932f650e40c6ab4051d825b0445e4b634fc54fb4160ee57eb4744059a68`。
- heldout SHA-256：`b0df0edba68cacd573cca5592f0572af71b129e46685c5ee57ab84adf5a68214`。
- golden 50 SHA-256：`dda6fe2bbe4ea4ddc75f3558f587cdc1e976398796bc7decd82d457b86fdcf83`。
- 分层 500 SHA-256：`69e33f6dddff052577630672d2aa4cf7903363e87e1e505d6f1ba2fb2522c1e0`。
- 按 ID、source lineage、prompt fingerprint 排除保护集，重叠 0。

## 事件 3：训练前检查

- 候选：`dpo-v2-ir-semantic-400-seed42-20260711`；输入为当前 DPO champion。
- 唯一变化变量：`preference_dataset`；训练方法、LoRA、beta、epoch、batch、梯度累积、学习率、max length、trainer/data seed 与 champion 相同。
- 单变量审计：通过。
- tokenizer preflight：最长 374 / 512，均值 309.35；零 label 0；相同 chosen/rejected 0；重复 ID/prompt/pair 均 0；train/heldout prompt overlap 0；保护集重叠 0。
- 两次 preflight launcher 失败已保留在会话证据中：第一次误用系统 Python（缺 transformers），第二次未设置 `PYTHONPATH=src`。第三次使用 `.venv/bin/python + PYTHONPATH=src` 成功；这两次失败不属于模型训练运行。

## 事件 4：首轮 v2 DPO 训练与 golden 拒绝

- 训练完成 23/23 step；train loss `0.07237`，heldout loss `0.00004055`，heldout preference accuracy `100%`。
- 训练末期 chosen reward `-1.776`、rejected reward `-13.269`：margin 虽达到 `11.493`，但 chosen 本身也被压低，出现 likelihood displacement 信号。
- v1 golden 50：candidate raw contract `68%`，与 champion `68%` 持平；schema 同为 `70%`、grounding `98%`、leak `0`，但 JSON 从 champion `100%` 退到 `96%`。
- v2 semantic golden 20：champion raw contract `25%`，candidate 仅 `5%`；candidate 分 Agent 为 GuideCoach `0%`、IntentRouter `0%`、MindMap `20%`、Practice `0%`。champion 的 IntentRouter 为 `100%`，说明训练造成明确跨 Agent 负迁移。
- 候选状态已写为 `rejected_golden`；按顺序门禁停止，不运行分层 500、完整 5000 或盲审。
- 根因：样本按 Agent 轮询、偏好轴按全局 index 轮询，导致每个 Agent 只看到一种 rejected 类型；四类样本都没有稳定覆盖“旧输出契约 → v2 IR”的结构迁移。

## 事件 5：论文驱动的下一步

- 2025–2026 preference optimization 工作指出 DPO 可能同时降低 chosen/rejected likelihood；本轮日志中的 chosen reward 下降与该现象一致。后续实验候选包括 TRL 原生 `sigmoid + sft` multi-loss，以 chosen SFT loss 抑制 likelihood displacement，但必须作为独立变量验证。
- ACL 2025 的 LoRA interference 与 adapter routing 工作支持把异质 Agent 分开路由；本轮 IntentRouter `100% -> 0%` 是产品内直接证据。但双 Adapter 仍不启用，先完成单 Adapter 数据修复门禁。
- 结构化生成论文提示逐 token constrained decoding 可能保证语法却扭曲语义；因此继续保留 unguided raw 指标，生产 structured decoding/guard 作为独立层统计。

## 事件 6：修复后的 v2.1 数据

- 路径：`outputs/preferences/v2-ir-balanced-500-20260711/`；总计 500，train 450 / heldout 50。
- 四个 Agent 各 125；每个 Agent 均衡覆盖五类偏好：legacy contract、wrong decision、weak semantics、unbound evidence、safety leak 各 25。
- train SHA-256：`5a146d60100cba50dc2ee871ace1ca83c9646205d845dea14e2f5287c120e3d7`。
- heldout SHA-256：`0ccf2ca6bad5410686e6bbf476bcc37c55bdf41e90de75659b16bd87fb87adad`。
- v2 system prompt 现在包含紧凑 IR 结构示例；这只描述语义 IR，不重新引入产品 DTO 模板。

## 事件 7：v2.1 OOM 与三 Agent 收缩实验

- 四 Agent 500 条训练在 step 5 发生 CUDA OOM；失败证据独立保存在 `outputs/adapters/dpo-v2-ir-balanced-500-seed42-20260711/failed_runs/20260711T062853Z/failure.json`，不与成功运行混记。
- 随后排除已在 compact v2 基线为 100% 的 IntentRouter，构造 GuideCoach / Practice / MindMap 三 Agent 450 条集；train 405 / heldout 45，五类偏好各 90，保护集重叠 0，最长 362 / 512 token。
- 三 Agent DPO 完成 26/26 step：train loss `0.08169`，heldout loss `0.0003984`，preference accuracy `100%`；chosen reward `+4.441`、rejected reward `-5.698`、margin `10.14`，未出现首轮 chosen likelihood displacement。

## 事件 8：递归 schema 复核与三 Agent 候选淘汰

- 发现旧评测只检查顶层 required/type，可能把嵌套 `$ref`、数组元素、enum、pattern 与数值约束错误误报为 schema valid；已升级为递归 JSON Schema 校验并增加嵌套缺字段、类型、pattern 回归测试。
- 在完全相同的三 Agent golden15 上重新计分：champion v2 raw contract `66.7%`，candidate v2 raw contract `33.3%`；两者 v1 raw contract 均为 `33.3%`。
- 分 Agent：champion 的 Practice `5/5`、MindMap `5/5`、GuideCoach `0/5`；candidate 的 Practice `5/5`、MindMap `0/5`、GuideCoach `0/5`。candidate 虽把 GuideCoach grounding 提升到 `5/5`，但没有修复其 schema，并破坏了 MindMap 的 `node_id` 约束，因此状态为 `rejected_golden`，禁止进入分层 500。
- 主要 raw 失败：GuideCoach 生成非允许事件名与字符串 hint level；MindMap 生成含连字符的 node id。此前浅层口径下的 v2 `100%` 作废，不作为模型能力结论。

## 事件 9：GuideCoach 单 Adapter 定向轮

- 架构决策：保留 champion 承载 Practice / MindMap / IntentRouter，仅训练 GuideCoach 候选；在真实同集通过前不启用双 Adapter 生产路由。
- 数据：`outputs/preferences/v2-ir-guide-schema-300-20260711/`，共 300 条，train 270 / heldout 30；legacy contract、schema constraint、wrong decision、weak semantics、unbound evidence、safety leak 各 50。
- schema constraint rejected 精确复现当前 raw 失败（非法事件名 + 字符串 hint level）；prompt 仅声明事件允许集合和 hint level 范围，不加入产品 DTO 固定样板。
- train SHA-256：`ae9b052ed840e36de87a0a9b2bac2ccdfa806b86e6de8a801b1d0fa2dcc9caf8`；heldout SHA-256：`4b0a9b674e625e55c3af5ea70bc5a68d59d67a5d187e757aa6bcd62399289799`；保护集重叠 0。
- preflight：最长 417 / 512、均值 398.46；零 label、重复 ID/prompt/pair、相同 chosen/rejected、train/heldout prompt overlap 均为 0；单变量审计通过。
- 训练已启动；晋级顺序仍为 GuideCoach golden 同集 → 分层同集 → 完整集 / 盲审，不以 heldout preference accuracy 代替产品评测。

## 事件 10：GuideCoach OOM 归档与语义等价压缩重试

- 首次 GuideCoach 300 条运行在 step 3/17 发生 CUDA OOM；失败已独立归档到 `outputs/adapters/dpo-v2-ir-guide-schema-300-seed42-20260711/failed_runs/20260711T124414Z/failure.json`，状态为 failed。
- OOM 前最长样本 417 token、均值 398.46；未通过的训练结果不进入模型比较。
- 保持 base/champion、DPO loss、beta、LoRA、epoch、batch、梯度累积、学习率、seed 与 max length 全部不变，仅压缩同一偏好数据的上下文键名和冗余措辞；语义轴、样本数与保护策略不变。
- 最终重试集：`outputs/preferences/v2-ir-guide-schema-compact4-300-20260711/`；train SHA-256 `2021da2a3c21d216c89b36865286dbad7754a8340a15dcadef28e2dcaafabc2d`，heldout SHA-256 `807cb34984a1dfc375405f3715c455ee66b9fa053dc8d05e2246e3e1ad0d18bd`。
- preflight 最长 361 / 512、均值 342.4，回到上一轮成功训练的显存安全区间；其他质量检查仍全部通过。重试已启动。

## 事件 11：三 Agent 失败候选的分层输出口径

- 递归 schema 重计后的 layered report：`outputs/comparisons/dpo-v2-ir-compact3-golden15-v2-deep-schema/`，包含逐案例 JSONL、失败明细、paired comparison 和 SVG 图表。
- champion raw / guard / final contract：`66.7% / 66.7% / 100%`，guard repair `0%`，fallback `33.3%`。
- rejected candidate raw / guard / final contract：`33.3% / 33.3% / 100%`，guard repair `0%`，fallback `66.7%`。
- final 100% 来自新增的确定性 v2 semantic fallback replay，不是模型达到 100%；candidate fallback 高于 champion，进一步违反晋级条件。
- 生产 Guard 的 JSON Schema 检查补齐 nested pattern / enum / const / oneOf / allOf / uniqueItems / additionalProperties，避免非法 MindMap node id 在 guard telemetry 中被误记为通过。

## 事件 12：GuideCoach compact4 候选与 IR 架构再收敛

- compact4 训练完成 17/17：train loss `0.09928`；heldout eval loss `0.0001654`、preference accuracy `100%`、chosen reward `+8.676`、rejected `-1.129`、margin `9.805`。
- 旧 WorkflowEvent enum 下真实 golden5 raw contract `0/5`，错误全部为 `next_event`；模型给出的教学语义是复习路径、阅读页面、检查提交，但不是产品内部枚举。
- 将开放大写事件别名交给 compiler 后，同 prompt 上 candidate / champion schema contract 都变成 `5/5`，但真实 compiler 可解析率分别仅 `4/5` 与 `0/5`；开放字符串会把错误从 schema 推迟到 compiler，compact4 仍不晋级、不跑分层50。
- v1 frozen GuideCoach golden5：candidate / champion contract 均 `0/5`；辅助 score `0.92 / 0.98`。该旧 DTO 口径只作兼容观察，不描述 v2 产品合同。
- 架构进一步收敛：模型只选择七类教学动作 `READ / REVIEW / TRACE / SCAFFOLD / PRACTICE / ASK / CHECK`，ContractCompiler 再映射到 WorkflowEvent 并检查 live allowlist；详细 action 继续由 `action_goal` 表达。

## 事件 13：GuideCoach 七类 event_intent 定向数据

- 路径：`outputs/preferences/v2-ir-guide-event-intent-240-20260711/`；总计 240，train 216 / heldout 24，六类偏好各 40，保护集重叠 0。
- train SHA-256：`bfcf64a4b1d1591043d920f3444c4ec8a0dc45976982b72a4b405a53c3a30a39`；heldout SHA-256：`c37a9f4de64ea06c72ead825fb91da563398ef13b58266d63d517f7c6a91222b`。
- preflight：最长 365 / 512、均值 347.73；零 label、重复、相同 chosen/rejected、split overlap 和保护集 overlap 均为 0；训练仍从原 champion 起步，唯一变量为 preference dataset。

## 事件 14：event_intent 候选的语义门禁淘汰

- 训练完成 14/14：train loss `0.12715`；heldout eval loss `0.0007158`、preference accuracy `100%`、chosen reward `+8.866`、rejected `-0.384`、margin `9.249`。
- golden5 上 candidate / champion 的 raw contract 均为 `100%`，但人工规则 gold 的 event_intent accuracy 分别只有 `20% / 0%`；candidate 主要塌缩到 TRACE。
- 新增 `semantic_valid_rate` 和 `promotion_pass_rate = contract_pass && semantic_valid`，不改变用户定义的 contract_pass；重新落盘后 candidate promotion pass `20%`、champion `0%`。
- 本候选状态为 rejected，不运行分层50。该结果证明仅报告 JSON/schema/grounding/safety 会掩盖教学决策错误。

## 事件 15：二维语义-偏好网格采样

- 首次均衡构造因保护集排除后 SCAFFOLD 为 0 而 fail fast；未伪造“七类已覆盖”。
- 对未受保护的真实 case 做可追溯反事实增强：保留 agent/chapter/KC/画像/证据/安全上下文，只替换 task_focus 与必要 learning_signal，补齐 READ / ASK / SCAFFOLD 稀缺类；metadata 保存 base source id 与 augmentation 标志。
- 新数据：`outputs/preferences/v2-ir-guide-intent-grid252-20260711/`，总计 252，七类 intent 各 36，六类 rejected 轴各 42；train 227 / heldout 25，反事实增强 48 条。
- train SHA-256：`7e0fbd8b865329597725fa7c6e60167e1124627bbc62df27b5fe5b4b25cf86c8`；heldout SHA-256：`4599a4191893fd4f288fa7197d13e37dd1c02c0fa284cb3c54bdbe3ec63c63ae`。
- preflight：最长 364 / 512、均值 349.36；污染、重复、零 label、相同 chosen/rejected、split overlap 均为 0；唯一变量仍为 preference dataset。

## 研究创新假设（保研项目口径）

- 暂命名 **SAGE-IR（Semantic Action Grid with Evidence-bound IR）**：小模型只预测教学语义 ontology；确定性 compiler 负责产品状态机、工具与 DTO；EvidenceBinder 负责引用命名空间；guard/fallback 独立统计。
- 训练创新：用“语义标签 × 失败轴”二维网格构造偏好对，并对稀缺标签做 evidence-preserving counterfactual augmentation，避免 DPO 在结构合同合格后发生 action collapse。
- 评测创新：分离 raw contract、semantic validity、compile validity、guard repair 与 final fallback；`promotion_pass` 要求 contract 与语义同时通过，防止 guard 后 100% 或 schema 100% 掩盖决策错误。
- 待消融：无语义网格 vs 一维失败轴均衡 vs 二维网格；固定 WorkflowEvent DTO vs 开放事件字符串 vs 七类 semantic ontology；单 Adapter vs 通过门禁后的路由 Adapter。
