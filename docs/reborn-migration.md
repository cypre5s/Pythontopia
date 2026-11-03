# Reborn migration

- Branch: `reborn`.
- Source `origin/main`: `783f15d8d08fd739fea3f665ec83d819b5eebb51`.
- Original checkout: `E:\AI\Pythontopia`; its existing changes remain untouched. It is detached at the source commit to release the branch for this managed worktree.
- No production service, database, object store, volume, repository setting, or main branch is in scope.

## Retention decisions

| Capability | Source | Reborn decision | Verification |
| --- | --- | --- | --- |
| Pixel world | frontend/public/assets/maps/town_hub.json, SRW assets | reuse map and legal art; replace retired route metadata | asset audit, pathfinding, browser |
| Courses | data/course-pack-43/json | retain original source IDs, seed versioned private materials | 7 documents, 561 pages, 7 chapters, 65 KC, 46 examples, 39 original problems |
| Media | old document paths/video manifests | source media absent; extracted text remains accurately labeled | upload authorized test media to verify viewers |
| Auth/classes/learning/quests | Java backend | replace with FastAPI modular monolith, separate database | permission, persistence, idempotency and graph tests |
| AI | old graph services | replace with one configured teaching assistant using create_agent | isolated provider tests; real invocation only with credentials |
| Code execution | old runner | Judge0 adapter only | contract tests; live Judge0 validation separately recorded |
| Student UI | old route pages | world plus a single replaceable workspace | frontend tests and browser |
| Native clients/research/old planners | clients, research, services, old docs/scripts | delete from reborn | tracked-file and import audit |
| Licenses/sources | LICENSE, LICENSE-ART.md, assets and legal notices | retain attribution and source obligations | asset dependency checks |

## Content audit

Chapter 179, document 186: 计算工具与计算思维. New concept questions are authored from real pages 10715, 10723 and 10724 and labeled as such. Chapter 180, document 187: Python 语言基础(2); pages 10731–10736 cover language history and page 10750 Python characteristics. Optional coding practice 476 uses page 10771 plus print/input pages 10786–10787. Original question 477 has contradictory expected output and must remain unpublished pending review.

No original PDF/PPT/PPTX/MP4/WebM was found. Old absolute Linux media paths are not usable resources. The 34-item video manifest is metadata, not evidence of playable video.

## Progress / resume

Implementation and local/independent-container validation are complete for the available environment. External Judge0/real-model/S3 limits remain explicitly documented below and in acceptance.md. Continue from actual code and this file; do not automatically merge main.

## 当前实现与续做（2026-11-03）

A–F的仓库隔离、Python单体、单世界/唯一工作区、两章任务、教学/练习、有限Agent、教师课程/KC/材料/任务/发布、私有存储/PPT转换、清理与独立部署已落地。最后结果见acceptance.md；不要以旧报告或跳过替代实际验证。

新基线迁移221d0f8caf6e；教师内容所有权增量7d4b982ce106，不修改旧基线。原始课程仍7章65KC，教师自有扩展不改变源文件计数。源码目录frontend/src/{app,features,game,shared}与backend/app模块。

当前用户预览为本机15174/API18081/独立SQLite开发库，账号与用户已保存进度保留。备用15175/API18082使用Reborn PostgreSQL及私有文档转换器。仅操作Reborn容器，不替换main服务。Docker使用授权已恢复。

下一步外部条件：提供兼容cgroupv1的独立Judge0环境后跑5项真实执行验收；配置真实模型后小额度流式/引用/取消验证；需要S3时再接真实服务；补原课件媒体与SRW授权书面凭据。禁止为判题绕过隔离或恢复旧Piston。main仍不自动合并，未来UI改动须逐个审查移植。
