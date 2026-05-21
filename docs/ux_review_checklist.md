# Pythontopia UX 走查 Checklist（阶段 14）

> 学生端 + 教师端各 1 轮 review，覆盖 9 大面板。每项确认后在 `状态` 列勾选 `OK / NEED_FIX / N/A`。
> 验收前需要全部为 `OK`。`NEED_FIX` 必须挂 issue。

## 走查矩阵

| # | 面板 / 页面 | 路由 | 关键检查项 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | 登录 / 注册 | `/login` `/register` | 表单可见区域 < 480px 时仍可滚动；错误 toast 颜色一致 | OK |
| 2 | 小镇主城 | `/town` | 30x20 像素瓦片地图清晰；NPC 与玩家不重叠；9 个建筑可识别（画像屋 / 知识田 / 资源工坊 / 训练场 / Bug 矿洞 / 公告板 / 复盘大厅 / 教师屋 / 图书馆） | OK |
| 3 | 画像屋面板 | `/profile-room` | 8 维进度条 + 当前问题 + 安全/grounding 卡片；done 状态文案不空 | OK |
| 4 | 知识田 | `/field` | 65 KC 全部可渲染；未解锁有锁标识；mastery bar 颜色与 token 一致 | OK |
| 5 | 资源工坊 | `/workshop` | 6 类资源展示 + evidence 标签；练习题默认折叠提示；mermaid 错误占位不空白 | OK |
| 6 | 训练场 | `/training` | Pyodide loading state；正式提交按钮在未登录 / 限频时禁用 | OK |
| 7 | Bug 矿洞 | `/bug-mine` | Layer 1 默认展开，Layer 2/3 锁；重试 success 后看到 ✓ | OK |
| 8 | 公告板 | `/board` | 4 个 tab；今日任务/路径/班级任务/历史路径切换无内容闪烁 | OK |
| 9 | 复盘大厅 | `/review-hall` | 6 个 metric 卡片；narrative 折叠 / 显示；accept / keep 按钮在 ACTIVE 状态隐藏 | OK |
| 10 | 教师班级详情 | `/teacher/classroom/:id` | 6 tabs：概览/任务/KC 热力图/高频误区/资源使用/成员；KC 热力图 4 档颜色清晰 | OK |
| 11 | 教师首页 | `/teacher` | 班级列表 + 创建表单；创建后 toast 提示 | OK |

## 通用项

- 颜色 token：所有按钮 / 卡片背景遵循 `frontend/src/styles/tokens.css`，无硬编码 #FFFFFF 等非 token 值（除非在 Phaser canvas 内）。
- 字体：中文使用霞鹜文楷或同级开源字体（fallback 到 PingFang SC）；代码字体使用 JetBrains Mono。
- 响应式：1280×720、1920×1080 两档下无错位。
- 文案：所有中文文案过校对，无错别字。
- 错误处理：错误 toast 与正常 toast 视觉区分；403/404 不直接抛 JSON。
- 像素素材：assets/pixel/LICENSE.md 列全所有素材来源与 license。
- 控制台：生产构建（`npm run build`）剥离 `console.log`（由 Vite/Terser 默认完成）。

## 已知风险（演示前需评估）

- 1280×720 在工坊面板下，6 类资源右侧抽屉可能需要横向滚动。
- Pyodide 首次加载约 6~8s，需保留 loading 文案 + 提示"演示前先开页面热身"。
- mermaid 渲染失败时显示纯文本占位（已实现），评估时主动避免演示 mindmap。

## 走查流程

1. 学生视角：`demo-stu-01` 登录跑完整闭环：画像 → 田 → 工坊 → 训练 → Bug 矿洞 → 公告板 → 复盘。
2. 教师视角：`demo-teacher-01` 登录，进入班级详情，浏览 6 tabs。
3. 走查者把所有 `NEED_FIX` 项录入 `docs/release/known_issues.md`。
