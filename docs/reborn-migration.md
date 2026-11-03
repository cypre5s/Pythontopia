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

Audit and isolation complete. Backend, frontend and assistant/judge/storage implementation in progress. This file and actual code are the continuation authority; do not automatically merge main. Final validation results belong in acceptance.md.
