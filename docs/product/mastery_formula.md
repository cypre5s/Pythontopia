# KC Mastery Formula

Pythontopia 用单一权威公式驱动 `kc_mastery` 更新；前端不允许做掌握度计算。

## 数据来源

- `kc_mastery_event(source, delta)`：每次事件追加 1 行，按时间倒序保留。
- `kc_mastery(mastery_score, confidence, last_event_at)`：当前快照。

## 事件 → delta 映射

| source             | 默认 delta | 触发阶段 |
| ------------------ | ---------- | -------- |
| `RESOURCE_READ`    | +0.10      | 阶段 07  |
| `PRACTICE_PASS`    | +0.20      | 阶段 09  |
| `PRACTICE_FAIL`    | -0.05      | 阶段 09  |
| `DEBUG_FIX`        | +0.15      | 阶段 10  |
| `MANUAL_OVERRIDE`  | 由教师指定 | 阶段 12  |
| `DECAY`            | 由衰减计算 | 本阶段   |
| `BOOTSTRAP`        | 0.00       | 本阶段   |

> 上述默认 delta 集中在 `application.yml` 的 `pythontopia.mastery.deltas` 命名空间，便于教学侧调参。

## 累积公式

```
mastery_score(new) = clamp(mastery_score(old) + delta, 0.0, 1.0)
```

## 置信度

`confidence` 与事件次数挂钩，使用 Beta 分布近似：

```
confidence(new) = clamp(confidence(old) + 0.05, 0.0, 1.0)   # 每次 success/practice/resource_read
confidence(new) = clamp(confidence(old) - 0.02, 0.0, 1.0)   # DECAY 触发
```

## 半衰期衰减

参数 `halfLifeDays` 默认 14（`pythontopia.mastery.halfLifeDays`）：

```
days = (now - last_event_at).total_days
mastery_score(new) = mastery_score(old) * 0.5 ^ (days / halfLifeDays)
```

衰减后会写入一条 `source=DECAY` 的 `kc_mastery_event`，`delta` 为新旧分差（负值）。

> **口径分层（Phase 1 后）**：上面的 14 天半衰期仅用于 `kc_mastery.mastery_score` 的快照衰减；
> 「遗忘风险 / 是否该复习」改由下文 **记忆矿脉 FSRS-lite 引擎** 计算，不再对所有 KC 一刀切 14 天。

## 记忆矿脉：FSRS-lite 复习引擎（Phase 1）

掌握度回答「会不会」，复习引擎回答「还记不记得、什么时候该复盘」。引擎为**纯函数、确定性、不调 LLM**
（`ReviewScheduler`），状态落在新表 `kc_review_state`（迁移 `V133`，空库从 `kc_mastery` 回填），
由 `MasteryEvaluator.applyEvent` 在每次掌握事件的**同事务**里演化，纯增量副作用，不改既有
`kc_mastery` / `kc_mastery_event` 行为。

三要素：难度 `D∈[0,1]`、稳定度 `S`（天，可提取性从 1.0 衰减到 0.9 锚点所需天数）、可提取性 `R∈[0,1]`。

```text
# 幂律遗忘曲线（DECAY=0.5，FACTOR = 0.9^(-1/DECAY) - 1 ≈ 0.2346，使 R(S)=0.9 恒成立）
R(t) = (1 + FACTOR * t / S) ^ (-DECAY)

# 达到目标留存率 target（默认 0.9）的复习间隔；target=0.9 时恰为 S
interval(S, target) = S * (target^(-1/DECAY) - 1) / FACTOR
due_at = last_review_at + interval(S, 0.9)         # now > due_at 即「到期 due」
```

事件 → 复习结果 → 稳定度演化（`outcome` 来自 `MasterySource`）：

| MasterySource | ReviewOutcome | 稳定度 S 演化（无既有状态时的播种） |
| ------------- | ------------- | ----------------------------------- |
| `PRACTICE_PASS` / `DEBUG_FIX` | `SUCCESS` | `S*(1 + 2.0*(1-R)*(1-0.3*D))`，取 max(旧,新)（播种 3.0）；D 向基线 0.30 回归 |
| `RESOURCE_READ` | `RESTUDY` | `max(S, S*1.15)`（播种 1.5） |
| `PRACTICE_FAIL` | `LAPSE` | `S*0.4`，且 `D += 0.15`（播种 1.0） |
| `DECAY` / `BOOTSTRAP` / `MANUAL_OVERRIDE` | —（不算一次复习） | 不演化 |

- 间隔效应：越接近遗忘（R 越低）时成功召回，S 增长越多。
- 约束：`S ∈ [0.5, 365]`、`D ∈ [0,1]`；难度成功时向基线 0.30 均值回归，避免「难度地狱」。
- 无 `kc_review_state` 行时按掌握度播种：`seedStability = clamp(1 + 13*mastery, 0.5, 14)`，承接旧 14 天口径并与 V133 回填一致。
- 对外信号 `ReviewSignal(kcCode, stability, retrievability, due, lastReviewAt, dueAt, reps, lapses)`，供错题排序、遗忘回声与 Phase 2 学习总管共用。

### Phase 2 接缝：记忆矿脉回声 → 学习总管

`BugMineService.decayingKcCodes(userId, limit)` 复用遗忘回声的 FSRS 到期判定与优先级排序，输出当下到期 KC；
`OrchestratorService` 每轮取前 5 个，经 `OrchestratorGraphClient` 以 `decaying_kcs` 透传给 Python graph 的学习总管
（`OrchestratorAgent._select_kcs` 把它提到最高优先级：到期 > 最近失败 > 最近接触），让看/学/练/测计划优先锚定待复习知识点。
百晓工坊意图路由新增 `REVIEW` 意图（关键词「复习/复盘/温习」或 LLM 判定），引导学生回错题矿洞按到期 KC 复盘（非生成）。

## Bug 矿洞推荐优先级

错题本排序由后端 `BugMineService` 计算 `minePriority`，前端只展示结果，不做掌握度或推荐计算：

```text
mine_priority =
  0.35 * (1 - mastery_score)
+ 0.20 * decay_risk
+ 0.20 * recent_fail_weight
+ 0.15 * profile_pattern_hit
+ 0.10 * (1 - confidence)
```

其中：

- `decay_risk = 1 - R`，`R` 为 FSRS-lite 可提取性（见「记忆矿脉」一节）；已替换原先全局 14 天半衰期口径。
- `recent_fail_weight` 在失败后 24 小时内为 1，之后到 7 天线性降到 0。
- `profile_pattern_hit` 来自 `learner_profile` 易错标签与本次判题/题目常见错误的匹配。
- 推荐原因按主导信号生成，例如“掌握薄弱”“遗忘回声”“画像命中”“近期失败”，并附带建议目标层级。

## Bug 矿洞遗忘回声

`GET /api/bug-mine/echoes` 从 `kc_mastery` 中召回“曾经会、现在该复查”的 KC，不依赖已有错题 session，
仅保留 FSRS **到期（due）** 的候选并按优先级排序：

```text
echo_priority =
  0.45 * decay_risk          # decay_risk = 1 - R（FSRS-lite）
+ 0.25 * (1 - confidence)
+ 0.20 * (1 - mastery_score)
+ 0.10
```

候选门槛：

- **FSRS 到期**：`now > due_at`（无 `kc_review_state` 行时按掌握度播种间隔 `clamp(1 + 13*mastery, 0.5, 14)` 天，与 V133 回填同口径）——替换原先对所有 KC 一刀切的「早于 14 天」。
- `mastery_score >= 0.45`，避免和“掌握薄弱”错题混在一起。
- 同一 KC 没有未关闭的 Bug 矿洞 session。
- 入矿题按掌握度选恰当难度（E）：`mastery >= 0.75` 偏 HARD，`0.45–0.75` 偏 MEDIUM。
- `POST /api/bug-mine/echoes` 会写入真实 `practice_submission(source=BUG_MINE_ECHO)`，再复用 DebugAgent 生成 5 层矿洞卡片。

## 状态映射

| 状态        | 条件 |
| ----------- | ---- |
| `LOCKED`    | `crop_tile_state.unlocked = false` |
| `SEED`      | `unlocked = true` 且 `mastery < 0.2` |
| `SPROUT`    | `0.2 ≤ mastery < 0.5` |
| `MATURE`    | `0.5 ≤ mastery < 0.8` 或 `0.8 ≤ mastery` 但已收获 |
| `WITHERED`  | `last_event_at` 早于 30 天且 `mastery < 0.2` |

阶段 13 的复盘逻辑会重置 `MATURE → SEED` 完成"再播种"。

## 不变约束

- `mastery_score` ∈ `[0.0, 1.0]`。
- `confidence` ∈ `[0.0, 1.0]`。
- `delta` 可正可负，但应用后必须裁剪到合法区间。
- 同一学生同一 KC 只有一行 `kc_mastery` 快照。
- 每次更新必须追加 `kc_mastery_event`，禁止只改快照不写事件。
