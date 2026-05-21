# 阶段 15 — Beta 验收、演示视频与 PPT 收尾

## 1. 时间窗口与定位

- 建议日期：2026-06-22 ~ 2026-06-30（收尾周，约 9 天）。
- 角色：1 名项目负责人主导验收与节奏；1 名视频/视觉负责人；全员配合 Bug 修复。
- 定位：**不再加新功能**。本阶段只做：Beta 验收、录制 7 分钟演示视频、完成 PPT、清理提交包、跑全量回归、修复评审演示路径上的问题。任何越界变更必须由项目负责人书面确认。

## 2. 本阶段目标

- 完成 Beta 验收 checklist（覆盖 `plan.md` 第 9 节 + `AGENTS.md` 全部硬要求）。
- 录制并交付 7 分钟演示视频（含教师视角与学生视角双线展示）。
- 完成 PPT 终稿（与 `docs/ppt_outline.md` 一致，扩展为可交付文件）。
- 准备提交包：源码、文档、镜像 tar、演示视频、PPT、运行说明、AI 披露、License。
- 跑全量回归（`mvn test`、`npm run test`、`pytest -q`、`demo_smoke.sh`）。
- 修复演示链路上的阻断性问题（非阻断问题列入"已知缺陷"清单）。

## 3. 上下游关系

- 上游：阶段 14 已就绪的部署、文档、视觉。
- 下游：比赛交付（评审日期）。

## 4. Alethicode 借用来源

- 不借用任何 Alethicode 文档或演示材料。Pythontopia 自有交付物。

## 5. 任务清单

### 5.1 Beta 验收 checklist

依据 `plan.md` 第 9 节 + `AGENTS.md` 硬要求，整理 `/home/cypress/Pythontopia/docs/beta_acceptance_checklist.md`，逐项打勾：

- 功能验收：
  - 学生注册/登录 → 进入小镇。
  - 教师创建班级 + 发布任务。
  - 学生完成画像（8 维）。
  - 学生种植 ≥ 10 个真实 KC。
  - 系统生成 ≥ 6 类资源（讲义/思维导图/练习题/代码案例/拓展阅读/动画脚本）。
  - 每个资源带课件引用或明确超纲说明。
  - Pyodide 即时运行可用。
  - 服务端判题入库。
  - 失败提交可进入 Bug 矿洞。
  - 班级看板展示真实聚合数据。
- 工程验收：
  - `frontend` TypeScript strict 通过。
  - `frontend` Vitest 通过。
  - `backend` Maven test 通过。
  - `pythontopia-graph`、`pythontopia-rag` pytest 通过。
  - Docker Compose 一键启动 OK。
  - 无未授权素材。
  - 无伪造课件引用。
  - 无纯前端假数据冒充真实业务数据。
- 教育验收：
  - KC=种子 / 资源=浇水 / 练习=除草 / Bug=病害矿洞 / 掌握=成长 映射在文档与代码中一致。
  - 班级任务真实推送到学生公告板，学生完成会反映到看板。

### 5.2 录制 7 分钟演示视频

- T15-10 视频脚本（基于 `docs/demo_script.md` 精修）：
  - 00:00 ~ 00:30：项目介绍与教育目标。
  - 00:30 ~ 01:30：小镇登场，玩家进入小镇，介绍场景。
  - 01:30 ~ 03:00：画像访谈 → KC 知识田 → 资源工坊（讲义 + 思维导图 + 练习题）。
  - 03:00 ~ 04:00：训练场 Pyodide 即时预览 + 正式提交，演示一次失败。
  - 04:00 ~ 05:00：进入 Bug 矿洞 → 看反例、变量轨迹、错误假设 → Layer 1/2 提示 → 重提通过。
  - 05:00 ~ 06:00：教师视角：班级任务发布 → 看板（KC 热力图、任务完成率、高频误区）。
  - 06:00 ~ 07:00：复盘大厅评估 → 接受新路径 → 总结教育闭环。
- T15-11 录制工具：OBS Studio（开源）；分辨率 1920×1080，30 fps，码率 6Mbps。
- T15-12 配音/字幕：使用项目成员真人配音；附中文字幕（SRT）；不使用未授权背景音乐。
- T15-13 录制前清理：使用专门的演示账号，预跑一次完整流程作热身。
- T15-14 视频成片放在 `/home/cypress/Pythontopia/docs/release/demo_v1.mp4`（不入 git，单独打包），并放说明 `docs/release/demo_v1.md` 列出剪辑时间轴。

### 5.3 PPT 终稿

- T15-20 基于 `docs/ppt_outline.md` 制作 PPT，结构：
  1. 项目背景与目标（高校 Python 初学者痛点）。
  2. 教育闭环：画像 → 路径 → 资源 → 练习 → 评估 → 复盘。
  3. 技术架构与多智能体协作。
  4. 课件 RAG 与防幻觉机制。
  5. 游戏化承载与教育映射。
  6. 班级架构与教师视角。
  7. 视频演示（30 秒精剪嵌入）。
  8. 工程质量保证：TS strict、DTO、Agent 输出结构化、grounding/safety。
  9. 已知风险与未来工作。
  10. 团队与致谢。
- T15-21 PPT 文件 `/home/cypress/Pythontopia/docs/release/Pythontopia_v1.pptx` 与 `Pythontopia_v1.pdf`（导出为 PDF 备份）。
- T15-22 PPT 内的项目截图来自演示账号，无任何真实学生 PII。

### 5.4 提交包

- T15-30 `/home/cypress/Pythontopia/docs/release/`：
  - `source/`：`git archive` 当前 main 分支为 tarball；剔除 `.env`、`node_modules`、`target/`。
  - `docs/`：阶段 14 全部文档 + 本阶段视频/PPT。
  - `images/`：可选，导出关键截图。
  - `images_tar/`：可选，`docker save` 主要镜像。
  - `README.md`：交付包导览。
- T15-31 提交包大小控制：源码 + 文档 + PPT < 200MB；镜像 tar 单独打包。

### 5.5 全量回归

- T15-40 在干净的虚拟机或 WSL 环境执行：
  - `git clone`（来自提交包）。
  - `cd backend && mvn test`。
  - `cd frontend && npm ci && npm run typecheck && npm run lint && npm run test`。
  - `cd services/pythontopia-graph && pip install -e ".[dev]" && pytest -q`。
  - `cd services/pythontopia-rag && pip install -e ".[dev]" && pytest -q`。
  - `cd deploy && docker compose up -d && bash scripts/init_rag_index.sh && bash scripts/seed_demo_classroom.sh && bash scripts/demo_smoke.sh`。
- T15-41 任何失败必须开 ticket 修复并重跑；本阶段不允许"绕过"或"标 skip"。
- T15-42 回归报告 `docs/release/regression_report.md`：记录每一项命令的退出码、耗时、备注。

### 5.6 已知缺陷与未来工作

- T15-50 整理 `docs/release/known_issues.md`：
  - 列出已知缺陷（标注是否影响演示链路）。
  - 列出未来工作（如开放世界、多 pack、移动端适配）。
- T15-51 演示前 24 小时内冻结代码；只允许文档与演示数据修改。

### 5.7 评审现场支持

- T15-60 准备演示备用计划：
  - 网络不稳定时切换到本地 Docker Compose。
  - 讯飞 API 限流时切换 Provider（`PYT_LLM_PROVIDER=openai_compat`，PPT 内注明）。
  - 评审提问预备回答：
    - "为什么不复制星露谷？"答：素材与玩法均自主重构，符合 license 与教育目标。
    - "为什么禁用 ReAct？"答：可控性与延迟，结合本课程教学场景。
    - "如何防止学生抄答案？"答：safety 节点 + 提示分层 + 服务端判题。
- T15-61 备用账号与备用密钥放在物理介质 / 离线笔记中。

## 6. 数据输入与输出

- 输入：阶段 14 全部成果。
- 输出：
  - Beta 验收 checklist（已勾全）。
  - 演示视频 + PPT。
  - 提交包。
  - 回归报告 + 已知缺陷清单。

## 7. 验收标准

### 7.1 演示验收

- 视频时长 6:30 ~ 7:30；分辨率 1920×1080；无马赛克、无背景噪音。
- 视频展示流程与 `demo_script.md` 一致。
- PPT 终稿 ≤ 20 页，覆盖所有规定章节，导出为 `.pptx` 与 `.pdf`。

### 7.2 工程验收

- 全量回归一次通过；回归报告完整。
- `demo_smoke.sh` 演示前最后一次执行通过。
- 提交包结构齐全，无敏感文件（`.env`、密钥）。

### 7.3 比赛合规验收

- 无未授权素材、无未公开 license 依赖。
- AI 披露清单完整。
- 课件引用真实存在；无伪造页码与章节名。
- 班级任务、学生提交、判题结果均为真实数据。

## 8. 验证命令

```bash
# 模拟评审机器
rm -rf /tmp/pyt-review && mkdir -p /tmp/pyt-review
cp -r /home/cypress/Pythontopia/docs/release/source/*.tar.gz /tmp/pyt-review/
cd /tmp/pyt-review && tar xzf *.tar.gz && cd Pythontopia

cd backend && mvn test
cd ../frontend && npm ci && npm run typecheck && npm run lint && npm run test
cd ../services/pythontopia-graph && pip install -e ".[dev]" && python -m pytest -q
cd ../pythontopia-rag && pip install -e ".[dev]" && python -m pytest -q

cd ../../deploy
cp .env.example .env
# 填入真实 .env
docker compose up -d
bash scripts/init_rag_index.sh
bash scripts/seed_demo_classroom.sh
bash scripts/demo_smoke.sh

# 录制视频前预跑
bash scripts/demo_smoke.sh
```

## 9. 风险与回滚

- 风险：评审日 LLM Provider 宕机；缓解：保留 `OpenAiCompatProvider` 备用 + 视频可替代演示。
- 风险：视频/PPT 包含真实学生 PII；缓解：所有演示账号为虚构姓名；演示截图人工审核。
- 风险：提交包遗漏关键文件；缓解：`release/README.md` 包含 checksum 与必备清单；交付前 2 人复核。
- 回滚：本阶段无业务代码变更；任何修复必须经"演示链路冒烟"再发布。

## 10. 不做事项

- 不再加新功能（任何新功能必须延后到 Beta 后版本）。
- 不在演示中展示未实现功能（避免误导评委）。
- 不为了视觉效果伪造数据。
- 不在 PPT/视频中使用未授权素材或音乐。
- 不在演示中暴露 API key、JWT secret。
- 不让 demo_smoke 自动复用上一次的 LLM 响应当成"新生成"。

