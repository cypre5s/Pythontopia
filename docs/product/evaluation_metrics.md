# 学习效果评估指标公式（阶段 13）

> 评估周期默认 `periodDays = 7`，可通过 `POST /api/learner/evaluations` 的 `periodDays`
> 字段覆盖，范围 1~30。所有指标在 `LearnerEvaluationService.compute` 内确定式计算，
> 后端是唯一权威写入；前端只负责展示与触发 accept/keep-old。

## 1. 周期窗口

- `period_end = now()`（UTC）。
- `period_start = period_end - periodDays`。
- 所有度量均严格使用 `(period_start, period_end]` 半开区间，避免边界重复计入。

## 2. 指标清单

| metric_key | 含义 | 公式 | display_unit |
| --- | --- | --- | --- |
| `kc_mastery_total` | 学生当前所有 KC 掌握度之和 | `SUM(kc_mastery.mastery_score)` | `score` |
| `kc_mastery_delta_total` | 周期内 KC 掌握增量总和 | `当前 SUM - 上一次评估的 kc_mastery_total` | `score` |
| `practice_pass_rate` | 周期内 AC 提交占比 | `AC 提交数 / 周期内总提交数`（分母为 0 时为 0） | `ratio` |
| `bug_mine_resolve_rate` | 周期内 Bug 矿洞修复率 | `RESOLVED session 数 / (RESOLVED + OPEN + ABANDONED 在周期内打开的总数)` | `ratio` |
| `profile_confidence_delta` | 周期内画像置信度变化 | `周期内 source IN (PRACTICE_PASS, DEBUG_FIX, EVALUATION) 的 revision 数量 - source IN (PRACTICE_FAIL) 的数量`，再除以总数缩放到 [-1, 1] | `score` |
| `resource_consumption_count` | 周期内消化的资源数 | 周期内 `feedback_status != NONE` 数 + `kc_mastery_event.source = RESOURCE_READ` 的不同 KC 数 | `count` |

注释：
- `numerator` / `denominator` 仅在比率/比值类指标里写入；纯计数指标 `numerator = value`、`denominator = 0`。
- `value` 一律使用 `BigDecimal`（scale 4），如 `0.7500`、`12.0000`，避免精度漂移。
- 指标顺序固定（按 metric_key 字典序），便于前端稳定渲染。

## 3. 排名（仅班级内匿名）

班级排名不存入 `learner_evaluation_metric`，仅在 GET 详情接口中即时返回：

```
ClassroomRanking{
  classroomId: 班级 ID（若学生属于多个班级，取首个 ACTIVE）
  anonymousIndex: 仅当前学生可见的匿名号（不暴露同学姓名）
  totalMembers: 班级 ACTIVE 成员数
  metricKey: kc_mastery_total / practice_pass_rate
}
```

排名只在前端展示时使用，不持久化，不暴露姓名/学号。

## 4. narrative_summary 生成

后端按上表算完所有数值后，调用 `EvaluationNarrativeAgent`（graph）生成自然语言解释：

- 输入：所有 `metrics` + `top_kcs`（按 `delta_score` DESC 取前 5）+ `recent_events`（前 3 条）+ `profile_brief`。
- Agent 强制 grounding：narrative_md / path_diff_explainer / key_recommendations 中
  出现的所有数字必须在 metrics 内，所有 KC 必须在 top_kcs 内。任何越界数字或 KC 直接判 FAIL。
- 评估周期内无任何事件 → metrics 全 0 → agent 返回 PARTIAL，narrative 走"近期无活动"模板。

## 5. 路径重排 (DRAFT)

- 评估完成后立刻调 `PathPlannerGraphClient`，复用阶段 08 的 PathPlannerAgent。
- 写入 `learning_path` 但 status = `DRAFT`，挂在评估行的 `draft_path_id` 上；
  不立即激活，不归档当前 ACTIVE 路径。
- 学生 `accept-new-path` → DRAFT 提升为 ACTIVE（同事务：归档旧 ACTIVE + 生成 quest）。
- 学生 `keep-old` → DRAFT 直接归档（status='ARCHIVED'）；evaluation 标 `KEPT_OLD`。

## 6. 画像被动更新映射

| LearningEvent | profile_revision.source | diff 内容 |
| --- | --- | --- |
| `PracticeSucceeded` | `PRACTICE_PASS` | `{kc, problemId, kind:'practice_pass'}` |
| `PracticeFailed` | `PRACTICE_FAIL` | `{kc, problemId, failureKind}` |
| `DebugResolved` | `DEBUG_FIX` | `{kc, problemId, kind:'debug_resolved'}` |
| `ResourceFeedback (LIKED/DISLIKED)` | `RESOURCE_FEEDBACK` | `{resourceId, status, kcCodes}` |
| `EvaluationCompleted` | `EVALUATION` | `{evaluationId, narrativeRef}` |

监听器位于 `com.pythontopia.learner.profile.PassiveProfileRevisionListener`，由 Spring
`@EventListener` 机制注册，不参与 mastery 计算，仅写 `profile_revision`。

## 7. 边界

- 评估窗口内 0 事件 → narrative PARTIAL + 不生成 DRAFT 路径（避免无意义重排）。
- 跨用户访问评估详情 → 403 `FORBIDDEN`。
- 班级排名只对当前学生可见，仅在 GET 详情中返回。
- `learner_evaluation.acted_status` 只能由对应学生 + 自己评估的 ID 修改。
