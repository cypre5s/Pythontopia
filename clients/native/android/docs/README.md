# Android 文档导览

| 文档 | 用途 |
| --- | --- |
| `../README.md` | Android 客户端定位 + **实测现状**（22 模块 / 16 feature / debug APK）+ 边界 |
| `ADR-001-tech-selection.md` | 关键技术选型 ADR；**§8 附录（2026-06-05）记录实锁版本 vs 决策表** |
| `roadmap.md` | 阶段计划（2026-06-05 已按真实代码重写：什么已落地 / 进行中 / 待办） |
| `04-backend-contract-drift.md` | **后端契约漂移审计**：虚构的 inventory/currency、refresh 语义、存档槽位范围 |
| `system_design.md` | 模块架构 + 关键时序图（待） |
| `ui_design.md` | 暗色像素主题 + 字体规范 + 关键页 UI（待） |
| `api_contracts.md` | Spring Boot REST API → Kotlin data class 映射（待；目标改由 OpenAPI 生成） |
| `testing_strategy.md` | 测试金字塔与质量门禁（待） |

## 调研子代理输出位置

`.work/thinking/android_research_{NN}.md`（被 gitignore，不入主 commit；汇总后转写入 `ADR-001-tech-selection.md`）

## 编号约定

- Android 端的迭代轮编号为 `Android-RN`（Android-R0 = 启动 baseline，Android-R1 = 骨架落地，...）
- 与 Web 端 R-100 编号空间完全独立。
- 与 `assets/inbox/MANIFEST.md` 中的素材轮编号（如 `R-100 #3`）独立。
