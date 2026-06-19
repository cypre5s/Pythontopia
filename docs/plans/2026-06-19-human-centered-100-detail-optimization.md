# Human-Centered 100 Detail Optimization Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete 100 small, human-centered optimizations across the Web learning chain without adding new product features.

**Architecture:** Treat the current Web app as the source of truth and polish existing surfaces only: workshop, training, guide, library, app shell, and shared feedback patterns. Each item must improve interaction clarity, front-end guidance, perceived performance, accessibility, resilience, or non-functional quality without introducing a new learner capability.

**Tech Stack:** Vue 3, TypeScript strict, Pinia, Vitest, Playwright where needed, Vite, existing Spring/graph services only when a UI polish needs live validation.

---

## Scope Boundary

- Do not add new product features, endpoints, data models, or gameplay systems.
- Do not upload unrelated dirty worktree changes to ECS.
- Keep every code change tied to an existing user journey.
- Prefer copy, state, accessibility, keyboard, layout, request, and performance improvements.
- Preserve the current visual language: pixel town shell, readable learning content, single-entry AI/workshop surfaces.

## Execution Buckets

| Bucket | Items | Primary Areas | Verification |
| --- | ---: | --- | --- |
| Entry Clarity | 1-10 | App shell, workshop, guide, training | component/page specs |
| First-Screen Guidance | 11-20 | Welcome, empty states, nudge cards | component specs |
| Action Feedback | 21-30 | Buttons, busy states, disabled states | component specs |
| Keyboard And Accessibility | 31-40 | Drawers, dialogs, forms, ARIA | component specs + typecheck |
| Mobile And Layout Stability | 41-50 | Workshop, training, guide, library | page specs + build |
| Perceived Performance | 51-60 | prefetch, caching, request avoidance | unit specs + build |
| Streaming And AI Trust | 61-70 | Guide overlay, workshop SSE, timeline | targeted specs |
| Learning Closure | 71-80 | Training, guide, history, library links | targeted specs |
| Privacy And Safety Signals | 81-90 | URLs, IDs, test data, copy | route/API specs |
| Release Reliability | 91-100 | verification, deploy, health checks | local + ECS checks |

## 100 Detail Iterations

| # | Area | Human Problem | Optimization | Evidence |
| ---: | --- | --- | --- | --- |
| 1 | Workshop | Top tabs expose state but not their controlled drawer. | Add `aria-controls` and stable drawer ids. | `WorkshopPage.spec.ts` |
| 2 | Workshop | Drawer buttons are terse for screen readers. | Add contextual `aria-label` for history, study, calls. | `WorkshopPage.spec.ts` |
| 3 | Workshop | `Esc` order is unclear when panels stack. | Keep modal first, side second, history last and test it. | `WorkshopPage.spec.ts` |
| 4 | Workshop | History drawer close button lacks context. | Give close button panel-specific label. | `WorkshopPage.spec.ts` |
| 5 | Workshop | Current map chip only says disabled visually. | Add `aria-current`/label for current location. | `WorkshopPage.spec.ts` |
| 6 | Workshop | Quick travel labels use shortcut letters without explanation. | Expose shortcut intent via title/aria label. | `WorkshopPage.spec.ts` |
| 7 | Workshop | Calls drawer empty state is passive. | Clarify that it fills after a question/generation. | visual/text check |
| 8 | Workshop | Resource modal title is generic. | Name it as current resource detail, not only document. | component check |
| 9 | Workshop | History selection keeps panels but user gets no feedback. | Add stable continuation hint in history state. | `ChatHistoryRail` spec |
| 10 | Workshop | Narrow layout can obscure close actions. | Ensure drawer bars remain sticky/visible. | CSS/build |
| 11 | Welcome | First prompt examples do not cover planning history issue. | Keep examples but align copy with “ask, plan, make, practice”. | `WelcomePanel.spec.ts` |
| 12 | Welcome | Capability reveal has no controlled region id. | Add `aria-controls` to capability toggle. | `WelcomePanel.spec.ts` |
| 13 | Welcome | Capability cards are visually similar. | Improve label hierarchy and example readability. | visual/build |
| 14 | Welcome | Chips lack explicit button labels for assistive tech. | Add title/aria-label to quick chips. | `WelcomePanel.spec.ts` |
| 15 | Welcome | Students may not know Enter sends. | Placeholder includes one compact interaction hint. | `ChatComposer.spec.ts` |
| 16 | Chat Composer | Uploading attachments silently block send. | Surface “附件解析中” reason on send button. | `ChatComposer.spec.ts` |
| 17 | Chat Composer | Failed attachment retry is easy to miss. | Keep error pill text short and actionable. | `AttachmentPills.spec.ts` |
| 18 | Chat Composer | Capability selection can feel sticky. | Make selected capability chip removable and labelled. | existing behavior check |
| 19 | Chat Composer | Slash and mention menus share keyboard patterns. | Assert Escape closes active menu cleanly. | existing tests |
| 20 | Chat Composer | Drag overlay copy can feel technical. | Use learner-friendly “把文件放这里” wording. | component check |
| 21 | Training | Run/submit buttons do not say when they are busy. | Change labels to “运行中…” and “提交中…”. | `TrainingPanel.spec.ts` |
| 22 | Training | Preview output empty state looks like output text. | Label it as empty output, not program result. | `TrainingPanel.spec.ts` |
| 23 | Training | Submission result lacks live announcement. | Add `role=status`/`aria-live`. | `TrainingPanel.spec.ts` |
| 24 | Training | Bug Mine and Code Theater actions compete visually. | Group failed-submission actions with a label. | `TrainingPanel.spec.ts` |
| 25 | Training | AI toggle does not expose panel state. | Add `aria-expanded` and `aria-controls`. | `TrainingPanel.spec.ts` |
| 26 | Training | Debug input label is English-only. | Use Chinese visible label with technical hint. | `TrainingPanel.spec.ts` |
| 27 | Training | Copy sample success has no fallback context. | Keep toast but button title names input/output. | component check |
| 28 | Training | Variant mode safety is hidden. | Add non-invasive copy when variant store is absent. | route/component check |
| 29 | Training | Empty page says “请选择题目” only. | Give one recovery action/copy. | component check |
| 30 | Training | Judge failure reason can overflow. | Ensure reason wraps and remains readable. | CSS/build |
| 31 | Guide Nudge | Nudge is important but not announced. | Add `role=status` and polite live region. | `GuideNudgeCard.spec.ts` |
| 32 | Guide Nudge | Disabled button copy is unchanged. | Label waiting state without adding an action. | `GuideNudgeCard.spec.ts` |
| 33 | Guide Nudge | CE/RE/TLE share copy too broadly. | Preserve distinction in title/body. | existing spec |
| 34 | Tutor Workflow | Empty state is long and dense. | Break it into shorter scan lines. | component check |
| 35 | Tutor Workflow | Running coach steps lack live context. | Add status semantics around progress list. | targeted spec |
| 36 | Tutor Workflow | Close button is only “×”. | Add title plus aria label. | component check |
| 37 | Tutor Workflow | Action buttons do not mark recommended item strongly. | Preserve existing recommendation and add accessible label. | component check |
| 38 | Tutor Workflow | Chat input has weak disabled affordance. | Clarify busy state. | component check |
| 39 | App Header | Logout is destructive but immediate. | Improve label copy, no new confirm flow. | component spec |
| 40 | App Shell | Main content lacks skip/landmark clarity. | Preserve `main`, add app shell accessible label if useful. | AppLayout spec |
| 41 | Workshop Mobile | History + side drawers collapse to full overlay. | Verify close remains visible under 1120px. | build/manual |
| 42 | Workshop Mobile | Travel bar can overflow with chips. | Ensure horizontal scroll/tight wrapping. | CSS/build |
| 43 | Training Mobile | Four-column tutor layout is too dense. | Collapse tutor below editor on narrow widths. | CSS/build |
| 44 | Training Mobile | Sample input/output two columns can squeeze. | Stack samples below breakpoint. | CSS/build |
| 45 | Guide Mobile | Nav toggle state is not reflected in layout class. | Preserve and test nav open class. | `GuidePage.spec.ts` |
| 46 | Guide Mobile | Outline competes with content. | Keep outline readable or hide at mobile breakpoint. | build |
| 47 | Library Mobile | Three panes can become unusable. | Tighten split layout breakpoints. | build |
| 48 | Library | Page loading overlay should not cause layout shift. | Preserve last PDF canvas while showing overlay. | existing `PdfPageView.spec.ts` |
| 49 | Library | Video list empty state should explain fallback. | Improve existing fallback text. | page spec |
| 50 | Library | KC select width can clip long names. | Improve select/title affordance. | CSS/build |
| 51 | Prefetch | Idle prefetch might run for anonymous users. | Keep auth retry and add tests. | prefetch spec |
| 52 | Prefetch | Save-Data users should not pay bandwidth. | Assert saveData/2g skips prefetch. | prefetch spec |
| 53 | Prefetch | Repeated schedule calls can duplicate imports. | Assert idempotency. | prefetch spec |
| 54 | Workshop History | Saved turn should not refetch whole DB. | Keep local merge behavior. | existing spec |
| 55 | Workshop History | Manual refresh remains available. | Keep refresh button DB request. | existing/added spec |
| 56 | Library | Page-id resolution can repeatedly list pages. | Cache document page lists during session. | page/unit spec |
| 57 | Library | RAG question retry does full fallback silently. | Clarify answer vs snippet fallback state. | page text check |
| 58 | Training | Pyodide preview should never hit server. | Preserve unit guard. | existing spec |
| 59 | App | Network toasts should not spam. | Preserve cooldown behavior. | existing API spec |
| 60 | Build | Large chunks need warning visibility. | Keep build warnings visible in final report. | build output |
| 61 | Guide Overlay | Streaming placeholder should be meaningful. | Keep loop-aware placeholder text. | GuideOverlay spec |
| 62 | Guide Overlay | Stop button must clearly stop loop. | Verify stop aborts SSE. | existing spec |
| 63 | Guide Overlay | Latest assistant bubble should be targeted. | Preserve latest-bubble assertion pattern. | existing spec |
| 64 | Workshop | ASK/PLAN/RESOURCE labels improve trust. | Keep kind badge beside evidence badge. | history spec |
| 65 | Workshop | Grounding label “记录” can be ambiguous. | Explain in title. | history component |
| 66 | Workshop | Tool traces empty state should not imply failure. | Copy says it fills after a tool run. | page spec |
| 67 | Tutor Workflow | Guardrail failures should be soft. | Keep toast fallback, no crash. | existing spec |
| 68 | SSE | Terminal result/error should stop stream. | Preserve `useSseStream` terminal behavior. | existing spec |
| 69 | AI Copy | Avoid promising hidden answers. | Keep “只引导、不给完整答案”. | text check |
| 70 | AI Copy | Explain when content is general knowledge. | Preserve grounding/no-evidence labels. | history/spec |
| 71 | Training | After AC, student gets bounded next step. | Keep nudge click before tutor. | existing spec |
| 72 | Training | After WA, actions align to debug/visualize. | Keep Bug Mine + Code Theater grouping. | spec |
| 73 | Library | Related problems jump to training. | Preserve problem link query. | page spec |
| 74 | Guide | Journey path matches “看学练测”. | Keep hero path sequence. | GuidePage spec |
| 75 | Workshop | Welcome and composer path match ask/plan/make. | Align copy. | component spec |
| 76 | Profile | Self-rate buttons show saving state. | Preserve disabled saving key. | existing spec |
| 77 | Review Hall | Path proposal remains accept/dismiss. | Preserve explicit actions. | existing spec |
| 78 | Mainline | Stage pages hide duplicate companion. | Preserve route meta. | router spec |
| 79 | Board | Empty class tasks stay explicit. | copy check |
| 80 | Companion | Hidden on focused AI pages. | router/app check |
| 81 | URLs | Private bug mine ids stay out of URL. | Preserve router sanitization. | router spec |
| 82 | URLs | Resource id stays in store, not URL. | Preserve WorkshopPage store modal. | page spec |
| 83 | Uploads | File extensions and size are bounded. | preserve upload tests |
| 84 | Auth | Session expiration redirects with target. | router spec |
| 85 | Exam | AI routes remain blocked during test. | route guard spec |
| 86 | Library | Sanitized markdown remains strict. | security spec |
| 87 | Teacher | Teacher-only panels stay role-gated. | existing API/UI tests |
| 88 | History | Probe/test accounts cleaned after live checks. | deploy log/report |
| 89 | Deployment | White-list changed files only. | rsync command report |
| 90 | Deployment | Verify release path and route before SSH conclusions. | route/health evidence |
| 91 | Local | Focused component specs pass. | vitest output |
| 92 | Local | TypeScript strict passes. | `npm run typecheck` |
| 93 | Local | Lint has no errors. | `npm run lint` |
| 94 | Local | Frontend production build passes. | `npm run build` |
| 95 | Local | Backend untouched or targeted tests pass. | Maven if touched |
| 96 | ECS | Direct route to ECS checked before deploy. | `ip route get` |
| 97 | ECS | Remote image rebuild succeeds. | docker build output |
| 98 | ECS | Containers all healthy. | docker compose ps |
| 99 | ECS | Public health endpoint 200. | curl `/healthz` |
| 100 | Final Report | Detailed list maps every iteration to evidence. | final checklist |

## Implementation Notes

- First implementation batch targets items 1-6, 21-26, 31-32, and 51-53 because they are high-signal and locally testable.
- Later batches should prefer existing specs before adding new ones.
- ECS deployment must use a whitelist that includes only the files changed for this plan plus any already-required history files if they remain part of the live release baseline.
