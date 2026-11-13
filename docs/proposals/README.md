# 提案与评估索引

这里放**尚未确认**的方案、评估和基准清单。它们不是需求，也不代表已实施：确认之后，内容应并入对应的当前规范（requirements/product/architecture/content-authoring/design/development），过期提案直接删除，不要留成平行计划。

| 日期 | 文档 | 状态 | 结论/下一步 |
| --- | --- | --- | --- |
| 2026-11-13 | [问题清单与修复权衡](2026-11-13-问题清单与修复权衡.md) | 前 6 条已按此实施（未提交），7/8 暂不动 | 见文末处理顺序表 |
| 2026-11-13 | [PPT 讲解接入方案](2026-11-13-PPT讲解接入方案.md) | 未实施 | 需用户拍板第 7 节 5 个问题；先做 MVP（文本讲解 + 审校 + 阅读） |
| 2026-11-13 | [ZONOS3 语音接入方案](2026-11-13-ZONOS3语音接入方案.md) | 未实施 | 倾向自托管（48 核 / 可用 28 GB），但仍按"作者期生成 + 审校 + 学生只播放"做 |
| 2026-11-13 | [ZONOS3 B0 基准清单](2026-11-13-ZONOS3-B0基准清单.md) | 脚本已写、未执行 | 在 master 上跑：拉模型 → 构建镜像 → `deploy/tts/bench.sh` 量 RTF/内存/听感 |

## 与实现的对应关系

| 提案 | 相关代码/脚本 | 备注 |
| --- | --- | --- |
| 问题清单第 1–6 条 | `frontend/e2e/worldConfiguration.spec.ts`、`backend/app/course_exchange.py`、`deploy/compose.yml`、`deploy/.env.example`、`scripts/check-docs.mjs`、`.github/workflows/reborn.yml` | 改动待提交 |
| ZONOS3 语音 | `deploy/tts/Dockerfile`、`deploy/tts/fetch-models.sh`、`deploy/tts/bench.sh`、`deploy/compose.tts.yml` | 全部未运行；`tts` 服务在 `tts` profile 下，不发布端口 |

## 写作要求

- 提案要写清**事实来源**（哪些核实过、哪些没有）和**为什么不走别人的流程**；
- 有成本的方案必须给量级估算，不要只写"便宜/贵"；
- 涉及 AI 或外部服务的方案，必须说明缺凭据时的降级行为；
- 方案被采纳或放弃时，更新本索引的状态列，不要静默留着。
