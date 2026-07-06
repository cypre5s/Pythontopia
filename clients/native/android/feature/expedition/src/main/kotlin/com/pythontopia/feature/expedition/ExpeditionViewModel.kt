package com.pythontopia.feature.expedition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 无尽远征 run 状态机（移植 Web Pinia `stores/rogueRun.ts`）。
 *
 * 一局 run：知识前沿生成分支地图 → 玩家沿出边选节点 → 战斗节点真判题（复用 boss 闭环）→
 * 对得分/攒气势、错按韧性温和扣血（掌握度收益持久保留）→ HP 归零结束、打通 boss 胜利。
 * 掌握度/补星后端权威；本 VM 持久化 run 进度（后端 + 本地兜底）。
 */
@HiltViewModel
class ExpeditionViewModel @Inject constructor(
    private val repo: ExpeditionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExpeditionUiState())
    val state: StateFlow<ExpeditionUiState> = _state.asStateFlow()

    private var summaryReported = false
    private var eventScoreMult = 1.0
    private var startEpochMs = 0L

    init {
        viewModelScope.launch {
            val mastery = repo.fetchMastery()
            _state.update { it.copy(mastery = mastery, dailyAvailable = repo.isDailyAvailable()) }
            // 续局：后端优先，本地兜底；可续 run 灌入并归一到地图。
            val snap = repo.fetchRun()
            if (snap != null && snap.nodes.isNotEmpty() && isResumablePhase(snap.phase)) {
                applySnapshot(snap)
            }
        }
    }

    // ── 开局 / 续局 ──

    fun start(focus: Int?) {
        viewModelScope.launch { startRun(focus, fresh = false) }
    }

    fun restart(focus: Int?) {
        viewModelScope.launch { startRun(focus, fresh = true) }
    }

    fun startDaily() {
        viewModelScope.launch {
            if (!repo.isDailyAvailable()) {
                _state.update { it.copy(dailyAvailable = false, toast = "今日挑战已完成，明天再来") }
                return@launch
            }
            val seed = repo.todayStr().replace("-", "").toIntOrNull() ?: 1
            startRun(null, rng = makeLcg(seed), daily = true, fresh = true)
        }
    }

    private suspend fun startRun(
        focus: Int?,
        rng: () -> Double = { Math.random() },
        daily: Boolean = false,
        fresh: Boolean = false,
    ) {
        if (!daily && !fresh) {
            val resumed = repo.fetchRun()
            if (resumed != null && resumed.nodes.isNotEmpty() && isResumablePhase(resumed.phase)) {
                applySnapshot(resumed)
                return
            }
        }
        val recent = _state.value.history.mapNotNull { it.kcCode }.take(12)
        val queue = ArrayDeque(repo.requestFrontierPicks(focus, recent, FRONTIER_COUNT))
        val usedKcs = HashSet<String>()
        val frontier = KcFrontier(_state.value.mastery)
        val map = RogueMapGenerator.generate(rng = rng) { kind, _ ->
            resolveChallenge(kind, usedKcs, queue, frontier, focus)
        }
        summaryReported = false
        eventScoreMult = 1.0
        _state.update {
            it.copy(
                phase = RogueRunPhase.MAP,
                focusChapter = focus,
                nodes = map.nodes,
                layers = map.layers,
                currentNodeId = null,
                hp = RogueConfig.EXPEDITION_MAX_HP,
                maxHp = RogueConfig.EXPEDITION_MAX_HP,
                momentum = 0,
                bestMomentum = 0,
                score = 0,
                relics = emptyList(),
                history = emptyList(),
                isDaily = daily,
                bossPhase = 1,
                activeNodeId = null,
                activeProblem = null,
                activeCode = "",
                challengeResult = null,
                loading = false,
                elapsedSec = 0,
                errorMessage = "",
                activeEventText = "",
            )
        }
        persist()
    }

    private fun resolveChallenge(
        kind: RogueNodeKind,
        usedKcs: MutableSet<String>,
        queue: ArrayDeque<FrontierPick>,
        frontier: KcFrontier,
        focus: Int?,
    ): ResolvedChallenge {
        if (kind == RogueNodeKind.REST || kind == RogueNodeKind.CHEST) {
            return ResolvedChallenge(null, null, 0)
        }
        val pick = queue.removeFirstOrNull()
        val kc: String
        var difficulty: Int
        if (pick != null) {
            kc = pick.kcCode
            difficulty = pick.difficulty
        } else {
            kc = frontier.pickKc(focus, usedKcs)
            difficulty = frontier.difficultyForKc(kc)
        }
        usedKcs.add(kc)
        val pool = RogueConfig.NODE_KIND_POOLS[kind].orEmpty()
        val challengeKind = if (pool.isNotEmpty()) pool[floor(Math.random() * pool.size).toInt()] else "dual_track"
        if (kind == RogueNodeKind.ELITE) difficulty = min(5, difficulty + 1)
        if (kind == RogueNodeKind.BOSS) difficulty = min(5, max(3, difficulty + 1))
        return ResolvedChallenge(kc, challengeKind, difficulty)
    }

    private fun applySnapshot(snap: ExpeditionRunSnapshot) {
        val phase = if (isResumablePhase(snap.phase) && snap.phase != RogueRunPhase.MAP) RogueRunPhase.MAP else snap.phase
        eventScoreMult = 1.0
        _state.update {
            it.copy(
                phase = phase,
                focusChapter = snap.focusChapter,
                nodes = snap.nodes,
                layers = snap.layers,
                currentNodeId = snap.currentNodeId,
                hp = snap.hp,
                maxHp = if (snap.maxHp > 0) snap.maxHp else RogueConfig.EXPEDITION_MAX_HP,
                momentum = snap.momentum,
                bestMomentum = snap.bestMomentum,
                score = snap.score,
                relics = snap.relics,
                isDaily = snap.isDaily,
                history = snap.history,
                activeNodeId = null,
                activeProblem = null,
                activeCode = "",
                challengeResult = null,
                loading = false,
                elapsedSec = 0,
                errorMessage = "",
                activeEventText = "",
            )
        }
    }

    // ── 地图选点 ──

    fun enterNode(id: String) {
        viewModelScope.launch { enterNodeInternal(id) }
    }

    private suspend fun enterNodeInternal(id: String) {
        val s = _state.value
        if (s.phase != RogueRunPhase.MAP) return
        val node = s.nodeById(id) ?: return
        if (node.state != RogueNodeState.AVAILABLE) return
        _state.update { it.copy(currentNodeId = id) }
        when (node.kind) {
            RogueNodeKind.REST -> {
                val heal = RogueConfig.REST_HEAL + if (hasRelic("focus_lens")) 1 else 0
                _state.update {
                    it.copy(
                        hp = min(it.maxHp, it.hp + heal),
                        nodes = markCleared(it.nodes, node.id),
                        toast = "营地休整：恢复 $heal 点体力",
                    )
                }
                persist()
            }
            RogueNodeKind.CHEST -> {
                _state.update { it.copy(nodes = markCleared(it.nodes, node.id)) }
                grantRelic()
                persist()
            }
            RogueNodeKind.EVENT -> {
                eventScoreMult = 1.0
                _state.update {
                    it.copy(
                        activeNodeId = node.id,
                        activeEventText = RogueEvents.pickScenario(kcDisplayName(node.kcCode)),
                        phase = RogueRunPhase.EVENT,
                    )
                }
                persist()
            }
            else -> {
                if (node.kind == RogueNodeKind.BOSS) _state.update { it.copy(bossPhase = 1) }
                beginChallenge(node)
            }
        }
    }

    fun chooseEvent(choice: RogueEventChoice) {
        viewModelScope.launch {
            val s = _state.value
            val node = s.activeNode ?: return@launch
            if (s.phase != RogueRunPhase.EVENT) return@launch
            when (choice) {
                RogueEventChoice.REVIEW -> {
                    val heal = 1 + if (hasRelic("focus_lens")) 1 else 0
                    _state.update {
                        it.copy(
                            hp = min(it.maxHp, it.hp + heal),
                            score = it.score + node.difficulty * 5,
                            nodes = markCleared(it.nodes, node.id),
                            phase = RogueRunPhase.MAP,
                        ).clearedActive()
                    }
                    persist()
                }
                RogueEventChoice.STUDY -> {
                    val harder = node.copy(difficulty = min(5, node.difficulty + 1))
                    eventScoreMult = 1.5
                    _state.update { it.copy(nodes = it.nodes.map { n -> if (n.id == harder.id) harder else n }) }
                    beginChallenge(harder)
                }
                RogueEventChoice.STEADY -> {
                    eventScoreMult = 1.0
                    beginChallenge(node)
                }
            }
        }
    }

    // ── 战斗 ──

    private suspend fun beginChallenge(node: RogueNode) {
        startEpochMs = System.currentTimeMillis()
        _state.update {
            it.copy(
                activeNodeId = node.id,
                activeCode = "",
                challengeResult = null,
                errorMessage = "",
                elapsedSec = 0,
                phase = RogueRunPhase.CHALLENGE,
                loading = true,
            )
        }
        repo.fetchProblem(node.kcCode.orEmpty(), node.challengeKind, node.difficulty)
            .onSuccess { problem -> _state.update { it.copy(activeProblem = problem, loading = false) } }
            .onFailure { e -> _state.update { it.copy(loading = false, errorMessage = e.message ?: "出题失败，请重试") } }
    }

    fun onCodeChange(code: String) {
        _state.update { it.copy(activeCode = code) }
    }

    fun submit() {
        val s = _state.value
        val node = s.activeNode ?: return
        val problem = s.activeProblem ?: return
        if (s.loading) return
        _state.update { it.copy(loading = true, errorMessage = "") }
        viewModelScope.launch {
            val elapsed = ((System.currentTimeMillis() - startEpochMs) / 1000L).toInt().coerceAtLeast(0)
            repo.judge(problem, s.activeCode, elapsed)
                .onFailure { e -> _state.update { it.copy(loading = false, errorMessage = e.message ?: "判题失败，请重试") } }
                .onSuccess { result ->
                    if (result.isJudgeUnavailable()) {
                        _state.update { it.copy(loading = false, errorMessage = "判题服务暂不可用，本次不计分，请稍后重试。") }
                        return@onSuccess
                    }
                    handleJudge(node, result)
                }
        }
    }

    private suspend fun handleJudge(node: RogueNode, result: ExpeditionAttemptResultDto) {
        val s = _state.value
        val gain = scoreFor(node, s)
        val entry = RogueHistoryEntry(
            nodeId = node.id,
            kcCode = node.kcCode,
            kind = node.kind,
            passed = result.passed,
            score = if (result.passed) gain else 0,
            at = Instant.now().toString(),
        )
        val newHistory = (listOf(entry) + s.history).take(50)

        if (result.passed) {
            val newMomentum = s.momentum + 1
            val newBest = max(s.bestMomentum, newMomentum)
            // BOSS 多阶段：未到最后一阶段则进入下一阶段、不结算，连续作战。
            if (node.kind == RogueNodeKind.BOSS && s.bossPhase < s.bossPhasesTotal) {
                _state.update {
                    it.copy(
                        challengeResult = result,
                        history = newHistory,
                        score = it.score + gain,
                        momentum = newMomentum,
                        bestMomentum = newBest,
                        bossPhase = it.bossPhase + 1,
                        loading = false,
                        toast = "BOSS 阶段 ${it.bossPhase}/${it.bossPhasesTotal} 通过，继续！",
                    )
                }
                maybeReportStar()
                persist()
                beginChallenge(node)
                return
            }
            val isElite = node.kind == RogueNodeKind.ELITE
            _state.update {
                it.copy(
                    challengeResult = result,
                    history = newHistory,
                    score = it.score + gain,
                    momentum = newMomentum,
                    bestMomentum = newBest,
                    nodes = markCleared(it.nodes, node.id),
                    loading = false,
                )
            }
            if (isElite) grantRelic()
        } else {
            var loss = 2
            if (hasRelic("calm_mind")) loss = max(1, loss - 1)
            _state.update {
                it.copy(
                    challengeResult = result,
                    history = newHistory,
                    hp = max(0, it.hp - loss),
                    momentum = if (hasRelic("second_wind")) it.momentum / 2 else 0,
                    loading = false,
                )
            }
        }
        maybeReportStar()
        persist()
    }

    fun continueAfterChallenge() {
        viewModelScope.launch {
            val s = _state.value
            val wasBoss = s.activeNode?.kind == RogueNodeKind.BOSS
            val passed = s.challengeResult?.passed == true
            val newPhase = when {
                s.hp <= 0 -> RogueRunPhase.OVER
                wasBoss && passed -> RogueRunPhase.CLEARED
                else -> RogueRunPhase.MAP
            }
            _state.update { it.copy(phase = newPhase).clearedActive() }
            if ((newPhase == RogueRunPhase.OVER || newPhase == RogueRunPhase.CLEARED) && _state.value.isDaily) {
                repo.markDailyDone()
                _state.update { it.copy(dailyAvailable = false) }
            }
            persist()
        }
    }

    fun retryChallenge() {
        viewModelScope.launch {
            val node = _state.value.activeNode ?: return@launch
            beginChallenge(node)
        }
    }

    fun abort() {
        viewModelScope.launch {
            _state.update {
                ExpeditionUiState(
                    phase = RogueRunPhase.IDLE,
                    mastery = it.mastery,
                    dailyAvailable = it.dailyAvailable,
                    focusChapter = it.focusChapter,
                )
            }
            repo.deleteRun()
        }
    }

    fun tick() {
        val s = _state.value
        if (s.phase == RogueRunPhase.CHALLENGE && !s.loading) {
            _state.update { it.copy(elapsedSec = it.elapsedSec + 1) }
        }
    }

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }

    // ── 辅助 ──

    private fun hasRelic(id: String): Boolean = _state.value.relics.contains(id)

    private fun grantRelic() {
        val owned = _state.value.relics.toSet()
        val candidates = RogueRelics.ALL.filter { it.id !in owned }
        if (candidates.isEmpty()) {
            _state.update { it.copy(toast = "已集齐全部学习增益") }
            return
        }
        val relic = candidates[floor(Math.random() * candidates.size).toInt()]
        _state.update {
            val bonusHp = if (relic.id == "sturdy_notes") 1 else 0
            it.copy(
                relics = it.relics + relic.id,
                maxHp = it.maxHp + bonusHp,
                hp = it.hp + bonusHp,
                toast = "获得学习增益：${relic.icon} ${relic.name}",
            )
        }
    }

    private fun scoreFor(node: RogueNode, s: ExpeditionUiState): Int {
        val base = node.difficulty * 10
        val momentumBonus = (base * s.momentum * 0.1).roundToInt()
        val kindMult = if (node.kind == RogueNodeKind.ELITE || node.kind == RogueNodeKind.BOSS) 1.5 else 1.0
        val relicMult = if (s.relics.contains("combo_scholar")) 1.2 else 1.0
        return ((base + momentumBonus) * kindMult * relicMult * eventScoreMult).roundToInt()
    }

    /** 标记节点通关：解锁出边、同层其它可选节点置为绕过（不可回头）。返回新节点列表。 */
    private fun markCleared(nodes: List<RogueNode>, nodeId: String): List<RogueNode> {
        val target = nodes.firstOrNull { it.id == nodeId } ?: return nodes
        val unlock = target.next.toHashSet()
        return nodes.map { n ->
            when {
                n.id == nodeId -> n.copy(state = RogueNodeState.CLEARED)
                n.layer == target.layer && n.state == RogueNodeState.AVAILABLE -> n.copy(state = RogueNodeState.BYPASSED)
                n.id in unlock && n.state == RogueNodeState.LOCKED -> n.copy(state = RogueNodeState.AVAILABLE)
                else -> n
            }
        }
    }

    private suspend fun maybeReportStar() {
        val chapter = _state.value.focusChapter ?: return
        if (summaryReported) return
        val correct = _state.value.passedCount
        val total = _state.value.attempts
        if (total <= 0 || correct < RogueConfig.STAR_MIN_CORRECT || correct.toDouble() / total < RogueConfig.STAR_MIN_ACCURACY) return
        summaryReported = true
        val awarded = repo.submitEndlessSummary(chapter, correct, total, _state.value.bestMomentum)
        if (awarded == null) {
            summaryReported = false
        } else if (awarded.starAwarded) {
            _state.update { it.copy(toast = "第 $chapter 章远征补星 +1 ★") }
        }
    }

    private suspend fun persist() {
        repo.saveRun(_state.value.toSnapshot())
    }

    private fun isResumablePhase(p: RogueRunPhase): Boolean =
        p == RogueRunPhase.MAP || p == RogueRunPhase.CHALLENGE || p == RogueRunPhase.EVENT

    /** 确定性 RNG（LCG）：每日挑战按日期种子生成稳定的当日地图。 */
    private fun makeLcg(seed: Int): () -> Double {
        var s = (seed.toLong() and 0xffffffffL).let { if (it == 0L) 1L else it }
        return {
            s = (s * 1664525L + 1013904223L) and 0xffffffffL
            s.toDouble() / 0xffffffffL.toDouble()
        }
    }

    private fun ExpeditionUiState.clearedActive(): ExpeditionUiState = copy(
        activeNodeId = null,
        activeProblem = null,
        activeCode = "",
        challengeResult = null,
        elapsedSec = 0,
        errorMessage = "",
        activeEventText = "",
    )

    private companion object {
        const val FRONTIER_COUNT = 28
    }
}
