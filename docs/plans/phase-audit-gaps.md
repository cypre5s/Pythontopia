# Infinity 蓝图逐项审计与缺口

> 对照: `infinity-living-world.md` §验收清单 | 更新: 2026-06-09
> ✅=达标 ⏳=部分 ❌=缺失

## P0-P1 ✅ (8/8)
所有地基止血+真协同任务已达标。

## Phase 2 · 数字孪生 (85%)
| Task | 验收 | 状态 |
|------|------|------|
| 2.1 | MemoryScorer 0.4/0.3/0.3 + GET /api/learner/memory | ✅ |
| 2.2 | ReflectionTrigger + graph/规则兜底 + profile_revision | ✅ |
| 2.3 | memory_highlights→directive + pytest | ✅ |
| 2.4 | GET /api/learner/twin/weekly (后端) | ✅ |
| 2.4 | TwinWeekly UI 接入学生可见页面 | ❌ PR1 |

## Phase 3 · 自适应引擎 (30%)
| Task | 验收 | 状态 | PR |
|------|------|------|-----|
| 3.1 | MasteryProperties.difficultyMultiplier (静态) | ✅ | — |
| 3.1 | V138 migration (V137已被agent_strategy占) | ❌ | PR2 |
| 3.1 | 判题主链调applyEventWithDifficulty | ❌ | PR2 |
| 3.1 | BktMasteryEvaluatorTest (连续失败confidence↓) | ❌ | PR2 |
| 3.2 | ResourceProduceRequestDto.target_difficulty (Java) | ❌ | PR3 |
| 3.2 | WorkshopService mastery→difficulty计算 | ❌ | PR3 |
| 3.2 | BossService difficulty下发 | ❌ | PR3 |
| 3.2 | test_practice_difficulty_adaptive | ❌ | PR3 |
| 3.3 | 推荐读洞察+记忆topK | ❌ | PR4 |
| 3.3 | RecommendationDto溯源字段 | ❌ | PR4 |
| 3.3 | RecommendationServiceTest | ❌ | PR4 |
| 3.4 | semantic_grounding接LLM entailment | ❌ | PR5 |
| 3.4 | 接入resource_graph critique链 | ❌ | PR5 |
| 3.4 | 歪曲语义→revise端到端测试 | ❌ | PR5 |

## Phase 4-8 (0-25%)
| Phase | 关键缺口 | PR |
|-------|----------|-----|
| P4 | resource_feedback表, @Scheduled, Graph读策略 | PR6 |
| P5 | OracleService读confidence/DAG, OracleController, 预防包 | PR7 |
| P6 | RemediationService, 教师按钮, 学生端推送 | PR8 |
| P7 | decision_trace, GlassBox组件 | PR9 |
| P8 | StoryboardScene, TTS降级 | PR10 |

## 迁移编号
V136=learner_insight, V137=agent_strategy, V138+=可用
