package com.pythontopia.feature.arena

/**
 * 训练场 UI 状态（MVVM + `StateFlow<ArenaUiState>` 单对象）。
 *
 * [lastSubmission] 按全局最近一次提交保存（非 `Map<problemId, Submission>`），切题不清空，重新提交会覆盖。
 * [myUserId] 当前为演示占位 [DEMO_USER_ID]；积分榜行的 userId 来自后端 PASSED 提交，高亮可能不命中，接登录态后改 JWT 真实 id。
 */
data class ArenaUiState(
    val isLoadingSession: Boolean = false,
    val isLoadingLeaderboard: Boolean = false,
    val isSubmitting: Boolean = false,
    val session: ArenaSession? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val lastSubmission: Submission? = null,
    val selectedTab: ArenaTab = ArenaTab.TODAY,
    val currentProblemIdx: Int = 0,
    val codeBuffers: Map<Long, String> = emptyMap(),
    val myUserId: Long = DEMO_USER_ID,
    val errorMessage: String? = null,
) {
    /** session 未加载或题目列表为空时返回 null。 */
    val currentProblem: ArenaProblem?
        get() = session?.problems?.getOrNull(currentProblemIdx)

    /** 优先取 [codeBuffers]，否则回退 [ArenaProblem.starterCode]。 */
    val currentCode: String
        get() {
            val problem = currentProblem ?: return ""
            return codeBuffers[problem.problemId] ?: problem.starterCode
        }

    /** 排行榜中 userId 匹配 [myUserId] 的条目；未上榜返回 null。 */
    val myRank: LeaderboardEntry?
        get() = leaderboard.firstOrNull { it.userId == myUserId }

    companion object {
        const val DEMO_USER_ID = 104L
    }
}

/** 顶部 tab：今日赛事 / 积分榜。 */
enum class ArenaTab(val label: String) {
    TODAY("今日赛事"),
    LEADERBOARD("积分榜"),
}
