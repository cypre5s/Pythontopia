package com.pythontopia.feature.expedition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RogueMapGeneratorTest {

    /** 注入 KC/题型/难度解析器：电池/宝箱无题型，其余给固定占位值。 */
    private fun resolver(kind: RogueNodeKind, layer: Int): ResolvedChallenge =
        if (kind == RogueNodeKind.REST || kind == RogueNodeKind.CHEST) {
            ResolvedChallenge(null, null, 0)
        } else {
            ResolvedChallenge("PY43-KC-001", "dual_track", 2)
        }

    private fun seq(values: List<Double>): () -> Double {
        var i = 0
        return { values[i++ % values.size] }
    }

    @Test
    fun `start layer is single available battle and top is single boss`() {
        val map = RogueMapGenerator.generate(rng = seq(listOf(0.5, 0.1, 0.9, 0.3)), resolveChallenge = ::resolver)

        assertEquals(RogueConfig.EXPEDITION_LAYERS + 1, map.layers)

        val startLayer = map.nodes.filter { it.layer == 0 }
        assertEquals(1, startLayer.size)
        assertEquals(RogueNodeKind.BATTLE, startLayer.first().kind)
        assertEquals(RogueNodeState.AVAILABLE, startLayer.first().state)

        val bosses = map.nodes.filter { it.kind == RogueNodeKind.BOSS }
        assertEquals(1, bosses.size)
        assertEquals(map.layers - 1, bosses.first().layer)
    }

    @Test
    fun `every node is reachable from the start and boss is terminal`() {
        val map = RogueMapGenerator.generate(rng = seq(listOf(0.2, 0.6, 0.4, 0.8, 0.05)), resolveChallenge = ::resolver)
        val byId = map.nodes.associateBy { it.id }

        // BFS 从起点层沿出边遍历，应覆盖全部节点（连边保证必可达）。
        val start = map.nodes.filter { it.layer == 0 }.map { it.id }
        val visited = HashSet<String>()
        val queue = ArrayDeque(start)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!visited.add(id)) continue
            byId[id]?.next?.forEach { queue.add(it) }
        }
        assertEquals(map.nodes.size, visited.size, "所有节点都应从起点可达")

        // 每个非起点节点至少有一条入边。
        val withInbound = map.nodes.flatMap { it.next }.toHashSet()
        map.nodes.filter { it.layer > 0 }.forEach { node ->
            assertTrue(withInbound.contains(node.id), "节点 ${node.id} 缺少入边")
        }

        // boss 是终点：无出边。
        assertTrue(map.nodes.first { it.kind == RogueNodeKind.BOSS }.next.isEmpty())
    }

    @Test
    fun `daily seed produces a stable map`() {
        fun lcg(seed: Int): () -> Double {
            var s = (seed.toLong() and 0xffffffffL).let { if (it == 0L) 1L else it }
            return {
                s = (s * 1664525L + 1013904223L) and 0xffffffffL
                s.toDouble() / 0xffffffffL.toDouble()
            }
        }
        val a = RogueMapGenerator.generate(rng = lcg(20260619), resolveChallenge = ::resolver)
        val b = RogueMapGenerator.generate(rng = lcg(20260619), resolveChallenge = ::resolver)
        assertEquals(a.nodes.map { it.id to it.kind }, b.nodes.map { it.id to it.kind })
    }
}
