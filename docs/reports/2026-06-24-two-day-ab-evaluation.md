# 2026-06-23/24 优化 A/B 评测报告

## 目标

对 2026-06-23 与 2026-06-24 的 AI / RAG / Evidence Graph / Evidence Memory / Agent Runtime / 自动优化改动做确定性 A/B 验证。实验组必须稳定优于基线；如果只靠少数样本拉高均值，则拒绝晋级。

## A/B 矩阵

| 优化项 | Baseline | Treatment | 指标与门槛 | 当前结果 | 覆盖文件 |
|---|---|---|---|---|---|
| KC 图谱检索扩展 | strict KC / graph off | graph force + 关系扩展 | 3 个场景胜率 100%，mean recall uplift >= 0.45 | prerequisite / co_occur / common_mistake 均 0.5 -> 1.0，win_rate 1.0，mean uplift +0.50 | `services/pythontopia-rag/tests/test_recent_optimization_ab.py` |
| Learning Evidence Graph | resource-only loop | resource + check + mastery + page + event + run graph | completeness +0.40，connected rate +0.30，grounded pages +2，check/mastery coverage +0.80 | 定向 Testcontainers 通过 | `backend/src/test/java/com/pythontopia/learner/evidence/LearningEvidenceGraphServiceTest.java` |
| TutorBench-lite 轨迹评测 | naive summary | graph-backed summary | pass rate uplift >= 0.50 | 定向 JUnit 通过 | `backend/src/test/java/com/pythontopia/tutorbench/TutorBenchEvaluatorTest.java` |
| Evidence Memory 2.0 | recent-event only memory | graph-backed selective memory | graph memory +0.35，retained evidence +2，signal/direct/propagated count +1 | 定向 Testcontainers 通过 | `backend/src/test/java/com/pythontopia/learner/evidence/EvidenceMemoryServiceTest.java` |
| 推荐重排 | mastery ascending rank | evidence-memory signal rerank | 目标 KC rank uplift >= 2 | baseline 第 3 位，treatment 第 1 位 | `backend/src/test/java/com/pythontopia/learner/recommendation/RecommendationServiceTest.java` |
| AG-UI runtime stream | legacy `trace/token/result/error` | canonical AG-UI lifecycle/step/text/state/tool events | 多场景 score uplift >= 0.30 | 资源生成 +0.77，工具循环 +0.585，错误终止 +0.495 | `services/pythontopia-graph/tests/test_sse_agui.py` |
| Eval-gated 自动优化 | mean-uplift gate only | mean + regression + severe regression + sample win-rate gate | unstable candidate with sample_win_rate 0.40 < 0.80 must reject | mean uplift +0.166 但被拒绝，reason=`sample_win_rate` | `services/pythontopia-graph/tests/test_eval_optimization.py` |
| Protocol descriptor | minimal tool/event surface | MCP resources/prompts + OTel GenAI attributes + evidence graph tool | descriptor includes new resources/prompts/tools/attrs | 定向 JUnit 通过 | `backend/src/test/java/com/pythontopia/protocol/ProtocolGatewayServiceTest.java` |

## 稳定性改进

本轮发现并修补了一个真实缺口：原自动优化器能拒绝高回归候选，但没有“样本胜率”门槛。一个候选可以只在少数样本大幅提升、其余样本几乎无收益，却凭平均分晋级。

改进后：

- `OptimizationPolicy.min_sample_win_rate` 默认 0.55。
- 严格 A/B 可设置为 0.80。
- `CandidateOptimizationSummary.sample_win_rate` 进入决策摘要。
- 选中 artifact 元数据记录 `sample_win_rate`。

## 验证命令

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_sse_agui.py tests/test_eval_optimization.py tests/test_orchestrator_route.py tests/test_tool_loop_route.py
# 32 passed
```

```bash
cd services/pythontopia-rag
python3 -m pytest -q tests/test_recent_optimization_ab.py tests/test_fusion_kc_graph.py tests/test_query_graph_mode.py tests/test_runtime_knowledge_graph.py tests/test_retrieval_golden.py
# 38 passed
```

```bash
cd backend
mvn -s ~/.m2/settings.xml -q -Dtest=LearningEvidenceGraphServiceTest,EvidenceMemoryServiceTest,RecommendationServiceTest,TutorBenchEvaluatorTest,ProtocolGatewayServiceTest test
# Surefire XML: 13 tests, 0 failures, 0 errors
```

## 未覆盖

- 没有跑真实浏览器全栈点击 e2e。
- 没有真实学生日志在线 A/B；当前是确定性离线 A/B 与集成测试门禁。
- 长期学习效果仍需在真实班级/演示日志中按 KC 掌握度变化、复习完成率、资源使用率继续跟踪。
