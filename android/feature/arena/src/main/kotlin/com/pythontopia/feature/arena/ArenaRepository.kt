package com.pythontopia.feature.arena

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Arena 仓库：封装"今日赛事 / 提交 / 排行榜"三个端点。
 *
 * **当前阶段（Android-R10 实施版）：纯离线 demo 数据**
 *
 * `:feature:arena` 模块当前依赖只声明了 `:core:common / :core:design / :core:data`，
 * 并未引入 `:core:network`（AGENTS.md "不引入新依赖" 约束）；同时 `:core:network` 也
 * 尚未补充 `ArenaApi`。所以本仓库目前以"始终 fallback 到本地 demo 快照"形式实现，
 * 接口签名严格对齐后端契约：
 *
 * - `GET  /api/arena/sessions/today`            → [todaysSession]
 * - `POST /api/arena/submit`                    → [submit]
 * - `GET  /api/arena/leaderboard?classroomId=X` → [leaderboard]
 *
 * 等后端接口稳定且 OpenAPI Generator 流水线收录 ArenaApi 后，把内部 `delay + 本地构造`
 * 替换为 `runCatching { api.xxx() }.getOrElse { DEMO_xxx }` 即可，**调用方零迁移成本**。
 *
 * 设计上跟随 [DialogRepository] / [HubRepository] 的 fallback 优先原则：演示阶段断网
 * 也不能崩，否则比赛叙事"打开应用就能玩"会破功。
 */
@Singleton
class ArenaRepository @Inject constructor() {

    /**
     * 拉取今日赛事元数据 + 题目列表。
     *
     * 返回的 [ArenaSession.startEpochMs] / [ArenaSession.endEpochMs] 以"调用瞬间"为基准
     * 重新计算，让倒计时无论几点打开 demo 都有合理剩余时间（默认还剩 30 分钟）。
     */
    suspend fun todaysSession(): ArenaSession {
        delay(SIMULATED_LATENCY_MS)
        val now = System.currentTimeMillis()
        return DEMO_SESSION.copy(
            startEpochMs = now - 60_000L,
            endEpochMs = now + DEMO_REMAINING_MS,
        )
    }

    /**
     * 提交一份代码，返回判定结果。
     *
     * **Demo 判定规则（不接后端时）**：
     *  - 代码内出现 `print(` 与 `return` 任一关键字 → 视为 PASSED + 满分
     *  - 代码内出现 `raise` / `1/0` → RUNTIME_ERROR
     *  - 其余 → FAILED，message 提示 "样例 1 输出不一致"
     *
     * 这是为了让无网络的真机 demo 能体验到三种结果分支（覆盖 ArenaViewModelTest 的三类断言），
     * 不代表真实判题逻辑；正式判题永远由后端 SubprocessPythonSandbox 决定。
     */
    suspend fun submit(problemId: Long, code: String): Submission {
        delay(SIMULATED_JUDGE_MS)
        val problem = DEMO_SESSION.problems.firstOrNull { it.problemId == problemId }
        val scoreCap = problem?.scoreReward ?: 100
        val submissionId = Random.nextLong(1_000L, 9_999_999L)
        return when {
            code.contains("raise") || code.contains("1/0") -> Submission(
                submissionId = submissionId,
                problemId = problemId,
                status = STATUS_RUNTIME_ERROR,
                message = "运行时错误：ZeroDivisionError 或主动 raise；请先在本地小数据集上验证",
                scoreAwarded = 0,
            )
            code.contains("print(") || code.contains("return") -> Submission(
                submissionId = submissionId,
                problemId = problemId,
                status = STATUS_PASSED,
                message = "全部 3 / 3 样例通过，记 $scoreCap 分",
                scoreAwarded = scoreCap,
            )
            else -> Submission(
                submissionId = submissionId,
                problemId = problemId,
                status = STATUS_FAILED,
                message = "样例 1 输出不一致：期望与实际不匹配，请检查输入处理",
                scoreAwarded = 0,
            )
        }
    }

    /**
     * 拉取排行榜。
     *
     * @param classroomId null 表示全校榜；非 null 表示班级内榜。当前 demo 数据相同，
     *        但调用方仍应区分两种语义（UI 上 "我所在班级 / 全校" 切换由调用方决定）。
     */
    suspend fun leaderboard(classroomId: Long?): List<LeaderboardEntry> {
        delay(SIMULATED_LATENCY_MS)
        return DEMO_LEADERBOARD
    }

    companion object {
        /** 三类终态对外暴露为 String，避免封装 enum 带来的反序列化噪音；调用方按需对比。 */
        const val STATUS_PASSED = "PASSED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_RUNTIME_ERROR = "RUNTIME_ERROR"

        private const val SIMULATED_LATENCY_MS = 120L
        private const val SIMULATED_JUDGE_MS = 350L
        private const val DEMO_REMAINING_MS = 30L * 60L * 1000L

        private val DEMO_SESSION = ArenaSession(
            sessionId = 1L,
            title = "Python 周末练习赛",
            classroomId = null,
            startEpochMs = 0L,
            endEpochMs = 0L,
            problems = listOf(
                ArenaProblem(
                    problemId = 1001L,
                    title = "T1 · 求和函数",
                    statementMd = "实现 `sum_two(a, b)`，返回两个整数之和。\n样例：sum_two(2, 3) → 5",
                    starterCode = "def sum_two(a, b):\n    # TODO: 实现返回 a + b\n    pass\n",
                    scoreReward = 100,
                ),
                ArenaProblem(
                    problemId = 1002L,
                    title = "T2 · 偶数计数",
                    statementMd = "给定整数列表，统计偶数个数并 print 输出。\n样例：[1,2,3,4] → 2",
                    starterCode = "def count_even(nums):\n    # TODO\n    return 0\n\nprint(count_even([1,2,3,4]))\n",
                    scoreReward = 150,
                ),
                ArenaProblem(
                    problemId = 1003L,
                    title = "T3 · 字符串反转",
                    statementMd = "实现 `reverse_str(s)`，不使用切片 `[::-1]`。\n样例：reverse_str(\"abc\") → \"cba\"",
                    starterCode = "def reverse_str(s):\n    # TODO: 用循环实现\n    return s\n",
                    scoreReward = 200,
                ),
            ),
        )

        private val DEMO_LEADERBOARD = listOf(
            LeaderboardEntry(rank = 1, userId = 101L, displayName = "陈思齐", score = 450, solvedCount = 3),
            LeaderboardEntry(rank = 2, userId = 102L, displayName = "黄子轩", score = 350, solvedCount = 3),
            LeaderboardEntry(rank = 3, userId = 103L, displayName = "李墨白", score = 250, solvedCount = 2),
            LeaderboardEntry(rank = 4, userId = 104L, displayName = "我（演示账号）", score = 150, solvedCount = 2),
            LeaderboardEntry(rank = 5, userId = 105L, displayName = "周可凡", score = 100, solvedCount = 1),
            LeaderboardEntry(rank = 6, userId = 106L, displayName = "吴晚秋", score = 100, solvedCount = 1),
            LeaderboardEntry(rank = 7, userId = 107L, displayName = "郑听雨", score = 0, solvedCount = 0),
        )
    }
}
