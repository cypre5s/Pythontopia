package com.pythontopia.feature.expedition

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 本地「知识前沿选择器」（移植 Web `useKcFrontier`），仅在后端 select-frontier 不可达时兜底。
 *
 * 优先级（高→低）：画像弱项且未掌握 > ZPD 中带(0.2–0.8) > 未掌握 > 越不熟越靠前。
 * Android 端暂无 profile signals，[weakKcs] 默认空集，退化为掌握度驱动的合理排序。
 */
class KcFrontier(
    private val mastery: Map<String, Double>,
    private val weakKcs: Set<String> = emptySet(),
    private val rng: () -> Double = { Random.nextDouble() },
) {
    private fun masteryOf(code: String): Double = mastery[code] ?: 0.0

    private fun priority(code: String): Double {
        val m = masteryOf(code)
        var p = 0.0
        if (weakKcs.contains(code) && m < 0.8) p += 100
        if (m in 0.2..0.8) p += 50
        if (m < 0.2) p += 20
        p += (1 - m) * 10
        return p
    }

    fun frontier(focusChapter: Int?): List<String> {
        val pool = if (focusChapter != null) RogueConfig.chapterKcs(focusChapter) else RogueConfig.allKcCodes()
        return pool.sortedByDescending { priority(it) }
    }

    fun pickKc(focusChapter: Int?, exclude: Set<String> = emptySet(), topN: Int = 8): String {
        val ranked = frontier(focusChapter).filter { !exclude.contains(it) }
        val list = ranked.ifEmpty { frontier(focusChapter) }
        if (list.isEmpty()) return RogueConfig.allKcCodes().first()
        val span = maxOf(1, minOf(topN, list.size))
        val idx = (rng() * span).toInt().coerceIn(0, span - 1)
        return list.getOrNull(idx) ?: list.first()
    }

    fun difficultyForKc(code: String): Int {
        val m = masteryOf(code)
        return (1 + m * 4).roundToInt().coerceIn(1, 5)
    }
}
