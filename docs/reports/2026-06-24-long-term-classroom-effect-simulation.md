# 2026-06-24 长期班级学习效果仿真报告

## 目标

模拟真实班级长期学习效果观测：继续看 KC 掌握度、复习完成率、资源使用率，并验证 2026-06-23/24 的 Evidence Memory / 图谱证据 / 推荐闭环方向是否在长期指标上保持正向。

## 仿真口径

- 班级：1 个教师班级。
- 学生：baseline 12 人，treatment 12 人。
- 周期：6 周。
- KC：从 pack 43 取前 5 个 KC。
- 写入真实表：
  - `classrooms`
  - `classroom_members`
  - `kc_mastery`
  - `kc_mastery_event`
  - `kc_review_state`
  - `learning_resource`
  - `learning_event_log`
- 聚合方式：测试只从 SQL 重新聚合，不使用仿真内存计数作为结果。

## 结果

| 指标 | Baseline | Treatment | Uplift |
|---|---:|---:|---:|
| 平均 KC 掌握度 | 0.280 | 0.482 | +0.202 |
| 复习完成率 | 0.333 | 0.800 | +0.467 |
| 资源使用率 | 0.625 | 1.000 | +0.375 |

原始日志量：

- `kc_mastery_event`: 720
- review logs (`review_scheduled` + `review_completed`): 1128
- `resource_consumed`: 234

## 门槛

测试要求 treatment 必须：

- 平均 KC 掌握度至少高 `+0.12`
- 复习完成率至少高 `+0.25`
- 资源使用率至少高 `+0.20`
- 三项指标全部胜过 baseline
- 原始日志必须存在并达到固定数量，避免只验证内存计算

## 验证

```bash
cd backend
mvn -s ~/.m2/settings.xml -q -Dtest=LongTermClassroomEffectSimulationTest test
# exit 0
```

测试文件：

- `backend/src/test/java/com/pythontopia/learner/effect/LongTermClassroomEffectSimulationTest.java`
- `backend/src/test/java/com/pythontopia/learner/effect/ClassroomLearningEffectSimulator.java`

## 结论

在确定性离线仿真中，treatment 组在三类长期指标上都稳定优于 baseline。结果支持当前方向继续推进：用 Evidence Memory / 图谱证据 / 推荐重排提升资源消费和复习执行，再通过真实掌握度事件观察长期 KC 增长。

## 边界

- 这不是线上真实学生 A/B。
- 这不是浏览器全流程点击验收。
- 后续真实班级日志应继续使用同样三项主指标，并补充留存率、错题复发率、教师干预次数等指标。
