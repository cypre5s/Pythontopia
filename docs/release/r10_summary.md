# R-10 收官总结

> 阶段 15 后启动的 10 轮代码质量审查与性能优化迭代。目标：让 Pythontopia 看起来更像一个**成熟的
> 教学游戏作品**，而不是单纯的"功能演示"。

## 1. 总览

- 共 10 轮小步迭代，全部完成并通过验证。
- 不加大功能模块，不引入新业务表/接口（严格遵守 `AGENTS.md` 与 `plan/阶段15` 限制）。
- 全量测试 **234 / 234 通过**：backend 101、graph 59、rag 18、frontend 56。
- 文档：`docs/release/r10_iterations.md` 每轮明细 + 本文件总结。

## 2. 教学游戏作品的成熟度提升点

| 维度 | 改动 | 价值 |
| --- | --- | --- |
| Onboarding 引导 | 复盘大厅、公告板、资源工坊顶部 hint 全部改成"教育闭环位置 + 触发建议"文案 | 学生第一次进入面板能立刻理解所处阶段 |
| 性能 | router 全量懒加载（除 Login/Town） | 初始首屏 chunk 缩减，演示机器加载更顺滑 |
| 体验一致性 | 像素风 token 渗透到复盘大厅 + 公告板，颜色/字体统一 | 视觉风格一致，演示更专业 |
| 反馈即时性 | 错误码统一、toast 文案友好、按钮禁用状态稳定 | 演示时不会暴露 JSON 错误 |
| 防幻觉硬约束 | grounding bug 修复（NUMBER 与 KC pattern 双计） | narrative 不会因为 `0.10` 与 `0.1` 不一致或 KC code 数字误判而 fail，又保留对越界数字的严格拦截 |
| 测试覆盖 | 评估 store 4 用例、planDraft fail-fast 1 用例 | 关键 action 与边界全部有断言保护 |
| 部署与运维 | docker-compose 6 服务编排 + 5 个脚本 + tokens.css + UX checklist | 一键启动 + 一键演示数据，演示流程可复盘 |

## 3. R-10 边界（未做）

- 不在 R-10 内引入新功能、新表、新 API。
- 不在 R-10 内做大型重构或包路径调整。
- 不修改 `git config`、不 push 远端、不 force push。
- 视频与 PPT 物件需要真人录制/制作（已写脚本与剪辑指南）。

## 4. 一次性验证命令

```bash
cd /home/cypress/Pythontopia/backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml test
cd ../frontend && npm run typecheck && npm run lint && npm run test
cd ../services/pythontopia-graph && .venv/bin/python -m pytest -q
cd ../pythontopia-rag && .venv/bin/python -m pytest -q
cd ../../deploy && docker compose up -d --build && bash scripts/demo_smoke.sh
```

## 5. 风险与跟进

- LLM Provider：演示前必须切到 `PYT_LLM_PROVIDER=spark`，并预留 `openai_compat` 备用。
- Pyodide 首次加载约 6~8s，演示前先热身。
- 文档与脚本依赖的镜像源在本机生效时可能需要 `docker tag` 回退（详见 `deploy/README.md`）。
- Beta 提交包内的视频 / PPT / 截图必须由真人完成（R-10 不做）。

## 6. 致谢

R-10 严格遵守 `AGENTS.md` 核心原则：先思考、再编码；简单优先；外科手术式修改；教育优先、游戏承载。
团队成员对所有 AI 协助生成代码与文档进行了人工审阅，并对最终质量负责。
