# Qwen v2 SAGE-IR quality flywheel — 2026-07-12

本日志延续 `quality-flywheel-log-2026-07-11.md`。所有晋级结论同时检查 raw
contract 与 semantic validity；guard/fallback 后的结果不归因于裸模型。

## 事件 1：二维网格纯 DPO 结果

- 候选：`dpo-v2-ir-guide-intent-grid252-seed42-20260711`。
- 数据：252 条，七类 event_intent 各 36，六类 preference failure axis 各 42；
  train / heldout 为 227 / 25，保护集 overlap 0，反事实增强 48 条且保留 base lineage。
- 训练 15/15：train loss `0.13099`；heldout loss `0.0003916`、chosen reward
  `+8.102`、rejected `-0.391`、margin `8.494`、preference accuracy `100%`。
- golden5：raw contract `100%`，semantic_valid / promotion_pass 仍为 `20%`；
  混淆为 `REVIEW→REVIEW`、`READ→SCAFFOLD`、`CHECK→TRACE`、
  `REVIEW→TRACE`、`READ→SCAFFOLD`。
- 结论：二维网格消除了训练数据标签缺失，但纯 DPO 未提高 strict 教学动作准确率；
  候选 rejected，不运行分层50。

## 事件 2：论文驱动的 chosen-SFT 单变量消融

- 动机：2025–2026 preference optimization 工作指出 DPO 可能发生 chosen likelihood
  displacement 或只学会相对偏好而不能稳定复现 chosen 决策；前序纯 DPO 结果与后者一致。
- 固定变量：完全复用 grid252 数据哈希、base/champion adapter、seed 42、LoRA r8、
  beta 0.1、epoch 1、batch/gradient accumulation、learning rate 5e-5、max length 512。
- 唯一变化：TRL loss 从 `sigmoid` 改为 `0.8 * sigmoid + 0.2 * sft`；SFT 只作用于
  chosen completion，不改 rejected、prompt 或数据分布。
- 计划：`outputs/training/dpo-sft-v2-ir-guide-intent-grid252-seed42-20260712/training_plan.json`。
- 候选：`outputs/adapters/dpo-sft-v2-ir-guide-intent-grid252-seed42-20260712/`。
- preflight：通过；训练已启动。仍按 golden5 semantic gate → 分层50 → 后续完整门禁执行。

## 事件 3：chosen-SFT 消融结果

- 训练完成 15/15：train loss `0.2864`；heldout loss `0.06543`、chosen reward
  `+17.75`、rejected `+9.07`、margin `8.68`、preference accuracy `100%`。
- 相比纯 DPO，chosen likelihood 明显提高，证明 TRL multi-loss 配置生效；但 golden5 的
  raw contract / semantic_valid / promotion_pass 仍为 `100% / 20% / 20%`，逐例 intent
  混淆与纯 DPO 相同。
- 结论：当前错误不再归因于 chosen likelihood displacement；继续调整 SFT 权重属于无证据
  盲搜。本候选 rejected，不运行分层50，不启用 Adapter 路由。
- 下一步转向标签问题：建立不查看模型身份的多标签 admissible-intent 与 pairwise ranking
  标注协议；在标注完成前，strict 单标签仍作为保守 fail-fast，不把 post-hoc 宽松集合用于晋级。

## SAGE-IR 研究主张的证据边界

- 已有证据支持：语义 ontology + compiler 能阻止模型记忆产品 WorkflowEvent/DTO；二维
  网格能显式审计标签覆盖；semantic gate 能识别 schema 100% 下的动作错误。
- 尚无证据支持：二维网格能提高真实动作准确率、路由 Adapter 优于单 Adapter、完整5000
  或人工盲审达标。因此不得在论文/简历中写成已验证结论。
- 下一可证伪点：chosen-SFT 是否把 golden5 strict semantic 从 20% 提升且不损害 contract、
  grounding、安全和 fallback；若无提升，停止同数据训练并转向人工多标签 gold / ranking 评测。

## 事件 4：ResourceDesigner 四子 Agent 的 Evidence-bound IR

- 真实产品链路新增 `ResourceDesignerAgent.handout / reading / code_lab / storyboard`
  四个 v2 schema；模型只输出教学语义和 opaque `evidence_refs`，compiler 负责 `PageRef`、
  KC、时长、voice、image prompt 等产品字段。
- 修复 ReadingAgent 旧链路未向模型提供 RAG 证据的问题；Handout/Reading 不再允许模型生成
  `page_id/page_no/chapter_id`。CodeLab compiler 使用 AST 拦截 import、文件/网络/系统调用、
  `eval/exec`；Storyboard compiler 将 `brief/normal/deep` 编译为 20/45/75 秒。
- guard 支持 `sections.*.evidence_refs` 等嵌套路径；离线 scorer 修复“合法证据出现即可、额外
  伪造证据未被拒绝”的漏洞，现在 nested `page-invented` 会使 grounding fail。
- 产品定向回归 75 条通过；v2 registry 四子 Agent 均登记为 evidence-bound semantic IR，
  通用 `ResourceDesignerAgent` 保持 `subagent-ir-split-in-progress`、不可直接训练。

## 事件 5：Resource v2 偏好数据、OOM 负结果与语义无损压缩

- 首版数据 240 条，四子 Agent 各 60，六失败轴各 40；golden50/分层500 通过 ID、lineage、
  prompt fingerprint 排除，overlap 0。train/heldout `216/24`，最长 438 tokens，零 label、
  重复与 train-heldout prompt overlap 均为 0。
- 纯 DPO 512 在 step 7 OOM；仅改 max length 为 448 的 DPO 在 step 1 OOM；同数据 reference-free
  ORPO 在 step 4 OOM。三次失败分别归档在对应 adapter 的 `failed_runs/*/failure.json`，不得
  把失败前 loss/reward 当作候选结果。
- 基于 OOM 证据进行语义无损压缩：保留四 Agent、六失败轴、画像/学习信号/误区/证据/安全
  与全部 IR 字段，只删除重复自然语言，最长 `438 -> 359`、均值 `327.39 -> 266.10` tokens。
  新数据哈希：train `73125d18b6c37f815f62d46209db78380d8dd7fecd0b0c92b38cbe1fadeddd3f`，
  heldout `fc40ab8a8d152f8017d7f07b5f936184e264e9e5c57e1b465d37aab04db5f594`。
- 压缩 ORPO 完成 14/14：train loss `2.576`；heldout loss `1.766`、chosen `-0.1430`、
  rejected `-0.1613`、margin `0.01826`、preference accuracy `75%`。负 reward 是平均 token
  log-prob 尺度；只以 chosen>rejected 与真实生成门禁解读。

## 事件 6：Resource golden20 同集结果与淘汰决定

- 完全相同的 20 条（原 golden50 中 5 个 Resource case × 四子 Agent）、prompt、temperature=0、
  max tokens 与 scorer 上，champion raw 为：JSON `50%`、schema/contract `0%`、grounding
  `95%`、leak `0`；ORPO candidate 为：JSON `65%`、schema/contract `20%`、grounding
  `90%`、leak `0`。
- 逐 Agent raw contract（每类 5 条）：champion 四类均 `0/5`；candidate Handout `3/5`、
  Reading `0/5`、CodeLab `0/5`、Storyboard `1/5`。主要失败为 Handout/Reading 输出 YAML
  或自然语言、CodeLab 空 code / `common_mistakes` 标量、Storyboard 少于 3 scenes，以及两条
  Reading 证据写法越过 allowlist。
- v2 fallback 已补齐四子 Agent。离线 replay 的 final contract/schema/grounding 为 100%、
  leak 0，但 candidate fallback rate `80%`（champion `100%`），不能把 final 100% 归因于模型。
- 决定：candidate **rejected**；绝对 raw contract 仅 20%、grounding 退化，不跑分层200，
  不跑完整5000/盲审，不启用双 Adapter 路由。下一轮只根据上述真实失败类别重构非保护集
  rejected，而不再次均匀重复相同六轴数据。

## 事件 7：failure-directed ORPO 单变量负结果

- 根据事件 6 的真实失败构造 240 条新偏好：四子 Agent 各 60；失败轴覆盖 YAML/自然语言、
  缺 sections/items/evidence、空 code、`common_mistakes` 标量、少 scenes、非法 pacing、
  unbound evidence、弱语义与安全泄露。train/heldout 为 `216/24`；golden 与分层保护集按
  ID、lineage、prompt fingerprint 排除，overlap、重复和零 label 均为 0。
- 数据哈希：train `058044a304d990b8e928cc42a2b8fd41a753a1a2e8277a97778ea9e462e4aaee`；
  heldout `d548a2d08bc442715913aac9a914b08ebb58969b998a940335e18463ae4b95fb`；最长/均值
  `364 / 265.73` tokens。
- 只修改偏好数据失败分布，仍从原 champion adapter 启动，ORPO beta `0.1`、LR `5e-5`、
  seed `42`、max length `512` 等控制不变。训练 14/14：train loss `2.5713`；heldout loss
  `1.7583`、chosen `-0.1430`、rejected `-0.1806`、margin `0.0377`、accuracy `83.3%`。
- 同一 golden20 的 unconstrained raw 与上一 compact ORPO 完全同档：JSON `65%`、schema/
  contract `20%`、grounding `90%`、leak `0`；逐案例 contract 与 auxiliary score 全部 tie。
  preference margin 改善没有转化为生成改进，候选 **rejected**，不进入分层500。

## 事件 8：SAGE-IR + EBCD 推理解码消融

- 新增三个明确分层的解码模式：`unconstrained` 保留为模型退化哨兵；`json_schema` 只约束
  静态 v2 IR 形状；`json_schema_bound` 进一步把每个请求的 evidence allowlist 写入
  `evidence_refs.items.enum`。当证据存在时，动态要求字段存在且 `minItems=1`。
- 第一次 bound 运行在 7/20 时发现 `evidence_refs` 仍可省略，主动中止并保留 partial run，
  修正 requiredness 后从空目录重跑；未把这段 partial 当作候选结果。
- failure-directed candidate：unconstrained contract `20%`；静态 schema 后 JSON/schema
  `100%`、grounding/contract `65%`；evidence-bound 后 JSON/schema/grounding/contract
  `100%`、leak `0`，但 semantic/promotion 仅 `80%`，四个 CodeLab 输出为 C 风格代码或只有
  函数签名，Python AST gate 全部拒绝，计 fallback `20%`。
- 原 champion 在完全相同 evidence-bound 模式下 contract `100%`、semantic/promotion
  `75%`、fallback `25%`。这证明合同提升主要来自推理架构；candidate 只多通过 1/20，且
  unconstrained raw 仍不达标，因此不晋级、不运行分层500/完整5000/盲审、不启用路由。
- 权威报告：`outputs/quality-flywheel/resource4-evidence-bound-decoding-golden20-20260713/`；
  含六组同案例 layered JSONL、逐案例配对、失败矩阵和 SVG。其 final 100% 来自确定性 fallback
  replay，不能描述为模型全面达到 100%。

## 事件 9：产品接入与可观测性

- 产品仍只通过 `FineTunedFallbackProvider + guard_context` 使用 adapter。Provider 仅对本地
  fine-tuned 路径注入 request-bound JSON Schema；外部 fallback 保留原 `json_object` 请求，
  避免 vLLM 特有能力破坏兜底链。
- `GuardMetrics` 新增 `schema_constrained_rate` 与 `evidence_bound_rate`；既有
  `guard_repair_rate/fallback_rate` 保留。CodeLab 将 compiler/AST 校验注册为 deterministic
  semantic guard，非法 Python 会触发可计数 provider fallback，而不是被 contract 100% 隐藏。
- 下一单变量不是再调 ORPO 超参，而是把 CodeLab 自由 `code` 字符串收缩为可编译的教学代码
  计划/模板选择 IR；模型训练目标变为 Python 教学语义和模式选择，compiler 负责语法安全。

## 事件 10：CodePlan v2.1 架构消融

- 冻结历史 `agent-schema-v2` CodeLab 自由代码合同，新增独立 `agent-schema-v2.1`：模型只输出
  `example_pattern / observation_goal / narrative / common_mistakes / evidence_refs`；禁止额外
  `code/expected_output` 字段，compiler 从八类教学模式生成可解析、无 import/文件/网络调用的
  Python 与预期输出。这样把“题目/讲解语义选择”和“Python 语法正确性”拆开计量。
- 受保护 golden20 只替换 5 条 CodeLab IR，其他 15 条、champion adapter、prompt 上下文、
  temperature=0、seed 42、单并发和 evidence-bound decoding 不变。数据哈希
  `cd92b50ddb6beb7558bc569c9e5d2bc9199460da2e6c3b628633d472924b487e`。
- 正确注册的 champion `e1-protocol-patch-v1-1000-v1-dpo-v2-len512` 结果：raw constrained
  JSON/schema/grounding/contract `100%`、leak `0`、semantic/promotion `90%`。相对旧自由代码
  IR 的 `75%` 提高 15 个百分点，但样本仅 20 条，不能视为全面达标。
- CodeLab 逐例模式选择为 `3/5`：while、if、作用域正确；变量绑定误选 `string_cleanup`，
  行尾换行误选 `exception_guard`。错误由原先 5 条非法 Python 收敛为 2 条可解释的语义路由
  错误，且不会进入 compiler。
- 一次运行误用了早期 `qlora-2epoch` adapter，在发现注册表路径不一致后主动中止；partial
  目录 `runs/champion-v2-ir-resource4-codeplan-v21-golden20-json-schema-bound-20260713/`
  标记 `failed`，不进入任何结论。评测 manifest 现新增显式 `adapter_path`，避免只记录服务名。

## 事件 11：CodePlan 定向偏好轮启动

- 从完整 5000 的 500 条 ResourceDesigner 非保护案例中构造 240 条 CodePlan v2.1 偏好；
  golden50 与分层500 按 ID、source lineage、稳定 prompt fingerprint 三层排除，overlap 0。
  train/heldout `216/24`，哈希分别为
  `65b15ede43854785667ca828adb46d0251b5289359bc059c27630fd8c0c1c854` / 
  `6112358e6c463b5b7ef0cd08a1899222c555f0ac5a9c8f360e37792e2a8d9918`。
- 语义标签覆盖八类 compiler pattern：assignment/conditional/exception/for/function/list/
  string/while 分布为 `33/33/32/32/32/32/13/33`；string_cleanup 仅 13 是非保护源数据的真实
  稀缺性，不复制评测行或制造重复 prompt。
- rejected 轴为 wrong pattern 120、unbound evidence 30、legacy free code 30、安全泄露 30、
  弱观察目标 30；chosen 不含固定产品 DTO 或自由代码，只教授教学模式、证据与安全边界。
- preflight 通过：最长/均值 `314 / 289.6` tokens，overlength、zero label、重复 ID/prompt/pair、
  train-heldout prompt overlap 和保护集 overlap 均为 0；单变量为
  `codeplan_v21_preference_dataset`。ORPO 仍固定 beta 0.1、LR 5e-5、seed 42、r8、epoch 1、
  max length 512，并从当前 champion adapter 启动。

## 事件 12：CodePlan ORPO 同集回归与淘汰

- 训练完成 14/14：train loss `2.7291`；24 条训练外 heldout loss `1.7478`、chosen
  `-0.1527`、rejected `-0.1732`、margin `0.0205`、preference accuracy `100%`。heldout
  仅证明偏好排序拟合，不作为模型晋级证据。
- golden20 evidence-bound 上 candidate 的 JSON/schema/grounding/contract 为 `100%`、leak `0`，
  semantic/promotion `95%`，相对 champion 的 `90%` 修复变量绑定，但仍把行尾换行误选为
  `exception_guard`。unconstrained raw contract 仅 `25%`，离线 final 100% 来自 `75%`
  fallback，不能归因于裸模型。
- 因子集受约束结果仅达到继续评测条件，随后在完全相同的原始 golden50 v1、prompt、
  temperature=0、seed 42、单并发和 unconstrained decoding 上重跑 champion 与 candidate。
  champion raw contract/schema/grounding/JSON/leak 为 `68%/70%/98%/98%/0`；candidate 为
  `56%/70%/86%/100%/0`。candidate 的 guard repair 从 `30%` 升到 `44%`，fallback 从
  `2%` 降到 `0%`，但 repair 改善后的满分不能覆盖裸模型合同和 grounding 退化。
- 逐案例配对按 contract 优先、辅助 score 次序统计：champion `7` 胜、candidate `1` 胜、
  `42` 完全平；六条 contract 净退化集中在 PathPlanner `3` 条与 Safety `3` 条，均为
  grounding 失败。报告与逐例 JSONL、失败矩阵、SVG 位于
  `outputs/quality-flywheel/codeplan-v21-pattern240-golden50-v1-20260713/`。
- 决定：candidate **rejected**。不运行分层500、完整5000或人工盲审，不启用双 Adapter。
  CodePlan v2.1 架构本身保留；下一轮先解决共享 Adapter 的跨 Agent 遗忘/梯度干扰，再考虑
  扩展 Agent 或训练，而不是在同一 240 条数据上盲调超参。
