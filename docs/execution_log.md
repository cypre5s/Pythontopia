# Pythontopia 执行日志

> 本文档实时记录每个 working session 的状态，作为跨会话的"工作记忆"。
> 一定要常翻 `AGENTS.md` 与 `/home/cypress/Pythontopia/plan/` 下的阶段计划，避免方向偏离。

## 0. 用户立场（不变约束）

1. 严格遵守 `/home/cypress/Pythontopia/AGENTS.md`。
2. 完成 `plan/` 目录下全部阶段计划，每阶段必须满足"验收标准"。
3. 完成后做 100 轮代码质量审查与性能优化（小步迭代，不擅自扩功能；目标：让项目更像"成熟教学游戏作品"）。
4. 每个阶段 + 整体优化都要有单测 + 联合测试。
5. LLM 现阶段复用 `/home/cypress/Alethicode/backend/.env`，阶段 11 已接入讯飞 Spark Provider，演示前切换。
6. 前端按星露谷"像素小镇"风格，但素材必须开源/CC0，禁止官方资产。
7. 可以使用 subagent 提升并发；2 分钟级进度推送。
8. 维护本文档，同步 `CHANGELOG.md`（中文、简洁）。

## 1. 阶段进度快照

| 阶段 | 名称 | 状态 |
| ---- | ---- | ---- |
| 00 | 仓库骨架与基础设施 | ✅ 已完成 |
| 01 | 课程包导出与种子数据落库 | ✅ 已完成 |
| 02 | 账号、班级与小镇基础后端 | ✅ 已完成 |
| 03 | 前端骨架与 Phaser 小镇 MVP | ✅ 已完成 |
| 04 | 画像屋与 ProfileAgent | ✅ 已完成（含验证补完） |
| 05 | 知识田与 KC 作物系统 | ✅ 已完成 |
| 06 | RAG 服务课件证据检索 | ✅ 已完成 |
| 07 | 资源工坊与六类资源生成 | ✅ 已完成 |
| 08 | 学习路径、每日任务与公告板 | ✅ 已完成 |
| 09 | 训练场、Pyodide 与服务端判题 | ✅ 已完成 |
| 10 | Bug 矿洞与 DebugAgent | ✅ 已完成 |
| 11 | 讯飞 Provider 接入 | ✅ 已完成 |
| 12 | 班级架构与教师看板 | ✅ 已完成 |
| 13 | 学习效果评估与复盘大厅 | ⏳ 待做 |
| 14 | Docker Compose 部署、视觉打磨与文档 | ⏳ 待做 |
| 15 | Beta 验收、视频与 PPT 收尾 | ⏳ 待做 |

## 2. 当前 working set

- 当前焦点：**阶段 13 — 学习效果评估、路径重排与复盘大厅**
- 阶段 12 已完成 ✅ → 见 CHANGELOG。
- 阶段 13 任务列表：
  - [ ] T13-01 V090 `learner_evaluation` + `learner_evaluation_metric`
  - [ ] T13-02 V091 `profile_revision` 加 `evaluation_id`
  - [ ] T13-11 V092 `learning_path.status` 接受 `DRAFT`
  - [ ] T13-10 `LearnerEvaluationService` 5+ 指标 + accept/keep
  - [ ] T13-20 `EvaluationNarrativeAgent`（仅写文字、不改数字）
  - [ ] T13-30 `ProfileService.applyPassiveRevision`（5 类 source）
  - [ ] T13-40 `ReviewHallPanel.vue` + 路由 + store + api

## 3. 接下来要做（按顺序）

1. 阶段 12 全部落地 + 验证（mvn test / npm run test）。
2. 阶段 13 评估 + 复盘大厅 + 画像被动更新。
3. 阶段 14 部署 + 视觉打磨 + 文档。
4. 阶段 15 Beta 验收 + 演示脚本 + 提交包 + 回归报告。
5. 100 轮小步迭代：教学游戏成熟度方向（视觉、节奏、文案、引导、错误处理、可访问性、文档完整度），不擅自加功能模块。

## 4. 决策与风险

- 不使用 ReAct，按确定式 + 结构化输出；多智能体输出全部 pydantic。
- 像素素材只用 OpenGameArt CC0 与 itch.io 标注开源的素材，严禁星露谷官方资产；下载到 `assets/pixel/` 并维护 `LICENSE.md`。
- LLM Provider 优先 `openai_compat`（来自 Alethicode .env），演示前 `export PYT_LLM_PROVIDER=spark`。
- 班级任务发布事务化：同一事务内写 `classroom_quest` + 批量 `quest`；幂等键 `(classroom_quest_id, user_id)`；用唯一约束兜底。
- 看板查询 ≤ 1 秒；班级规模 ≤ 50 不引入异步。

## 5. 历史完成（与 CHANGELOG.md 对齐）

详见 `/home/cypress/Pythontopia/CHANGELOG.md`，每阶段完成后追加"完成 / 验证命令 / 验证结果"三段。
