# 已知缺陷与未来工作（Beta v1）

> 阶段 15 起，所有未修复缺陷必须列入本文件并标注影响范围。

> **R-14 对齐（2026-06-08 · 世界收缩方案 A）**：本文件部分条目写于 R-12「5 图 + 20 NPC」星露谷世界；R-14 已收敛为**单一中央小镇 `town_hub`**（+ `mine_south` Bug 矿洞支线），移除农场 / 作物 / 货币 / 商店。**磁盘现实：2 张地图（+ index.json，共 3 个 JSON）/ 12 NPC**。

## 0. R-14 世界收缩遗留（2026-06-08）

- **过时烟雾断言（已修复）**：`scripts/r12-smoke-test.sh` 原断言 `maps=6` 与 `npcs=20`，在 R-14 单 hub 现实（2 图 / 12 NPC）下会失败。**Phase 0 已修正**为 `maps=3`（含 `index.json`）/ `npcs=12` / `migrations>=36`。
- **`town_hub` 占位 building 已移除点击路由（已修复）**：原 5 个指向不存在路由的占位 building（纺织展馆×2 + 南面民居×3，路由 `/house-*`）。**Phase 0 已移除**其 building 点击对象，视觉房屋瓦片保留以维持小镇氛围（仍参与碰撞）。
- **`town_hub` 出口契约已修正（R-14 已修复）**：`index.json` 的 `town_hub.exits` 由 `["mine_south"]`（与地图不符）改为 `[]`，与「`town_hub.json` 无 `exit` 对象」一致。
- **`mine_south`（WorldScene 地图）步行可达性**（建议后续评估）：`town_hub` 无 walk-exit 通向 `mine_south`，`FAST_TRAVEL_ROUTES` 亦仅剩 `town_hub`；Bug 矿洞主要经 building → `/bug-mine`（`BugMinePage`）进入。WorldScene 版 `mine_south` 地图当前在主流程中基本不可步行抵达，属既有设计现状。

## 1. 演示阻断（必须在发布前修复）

目前无演示阻断项。

## 2. 演示非阻断（可在 Beta 后版本修复）

- **Pyodide 首加载耗时**（~6~8s）：首次进入 `/training` 时需要等待，演示前预跑一次浏览器缓存即可。
- **mermaid 渲染容错**：复杂 mindmap 在某些边缘语法下渲染失败，前端回退到纯文本占位（已实现）。
- **班级排名匿名编号**：仅当前学生可见，多班级场景下未做并表展示，演示 1 班级足够。
- **LLM 限流与预算**：当前主链路使用 DeepSeek；账号级每日请求数限制不等于 token 或费用硬上限。真实文本和图片调用已完成本地验证，密钥若曾出现在对话或提交内容中应轮换。
- **远征 Golden 题库覆盖**：Graph 不可用时，只能用与当前 KC 精确匹配的课程题兜底；例如 `PY43-KC-001` 当前无匹配编程题时会按设计返回 503，避免错记到其他 KC。需补齐题库覆盖后才能保证该 KC 在 Graph 故障时继续远征。

## 3. 教育验证欠缺

- 班级聚合的同类对照（同一 KC 不同班级表现对比）未实现，阶段 15 不做。
- 教师辅导提醒（学生掌握度低于阈值时主动推送）未实现，阶段 15 不做。

## 4. 工程债

- 单 worker 模式：演示与生产默认单 worker，避免进程内状态错乱；如需横向扩展需先迁移会话状态到 Redis（未做）。
- 静态资源体积：Phaser + Vue + axios + marked + mermaid 合计约 1.8MB（gzip 后约 600KB），暂未做 tree-shaking 进一步优化。
- 后端 Maven settings.xml 写死 Maven Central；如需国内镜像，本机 `~/.m2/settings.xml` 覆盖。

## 5. 未来工作（Beta 后路线）

1. **多 pack 支持**：解耦 `pack_id=43` 硬编码，新增课程包发现机制。
2. **移动端适配**：现有像素小镇仅做桌面端验证。
3. **真实长视频生成**：StoryboardAgent 当前仅出脚本，未对接视频合成。
4. **教师诊断报告**：基于评估周期的纵向学情对比，给出干预建议。
5. **R-10 优化迭代**：10 轮代码质量审查与性能优化（在阶段 15 后立即启动）。
6. **R-10 之后**：本地 Beta 后再决定是否引入 monitoring/APM；当前 Beta 不需要。

## 6. 修复 SLA

- 演示阻断项：T+24 小时内修复并重跑 `demo_smoke.sh`。
- 演示非阻断项：T+7 天内排期；超期升级为阻断项。
- 工程债：进入 Backlog，每月 review 一次。
