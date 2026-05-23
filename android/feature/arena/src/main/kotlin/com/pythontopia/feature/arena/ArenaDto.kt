package com.pythontopia.feature.arena

/**
 * Arena 模块 DTO 集合。
 *
 * Arena = 班级内 / 全校的练习赛：每日一场（单日多题快速判定），赛后按通过题数 + 通关时长出
 * 积分榜。Beta 首发不做 PVP 实时同步，所以 DTO 只覆盖最小闭环：
 *
 * - 当日赛事元数据（[ArenaSession]）+ 题目列表（[ArenaProblem]）
 * - 玩家提交（[Submission]）：当前只回三类终态（PASSED / FAILED / RUNTIME_ERROR）+ 是否正
 *   式记分；判题细节交给后端 SubprocessPythonSandbox，前端只做展示
 * - 积分榜条目（[LeaderboardEntry]）：单赛事内的排名快照
 *
 * 这些类故意不加 `@Serializable`，因为 `:feature:arena` 模块没有应用
 * `kotlin-serialization` 插件（AGENTS.md 约束 R-12 不引入新依赖）。等 `:core:network`
 * 正式补充 `ArenaApi` 时，再用 OpenAPI Generator 生成等价 DTO 即可，业务层迁移成本可控。
 *
 * 对应后端端点（见任务背景）：
 * - GET  /api/arena/sessions/today          → [ArenaSession]（内含 [ArenaProblem] 列表）
 * - POST /api/arena/submit                  → [Submission]
 * - GET  /api/arena/leaderboard?classroomId → List&lt;[LeaderboardEntry]&gt;
 */

/**
 * 一场练习赛的快照。
 *
 * @property sessionId 赛事自增 ID；Beta 首发同一时间全校只有 1 场，对应 "今日赛事"。
 * @property title 赛事标题（如 "5/24 Python 周末赛"），用于 [ArenaScreen] 顶部展示。
 * @property classroomId 关联班级 ID；为 null 表示全校公开赛，不绑定具体班级。
 * @property startEpochMs 赛事开始时间（UTC 毫秒），用于倒计时。
 * @property endEpochMs 赛事结束时间（UTC 毫秒），用于倒计时与"是否已结束"判定。
 * @property problems 本场题目列表；顺序即题号 1/2/3...，长度通常 3-5。
 */
data class ArenaSession(
    val sessionId: Long,
    val title: String,
    val classroomId: Long?,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val problems: List<ArenaProblem>,
)

/**
 * 一道练习赛题目。
 *
 * 与 `:feature:workshop` 的 `ProblemSpec`、`:feature:mine` 的题目模型保持精神一致：
 * "题面 markdown + 起始模板代码"。Arena 不渲染样例 IO，因为快速判定场景里玩家直接看
 * statementMd 中的说明即可，多余的样例栏会占用倒计时下方的稀缺垂直空间。
 *
 * @property problemId 全局题目 ID，提交时透传给 `POST /api/arena/submit` 的 problemId 字段。
 * @property title 题目标题，用于 tab 切换显示 "T1 / T2 / T3"。
 * @property statementMd 题面 markdown 文本；当前用纯文本 [androidx.compose.material3.Text]
 *           渲染（不引入 Markwon），Beta+1 接 :feature:workshop 的 Markdown renderer。
 * @property starterCode 起始模板代码，进入题目时自动填入 [ProblemRunner] 的编辑器，玩家在此基础上改写。
 * @property scoreReward 通过本题可得积分；用于排行榜累计计算。
 */
data class ArenaProblem(
    val problemId: Long,
    val title: String,
    val statementMd: String,
    val starterCode: String,
    val scoreReward: Int,
)

/**
 * 一次代码提交的判定结果。
 *
 * Arena 走"快速判定"，所以只关心终态，不做 ARENA 模块内的轮询（轮询由后端
 * 一次 POST 同步返回结果；如果后端选择异步则 [ArenaRepository.submit] 内部封装）。
 * Web 端 `:feature:arena` 的 PENDING/POLLING/SUBMITTING 三态在 Android 演示版被压缩成
 * "提交中" → "结果出来" 两步，由 [ArenaViewModel] 的 `isSubmitting` 标志承担。
 *
 * @property submissionId 提交记录 ID，后续看排行榜时按此 ID 关联。
 * @property problemId 关联题目 ID，与 [ArenaProblem.problemId] 对齐。
 * @property status 三类终态字符串：`PASSED` / `FAILED` / `RUNTIME_ERROR`；后端枚举对齐。
 * @property message 给玩家看的人类可读反馈（如 "样例 2 失败：期望 6 实际 5"）。
 * @property scoreAwarded 本次提交实际得分；FAILED / RUNTIME_ERROR 时通常为 0。
 */
data class Submission(
    val submissionId: Long,
    val problemId: Long,
    val status: String,
    val message: String,
    val scoreAwarded: Int,
)

/**
 * 排行榜单行条目。
 *
 * 没有 avatarUrl 字段，因为 Beta 首发用首字母占位头像（见
 * [LeaderboardView]），减小 OkHttp 图片缓存与权限成本；Beta+2 接 :feature:profile
 * 的真实头像 URL 时再扩展。
 *
 * @property rank 排名（1 开始）；同分按提交时间升序排前。
 * @property userId 学员 ID，用于跨场对比与高亮"我"的位置。
 * @property displayName 学员显示名（昵称优先，无昵称回退到学号）。
 * @property score 本场累计积分。
 * @property solvedCount 已通过题数，作为同分时的次序参考显示。
 */
data class LeaderboardEntry(
    val rank: Int,
    val userId: Long,
    val displayName: String,
    val score: Int,
    val solvedCount: Int,
)
