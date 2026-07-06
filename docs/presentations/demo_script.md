# Pythontopia 演示脚本（7 分钟）

> 与 `plan/阶段15-Beta验收视频与PPT收尾.md` 一致，覆盖学生端 + 教师端双线。每段时间含演示者旁白要点。

## 0. 演示前准备

- 拉镜像并启动 `docker compose up -d --build`，确认 6 service healthy。
- `bash scripts/init_rag_index.sh` 完成课件索引重建。
- `bash scripts/seed_demo_classroom.sh` 创建 1 教师 + 3 学生 + 1 班级任务，记录一次性密码。
- `bash scripts/demo_smoke.sh` 通过。
- 浏览器分别开两个窗口：左侧 `demo-stu-01`，右侧 `demo-teacher-01`。

## 1. 项目介绍（00:00 – 00:30）

旁白：

> Pythontopia 是一座面向高校 Python 初学者的网页版像素学习小镇。
> 它把课程包、画像、路径、资源、练习、Bug 调试、班级管理统一到一个游戏化闭环中。
> 演示分两条线：学生进入小镇完成一次学习闭环；教师在班级看板查看真实聚合数据。

操作：演示者打开 PPT 第 1~2 页（项目目标 + 教育闭环）。

## 2. 进入小镇（00:30 – 01:30）

操作：

1. 学生窗口登录 `demo-stu-01`。
2. 自动跳到 `/town`，鼠标拖动玩家，简要介绍 8 个建筑。
3. 提到天气、日夜、玩家位置每 5 秒同步。

旁白：

> 小镇是 30×20 的瓦片地图，9 个建筑分别对应画像屋、知识田、资源工坊、训练场、Bug 矿洞、公告板、复盘大厅、教师屋、图书馆。
> 玩家位置真实持久化到 `player_state` 表。

## 3. 画像 → 知识田 → 资源工坊（01:30 – 03:00）

操作：

1. 进入画像屋，发 3~4 轮对话（ProfileAgent 真实写入）。
2. 进入知识田，点击 bootstrap，65 个 KC 落库。
3. 进入资源工坊，选 KC `PY43-KC-001` 生成讲义 + 思维导图 + 练习题。

旁白：

> 画像是 8 维结构化输出；知识田 65 个 KC 一次写入 `crop_tile_state`；资源工坊调用 6 类 agent，
> 每个生成结果都带课件页码引用或显式"超纲"标记，由 GroundingCritic 与 SafetyAgent 严格审查。

## 4. 训练场 → Bug 矿洞（03:00 – 04:30）

操作：

1. 进入 `/training`，选 problem 476（圆面积）。
2. 写一段错误代码（仅 print 输入）。
3. Pyodide 预览失败，提交服务端，verdict=WA。
4. 点击 "进入 Bug 矿洞"，自动落 6 卡片。
5. 展开 Layer 1 提示，重试一段正确代码 → AC。

旁白：

> Pyodide 即时预览只是开发体验，真正判题在服务端的隔离 sandbox。
> 失败提交进入 Bug 矿洞，DebugAgent 给出反例、变量轨迹、错误假设和 3 层提示。
> 重试通过会触发 `DEBUG_FIX` 学习事件，画像也同步被动更新。

## 5. 教师视角（04:30 – 05:30）

操作：

1. 切换教师窗口 `demo-teacher-01`。
2. 进入 `/teacher/classroom/:id`，浏览 6 tabs。
3. 重点展示 KC 热力图、高频误区、资源使用。

旁白：

> 班级看板真实聚合 5 类数据；KC 热力图 4 档颜色对应班级整体掌握度，便于教师介入辅导。

## 6. 复盘大厅（05:30 – 06:30）

操作：

1. 回到学生窗口，进入 `/review-hall`。
2. 点击 "立即评估"，6 张 metric 卡片刷新；narrative 摘要显示带数字与 KC。
3. 展开 PathDiff，比较旧 ACTIVE 与新 DRAFT 路径。
4. 点击 "接受新路径"，状态变为 ACCEPTED，公告板今日任务自动更新。

旁白：

> EvaluationNarrativeAgent 写的所有数字与 KC 都来自后端真实数据，越界直接 fail；
> 学生掌控是否接受新路径，避免 AI 单方面改方向。

## 7. 总结（06:30 – 07:00）

操作：演示者打开 PPT "工程质量" 页面，强调：

- 全链路 100/59/18/52 测试通过。
- 不复制官方游戏素材，不伪造课件引用。
- 班级数据真实落库，所有 agent 输出结构化。

旁白：

> Pythontopia Beta 已经把"教育闭环 → 游戏化承载 → 真实班级"全部跑通；后续工作包括多 pack、长视频、
> 移动端与更深的学情诊断。

## 演示风险与备用计划

- 若讯飞 Spark API 限流：切换 `PYT_LLM_PROVIDER=openai_compat` 并在 PPT 注明。
- 若 Pyodide CDN 加载慢：演示前预跑一次让浏览器缓存。
- 若 narrative grounding 失败：评估接口会 422，演示者切换到上一次评估的详情。

## 演示后清理

```bash
bash scripts/teardown_demo.sh
```

清除 `demo-*` 账号、班级与任务。
