# Pythontopia Reborn

This branch is a Web-only Python-learning RPG. Students enter /world; teachers use management pages. Start with docs/README.md to find the relevant current specification. Current code and docs/product.md, architecture.md, api-contract.md, content-authoring.md, development.md, acceptance.md, reborn-migration.md and docs/design/README.md supersede historical plans.

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
- Development replay tools require explicit `REBORN_ENV=development` and `REBORN_DEV_TOOLS=1`; production never mounts reset routes. Resets affect only the authenticated student in the selected joined classroom. Acceptance tests use new isolated accounts and never reset real demonstration records.

## Interface design
- 游戏页面与教师后台使用两套界面规范，入口见 [设计规范索引](docs/design/README.md)。新增或修改页面前，先读对应规范，不将一套主题移植到另一套页面。
- 学生游戏工作区遵循 [游戏界面规范](docs/design/game-interface.md)：复用 `OverlayHost.vue` 的单一图标标题、一级返回与“回到小镇”；共享令牌、控件和交互状态由 `gameDesign.css` 提供，目录与正文布局由 `townWorkspaces.css` 提供。工作区外壳透明，各内容卡片独立，卡片间可见小镇，不再添加覆盖整页的大底板或重复页面标题。
- 游戏共享样式限定在 `.game-workspace` 内。新增控件优先复用现有组件与语义化原生 `button`、`input`、`select`、`textarea`；保持可见焦点、44px 基础操作目标、100%–200% 文字偏好、减少动效和输入时暂停角色移动。页面局部样式只负责其内容差异，不复制共享控件主题。
- 教师管理页遵循 [后台界面规范](docs/design/teacher-interface.md)，复用现有后台组件与 `teacherTheme.css`，主题限定在 `.teacher-page` / `.teacher-dialog`。不要在后台导入游戏主题，也不要把后台主题、Tailwind Preflight 或无范围控件规则引入学生页面。

## Documentation and validation
Use Context7 for library/API usage: npx ctx7@latest library <official-name> "<specific question>", then docs <returned-id> "<question>"; outside sandbox, maximum 3 commands per question. Report quota errors and suggest login/CONTEXT7_API_KEY.

The only built-in main quest is arrival (初来小镇：第一堂 Python 课). Retain the full course materials and knowledge points; do not restore retired chapter quests to provide test coverage. Use test-only task definitions for generic branching and merge tests.

Run frontend npm ci, typecheck, lint, test and build; backend locked install, ruff, pytest, fresh Alembic migration and repeated seed. Actual startup commands belong in development.md. Validate the authored main quest including resume and account-switch scenarios, permissions, idempotency, zero incidental AI calls, map reachability, independent deployment and browser viewports. Accurately record passed/failed/skipped and live-versus-contract results in acceptance.md. Update reborn-migration.md for continuation rather than creating parallel plans/reports.
