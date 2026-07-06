package com.pythontopia.feature.expedition

/**
 * 无尽远征配置 + KC↔章节工具（移植 Web `frontend/src/data/rogueConfig.ts`）。
 *
 * KC 编码固定 PY43-KC-001..065，章节区间与 bossCatalog / 后端补星阈值一致。
 */
object RogueConfig {

    /** [章节, KC 起编号, KC 止编号]（含端点）。 */
    val KC_CHAPTER_RANGES: List<Triple<Int, Int, Int>> = listOf(
        Triple(1, 1, 7),
        Triple(2, 8, 16),
        Triple(3, 17, 30),
        Triple(4, 31, 38),
        Triple(5, 39, 47),
        Triple(6, 48, 53),
        Triple(7, 54, 65),
    )

    const val KC_TOTAL = 65

    private fun kcCode(num: Int): String = "PY43-KC-${num.toString().padStart(3, '0')}"

    /** 全部 65 个 KC 编码。 */
    fun allKcCodes(): List<String> = (1..KC_TOTAL).map { kcCode(it) }

    /** 某 KC 属于第几章（解析不出时回退第 1 章）。 */
    fun kcChapter(code: String): Int {
        val num = Regex("""PY43-KC-(\d+)""").find(code)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return 1
        return KC_CHAPTER_RANGES.firstOrNull { (_, lo, hi) -> num in lo..hi }?.first ?: 1
    }

    /** 某章的全部 KC 编码。 */
    fun chapterKcs(chapter: Int): List<String> {
        val range = KC_CHAPTER_RANGES.firstOrNull { it.first == chapter } ?: return emptyList()
        return (range.second..range.third).map { kcCode(it) }
    }

    /**
     * 节点类型 → 题型候选池（加权随机取一个）。chest/rest 无题型。
     * 题型字符串对齐 Web ArenaLevelKind，作为 boss/problem 的 preferredKind 传后端。
     */
    val NODE_KIND_POOLS: Map<RogueNodeKind, List<String>> = mapOf(
        RogueNodeKind.BATTLE to listOf("dual_track", "fill_blank", "closed_loop"),
        RogueNodeKind.ELITE to listOf("dual_track", "multi_round"),
        RogueNodeKind.EVENT to listOf("short_answer", "pdf_question"),
        RogueNodeKind.BUG to listOf("debug", "bug_mine"),
        RogueNodeKind.CHEST to emptyList(),
        RogueNodeKind.REST to emptyList(),
        RogueNodeKind.BOSS to listOf("multi_round", "dual_track"),
    )

    val NODE_KIND_LABEL: Map<RogueNodeKind, String> = mapOf(
        RogueNodeKind.BATTLE to "判题战斗",
        RogueNodeKind.ELITE to "精英挑战",
        RogueNodeKind.EVENT to "概念考点",
        RogueNodeKind.BUG to "抓 Bug",
        RogueNodeKind.CHEST to "资源宝箱",
        RogueNodeKind.REST to "复盘营地",
        RogueNodeKind.BOSS to "知识闸 · BOSS",
    )

    val NODE_KIND_ICON: Map<RogueNodeKind, String> = mapOf(
        RogueNodeKind.BATTLE to "⚔",
        RogueNodeKind.ELITE to "✦",
        RogueNodeKind.EVENT to "❓",
        RogueNodeKind.BUG to "🐞",
        RogueNodeKind.CHEST to "🎁",
        RogueNodeKind.REST to "⛺",
        RogueNodeKind.BOSS to "★",
    )

    fun label(kind: RogueNodeKind): String = NODE_KIND_LABEL[kind] ?: "挑战"
    fun icon(kind: RogueNodeKind): String = NODE_KIND_ICON[kind] ?: "⚔"

    /** 地图形状默认值。 */
    const val EXPEDITION_LAYERS = 9
    const val LAYER_MIN_WIDTH = 2
    const val LAYER_MAX_WIDTH = 4

    /** HP / 计分。 */
    const val EXPEDITION_MAX_HP = 5
    const val REST_HEAL = 1

    /** 补星阈值（与 EndlessPage / 后端 MainlineRecoveryService 一致）。 */
    const val STAR_MIN_CORRECT = 8
    const val STAR_MIN_ACCURACY = 0.8

    /** BOSS 多阶段：连过 N 道才算击穿知识闸。 */
    const val BOSS_PHASES = 3
}
