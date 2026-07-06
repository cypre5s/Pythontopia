# R-13 §5 主线 + AI Boss 设计文档（落地版）

> 本文件由 `R13_OPTIMIZATION_PLAN.md` §1-§3 落实为代码后整理成稿。
> 任何与代码不一致的描述以 `frontend/src/data/mainQuests.ts` /
> `frontend/src/types/mainQuest.ts` / `frontend/src/stores/mainQuest.ts` 为准。

## 1. One-liner

> 「学生扮演镇长 Py 新招的实习生，跟 7 章 Python 知识修复 5 块荒废区域，每章末打一场 4 阶段 AI 出题的『Bug 大梦魇』Boss。」

## 2. 数字目标

- 主线长度：**7 章 × 5 子任务 = 35 主任务** + **7 章 boss = 7 boss 战**。
- 节奏：14 周 / 学期，约 4-6 小时实玩 + 课外预习。
- 完成率目标：主线 60%、7 boss 全胜 30%。
- AI 出题正确率目标 ≥ 95%（依赖 §5 五重审查链）。

## 3. 主线骨架（7 章 × 5 任务）

| 章 | 课程 KC 段 | 复兴区域 | Boss |
| -- | -- | -- | -- |
| 1 入门 | PY43-KC-001..005 | town_hub 喷泉广场 | NameError 雾团 |
| 2 数据类型 | KC-006..015 | farm_north 知识田 | TypeError 章鱼 |
| 3 控制流 | KC-016..025 | mine_south 矿洞入口 | ConditionBug 巨蝙 |
| 4 循环 | KC-026..035 | campus_east 图书馆 + 教室 | LoopHang 巨蛇 |
| 5 函数 | KC-036..045 | port_west 商船 | RecursionBoss 镜像 |
| 6 类 | KC-046..055 | campus_east 工坊 | InheritanceBoss 灵兽 |
| 7 模块/IO/异常 | KC-056..065 | 全镇毕业典礼 | SyntheticBoss 混合体 |

完整 35 条子任务（id / mapKey / kcCodes / evidenceRequired / rewards）见
`frontend/src/data/mainQuests.ts`，由 `tests/data/mainQuests.spec.ts` 10 条用例保证完整性。

## 4. 进度可视化

- **HUD 顶部 chip**（`MainQuestChip.vue`）：`第 X 章 · Boss 名 (i/5) · 整体 N%`。
- **Q 键**（同时点击 chip 的 Q 槽）：打开 `MainQuestPanel.vue` 全屏面板。
- **MainQuestPanel**：7 章树，每章 5 任务 + boss，按 `status` 标签着色
  （completed / in_progress / available / locked）。
- **持久化**：`pyt_main_quest_progress_v1` localStorage key，跨刷新生效。
  跨设备同步等 R-14 接 SaveSlot.snapshot.mainQuest JSONB。

## 5. Boss 副本（`/boss/:chapter`）

- 4 阶段（stage 1..4），每阶段调 `requestBossProblem(chapter, stage)`：
  - 优先 `POST /api/world/boss/{chapter}/problem`（5 重审查通过的 AI 题）；
  - 失败自动 fallback 到 `frontend/src/api/boss.ts` 内置 golden 题模板。
- 提交：`submitBossAttempt`，本地 Pyodide 即时评测 +
  服务端 `POST /api/world/boss/{chapter}/attempt`（当前后端占位，靠 Pyodide）。
- 通过：`stage++` → 下阶段；4 阶段全胜 → `victory` 触发
  `mainQuest.defeatBoss(chapter)` + 4 秒后自动回 `/world`。
- 自适应难度：`recordAiProblem` 写入最近 30 道做题记录，graph 服务读后调难度。

## 6. 数据形态

```ts
interface MainQuestProgress {
  currentChapter: number;          // 1..7，未开始 0
  currentSubQuest: number;         // 1..5，0 = 待领取
  completedSubQuests: string[];    // ["1-1", "1-2", ...]
  defeatedBosses: number[];        // [1, 2, ...]
  unlockedMilestones: string[];    // ["outfit:apprentice", ...]
  recentAiProblems: RecentAiProblemEntry[];
}

interface MainSubQuest {
  id: string;          // "1-1"
  chapter: number;
  index: number;
  title: string;
  hint: string;
  mapKey: 'town_hub' | 'farm_north' | 'mine_south' | 'campus_east' | 'port_west';
  npcKey?: string;
  kcCodes: string[];
  evidenceRequired: MainSubQuestEvidence;
  rewards: MainSubQuestReward[];
}
```

`MainSubQuestEvidence` 5 种 type：`dialog_complete` / `submission` / `resource_consumed` /
`building_visit` / `kc_mastery`。

## 7. 与后端的契约

| 路径 | 方法 | 来源 | 备注 |
| -- | -- | -- | -- |
| `/api/world/boss/{chapter}/problem` | POST | 前端 BossPage | body `{stage, kcCode?, difficulty?}`；返回 BossProblemDto |
| `/api/world/boss/{chapter}/attempt` | POST | 前端 useBossSession | body `{problemId, chapter, stage, code, elapsedSec}`；返回 BossAttemptResultDto |
| `/api/course/documents/{docId}/pdf` | GET | LibraryPage PdfPageView | application/pdf 流式分发，path traversal 防护 |

后端实现：`backend/src/main/java/com/pythontopia/world/boss/`、
`backend/src/main/java/com/pythontopia/coursepack/controller/CourseDocumentPdfController.java`。

## 8. 测试套（本会话新增）

| 文件 | 用例数 |
| -- | -- |
| `frontend/tests/data/mainQuests.spec.ts` | 10 |
| `frontend/tests/stores/mainQuest.spec.ts` | 16 |
| `frontend/tests/components/MainQuestChip.spec.ts` | 6 |
| `frontend/tests/components/MainQuestPanel.spec.ts` | 10 |
| `frontend/tests/pages/BossPage.spec.ts` | 12 |
| `frontend/tests/pages/WorkshopPage.spec.ts` | 6 |
| `frontend/tests/components/WorkshopPanel.spec.ts` | 7 |
| `frontend/tests/components/PdfPageView.spec.ts` | 4 |
| `frontend/tests/api/boss.spec.ts` | 6 |
| `frontend/tests/integration/main-quest-flow.integration.spec.ts` | 20 |
| `backend/.../coursepack/CourseDocumentPdfControllerTest.java` | 5 |
| `backend/.../world/boss/BossServiceTest.java` | 8 |
| `backend/.../world/boss/BossControllerTest.java` | 6 |
| Python `services/pythontopia-graph/tests/agents/boss/*.py` | 94 |

## 9. 后续 wave 待办

- BossPage 4 阶段像素 boss 立绘 + GSAP 转场动画（当前 emoji 占位）。
- graph `/agents/boss/problem` 接 spark / openai provider（当前 100% fallback）。
- 教师 `/teacher/review` 页面（plan §2.3-6 兜底审核流）。
- V120 `save_slot.snapshot.mainQuest` JSONB 字段 + 跨设备同步。
