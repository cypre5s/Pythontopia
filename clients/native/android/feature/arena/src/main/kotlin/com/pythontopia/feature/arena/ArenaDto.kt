package com.pythontopia.feature.arena

/**
 * 训练场 UI DTO；Retrofit wire 类型留在 [ArenaRepository] 内部。
 *
 * 后端：GET `api/course/problems`、POST/GET `api/submissions`、GET `api/submissions?status=PASSED`（派生本地积分榜）。
 * [LeaderboardEntry] 为 local-only 个人战绩，非真实多人排名。
 */

/** 一场练习赛快照。 */
data class ArenaSession(
    val sessionId: Long,
    val title: String,
    val classroomId: Long?,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val problems: List<ArenaProblem>,
)

/** 练习题目：题面 markdown + 起始模板；不渲染样例 IO。 */
data class ArenaProblem(
    val problemId: Long,
    val title: String,
    val statementMd: String,
    val starterCode: String,
    val scoreReward: Int,
)

/**
 * 提交终态；[ArenaRepository.submit] 轮询 `GET /api/submissions/{id}` 收敛 PENDING/RUNNING。
 * status：`PASSED` / `FAILED` / `ERROR`；[scoreAwarded] 为 local-only 展示积分。
 */
data class Submission(
    val submissionId: Long,
    val problemId: Long,
    val status: String,
    val message: String,
    val scoreAwarded: Int,
)

/**
 * 积分榜单行（local-only）：由当前账号 PASSED 提交派生，[rank] 恒为 1。
 * [score] / [solvedCount] 为前端展示换算，后端无跨用户排行榜端点。
 */
data class LeaderboardEntry(
    val rank: Int,
    val userId: Long,
    val displayName: String,
    val score: Int,
    val solvedCount: Int,
)
