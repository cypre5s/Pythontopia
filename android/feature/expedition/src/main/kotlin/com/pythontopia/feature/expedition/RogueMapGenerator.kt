package com.pythontopia.feature.expedition

import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/**
 * 无尽远征地图生成（纯函数，可注入 RNG + KC 解析器，便于单测）。
 * 移植 Web `frontend/src/data/rogueMap.ts`。
 *
 * 产出一张分层 DAG：layer 0 单起点(battle)，最后一层单 boss，中间层 2..4 个节点，
 * 每 3 层保证一个营地(rest)。连边保证每个下层节点都有入边、每个上层节点都有出边 → 必可达 boss。
 */

data class ResolvedChallenge(
    val kcCode: String?,
    val challengeKind: String?,
    val difficulty: Int,
)

object RogueMapGenerator {

    private fun pickKindForLayer(layer: Int, midLayers: Int, rng: () -> Double): RogueNodeKind {
        val t = if (midLayers > 0) layer.toDouble() / midLayers else 0.0
        val pool = mutableListOf(RogueNodeKind.BATTLE, RogueNodeKind.BATTLE, RogueNodeKind.EVENT)
        if (t > 0.25) pool.add(RogueNodeKind.BUG)
        if (t > 0.3) pool.add(RogueNodeKind.CHEST)
        if (t > 0.55) pool.add(RogueNodeKind.ELITE)
        return pool.getOrNull(floor(rng() * pool.size).toInt()) ?: RogueNodeKind.BATTLE
    }

    fun generate(
        layersOption: Int = RogueConfig.EXPEDITION_LAYERS,
        rng: () -> Double = { Random.nextDouble() },
        resolveChallenge: (kind: RogueNodeKind, layer: Int) -> ResolvedChallenge,
    ): RogueMap {
        val midLayers = maxOf(3, layersOption)
        val totalLayers = midLayers + 1 // + boss 层
        fun randInt(lo: Int, hi: Int): Int = lo + floor(rng() * (hi - lo + 1)).toInt()

        // 1) 每层节点类型
        val layerKinds = ArrayList<List<RogueNodeKind>>(totalLayers)
        for (layer in 0 until totalLayers) {
            when {
                layer == 0 -> layerKinds.add(listOf(RogueNodeKind.BATTLE))
                layer == totalLayers - 1 -> layerKinds.add(listOf(RogueNodeKind.BOSS))
                else -> {
                    val width = randInt(RogueConfig.LAYER_MIN_WIDTH, RogueConfig.LAYER_MAX_WIDTH)
                    val forceRest = layer % 3 == 0
                    val kinds = ArrayList<RogueNodeKind>(width)
                    for (col in 0 until width) {
                        kinds.add(
                            if (forceRest && col == 0) RogueNodeKind.REST
                            else pickKindForLayer(layer, midLayers, rng),
                        )
                    }
                    layerKinds.add(kinds)
                }
            }
        }

        // 2) 建节点（可变出边列表，连边后定型）
        val byLayer = ArrayList<List<MutableNode>>(totalLayers)
        val all = ArrayList<MutableNode>()
        layerKinds.forEachIndexed { layer, kinds ->
            val row = kinds.mapIndexed { col, kind ->
                val resolved = resolveChallenge(kind, layer)
                MutableNode(
                    id = "L$layer-$col",
                    layer = layer,
                    col = col,
                    kind = kind,
                    kcCode = resolved.kcCode,
                    challengeKind = resolved.challengeKind,
                    difficulty = resolved.difficulty,
                    next = mutableListOf(),
                    state = if (layer == 0) RogueNodeState.AVAILABLE else RogueNodeState.LOCKED,
                )
            }
            byLayer.add(row)
            all.addAll(row)
        }

        // 3) 连边：按归一化列位置取最近 1..2 个，再补齐让下层每个节点都有入边
        fun posOf(n: MutableNode, len: Int): Double = if (len <= 1) 0.5 else n.col.toDouble() / (len - 1)
        for (layer in 0 until totalLayers - 1) {
            val cur = byLayer[layer]
            val nxt = byLayer[layer + 1]
            for (node in cur) {
                val p = posOf(node, cur.size)
                val sorted = nxt.sortedBy { abs(posOf(it, nxt.size) - p) }
                val links = 1 + if (rng() < 0.45 && sorted.size > 1) 1 else 0
                node.next = sorted.take(links).map { it.id }.toMutableList()
            }
            val covered = cur.flatMap { it.next }.toHashSet()
            for (target in nxt) {
                if (covered.contains(target.id)) continue
                val p = posOf(target, nxt.size)
                val src = cur.minByOrNull { abs(posOf(it, cur.size) - p) }
                if (src != null && !src.next.contains(target.id)) src.next.add(target.id)
            }
        }

        return RogueMap(nodes = all.map { it.freeze() }, layers = totalLayers)
    }

    private class MutableNode(
        val id: String,
        val layer: Int,
        val col: Int,
        val kind: RogueNodeKind,
        val kcCode: String?,
        val challengeKind: String?,
        val difficulty: Int,
        var next: MutableList<String>,
        val state: RogueNodeState,
    ) {
        fun freeze() = RogueNode(id, layer, col, kind, kcCode, challengeKind, difficulty, next.toList(), state)
    }
}
