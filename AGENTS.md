# Pythontopia Reborn

This branch is a Web-only Python-learning RPG. Students enter /world; teachers use management pages. Current code and docs/product.md, architecture.md, api-contract.md, content-authoring.md, development.md, acceptance.md and reborn-migration.md supersede historical plans.

## Scope and safety
- Git提交标题和正文统一使用中文。
- Work only on reborn. Check the branch before commits. Never change/push main, force-push, rewrite history, replace production, connect to old databases/volumes, or remove another person's changes.
- Main evolves independently: never automatically merge it. Review and port individual UI changes with their dependencies.
- Do not restore Java, old multi-agent orchestration, profiles, autonomous learning paths, mine/expedition/boss systems, independent student feature pages, economy or non-Web clients.
- Preserve LICENSE, attribution and licensed/editable sources of retained assets. No commercial game art, invented citations, fake live AI/judge/data.

## Implementation
- Vue 3, strict TypeScript, Pinia, Phaser 3. One Phaser.Game per student session. Phaser owns world rendering; Vue owns readable learning UI through typed events.
- One primary workspace at a time. Lesson, practice, journal, library, classroom and settings replace each other. Preserve drafts/reading position. Focused editors block movement. Logout clears user state.
- FastAPI modular monolith, SQLAlchemy 2, Alembic, PostgreSQL. Share one project; no old compatibility API. Validate DTOs, authorize server-side, use transactions and unique constraints.
- Current task DAG definitions are separate from player state. Validate scripts, branches, ALL/ANY, optional nodes and effects. No eval/exec in config. Progress and grades require server-verified evidence. Retries are idempotent.
- Use real Python语言基础 pack 43: 7 documents, 561 pages, 7 chapters, 65 KC, 46 examples, 39 original problems. Unreviewed questions remain unpublished. New authored questions cite real pages. Missing media remains explicitly missing.
- Movement, NPC scripts, task progress, page turns and world loading make zero model calls. Only explicit student questions in authorized activities invoke one create_agent runtime. Role configuration, limited tools, budgets and persisted sessions are isolated by user/class/activity. No grading/unlock/publish tools; missing credentials means unavailable.
- Execute student code only through Judge0, never on the API host. Run differs from formal submit. Never expose hidden cases, answers or reference code to students/prompts/bundles. Unknown external dispatches require reconciliation, not blind resubmission.
- Uploads are private, checked and bounded; replacements update current content. Conversions fail visibly. Retrieval citations must belong to authorized current materials.

## Documentation and validation
Use Context7 for library/API usage: npx ctx7@latest library <official-name> "<specific question>", then docs <returned-id> "<question>"; outside sandbox, maximum 3 commands per question. Report quota errors and suggest login/CONTEXT7_API_KEY.

The only built-in main quest is arrival (初来小镇：第一堂 Python 课). Retain the full course materials and knowledge points; do not restore retired chapter quests to provide test coverage. Use test-only task definitions for generic branching and merge tests.

Run frontend npm ci, typecheck, lint, test and build; backend locked install, ruff, pytest, fresh Alembic migration and repeated seed. Actual startup commands belong in development.md. Validate the authored main quest including resume and account-switch scenarios, permissions, idempotency, zero incidental AI calls, map reachability, independent deployment and browser viewports. Accurately record passed/failed/skipped and live-versus-contract results in acceptance.md. Update reborn-migration.md for continuation rather than creating parallel plans/reports.
