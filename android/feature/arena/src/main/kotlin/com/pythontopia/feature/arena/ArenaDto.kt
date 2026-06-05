package com.pythontopia.feature.arena

/**
 * Arena 模块 UI DTO 集合。
 *
 * Arena = Python 课程训练场：抽取课程题库若干题，做“看题→写码→提交→服务端判题”的快速闭环。
 * Beta 首发不做 PVP / 跨用户排行榜，所以 DTO 只覆盖最小闭环：
 *
 * - 当日赛事元数据（[ArenaSession]）+ 题目列表（[ArenaProblem]）
 * - 玩家提交（[Submission]）：服务端判题终态 PASSED / FAILED / ERROR + 本地展示积分；
 *   判题明细（通过用例数 / 失败原因）来自后端 judgeResult，前端只做展示
 * - “积分榜”条目（[LeaderboardEntry]）：local-only 个人战绩，非真实多人排名（后端无排行榜端点）
 *
 * 这些类是 Compose/ViewModel 使用的稳定模型；Retrofit wire DTO 留在
 * [ArenaRepository] 内部，避免把后端字段形状扩散到 UI。
 *
 * 对应后端端点：
 * - GET  /api/course/problems?kcCode=   → [ArenaProblem] 来源
 * - POST /api/submissions               → 创建提交（source=TRAINING）
 * - GET  /api/submissions/{id}          → 轮询判题终态 + judgeResult（[Submission]）
 * - GET  /api/submissions?status=PASSED → 当前账号真实练习统计（派生本地积分榜）
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
 * @property problemId 全局题目 ID，提交时透传给 `POST /api/submissions` 的 problemId 字段。
 * @property title 题目标题，用于 tab 切换显示 "T1 / T2 / T3"。
 * @property statementMd 题面 markdown 文本；当前用纯文本 [androidx.compose.material3.Text]
 *           渲染（不引入 Markwon），Beta+1 接 :feature:workshop 的 Markdown renderer。
 * @property starterCode 起始模板代码，进入题目时自动填入 [ProblemRunner] 的编辑器，玩家在此基础上改写。
 * @property scoreReward 通过本题的展示积分（local-only，前端按难度换算，后端不下发也不记账）。
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
 * 判题契约：[ArenaRepository.submit] 先 POST 再轮询 `GET /api/submissions/{id}` 到终态，
 * 把 PENDING/RUNNING 收敛成此终态模型。UI 侧的 "提交中" → "结果出来" 由
 * [ArenaViewModel] 的 `isSubmitting` 标志承担。
 *
 * @property submissionId 提交记录 ID（后端 SubmissionDto.id）。
 * @property problemId 关联题目 ID，与 [ArenaProblem.problemId] 对齐。
 * @property status 终态字符串：`PASSED` / `FAILED` / `ERROR`（对齐后端 SubmissionStatus 枚举名）。
 * @property message 给玩家看的人类可读反馈，失败时取后端 judgeResult.failureReason。
 * @property scoreAwarded 本次提交的展示积分（local-only）；非 PASSED 为 0，后端不记账。
 */
data class Submission(
    val submissionId: Long,
    val problemId: Long,
    val status: String,
    val message: String,
    val scoreAwarded: Int,
)

/**
 * “积分榜”单行条目。
 *
 * **local-only**：后端没有跨用户排行榜端点，[ArenaRepository.leaderboard] 只能用当前账号
 * 自己的 `GET /api/submissions?status=PASSED` 真实数据派生出单行个人战绩，因此这是“我的战绩”
 * 而非真实多人排名。沿用 [LeaderboardView] 的列表样式展示，Beta+ 若后端补排行榜端点再扩展。
 *
 * 没有 avatarUrl 字段，因为 Beta 首发用首字母占位头像（见 [LeaderboardView]）。
 *
 * @property rank 名次（当前恒为 1，单行个人战绩）。
 * @property userId 学员 ID（取自后端 SubmissionDto.userId），用于高亮“我”的位置。
 * @property displayName 学员显示名（当前为占位“当前账号”，接登录态后改真实昵称）。
 * @property score 已解题数换算的展示积分（local-only）。
 * @property solvedCount 已通过的不同题目数（来自真实 PASSED 提交去重）。
 */
data class LeaderboardEntry(
    val rank: Int,
    val userId: Long,
    val displayName: String,
    val score: Int,
    val solvedCount: Int,
)
