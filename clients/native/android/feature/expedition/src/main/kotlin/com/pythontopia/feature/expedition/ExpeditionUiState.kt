package com.pythontopia.feature.expedition

/**
 * 无尽远征 UI 状态（单一不可变快照；持久化部分对齐 [ExpeditionRunSnapshot]，
 * active* / loading / elapsed 等为「中途答题」临时态，不持久化）。
 */
data class ExpeditionUiState(
    val phase: RogueRunPhase = RogueRunPhase.IDLE,
    val focusChapter: Int? = null,
    val nodes: List<RogueNode> = emptyList(),
    val layers: Int = 0,
    val currentNodeId: String? = null,
    val hp: Int = RogueConfig.EXPEDITION_MAX_HP,
    val maxHp: Int = RogueConfig.EXPEDITION_MAX_HP,
    val momentum: Int = 0,
    val bestMomentum: Int = 0,
    val score: Int = 0,
    val relics: List<String> = emptyList(),
    val history: List<RogueHistoryEntry> = emptyList(),
    val isDaily: Boolean = false,
    val dailyAvailable: Boolean = true,
    // ── 临时态（不持久化） ──
    val activeNodeId: String? = null,
    val activeProblem: ExpeditionProblemDto? = null,
    val activeCode: String = "",
    val challengeResult: ExpeditionAttemptResultDto? = null,
    val loading: Boolean = false,
    val elapsedSec: Int = 0,
    val errorMessage: String = "",
    val activeEventText: String = "",
    val bossPhase: Int = 1,
    val bossPhasesTotal: Int = RogueConfig.BOSS_PHASES,
    // ── 衍生数据源 ──
    val mastery: Map<String, Double> = emptyMap(),
    /** 一次性提示（relic/营地回血等），Screen 消费后调 [clearToast]。 */
    val toast: String? = null,
) {
    val activeNode: RogueNode? get() = activeNodeId?.let { id -> nodes.firstOrNull { it.id == id } }

    val nodesByLayer: List<List<RogueNode>>
        get() = (0 until layers).map { layer ->
            nodes.filter { it.layer == layer }.sortedBy { it.col }
        }

    val depth: Int get() = nodes.count { it.state == RogueNodeState.CLEARED && it.challengeKind != null }
    val passedCount: Int get() = history.count { it.passed }
    val attempts: Int get() = history.size

    fun nodeById(id: String?): RogueNode? = id?.let { x -> nodes.firstOrNull { it.id == x } }

    fun toSnapshot(): ExpeditionRunSnapshot = ExpeditionRunSnapshot(
        phase = phase,
        focusChapter = focusChapter,
        nodes = nodes,
        layers = layers,
        currentNodeId = currentNodeId,
        hp = hp,
        maxHp = maxHp,
        momentum = momentum,
        bestMomentum = bestMomentum,
        score = score,
        relics = relics,
        isDaily = isDaily,
        history = history,
    )
}
