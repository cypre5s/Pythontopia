/**
 * 单个 KC 的掌握度快照（`GET /api/learner/mastery`）。
 *
 * **单位契约**：`masteryScore` 与 `confidence` 都是 **0–1 小数**，由后端
 * `MasteryEvaluator.clamp()` 钳在 [0,1]（MIN=0 / MAX=1），并做读时遗忘衰减。
 * 渲染成百分比时必须先 `* 100`（如 HUD 掌握度条），直接 `Math.round(avg)` 会永远得到 0 或 1；
 * 同理，任何「衰减/加分」都属于后端语义，前端不得在本地对该值做加减伪造。
 */
export interface MasterySnapshotDto {
  kcCode: string;
  /** 掌握度，0–1 小数（不是百分比）。 */
  masteryScore: number;
  /** 置信度，0–1 小数。 */
  confidence: number;
  lastEventAt: string | null;
  latestSource: string | null;
}
